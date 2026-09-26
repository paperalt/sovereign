# Sovereign: Aplikasi Android Murni Intelijen Wicara

**Platform Transkripsi Audio Real-Time Mandiri Tanpa Backend, Rekomendasi Pertanyaan Rapat, dan Kedaulatan Data Lokal Penuh**

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg?style=flat-square)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B%20%28API%2026%2B%29-3DDC84?style=flat-square&logo=android)](https://developer.android.com/)
[![Language](https://img.shields.io/badge/Kotlin-2.0%20%7C%20Jetpack%20Compose-7F52FF?style=flat-square&logo=kotlin)](https://kotlinlang.org/)
[![Database](https://img.shields.io/badge/Storage-On--Device%20SQLite%203-003B57?style=flat-square&logo=sqlite)](https://sqlite.org/)
[![Key Vault](https://img.shields.io/badge/Security-Android%20Keystore%20AES--256--GCM-10B981?style=flat-square)]()
[![AI Routing](https://img.shields.io/badge/Direct%20AI-Groq%20LPU%20%7C%20Gemini%20Flash%20%7C%20OpenAI-FF6F00?style=flat-square)](https://groq.com/)

---

> 🌐 **Bahasa:** **Bahasa Indonesia** | [English Version](README.md)

---

## 1. Ringkasan Eksekutif & Paradigma Aplikasi Mandiri (Pure Standalone)

**Sovereign** adalah aplikasi Android mandiri (*pure standalone application*) berlisensi open source murni yang memproses perekaman audio langsung, transkripsi real-time, rekomendasi pertanyaan rapat cerdas, dan perangkuman eksekutif **tanpa memerlukan server backend, basis data terpusat, atau perantara cloud apa pun**.

Di saat layanan transkripsi komersial (seperti Otter.ai atau Fireflies.ai) membebankan biaya langganan rutin ($17–$18/bulan), membatasi pengguna pada vendor tunggal, dan menyimpan rekaman rapat privat di server cloud pihak ketiga, Sovereign menerapkan **Arsitektur Kedaulatan Data Lokal Penuh**:

* **Murni Tanpa Server Backend:** Tidak memerlukan server Go, API Python, kontainer Docker, maupun VPS untuk beroperasi. Seluruh sistem berjalan mandiri di dalam satu aplikasi Android.
* **Penyimpanan 100% di Perangkat (Native SQLite 3):** Seluruh transkrip, potongan rekaman, pengelompokan folder (*groups*), dan ringkasan eksekutif disimpan di basis data SQLite lokal (`sovereign_transcribe.db`) di dalam ruang aman (*internal app storage sandbox*) Android.
* **Ingesti & Encoding Audio Langsung di Android:** Sinyal mentah 16kHz 16-bit Mono Linear PCM dari `AudioRecord` dipotong dan dikemas menjadi kontainer biner standar RIFF/WAVE (RFC 2361) secara in-memory menggunakan enkoder bawaan Kotlin (`WavEncoder.kt`).
* **Client-Side VAD Streaming Chunker:** Voice Activity Detection (supresi hening < 50 RMS, pemotongan jeda bicara < 280 RMS, jendela durasi 2,5s–12,0s) dieksekusi langsung menggunakan coroutine Kotlin di ponsel.
* **Routing AI Langsung Ponsel-ke-Penyedia (Direct HTTPS):** Ponsel terhubung langsung ke API penyedia AI (Groq LPU Whisper Large Turbo, Google AI Studio Gemini 2.0 Flash, atau OpenAI Whisper-1) melalui protokol HTTPS standar menggunakan API key pribadi pengguna tanpa perantara proxy.
* **Konfigurasi Universal Multi-Endpoint & Auto-Deteksi Model:** Pengguna dapat memasukkan endpoint API dan model kustom apa pun secara mandiri untuk Voice (STT) dan LLM (Nalar/Ide Tanya). Tombol `[DETEKSI]` melakukan panggilan otomatis ke `GET /models` untuk mendeteksi daftar model yang tersedia di server target.
* **Preset Terkonfigurasi via GitHub Raw JSON:** Profil penyedia populer (Groq, Google Gemini, OpenAI, DeepSeek, OpenRouter, Ollama) ditarik otomatis dari berkas `config/providers.json` di GitHub (dengan fallback lokal), sehingga pengguna cukup menempelkan API key dari penyedia.
* **Brankas Kunci Keras Android Keystore:** Kunci API pengguna disimpan secara terenkripsi menggunakan **Android Keystore (AES-256-GCM)** via `EncryptedSharedPreferences`. Kunci tidak pernah terekspos ke penyimpanan eksternal atau teks biasa.
* **100% Bebas & Terbuka:** Berlisensi resmi di bawah **MIT License**.

---

## 2. Arsitektur Sistem Klien Mandiri

```mermaid
flowchart TD
    subgraph AndroidApp["APLIKASI ANDROID SOVEREIGN (Tanpa Server Backend)"]
        subgraph Hardware["1. Perangkat Keras Audio & Encoding"]
            Mic["Input Mikrofon<br/>• AudioRecord 16kHz Mono PCM<br/>• Penalaan VOICE_RECOGNITION"]
            Chunker["AudioStreamChunker.kt<br/>• Client-Side RMS VAD Gating<br/>• Deteksi Jeda Hening (< 280 RMS)<br/>• Pemotongan Adaptif: 2.5s - 12.0s"]
            Encoder["WavEncoder.kt<br/>• Enkoder RFC 2361 RIFF/WAVE<br/>• Injeksi Header 44-byte di RAM"]
            Mic --> Chunker --> Encoder
        end

        subgraph LocalStore["2. Basis Data SQLite Lokal"]
            SQLite[("Native SQLite 3 DB<br/>• sovereign_transcribe.db<br/>• meetings, transcript_chunks<br/>• transcript_groups, summaries<br/>• Kueri Cepat Sub-Milidetik")]
        end

        subgraph UI["3. Antarmuka Jetpack Compose"]
            Dashboard["DashboardScreen.kt<br/>• Pustaka rapat offline<br/>• Operasi massal long-press<br/>• Pencarian teks instan"]
            Live["LiveTranscriptionScreen.kt<br/>• Visualisasi waveform 32-bar<br/>• Aliran teks transkrip live<br/>• Dialog rekomendasi ide tanya"]
            Detail["MeetingDetailScreen.kt<br/>• Editor in-place chunk & naskah<br/>• Regenerasi ringkasan eksekutif<br/>• Ekspor format Markdown"]
        end

        subgraph DirectAI["4. Klien AI Langsung (DirectAIClient)"]
            AIClient["DirectAIClient.kt (OkHttp)<br/>• Groq LPU Whisper Turbo (~0.3s STT)<br/>• Groq Llama 3.3 70B (Summary & Tanya)<br/>• Google Gemini 2.0 Flash / OpenAI"]
        end

        Encoder --> AIClient
        AIClient --> LocalStore
        LocalStore <--> UI
    end

    subgraph ExternalAPIs["PENYEDIA MODEL AI PENGGUNA (Direct HTTPS)"]
        Groq["Groq Cloud API<br/>https://api.groq.com"]
        Google["Google AI Studio<br/>generativelanguage.googleapis.com"]
        OpenAI["OpenAI Platform<br/>api.openai.com"]
    end

    AIClient <==> |Direct HTTPS dengan Kunci Pribadi| ExternalAPIs
```

---

## 3. Panduan Kompilasi & Pemasangan

### Prasyarat
* JDK 17 atau JDK 21
* Android SDK 35 (minSdk 26 — Android 8.0 Oreo ke atas)

### Kompilasi Debug APK
```bash
# 1. Klon repositori
git clone https://github.com/paperalt/sovereign.git
cd sovereign

# 2. Beri hak akses eksekusi gradle wrapper
chmod +x gradlew

# 3. Kompilasi APK
./gradlew assembleDebug

# Lokasi berkas APK hasil kompilasi:
# app/build/outputs/apk/debug/app-debug.apk
```

### Pemasangan Langsung ke Ponsel via ADB
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 4. Matriks Perbandingan Teknis

| Kapabilitas / Parameter | Sovereign (Aplikasi Android Murni) | Layanan Cloud SaaS (Otter.ai, dll) |
| :--- | :---: | :---: |
| **Ketergantungan Server Backend** | **TIDAK ADA (Murni di Android)** | Membutuhkan Server Cloud Vendor |
| **Lokasi Penyimpanan Data** | **100% di Perangkat (SQLite 3 Lokal)** | Basis Data Cloud Pihak Ketiga |
| **Enkoding Audio** | **In-Memory di Ponsel (WavEncoder 16kHz)** | Transcoding di Server / Cloud |
| **Latensi Jaringan** | **Direct HTTPS (~300ms via Groq)** | Tinggi (Antrean WebSocket Server) |
| **Biaya Bulanan** | **$0 / bulan (Bebas & Open Source)** | $17–$18 / bulan rutin |
| **Privasi Rekaman** | **Zero-Knowledge (Data tidak keluar HP)** | Tersimpan di server vendor |
| **Penyimpanan Kunci API** | **Android Keystore AES-256-GCM** | Server vendor / teks biasa |
| **Lisensi Perangkat Lunak** | **MIT License** | Lisensi Komersial Terikat |

---

## 5. Repositori Resmi & Lisensi

* **Repositori GitHub:** [`https://github.com/paperalt/sovereign`](https://github.com/paperalt/sovereign)
* **Penulis / Pemilik:** Asmaul Khusna (`@paperalt`)
* **Lisensi:** [MIT License](LICENSE)
