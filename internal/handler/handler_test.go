package handler

import (
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/gorilla/websocket"
	"github.com/paperalt/sovereign-speech-intelligence/internal/database"
	"github.com/paperalt/sovereign-speech-intelligence/internal/middleware"
	"github.com/paperalt/sovereign-speech-intelligence/internal/model"
	"github.com/paperalt/sovereign-speech-intelligence/internal/repository"
	"github.com/paperalt/sovereign-speech-intelligence/internal/service"
)

func setupTestServer(t *testing.T) (*httptest.Server, string) {
	db, err := database.OpenDatabase("sqlite", ":memory:")
	if err != nil {
		t.Fatalf("failed to open sqlite: %v", err)
	}
	if err := database.Migrate(db, "sqlite"); err != nil {
		t.Fatalf("failed to migrate: %v", err)
	}

	userRepo := repository.NewUserRepository(db)
	tokenRepo := repository.NewTokenRepository(db)
	meetingRepo := repository.NewMeetingRepository(db)
	transcriptRepo := repository.NewTranscriptRepository(db)
	summaryRepo := repository.NewSummaryRepository(db)

	jwtSecret := "test-secret-key-12345678901234567890"

	// Mock Gemini Server
	mockGemini := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		w.Write([]byte(`{
			"choices": [
				{
					"message": {
						"content": "Hasil transkripsi uji coba websocket dan ringkasan."
					}
				}
			]
		}`))
	}))

	geminiClient := service.NewGeminiClient(service.GeminiConfig{
		BaseURL: mockGemini.URL,
		APIKey:  "test-key",
		Model:   "ag/gemini-3.8-flash-low",
		Timeout: 5 * time.Second,
	})

	mockGoogle := service.NewMockGoogleValidator()
	authHandler := NewAuthHandler(userRepo, tokenRepo, mockGoogle, jwtSecret, 15*time.Minute, 7*24*time.Hour)
	authHandler.SetAllowPasswordAuth(true)
	meetingHandler := NewMeetingHandler(userRepo, meetingRepo, transcriptRepo, summaryRepo, geminiClient)
	wsHandler := NewWSHandler(userRepo, meetingRepo, transcriptRepo, summaryRepo, geminiClient, jwtSecret)

	mux := http.NewServeMux()

	// Public routes
	mux.HandleFunc("POST /api/v1/auth/register", authHandler.Register)
	mux.HandleFunc("POST /api/v1/auth/login", authHandler.Login)
	mux.HandleFunc("POST /api/v1/auth/refresh", authHandler.Refresh)
	mux.HandleFunc("POST /api/v1/auth/logout", authHandler.Logout)

	// Protected routes
	authMW := middleware.RequireAuth(jwtSecret)
	mux.Handle("POST /api/v1/meetings", authMW(http.HandlerFunc(meetingHandler.Create)))
	mux.Handle("GET /api/v1/meetings", authMW(http.HandlerFunc(meetingHandler.List)))
	mux.Handle("GET /api/v1/meetings/active", authMW(http.HandlerFunc(meetingHandler.GetActive)))
	mux.Handle("GET /api/v1/meetings/search", authMW(http.HandlerFunc(meetingHandler.Search)))
	mux.Handle("GET /api/v1/meetings/{id}", authMW(http.HandlerFunc(meetingHandler.GetByID)))
	mux.Handle("PUT /api/v1/meetings/{id}", authMW(http.HandlerFunc(meetingHandler.Update)))
	mux.Handle("GET /api/v1/meetings/{id}/transcript", authMW(http.HandlerFunc(meetingHandler.GetTranscript)))
	mux.Handle("PUT /api/v1/meetings/{id}/chunks/{chunk_id}", authMW(http.HandlerFunc(meetingHandler.UpdateChunk)))
	mux.Handle("PUT /api/v1/meetings/{id}/transcript", authMW(http.HandlerFunc(meetingHandler.UpdateTranscript)))
	mux.Handle("GET /api/v1/meetings/{id}/search", authMW(http.HandlerFunc(meetingHandler.Search)))
	mux.Handle("POST /api/v1/meetings/{id}/stop", authMW(http.HandlerFunc(meetingHandler.Stop)))
	mux.Handle("POST /api/v1/meetings/{id}/cancel", authMW(http.HandlerFunc(meetingHandler.Cancel)))
	mux.Handle("POST /api/v1/meetings/{id}/summarize", authMW(http.HandlerFunc(meetingHandler.Summarize)))
	mux.Handle("DELETE /api/v1/meetings/{id}", authMW(http.HandlerFunc(meetingHandler.Delete)))

	// WebSocket route
	mux.HandleFunc("/ws/transcribe", wsHandler.ServeWS)

	server := httptest.NewServer(mux)
	return server, jwtSecret
}

