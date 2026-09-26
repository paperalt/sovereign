# Sovereign Speech Intelligence (Sovereign Core)

**Enterprise Local-First Real-Time Speech Ingestion, In-Meeting Grounded Inquiry & Executive Intelligence Stack**

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg?style=flat-square)](LICENSE)
[![Security Vault](https://img.shields.io/badge/Security-Android%20Keystore%20AES--256--GCM-10B981?style=flat-square)]()
[![Go Backend](https://img.shields.io/badge/Go-1.22%2B%20%7C%20Zero--CGO%20SQLite-00ADD8?style=flat-square&logo=go)](https://go.dev/)
[![Android Client](https://img.shields.io/badge/Android-Kotlin%202.0%20%7C%20Jetpack%20Compose-3DDC84?style=flat-square&logo=android)](https://developer.android.com/jetpack/compose)
[![Storage](https://img.shields.io/badge/Storage-Local%20SQLite%203%20FTS5-003B57?style=flat-square&logo=sqlite)](https://sqlite.org/)
[![AI Orchestration](https://img.shields.io/badge/AI%20Engines-Groq%20LPU%20%7C%20Gemini%20Flash%20%7C%20OpenAI-FF6F00?style=flat-square)](https://groq.com/)

---

> 🌐 **Language:** **English** | [Versi Bahasa Indonesia](README_ID.md)

---

## 1. Executive Summary & The Sovereign Paradigm

**Sovereign Speech Intelligence** is a fully open-source, local-first speech-to-intelligence ecosystem designed to return complete data sovereignty back to the user. While existing commercial SaaS platforms (Otter.ai, Fireflies.ai) charge high recurring subscriptions ($17–$18/month), enforce vendor lock-in, and store confidential meeting recordings on proprietary multi-tenant cloud servers, Sovereign Core shifts **100% of endpoints and data storage directly to the user's side**.

### Core Architectural Tenets:
* **100% User-Side Storage (Embedded SQLite 3):** All meeting transcripts, audio chunks, and structured executive summaries are stored in an embedded SQLite database (`./data/sovereign.db` or `~/.sovereign/sovereign.db`) using zero-CGO pure Go driver (`modernc.org/sqlite`). Full-text search is powered by SQLite FTS, providing sub-millisecond query retrieval without requiring any external database server.
* **100% User-Controlled Endpoints:** Operates as a lightweight, single-binary daemon on `127.0.0.1:8080` (or `0.0.0.0:8080` for private home labs, LANs, and Tailscale networks). Zero external user tracking, zero centralized telemetry, and zero mandatory cloud gateways.
* **Direct BYOK AI Routing:** Users supply their own personal API keys (Groq Cloud, Google AI Studio, OpenAI, or local Ollama). Audio is transcribed via **Groq LPU Whisper Large Turbo** at ~0.3s latency (free 8 hours/day) and summarized via **Groq Llama 3.3 70B** or **Google Gemini 2.0 Flash**.
* **Zero-Knowledge Hardware Vault:** Keys entered on the mobile client reside exclusively in hardware-backed **Android Keystore (AES-256-GCM)** and are transmitted strictly via TLS 1.3 to transient Goroutine memory, immediately cleared upon socket termination.
* **Dual Ingestion Pipelines:**
  * *Standard Pipeline:* Lossless 16kHz 16-bit Mono PCM streamed with server-side RMS VAD chunking (5.0s–25.0s).
  * *Adaptive Low-Latency Pipeline:* Client-side energy gating (RMS 280.0 silence suppression, saving ~78.2% mobile data), 256ms pre-roll queue, and 128ms (4096 bytes) packet compaction.
* **In-Meeting Grounded Inquiry Engine:** Generates sharp, actionable questions during live discussions across sliding time windows (5m, 15m, 30m, full) backed by verbatim quote references (`context_ref`) to eliminate hallucinations.
* **100% Open Source:** Released permissively under the **MIT License** for unrestricted personal, academic, and enterprise self-hosting.

---

## 2. System Architecture & Topology

```mermaid
flowchart TD
    subgraph UserDevice["1. CLIENT RUNTIME (Android / Desktop / Web)"]
        UI["Industrial Dark UI<br/>• Jetpack Compose Material 3<br/>• Long-Press Batch Multi-Select<br/>• In-Place Chunk & Document Editor"]
        Vault["Hardware Keystore Vault<br/>• Android Keystore AES-256-GCM<br/>• Transient TLS Handshake Transmission"]
        AudioPump["Audio Streaming Engine<br/>• AudioRecord 16kHz Mono PCM<br/>• Client VAD Suppression (< 280 RMS)<br/>• 256ms Pre-Roll & 128ms Compaction"]
        UI --> Vault
        UI --> AudioPump
    end

    subgraph LocalDaemon["2. SOVEREIGN ENGINE (User-Side Go Daemon :8080)"]
        WSGateway["WebSocket Ingestion Gateway<br/>• WSS: /ws/transcribe<br/>• Direct In-Memory PCM Ingestion"]
        Chunker["In-Memory RMS VAD Stream Chunker<br/>• Standard Mode: 5.0s - 25.0s<br/>• Adaptive Mode: 2.5s - 12.0s<br/>• Dead-Air RMS Silence Guard (< 50 RMS)"]
        FIFO["Sequential FIFO Queue<br/>• Strict Monotonic Processing Order"]
        InquiryEngine["Auto Question Suggestion Engine<br/>• Grounded Anti-Hallucination (Temp 0.2)<br/>• Verbatim Quote Extraction (context_ref)"]
        ExecutiveSummarizer["Executive Intelligence Synthesizer<br/>• Zero-Preamble Synthesis Harness<br/>• Executive Summary, Key Points & Action Items"]

        WSGateway --> Chunker
        Chunker --> FIFO
    end

    subgraph UserStorage["3. USER-SIDE LOCAL STORAGE"]
        SQLite[("Embedded SQLite 3 (sovereign.db)<br/>• Zero CGO (modernc.org/sqlite)<br/>• Local Full-Text Search (FTS)<br/>• Sub-Millisecond In-Memory Queries")]
    end

    subgraph DirectAI["4. DIRECT USER BYOK ROUTING"]
        Groq["Groq Cloud LPU<br/>• Whisper Large Turbo (~0.3s STT)<br/>• Llama 3.3 70B Versatile (~1.8s Nalar)"]
        Gemini["Google AI Studio<br/>• Gemini 2.0 Flash (1M Token Context)"]
        OpenAI["OpenAI Platform<br/>• Whisper-1 & GPT-4o Mini"]
        LocalModel["Local Inference (Optional)<br/>• Ollama / Local Whisper.cpp"]
    end

    UserDevice <==> |Local WSS / HTTP :8080| LocalDaemon
    LocalDaemon <==> |Direct Disk I/O| SQLite
    LocalDaemon --> DirectAI
```

---

## 3. Quick Start: Single-Binary Execution

### Option A: Direct Go Binary (Zero External Dependencies)
```bash
# 1. Clone repository
git clone https://github.com/paperalt/sovereign.git
cd sovereign-speech-intelligence

# 2. Build single binary
go build -o bin/sovereign-server ./cmd/server

# 3. Run daemon (runs with embedded SQLite on 0.0.0.0:8080)
./bin/sovereign-server
```

### Option B: Run via Docker Compose (Portable Local Stack)
```bash
# Start lightweight containerized daemon
docker compose -f docker-compose.sqlite.yml up -d

# Verify daemon status
curl -s http://127.0.0.1:8080/health
# Output: {"status":"ok"}
```

---

## 4. Multi-Target Disaster Recovery & Backup

Sovereign Core includes automated multi-target backup and restore scripts (`scripts/backup.sh` and `scripts/restore.sh`):

```bash
# 1. Backup to Local Directory or External USB/HDD
./scripts/backup.sh /var/backups/sovereign

# 2. Backup to Remote Home Server via SSH / SCP
./scripts/backup.sh user@192.168.1.100:/mnt/storage/backups

# 3. Backup to Private Cloud via Rclone (S3, Cloudflare R2, Google Drive)
./scripts/backup.sh r2:my-bucket/sovereign-backups

# 4. Instant One-Command Restoration
./scripts/restore.sh /var/backups/sovereign
```

---

## 5. Technical Specifications Matrix

| Metric / Parameter | Sovereign Speech Intelligence | Traditional Cloud SaaS (Otter.ai, etc.) |
| :--- | :---: | :---: |
| **Data Storage Location** | **100% User-Side (Local SQLite 3)** | Third-Party Centralized Cloud |
| **API Endpoints** | **Localhost / User-Hosted Daemon** | Vendor-Controlled Gateways |
| **Cost & Business Model** | **Free & Open Source (MIT License)** | $17–$18 / month recurring |
| **Transcription Latency** | **~0.30s (Groq LPU Whisper Turbo)** | 2.0s – 5.0s |
| **Data Bandwidth** | **~25 MB / hour (Adaptive VAD Gating)** | 60–120 MB / hour |
| **In-Meeting Inquiries** | **Grounded AI with Citations (`context_ref`)** | None / Static Post-Meeting |
| **Key Vault Security** | **Android Keystore AES-256-GCM** | Vendor Stored / Plaintext |
| **Offline / LAN Capability** | **Full Local Support (LAN / Tailscale)** | Requires Internet & Vendor Login |

---

## 6. Official Repository & License

* **GitHub Repository:** [`https://github.com/paperalt/sovereign`](https://github.com/paperalt/sovereign)
* **Author:** Asmaul Khusna (`@paperalt`)
* **License:** [MIT License](LICENSE) — Free for personal, academic, and commercial self-hosting.
