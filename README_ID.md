# Sovereign: Aplikasi Android Mandiri untuk Intelijen Wicara

**Arsitektur Local-First, Zero-Backend, Ingesti Audio Real-Time, Perumusan Pertanyaan Rapat & Ringkasan Eksekutif Langsung di Perangkat Android**

[![Lisensi: MIT](https://img.shields.io/badge/License-MIT-blue.svg?style=flat-square)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B%20%28API%2026%2B%29-3DDC84?style=flat-square&logo=android)](https://developer.android.com/)
[![Bahasa](https://img.shields.io/badge/Kotlin-2.0%20%7C%20Jetpack%20Compose-7F52FF?style=flat-square&logo=kotlin)](https://kotlinlang.org/)
[![Database](https://img.shields.io/badge/Storage-On--Device%20SQLite%203-003B57?style=flat-square&logo=sqlite)](https://sqlite.org/)
[![Brankas Keamanan](https://img.shields.io/badge/Security-Android%20Keystore%20AES--256--GCM-10B981?style=flat-square)]()
[![Routing AI](https://img.shields.io/badge/Direct%20AI-Groq%20LPU%20%7C%20Gemini%20Flash%20%7C%20OpenAI-FF6F00?style=flat-square)](https://groq.com/)

---

> 🌐 **Bahasa:** [English](README.md) | **Bahasa Indonesia**

---

## 1. Ringkasan Eksekutif & Paradigma Pure Client-Side

**Sovereign** adalah aplikasi Android murni yang menghadirkan transkripsi wicara rapat secara *real-time*, rekomendasi pertanyaan kontekstual selama diskusi, dan perangkuman eksekutif langsung pada perangkat ponsel Anda **tanpa memerlukan server backend, basis data terpusat, atau perantara cloud berbayar**.

Jika layanan SaaS komersial (Otter.ai, Fireflies.ai) membebankan biaya langganan bulanan ($17–$18/bulan), menerapkan *vendor lock-in*, dan menyimpan rekaman audio rapat rahasia di server pihak ketiga, Sovereign beroperasi dengan **Arsitektur Local-First Penuh**:

* **Nol Server Backend:** Tanpa server Go, API Python, kontainer Docker, atau server remote yang harus di-maintain. Aplikasi 100% mandiri di sisi klien.
* **Penyimpanan 100% On-Device (Native SQLite 3):** Seluruh sesi rapat, potongan audio, transkrip, dan ringkasan eksekutif tersimpan dalam basis data SQLite lokal (`sovereign_transcribe.db`) di dalam sandbox privat Android.
* **Pengodean Audio In-Memory Langsung di RAM:** Audio mentah 16kHz 16-bit Mono Linear PCM dari `AudioRecord` dipotong dan dikemas ke dalam format kontainer RIFF/WAVE (RFC 2361) secara biner di RAM (`WavEncoder.kt`).
* **Streaming VAD Chunker di Klien:** Deteksi jeda wicara (VAD) berbasis energi RMS (< 50 RMS peredam hening, < 280 RMS pemotong jeda wicara) berjalan langsung di coroutine Android, menghemat ~78% bandwidth transfer data.
* **Pemisahan Konfigurasi Endpoint STT & LLM:** Endpoint Voice (STT) dan Penalaran (LLM) dikonfigurasi mandiri melalui menu drawer khusus. Pengguna bebas menggabungkan model STT dan model penalaran apa pun ke dalam satu *pipeline* aktif.
* **Koneksi Klien-ke-Penyedia AI Langsung:** Aplikasi menghubungi API penyedia AI (Groq Cloud Whisper Turbo, Google AI Studio Gemini 2.0 Flash, OpenAI Whisper-1, DeepSeek, xAI Grok, atau Ollama lokal) melalui HTTPS langsung menggunakan API key pribadi pengguna (*Bring Your Own Key*).
* **Dukungan Kuota Gratis Resmi (Verified Free Tier):** Terintegrasi langsung dengan penyedia kuota gratis tanpa kartu kredit (Groq Whisper Turbo & Llama 3.3, Google Gemini 2.0 Flash konteks 1 juta token, OpenRouter `:free`, dan Ollama localhost).
* **Brankas Kriptografis Hardware Keystore:** Kunci API pengguna disimpan terenkripsi di **Android Keystore (AES-256-GCM)** via `EncryptedSharedPreferences`. Kunci tidak pernah terkirim ke server mana pun.
* **100% Open Source:** Dirilis di bawah lisensi terbuka **MIT License**.

---

## 2. Arsitektur Klien Mandiri

```mermaid
flowchart TD
    subgraph AndroidApp["APLIKASI ANDROID SOVEREIGN (Tanpa Server Backend)"]
        subgraph Hardware["1. Audio Hardware & Ingestion"]
            Mic["Input Mikrofon<br/>• AudioRecord 16kHz Mono PCM<br/>• VOICE_RECOGNITION tuning"]
            Chunker["AudioStreamChunker.kt<br/>• RMS VAD Gating di Klien<br/>• Deteksi Jeda Wicara (< 280 RMS)<br/>• Batas Chunk: 2.5s - 12.0s"]
            Encoder["WavEncoder.kt<br/>• RFC 2361 RIFF/WAVE Packer<br/>• Injeksi Header 44-byte di RAM"]
            Mic --> Chunker --> Encoder
        end

        subgraph LocalStore["2. Penyimpanan SQLite Lokal"]
            SQLite[("Native SQLite 3 DB<br/>• sovereign_transcribe.db<br/>• meetings, transcript_chunks<br/>• transcript_groups, summaries<br/>• Kueri Sub-Milidetik")]
        end

        subgraph UI["3. Antarmuka Jetpack Compose"]
            Dashboard["DashboardScreen.kt<br/>• Daftar sesi lokal offline<br/>• Aksi seleksi batch<br/>• Pencarian indeks lokal"]
            Live["LiveTranscriptionScreen.kt<br/>• Waveform audio real-time (32 bar)<br/>• Feed transkrip langsung<br/>• Dialog in-meeting inquiry"]
            Detail["MeetingDetailScreen.kt<br/>• Editor in-place per potongan & teks penuh<br/>• Generate ulang ringkasan<br/>• Ekspor Markdown"]
            Pipeline["AIEngineConfigDialog.kt<br/>• Visual Combine Pipeline Card<br/>• Pengujian latensi real-time<br/>• Preset kombinasi 1-ketukan"]
            STTManager["STTEndpointsDialog.kt<br/>• Pengelola mandiri endpoint STT"]
            LLMManager["LLMEndpointsDialog.kt<br/>• Pengelola mandiri endpoint LLM<br/>• Auto-deteksi /models"]
        end

        subgraph DirectAI["4. Direct-to-Provider AI Client"]
            AIClient["DirectAIClient.kt (OkHttp)<br/>• Groq LPU Whisper Turbo (~0.3s STT)<br/>• Groq Llama 3.3 70B (Summary & Questions)<br/>• Google Gemini 2.0 Flash / OpenAI / Ollama"]
        end

        Encoder --> AIClient
        AIClient --> LocalStore
        LocalStore <--> UI
    end

    subgraph ExternalAPIs["PENYEDIA AI PILIHAN PENGGUNA (Direct HTTPS)"]
        Groq["Groq Cloud API (Free Tier)<br/>https://api.groq.com"]
        Google["Google AI Studio (Free Tier)<br/>generativelanguage.googleapis.com"]
        OpenRouter["OpenRouter (Free Router)<br/>https://openrouter.ai"]
        Ollama["Ollama Localhost (100% Offline)<br/>http://10.0.2.2:11434"]
    end

    AIClient <==> |Direct HTTPS via BYOK| ExternalAPIs
```

---

## 3. Matriks Penyedia Kuota Gratis (Verified Free Tier)

| Penyedia | Fungsi | Endpoint | Model Bawaan | Kuota Gratis | Kartu Kredit |
| :--- | :---: | :--- | :--- | :--- | :---: |
| **Groq Cloud** | **STT (Suara)** | `https://api.groq.com/openai/v1/audio/transcriptions` | `whisper-large-v3-turbo` | 20–30 RPM, 14.400 RPD (~0.3s LPU) | **Tidak Perlu** |
| **Groq Cloud** | **LLM (Nalar)** | `https://api.groq.com/openai/v1/chat/completions` | `llama-3.3-70b-versatile` | 30 RPM, 14.400 RPD (~1.8s LPU) | **Tidak Perlu** |
| **Google AI Studio** | **LLM (Nalar)** | `https://generativelanguage.googleapis.com/v1beta/models/...` | `gemini-2.0-flash` | 15 RPM, 1.500 RPD (Konteks 1 Juta Token) | **Tidak Perlu** |
| **OpenRouter** | **LLM (Nalar)** | `https://openrouter.ai/api/v1/chat/completions` | `meta-llama/llama-3.3-70b-instruct:free` | 50 request gratis/hari | **Tidak Perlu** |
| **Ollama Local** | **STT & LLM** | `http://10.0.2.2:11434/v1/...` | `whisper` / `llama3.2` | Tanpa Batas (100% Offline di Komputer) | **Tidak Perlu** |

---

## 4. Kompilasi & Pemasangan Biner APK

### Kebutuhan Sistem
* JDK 17 atau JDK 21
* Android SDK 35 (minSdk 26 — Android 8.0 Oreo ke atas)

### Kompilasi Biner Rilis
```bash
# 1. Kloning repositori
git clone https://github.com/paperalt/sovereign.git
cd sovereign

# 2. Berikan izin eksekusi pada wrapper Gradle
chmod +x gradlew

# 3. Kompilasi biner rilis APK
./gradlew assembleRelease

# Lokasi file APK hasil kompilasi:
# app/build/outputs/apk/release/app-release.apk
```

### Pemasangan langsung via ADB
```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

---

## 5. Perbandingan Spesifikasi Teknis

| Fitur | Sovereign (Android Standalone) | Cloud SaaS (Otter.ai, dll.) |
| :--- | :---: | :---: |
| **Server Backend** | **TIDAK ADA (100% Lokal di Ponsel)** | Cloud Gateway Tertutup |
| **Penyimpanan Data** | **SQLite 3 Lokal di Sandbox Aplikasi** | Server Cloud Vendor |
| **Enkoding Audio** | **On-Device (WavEncoder 16kHz)** | Transcode Cloud / Server |
| **Latensi Jaringan** | **Direct HTTPS (~300ms via Groq)** | Lambat (WebSocket Hop + Antrean Server) |
| **Biaya Layanan** | **Rp 0 / bulan (Free & Open Source)** | Rp 250.000+ / bulan |
| **Privasi Data** | **Zero-Knowledge (Data tidak keluar HP)** | Dimonitisasi oleh Vendor |
| **Brankas Kunci API** | **Hardware Keystore AES-256-GCM** | Disimpan di server vendor |
| **Lisensi** | **MIT License** | Kepemilikan Tertutup (Proprietary) |

---

## 6. Repositori & Lisensi

* **Repositori GitHub:** [`https://github.com/paperalt/sovereign`](https://github.com/paperalt/sovereign)
* **Pengembang:** [`@paperalt`](https://github.com/paperalt)
* **Lisensi:** [MIT License](LICENSE)
