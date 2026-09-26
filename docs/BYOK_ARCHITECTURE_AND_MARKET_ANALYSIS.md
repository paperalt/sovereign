# Bring Your Own Key (BYOK) Architecture & Global Market Analysis
**Transcribe Core — Real-Time Speech-to-Text & In-Meeting Inquiry Engine**  
*BYOK System Architecture, AI Model Rate Limit Benchmarks, and Competitive Landscape Analysis*

[![Release Version](https://img.shields.io/badge/Release-v2.1.0%20(Build%2042)-38BDF8?style=flat-square)](https://gate.eclipsegate.my.id/downloads/transcribe-core.apk)

---

> 🌐 **Language:** **English** | [Versi Bahasa Indonesia](BYOK_ARCHITECTURE_AND_MARKET_ANALYSIS_ID.md)

---

## 1. Executive Summary & Strategic Context

The global AI meeting assistant and transcription ecosystem is undergoing a major paradigm shift. For years, the market has been dominated by expensive closed-source SaaS platforms such as Otter.ai ($17/month) and Fireflies.ai ($18/month). These traditional models present two fundamental pain points:
1. **Subscription Fatigue:** Students, academic researchers, and engineers resist paying high fixed recurring subscriptions when meeting or lecture frequency fluctuates.
2. **Data Sovereignty & Privacy Concerns:** Sensitive corporate audio and university lectures are uploaded to opaque third-party cloud servers.

To resolve these trade-offs, **Transcribe Core** pioneers a **Hybrid: Managed Voucher + Bring Your Own Key (BYOK)** operational model:
* General users can utilize managed server infrastructure funded via affordable prepaid vouchers.
* Technical users, engineering students, and privacy-conscious professionals can plug in their own free personal API keys (Groq, Gemini, OpenAI) to enjoy unlimited transcription, ~0.3s ultra-low latency, and reduce server operational computing costs to zero.

---

## 2. Empirical Benchmarks & AI Model Rate Limits (2026 Audit)

Based on direct empirical stress tests and verified documentation from global AI providers, model boundaries and latencies are established as follows:

### A. Groq Cloud LPU (`whisper-large-v3-turbo` & `llama-3.3-70b-versatile`)
* **Hardware Architecture:** Language Processing Units (LPU) purpose-built for ultra-low latency inference.
* **Empirical Turnaround (9.2s Test Audio):**
  * `groq/whisper-large-v3-turbo`: **0.30s** (Real-Time Factor: **0.03x** — 30x faster than real-time).
  * `groq/llama-3.3-70b-versatile`: **~1.80s** full structured executive summary generation.
* **Official Free-Tier Rate Limits:**
  * **RPM (Requests Per Minute):** `20 RPM`
  * **RPD (Requests Per Day):** `2,000 RPD`
  * **ASH (Audio Seconds per Hour):** `7,200 sec/hour` *(equivalent to 2 hours of audio per hour)*
  * **ASD (Audio Seconds per Day):** `28,800 sec/day` *(equivalent to 8 hours of audio per day)*
* **Architecture Assessment:** Outstanding choice for individual BYOK users (8 hours of free meeting transcription per day).

### B. Google Gemini Flash (`ag/gemini-3.8-flash` / `gemini-2.0-flash`)
* **Hardware Architecture:** Native Multimodal Audio (ingests Base64 WAV directly without standalone ASR converters).
* **Empirical Turnaround:** Latency **1.50s – 3.50s** (RTF: 0.35x – 0.45x).
* **Official Free-Tier Limits (Google AI Studio):**
  * **RPM:** `15 RPM` | **RPD:** `1,500 RPD` | **TPM:** `1,000,000 tokens/min`
* **Key Advantage:** Massive 1,000,000-token context window allowing seamless multi-hour single-prompt document analysis.

### C. OpenAI Platform (`whisper-1` & `gpt-4o-mini`)
* **Hardware Architecture:** Industry-standard REST endpoints.
* **Empirical Turnaround:** Latency **1.80s – 2.80s**.
* **Key Advantage:** Corporate standard accuracy for multi-language technical jargon, medical terminology, and legal dictation.

---

## 3. Competitive Landscape & Architectural Moat

| Evaluation Criteria | Otter.ai / Fireflies | Meetily / Natively | EchoScribe (Mobile) | **Transcribe Core (Our System)** |
| :--- | :---: | :---: | :---: | :---: |
| **Business Model** | Expensive Subscriptions ($17–$18/mo) | 100% Free BYOK | Freemium BYOK | **Hybrid (Affordable Voucher + Unlimited BYOK)** |
| **Target Platforms** | Web & Cloud Meeting Bots | Desktop Apps (Win/Mac) | Android (Basic) | **Android Native (Kotlin 2.0 / Jetpack Compose)** |
| **Ingestion Topology** | Cloud Injection Bots | Local / API | File Upload Batch | **In-Memory Dual Pipeline Streaming (Standard & Adaptive)** |
| **Background Resiliency** | Browser Tab Dependent | Laptop Bound | Frequent OS Kills | **Foreground Service + WakeLock + AOD Support** |
| **In-Meeting Inquiries** | Restricted / Closed | None | None | **Auto Question Suggestion Engine (5m–30m Windows)** |
| **Transcription Latency** | 2.0s – 4.0s | 0.3s (Groq) | Slow Batch | **0.3s (Groq LPU) / 1.5s (Gemini Flash)** |
| **Key Sovereignty** | Stored on Vendor Servers | Stored on Laptop Disk | In Plain App Prefs | **Hardware Keystore AES-256 (Zero-Knowledge Server)** |

---

## 4. Zero-Knowledge Technical Security Architecture

### 1. Android Keystore Hardware Vault
User credentials **NEVER** touch persistent server disks or database tables:
1. User enters API keys locally in the Android application.
2. Credentials are encrypted via `EncryptedSharedPreferences` backed by `MasterKeys.AES256_GCM_SPEC` inside the mobile device's Hardware Security Module (TEE/SE Keystore).
3. During active transcription sessions, keys are transmitted via TLS 1.3 to transient Goroutine memory on the Go server.
4. Upon WebSocket termination, the Goroutine memory is reclaimed by the runtime garbage collector.

### 2. Unified STT & LLM Routing Matrix

| Provider | Audio Transcription (STT) | Reasoning LLM (Summary & Inquiries) | Key Benefit |
| :--- | :--- | :--- | :--- |
| **`DEFAULT`** | `ag/gemini-3.8-flash` | `ag/gemini-3.8-flash` | Uses managed server voucher quota. |
| **`GROQ`** | `whisper-large-v3-turbo` | `llama-3.3-70b-versatile` | Ultra-fast (< 1s), free 8 hours audio/day. |
| **`GEMINI`** | `gemini-2.0-flash` | `gemini-2.0-flash` | Giant 1M token context for long sessions. |
| **`OPENAI`** | `whisper-1` | `gpt-4o-mini` | Enterprise-grade OpenAI reasoning precision. |
