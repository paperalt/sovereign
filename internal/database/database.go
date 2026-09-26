package database

import (
	"database/sql"
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"time"

	_ "github.com/jackc/pgx/v5/stdlib"
	_ "modernc.org/sqlite"
)

// OpenDatabase initializes a SQL database connection pool.
func OpenDatabase(driver, dsn string) (*sql.DB, error) {
	driverName := driver
	if driver == "postgres" || driver == "postgresql" || driver == "pgx" {
		driverName = "pgx"
	} else if driver == "sqlite" || driver == "sqlite3" {
		driverName = "sqlite"
		if dsn != ":memory:" && !strings.HasPrefix(dsn, "file::memory:") {
			if dir := filepath.Dir(dsn); dir != "" && dir != "." {
				_ = os.MkdirAll(dir, 0755)
			}
		}
	}

	db, err := sql.Open(driverName, dsn)
	if err != nil {
		return nil, fmt.Errorf("failed to open database (%s): %w", driverName, err)
	}

	db.SetMaxOpenConns(50)
	db.SetMaxIdleConns(10)
	db.SetConnMaxLifetime(10 * time.Minute)

	if err := db.Ping(); err != nil {
		return nil, fmt.Errorf("failed to ping database: %w", err)
	}

	return db, nil
}

// Migrate executes initial table schemas for SQLite or PostgreSQL.
func Migrate(db *sql.DB, driver string) error {
	var statements []string

	if driver == "sqlite" || driver == "sqlite3" {
		statements = []string{
			`CREATE TABLE IF NOT EXISTS users (
				id TEXT PRIMARY KEY,
				email TEXT UNIQUE NOT NULL,
				password_hash TEXT,
				full_name TEXT NOT NULL,
				role TEXT NOT NULL DEFAULT 'user',
				google_id TEXT UNIQUE,
				tier TEXT NOT NULL DEFAULT 'free',
				quota_seconds INTEGER NOT NULL DEFAULT 1800,
				used_seconds INTEGER NOT NULL DEFAULT 0,
				created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
				updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
			);`,
			`CREATE TABLE IF NOT EXISTS refresh_tokens (
				id TEXT PRIMARY KEY,
				user_id TEXT NOT NULL,
				token_hash TEXT UNIQUE NOT NULL,
				device_info TEXT DEFAULT 'Android Device',
				ip_address TEXT,
				expires_at DATETIME NOT NULL,
				revoked_at DATETIME,
				created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
				FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
			);`,
			`CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_id ON refresh_tokens(user_id);`,
			`CREATE TABLE IF NOT EXISTS meetings (
				id TEXT PRIMARY KEY,
				user_id TEXT NOT NULL,
				title TEXT NOT NULL,
				language TEXT NOT NULL DEFAULT 'id',
				target_language TEXT NOT NULL DEFAULT '',
				status TEXT NOT NULL DEFAULT 'IN_PROGRESS',
				started_at DATETIME DEFAULT CURRENT_TIMESTAMP,
				ended_at DATETIME,
				duration_sec REAL DEFAULT 0.0,
				summary TEXT DEFAULT '',
				created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
				updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
				FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
			);`,
			`CREATE INDEX IF NOT EXISTS idx_meetings_user_created ON meetings(user_id, created_at DESC);`,
			`CREATE TABLE IF NOT EXISTS transcript_chunks (
				id INTEGER PRIMARY KEY AUTOINCREMENT,
				meeting_id TEXT NOT NULL,
				chunk_index INTEGER NOT NULL,
				start_time_sec REAL NOT NULL,
				end_time_sec REAL NOT NULL,
				raw_text TEXT NOT NULL,
				created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
				UNIQUE(meeting_id, chunk_index),
				FOREIGN KEY (meeting_id) REFERENCES meetings(id) ON DELETE CASCADE
			);`,
			`CREATE INDEX IF NOT EXISTS idx_chunks_meeting_id ON transcript_chunks(meeting_id);`,
			`CREATE TABLE IF NOT EXISTS meeting_summaries (
				id TEXT PRIMARY KEY,
				meeting_id TEXT UNIQUE NOT NULL,
				executive_summary TEXT NOT NULL,
				key_points TEXT NOT NULL DEFAULT '[]',
				action_items TEXT NOT NULL DEFAULT '[]',
				model_used TEXT NOT NULL DEFAULT 'ag/gemini-3.8-flash-low',
				created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
				FOREIGN KEY (meeting_id) REFERENCES meetings(id) ON DELETE CASCADE
			);`,
			`CREATE TABLE IF NOT EXISTS tags (
				id INTEGER PRIMARY KEY AUTOINCREMENT,
				user_id TEXT NOT NULL,
				name TEXT NOT NULL,
				created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
				UNIQUE(user_id, name),
				FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
			);`,
			`CREATE TABLE IF NOT EXISTS meeting_tags (
				meeting_id TEXT NOT NULL,
				tag_id INTEGER NOT NULL,
				PRIMARY KEY (meeting_id, tag_id),
				FOREIGN KEY (meeting_id) REFERENCES meetings(id) ON DELETE CASCADE,
				FOREIGN KEY (tag_id) REFERENCES tags(id) ON DELETE CASCADE
			);`,
			`CREATE TABLE IF NOT EXISTS transcript_groups (
				id TEXT PRIMARY KEY,
				user_id TEXT NOT NULL,
				name TEXT NOT NULL,
				description TEXT DEFAULT '',
				color TEXT DEFAULT '#38BDF8',
				created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
				updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
				FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
			);`,
			`CREATE INDEX IF NOT EXISTS idx_transcript_groups_user ON transcript_groups(user_id, created_at DESC);`,
			`CREATE TABLE IF NOT EXISTS quota_transactions (
				id TEXT PRIMARY KEY,
				user_id TEXT NOT NULL,
				plan_id TEXT NOT NULL,
				plan_name TEXT NOT NULL,
				seconds_added INTEGER NOT NULL,
				amount_idr INTEGER NOT NULL,
				status TEXT NOT NULL DEFAULT 'COMPLETED',
				created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
				FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
			);`,
			`CREATE INDEX IF NOT EXISTS idx_quota_transactions_user ON quota_transactions(user_id, created_at DESC);`,
			`CREATE TABLE IF NOT EXISTS prepaid_vouchers (
				code TEXT PRIMARY KEY,
				duration_seconds INTEGER NOT NULL,
				plan_name TEXT NOT NULL,
				tier TEXT NOT NULL DEFAULT 'pro',
				max_uses INTEGER NOT NULL DEFAULT 1000,
				used_count INTEGER NOT NULL DEFAULT 0,
				is_active INTEGER NOT NULL DEFAULT 1,
				created_at DATETIME DEFAULT CURRENT_TIMESTAMP
			);`,
			`INSERT OR IGNORE INTO prepaid_vouchers (code, duration_seconds, plan_name, tier, max_uses)
			 VALUES 
			   ('FREEPLUS30', 1800, 'Voucher Bonus 30 Menit', 'starter', 1000),
			   ('PRO60M', 3600, 'Voucher Paket Starter 60 Menit', 'starter', 1000),
			   ('PRO300M', 18000, 'Voucher Paket Pro 300 Menit (5 Jam)', 'pro', 1000),
			   ('VIP1200M', 72000, 'Voucher Akses Bulanan VIP 1.200 Menit', 'pro', 1000);`,
		}
	} else {
		// PostgreSQL DDL (Refined & High-Efficiency)
		statements = []string{
			`CREATE EXTENSION IF NOT EXISTS "uuid-ossp";`,
			`CREATE EXTENSION IF NOT EXISTS "pg_trgm";`,
			`CREATE TABLE IF NOT EXISTS users (
				id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
				email VARCHAR(255) NOT NULL UNIQUE,
				password_hash VARCHAR(255),
				full_name VARCHAR(120) NOT NULL,
				role VARCHAR(32) NOT NULL DEFAULT 'user',
				google_id VARCHAR(64) UNIQUE,
				tier VARCHAR(32) NOT NULL DEFAULT 'free',
				quota_seconds INT NOT NULL DEFAULT 1800,
				used_seconds INT NOT NULL DEFAULT 0,
				created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
				updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
				CONSTRAINT chk_user_role CHECK (role IN ('user', 'admin'))
			);`,
			`ALTER TABLE users ADD COLUMN IF NOT EXISTS tier VARCHAR(32) NOT NULL DEFAULT 'free';`,
			`ALTER TABLE users ADD COLUMN IF NOT EXISTS quota_seconds INT NOT NULL DEFAULT 1800;`,
			`ALTER TABLE users ADD COLUMN IF NOT EXISTS used_seconds INT NOT NULL DEFAULT 0;`,
			`CREATE TABLE IF NOT EXISTS quota_transactions (
				id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
				user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
				plan_id VARCHAR(64) NOT NULL,
				plan_name VARCHAR(120) NOT NULL,
				seconds_added INT NOT NULL,
				amount_idr INT NOT NULL,
				status VARCHAR(32) NOT NULL DEFAULT 'COMPLETED',
				created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
			);`,
			`CREATE INDEX IF NOT EXISTS idx_quota_transactions_user ON quota_transactions(user_id, created_at DESC);`,
			`CREATE TABLE IF NOT EXISTS prepaid_vouchers (
				code VARCHAR(64) PRIMARY KEY,
				duration_seconds INT NOT NULL,
				plan_name VARCHAR(120) NOT NULL,
				tier VARCHAR(32) NOT NULL DEFAULT 'pro',
				max_uses INT NOT NULL DEFAULT 1000,
				used_count INT NOT NULL DEFAULT 0,
				is_active BOOLEAN NOT NULL DEFAULT TRUE,
				created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
			);`,
			`INSERT INTO prepaid_vouchers (code, duration_seconds, plan_name, tier, max_uses)
			 VALUES 
			   ('FREEPLUS30', 1800, 'Voucher Bonus 30 Menit', 'starter', 1000),
			   ('PRO60M', 3600, 'Voucher Paket Starter 60 Menit', 'starter', 1000),
			   ('PRO300M', 18000, 'Voucher Paket Pro 300 Menit (5 Jam)', 'pro', 1000),
			   ('VIP1200M', 72000, 'Voucher Akses Bulanan VIP 1.200 Menit', 'pro', 1000)
			 ON CONFLICT (code) DO NOTHING;`,
			`CREATE TABLE IF NOT EXISTS refresh_tokens (
				id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
				user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
				token_hash VARCHAR(64) NOT NULL UNIQUE,
				device_info VARCHAR(255) DEFAULT 'Android Device',
				ip_address VARCHAR(45),
				expires_at TIMESTAMPTZ NOT NULL,
				revoked_at TIMESTAMPTZ,
				created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
			);`,
			`CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_id ON refresh_tokens(user_id);`,
			`CREATE INDEX IF NOT EXISTS idx_active_refresh_tokens ON refresh_tokens(token_hash) WHERE revoked_at IS NULL;`,
			`CREATE TABLE IF NOT EXISTS meetings (
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
			);`,
			`CREATE INDEX IF NOT EXISTS idx_meetings_user_created ON meetings(user_id, created_at DESC);`,
			`CREATE TABLE IF NOT EXISTS transcript_chunks (
				id BIGSERIAL PRIMARY KEY,
				meeting_id UUID NOT NULL REFERENCES meetings(id) ON DELETE CASCADE,
				chunk_index INT NOT NULL,
				start_time_sec REAL NOT NULL,
				end_time_sec REAL NOT NULL,
				raw_text TEXT NOT NULL,
				search_vector tsvector GENERATED ALWAYS AS (to_tsvector('simple', raw_text)) STORED,
				created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
				CONSTRAINT uq_meeting_chunk UNIQUE (meeting_id, chunk_index)
			);`,
			`CREATE INDEX IF NOT EXISTS idx_chunks_search ON transcript_chunks USING GIN(search_vector);`,
			`CREATE TABLE IF NOT EXISTS meeting_summaries (
				id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
				meeting_id UUID NOT NULL UNIQUE REFERENCES meetings(id) ON DELETE CASCADE,
				executive_summary TEXT NOT NULL,
				key_points JSONB NOT NULL DEFAULT '[]'::jsonb,
				action_items JSONB NOT NULL DEFAULT '[]'::jsonb,
				model_used VARCHAR(64) NOT NULL DEFAULT 'ag/gemini-3.8-flash-low',
				created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
			);`,
			`CREATE INDEX IF NOT EXISTS idx_summaries_action_items ON meeting_summaries USING GIN(action_items);`,
			`CREATE TABLE IF NOT EXISTS tags (
				id SERIAL PRIMARY KEY,
				user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
				name VARCHAR(64) NOT NULL,
				created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
				CONSTRAINT uq_user_tag UNIQUE (user_id, name)
			);`,
			`CREATE TABLE IF NOT EXISTS meeting_tags (
				meeting_id UUID NOT NULL REFERENCES meetings(id) ON DELETE CASCADE,
				tag_id INT NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
				PRIMARY KEY (meeting_id, tag_id)
			);`,
			`CREATE INDEX IF NOT EXISTS idx_meeting_tags_tag ON meeting_tags(tag_id);`,
			`CREATE TABLE IF NOT EXISTS transcript_groups (
				id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
				user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
				name VARCHAR(120) NOT NULL,
				description TEXT DEFAULT '',
				color VARCHAR(16) NOT NULL DEFAULT '#38BDF8',
				created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
				updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
			);`,
			`CREATE INDEX IF NOT EXISTS idx_transcript_groups_user ON transcript_groups(user_id, created_at DESC);`,
		}
	}

	for _, stmt := range statements {
		if _, err := db.Exec(stmt); err != nil {
			return fmt.Errorf("migration error executing [%s]: %w", stmt, err)
		}
	}

	if driver == "sqlite" || driver == "sqlite3" {
		_, _ = db.Exec("ALTER TABLE users ADD COLUMN google_id TEXT UNIQUE;")
		_, _ = db.Exec("ALTER TABLE meetings ADD COLUMN group_id TEXT REFERENCES transcript_groups(id) ON DELETE SET NULL;")
	} else {
		_, _ = db.Exec("ALTER TABLE users ADD COLUMN IF NOT EXISTS google_id VARCHAR(64) UNIQUE;")
		_, _ = db.Exec("ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;")
		_, _ = db.Exec("CREATE INDEX IF NOT EXISTS idx_users_google_id ON users(google_id);")
		_, _ = db.Exec("ALTER TABLE meetings ADD COLUMN IF NOT EXISTS group_id UUID REFERENCES transcript_groups(id) ON DELETE SET NULL;")
		_, _ = db.Exec("CREATE INDEX IF NOT EXISTS idx_meetings_group_id ON meetings(group_id);")
	}

	return nil
}
