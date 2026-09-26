package repository

import (
	"context"
	"encoding/json"
	"testing"
	"time"

	"github.com/paperalt/sovereign/internal/database"
)

func setupTestDB(t *testing.T) *databaseTestFixture {
	db, err := database.OpenDatabase("sqlite", ":memory:")
	if err != nil {
		t.Fatalf("failed to open in-memory sqlite: %v", err)
	}

	if err := database.Migrate(db, "sqlite"); err != nil {
		t.Fatalf("failed to migrate test db: %v", err)
	}

	return &databaseTestFixture{
		userRepo:       NewUserRepository(db),
		tokenRepo:      NewTokenRepository(db),
		meetingRepo:    NewMeetingRepository(db),
		transcriptRepo: NewTranscriptRepository(db),
		summaryRepo:    NewSummaryRepository(db),
	}
}

type databaseTestFixture struct {
	userRepo       UserRepository
	tokenRepo      TokenRepository
	meetingRepo    MeetingRepository
	transcriptRepo TranscriptRepository
	summaryRepo    SummaryRepository
}

func TestUserRepository_CRUD(t *testing.T) {
	fixture := setupTestDB(t)
	ctx := context.Background()

	email := "homura@mitakihara.sec"
	pwdHash := "$argon2id$v=19$test_hash"
	fullName := "Akemi Homura"

	// 1. Create
	user, err := fixture.userRepo.Create(ctx, email, pwdHash, fullName)
	if err != nil {
		t.Fatalf("failed to create user: %v", err)
	}
	if user.ID == "" || user.Email != email || user.Role != "user" {
		t.Errorf("invalid user fields: %+v", user)
	}

	// 2. GetByEmail
	byEmail, err := fixture.userRepo.GetByEmail(ctx, email)
	if err != nil {
		t.Fatalf("failed to get user by email: %v", err)
	}
	if byEmail.ID != user.ID || byEmail.FullName != fullName {
		t.Errorf("user mismatch: %+v", byEmail)
	}

	// 3. GetByID
	byID, err := fixture.userRepo.GetByID(ctx, user.ID)
	if err != nil {
		t.Fatalf("failed to get user by ID: %v", err)
	}
	if byID.Email != email {
		t.Errorf("email mismatch: %s", byID.Email)
	}

	// 4. Duplicate create error
	_, err = fixture.userRepo.Create(ctx, email, pwdHash, "Duplicate")
	if err == nil {
		t.Errorf("expected error on duplicate email creation")
	}
}

func TestTokenRepository_Lifecycle(t *testing.T) {
	fixture := setupTestDB(t)
	ctx := context.Background()

	user, _ := fixture.userRepo.Create(ctx, "token.test@sec.local", "hash", "Token User")

	rawToken := "sample-refresh-token-xyz-12345"
	expiresAt := time.Now().Add(24 * time.Hour)

	// 1. Create token
	token, err := fixture.tokenRepo.CreateRefreshToken(ctx, user.ID, rawToken, "Pixel 8 Pro", "192.168.1.50", expiresAt)
	if err != nil {
		t.Fatalf("failed to create refresh token: %v", err)
	}
	if token.UserID != user.ID {
		t.Errorf("user_id mismatch in token")
	}

	// 2. Get active token
	active, err := fixture.tokenRepo.GetActiveRefreshToken(ctx, rawToken)
	if err != nil {
		t.Fatalf("failed to get active token: %v", err)
	}
	if active.ID != token.ID {
		t.Errorf("token ID mismatch")
	}

	// 3. Revoke token
	if err := fixture.tokenRepo.RevokeRefreshToken(ctx, rawToken); err != nil {
		t.Fatalf("failed to revoke token: %v", err)
	}

	// 4. Get revoked token (must return ErrTokenNotFound)
	_, err = fixture.tokenRepo.GetActiveRefreshToken(ctx, rawToken)
	if err != ErrTokenNotFound {
		t.Errorf("expected ErrTokenNotFound for revoked token, got %v", err)
	}
}

