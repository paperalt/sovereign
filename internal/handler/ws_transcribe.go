package handler

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"math"
	"net/http"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	"github.com/gorilla/websocket"
	"github.com/paperalt/sovereign-speech-intelligence/internal/audio"
	"github.com/paperalt/sovereign-speech-intelligence/internal/model"
	"github.com/paperalt/sovereign-speech-intelligence/internal/repository"
	"github.com/paperalt/sovereign-speech-intelligence/internal/service"
	"github.com/paperalt/sovereign-speech-intelligence/pkg/token"
)

type WSHandler struct {
	userRepo       repository.UserRepository
	upgrader       websocket.Upgrader
	meetingRepo    repository.MeetingRepository
	transcriptRepo repository.TranscriptRepository
	summaryRepo    repository.SummaryRepository
	geminiClient   *service.GeminiClient
	byokClient     service.BYOKService
	questionEngine service.QuestionEngine
	jwtSecret      string
	activeMu       sync.Mutex
	activeStreams  map[string]struct{}
}

func (h *WSHandler) SetBYOKClient(c service.BYOKService) {
	h.byokClient = c
}

func NewWSHandler(
	userRepo repository.UserRepository,
	meetingRepo repository.MeetingRepository,
	transcriptRepo repository.TranscriptRepository,
	summaryRepo repository.SummaryRepository,
	geminiClient *service.GeminiClient,
	jwtSecret string,
) *WSHandler {
	var qe service.QuestionEngine
	if geminiClient != nil && transcriptRepo != nil && meetingRepo != nil {
		qe = service.NewQuestionEngine(geminiClient, transcriptRepo, meetingRepo)
	}
	return &WSHandler{
		userRepo: userRepo,
		upgrader: websocket.Upgrader{
			ReadBufferSize:  4096,
			WriteBufferSize: 4096,
			CheckOrigin: func(r *http.Request) bool {
				return true // Allows mobile client / any origin
			},
		},
		meetingRepo:    meetingRepo,
		transcriptRepo: transcriptRepo,
		summaryRepo:    summaryRepo,
		geminiClient:   geminiClient,
		byokClient:     service.NewBYOKClient(),
		questionEngine: qe,
		jwtSecret:      jwtSecret,
		activeStreams:  make(map[string]struct{}),
	}
}

type wsControlMessage struct {
	Action        string `json:"action"` // "STOP", "CANCEL", "PING", "GET_QUESTION_SUGGESTIONS"
	WindowMinutes int    `json:"window_minutes,omitempty"`
	FocusTopic    string `json:"focus_topic,omitempty"`
}

type wsEventMessage struct {
	Event                string                     `json:"event"` // "CHUNK_TRANSCRIBED", "STATUS", "SUMMARY_GENERATED", "ERROR", "QUOTA_UPDATED", "SUGGESTED_QUESTIONS"
	ChunkIndex           int                        `json:"chunk_index,omitempty"`
	StartTimeSec         float64                    `json:"start_time_sec,omitempty"`
	EndTimeSec           float64                    `json:"end_time_sec,omitempty"`
	Text                 string                     `json:"text,omitempty"`
	Summary              string                     `json:"summary,omitempty"`
	Status               string                     `json:"status,omitempty"`
	Message              string                     `json:"message,omitempty"`
	RemainingSeconds     int                        `json:"remaining_seconds,omitempty"`
	WindowMinutes        int                        `json:"window_minutes,omitempty"`
	HasSufficientContext bool                       `json:"has_sufficient_context,omitempty"`
	Suggestions          []model.QuestionSuggestion `json:"suggestions,omitempty"`
}

