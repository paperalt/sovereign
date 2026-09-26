# BLUEPRINT: Real-Time Speech Intelligence System
**Enterprise Heterogeneous Cross-Model Audio Streaming & Executive Intelligence Stack**

[![Release Version](https://img.shields.io/badge/Release-v2.1.0%20(Build%2042)-38BDF8?style=flat-square)](https://gate.eclipsegate.my.id/downloads/transcribe-core.apk)
[![Security Vault](https://img.shields.io/badge/Security-Android%20Keystore%20AES--256--GCM-10B981?style=flat-square)]()
[![Go Backend](https://img.shields.io/badge/Go-1.22%2B-00ADD8?style=flat-square&logo=go)](https://go.dev/)
[![Android Client](https://img.shields.io/badge/Android-Kotlin%202.0%20%7C%20Jetpack%20Compose-3DDC84?style=flat-square&logo=android)](https://developer.android.com/jetpack/compose)

---

> 🌐 **Language:** **English** | [Versi Bahasa Indonesia](BLUEPRINT_ID.md)

---

## 1. Executive Summary & System Objectives

The system is engineered to handle real-time or near-real-time audio transcription of corporate meetings, academic lectures, and long-form discussions with sub-second turnaround latency (< 1s) and zero data loss:
* **Backend Core:** Go 1.22+ for high concurrency, ultra-compact memory footprint (< 17 MB RSS), isolated per-connection FIFO sequential channels, and in-memory sample-aligned WAV wrapping.
* **Mobile Client:** Android native (Kotlin 2.0 / Jetpack Compose) with 16kHz PCM capture, Foreground Service protected by `PARTIAL_WAKE_LOCK`, Always-On Display (AOD), and hardware-backed **Android Keystore (AES-256-GCM)** vault.
* **Heterogeneous Cross-Model AI:**
  * **Audio STT:** Groq LPU Whisper Large Turbo (~0.3s latency, server quota bypassed), Google Gemini 2.0 Flash Multimodal, or OpenAI Whisper-1.
  * **LLM Reasoning (Summary & Inquiry):** Server Gemini 3.8 Flash High (1M token context), Groq Llama 3.3 70B Versatile (~1.8s LPU latency), Google Gemini 2.0 Flash, or OpenAI GPT-4o Mini.
* **Database Layer:** PostgreSQL 16 with GIN Index Full-Text Search (`tsvector`), composite pagination indexes `(user_id, created_at DESC)`, hierarchical `transcript_groups`, and atomic prepaid voucher ledgers (with SQLite 3 fallback).

---

## 2. Dual Pipeline Audio Streaming & Data Flow

```
[ Android Microphone / AudioRecord 16kHz 16-bit Mono ]
                         │
                         ├──► Standard Mode (Lossless Raw PCM Micro-Batching)
                         │      - 64ms frame (2048 bytes) pushed directly over WebSocket
                         │      - Server Chunker: 5.0s - 25.0s (Silence pause 0.4s)
                         │
                         └──► Adaptive Streaming Mode (Client VAD Gated Compaction)
                                - Per-frame RMS energy gating (Vocal threshold: RMS >= 280.0)
                                - 256ms pre-roll queue (4 frames) prevents opening phoneme clipping
                                - 128ms packet compaction (4096 bytes) cuts network syscalls by 50%
                                - Silence suppression (RMS < 280 for > 380ms halts streaming)
                                - Heartbeat {"action":"VAD_SILENCE"} every 4s silence (Resets deadline)
                                - Server Fast Chunker: 2.5s - 12.0s (Silence pause 0.3s)
                         │
                         ▼
[ OkHttp WebSocket Client / TLS 1.3 ]
     WSS: /ws/transcribe?pipeline_mode=adaptive_beta&stt_provider=GROQ&llm_provider=SERVER
                         │
                         ▼
[ Go Ingestion Engine (Isolated Goroutine per Client) ]
     │
     ├─► [ RMS Silence & Energy Pre-Filter Guard ] (Discard dead air < 50 RMS)
     ├─► [ In-Memory RIFF/WAV Container Packer ] (Sample-aligned 16kHz WAV header)
     ├─► [ Sequential FIFO Channel Queue ] (chan *AudioChunk, eliminates out-of-order execution)
     │
     ▼
[ Heterogeneous STT Router ]
     ├──► [ Groq Cloud LPU ] (Whisper Large Turbo ~0.3s) ──► Server Quota Bypassed
     ├──► [ 9Router / Gemini ] (ag/gemini-3.8-flash)     ──► Deduct Voucher Quota
     └──► [ OpenAI Platform ] (whisper-1)                ──► BYOK Self-Keyed
     │
     ▼
[ Transcript Stitcher & Formatter ]
     ├─► [ PostgreSQL 16 ] (INSERT transcript_chunks via Commit Batch)
     ├─► [ 400-Char Rolling Context Buffer ] (Accumulate vocabulary for next chunk)
     └─► [ WSS Downstream ] (Push CHUNK_TRANSCRIBED event to Android UI)
     │
     ▼
[ Post-Processing & Executive Intelligence ]
     ├──► [ Auto Question Suggestion Engine ] (GET_QUESTION_SUGGESTIONS, Temp 0.2, >= 35 words)
     └──► [ Executive Summarizer ] (STOP Action / Auto-Finalize, Zero-Preamble Direct Synthesis)
```

---

## 3. Production Database Schema (PostgreSQL 16)

```sql
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 1. Centralized Users Table (Supports Password & Google OIDC)
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255),
    full_name VARCHAR(120) NOT NULL,
    role VARCHAR(32) NOT NULL DEFAULT 'user',
    google_id VARCHAR(128) UNIQUE,
    tier VARCHAR(32) NOT NULL DEFAULT 'starter',
    quota_seconds BIGINT NOT NULL DEFAULT 1800,
    used_seconds BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 2. Quota Transaction Ledger
CREATE TABLE IF NOT EXISTS quota_transactions (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    amount_seconds BIGINT NOT NULL,
    balance_after BIGINT NOT NULL,
    type VARCHAR(32) NOT NULL, -- 'INITIAL', 'USAGE', 'VOUCHER_REDEEM', 'ADMIN_ADJUST'
    reference_id VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 3. Prepaid Vouchers
CREATE TABLE IF NOT EXISTS prepaid_vouchers (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    code VARCHAR(64) UNIQUE NOT NULL,
    plan_tier VARCHAR(32) NOT NULL,
    duration_seconds BIGINT NOT NULL,
    max_uses INT NOT NULL DEFAULT 1,
    used_count INT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by VARCHAR(64) NOT NULL DEFAULT 'ADMIN',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ
);

-- 4. Hierarchical Transcript Groups / Folders
CREATE TABLE IF NOT EXISTS transcript_groups (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(128) NOT NULL,
    description TEXT DEFAULT '',
    color VARCHAR(16) NOT NULL DEFAULT '#38BDF8',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_user_group_name UNIQUE (user_id, name)
);

-- 5. Meeting Sessions
CREATE TABLE IF NOT EXISTS meetings (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    group_id UUID REFERENCES transcript_groups(id) ON DELETE SET NULL,
    title VARCHAR(255) NOT NULL,
    language VARCHAR(16) NOT NULL DEFAULT 'id',
    target_language VARCHAR(16) DEFAULT '',
    status VARCHAR(32) NOT NULL DEFAULT 'IN_PROGRESS',
    started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ended_at TIMESTAMPTZ,
    duration_sec REAL DEFAULT 0.0,
    summary TEXT DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_meeting_status CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'FAILED'))
);

-- 6. Transcript Audio Chunks
CREATE TABLE IF NOT EXISTS transcript_chunks (
    id BIGSERIAL PRIMARY KEY,
    meeting_id UUID NOT NULL REFERENCES meetings(id) ON DELETE CASCADE,
    chunk_index INT NOT NULL,
    start_time_sec REAL NOT NULL,
    end_time_sec REAL NOT NULL,
    raw_text TEXT NOT NULL,
    search_vector tsvector GENERATED ALWAYS AS (to_tsvector('simple', raw_text)) STORED,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_meeting_chunk UNIQUE (meeting_id, chunk_index)
);

-- 7. Structured Intelligence Summaries
CREATE TABLE IF NOT EXISTS meeting_summaries (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    meeting_id UUID NOT NULL UNIQUE REFERENCES meetings(id) ON DELETE CASCADE,
    executive_summary TEXT NOT NULL,
    key_points JSONB NOT NULL DEFAULT '[]',
    action_items JSONB NOT NULL DEFAULT '[]',
    model_used VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- High-Performance Indexes
CREATE INDEX IF NOT EXISTS idx_meetings_user_created ON meetings(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_meetings_group_id ON meetings(group_id);
CREATE INDEX IF NOT EXISTS idx_chunks_meeting_id ON transcript_chunks(meeting_id);
CREATE INDEX IF NOT EXISTS idx_chunks_search ON transcript_chunks USING GIN(search_vector);
CREATE INDEX IF NOT EXISTS idx_quota_tx_user ON quota_transactions(user_id, created_at DESC);
```

---

## 4. Backend Project Architecture (Go 1.22+)

```
transcribe-backend/
├── cmd/
│   ├── server/                     # Production HTTP/WSS daemon entrypoint
│   └── stress_test/                # Adversarial load & stress testing runner
├── internal/
│   ├── audio/                      # In-memory RMS VAD chunker, Fast Chunker & WAV packer
│   │   ├── chunker.go
│   │   └── wav.go
│   ├── config/                     # Environment loader (JWT, Admin Secret, Ports)
│   ├── database/                   # Dual-driver connection pooling (pgxpool & sqlite)
│   ├── handler/                    # REST API & WebSocket Controller
│   │   ├── ai_validate_handler.go  # Live API key 0-token ping verification
│   │   ├── auth.go                 # Google OIDC & Refresh token handler
│   │   ├── group_handler.go        # Hierarchical transcript groups & batch actions
│   │   ├── meeting_handler.go      # Meeting CRUD, chunk/doc editing, and rename
│   │   ├── subscription_handler.go # Multi-tier voucher & admin voucher management
│   │   └── ws_transcribe.go        # Dual-pipeline WebSocket ingestion & auto-finalizer
│   ├── middleware/                 # Argon2id/JWT dual-token auth, CORS, Rate Limit
│   ├── model/                      # Domain entities, DTOs & response contracts
│   ├── repository/                 # Database persistence, GIN search & atomic transactions
│   └── service/                    # AI clients orchestration
│       ├── byok_client.go          # Multi-provider adapter (Groq, Gemini, OpenAI)
│       ├── cleaner.go              # Zero-Preamble regex sanitizers & fallback parser
│       ├── gemini_client.go        # Server Gemini client multimodal with RMS guard
│       ├── google_auth.go          # Google OIDC token validator
│       └── question_engine.go      # Auto Question Suggestion Engine (grounded)
├── pkg/
│   └── token/                      # Argon2id password hasher & JWT claim manager
├── scripts/                        # Disaster recovery, encrypted backup & migration scripts
└── test/
    ├── eval_harness/               # Automated quantitative AI evaluation framework
    └── integration/                # End-to-end security & adaptive pipeline suites
```

---

## 5. Android Keystore Hardware Vault (Zero-Knowledge Architecture)

* **Cryptographic Local Storage:** User private keys are secured in `EncryptedSharedPreferences` governed by `MasterKeys.AES256_GCM_SPEC` backed by mobile hardware security modules (Android Keystore).
* **Storage Isolation:** Dedicated partitions per provider:
  * `KEY_PROVIDER_GROQ`: Holds Groq Cloud API Key (`gsk_...`).
  * `KEY_PROVIDER_GEMINI`: Holds Google AI Studio API Key (`AIza...`).
  * `KEY_PROVIDER_OPENAI`: Holds OpenAI Platform API Key (`sk-...`).
* **Transient Memory Lifecycle:** Keys are transmitted only during active WSS handshake over TLS 1.3, kept purely in volatile Goroutine RAM, and purged upon socket termination. Keys are never written to disk or database.
