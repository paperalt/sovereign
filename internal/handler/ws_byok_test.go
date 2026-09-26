package handler

import (
	"context"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/gorilla/websocket"
	"github.com/paperalt/sovereign-speech-intelligence/internal/database"
	"github.com/paperalt/sovereign-speech-intelligence/internal/repository"
	"github.com/paperalt/sovereign-speech-intelligence/internal/service"
	"github.com/paperalt/sovereign-speech-intelligence/pkg/token"
)

type mockBYOKService struct {
	transcribeCalled bool
	lastProvider     string
	lastAPIKey       string
}

func (m *mockBYOKService) TranscribeAudio(ctx context.Context, provider, apiKey string, wavBytes []byte, lang string) (string, error) {
	m.transcribeCalled = true
	m.lastProvider = provider
	m.lastAPIKey = apiKey
	return "Transkripsi BYOK Groq berhasil tanpa kuota server.", nil
}

func (m *mockBYOKService) GenerateCompletion(ctx context.Context, provider, apiKey string, systemPrompt, userPrompt string, temperature float64) (string, error) {
	return `{"executive_summary":"Ringkasan BYOK selesai","key_points":["Poin A"],"action_items":[]}`, nil
}

func (m *mockBYOKService) ValidateKey(ctx context.Context, provider, apiKey string) (*service.KeyValidationResult, error) {
	if apiKey == "gsk_valid" {
		return &service.KeyValidationResult{
			Valid:     true,
			Provider:  provider,
			LatencyMS: 120,
			Message:   "Koneksi Aktif & Terverifikasi",
		}, nil
	}
	return &service.KeyValidationResult{
		Valid:     false,
		Provider:  provider,
		LatencyMS: 120,
		Message:   "Kunci tidak valid",
	}, nil
}

func TestWS_BYOK_HandshakeAndQuotaBypass(t *testing.T) {
	db, err := database.OpenDatabase("sqlite", ":memory:")
	if err != nil {
		t.Fatalf("failed to open sqlite: %v", err)
	}
	defer db.Close()

	if err := database.Migrate(db, "sqlite"); err != nil {
		t.Fatalf("failed to migrate db: %v", err)
	}

	userRepo := repository.NewUserRepository(db)
	meetingRepo := repository.NewMeetingRepository(db)
	transcriptRepo := repository.NewTranscriptRepository(db)
	summaryRepo := repository.NewSummaryRepository(db)

	jwtSecret := "test-jwt-secret-byok-32bytes-ok"

	// 1. Create test user
	ctx := context.Background()
	user, err := userRepo.CreateOAuthUser(ctx, "byok_user@example.com", "BYOK User", "google_byok_123")
	if err != nil {
		t.Fatalf("failed to create user: %v", err)
	}

	// Ensure user quota is 0 seconds (exhausted)
	quota, err := userRepo.GetQuota(ctx, user.ID)
	if err != nil {
		t.Fatalf("failed to get quota: %v", err)
	}
	if quota.RemainingSeconds > 0 {
		_, _ = userRepo.DeductQuota(ctx, user.ID, quota.RemainingSeconds)
	}

	// 2. Create meeting
	meeting, err := meetingRepo.Create(ctx, user.ID, "BYOK Meeting Test", "id", "")
	if err != nil {
		t.Fatalf("failed to create meeting: %v", err)
	}

	// 3. Generate token
	accessToken, _, err := token.GenerateTokenPair(user.ID, user.Email, jwtSecret, 15*time.Minute, 24*time.Hour)
	if err != nil {
		t.Fatalf("failed to generate token: %v", err)
	}

	wsHandler := NewWSHandler(userRepo, meetingRepo, transcriptRepo, summaryRepo, nil, jwtSecret)
	mockBYOK := &mockBYOKService{}
	wsHandler.SetBYOKClient(mockBYOK)

	server := httptest.NewServer(http.HandlerFunc(wsHandler.ServeWS))
	defer server.Close()

	// 4. Attempt to connect WITHOUT BYOK (should fail with HTTP 402 Payment Required because quota is 0)
	normalWSURL := "ws" + strings.TrimPrefix(server.URL, "http") + "/ws?token=" + accessToken + "&meeting_id=" + meeting.ID
	_, resp, err := websocket.DefaultDialer.Dial(normalWSURL, nil)
	if err == nil {
		t.Fatalf("expected dial failure due to 0 quota, but connected successfully")
	}
	if resp != nil && resp.StatusCode != http.StatusPaymentRequired {
		t.Fatalf("expected HTTP 402 PaymentRequired, got: %d", resp.StatusCode)
	}

	// 5. Connect WITH BYOK parameters (should succeed despite 0 quota!)
	byokWSURL := normalWSURL + "&byok_provider=GROQ&byok_key=gsk_my_secret_key_12345"
	conn, resp, err := websocket.DefaultDialer.Dial(byokWSURL, nil)
	if err != nil {
		t.Fatalf("expected BYOK connection to succeed, got error: %v, status: %v", err, resp.StatusCode)
	}
	defer conn.Close()

	// Read initial STATUS message
	var msg wsEventMessage
	err = conn.ReadJSON(&msg)
	if err != nil {
		t.Fatalf("failed to read initial message: %v", err)
	}
	if msg.Status != "CONNECTED" || !strings.Contains(msg.Message, "Audio: GROQ Bebas Kuota") {
		t.Fatalf("expected custom STT status message, got: %+v", msg)
	}

	// Send STOP action
	_ = conn.WriteJSON(wsControlMessage{Action: "STOP"})

	// Verify meeting finalized
	time.Sleep(100 * time.Millisecond)
	m, err := meetingRepo.GetByID(ctx, meeting.ID, user.ID)
	if err != nil {
		t.Fatalf("failed to get meeting: %v", err)
	}
	if m.Status != "COMPLETED" {
		t.Fatalf("expected meeting COMPLETED, got: %s", m.Status)
	}
}