func TestMeetingAndTranscript_Flow(t *testing.T) {
	fixture := setupTestDB(t)
	ctx := context.Background()

	userA, _ := fixture.userRepo.Create(ctx, "usera@sec.local", "hash", "User A")
	userB, _ := fixture.userRepo.Create(ctx, "userb@sec.local", "hash", "User B")

	// 1. User A creates meeting
	meeting, err := fixture.meetingRepo.Create(ctx, userA.ID, "Rapat Arsitektur Keamanan", "id", "")
	if err != nil {
		t.Fatalf("failed to create meeting: %v", err)
	}
	if meeting.Status != "IN_PROGRESS" {
		t.Errorf("expected IN_PROGRESS, got %s", meeting.Status)
	}
	if meeting.Language != "id" {
		t.Errorf("expected language 'id', got %s", meeting.Language)
	}

	// 2. Tenant Isolation: User B cannot get User A's meeting
	_, err = fixture.meetingRepo.GetByID(ctx, meeting.ID, userB.ID)
	if err != ErrMeetingNotFound {
		t.Errorf("security violation: User B accessed User A's meeting, err=%v", err)
	}

	// 3. Add Transcript Chunks
	_, err = fixture.transcriptRepo.AddChunk(ctx, meeting.ID, 0, 0.0, 12.5, "Pembukaan sesi rapat koordinasi kriptografi.")
	if err != nil {
		t.Fatalf("failed to add chunk 1: %v", err)
	}

	_, err = fixture.transcriptRepo.AddChunk(ctx, meeting.ID, 1, 12.5, 25.0, "Evaluasi arsitektur streaming berhasil.")
	if err != nil {
		t.Fatalf("failed to add chunk 2: %v", err)
	}

	// 4. Full Text Search test
	searchResults, err := fixture.transcriptRepo.SearchChunks(ctx, userA.ID, meeting.ID, "kriptografi", 10)
	if err != nil {
		t.Fatalf("failed to search chunks: %v", err)
	}
	if len(searchResults) != 1 {
		t.Errorf("expected 1 search result for 'kriptografi', got %d", len(searchResults))
	}

	// 5. Save & Retrieve Structured Summary
	kp := json.RawMessage(`["Poin 1: Kriptografi stabil", "Poin 2: Integrasi DB berhasil"]`)
	ai := json.RawMessage(`[{"task": "Audit keamanan", "assignee": "Homura", "status": "DONE"}]`)

	sum, err := fixture.summaryRepo.SaveSummary(ctx, meeting.ID, "Ringkasan eksekutif pengujian sistem.", kp, ai, "ag/gemini-3.8-flash-low")
	if err != nil {
		t.Fatalf("failed to save summary: %v", err)
	}
	if sum.ExecutiveSummary == "" {
		t.Errorf("expected non-empty summary")
	}

	fetchedSum, err := fixture.summaryRepo.GetSummaryByMeeting(ctx, meeting.ID)
	if err != nil {
		t.Fatalf("failed to fetch summary: %v", err)
	}
	if fetchedSum.ExecutiveSummary != sum.ExecutiveSummary {
		t.Errorf("summary mismatch")
	}

	// 6. Update Meeting Status to COMPLETED
	if err := fixture.meetingRepo.UpdateStatus(ctx, meeting.ID, userA.ID, "COMPLETED"); err != nil {
		t.Fatalf("failed to update status: %v", err)
	}

	// 7. Update Transcript Chunk
	chunks, err := fixture.transcriptRepo.GetChunksByMeeting(ctx, meeting.ID)
	if err != nil || len(chunks) == 0 {
		t.Fatalf("failed to get chunks: %v", err)
	}
	updatedChunk, err := fixture.transcriptRepo.UpdateChunk(ctx, meeting.ID, chunks[0].ID, "Pembukaan sesi rapat koordinasi kriptografi terenkripsi.")
	if err != nil {
		t.Fatalf("failed to update chunk: %v", err)
	}
	if updatedChunk.RawText != "Pembukaan sesi rapat koordinasi kriptografi terenkripsi." {
		t.Errorf("chunk text mismatch: %s", updatedChunk.RawText)
	}

	// 8. Replace Full Transcript
	err = fixture.transcriptRepo.ReplaceFullTranscript(ctx, meeting.ID, "Transkrip lengkap setelah revisi menyeluruh.")
	if err != nil {
		t.Fatalf("failed to replace full transcript: %v", err)
	}
	newFull, err := fixture.transcriptRepo.GetFullTranscript(ctx, meeting.ID)
	if err != nil || newFull != "Transkrip lengkap setelah revisi menyeluruh." {
		t.Errorf("expected replaced full transcript, got %q", newFull)
	}
}

func TestUserRepository_QuotaLifecycle(t *testing.T) {
	fixture := setupTestDB(t)

	ctx := context.Background()
	user, err := fixture.userRepo.CreateOAuthUser(ctx, "quota_test@sec.local", "Quota Tester", "g_quota_123")
	if err != nil {
		t.Fatalf("failed to create oauth user: %v", err)
	}

	// 1. Initial Quota Verification (30 mins = 1800s)
	quota, err := fixture.userRepo.GetQuota(ctx, user.ID)
	if err != nil {
		t.Fatalf("failed to get quota: %v", err)
	}
	if quota.RemainingSeconds != 1800 || quota.Tier != "free" {
		t.Errorf("expected 1800s remaining on free tier, got %d", quota.RemainingSeconds)
	}

	// 2. Deduct Quota (e.g. 300s used in a meeting)
	rem, err := fixture.userRepo.DeductQuota(ctx, user.ID, 300)
	if err != nil {
		t.Fatalf("failed to deduct quota: %v", err)
	}
	if rem != 1500 {
		t.Errorf("expected 1500s remaining, got %d", rem)
	}

	// 3. Add Quota / Top-Up (e.g. Starter Plan: +3600s)
	err = fixture.userRepo.AddQuota(ctx, user.ID, "plan_starter", "Paket Top Up 60 Menit", 3600, 15000, "starter")
	if err != nil {
		t.Fatalf("failed to add quota: %v", err)
	}

	updatedQuota, err := fixture.userRepo.GetQuota(ctx, user.ID)
	if err != nil {
		t.Fatalf("failed to get updated quota: %v", err)
	}
	if updatedQuota.QuotaSeconds != 5400 || updatedQuota.RemainingSeconds != 5100 || updatedQuota.Tier != "starter" {
		t.Errorf("unexpected updated quota: %+v", updatedQuota)
	}
}
