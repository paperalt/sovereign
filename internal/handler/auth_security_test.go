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
	"github.com/paperalt/sovereign-speech-intelligence/pkg/token"
)

func TestSecurityAndFeatures_ComprehensiveAudit(t *testing.T) {
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

	jwtSecret := "security-audit-secret-key-32bytes!!"

	// Mock AI Gateway
	mockGemini := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)

		var req map[string]interface{}
		json.NewDecoder(r.Body).Decode(&req)
		reqStr, _ := json.Marshal(req)

		if strings.Contains(string(reqStr), "input_audio") {
			// Transcription response
			w.Write([]byte(`{
				"choices": [{"message": {"content": "Hasil transkripsi rahasia pengguna A."}}]
			}`))
		} else {
			// Summary response
			w.Write([]byte(`{
				"choices": [{
					"message": {
						"content": "{\"executive_summary\": \"Audit keamanan berhasil diselesaikan.\", \"key_points\": [\"Otentikasi aman\", \"Isolasi tenant valid\"], \"action_items\": [{\"task\": \"Deploy production\", \"status\": \"READY\"}]}"
					}
				}]
			}`))
		}
	}))
	defer mockGemini.Close()

	geminiClient := service.NewGeminiClient(service.GeminiConfig{
		BaseURL: mockGemini.URL,
		APIKey:  "audit-key",
		Model:   "ag/gemini-3.8-flash-low",
		Timeout: 5 * time.Second,
	})

	mockGoogle := service.NewMockGoogleValidator()
	mockGoogle.ExpectedClaims["valid-google-id-token"] = &service.GoogleClaims{
		GoogleID:      "google-sub-1001",
		Email:         "google.user@mitakihara.sec",
		FullName:      "Google Homura",
		EmailVerified: true,
	}
	mockGoogle.ExpectedClaims["unverified-google-token"] = &service.GoogleClaims{
		GoogleID:      "google-sub-unverified",
		Email:         "unverified@mitakihara.sec",
		FullName:      "Unverified User",
		EmailVerified: false,
	}
	mockGoogle.ExpectedClaims["link-google-token"] = &service.GoogleClaims{
		GoogleID:      "google-sub-link-999",
		Email:         "usera@sec.local", // Existing User A email to test linking!
		FullName:      "User Alpha",
		EmailVerified: true,
	}

	authHandler := NewAuthHandler(userRepo, tokenRepo, mockGoogle, jwtSecret, 15*time.Minute, 7*24*time.Hour)
	authHandler.SetAllowPasswordAuth(true)
	meetingHandler := NewMeetingHandler(userRepo, meetingRepo, transcriptRepo, summaryRepo, geminiClient)
	wsHandler := NewWSHandler(userRepo, meetingRepo, transcriptRepo, summaryRepo, geminiClient, jwtSecret)

	mux := http.NewServeMux()
	mux.HandleFunc("POST /api/v1/auth/register", authHandler.Register)
	mux.HandleFunc("POST /api/v1/auth/login", authHandler.Login)
	mux.HandleFunc("POST /api/v1/auth/google", authHandler.GoogleLogin)
	mux.HandleFunc("POST /api/v1/auth/refresh", authHandler.Refresh)
	mux.HandleFunc("POST /api/v1/auth/logout", authHandler.Logout)

	authMW := middleware.RequireAuth(jwtSecret)
	mux.Handle("POST /api/v1/meetings", authMW(http.HandlerFunc(meetingHandler.Create)))
	mux.Handle("GET /api/v1/meetings", authMW(http.HandlerFunc(meetingHandler.List)))
	mux.Handle("GET /api/v1/meetings/active", authMW(http.HandlerFunc(meetingHandler.GetActive)))
	mux.Handle("GET /api/v1/meetings/search", authMW(http.HandlerFunc(meetingHandler.Search)))
	mux.Handle("GET /api/v1/meetings/{id}", authMW(http.HandlerFunc(meetingHandler.GetByID)))
	mux.Handle("GET /api/v1/meetings/{id}/transcript", authMW(http.HandlerFunc(meetingHandler.GetTranscript)))
	mux.Handle("GET /api/v1/meetings/{id}/search", authMW(http.HandlerFunc(meetingHandler.Search)))
	mux.Handle("POST /api/v1/meetings/{id}/stop", authMW(http.HandlerFunc(meetingHandler.Stop)))
	mux.Handle("POST /api/v1/meetings/{id}/cancel", authMW(http.HandlerFunc(meetingHandler.Cancel)))
	mux.Handle("POST /api/v1/meetings/{id}/summarize", authMW(http.HandlerFunc(meetingHandler.Summarize)))
	mux.Handle("DELETE /api/v1/meetings/{id}", authMW(http.HandlerFunc(meetingHandler.Delete)))
	mux.HandleFunc("/ws/transcribe", wsHandler.ServeWS)

	server := httptest.NewServer(mux)
	defer server.Close()
	client := server.Client()

	// -------------------------------------------------------------
	// 1. FEATURE & AUTH TESTING: REGISTRATION & LOGIN
	// -------------------------------------------------------------
	t.Log("===> [TEST 1] User Registration & Credential Validation")
	
	// Negative: Verify Password Auth is disabled when allowPasswordAuth = false
	regA := `{"email": "usera@sec.local", "password": "PasswordUserA#123", "full_name": "User Alpha"}`
	authHandler.SetAllowPasswordAuth(false)
	resp, _ := client.Post(server.URL+"/api/v1/auth/register", "application/json", strings.NewReader(regA))
	if resp.StatusCode != http.StatusForbidden {
		t.Errorf("expected 403 Forbidden when password auth is disabled, got %d", resp.StatusCode)
	}
	resp, _ = client.Post(server.URL+"/api/v1/auth/login", "application/json", strings.NewReader(regA))
	if resp.StatusCode != http.StatusForbidden {
		t.Errorf("expected 403 Forbidden on login when password auth is disabled, got %d", resp.StatusCode)
	}
	authHandler.SetAllowPasswordAuth(true) // Re-enable for credential tests
	
	// Valid Register User A
	resp, _ = client.Post(server.URL+"/api/v1/auth/register", "application/json", strings.NewReader(regA))
	if resp.StatusCode != http.StatusCreated {
		t.Fatalf("expected 201 Created for User A, got %d", resp.StatusCode)
	}
	var authA struct {
		Token        string     `json:"token"`
		RefreshToken string     `json:"refresh_token"`
		User         model.User `json:"user"`
	}
	json.NewDecoder(resp.Body).Decode(&authA)

	// Valid Register User B
	regB := `{"email": "userb@sec.local", "password": "PasswordUserB#123", "full_name": "User Bravo"}`
	resp, _ = client.Post(server.URL+"/api/v1/auth/register", "application/json", strings.NewReader(regB))
	if resp.StatusCode != http.StatusCreated {
		t.Fatalf("expected 201 Created for User B, got %d", resp.StatusCode)
	}
	var authB struct {
		Token        string     `json:"token"`
		RefreshToken string     `json:"refresh_token"`
		User         model.User `json:"user"`
	}
	json.NewDecoder(resp.Body).Decode(&authB)

	// Negative: Duplicate Email Registration
	resp, _ = client.Post(server.URL+"/api/v1/auth/register", "application/json", strings.NewReader(regA))
	if resp.StatusCode != http.StatusConflict {
		t.Errorf("expected 409 Conflict on duplicate email, got %d", resp.StatusCode)
	}

	// Negative: Short Password (< 8 chars)
	shortPass := `{"email": "weak@sec.local", "password": "123", "full_name": "Weak"}`
	resp, _ = client.Post(server.URL+"/api/v1/auth/register", "application/json", strings.NewReader(shortPass))
	if resp.StatusCode != http.StatusBadRequest {
		t.Errorf("expected 400 Bad Request for short password, got %d", resp.StatusCode)
	}

	// Negative: Login Wrong Password
	loginBad := `{"email": "usera@sec.local", "password": "WrongPassword!"}`
	resp, _ = client.Post(server.URL+"/api/v1/auth/login", "application/json", strings.NewReader(loginBad))
	if resp.StatusCode != http.StatusUnauthorized {
		t.Errorf("expected 401 Unauthorized for bad password, got %d", resp.StatusCode)
	}

	// -------------------------------------------------------------
	// 1B. GOOGLE OAUTH FLOW TESTING
	// -------------------------------------------------------------
	t.Log("===> [TEST 1B] Google OAuth OIDC Validation & Account Linking")

	// Positive: New user sign in with Google
	googleReqValid := `{"id_token": "valid-google-id-token"}`
	resp, _ = client.Post(server.URL+"/api/v1/auth/google", "application/json", strings.NewReader(googleReqValid))
	if resp.StatusCode != http.StatusOK {
		t.Fatalf("expected 200 OK on valid Google login, got %d", resp.StatusCode)
	}
	var googleAuthResp struct {
		Token        string     `json:"token"`
		RefreshToken string     `json:"refresh_token"`
		User         model.User `json:"user"`
	}
	json.NewDecoder(resp.Body).Decode(&googleAuthResp)
	if googleAuthResp.Token == "" || googleAuthResp.User.GoogleID == nil {
		t.Errorf("expected token and google_id in google login response")
	}

	// Negative: Google login with unverified email (Must fail)
	googleReqUnverified := `{"id_token": "unverified-google-token"}`
	resp, _ = client.Post(server.URL+"/api/v1/auth/google", "application/json", strings.NewReader(googleReqUnverified))
	if resp.StatusCode != http.StatusUnauthorized {
		t.Errorf("SECURITY FLAW: unverified Google email accepted! Got %d", resp.StatusCode)
	}

	// Negative: Google login with invalid token
	googleReqBad := `{"id_token": "bad-google-token"}`
	resp, _ = client.Post(server.URL+"/api/v1/auth/google", "application/json", strings.NewReader(googleReqBad))
	if resp.StatusCode != http.StatusUnauthorized {
		t.Errorf("expected 401 on invalid Google token, got %d", resp.StatusCode)
	}

	// Positive: Account Linking with existing User A email
	googleLinkReq := `{"id_token": "link-google-token"}`
	resp, _ = client.Post(server.URL+"/api/v1/auth/google", "application/json", strings.NewReader(googleLinkReq))
	if resp.StatusCode != http.StatusOK {
		t.Errorf("expected 200 OK on account linking, got %d", resp.StatusCode)
	}
	var linkResp struct {
		User model.User `json:"user"`
	}
	json.NewDecoder(resp.Body).Decode(&linkResp)
	if linkResp.User.ID != authA.User.ID {
		t.Errorf("expected linked user ID %s, got %s", authA.User.ID, linkResp.User.ID)
	}

	// -------------------------------------------------------------
	// 2. AUTHORIZATION & TOKEN BOUNDARY TESTING
	// -------------------------------------------------------------
	t.Log("===> [TEST 2] Token Validation & Authorization Headers")

	// Negative: Unauthenticated request to protected endpoint
	req, _ := http.NewRequest("GET", server.URL+"/api/v1/meetings", nil)
	resp, _ = client.Do(req)
	if resp.StatusCode != http.StatusUnauthorized {
		t.Errorf("expected 401 Unauthorized without auth header, got %d", resp.StatusCode)
	}

	// Negative: Malformed Authorization Header (no 'Bearer' prefix)
	req, _ = http.NewRequest("GET", server.URL+"/api/v1/meetings", nil)
	req.Header.Set("Authorization", "Token "+authA.Token)
	resp, _ = client.Do(req)
	if resp.StatusCode != http.StatusUnauthorized {
		t.Errorf("expected 401 Unauthorized for malformed header, got %d", resp.StatusCode)
	}

	// Negative: Tampered / Fake JWT Signature
	fakeToken := authA.Token + "tampered"
	req, _ = http.NewRequest("GET", server.URL+"/api/v1/meetings", nil)
	req.Header.Set("Authorization", "Bearer "+fakeToken)
	resp, _ = client.Do(req)
	if resp.StatusCode != http.StatusUnauthorized {
		t.Errorf("expected 401 Unauthorized for tampered token, got %d", resp.StatusCode)
	}

	// Negative: Using Refresh Token as Access Token
	req, _ = http.NewRequest("GET", server.URL+"/api/v1/meetings", nil)
	req.Header.Set("Authorization", "Bearer "+authA.RefreshToken)
	resp, _ = client.Do(req)
	if resp.StatusCode != http.StatusUnauthorized {
		t.Errorf("expected 401 Unauthorized when using refresh token as access token, got %d", resp.StatusCode)
	}

	// Negative: Expired Access Token
	expiredAccess, _ := token.GenerateJWT(authA.User.ID, authA.User.Email, jwtSecret, -5*time.Minute)
	req, _ = http.NewRequest("GET", server.URL+"/api/v1/meetings", nil)
	req.Header.Set("Authorization", "Bearer "+expiredAccess)
	resp, _ = client.Do(req)
	if resp.StatusCode != http.StatusUnauthorized {
		t.Errorf("expected 401 Unauthorized for expired token, got %d", resp.StatusCode)
	}

	// -------------------------------------------------------------
	// 3. MEETINGS CREATION & MULTI-TENANT ISOLATION (IDOR AUDIT)
	// -------------------------------------------------------------
	t.Log("===> [TEST 3] Meeting Operations & Multi-Tenant IDOR Audit")

	// User A creates confidential meeting
	createA := `{"title": "User A Classified Strategy", "language": "en", "target_language": "id"}`
	req, _ = http.NewRequest("POST", server.URL+"/api/v1/meetings", strings.NewReader(createA))
	req.Header.Set("Authorization", "Bearer "+authA.Token)
	req.Header.Set("Content-Type", "application/json")
	resp, _ = client.Do(req)
	if resp.StatusCode != http.StatusCreated {
		t.Fatalf("expected 201 for meeting A, got %d", resp.StatusCode)
	}
	var meetingA model.Meeting
	json.NewDecoder(resp.Body).Decode(&meetingA)

	// User B creates normal meeting
	createB := `{"title": "User B Public Discussion", "language": "id"}`
	req, _ = http.NewRequest("POST", server.URL+"/api/v1/meetings", strings.NewReader(createB))
	req.Header.Set("Authorization", "Bearer "+authB.Token)
	req.Header.Set("Content-Type", "application/json")
	resp, _ = client.Do(req)
	var meetingB model.Meeting
	json.NewDecoder(resp.Body).Decode(&meetingB)

	// IDOR TEST 1: User B attempts to read User A's meeting by ID
	req, _ = http.NewRequest("GET", server.URL+"/api/v1/meetings/"+meetingA.ID, nil)
	req.Header.Set("Authorization", "Bearer "+authB.Token)
	resp, _ = client.Do(req)
	if resp.StatusCode != http.StatusNotFound {
		t.Errorf("SECURITY FLAW (IDOR Read): User B could read User A meeting! Expected 404, got %d", resp.StatusCode)
	}

	// IDOR TEST 2: User B attempts to read User A's transcript
	req, _ = http.NewRequest("GET", server.URL+"/api/v1/meetings/"+meetingA.ID+"/transcript", nil)
	req.Header.Set("Authorization", "Bearer "+authB.Token)
	resp, _ = client.Do(req)
	if resp.StatusCode != http.StatusNotFound {
		t.Errorf("SECURITY FLAW (IDOR Transcript): User B could read User A transcript! Expected 404, got %d", resp.StatusCode)
	}

	// IDOR TEST 3: User B attempts to stop User A's meeting
	req, _ = http.NewRequest("POST", server.URL+"/api/v1/meetings/"+meetingA.ID+"/stop", nil)
	req.Header.Set("Authorization", "Bearer "+authB.Token)
	resp, _ = client.Do(req)
	if resp.StatusCode != http.StatusNotFound {
		t.Errorf("SECURITY FLAW (IDOR Stop): User B could stop User A meeting! Expected 404, got %d", resp.StatusCode)
	}

	// IDOR TEST 4: User B attempts to delete User A's meeting
	req, _ = http.NewRequest("DELETE", server.URL+"/api/v1/meetings/"+meetingA.ID, nil)
	req.Header.Set("Authorization", "Bearer "+authB.Token)
	resp, _ = client.Do(req)
	if resp.StatusCode != http.StatusNotFound {
		t.Errorf("SECURITY FLAW (IDOR Delete): User B could delete User A meeting! Expected 404, got %d", resp.StatusCode)
	}

	// -------------------------------------------------------------
	// 4. WEBSOCKET STREAMING & WEBSOCKET AUTHORIZATION
	// -------------------------------------------------------------
	t.Log("===> [TEST 4] WebSocket Streaming & WebSocket Auth")

	// Negative: Connect to WebSocket with User B token for User A's meeting
	wsURL_IDOR := "ws" + strings.TrimPrefix(server.URL, "http") + fmt.Sprintf("/ws/transcribe?token=%s&meeting_id=%s", authB.Token, meetingA.ID)
	_, wsResp, err := websocket.DefaultDialer.Dial(wsURL_IDOR, nil)
	if err == nil || (wsResp != nil && wsResp.StatusCode != http.StatusNotFound) {
		t.Errorf("SECURITY FLAW (WS IDOR): User B connected to User A meeting via WS! Got status %v", wsResp)
	}

	// Negative: Connect to WebSocket without token
	wsURL_NoToken := "ws" + strings.TrimPrefix(server.URL, "http") + fmt.Sprintf("/ws/transcribe?meeting_id=%s", meetingA.ID)
	_, wsResp, err = websocket.DefaultDialer.Dial(wsURL_NoToken, nil)
	if err == nil || (wsResp != nil && wsResp.StatusCode != http.StatusUnauthorized) {
		t.Errorf("SECURITY FLAW (WS No Auth): Expected 401 without token, got status %v", wsResp)
	}

	// Positive: User A connects to User A's meeting
	wsURL_Valid := "ws" + strings.TrimPrefix(server.URL, "http") + fmt.Sprintf("/ws/transcribe?token=%s&meeting_id=%s", authA.Token, meetingA.ID)
	wsConn, _, err := websocket.DefaultDialer.Dial(wsURL_Valid, nil)
	if err != nil {
		t.Fatalf("valid websocket dial failed: %v", err)
	}
	defer wsConn.Close()

	// Read initial status
	var initEvt wsEventMessage
	wsConn.ReadJSON(&initEvt)
	if initEvt.Status != "CONNECTED" {
		t.Errorf("expected CONNECTED, got %s", initEvt.Status)
	}

	// Stream 30 packets of audio PCM
	pcmChunk := make([]byte, 2048)
	for i := range pcmChunk {
		pcmChunk[i] = byte(i % 128)
	}
	for i := 0; i < 30; i++ {
		wsConn.WriteMessage(websocket.BinaryMessage, pcmChunk)
	}

	// Stop meeting via WebSocket action
	wsConn.WriteMessage(websocket.TextMessage, []byte(`{"action": "STOP"}`))

	// Drain messages
	for {
		var evt wsEventMessage
		wsConn.SetReadDeadline(time.Now().Add(2 * time.Second))
		err := wsConn.ReadJSON(&evt)
		if err != nil {
			break
		}
		if evt.Event == "STATUS" && evt.Status == "COMPLETED" {
			break
		}
	}

	// -------------------------------------------------------------
	// 5. FULL-TEXT SEARCH LEAKAGE AUDIT
	// -------------------------------------------------------------
	t.Log("===> [TEST 5] Full-Text Search Isolation Audit")
	time.Sleep(50 * time.Millisecond)

	// User B searches for transcripts -> MUST NOT return User A's transcript chunks!
	req, _ = http.NewRequest("GET", server.URL+"/api/v1/meetings/search?q=transkripsi", nil)
	req.Header.Set("Authorization", "Bearer "+authB.Token)
	resp, _ = client.Do(req)
	var searchB struct {
		Total   int                     `json:"total"`
		Results []model.SearchResultDTO `json:"results"`
	}
	json.NewDecoder(resp.Body).Decode(&searchB)
	if searchB.Total != 0 {
		t.Errorf("SECURITY FLAW (Search Leak): User B found User A's transcript via search! Total: %d", searchB.Total)
	}

	// User A searches for their own transcripts -> MUST find them
	req, _ = http.NewRequest("GET", server.URL+"/api/v1/meetings/search?q=transkripsi", nil)
	req.Header.Set("Authorization", "Bearer "+authA.Token)
	resp, _ = client.Do(req)
	var searchA struct {
		Total   int                     `json:"total"`
		Results []model.SearchResultDTO `json:"results"`
	}
	json.NewDecoder(resp.Body).Decode(&searchA)
	if searchA.Total == 0 {
		t.Errorf("expected User A to find their own transcript, got 0")
	}

	// -------------------------------------------------------------
	// 6. LOGOUT & REFRESH TOKEN REVOCATION AUDIT
	// -------------------------------------------------------------
	t.Log("===> [TEST 6] Logout & Revoked Token Replay Audit")

	// User A logs out
	logoutReq := fmt.Sprintf(`{"refresh_token": "%s"}`, authA.RefreshToken)
	resp, _ = client.Post(server.URL+"/api/v1/auth/logout", "application/json", strings.NewReader(logoutReq))
	if resp.StatusCode != http.StatusOK {
		t.Errorf("expected 200 OK on logout, got %d", resp.StatusCode)
	}

	// Replay Attack: Try to refresh token after logout -> MUST FAIL WITH 401
	resp, _ = client.Post(server.URL+"/api/v1/auth/refresh", "application/json", strings.NewReader(logoutReq))
	if resp.StatusCode != http.StatusUnauthorized {
		t.Errorf("SECURITY FLAW (Token Replay): Revoked refresh token was accepted after logout! Status: %d", resp.StatusCode)
	}

	t.Log("===> [ALL 6 AUDIT PHASES PASSED WITHOUT VULNERABILITIES]")
}
