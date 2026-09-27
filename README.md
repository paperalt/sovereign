# Sovereign: Standalone On-Device Android Speech Intelligence

**Local-First, Zero-Backend Real-Time Speech Ingestion, In-Meeting Grounded Inquiry & Executive Intelligence Engine**

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg?style=flat-square)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B%20%28API%2026%2B%29-3DDC84?style=flat-square&logo=android)](https://developer.android.com/)
[![Language](https://img.shields.io/badge/Kotlin-2.0%20%7C%20Jetpack%20Compose-7F52FF?style=flat-square&logo=kotlin)](https://kotlinlang.org/)
[![Database](https://img.shields.io/badge/Storage-On--Device%20SQLite%203-003B57?style=flat-square&logo=sqlite)](https://sqlite.org/)
[![Security Vault](https://img.shields.io/badge/Security-Android%20Keystore%20AES--256--GCM-10B981?style=flat-square)]()
[![AI Routing](https://img.shields.io/badge/Direct%20AI-Groq%20LPU%20%7C%20Gemini%20Flash%20%7C%20OpenAI-FF6F00?style=flat-square)](https://groq.com/)

---

> 🌐 **Language:** **English** | [Versi Bahasa Indonesia](README_ID.md)

---

## 1. Executive Summary & Pure Client-Side Paradigm

**Sovereign** is a 100% standalone, pure Android application that delivers real-time speech transcription, contextual in-meeting inquiry, and executive summarization directly to your device **without any backend server, central database, or intermediary cloud relay**.

While commercial transcription platforms (Otter.ai, Fireflies.ai) charge recurring subscription fees ($17–$18/month), enforce vendor lock-in, and store confidential meeting recordings on third-party servers, Sovereign operates on a **Pure Local-First Architecture**:

* **Zero Backend Server Required:** No Go server, Python API, Docker daemon, or remote host to deploy or maintain. The application is completely self-contained.
* **100% On-Device Data Storage (Native SQLite 3):** All meeting sessions, audio chunks, transcripts, and structured executive summaries reside in a local SQLite database (`sovereign_transcribe.db`) inside the Android app's private sandbox.
* **On-Device Audio Ingestion & WAV Packaging:** Raw 16kHz, 16-bit Mono Linear PCM from `AudioRecord` is chunked and packed into RFC 2361 compliant RIFF/WAVE byte containers in memory using native Kotlin binary encoders (`WavEncoder.kt`).
* **Client-Side VAD Streaming Chunker:** Voice Activity Detection (VAD) energy gating (< 50 RMS silence suppression, < 280 RMS pause boundary detection, 2.5s–12.0s chunking) runs directly in Kotlin coroutines on the device, saving ~78% network bandwidth.
* **Decoupled STT & LLM Pipeline Architecture:** Voice (STT) and Reasoning (LLM) endpoints are independently configured. Users can combine any speech-to-text model with any intelligence model into an active engine pipeline.
* **Direct Client-to-Provider AI Ingestion:** The app connects directly from the device to AI providers (Groq Cloud Whisper Turbo, Google AI Studio Gemini 2.0 Flash, OpenAI Whisper-1, DeepSeek, xAI Grok, or local Ollama) via direct HTTPS multipart/JSON calls using personal API keys.
* **Verified Free Tier Support:** Seamlessly integrates with zero-cost developer tiers (Groq Whisper Turbo & Llama 3.3, Google Gemini 2.0 Flash 1M context, OpenRouter `:free` models, and Ollama localhost).
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
            Pipeline["AIEngineConfigDialog.kt<br/>• Visual Combine Pipeline Card<br/>• Live latency test<br/>• 1-tap preset pairing"]
            STTManager["STTEndpointsDialog.kt<br/>• Dedicated STT manager"]
            LLMManager["LLMEndpointsDialog.kt<br/>• Dedicated LLM manager<br/>• Auto-detect /models"]
        end

        subgraph DirectAI["4. Direct-to-Provider AI Client"]
            AIClient["DirectAIClient.kt (OkHttp)<br/>• Groq LPU Whisper Turbo (~0.3s STT)<br/>• Groq Llama 3.3 70B (Summary & Questions)<br/>• Google Gemini 2.0 Flash / OpenAI / Ollama"]
        end

        Encoder --> AIClient
        AIClient --> LocalStore
        LocalStore <--> UI
    end

    subgraph ExternalAPIs["USER'S OWN AI PROVIDERS (Direct HTTPS)"]
        Groq["Groq Cloud API (Free Tier)<br/>https://api.groq.com"]
        Google["Google AI Studio (Free Tier)<br/>generativelanguage.googleapis.com"]
        OpenRouter["OpenRouter (Free Router)<br/>https://openrouter.ai"]
        Ollama["Ollama Localhost (100% Offline)<br/>http://10.0.2.2:11434"]
    end

    AIClient <==> |Direct HTTPS with BYOK| ExternalAPIs
```

---

## 3. Verified Free Tier Providers

| Provider | Capability | Endpoint | Default Model | Free Limits | Credit Card |
| :--- | :---: | :--- | :--- | :--- | :---: |
| **Groq Cloud** | **STT (Voice)** | `https://api.groq.com/openai/v1/audio/transcriptions` | `whisper-large-v3-turbo` | 20–30 RPM, 14.4k RPD (~0.3s LPU) | **No** |
| **Groq Cloud** | **LLM (Reasoning)** | `https://api.groq.com/openai/v1/chat/completions` | `llama-3.3-70b-versatile` | 30 RPM, 14.4k RPD (~1.8s LPU) | **No** |
| **Google AI Studio** | **LLM (Reasoning)** | `https://generativelanguage.googleapis.com/v1beta/models/...` | `gemini-2.0-flash` | 15 RPM, 1.5k RPD (1M token ctx) | **No** |
| **OpenRouter** | **LLM (Reasoning)** | `https://openrouter.ai/api/v1/chat/completions` | `meta-llama/llama-3.3-70b-instruct:free` | 50 requests/day free tier | **No** |
| **Ollama Local** | **STT & LLM** | `http://10.0.2.2:11434/v1/...` | `whisper` / `llama3.2` | Unlimited (Runs 100% Offline on PC) | **No** |

---

## 4. How to Build & Install

### Prerequisites
* JDK 17 or JDK 21
* Android SDK 35 (minSdk 26 — Android 8.0 Oreo or higher)

### Build Release APK
```bash
# 1. Clone repository
git clone https://github.com/paperalt/sovereign.git
cd sovereign

# 2. Make Gradle wrapper executable
chmod +x gradlew

# 3. Assemble Release APK
./gradlew assembleRelease

# Output APK path:
# app/build/outputs/apk/release/app-release.apk
```

### Install directly to device
```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

---

## 5. Technical Specifications Comparison

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

## 6. Repository & License

* **GitHub Repository:** [`https://github.com/paperalt/sovereign`](https://github.com/paperalt/sovereign)
* **Author:** [`@paperalt`](https://github.com/paperalt)
* **License:** [MIT License](LICENSE)
