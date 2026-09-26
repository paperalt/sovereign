# SPESIFIKASI DESAIN UI/UX ANDROID CLIENT (KOTLIN / JETPACK COMPOSE)
**Anti-AI-Slop, Enterprise-Grade Utility Interface**

---

## 1. Filosofi & Prinsip Desain
Desain antarmuka ini mengacu pada standar utilitas profesional (seperti *Google Recorder*, *Linear*, dan *Descript*):
- **Bebas Gimmick (Anti-AI-Slop):** Dilarang menggunakan gradien pelangi, ikon robot/bintang AI mengambang, efek *glassmorphism* berlebihan, atau lencana kosmetik tidak fungsional. AI bekerja senyap di latar belakang (*invisible utility*).
- **Tipografi & Kontras Tinggi:** Rasio kontras teks minimal 7:1 (WCAG AAA) dengan *tabular figures* untuk timer dan stempel waktu (*timestamps*).
- **Ketegasan Fungsional:** Mengutamakan kecepatan baca teks transkripsi, visibilitas status perekaman secara instan, dan kontrol yang tidak dapat tertekan secara tidak sengaja.
- **Strict Grid System:** Menggunakan *8dp base grid* (4dp untuk *micro-spacing*) yang konsisten pada seluruh resolusi layar Android.

---

## 2. Design Tokens & Palette

### Warna (Monochrome Industrial Dark Theme)
Sistem menggunakan tema gelap monokromatik untuk kenyamanan membaca transkripsi berdurasi panjang dan efisiensi baterai pada layar OLED:

```
[Backgrounds]
Surface 0 (Screen BG)      : #0A0D12 (Onyx Black)
Surface 1 (Card / Container): #141A22 (Dark Slate)
Surface 2 (Hover / Active)  : #1E2632 (Charcoal Slate)
Border / Divider           : #283342 (Subtle Steel)

[Text & Foreground]
Text Primary               : #F0F4F8 (High Contrast White)
Text Secondary             : #94A3B8 (Neutral Slate)
Text Tertiary (Timestamps) : #64748B (Muted Steel)

[Functional Accents]
Recording Active (Signal)  : #E11D48 (Crimson Red - Only during active mic)
Status Connected           : #10B981 (Subtle Emerald)
Action / Accent Primary    : #38BDF8 (Clean Sky Blue - Neutral interaction)
Action Background Pill     : #0F2A3D (Deep Cyan Tint)
```

### Tipografi (Inter / Roboto Tabular)
- **Title Large:** 20sp, Medium, Line-height 28sp (Judul Rapat).
- **Body Large (Transcript Text):** 16sp, Normal, Line-height 26sp, Letter-spacing 0.15sp.
- **Caption / Timestamp:** 12sp, Tabular Figures (Monospace-aligned), Medium.
- **Label / Tag:** 11sp, Semibold, Uppercase, Letter-spacing 0.5sp.

---

## 3. Spesifikasi Tata Letak Layar (Screen Blueprints)

### LAYAR 1: Dashboard / Riwayat Sesi (Session Index)
```
+-------------------------------------------------------------+
| [Search Sesi, Transkripsi, atau Action Items...]            |
+-------------------------------------------------------------+
| FILTER: [Semua]  [Bahasa: ID]  [Bahasa: EN]                |
+-------------------------------------------------------------+
| RIWAYAT SESI                                                |
|                                                             |
| +---------------------------------------------------------+ |
| | Rapat Teknis Arsitektur Cloud                           | |
| | 20 Sep 2026, 14:00 • 42 Menit • [EN -> ID]             | |
| | "Evaluasi arsitektur streaming WebSocket menunjukkan     | |
| |  penurunan latensi hingga 15 milidetik..."              | |
| +---------------------------------------------------------+ |
|                                                             |
| +---------------------------------------------------------+ |
| | Pembahasan Audit Keamanan Sistem                        | |
| | 19 Sep 2026, 09:15 • 18 Menit • [ID]                    | |
| | "Implementasi Argon2id telah divalidasi kebal terhadap  | |
| |  serangan replay token dan IDOR..."                     | |
| +---------------------------------------------------------+ |
|                                                             |
+-------------------------------------------------------------+
| [ + MULAI TRANSCRIBE BARU ] (Sticky Bottom Full-Width CTA)  |
+-------------------------------------------------------------+
```

---

### LAYAR 2: Sesi Perekaman Aktif (Live Streaming & Transcription)
Fokus total pada teks yang masuk secara real-time, monitoring input suara, dan kontrol rekam:

```
+-------------------------------------------------------------+
| <- BATAL     Rapat Koordinasi Tim              PILIH BAHASA |
|              Status: LIVE STREAMING [ID]                    |
+-------------------------------------------------------------+
|                                                             |
| AUDIO INPUT METER (Real-Time Amplitude Bars / 32 Bands)     |
| [ ||||||||||||||||||||||||||||||||||||||||||||||||||||||| ] |
|                                                             |
+-------------------------------------------------------------+
| TRANSKRIPSI LANGSUNG (Auto-scroll to latest chunk)          |
|                                                             |
| 00:00:12  Kita mulai sesi pembahasan implementasi backend.  |
|                                                             |
| 00:00:25  Seluruh audio chunking telah berbasis deteksi      |
|           energi suara (RMS) tanpa pemotongan kaku kata.    |
|                                                             |
| 00:00:48  Model ag/gemini-3.8-flash-low memproses biner WAV  |
|           dengan kecepatan rata-rata 1.5 detik per blok.    |
|                                                             |
| [Kursor streaming berkedip...]                              |
|                                                             |
+-------------------------------------------------------------+
| DURASI: 00:01:14                                            |
|                                                             |
|       [ II JEDA ]                 [ ■ SELESAIKAN RAPAT ]    |
+-------------------------------------------------------------+
```

