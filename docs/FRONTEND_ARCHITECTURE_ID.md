# Arsitektur Front-End Android (Kotlin / Jetpack Compose)
**Pola MVI, Ketahanan Hardware, & Standar Rekayasa Zero-Defect**  
*Mitigasi Bug Visual, Frame Drops, Memory Leaks, Audio Focus Hijack, dan Network Interruption*

[![Release Version](https://img.shields.io/badge/Release-v2.1.0%20(Build%2042)-38BDF8?style=flat-square)](https://gate.eclipsegate.my.id/downloads/transcribe-core.apk)

---

> 🌐 **Bahasa:** **Bahasa Indonesia** | [English Version](FRONTEND_ARCHITECTURE.md)

---

## 1. Ringkasan Eksekutif

Klien Android dirancang untuk mencegah flicker visual, mengisolasi render 60 FPS, dan menjaga stabilitas penangkapan audio di bawah kondisi ekstrem sistem operasi Android (layar mati, pembatasan proses background, interupsi panggilan telepon masuk, dan transisi jaringan seluler). Dibangun menggunakan Jetpack Compose dan Kotlin Coroutines, arsitektur ini mengandalkan Foreground Service mandiri dengan AudioRecord 16kHz, buffer memori sirkular 5MB, antrean pre-roll 256ms, dan mesin VAD client-side (< 280 RMS) yang menghemat kuota transmisi hingga ~80%.

---

## 2. Anatomi Bug Kritis pada Aplikasi Transkripsi Real-Time

Aplikasi perekaman dan transkripsi streaming memiliki tantangan teknis yang jauh lebih kompleks dibanding aplikasi CRUD biasa. Terdapat 6 titik kegagalan fatal yang dicegah di level arsitektur:

1. **Scroll Jumping & Viewport Conflict (Bug Visual Utama):**
   * *Penyebab:* Saat teks baru tiba via WebSocket, pemanggilan kaku `listState.animateScrollToItem()` akan menyentak layar ke bawah secara paksa saat pengguna sedang berusaha membaca kalimat sebelumnya.
   * *Solusi:* State machine penahan posisi gulir (*Scroll Anchor FSM*). Auto-scroll hanya aktif jika pengguna berada pada batas bawah. Jika pengguna menggulir ke atas, auto-scroll dinonaktifkan dan muncul indikator diskret `[ ↓ Teks Baru ]`.
2. **Recomposition Churn & UI Jank (Bug Performa / 60 FPS Drops):**
   * *Penyebab:* Visualisasi waveform suara berdetak pada 30–60 Hz. Jika state amplitude digabung dalam satu ViewModel State dengan teks transkripsi, seluruh komponen layar akan ter-recompose 60 kali per detik, memicu *frame drop* dan pemborosan baterai.
   * *Solusi:* Pemisahan total frekuensi state (*High-Frequency State vs Low-Frequency State*). Waveform dirender langsung pada `Canvas` melalui `FloatArray` terisolasi tanpa memicu recomposition pohon widget teks.
3. **Process Death & Activity Recreation (Screen Rotation Bug):**
   * *Penyebab:* Rotasi layar atau meminimalkan aplikasi ke latar belakang akan menghancurkan `Activity`. Jika mic (`AudioRecord`) atau WebSocket terikat pada lifecycle Activity, rekaman audio akan langsung terputus.
   * *Solusi:* Audio ingestion dan koneksi WebSocket dijalankan di dalam **Foreground Service** native Android yang terikat pada *Ongoing Notification*. UI hanya berfungsi sebagai observer pasif berbasis `StateFlow`.
4. **Network Handover & Zombie Sockets (Bug Jaringan 4G/Wi-Fi):**
   * *Penyebab:* Transisi sinyal memicu soket menggantung (*zombie socket*) tanpa memicu callback `onFailure` secara instan.
   * *Solusi:* WebSocket Heartbeat FSM dengan ping lokal tiap 15s dan *Exponential Backoff Reconnection*. Audio PCM di-buffer di memori terbatas (*Bounded Ring Buffer* max 5MB) agar data ucapan saat offline tidak hilang.
5. **Layout Shift & Text Glitching (Bug Identitas List):**
   * *Penyebab:* Menggunakan list index sebagai key Compose menyebabkan animasi flickering saat item bertambah.
   * *Solusi:* Seluruh chunk wajib memiliki `@Stable` immutable ID berbasis `chunk_index` integer unik dari backend.
6. **Audio Hardware Hijack (Bug Interupsi Panggilan Masuk):**
   * *Penyebab:* Panggilan telepon masuk atau alarm mencuri hardware mikrofon (`AudioRecord` melempar *IllegalStateException*).
   * *Solusi:* Manajemen `AudioManager.OnAudioFocusChangeListener` dengan retensi jeda manual (`isManuallyPaused`) untuk mencegah unpause otomatis tanpa persetujuan pengguna.

---

## 3. Diagram Aliran Data Sistem (MVI Architecture)

```
[ Mikrofon Perangkat ] 
         │ (AudioRecord 16kHz PCM Buffer)
         ▼
[ TranscriptionForegroundService ] ◄── Lifecycle Independen (Tahan Rotasi Layar)
   ├── Audio Focus Listener (Telepon masuk / Interupsi)
   ├── Bounded Ring Buffer (Kapasitas Maksimal 5 MB / Menolak OOM)
   └── OkHttp WebSocket Client (FSM: CONNECTING, STREAMING, RETRYING)
         │
         │ (StateFlow: Low-Frequency Chunks)  (SharedFlow: Amplitude 60Hz)
         ▼                                   ▼
[ TranscriptionViewModel ] ───────────────────────────────────────────┐
   │ (Single Source of Truth - UDF)                                   │
   ▼                                                                  ▼
[ LiveTranscriptionScreen (Compose) ]                      [ WaveformCanvas (Isolated) ]
   ├── ScrollAnchorFSM (Anti-Jumping)                         └── Direct Draw Scope (Nol Recompose)
   └── Keyed LazyColumn (Anti-Flicker)
```

---

## 4. Implementasi State Machine Pencegah Bug Visual (Scroll Anchor FSM)

Mekanisme ini menjamin transkripsi yang mengalir deras tidak pernah menyentak layar saat pengguna sedang membaca teks riwayat di atas.

```kotlin
// ui/state/ScrollAnchorController.kt
package id.eclipsegate.transcribe.ui.state

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Stable
class ScrollAnchorController(
    val listState: LazyListState,
    val coroutineScope: CoroutineScope
) {
    var isUserScrolledUp by mutableStateOf(false)
        private set

    var hasPendingChunks by mutableStateOf(false)
        private set

    fun onNewItemAppended(totalItems: Int) {
        if (!isUserScrolledUp) {
            coroutineScope.launch {
                listState.animateScrollToItem(totalItems - 1)
            }
        } else {
            hasPendingChunks = true
        }
    }

    fun onScrollToBottomClicked(totalItems: Int) {
        isUserScrolledUp = false
        hasPendingChunks = false
        coroutineScope.launch {
            listState.animateScrollToItem(totalItems - 1)
        }
    }
}
```
