# CETAK BIRU ARSITEKTUR: Real-Time Speech Intelligence System
**Pipa Streaming Audio Heterogen Silang-Model & Ekstraksi Intelijen Eksekutif Kelas Enterprise**

[![Release Version](https://img.shields.io/badge/Release-v2.1.0%20(Build%2042)-38BDF8?style=flat-square)](https://gate.eclipsegate.my.id/downloads/transcribe-core.apk)
[![Security Vault](https://img.shields.io/badge/Security-Android%20Keystore%20AES--256--GCM-10B981?style=flat-square)]()
[![Go Backend](https://img.shields.io/badge/Go-1.22%2B-00ADD8?style=flat-square&logo=go)](https://go.dev/)
[![Android Client](https://img.shields.io/badge/Android-Kotlin%202.0%20%7C%20Jetpack%20Compose-3DDC84?style=flat-square&logo=android)](https://developer.android.com/jetpack/compose)

---

> 🌐 **Bahasa:** **Bahasa Indonesia** | [English Version](BLUEPRINT.md)

---

## 1. Ringkasan Eksekutif & Objektif Sistem

Sistem ini dirancang untuk menangani transkripsi audio rapat (*meeting*), perkuliahan, dan percakapan panjang secara *real-time* atau *near real-time* dengan toleransi latensi rendah (< 1 detik) dan integritas data mutlak:
* **Backend Inti:** Go 1.22+ untuk konkurensi tinggi, *memory footprint* minimal (< 17 MB RSS), antrean FIFO sequential channel terisolasi per-koneksi, dan I/O streaming biner in-memory.
* **Klien Mobile:** Android (Kotlin 2.0 / Jetpack Compose) dengan `AudioRecord` 16kHz PCM capture, Foreground Service berproteksi `PARTIAL_WAKE_LOCK`, Always-On Display (AOD), dan brankas kredensial **Android Keystore (AES-256-GCM)**.
* **Heterogeneous Cross-Model AI:**
  * **Audio STT:** Groq LPU Whisper Large Turbo (latensi ~0,3s, bebas kuota server), Google Gemini 2.0 Flash Multimodal, atau OpenAI Whisper-1.
  * **Nalar LLM (Ringkasan & Ide Tanya):** Server Google Gemini 3.8 Flash High (1M token context), Groq Llama 3.3 70B Versatile (latensi ~1,8s pada Groq LPU), Google Gemini 2.0 Flash, atau OpenAI GPT-4o Mini.
* **Lapis Basis Data:** PostgreSQL 16 dengan GIN Index Full-Text Search (`tsvector`), composite index paginasi `(user_id, created_at DESC)`, tabel folder hirarkis `transcript_groups`, dan buku kas transaksi voucher prabayar (dengan fallback SQLite 3 ultra-ringan).

---

## 2. Pipa Transmisi Audio Ganda & Alur Data

```
[ Mikrofon Perangkat Android / AudioRecord 16kHz 16-bit Mono ]
                         │
                         ├──► Mode Standar (Lossless Raw PCM Micro-Batching)
                         │      - 64ms frame (2048 bytes) langsung dikirim via WebSocket
                         │      - Server Chunker: 5.0s - 25.0s (Jeda hening 0.4s)
                         │
                         └──► Mode Streaming Adaptif (Client VAD Gated Compaction)
                                - Evaluasi RMS per-frame (Ambang batas vokal: RMS >= 280.0)
                                - Pre-roll queue 256ms (4 frame) mencegah kepotong fonem awal
                                - Konsolidasi paket 128ms (4096 bytes) mengurangi syscall 50%
                                - Supresi hening (RMS < 280 selama > 380ms hentikan streaming)
                                - Heartbeat {"action":"VAD_SILENCE"} tiap 4s hening (Reset deadline)
                                - Server Fast Chunker: 2.5s - 12.0s (Jeda hening 0.3s)
                         │
                         ▼
[ OkHttp WebSocket Client / TLS 1.3 ]
     WSS: /ws/transcribe?pipeline_mode=adaptive_beta&stt_provider=GROQ&llm_provider=SERVER
                         │
                         ▼
[ Go Ingestion Engine (Goroutine Terisolasi per Client) ]
     │
     ├─► [ RMS Silence & Energy Pre-Filter Guard ] (Discard audio hening < 50 RMS)
     ├─► [ In-Memory RIFF/WAV Container Packer ] (Sample-aligned 16kHz WAV header)
     ├─► [ Sequential FIFO Channel Queue ] (chan *AudioChunk, eliminasi out-of-order)
     │
     ▼
[ Router STT Heterogen ]
     ├──► [ Groq Cloud LPU ] (Whisper Large Turbo ~0.3s) ──► Bebas Kuota Server
     ├──► [ 9Router / Gemini ] (ag/gemini-3.8-flash)     ──► Potong Kuota Voucher
     └──► [ OpenAI Platform ] (whisper-1)                ──► Kunci Mandiri
     │
     ▼
[ Transcript Stitcher & Formatter ]
     ├─► [ PostgreSQL 16 ] (INSERT transcript_chunks via Commit Batch)
     ├─► [ 400-Char Rolling Context Buffer ] (Akumulasi glosarium chunk berikutnya)
     └─► [ WSS Downstream ] (Push event CHUNK_TRANSCRIBED ke UI Android)
     │
     ▼
[ Post-Processing & Executive Intelligence ]
     ├──► [ Auto Question Suggestion Engine ] (GET_QUESTION_SUGGESTIONS, Temp 0.2, >= 35 kata)
     └──► [ Executive Summarizer ] (STOP Action / Auto-Finalize, Zero-Preamble Direct Synthesis)
```

---

## 3. Skema Basis Data Produksi (PostgreSQL 16)

```sql
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 1. Tabel Pengguna Terpusat (Mendukung Password & Google OIDC)
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

-- 2. Buku Kas Transaksi Kuota
CREATE TABLE IF NOT EXISTS quota_transactions (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    amount_seconds BIGINT NOT NULL,
    balance_after BIGINT NOT NULL,
    type VARCHAR(32) NOT NULL, -- 'INITIAL', 'USAGE', 'VOUCHER_REDEEM', 'ADMIN_ADJUST'
    reference_id VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 3. Voucher Prabayar
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

-- 4. Tabel Folder / Grup Transkrip
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

-- 5. Tabel Sesi Rapat / Kuliah
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

-- 6. Tabel Potongan Transkripsi (Chunks)
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

-- 7. Tabel Ringkasan Terstruktur
CREATE TABLE IF NOT EXISTS meeting_summaries (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    meeting_id UUID NOT NULL UNIQUE REFERENCES meetings(id) ON DELETE CASCADE,
    executive_summary TEXT NOT NULL,
    key_points JSONB NOT NULL DEFAULT '[]',
    action_items JSONB NOT NULL DEFAULT '[]',
    model_used VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indeks Kinerja Tinggi
CREATE INDEX IF NOT EXISTS idx_meetings_user_created ON meetings(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_meetings_group_id ON meetings(group_id);
CREATE INDEX IF NOT EXISTS idx_chunks_meeting_id ON transcript_chunks(meeting_id);
CREATE INDEX IF NOT EXISTS idx_chunks_search ON transcript_chunks USING GIN(search_vector);
CREATE INDEX IF NOT EXISTS idx_quota_tx_user ON quota_transactions(user_id, created_at DESC);
```

---

## 4. Struktur Proyek Backend (Go 1.22+)

```
transcribe-backend/
├── cmd/
│   ├── server/                     # Entrypoint daemon produksi HTTP/WSS
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

## 5. Brankas Kredensial Android Keystore (Arsitektur Zero-Knowledge)

* **Enkripsi Kriptografis Lokal:** Kunci pribadi disimpan di `EncryptedSharedPreferences` dengan skema master key `MasterKeys.AES256_GCM_SPEC` yang dilindungi perangkat keras chip keamanan ponsel (Android Keystore).
* **Isolasi Penyimpanan:** Kunci dipartisi per provider:
  * `KEY_PROVIDER_GROQ`: Menyimpan kunci API Groq Cloud (`gsk_...`).
  * `KEY_PROVIDER_GEMINI`: Menyimpan kunci API Google AI Studio (`AIza...`).
  * `KEY_PROVIDER_OPENAI`: Menyimpan kunci API OpenAI Platform (`sk-...`).
* **Siklus Hidup Transien:** Saat sesi rekaman aktif, kunci diteruskan sebagai parameter handshake WSS terenkripsi TLS 1.3, disimpan di RAM goroutine koneksi, dan langsung dibersihkan saat koneksi terputus. Server tidak pernah menuliskan kunci pengguna ke disk atau database.
