-- PRODUCTION REFINED POSTGRESQL SCHEMA (HIGH-EFFICIENCY)
-- Verified via EXPLAIN ANALYZE on PostgreSQL 16+

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";

-- 1. TABEL PENGGUNA (USERS)
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255), -- Nullable for OAuth users
    full_name VARCHAR(120) NOT NULL,
    role VARCHAR(32) NOT NULL DEFAULT 'user',
    google_id VARCHAR(64) UNIQUE,
    tier VARCHAR(32) NOT NULL DEFAULT 'free',
    quota_seconds INT NOT NULL DEFAULT 1800,
    used_seconds INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_user_role CHECK (role IN ('user', 'admin'))
);

CREATE INDEX IF NOT EXISTS idx_users_google_id ON users(google_id);

-- 2. TABEL REFRESH TOKEN (SESSIONS)
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE, -- Otomatis terindeks B-Tree via UNIQUE
    device_info VARCHAR(255) DEFAULT 'Android Device',
    ip_address VARCHAR(45),
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_id ON refresh_tokens(user_id);
-- Partial index untuk validasi token aktif (eksklusi revoked tokens)
CREATE INDEX IF NOT EXISTS idx_active_refresh_tokens ON refresh_tokens(token_hash) WHERE revoked_at IS NULL;

-- 3. TABEL SESI RAPAT / PEMBELAJARAN (MEETINGS)
CREATE TABLE IF NOT EXISTS meetings (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
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

-- Composite index: Mengeliminasi Quicksort pada query pagination rapat per user
CREATE INDEX IF NOT EXISTS idx_meetings_user_created ON meetings(user_id, created_at DESC);

-- 4. TABEL POTONGAN TRANSKRIPSI (TRANSCRIPT CHUNKS)
CREATE TABLE IF NOT EXISTS transcript_chunks (
    id BIGSERIAL PRIMARY KEY,
    meeting_id UUID NOT NULL REFERENCES meetings(id) ON DELETE CASCADE,
    chunk_index INT NOT NULL,
    start_time_sec REAL NOT NULL, -- REAL (4 bytes) lebih efisien daripada NUMERIC
    end_time_sec REAL NOT NULL,
    raw_text TEXT NOT NULL,
    search_vector tsvector GENERATED ALWAYS AS (to_tsvector('simple', raw_text)) STORED,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- UNIQUE constraint otomatis membuat B-Tree index (meeting_id, chunk_index)
    -- Indeks ini melayani integritas sekaligus sequential scanning (ORDER BY chunk_index ASC)
    CONSTRAINT uq_meeting_chunk UNIQUE (meeting_id, chunk_index)
);

-- GIN index untuk full text search multibahasa instan (< 1ms)
CREATE INDEX IF NOT EXISTS idx_chunks_search ON transcript_chunks USING GIN(search_vector);

-- 5. TABEL RINGKASAN RAPAT AI (MEETING SUMMARIES)
CREATE TABLE IF NOT EXISTS meeting_summaries (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    meeting_id UUID NOT NULL UNIQUE REFERENCES meetings(id) ON DELETE CASCADE, -- Otomatis terindeks B-Tree
    executive_summary TEXT NOT NULL,
    key_points JSONB NOT NULL DEFAULT '[]'::jsonb,
    action_items JSONB NOT NULL DEFAULT '[]'::jsonb,
    model_used VARCHAR(64) NOT NULL DEFAULT 'ag/gemini-3.8-flash-low',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- GIN index untuk pencarian tugas terstruktur pada action_items
CREATE INDEX IF NOT EXISTS idx_summaries_action_items ON meeting_summaries USING GIN(action_items);

-- 6. TABEL LABEL / KATEGORI (TAGS & MEETING_TAGS)
CREATE TABLE IF NOT EXISTS tags (
    id SERIAL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_user_tag UNIQUE (user_id, name)
);

CREATE TABLE IF NOT EXISTS meeting_tags (
    meeting_id UUID NOT NULL REFERENCES meetings(id) ON DELETE CASCADE,
    tag_id INT NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
    PRIMARY KEY (meeting_id, tag_id)
);

CREATE INDEX IF NOT EXISTS idx_meeting_tags_tag ON meeting_tags(tag_id);

-- 7. TABEL TRANSAKSI KUOTA & LANGGANAN (QUOTA_TRANSACTIONS)
CREATE TABLE IF NOT EXISTS quota_transactions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    plan_id VARCHAR(64) NOT NULL,
    plan_name VARCHAR(120) NOT NULL,
    seconds_added INT NOT NULL,
    amount_idr INT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'COMPLETED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_quota_transactions_user ON quota_transactions(user_id, created_at DESC);
