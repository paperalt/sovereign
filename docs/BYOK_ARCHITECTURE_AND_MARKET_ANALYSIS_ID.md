# Cetak Biru Arsitektur Kunci Pribadi (BYOK) & Analisis Pasar Global
**Transcribe Core — Real-Time Speech-to-Text & In-Meeting Inquiry Engine**  
*Spesifikasi Sistem Kunci Pribadi, Evaluasi Rate Limit Model AI, dan Analisis Komparatif Kompetitor*

[![Release Version](https://img.shields.io/badge/Release-v2.1.0%20(Build%2042)-38BDF8?style=flat-square)](https://gate.eclipsegate.my.id/downloads/transcribe-core.apk)

---

> 🌐 **Bahasa:** **Bahasa Indonesia** | [English Version](BYOK_ARCHITECTURE_AND_MARKET_ANALYSIS.md)

---

## 1. Eksekutif & Latar Belakang Strategis

Ekosistem transkripsi dan asisten rapat berbasis AI global sedang mengalami pergeseran paradigma (*paradigm shift*). Selama beberapa tahun terakhir, pasar didominasi oleh platform *Closed-Source SaaS* berlangganan mahal seperti Otter.ai ($17/bulan) dan Fireflies.ai ($18/bulan). Model ini memiliki dua kelemahan mendasar:
1. **Beban Biaya Berulang (*Subscription Fatigue*):** Mahasiswa, akademisi, dan profesional keberatan membayar langganan bulanan tetap jika intensitas rapat/kuliah mereka fluktuatif.
2. **Kekhawatiran Privasi Data (*Data Sovereignty*):** Audio pembicaraan rahasia dan materi kuliah disimpan di server pihak ketiga yang tidak transparan.

Untuk mengatasi hal tersebut, *Transcribe Core* mengadopsi model **Hybrid: Managed Voucher + Bring Your Own Key (BYOK)**.
* Pengguna umum dapat menggunakan infrastruktur server terkelola berbasis kuota voucher.
* Pengguna tingkat lanjut (*power users* / mahasiswa teknik) dapat memasukkan API Key pribadi mereka (Groq, Gemini, OpenAI) secara cuma-cuma, menikmati transkripsi tanpa batas (*unlimited*), latensi instan 0,3 detik, dan mengurangi biaya komputasi server hingga 0%.

---

## 2. Tolok Ukur Empiris & Batasan Penggunaan Model AI (Rate Limits 2026)

Berdasarkan pengujian teknis langsung di gerbang 9Router (`http://127.0.0.1:20128`) dan audit dokumentasi resmi penyedia AI global, berikut batasan dan performa masing-masing model:

### A. Groq Cloud LPU (`whisper-large-v3-turbo` & `llama-3.3-70b-versatile`)
* **Arsitektur:** Ditenagai chip LPU (*Language Processing Unit*) berbasis perangkat keras khusus.
* **Performa Empiris (Audio Uji 9,2 Detik):**
  * `groq/whisper-large-v3-turbo`: **0,30 detik** (RTF: **0,03x** — 30x lebih cepat dari real-time).
  * `groq/llama-3.3-70b-versatile`: **~1,80 detik** untuk penalaran ringkasan eksekutif penuh.
* **Batasan Penggunaan Resmi (Free Tier Groq):**
  * **RPM (Requests Per Minute):** `20 RPM`
  * **RPD (Requests Per Day):** `2.000 RPD`
  * **ASH (Audio Seconds per Hour):** `7.200 detik/jam` *(setara 2 jam audio per jam)*
  * **ASD (Audio Seconds per Day):** `28.800 detik/hari` *(setara 8 jam audio per hari)*
* **Dampak Sistem:** Sangat ideal untuk Kunci Pribadi perorangan (8 jam rapat gratis setiap hari).

### B. Google Gemini Flash (`ag/gemini-3.8-flash` / `gemini-2.0-flash`)
* **Arsitektur:** Native Multimodal Audio (menerima Base64 WAV langsung tanpa konverter ASR terpisah).
* **Performa Empiris:** Latensi **1,50s – 3,50s** (RTF: 0,35x – 0,45x).
* **Batasan Penggunaan (Free Tier Google AI Studio):**
  * **RPM:** `15 RPM` | **RPD:** `1.500 RPD` | **TPM:** `1.000.000 tokens/menit`
* **Keunggulan:** Jendela konteks raksasa (1.000.000 token) yang memungkinkan analisis transkrip panjang hingga puluhan jam dalam satu sesi ringkasan.

### C. OpenAI Platform (`whisper-1` & `gpt-4o-mini`)
* **Arsitektur:** Standar industri OpenAI REST API.
* **Performa Empiris:** Latensi **1,80s – 2,80s**.
* **Keunggulan:** Akurasi tinggi pada istilah multibahasa dan penyesuaian domain medis/hukum.

---

## 3. Analisis Lanskap Pasar & Keunggulan Kompetitif

| Kriteria Evaluasi | Otter.ai / Fireflies | Meetily / Natively | EchoScribe (Play Store) | **Transcribe Core (Proyek Kita)** |
| :--- | :---: | :---: | :---: | :---: |
| **Model Bisnis** | Langganan mahal ($17-$18/bln) | 100% Free BYOK | Freemium BYOK | **Hybrid (Voucher Murah + BYOK Bebas)** |
| **Platform Target** | Web & Bot Rapat Virtual | Desktop App (Win/Mac) | Android (Basic) | **Android Native (Jetpack Compose)** |
| **Metode Ingestion** | Cloud Ingestion Bot | Local / API | Simpan File $\to$ Upload | **In-Memory Streaming (Dual Pipeline)** |
| **Background Resiliency** | Bergantung Web Browser | Khusus Laptop | Sering di-kill OS | **Foreground Service + WakeLock + AOD** |
| **In-Meeting Inquiry** | Terbatas / Tertutup | Tidak Ada | Tidak Ada | **Auto Question Engine (5m-30m Grounded)** |
| **Kecepatan Transkripsi** | 2,0s - 4,0s | 0,3s (Groq) | Lambat (Batch) | **0,3s (Groq LPU) / 1,5s (Gemini Flash)** |
| **Kedaulatan Kunci** | Kunci disimpan vendor | Kunci di laptop user | Kunci di HP | **Keystore AES-256 (Zero-Knowledge Server)** |

---

## 4. Arsitektur Teknis Kunci Pribadi (Zero-Knowledge Server)

### 1. Brankas Kredensial Android Keystore
Kunci API pribadi pengguna **TIDAK PERNAH** dikirimkan untuk disimpan di basis data server atau log disk:
1. Pengguna memasukkan kunci di aplikasi Android (Groq, Gemini, atau OpenAI).
2. Kunci dienkripsi menggunakan `EncryptedSharedPreferences` dengan algoritma `AES-256-GCM` yang dikunci oleh chip perangkat keras (*Hardware-backed KeyStore*).
3. Saat melakukan transkripsi atau pembuatan ringkasan, kunci dikirimkan melalui header/query TLS terenkripsi langsung ke memori transien Goroutine di server Go.
4. Begitu koneksi soket ditutup, memori Goroutine dimusnahkan oleh runtime garbage collector.

### 2. Pemetaan STT Audio + LLM Reasoning

| Provider | Model Transkripsi (STT) | Model Nalar (Ringkasan & Ide Tanya) | Keunggulan |
| :--- | :--- | :--- | :--- |
| **`DEFAULT`** | `ag/gemini-3.8-flash` | `ag/gemini-3.8-flash` | Menggunakan kuota voucher akun. |
| **`GROQ`** | `whisper-large-v3-turbo` | `llama-3.3-70b-versatile` | Ultra cepat (< 1 detik), gratis 8 jam audio/hari. |
| **`GEMINI`** | `gemini-2.0-flash` | `gemini-2.0-flash` | Konteks rapat panjang hingga 1 juta token. |
| **`OPENAI`** | `whisper-1` | `gpt-4o-mini` | Presisi korporat standar OpenAI. |