func (h *WSHandler) ServeWS(w http.ResponseWriter, r *http.Request) {
	// 1. Authenticate via Query Param or Header
	tokenStr := r.URL.Query().Get("token")
	if tokenStr == "" {
		authHdr := r.Header.Get("Authorization")
		if strings.HasPrefix(authHdr, "Bearer ") {
			tokenStr = strings.TrimPrefix(authHdr, "Bearer ")
		}
	}

	if tokenStr == "" {
		http.Error(w, "missing authentication token", http.StatusUnauthorized)
		return
	}

	claims, err := token.ValidateAccessToken(tokenStr, h.jwtSecret)
	if err != nil {
		http.Error(w, "invalid or expired access token", http.StatusUnauthorized)
		return
	}

	// 2. Validate Meeting ownership
	meetingID := r.URL.Query().Get("meeting_id")
	if meetingID == "" {
		http.Error(w, "missing meeting_id parameter", http.StatusBadRequest)
		return
	}

	meeting, err := h.meetingRepo.GetByID(r.Context(), meetingID, claims.UserID)
	if err != nil {
		http.Error(w, "meeting not found or unauthorized", http.StatusNotFound)
		return
	}

	if meeting.Status == "COMPLETED" {
		http.Error(w, "meeting has already been completed", http.StatusBadRequest)
		return
	}

	// Parse cross-model parameters from query params or headers
	sttProvider := strings.ToUpper(strings.TrimSpace(r.URL.Query().Get("stt_provider")))
	sttKey := strings.TrimSpace(r.URL.Query().Get("stt_key"))
	if sttProvider == "" {
		sttProvider = strings.ToUpper(strings.TrimSpace(r.URL.Query().Get("byok_provider")))
	}
	if sttKey == "" {
		sttKey = strings.TrimSpace(r.URL.Query().Get("byok_key"))
	}
	if sttProvider == "" {
		sttProvider = strings.ToUpper(strings.TrimSpace(r.Header.Get("X-STT-Provider")))
	}
	if sttKey == "" {
		sttKey = strings.TrimSpace(r.Header.Get("X-STT-Key"))
	}
	isCustomSTT := (sttProvider == "GROQ" || sttProvider == "GEMINI" || sttProvider == "OPENAI") && sttKey != ""

	llmProvider := strings.ToUpper(strings.TrimSpace(r.URL.Query().Get("llm_provider")))
	llmKey := strings.TrimSpace(r.URL.Query().Get("llm_key"))
	if llmProvider == "" {
		llmProvider = strings.ToUpper(strings.TrimSpace(r.URL.Query().Get("byok_provider")))
	}
	if llmKey == "" {
		llmKey = strings.TrimSpace(r.URL.Query().Get("byok_key"))
	}
	if llmProvider == "" {
		llmProvider = strings.ToUpper(strings.TrimSpace(r.Header.Get("X-LLM-Provider")))
	}
	if llmKey == "" {
		llmKey = strings.TrimSpace(r.Header.Get("X-LLM-Key"))
	}
	isCustomLLM := (llmProvider == "GROQ" || llmProvider == "GEMINI" || llmProvider == "OPENAI") && llmKey != ""

	// Concurrency guard: only 1 active WebSocket stream per user account
	h.activeMu.Lock()
	if _, isStreaming := h.activeStreams[claims.UserID]; isStreaming {
		h.activeMu.Unlock()
		http.Error(w, "pengguna sudah memiliki koneksi streaming aktif", http.StatusConflict)
		return
	}
	h.activeStreams[claims.UserID] = struct{}{}
	h.activeMu.Unlock()

	defer func() {
		h.activeMu.Lock()
		delete(h.activeStreams, claims.UserID)
		h.activeMu.Unlock()
	}()

	// Quota check: ensure user has quota remaining (bypassed if audio STT uses custom provider key)
	if !isCustomSTT && h.userRepo != nil {
		userQuota, qErr := h.userRepo.GetQuota(r.Context(), claims.UserID)
		if qErr == nil && !userQuota.IsUnlimited && userQuota.RemainingSeconds <= 0 {
			http.Error(w, "kuota transkripsi audio Anda telah habis; silakan gunakan Kunci Pribadi atau klaim voucher", http.StatusPaymentRequired)
			return
		}
	}

	// 3. Upgrade HTTP connection to WebSocket
	conn, err := h.upgrader.Upgrade(w, r, nil)
	if err != nil {
		log.Printf("[WS] Upgrade failed: %v", err)
		return
	}
	defer conn.Close()

	// 3-hour maximum session hard cap
	maxDurationTimer := time.AfterFunc(3*time.Hour, func() {
		log.Printf("[WS] Meeting %s exceeded 3 hours maximum duration cap; terminating", meetingID)
		conn.Close()
	})
	defer maxDurationTimer.Stop()

	// Rate limiting & DoS defense: cap maximum single frame size to 64KB
	conn.SetReadLimit(64 * 1024)

	// Heartbeat & dead connection timeout setup
	conn.SetReadDeadline(time.Now().Add(60 * time.Second))
	conn.SetPongHandler(func(string) error {
		conn.SetReadDeadline(time.Now().Add(60 * time.Second))
		return nil
	})

	var writeMu sync.Mutex
	sendJSON := func(msg wsEventMessage) {
		writeMu.Lock()
		defer writeMu.Unlock()
		conn.WriteJSON(msg)
	}

	// Active Ping Ticker
	doneChan := make(chan struct{})
	defer close(doneChan)
	go func() {
		ticker := time.NewTicker(25 * time.Second)
		defer ticker.Stop()
		for {
			select {
			case <-ticker.C:
				writeMu.Lock()
				err := conn.WriteControl(websocket.PingMessage, []byte{}, time.Now().Add(5*time.Second))
				writeMu.Unlock()
				if err != nil {
					return
				}
			case <-doneChan:
				return
			}
		}
	}()

	// 4. Initialize Stream Chunker & Sequential Processing Queue
	pipelineMode := strings.ToLower(strings.TrimSpace(r.URL.Query().Get("pipeline_mode")))
	if pipelineMode == "" {
		pipelineMode = strings.ToLower(strings.TrimSpace(r.Header.Get("X-Pipeline-Mode")))
	}
	isAdaptiveBeta := (pipelineMode == "adaptive_beta" || pipelineMode == "beta")

	cfg := audio.DefaultChunkerConfig()
	if isAdaptiveBeta {
		cfg = audio.BetaAdaptiveChunkerConfig()
	}
	chunker := audio.NewStreamChunker(cfg)

	if isAdaptiveBeta {
		sendJSON(wsEventMessage{
			Event:   "PIPELINE_MODE",
			Status:  "ADAPTIVE_BETA",
			Message: "Beta Adaptive Pipeline Active: Client-VAD Gating + Frame Compaction + 2.5s Low-Latency Chunker",
		})
	}

	// Dedicated bounded channel guaranteeing strict FIFO processing order
	chunkQueue := make(chan *audio.AudioChunk, 64)
	var workerWg sync.WaitGroup
	var rollingContext string
	var quotaExhausted atomic.Bool

	processChunk := func(chunk *audio.AudioChunk) {
		if chunk == nil || len(chunk.Data) == 0 {
			return
		}

		wavBytes, err := audio.WrapPCMToWAV(chunk.Data, cfg.SampleRate, cfg.Channels, cfg.BitsPerSample)
		if err != nil {
			log.Printf("[WS] Error wrapping WAV: %v", err)
			return
		}

		ctx, cancel := context.WithTimeout(context.Background(), 35*time.Second)
		defer cancel()

		opts := service.TranscribeOptions{
			ContextHint:    rollingContext,
			SourceLanguage: meeting.Language,
			TargetLanguage: meeting.TargetLanguage,
		}

		var transcribedText string
		var trErr error
		if isCustomSTT && h.byokClient != nil {
			transcribedText, trErr = h.byokClient.TranscribeAudio(ctx, sttProvider, sttKey, wavBytes, meeting.Language)
		} else if h.geminiClient != nil {
			transcribedText, trErr = h.geminiClient.TranscribeWAV(ctx, wavBytes, opts)
		}
		if trErr != nil {
			log.Printf("[WS] Transcription error for chunk %d (provider=%s): %v", chunk.Index, sttProvider, trErr)
			return
		}

		if transcribedText == "" {
			return
		}

		// Update monotonic rolling context (accumulate up to 400 characters of recent speech)
		if rollingContext == "" {
			rollingContext = transcribedText
		} else {
			rollingContext = rollingContext + " " + transcribedText
		}
		if len(rollingContext) > 400 {
			trimmed := rollingContext[len(rollingContext)-400:]
			if idx := strings.Index(trimmed, " "); idx != -1 {
				rollingContext = strings.TrimSpace(trimmed[idx+1:])
			} else {
				rollingContext = strings.TrimSpace(trimmed)
			}
		}

		// Persist chunk to database
		dbCtx, dbCancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer dbCancel()
		_, err = h.transcriptRepo.AddChunk(dbCtx, meetingID, chunk.Index, chunk.StartTimeSec, chunk.EndTimeSec, transcribedText)
		if err != nil {
			log.Printf("[WS] DB error saving chunk %d: %v", chunk.Index, err)
		}

		// Deduct quota based on real audio duration (bypassed if custom STT is active)
		var remSeconds int
		if !isCustomSTT && h.userRepo != nil {
			durSec := int(math.Ceil(chunk.EndTimeSec - chunk.StartTimeSec))
			if durSec <= 0 {
				durSec = 1
			}
			rem, dErr := h.userRepo.DeductQuota(dbCtx, claims.UserID, durSec)
			if dErr == nil {
				remSeconds = rem
			}
		}

		// Push event to mobile client
		sendJSON(wsEventMessage{
			Event:            "CHUNK_TRANSCRIBED",
			ChunkIndex:       chunk.Index,
			StartTimeSec:     chunk.StartTimeSec,
			EndTimeSec:       chunk.EndTimeSec,
			Text:             transcribedText,
			RemainingSeconds: remSeconds,
		})

		// Quota exhaustion check mid-stream (only for server audio quota)
		if !isCustomSTT && h.userRepo != nil && remSeconds <= 0 {
			quota, _ := h.userRepo.GetQuota(dbCtx, claims.UserID)
			if quota != nil && !quota.IsUnlimited {
				quotaExhausted.Store(true)
				sendJSON(wsEventMessage{
					Event:   "ERROR",
					Message: "Kuota transkripsi Anda telah habis. Sesi streaming difinalisasi.",
				})
				conn.Close()
				return
			}
		}
	}

	// Start sequential worker goroutine
	workerWg.Add(1)
	go func() {
		defer workerWg.Done()
		for chunk := range chunkQueue {
			processChunk(chunk)
		}
	}()

	statusMsg := "Streaming ingestion initialized"
	if isCustomSTT {
		statusMsg = fmt.Sprintf("Streaming ingestion initialized (Audio: %s Bebas Kuota)", sttProvider)
	}
	sendJSON(wsEventMessage{
		Event:   "STATUS",
		Status:  "CONNECTED",
		Message: statusMsg,
	})

	// 5. Read Loop
	var clientRequestedStop bool
	var clientRequestedCancel bool
	for {
		msgType, payload, err := conn.ReadMessage()
		if err != nil {
			break
		}
		_ = conn.SetReadDeadline(time.Now().Add(60 * time.Second))

		if msgType == websocket.BinaryMessage {
			if chunk := chunker.Push(payload); chunk != nil {
				chunkQueue <- chunk
			}
		} else if msgType == websocket.TextMessage {
			var ctrl wsControlMessage
			if err := json.Unmarshal(payload, &ctrl); err == nil {
				action := strings.ToUpper(ctrl.Action)
				if action == "STOP" {
					clientRequestedStop = true
					break
				} else if action == "CANCEL" {
					clientRequestedCancel = true
					break
				} else if action == "PAUSE" {
					// Client paused: flush in-flight audio buffer so user sees last spoken phrase immediately
					if pauseChunk := chunker.Flush(); pauseChunk != nil {
						chunkQueue <- pauseChunk
					}
					sendJSON(wsEventMessage{
						Event:   "STATUS",
						Status:  "PAUSED",
						Message: "Sesi streaming dijeda",
					})
				} else if action == "RESUME" {
					sendJSON(wsEventMessage{
						Event:   "STATUS",
						Status:  "STREAMING",
						Message: "Sesi streaming dilanjutkan",
					})
				} else if action == "VAD_SILENCE" || action == "PING" {
					// Client VAD silence keepalive to maintain active connection without audio payload
					sendJSON(wsEventMessage{
						Event: "PONG",
					})
				} else if action == "GET_QUESTION_SUGGESTIONS" || action == "SUGGEST_QUESTIONS" {
					if h.questionEngine != nil {
						go func(wm int, ft string) {
							qCtx, qCancel := context.WithTimeout(context.Background(), 25*time.Second)
							defer qCancel()
							resp, err := h.questionEngine.SuggestQuestions(qCtx, meetingID, claims.UserID, wm, ft)
							if err != nil {
								sendJSON(wsEventMessage{
									Event:   "ERROR",
									Message: "Gagal merumuskan saran pertanyaan: " + err.Error(),
								})
								return
							}
							sendJSON(wsEventMessage{
								Event:                "SUGGESTED_QUESTIONS",
								WindowMinutes:        resp.WindowMinutes,
								HasSufficientContext: resp.HasSufficientContext,
								Message:              resp.Message,
								Suggestions:          resp.Suggestions,
							})
						}(ctrl.WindowMinutes, ctrl.FocusTopic)
					}
				}
			}
		}
	}

	// 6. Cleanup: Flush remaining audio buffer and wait for worker completion
	if finalChunk := chunker.Flush(); finalChunk != nil && !clientRequestedCancel {
		chunkQueue <- finalChunk
	}
	close(chunkQueue)
	workerWg.Wait()

	// If cancelled by client, purge session immediately
	if clientRequestedCancel {
		cleanupCtx, cleanupCancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cleanupCancel()
		_ = h.meetingRepo.Delete(cleanupCtx, meetingID, claims.UserID)
		sendJSON(wsEventMessage{
			Event:   "STATUS",
			Status:  "CANCELLED",
			Message: "Meeting session cancelled and purged",
		})
		return
	}

	// 7. Auto-Summarization & Meeting Finalization if explicitly requested by client STOP action OR quota exhausted
	if clientRequestedStop || quotaExhausted.Load() {
		cleanupCtx, cleanupCancel := context.WithTimeout(context.Background(), 60*time.Second)
		defer cleanupCancel()

		fullTranscript, _ := h.transcriptRepo.GetFullTranscript(cleanupCtx, meetingID)
		if strings.TrimSpace(fullTranscript) != "" {
			sumLang := meeting.TargetLanguage
			if sumLang == "" {
				sumLang = meeting.Language
			}

			if isCustomLLM && h.byokClient != nil {
				sysPrompt := service.BuildSummarySystemPrompt(sumLang)
				userPrompt := service.BuildSummaryUserPrompt(fullTranscript, sumLang)
				rawResp, bErr := h.byokClient.GenerateCompletion(cleanupCtx, llmProvider, llmKey, sysPrompt, userPrompt, 0.2)
				if bErr == nil {
					cleaned := strings.TrimSpace(rawResp)
					if strings.HasPrefix(cleaned, "```json") {
						cleaned = strings.TrimPrefix(cleaned, "```json")
					}
					if strings.HasPrefix(cleaned, "```") {
						cleaned = strings.TrimPrefix(cleaned, "```")
					}
					if strings.HasSuffix(cleaned, "```") {
						cleaned = strings.TrimSuffix(cleaned, "```")
					}
					cleaned = strings.TrimSpace(cleaned)

					var parsed service.StructuredSummaryDTO
					if jsonErr := json.Unmarshal([]byte(cleaned), &parsed); jsonErr == nil && parsed.ExecutiveSummary != "" {
						parsed.ExecutiveSummary = service.CleanExecutiveSummary(parsed.ExecutiveSummary)
						_ = h.meetingRepo.UpdateSummary(cleanupCtx, meetingID, claims.UserID, parsed.ExecutiveSummary)
						_, _ = h.summaryRepo.SaveSummary(cleanupCtx, meetingID, parsed.ExecutiveSummary, parsed.KeyPoints, parsed.ActionItems, llmProvider)
						sendJSON(wsEventMessage{
							Event:   "SUMMARY_GENERATED",
							Summary: parsed.ExecutiveSummary,
						})
					} else {
						// Fallback parser if BYOK output markdown sections instead of raw JSON
						fallback := service.ParseMarkdownSummaryFallback(rawResp, llmProvider)
						_ = h.meetingRepo.UpdateSummary(cleanupCtx, meetingID, claims.UserID, fallback.ExecutiveSummary)
						_, _ = h.summaryRepo.SaveSummary(cleanupCtx, meetingID, fallback.ExecutiveSummary, fallback.KeyPoints, fallback.ActionItems, llmProvider)
						sendJSON(wsEventMessage{
							Event:   "SUMMARY_GENERATED",
							Summary: fallback.ExecutiveSummary,
						})
					}
				} else {
					log.Printf("[WS] BYOK summarization error: %v", bErr)
				}
			} else if h.geminiClient != nil {
				structSummary, sumErr := h.geminiClient.SummarizeStructured(cleanupCtx, fullTranscript, sumLang)
				if sumErr == nil && structSummary != nil {
					_ = h.meetingRepo.UpdateSummary(cleanupCtx, meetingID, claims.UserID, structSummary.ExecutiveSummary)
					_, _ = h.summaryRepo.SaveSummary(cleanupCtx, meetingID, structSummary.ExecutiveSummary, structSummary.KeyPoints, structSummary.ActionItems, structSummary.ModelUsed)
					sendJSON(wsEventMessage{
						Event:   "SUMMARY_GENERATED",
						Summary: structSummary.ExecutiveSummary,
					})
				} else if sumErr != nil {
					log.Printf("[WS] Auto-summarization failed for meeting %s: %v", meetingID, sumErr)
				}
			}
		}

		_ = h.meetingRepo.UpdateStatus(cleanupCtx, meetingID, claims.UserID, "COMPLETED")

		sendJSON(wsEventMessage{
			Event:   "STATUS",
			Status:  "COMPLETED",
			Message: "Meeting finalized",
		})
	} else {
		log.Printf("[WS] Client disconnected abruptly for meeting %s (buffer saved; meeting retained IN_PROGRESS for reconnect)", meetingID)
	}
}