func TestWS_AdaptiveBeta_HandshakeAndVADSilence(t *testing.T) {
	db, err := database.OpenDatabase("sqlite", ":memory:")
	if err != nil {
		t.Fatalf("failed to open sqlite: %v", err)
	}
	defer db.Close()

	if err := database.Migrate(db, "sqlite"); err != nil {
		t.Fatalf("failed to migrate db: %v", err)
	}

	userRepo := repository.NewUserRepository(db)
	meetingRepo := repository.NewMeetingRepository(db)
	transcriptRepo := repository.NewTranscriptRepository(db)
	summaryRepo := repository.NewSummaryRepository(db)

	jwtSecret := "test-jwt-secret-beta-pipeline-32b"

	ctx := context.Background()
	user, err := userRepo.CreateOAuthUser(ctx, "beta_user@example.com", "Beta User", "google_beta_123")
	if err != nil {
		t.Fatalf("failed to create user: %v", err)
	}

	accessToken, _, err := token.GenerateTokenPair(user.ID, user.Email, jwtSecret, 15*time.Minute, 24*time.Hour)
	if err != nil {
		t.Fatalf("failed to generate token: %v", err)
	}

	meeting, err := meetingRepo.Create(ctx, user.ID, "Beta Test Sesi", "id", "")
	if err != nil {
		t.Fatalf("failed to create meeting: %v", err)
	}

	wsHandler := NewWSHandler(userRepo, meetingRepo, transcriptRepo, summaryRepo, nil, jwtSecret)

	server := httptest.NewServer(http.HandlerFunc(wsHandler.ServeWS))
	defer server.Close()

	// Connect with pipeline_mode=adaptive_beta
	betaWSURL := "ws" + strings.TrimPrefix(server.URL, "http") + "/ws?token=" + accessToken + "&meeting_id=" + meeting.ID + "&pipeline_mode=adaptive_beta"
	conn, resp, err := websocket.DefaultDialer.Dial(betaWSURL, nil)
	if err != nil {
		t.Fatalf("expected beta connection to succeed, got error: %v, status: %v", err, resp.StatusCode)
	}
	defer conn.Close()

	// 1. Read PIPELINE_MODE event
	var msg1 wsEventMessage
	if err := conn.ReadJSON(&msg1); err != nil {
		t.Fatalf("failed to read msg1: %v", err)
	}
	if msg1.Event != "PIPELINE_MODE" || msg1.Status != "ADAPTIVE_BETA" {
		t.Fatalf("expected PIPELINE_MODE ADAPTIVE_BETA, got %+v", msg1)
	}

	// 2. Read initial STATUS message
	var msg2 wsEventMessage
	if err := conn.ReadJSON(&msg2); err != nil {
		t.Fatalf("failed to read msg2: %v", err)
	}
	if msg2.Status != "CONNECTED" {
		t.Fatalf("expected CONNECTED, got %+v", msg2)
	}

	// 3. Send VAD_SILENCE keepalive action and expect PONG
	if err := conn.WriteJSON(wsControlMessage{Action: "VAD_SILENCE"}); err != nil {
		t.Fatalf("failed to write VAD_SILENCE: %v", err)
	}

	var msg3 wsEventMessage
	if err := conn.ReadJSON(&msg3); err != nil {
		t.Fatalf("failed to read msg3: %v", err)
	}
	if msg3.Event != "PONG" {
		t.Fatalf("expected PONG event, got %+v", msg3)
	}

	// 4. Send STOP action
	_ = conn.WriteJSON(wsControlMessage{Action: "STOP"})
}
