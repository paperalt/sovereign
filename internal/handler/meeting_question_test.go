package handler

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/paperalt/sovereign/internal/database"
	"github.com/paperalt/sovereign/internal/middleware"
	"github.com/paperalt/sovereign/internal/model"
	"github.com/paperalt/sovereign/internal/repository"
	"github.com/paperalt/sovereign/internal/service"
	"github.com/paperalt/sovereign/pkg/token"
)

func TestMeetingHandler_SuggestQuestions_Endpoint(t *testing.T) {
	db, err := database.OpenDatabase("sqlite", ":memory:")
	if err != nil {
		t.Fatalf("failed to init sqlite db: %v", err)
	}
	defer db.Close()

	if err := database.Migrate(db, "sqlite"); err != nil {
		t.Fatalf("failed to run migrations: %v", err)
	}

	userRepo := repository.NewUserRepository(db)
	meetingRepo := repository.NewMeetingRepository(db)
	transcriptRepo := repository.NewTranscriptRepository(db)
	summaryRepo := repository.NewSummaryRepository(db)

	// Create test user
	user, err := userRepo.Create(context.Background(), "user@test.com", "hash", "tester")
	if err != nil {
		t.Fatalf("failed to create test user: %v", err)
	}

	// Create test meeting
	meeting, err := meetingRepo.Create(context.Background(), user.ID, "Kuliah Keamanan Siber", "id", "")
	if err != nil {
		t.Fatalf("failed to create meeting: %v", err)
	}

	// Mock AI Gateway
	mockQuestionsJSON := `[
		{
			"id": "q1",
			"question": "Bagaimana pencegahan serangan Man-in-the-Middle jika enkripsi TLS tidak memvalidasi sertifikat akar?",
			"category": "critical_edge_case",
			"context_ref": "Dosen menjelaskan bahwa TLS mengamankan data yang lewat di jaringan publik",
			"thought_starter": "Bagus ditanyakan saat membahas validasi sertifikat"
		}
	]`

	aiServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		fmt.Fprintf(w, `{"choices":[{"message":{"content":%q}}]}`, mockQuestionsJSON)
	}))
	defer aiServer.Close()

	geminiClient := service.NewGeminiClient(service.GeminiConfig{
		BaseURL: aiServer.URL,
		APIKey:  "test-api-key",
		Model:   "test-model",
		Timeout: 5 * time.Second,
	})

	jwtSecret := "super-secure-secret-key-for-test-purposes"
	authMW := middleware.RequireAuth(jwtSecret)
	authToken, _ := token.GenerateJWT(user.ID, user.Email, jwtSecret, 1*time.Hour)

	meetingHandler := NewMeetingHandler(userRepo, meetingRepo, transcriptRepo, summaryRepo, geminiClient)

	mux := http.NewServeMux()
	mux.Handle("POST /api/v1/meetings/{id}/suggest-questions", authMW(http.HandlerFunc(meetingHandler.SuggestQuestions)))

	// Scenario 1: Insufficient Context (< 35 words)
	_, _ = transcriptRepo.AddChunk(context.Background(), meeting.ID, 0, 0.0, 5.0, "Halo selamat pagi.")

	reqBody, _ := json.Marshal(model.SuggestQuestionsRequest{WindowMinutes: 5})
	req := httptest.NewRequest(http.MethodPost, fmt.Sprintf("/api/v1/meetings/%s/suggest-questions", meeting.ID), bytes.NewReader(reqBody))
	req.Header.Set("Authorization", "Bearer "+authToken)
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	mux.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Fatalf("expected HTTP 200, got %d: %s", w.Code, w.Body.String())
	}
	var resp1 model.QuestionSuggestionResponse
	if err := json.Unmarshal(w.Body.Bytes(), &resp1); err != nil {
		t.Fatalf("failed to decode response: %v", err)
	}
	if resp1.HasSufficientContext {
		t.Errorf("expected insufficient context, got true")
	}

	// Scenario 2: Rich Context (Add substantive chunks)
	richText := `Protokol TLS versi satu titik tiga memperkenalkan penyederhanaan handshake kriptografi dengan menghapus algoritma lama seperti RSA key exchange dan beralih sepenuhnya ke Ephemeral Diffie-Hellman. Namun jika implementasi klien menonaktifkan validasi rantai sertifikat atau cert pinning, penyerang pada jaringan lokal tetap dapat melakukan intersepsi lalu lintas data.`
	_, _ = transcriptRepo.AddChunk(context.Background(), meeting.ID, 1, 5.0, 45.0, richText)

	req2 := httptest.NewRequest(http.MethodPost, fmt.Sprintf("/api/v1/meetings/%s/suggest-questions", meeting.ID), bytes.NewReader(reqBody))
	req2.Header.Set("Authorization", "Bearer "+authToken)
	req2.Header.Set("Content-Type", "application/json")
	w2 := httptest.NewRecorder()
	mux.ServeHTTP(w2, req2)

	if w2.Code != http.StatusOK {
		t.Fatalf("expected HTTP 200, got %d: %s", w2.Code, w2.Body.String())
	}
	var resp2 model.QuestionSuggestionResponse
	if err := json.Unmarshal(w2.Body.Bytes(), &resp2); err != nil {
		t.Fatalf("failed to decode response: %v", err)
	}
	if !resp2.HasSufficientContext {
		t.Fatalf("expected sufficient context, got false")
	}
	if len(resp2.Suggestions) != 1 {
		t.Fatalf("expected 1 suggestion, got %d", len(resp2.Suggestions))
	}
	if resp2.Suggestions[0].Category != "critical_edge_case" {
		t.Errorf("expected critical_edge_case, got %s", resp2.Suggestions[0].Category)
	}
}