func TestCompleteWorkflow_E2E(t *testing.T) {
	server, _ := setupTestServer(t)
	defer server.Close()

	client := server.Client()

	// 1. Register User (Receives Access Token + Refresh Token persisted in DB)
	regPayload := `{"email": "agent@eclipsegate.my.id", "password": "SuperSecretPassword123!", "full_name": "Field Agent"}`
	resp, err := client.Post(server.URL+"/api/v1/auth/register", "application/json", strings.NewReader(regPayload))
	if err != nil {
		t.Fatalf("register failed: %v", err)
	}
	if resp.StatusCode != http.StatusCreated {
		t.Fatalf("expected 201 Created, got %d", resp.StatusCode)
	}

	var authResp struct {
		Token        string     `json:"token"`
		RefreshToken string     `json:"refresh_token"`
		User         model.User `json:"user"`
	}
	json.NewDecoder(resp.Body).Decode(&authResp)
	token := authResp.Token
	refreshToken := authResp.RefreshToken
	if token == "" || refreshToken == "" {
		t.Fatalf("access token or refresh token empty in response")
	}

	// 2. Test DB-backed Refresh Token Rotation
	refreshPayload := fmt.Sprintf(`{"refresh_token": "%s"}`, refreshToken)
	resp, err = client.Post(server.URL+"/api/v1/auth/refresh", "application/json", strings.NewReader(refreshPayload))
	if err != nil {
		t.Fatalf("refresh failed: %v", err)
	}
	if resp.StatusCode != http.StatusOK {
		var errMap map[string]interface{}
		json.NewDecoder(resp.Body).Decode(&errMap)
		t.Fatalf("expected 200 OK on refresh, got %d: %v", resp.StatusCode, errMap)
	}

	var rotatedResp struct {
		Token        string `json:"token"`
		RefreshToken string `json:"refresh_token"`
	}
	json.NewDecoder(resp.Body).Decode(&rotatedResp)
	if rotatedResp.Token == "" || rotatedResp.RefreshToken == "" {
		t.Fatalf("expected new token pair after refresh")
	}
	token = rotatedResp.Token
	refreshToken = rotatedResp.RefreshToken

	// 3. Create Multilingual Meeting (English Audio -> Indonesian Transcript)
	createMeetingPayload := `{"title": "Rapat Perencanaan Operasi", "language": "en", "target_language": "id"}`
	req, _ := http.NewRequest("POST", server.URL+"/api/v1/meetings", strings.NewReader(createMeetingPayload))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")

	resp, err = client.Do(req)
	if err != nil {
		t.Fatalf("create meeting failed: %v", err)
	}
	if resp.StatusCode != http.StatusCreated {
		t.Fatalf("expected 201, got %d", resp.StatusCode)
	}

	var meeting model.Meeting
	json.NewDecoder(resp.Body).Decode(&meeting)
	if meeting.ID == "" || meeting.Title != "Rapat Perencanaan Operasi" {
		t.Fatalf("invalid meeting response: %+v", meeting)
	}
	if meeting.Language != "en" || meeting.TargetLanguage != "id" {
		t.Errorf("expected language=en, target_language=id; got lang=%s, target=%s", meeting.Language, meeting.TargetLanguage)
	}

	// 4. Connect to WebSocket & Stream Audio
	wsURL := "ws" + strings.TrimPrefix(server.URL, "http") + fmt.Sprintf("/ws/transcribe?token=%s&meeting_id=%s", token, meeting.ID)
	wsConn, _, err := websocket.DefaultDialer.Dial(wsURL, nil)
	if err != nil {
		t.Fatalf("websocket dial failed: %v", err)
	}
	defer wsConn.Close()

	// Read initial STATUS event
	var initEvent wsEventMessage
	if err := wsConn.ReadJSON(&initEvent); err != nil {
		t.Fatalf("failed to read initial ws event: %v", err)
	}
	if initEvent.Status != "CONNECTED" {
		t.Errorf("expected CONNECTED status, got %s", initEvent.Status)
	}

	// Send simulated 16kHz PCM audio in realistic 2KB chunks
	pcmChunk := make([]byte, 2048)
	for i := range pcmChunk {
		pcmChunk[i] = byte(i % 128)
	}
	for i := 0; i < 30; i++ {
		if err := wsConn.WriteMessage(websocket.BinaryMessage, pcmChunk); err != nil {
			t.Fatalf("failed to write binary audio chunk %d: %v", i, err)
		}
	}

	// Send STOP action
	stopMsg := `{"action": "STOP"}`
	if err := wsConn.WriteMessage(websocket.TextMessage, []byte(stopMsg)); err != nil {
		t.Fatalf("failed to write stop message: %v", err)
	}

	// Read events until COMPLETED
	receivedTranscribeEvent := false
	receivedSummaryEvent := false
	for {
		var evt wsEventMessage
		wsConn.SetReadDeadline(time.Now().Add(3 * time.Second))
		err := wsConn.ReadJSON(&evt)
		if err != nil {
			break
		}
		if evt.Event == "CHUNK_TRANSCRIBED" {
			receivedTranscribeEvent = true
		}
		if evt.Event == "SUMMARY_GENERATED" {
			receivedSummaryEvent = true
		}
		if evt.Event == "STATUS" && evt.Status == "COMPLETED" {
			break
		}
	}

	t.Logf("Events received: transcribed=%v, summary=%v", receivedTranscribeEvent, receivedSummaryEvent)

	// 5. Verify Meeting Transcript API (Includes Structured Summary)
	time.Sleep(100 * time.Millisecond) // Allow DB write to settle
	req, _ = http.NewRequest("GET", server.URL+fmt.Sprintf("/api/v1/meetings/%s/transcript", meeting.ID), nil)
	req.Header.Set("Authorization", "Bearer "+token)

	resp, err = client.Do(req)
	if err != nil {
		t.Fatalf("failed to get transcript: %v", err)
	}
	if resp.StatusCode != http.StatusOK {
		t.Fatalf("expected 200 OK, got %d", resp.StatusCode)
	}

	var fullTranscript model.FullTranscriptDTO
	json.NewDecoder(resp.Body).Decode(&fullTranscript)
	if fullTranscript.MeetingID != meeting.ID {
		t.Errorf("meeting ID mismatch in transcript: %s", fullTranscript.MeetingID)
	}

	// 5B. Test Update Chunk API
	if len(fullTranscript.Chunks) > 0 {
		targetChunk := fullTranscript.Chunks[0]
		chunkURL := server.URL + fmt.Sprintf("/api/v1/meetings/%s/chunks/%d", meeting.ID, targetChunk.ID)
		updatePayload := `{"raw_text": "Teks transkrip terkoreksi hasil edit."}`
		chunkReq, _ := http.NewRequest("PUT", chunkURL, strings.NewReader(updatePayload))
		chunkReq.Header.Set("Authorization", "Bearer "+token)
		chunkReq.Header.Set("Content-Type", "application/json")
		cResp, cErr := client.Do(chunkReq)
		if cErr != nil || cResp.StatusCode != http.StatusOK {
			t.Fatalf("failed to update chunk: %v, status: %d", cErr, cResp.StatusCode)
		}
		var updatedChunk model.TranscriptChunk
		json.NewDecoder(cResp.Body).Decode(&updatedChunk)
		if updatedChunk.RawText != "Teks transkrip terkoreksi hasil edit." {
			t.Errorf("expected updated chunk text, got %q", updatedChunk.RawText)
		}
	}

	// 5C. Test Update Full Transcript API
	fullTransURL := server.URL + fmt.Sprintf("/api/v1/meetings/%s/transcript", meeting.ID)
	fullPayload := `{"raw_text": "Teks transkripsi lengkap yang telah direvisi total."}`
	fullReq, _ := http.NewRequest("PUT", fullTransURL, strings.NewReader(fullPayload))
	fullReq.Header.Set("Authorization", "Bearer "+token)
	fullReq.Header.Set("Content-Type", "application/json")
	ftResp, ftErr := client.Do(fullReq)
	if ftErr != nil || ftResp.StatusCode != http.StatusOK {
		t.Fatalf("failed to update full transcript: %v, status: %d", ftErr, ftResp.StatusCode)
	}

	// 5D. Test Rename Meeting API
	renameURL := server.URL + fmt.Sprintf("/api/v1/meetings/%s", meeting.ID)
	renamePayload := `{"title": "Judul Rapat Baru Hasil Rename"}`
	renameReq, _ := http.NewRequest("PUT", renameURL, strings.NewReader(renamePayload))
	renameReq.Header.Set("Authorization", "Bearer "+token)
	renameReq.Header.Set("Content-Type", "application/json")
	renResp, renErr := client.Do(renameReq)
	if renErr != nil || renResp.StatusCode != http.StatusOK {
		t.Fatalf("failed to rename meeting: %v, status: %d", renErr, renResp.StatusCode)
	}
	var renamedMeeting model.Meeting
	json.NewDecoder(renResp.Body).Decode(&renamedMeeting)
	if renamedMeeting.Title != "Judul Rapat Baru Hasil Rename" {
		t.Errorf("expected renamed title 'Judul Rapat Baru Hasil Rename', got %q", renamedMeeting.Title)
	}

	// 6. Test Search API (Full-text search across user meetings)
	searchURL := server.URL + "/api/v1/meetings/search?q=transkripsi"
	req, _ = http.NewRequest("GET", searchURL, nil)
	req.Header.Set("Authorization", "Bearer "+token)

	resp, err = client.Do(req)
	if err != nil {
		t.Fatalf("search failed: %v", err)
	}
	if resp.StatusCode != http.StatusOK {
		t.Fatalf("expected 200 OK on search, got %d", resp.StatusCode)
	}

	var searchResp struct {
		Query   string                  `json:"query"`
		Results []model.SearchResultDTO `json:"results"`
		Total   int                     `json:"total"`
	}
	json.NewDecoder(resp.Body).Decode(&searchResp)
	if searchResp.Total == 0 {
		t.Errorf("expected search matches for 'transkripsi', got 0")
	}

	// 7. Test Logout & Token Revocation
	logoutPayload := fmt.Sprintf(`{"refresh_token": "%s"}`, refreshToken)
	resp, err = client.Post(server.URL+"/api/v1/auth/logout", "application/json", strings.NewReader(logoutPayload))
	if err != nil {
		t.Fatalf("logout request failed: %v", err)
	}
	if resp.StatusCode != http.StatusOK {
		t.Fatalf("expected 200 OK on logout, got %d", resp.StatusCode)
	}

	// Verification: Attempting refresh with revoked token MUST fail with 401
	resp, err = client.Post(server.URL+"/api/v1/auth/refresh", "application/json", strings.NewReader(logoutPayload))
	if err != nil {
		t.Fatalf("refresh attempt after logout failed: %v", err)
	}
	if resp.StatusCode != http.StatusUnauthorized {
		t.Fatalf("security violation: expected 401 Unauthorized for revoked token, got %d", resp.StatusCode)
	}
}
