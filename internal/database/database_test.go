package database

import (
	"context"
	"os"
	"testing"
	"time"
)

func TestMigrate_SQLite(t *testing.T) {
	db, err := OpenDatabase("sqlite", ":memory:")
	if err != nil {
		t.Fatalf("failed to open sqlite: %v", err)
	}
	defer db.Close()

	if err := Migrate(db, "sqlite"); err != nil {
		t.Fatalf("failed to migrate sqlite: %v", err)
	}

	// Verify tables exist
	tables := []string{"users", "refresh_tokens", "meetings", "transcript_chunks", "meeting_summaries", "tags", "meeting_tags"}
	for _, tbl := range tables {
		var name string
		err := db.QueryRow("SELECT name FROM sqlite_master WHERE type='table' AND name=?", tbl).Scan(&name)
		if err != nil || name != tbl {
			t.Errorf("expected table %s in sqlite, got %v", tbl, err)
		}
	}
}

func TestMigrate_PostgreSQL(t *testing.T) {
	pgDSN := os.Getenv("TEST_POSTGRES_DSN")
	if pgDSN == "" {
		pgDSN = "postgres://postgres:postgres@127.0.0.1:5432/transcribe_test?sslmode=disable"
	}

	db, err := OpenDatabase("postgres", pgDSN)
	if err != nil {
		t.Skipf("skipping postgres test (container not reachable): %v", err)
		return
	}
	defer db.Close()

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	if err := db.PingContext(ctx); err != nil {
		t.Skipf("skipping postgres test (ping failed): %v", err)
		return
	}

	if err := Migrate(db, "postgres"); err != nil {
		t.Fatalf("failed to migrate postgres: %v", err)
	}

	// Verify tables exist in pg_tables
	tables := []string{"users", "refresh_tokens", "meetings", "transcript_chunks", "meeting_summaries", "tags", "meeting_tags"}
	for _, tbl := range tables {
		var name string
		err := db.QueryRow("SELECT tablename FROM pg_tables WHERE schemaname = 'public' AND tablename = $1", tbl).Scan(&name)
		if err != nil || name != tbl {
			t.Errorf("expected table %s in postgres, got %v", tbl, err)
		}
	}
}
