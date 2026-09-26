# Sovereign: Pure Standalone Android Speech Intelligence

**Local-First, Zero-Backend Real-Time Speech Ingestion, In-Meeting Grounded Inquiry & Executive Intelligence Engine**

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg?style=flat-square)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B%20%28API%2026%2B%29-3DDC84?style=flat-square&logo=android)](https://developer.android.com/)
[![Language](https://img.shields.io/badge/Kotlin-2.0%20%7C%20Jetpack%20Compose-7F52FF?style=flat-square&logo=kotlin)](https://kotlinlang.org/)
[![Database](https://img.shields.io/badge/Storage-On--Device%20SQLite%203-003B57?style=flat-square&logo=sqlite)](https://sqlite.org/)
[![Key Vault](https://img.shields.io/badge/Security-Android%20Keystore%20AES--256--GCM-10B981?style=flat-square)]()
[![AI Routing](https://img.shields.io/badge/Direct%20AI-Groq%20LPU%20%7C%20Gemini%20Flash%20%7C%20OpenAI-FF6F00?style=flat-square)](https://groq.com/)

---

> 🌐 **Language:** **English** | [Versi Bahasa Indonesia](README_ID.md)

---

## 1. Executive Summary & Pure Client-Side Paradigm

**Sovereign** is a 100% standalone, pure Android application that brings real-time speech transcription, contextual in-meeting inquiry, and executive summarization directly to your mobile device **without any backend server, central database, or intermediary cloud proxy**.

While commercial transcription platforms (Otter.ai, Fireflies.ai) charge $17–$18/month, enforce vendor lock-in, and store confidential meeting recordings on third-party servers, Sovereign operates on a **Pure Local-First Architecture**:

* **Zero Backend Server Required:** No Go server, no Python API, no Docker container, and no remote host to maintain. The application is completely self-contained.
* **100% On-Device Data Storage (Native SQLite 3):** All meeting transcripts, audio chunks, and structured executive summaries are stored in a local SQLite database (`sovereign_transcribe.db`) inside the Android app's private sandbox.
* **On-Device Audio Ingestion & WAV Encoding:** Raw 16kHz, 16-bit Mono Linear PCM from `AudioRecord` is chunked and packed into RFC 2361 compliant RIFF/WAVE byte containers in memory using native Kotlin binary encoders (`WavEncoder.kt`).
* **Client-Side VAD Streaming Chunker:** Voice Activity Detection (VAD) energy gating (< 50 RMS silence suppression, < 280 RMS pause boundary detection, 2.5s–12.0s chunking) runs directly in Kotlin coroutines on the device.
* **Direct Client-to-Provider AI Ingestion:** The app connects directly from the phone to AI providers (Groq Cloud Whisper Turbo, Google AI Studio Gemini 2.0 Flash, or OpenAI Whisper-1) via direct HTTPS multipart/JSON calls using personal API keys.
* **Universal Multi-Endpoint Configuration & Model Auto-Detection:** Users can configure arbitrary custom endpoints and models for Voice (STT) and LLM (Reasoning) independently. The `[DETEKSI]` feature queries the endpoint's `GET /models` to discover and list all available models in real time.
* **Preconfigured Presets via GitHub Raw JSON:** Provider profiles are fetched dynamically from `config/providers.json` on GitHub with offline fallback, enabling one-tap configuration where the user only needs to paste their API key.
* **Hardware-Backed Keystore Vault:** API keys reside exclusively in the **Android Keystore (AES-256-GCM)** via `EncryptedSharedPreferences`. Keys never touch an external server or unencrypted storage.
* **100% Free & Open Source:** Licensed under the permissive **MIT License**.

---

## 2. Standalone Client Architecture

```mermaid
flowchart TD
    subgraph AndroidApp["SOVEREIGN PURE ANDROID APP (No Backend Server)"]
        subgraph Hardware["1. Audio Hardware & Ingestion"]
            Mic["Microphone Input<br/>• AudioRecord 16kHz Mono PCM<br/>• VOICE_RECOGNITION tuning"]
            Chunker["AudioStreamChunker.kt<br/>• Client-Side RMS VAD Gating<br/>• Pause Detection (< 280 RMS)<br/>• Chunk boundaries: 2.5s - 12.0s"]
            Encoder["WavEncoder.kt<br/>• RFC 2361 RIFF/WAVE Packer<br/>• 44-byte Header Injection in RAM"]
            Mic --> Chunker --> Encoder
        end

        subgraph LocalStore["2. On-Device SQLite Storage"]
            SQLite[("Native SQLite 3 DB<br/>• sovereign_transcribe.db<br/>• meetings, transcript_chunks<br/>• transcript_groups, summaries<br/>• Sub-millisecond queries")]
        end

        subgraph UI["3. Jetpack Compose UI"]
            Dashboard["DashboardScreen.kt<br/>• Offline meeting library<br/>• Long-press batch actions<br/>• Local search index"]
            Live["LiveTranscriptionScreen.kt<br/>• Real-time waveform (32 bars)<br/>• Live chunk feed<br/>• In-meeting inquiry dialog"]
            Detail["MeetingDetailScreen.kt<br/>• In-place chunk & full editor<br/>• Summary regeneration<br/>• Markdown export"]
        end

        subgraph DirectAI["4. Direct-to-Provider AI Client"]
            AIClient["DirectAIClient.kt (OkHttp)<br/>• Groq LPU Whisper Turbo (~0.3s STT)<br/>• Groq Llama 3.3 70B (Summary & Questions)<br/>• Google Gemini 2.0 Flash / OpenAI"]
        end

        Encoder --> AIClient
        AIClient --> LocalStore
        LocalStore <--> UI
    end

    subgraph ExternalAPIs["USER'S OWN AI PROVIDERS (Direct HTTPS)"]
        Groq["Groq Cloud API<br/>https://api.groq.com"]
        Google["Google AI Studio<br/>generativelanguage.googleapis.com"]
        OpenAI["OpenAI Platform<br/>api.openai.com"]
    end

    AIClient <==> |Direct HTTPS with BYOK| ExternalAPIs
```

---

## 3. How to Build & Install

### Prerequisites
* JDK 17 or JDK 21
* Android SDK 35 (minSdk 26 — Android 8.0 Oreo or higher)

### Build Debug APK
```bash
# 1. Clone repository
git clone https://github.com/paperalt/sovereign.git
cd sovereign

# 2. Make Gradle wrapper executable
chmod +x gradlew

# 3. Assemble Debug APK
./gradlew assembleDebug

# Output APK path:
# app/build/outputs/apk/debug/app-debug.apk
```

### Install directly to device
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 4. Technical Specifications & Features

| Capability | Sovereign (Pure Android) | Cloud SaaS (Otter.ai, etc.) |
| :--- | :---: | :---: |
| **Backend Server** | **NONE (Pure Android App)** | Proprietary Cloud Gateway |
| **Data Storage** | **100% On-Device (Native SQLite 3)** | Vendor Multi-Tenant Cloud |
| **Audio Encoding** | **On-Device (WavEncoder 16kHz)** | Server-side / Cloud transcode |
| **Network Latency** | **Direct HTTPS (~300ms via Groq)** | High (WebSocket hop + Server queue) |
| **Cost** | **$0 / month (Free & Open Source)** | $17–$18 / month recurring |
| **Data Privacy** | **Zero-Knowledge (Data never leaves phone)** | Shared with SaaS vendor |
| **API Key Storage** | **Android Keystore AES-256-GCM** | Stored on vendor servers |
| **License** | **MIT License** | Proprietary Commercial |

---

## 5. Repository & License

* **GitHub Repository:** [`https://github.com/paperalt/sovereign`](https://github.com/paperalt/sovereign)
* **Author:** Asmaul Khusna (`@paperalt`)
* **License:** [MIT License](LICENSE)
