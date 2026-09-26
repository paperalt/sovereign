# Desain Basis Data Produksi (PostgreSQL 16+)
**Skema Relasional, Pengindeksan Full-Text GIN, Isolasi Multi-Tenant Anti-IDOR & Buku Kas Transaksi**

[![Release Version](https://img.shields.io/badge/Release-v2.1.0%20(Build%2042)-38BDF8?style=flat-square)](https://gate.eclipsegate.my.id/downloads/transcribe-core.apk)

---

> 🌐 **Bahasa:** **Bahasa Indonesia** | [English Version](DATABASE_DESIGN.md)

---

## 1. Ringkasan Eksekutif

Lapisan persistensi data didukung oleh kluster PostgreSQL 16 (dengan SQLite 3 untuk pengujian integrasi lokal). Skema ini menerapkan otorisasi ketat di level baris untuk mencegah Insecure Direct Object References (IDOR), indeks Full-Text Search GIN pada generated column `tsvector`, pengelompokan folder hirarkis (`transcript_groups`), indeks komposit paginasi `(user_id, created_at DESC)`, serta buku kas kuota dan voucher atomik.

---

## 2. Entity-Relationship Diagram (ERD)

```
       +-------------------------------+
       |             users             |
       +-------------------------------+
       | PK  id (UUID)                 |
       |     email (UNIQUE)            |<------------------+
       |     password_hash (NULLABLE)  |                   |
       |     full_name                 |                   |
       |     role                      |                   |
       |     google_id (UNIQUE, INDEX) |                   |
       |     tier                      |                   |
       |     quota_seconds             |                   |
       |     used_seconds              |                   |
       +-------------------------------+                   |
          | 1          | 1             | 1                 |
          |            |               |                   |
          | N          | N             | N                 |
+---------------------+  |  +--------------------+         |
|   refresh_tokens    |  |  | transcript_groups  |         |
+---------------------+  |  +--------------------+         |
| PK  id (UUID)       |  |  | PK  id (UUID)      |         |
| FK  user_id         |  |  | FK  user_id        |<----+   |
|     token_hash      |  |  |     name           |     |   |
|     device_info     |  |  |     description    |     |   |
|     expires_at      |  |  |     color          |     |   |
|     revoked_at      |  |  +--------------------+     |   |
+---------------------+  |            | 1              |   |
                         |            |                |   |
                         |            | N              |   |
                         |  +--------------------+     |   |
                         +->|      meetings      |     |   |
                            +--------------------+     |   |
                            | PK  id (UUID)      |     |   |
                            | FK  user_id        |     |   |
                            | FK  group_id       |-----+   |
                            |     title          |         |
                            |     language       |         |
                            |     target_lang    |         |
                            |     status         |         |
                            |     started_at     |         |
                            |     ended_at       |         |
                            |     duration_sec   |         |
                            +--------------------+         |
                               | 1         | 1             |
                               |           |               |
                               | N         | 1             |
                +--------------+           +---------------+
                |                                          |
                ▼ N                                        ▼ 1
   +---------------------------+             +---------------------------+
   |     transcript_chunks     |             |     meeting_summaries     |
   +---------------------------+             +---------------------------+
   | PK  id (BIGSERIAL)        |             | PK  id (UUID)             |
   | FK  meeting_id            |             | FK  meeting_id (UNIQUE)   |
   |     chunk_index           |             |     executive_summary     |
   |     start_time_sec (REAL) |             |     key_points (JSONB)    |
   |     end_time_sec (REAL)   |             |     action_items (JSONB)  |
   |     raw_text              |             |     model_used            |
   |     search_vector (TSV)   |             |     created_at            |
   +---------------------------+             +---------------------------+

          +-------------------------------+
          |       quota_transactions      |
          +-------------------------------+
          | PK  id (BIGSERIAL)            |
          | FK  user_id                   |
          |     amount_seconds            |
          |     balance_after             |
          |     type                      |
          |     reference_id              |
          |     created_at                |
          +-------------------------------+

          +-------------------------------+
          |       prepaid_vouchers        |
          +-------------------------------+
          | PK  id (UUID)                 |
          |     code (UNIQUE)             |
          |     plan_tier                 |
          |     duration_seconds          |
          |     max_uses                  |
          |     used_count                |
          |     is_active                 |
          |     created_by                |
          |     created_at                |
          |     expires_at                |
          +-------------------------------+
```

---

## 3. Definisi Struktur Tabel Produksi (DDL)

```sql
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";

-- 1. TABEL PENGGUNA (USERS)
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

CREATE INDEX IF NOT EXISTS idx_users_google_id ON users(google_id) WHERE google_id IS NOT NULL;

-- 2. TABEL TOKEN REFRESH (REFRESH_TOKENS)
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) UNIQUE NOT NULL,
    device_info VARCHAR(255),
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_id ON refresh_tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_active_refresh_tokens ON refresh_tokens(token_hash) WHERE revoked_at IS NULL;

-- 3. BUKU KAS TRANSAKSI KUOTA
CREATE TABLE IF NOT EXISTS quota_transactions (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    amount_seconds BIGINT NOT NULL,
    balance_after BIGINT NOT NULL,
    type VARCHAR(32) NOT NULL,
    reference_id VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_quota_tx_user ON quota_transactions(user_id, created_at DESC);

-- 4. VOUCHER PRABAYAR
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

-- 5. TABEL FOLDER / GRUP TRANSKRIP
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

CREATE INDEX IF NOT EXISTS idx_groups_user_id ON transcript_groups(user_id);

-- 6. TABEL SESI RAPAT (MEETINGS)
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

CREATE INDEX IF NOT EXISTS idx_meetings_user_created ON meetings(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_meetings_group_id ON meetings(group_id);

-- 7. TABEL POTONGAN TRANSKRIPSI (TRANSCRIPT_CHUNKS)
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

CREATE INDEX IF NOT EXISTS idx_chunks_meeting_id ON transcript_chunks(meeting_id);
CREATE INDEX IF NOT EXISTS idx_chunks_search ON transcript_chunks USING GIN(search_vector);

-- 8. TABEL RINGKASAN TERSTRUKTUR (MEETING_SUMMARIES)
CREATE TABLE IF NOT EXISTS meeting_summaries (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    meeting_id UUID NOT NULL UNIQUE REFERENCES meetings(id) ON DELETE CASCADE,
    executive_summary TEXT NOT NULL,
    key_points JSONB NOT NULL DEFAULT '[]',
    action_items JSONB NOT NULL DEFAULT '[]',
    model_used VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```
