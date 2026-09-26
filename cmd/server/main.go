package main

import (
	"context"
	"errors"
	"fmt"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/paperalt/sovereign-speech-intelligence/internal/config"
	"github.com/paperalt/sovereign-speech-intelligence/internal/database"
	"github.com/paperalt/sovereign-speech-intelligence/internal/handler"
	"github.com/paperalt/sovereign-speech-intelligence/internal/middleware"
	"github.com/paperalt/sovereign-speech-intelligence/internal/repository"
	"github.com/paperalt/sovereign-speech-intelligence/internal/service"
)

func main() {
	cfg := config.LoadConfig()
	log.Println("[INFO] Starting Audio Transcription Backend...")
	log.Printf("[INFO] Using Database Driver: %s, Gemini Model: %s", cfg.DBDriver, cfg.GeminiModel)

	// 1. Initialize Database
	db, err := database.OpenDatabase(cfg.DBDriver, cfg.DBDSN)
	if err != nil {
		log.Fatalf("[FATAL] Failed to connect to database: %v", err)
	}
	defer db.Close()

	if err := database.Migrate(db, cfg.DBDriver); err != nil {
		log.Fatalf("[FATAL] Failed to migrate database: %v", err)
	}
	log.Println("[INFO] Database migration verified successfully")

	// 2. Initialize Repositories
	userRepo := repository.NewUserRepository(db)
	tokenRepo := repository.NewTokenRepository(db)
	groupRepo := repository.NewGroupRepository(db)
	meetingRepo := repository.NewMeetingRepository(db)
	transcriptRepo := repository.NewTranscriptRepository(db)
	summaryRepo := repository.NewSummaryRepository(db)

	// 3. Initialize Services
	geminiClient := service.NewGeminiClient(service.GeminiConfig{
		BaseURL: cfg.NinerouterURL,
		APIKey:  cfg.NinerouterKey,
		Model:   cfg.GeminiModel,
		Timeout: 45 * time.Second,
	})

	var googleValidator service.GoogleTokenValidator
	if cfg.GoogleClientID != "" {
		googleValidator = service.NewProductionGoogleValidator(cfg.GoogleClientID)
		log.Println("[INFO] Google OAuth validator initialized")
	}

	// 4. Initialize Handlers
	authHandler := handler.NewAuthHandler(userRepo, tokenRepo, googleValidator, cfg.JWTSecret, 15*time.Minute, 7*24*time.Hour)
	authHandler.SetAllowPasswordAuth(cfg.AllowPasswordAuth)
	if !cfg.AllowPasswordAuth {
		log.Println("[INFO] Exclusive Google OAuth authentication enforced (password registration disabled)")
	}
	byokClient := service.NewBYOKClient()
	meetingHandler := handler.NewMeetingHandler(userRepo, meetingRepo, transcriptRepo, summaryRepo, geminiClient)
	meetingHandler.SetBYOKClient(byokClient)
	groupHandler := handler.NewGroupHandler(groupRepo, meetingRepo)
	wsHandler := handler.NewWSHandler(userRepo, meetingRepo, transcriptRepo, summaryRepo, geminiClient, cfg.JWTSecret)
	wsHandler.SetBYOKClient(byokClient)
	subscriptionHandler := handler.NewSubscriptionHandler(userRepo, cfg.AdminSecret)
	appHandler := handler.NewAppHandler(cfg.DownloadsDir)

	// 5. Setup Routes
	mux := http.NewServeMux()

	// Health check
	mux.HandleFunc("GET /health", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		w.Write([]byte(`{"status":"ok"}`))
	})

	// App Version Check
	mux.HandleFunc("GET /api/v1/app/version", appHandler.CheckVersion)

	// Public Auth endpoints
	mux.HandleFunc("POST /api/v1/auth/register", authHandler.Register)
	mux.HandleFunc("POST /api/v1/auth/login", authHandler.Login)
	mux.HandleFunc("POST /api/v1/auth/google", authHandler.GoogleLogin)
	mux.HandleFunc("POST /api/v1/auth/refresh", authHandler.Refresh)
	mux.HandleFunc("POST /api/v1/auth/logout", authHandler.Logout)

	// Subscription & Quota routes
	authMW := middleware.RequireAuth(cfg.JWTSecret)
	mux.HandleFunc("GET /api/v1/subscription/plans", subscriptionHandler.GetPlans)
	mux.Handle("GET /api/v1/user/quota", authMW(http.HandlerFunc(subscriptionHandler.GetUserQuota)))
	mux.Handle("POST /api/v1/subscription/topup", authMW(http.HandlerFunc(subscriptionHandler.TopUp)))
	mux.Handle("POST /api/v1/subscription/redeem", authMW(http.HandlerFunc(subscriptionHandler.RedeemVoucher)))

	// Admin Voucher Management routes (Secured by X-Admin-Key / X-Admin-Secret header or Admin Role)
	mux.HandleFunc("POST /api/v1/admin/vouchers", subscriptionHandler.AdminCreateVoucher)
	mux.HandleFunc("GET /api/v1/admin/vouchers", subscriptionHandler.AdminListVouchers)
	mux.HandleFunc("DELETE /api/v1/admin/vouchers/{code}", subscriptionHandler.AdminDeleteVoucher)

	// Protected Meeting & Search endpoints
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
	mux.Handle("POST /api/v1/meetings/{id}/suggest-questions", authMW(http.HandlerFunc(meetingHandler.SuggestQuestions)))
	mux.Handle("GET /api/v1/meetings/{id}/suggest-questions", authMW(http.HandlerFunc(meetingHandler.SuggestQuestions)))
	mux.Handle("DELETE /api/v1/meetings/{id}", authMW(http.HandlerFunc(meetingHandler.Delete)))
	mux.Handle("POST /api/v1/meetings/batch-delete", authMW(http.HandlerFunc(meetingHandler.BatchDelete)))
	mux.Handle("POST /api/v1/meetings/batch-group", authMW(http.HandlerFunc(groupHandler.BatchAssignMeetingGroup)))

	// Transcript Group & Hierarchy Management routes
	mux.Handle("GET /api/v1/groups", authMW(http.HandlerFunc(groupHandler.List)))
	mux.Handle("POST /api/v1/groups", authMW(http.HandlerFunc(groupHandler.Create)))
	mux.Handle("GET /api/v1/groups/{id}", authMW(http.HandlerFunc(groupHandler.Get)))
	mux.Handle("PUT /api/v1/groups/{id}", authMW(http.HandlerFunc(groupHandler.Update)))
	mux.Handle("DELETE /api/v1/groups/{id}", authMW(http.HandlerFunc(groupHandler.Delete)))
	mux.Handle("POST /api/v1/groups/batch-delete", authMW(http.HandlerFunc(groupHandler.BatchDelete)))
	mux.Handle("PUT /api/v1/meetings/{id}/group", authMW(http.HandlerFunc(groupHandler.AssignMeeting)))

	// AI Key Validation (Live Ping Test)
	mux.Handle("POST /api/v1/ai/validate-key", authMW(http.HandlerFunc(meetingHandler.ValidateAIKey)))

	// WebSocket audio streaming endpoint
	mux.HandleFunc("/ws/transcribe", wsHandler.ServeWS)

	// Public Static Downloads
	mux.Handle("GET /downloads/", http.StripPrefix("/downloads/", http.FileServer(http.Dir(cfg.DownloadsDir))))

	// Apply Body Limiter (1MB cap) & CORS
	bodyLimitedHandler := middleware.LimitBodySize(1 << 20)(mux)
	finalHandler := middleware.EnableCORS(bodyLimitedHandler)

	srv := &http.Server{
		Addr:         cfg.BindHost + ":" + cfg.Port,
		Handler:      finalHandler,
		ReadTimeout:  60 * time.Second,
		WriteTimeout: 60 * time.Second,
		IdleTimeout:  120 * time.Second,
	}

	// 6. Graceful Shutdown listener
	shutdownErrChan := make(chan error, 1)
	go func() {
		sigChan := make(chan os.Signal, 1)
		signal.Notify(sigChan, os.Interrupt, syscall.SIGTERM)
		<-sigChan

		log.Println("[INFO] Shutting down server gracefully...")
		ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()

		shutdownErrChan <- srv.Shutdown(ctx)
	}()

	log.Printf("[INFO] Server listening on %s:%s", cfg.BindHost, cfg.Port)
	if err := srv.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
		log.Fatalf("[FATAL] Server listener error: %v", err)
	}

	if err := <-shutdownErrChan; err != nil {
		log.Printf("[ERROR] Server graceful shutdown error: %v", err)
	}
	fmt.Println("[INFO] Server stopped cleanly")
}