---

### LAYAR 3: Detail Sesi & Intelijen Rapat (Meeting Detail & Intelligence)
Setelah rapat selesai, hasil transkripsi dan intelijen AI dibagi ke dalam segmented control yang bersih:

```
+-------------------------------------------------------------+
| <- KEMBALI   Rapat Koordinasi Tim                    [SHARE]|
|              20 Sep 2026 • 24 Menit • Status: COMPLETED     |
+-------------------------------------------------------------+
| TAB:  [ TRANSKRIPSI ]    [ RINGKASAN ]    [ ACTION ITEMS ]  |
+-------------------------------------------------------------+
|                                                             |
| [Saat Tab ACTION ITEMS Aktif]:                              |
|                                                             |
| +---------------------------------------------------------+ |
| | [ ] Audit integritas token revocation pada tabel DB     | |
| |     Assignee: Tim Keamanan • Prioritas: TINGGI          | |
| +---------------------------------------------------------+ |
|                                                             |
| +---------------------------------------------------------+ |
| | [ ] Integrasikan SSL Pinning pada OkHttp client Android | |
| |     Assignee: Mobile Engineer • Prioritas: TINGGI       | |
| +---------------------------------------------------------+ |
|                                                             |
| +---------------------------------------------------------+ |
| | [x] Evaluasi performa indeks GIN pada PostgreSQL 16     | |
| |     Selesai • Verifikasi query < 1ms                    | |
| +---------------------------------------------------------+ |
|                                                             |
+-------------------------------------------------------------+
| [ EKSPOR MARKDOWN ]   [ SALIN SEMUA TEKS ]   [ HAPUS SESI ] |
+-------------------------------------------------------------+
```

---

## 4. Implementasi Komponen Inti Jetpack Compose (Kotlin)

Berikut kode implementasi komponen inti transkripsi langsung yang modular, bebas dependensi visual berlebih, dan efisien:

```kotlin
package id.eclipsegate.transcribe.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

// Design Tokens
val OnyxBlack = Color(0xFF0A0D12)
val DarkSlate = Color(0xFF141A22)
val SteelBorder = Color(0xFF283342)
val TextPrimary = Color(0xFFF0F4F8)
val TextSecondary = Color(0xFF94A3B8)
val TextMuted = Color(0xFF64748B)
val CrimsonActive = Color(0xFFE11D48)
val EmeraldConnected = Color(0xFF10B981)

data class TranscriptItem(
    val index: Int,
    val timeLabel: String,
    val text: String
)

@Composable
fun LiveTranscriptionScreen(
    meetingTitle: String,
    elapsedTime: String,
    transcriptItems: List<TranscriptItem>,
    isRecording: Boolean,
    onStopClicked: () -> Unit,
    onPauseClicked: () -> Unit,
    onCancelClicked: () -> Unit
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Auto-scroll to latest chunk
    LaunchedEffect(transcriptItems.size) {
        if (transcriptItems.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(transcriptItems.size - 1)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OnyxBlack)
            .padding(16.dp)
    ) {
        // 1. Top Bar Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onCancelClicked) {
                Text("BATAL", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = meetingTitle,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(if (isRecording) CrimsonActive else EmeraldConnected, shape = RoundedCornerShape(3.dp))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isRecording) "RECORDING" else "IDLE",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            Box(modifier = Modifier.width(48.dp)) // Spacer balance
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Transcript Stream Area (Primary focus)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(DarkSlate, shape = RoundedCornerShape(8.dp))
                .border(1.dp, SteelBorder, shape = RoundedCornerShape(8.dp))
                .padding(16.dp)
        ) {
            if (transcriptItems.isEmpty()) {
                Text(
                    text = "Menunggu suara pembicara...",
                    color = TextMuted,
                    fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(transcriptItems, key = { it.index }) { item ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = item.timeLabel,
                                color = TextMuted,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.width(60.dp)
                            )
                            Text(
                                text = item.text,
                                color = TextPrimary,
                                fontSize = 15.sp,
                                lineHeight = 24.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Bottom Control Console
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = elapsedTime,
                color = TextPrimary,
                fontSize = 20.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onPauseClicked,
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SteelBorder)
                ) {
                    Text("JEDA", color = TextPrimary, fontSize = 13.sp)
                }

                Button(
                    onClick = onStopClicked,
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonActive),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("SELESAIKAN", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
```

---

## 5. Fitur Aksesibilitas & Efisiensi Layar
1. **Tidak Ada Animasi Berat:** Menghindari partikel atau pergerakan yang memakan siklus GPU.
2. **Auto-Scroll Pintar:** Jika pengguna sedang melakukan *scroll* ke atas untuk membaca teks sebelumnya, sistem menonaktifkan *auto-scroll* otomatis agar tidak menginterupsi pembacaan. Tombol *"Ke Bawah"* muncul secara diskret di pojok kanan bawah.
3. **Ketahanan Orientasi:** Desain menggunakan *flexbox-based responsive constraint*, otomatis beralih ke tata letak dua kolom (*dual-column landscape*) saat perangkat diputar horizontal atau digunakan pada Android Tablet.
