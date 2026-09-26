package handler

import (
	"context"
	"database/sql"
	"encoding/json"
	"errors"
	"fmt"
	"log"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/paperalt/sovereign-speech-intelligence/internal/middleware"
	"github.com/paperalt/sovereign-speech-intelligence/internal/model"
	"github.com/paperalt/sovereign-speech-intelligence/internal/repository"
	"github.com/paperalt/sovereign-speech-intelligence/internal/service"
)

type MeetingHandler struct {
	userRepo       repository.UserRepository
	meetingRepo    repository.MeetingRepository
	transcriptRepo repository.TranscriptRepository
	summaryRepo    repository.SummaryRepository
	geminiClient   *service.GeminiClient
	byokClient     service.BYOKService
	questionEngine service.QuestionEngine
}

func (h *MeetingHandler) SetBYOKClient(c service.BYOKService) {
	h.byokClient = c
}

func NewMeetingHandler(
	userRepo repository.UserRepository,
	meetingRepo repository.MeetingRepository,
	transcriptRepo repository.TranscriptRepository,
	summaryRepo repository.SummaryRepository,
	geminiClient *service.GeminiClient,
) *MeetingHandler {
	var qe service.QuestionEngine
	if geminiClient != nil && transcriptRepo != nil && meetingRepo != nil {
		qe = service.NewQuestionEngine(geminiClient, transcriptRepo, meetingRepo)
	}
	return &MeetingHandler{
		userRepo:       userRepo,
		meetingRepo:    meetingRepo,
		transcriptRepo: transcriptRepo,
		summaryRepo:    summaryRepo,
		geminiClient:   geminiClient,
		byokClient:     service.NewBYOKClient(),
		questionEngine: qe,
	}
}

type createMeetingRequest struct {
	Title          string  `json:"title"`
	Language       string  `json:"language"`        // Source language e.g. "id", "en", "ja", "auto" (default: "id")
	TargetLanguage string  `json:"target_language"` // Optional translation target language
	GroupID        *string `json:"group_id"`        // Optional group assignment
}

func (h *MeetingHandler) Create(w http.ResponseWriter, r *http.Request) {
	claims := middleware.GetUserClaims(r.Context())
	if claims == nil {
		respondError(w, http.StatusUnauthorized, "unauthorized")
		return
	}

	var req createMeetingRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		respondError(w, http.StatusBadRequest, "invalid request body")
		return
	}

	req.Title = strings.TrimSpace(req.Title)
	if req.Title == "" {
		req.Title = "Untitled Meeting"
	}

	req.Language = strings.TrimSpace(strings.ToLower(req.Language))
	if req.Language == "" {
		req.Language = "id"
	}
	req.TargetLanguage = strings.TrimSpace(strings.ToLower(req.TargetLanguage))

	// Concurrency guard: user cannot have multiple simultaneous meetings in progress
	active, err := h.meetingRepo.GetActiveMeeting(r.Context(), claims.UserID)
	if err == nil && active != nil {
		respondError(w, http.StatusConflict, fmt.Sprintf("anda masih memiliki sesi rapat aktif: \"%s\"", active.Title))
		return
	}

	// Quota check: ensure user has remaining transcription quota
	if h.userRepo != nil {
		quota, qErr := h.userRepo.GetQuota(r.Context(), claims.UserID)
		if qErr == nil && !quota.IsUnlimited && quota.RemainingSeconds <= 0 {
			respondError(w, http.StatusPaymentRequired, "kuota transkripsi Anda telah habis (30 menit free tier). Silakan top up paket kuota atau lakukan langganan untuk melanjutkan.")
			return
		}
	}

	meeting, err := h.meetingRepo.Create(r.Context(), claims.UserID, req.Title, req.Language, req.TargetLanguage, req.GroupID)
	if err != nil {
		log.Printf("[MeetingHandler] Failed to create meeting for user %s: %v", claims.UserID, err)
		respondError(w, http.StatusInternalServerError, "failed to create meeting: "+err.Error())
		return
	}

	respondJSON(w, http.StatusCreated, meeting)
}

func (h *MeetingHandler) List(w http.ResponseWriter, r *http.Request) {
	claims := middleware.GetUserClaims(r.Context())
	if claims == nil {
		respondError(w, http.StatusUnauthorized, "unauthorized")
		return
	}

	limit := 20
	offset := 0

	if lStr := r.URL.Query().Get("limit"); lStr != "" {
		if val, err := strconv.Atoi(lStr); err == nil && val > 0 {
			limit = val
		}
	}
	if oStr := r.URL.Query().Get("offset"); oStr != "" {
		if val, err := strconv.Atoi(oStr); err == nil && val >= 0 {
			offset = val
		}
	}

	meetings, err := h.meetingRepo.ListByUser(r.Context(), claims.UserID, limit, offset)
	if err != nil {
		respondError(w, http.StatusInternalServerError, "failed to fetch meetings")
		return
	}
	if meetings == nil {
		meetings = []model.Meeting{}
	}

	respondJSON(w, http.StatusOK, map[string]interface{}{
		"meetings": meetings,
		"limit":    limit,
		"offset":   offset,
	})
}

func (h *MeetingHandler) GetActive(w http.ResponseWriter, r *http.Request) {
	claims := middleware.GetUserClaims(r.Context())
	if claims == nil {
		respondError(w, http.StatusUnauthorized, "unauthorized")
		return
	}

	meeting, err := h.meetingRepo.GetActiveMeeting(r.Context(), claims.UserID)
	if err != nil {
		if errors.Is(err, repository.ErrMeetingNotFound) {
			respondJSON(w, http.StatusOK, map[string]interface{}{"active": false, "meeting": nil})
			return
		}
		respondError(w, http.StatusInternalServerError, "failed to get active meeting")
		return
	}

	respondJSON(w, http.StatusOK, map[string]interface{}{"active": true, "meeting": meeting})
}

func (h *MeetingHandler) GetByID(w http.ResponseWriter, r *http.Request) {
	claims := middleware.GetUserClaims(r.Context())
	if claims == nil {
		respondError(w, http.StatusUnauthorized, "unauthorized")
		return
	}

	id := r.PathValue("id")
	if id == "" {
		respondError(w, http.StatusBadRequest, "meeting id required")
		return
	}

	meeting, err := h.meetingRepo.GetByID(r.Context(), id, claims.UserID)
	if err != nil {
		if errors.Is(err, repository.ErrMeetingNotFound) {
			respondError(w, http.StatusNotFound, "meeting not found")
			return
		}
		respondError(w, http.StatusInternalServerError, "failed to get meeting")
		return
	}

	respondJSON(w, http.StatusOK, meeting)
}

func (h *MeetingHandler) GetTranscript(w http.ResponseWriter, r *http.Request) {
	claims := middleware.GetUserClaims(r.Context())
	if claims == nil {
		respondError(w, http.StatusUnauthorized, "unauthorized")
		return
	}

	id := r.PathValue("id")
	if id == "" {
		respondError(w, http.StatusBadRequest, "meeting id required")
		return
	}

	meeting, err := h.meetingRepo.GetByID(r.Context(), id, claims.UserID)
	if err != nil {
		if errors.Is(err, repository.ErrMeetingNotFound) {
			respondError(w, http.StatusNotFound, "meeting not found")
			return
		}
		respondError(w, http.StatusInternalServerError, "failed to get meeting")
		return
	}

	chunks, err := h.transcriptRepo.GetChunksByMeeting(r.Context(), id)
	if err != nil {
		respondError(w, http.StatusInternalServerError, "failed to get transcript chunks")
		return
	}
	if chunks == nil {
		chunks = []model.TranscriptChunk{}
	}

	var sb strings.Builder
	for i, chunk := range chunks {
		if i > 0 {
			sb.WriteString(" ")
		}
		sb.WriteString(strings.TrimSpace(chunk.RawText))
	}

	// Fetch structured summary if available
	structuredSummary, _ := h.summaryRepo.GetSummaryByMeeting(r.Context(), id)

	fullTranscript := model.FullTranscriptDTO{
		MeetingID:         meeting.ID,
		Title:             meeting.Title,
		Language:          meeting.Language,
		TargetLanguage:    meeting.TargetLanguage,
		Status:            meeting.Status,
		StartedAt:         meeting.StartedAt,
		EndedAt:           meeting.EndedAt,
		DurationSec:       meeting.DurationSec,
		Summary:           meeting.Summary,
		StructuredSummary: structuredSummary,
		FullText:          sb.String(),
		TotalChunks:       len(chunks),
		Chunks:            chunks,
	}

	respondJSON(w, http.StatusOK, fullTranscript)
}

func (h *MeetingHandler) Stop(w http.ResponseWriter, r *http.Request) {
	claims := middleware.GetUserClaims(r.Context())
	if claims == nil {
		respondError(w, http.StatusUnauthorized, "unauthorized")
		return
	}

	id := r.PathValue("id")
	if id == "" {
		respondError(w, http.StatusBadRequest, "meeting id required")
		return
	}

	meeting, err := h.meetingRepo.GetByID(r.Context(), id, claims.UserID)
	if err != nil {
		if errors.Is(err, repository.ErrMeetingNotFound) {
			respondError(w, http.StatusNotFound, "meeting not found")
			return
		}
		respondError(w, http.StatusInternalServerError, "failed to get meeting")
		return
	}

	// User edge case: If meeting was stopped with 0 audio chunks, purge ghost record
	chunks, _ := h.transcriptRepo.GetChunksByMeeting(r.Context(), id)
	if len(chunks) == 0 {
		_ = h.meetingRepo.Delete(r.Context(), id, claims.UserID)
		respondJSON(w, http.StatusOK, map[string]string{"status": "DISCARDED", "message": "Empty session discarded"})
		return
	}

	if err := h.meetingRepo.UpdateStatus(r.Context(), id, claims.UserID, "COMPLETED"); err != nil {
		respondError(w, http.StatusInternalServerError, "failed to update meeting status")
		return
	}

	// Auto-summarize if summary is empty and client is configured
	if meeting.Summary == "" && h.geminiClient != nil {
		fullText, _ := h.transcriptRepo.GetFullTranscript(r.Context(), id)
		if strings.TrimSpace(fullText) != "" {
			sumLang := meeting.TargetLanguage
			if sumLang == "" {
				sumLang = meeting.Language
			}
			sumCtx, sumCancel := context.WithTimeout(context.Background(), 60*time.Second)
			structSummary, sumErr := h.geminiClient.SummarizeStructured(sumCtx, fullText, sumLang)
			sumCancel()
			if sumErr == nil && structSummary != nil {
				_ = h.meetingRepo.UpdateSummary(r.Context(), id, claims.UserID, structSummary.ExecutiveSummary)
				_, _ = h.summaryRepo.SaveSummary(r.Context(), id, structSummary.ExecutiveSummary, structSummary.KeyPoints, structSummary.ActionItems, structSummary.ModelUsed)
			}
		}
	}

	respondJSON(w, http.StatusOK, map[string]string{"status": "COMPLETED"})
}

func (h *MeetingHandler) Summarize(w http.ResponseWriter, r *http.Request) {
	claims := middleware.GetUserClaims(r.Context())
	if claims == nil {
		respondError(w, http.StatusUnauthorized, "unauthorized")
		return
	}

	id := r.PathValue("id")
	if id == "" {
		respondError(w, http.StatusBadRequest, "meeting id required")
		return
	}

	meeting, err := h.meetingRepo.GetByID(r.Context(), id, claims.UserID)
	if err != nil {
		if errors.Is(err, repository.ErrMeetingNotFound) {
			respondError(w, http.StatusNotFound, "meeting not found")
			return
		}
		respondError(w, http.StatusInternalServerError, "failed to get meeting")
		return
	}

	fullText, err := h.transcriptRepo.GetFullTranscript(r.Context(), id)
	if err != nil || strings.TrimSpace(fullText) == "" {
		respondError(w, http.StatusBadRequest, "no transcript available to summarize")
		return
	}

	sumLang := meeting.TargetLanguage
	if sumLang == "" {
		sumLang = meeting.Language
	}

	sumCtx, sumCancel := context.WithTimeout(context.Background(), 60*time.Second)
	defer sumCancel()

	llmProvider := strings.ToUpper(strings.TrimSpace(r.Header.Get("X-LLM-Provider")))
	llmKey := strings.TrimSpace(r.Header.Get("X-LLM-Key"))
	isCustomLLM := (llmProvider == "GROQ" || llmProvider == "OPENAI" || llmProvider == "GEMINI") && llmKey != ""

	var structSummary *service.StructuredSummaryDTO
	if isCustomLLM && h.byokClient != nil {
		sysPrompt := service.BuildSummarySystemPrompt(sumLang)
		userPrompt := service.BuildSummaryUserPrompt(fullText, sumLang)
		rawResp, bErr := h.byokClient.GenerateCompletion(sumCtx, llmProvider, llmKey, sysPrompt, userPrompt, 0.2)
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
				parsed.ModelUsed = llmProvider
				structSummary = &parsed
			} else {
				fallback := service.ParseMarkdownSummaryFallback(rawResp, llmProvider)
				structSummary = fallback
			}
		} else {
			log.Printf("[Summarize] BYOK completion error: %v, falling back to default", bErr)
		}
	}

	if structSummary == nil {
		if h.geminiClient == nil {
			respondError(w, http.StatusServiceUnavailable, "summarization engine not available")
			return
		}
		var err error
		structSummary, err = h.geminiClient.SummarizeStructured(sumCtx, fullText, sumLang)
		if err != nil {
			respondError(w, http.StatusInternalServerError, "failed to generate summary: "+err.Error())
			return
		}
	}

	if err := h.meetingRepo.UpdateSummary(r.Context(), id, claims.UserID, structSummary.ExecutiveSummary); err != nil {
		respondError(w, http.StatusInternalServerError, "failed to save meeting summary")
		return
	}

	savedSummary, err := h.summaryRepo.SaveSummary(r.Context(), id, structSummary.ExecutiveSummary, structSummary.KeyPoints, structSummary.ActionItems, structSummary.ModelUsed)
	if err != nil {
		respondError(w, http.StatusInternalServerError, "failed to persist structured summary")
		return
	}

	respondJSON(w, http.StatusOK, savedSummary)
}

func (h *MeetingHandler) Search(w http.ResponseWriter, r *http.Request) {
	claims := middleware.GetUserClaims(r.Context())
	if claims == nil {
		respondError(w, http.StatusUnauthorized, "unauthorized")
		return
	}

	query := r.URL.Query().Get("q")
	if strings.TrimSpace(query) == "" {
		respondError(w, http.StatusBadRequest, "query parameter 'q' is required")
		return
	}

	meetingID := r.PathValue("id") // Optional: /api/v1/meetings/{id}/search
	if meetingID == "" {
		meetingID = r.URL.Query().Get("meeting_id")
	}

	limit := 20
	if lStr := r.URL.Query().Get("limit"); lStr != "" {
		if val, err := strconv.Atoi(lStr); err == nil && val > 0 {
			limit = val
		}
	}

	results, err := h.transcriptRepo.SearchChunks(r.Context(), claims.UserID, meetingID, query, limit)
	if err != nil {
		respondError(w, http.StatusInternalServerError, "failed to execute full-text search: "+err.Error())
		return
	}

	meetings, mErr := h.meetingRepo.SearchMeetings(r.Context(), claims.UserID, query, limit)
	if mErr != nil || meetings == nil {
		meetings = []model.Meeting{}
	}

	respondJSON(w, http.StatusOK, map[string]interface{}{
		"query":    query,
		"results":  results,
		"meetings": meetings,
		"total":    len(results) + len(meetings),
	})
}

func (h *MeetingHandler) Cancel(w http.ResponseWriter, r *http.Request) {
	claims := middleware.GetUserClaims(r.Context())
	if claims == nil {
		respondError(w, http.StatusUnauthorized, "unauthorized")
		return
	}

	id := r.PathValue("id")
	if id == "" {
		respondError(w, http.StatusBadRequest, "meeting id required")
		return
	}

	if err := h.meetingRepo.Delete(r.Context(), id, claims.UserID); err != nil {
		if errors.Is(err, repository.ErrMeetingNotFound) {
			respondError(w, http.StatusNotFound, "meeting not found")
			return
		}
		respondError(w, http.StatusInternalServerError, "failed to cancel meeting")
		return
	}

	respondJSON(w, http.StatusOK, map[string]string{"status": "CANCELLED", "message": "Meeting discarded"})
}

func (h *MeetingHandler) Delete(w http.ResponseWriter, r *http.Request) {
	claims := middleware.GetUserClaims(r.Context())
	if claims == nil {
		respondError(w, http.StatusUnauthorized, "unauthorized")
		return
	}

	id := r.PathValue("id")
	if id == "" {
		respondError(w, http.StatusBadRequest, "meeting id required")
		return
	}

	if err := h.meetingRepo.Delete(r.Context(), id, claims.UserID); err != nil {
		if errors.Is(err, repository.ErrMeetingNotFound) {
			respondError(w, http.StatusNotFound, "meeting not found")
			return
		}
		respondError(w, http.StatusInternalServerError, "failed to delete meeting")
		return
	}

	respondJSON(w, http.StatusOK, map[string]string{"message": "meeting deleted"})
}

func (h *MeetingHandler) BatchDelete(w http.ResponseWriter, r *http.Request) {
	claims := middleware.GetUserClaims(r.Context())
	if claims == nil {
		respondError(w, http.StatusUnauthorized, "unauthorized")
		return
	}

	var req model.BatchDeleteMeetingsRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		respondError(w, http.StatusBadRequest, "invalid request body")
		return
	}

	deletedCount := 0
	for _, id := range req.MeetingIDs {
		id = strings.TrimSpace(id)
		if id == "" {
			continue
		}
		if err := h.meetingRepo.Delete(r.Context(), id, claims.UserID); err == nil {
			deletedCount++
		}
	}

	respondJSON(w, http.StatusOK, map[string]interface{}{
		"success":       true,
		"deleted_count": deletedCount,
	})
}

// SuggestQuestions generates on-demand grounded questions from a configurable window of meeting transcripts.
func (h *MeetingHandler) SuggestQuestions(w http.ResponseWriter, r *http.Request) {
	claims := middleware.GetUserClaims(r.Context())
	if claims == nil {
		respondError(w, http.StatusUnauthorized, "unauthorized")
		return
	}

	id := r.PathValue("id")
	if id == "" {
		respondError(w, http.StatusBadRequest, "meeting id required")
		return
	}

	if h.questionEngine == nil {
		respondError(w, http.StatusServiceUnavailable, "question suggestion engine not available")
		return
	}

	var req model.SuggestQuestionsRequest
	if r.Body != nil && r.ContentLength > 0 {
		_ = json.NewDecoder(r.Body).Decode(&req)
	}

	if req.WindowMinutes == 0 {
		if wmStr := r.URL.Query().Get("window_minutes"); wmStr != "" {
			if parsed, err := strconv.Atoi(wmStr); err == nil {
				req.WindowMinutes = parsed
			}
		}
	}
	if req.FocusTopic == "" {
		req.FocusTopic = r.URL.Query().Get("focus_topic")
	}

	resp, err := h.questionEngine.SuggestQuestions(r.Context(), id, claims.UserID, req.WindowMinutes, req.FocusTopic)
	if err != nil {
		if errors.Is(err, repository.ErrMeetingNotFound) {
			respondError(w, http.StatusNotFound, "meeting not found")
			return
		}
		respondError(w, http.StatusInternalServerError, "failed to generate question suggestions: "+err.Error())
		return
	}

	respondJSON(w, http.StatusOK, resp)
}

type validateKeyRequest struct {
	Provider string `json:"provider"`
	APIKey   string `json:"api_key"`
}

// ValidateAIKey tests live connectivity and key validity for a given AI provider.
func (h *MeetingHandler) ValidateAIKey(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		respondError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}

	var req validateKeyRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		respondError(w, http.StatusBadRequest, "invalid request body")
		return
	}

	req.Provider = strings.TrimSpace(req.Provider)
	req.APIKey = strings.TrimSpace(req.APIKey)
	if req.Provider == "" || req.APIKey == "" {
		respondError(w, http.StatusBadRequest, "provider dan api_key wajib diisi")
		return
	}

	client := h.byokClient
	if client == nil {
		client = service.NewBYOKClient()
	}

	ctx, cancel := context.WithTimeout(r.Context(), 15*time.Second)
	defer cancel()

	res, err := client.ValidateKey(ctx, req.Provider, req.APIKey)
	if err != nil {
		respondError(w, http.StatusInternalServerError, "gagal memvalidasi kunci: "+err.Error())
		return
	}

	respondJSON(w, http.StatusOK, res)
}

type updateChunkRequest struct {
	RawText string `json:"raw_text"`
}

func (h *MeetingHandler) UpdateChunk(w http.ResponseWriter, r *http.Request) {
	claims := middleware.GetUserClaims(r.Context())
	if claims == nil {
		respondError(w, http.StatusUnauthorized, "unauthorized")
		return
	}

	meetingID := r.PathValue("id")
	chunkIDStr := r.PathValue("chunk_id")
	if meetingID == "" || chunkIDStr == "" {
		respondError(w, http.StatusBadRequest, "meeting id and chunk id required")
		return
	}

	chunkID, err := strconv.ParseInt(chunkIDStr, 10, 64)
	if err != nil {
		respondError(w, http.StatusBadRequest, "invalid chunk id")
		return
	}

	// Verify meeting ownership (IDOR defense)
	_, err = h.meetingRepo.GetByID(r.Context(), meetingID, claims.UserID)
	if err != nil {
		if errors.Is(err, repository.ErrMeetingNotFound) {
			respondError(w, http.StatusNotFound, "meeting not found")
			return
		}
		respondError(w, http.StatusInternalServerError, "failed to get meeting")
		return
	}

	var req updateChunkRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		respondError(w, http.StatusBadRequest, "invalid request body")
		return
	}

	trimmed := strings.TrimSpace(req.RawText)
	if trimmed == "" {
		respondError(w, http.StatusBadRequest, "raw_text cannot be empty")
		return
	}

	updatedChunk, err := h.transcriptRepo.UpdateChunk(r.Context(), meetingID, chunkID, trimmed)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			respondError(w, http.StatusNotFound, "chunk not found")
			return
		}
		respondError(w, http.StatusInternalServerError, "failed to update chunk: "+err.Error())
		return
	}

	respondJSON(w, http.StatusOK, updatedChunk)
}

func (h *MeetingHandler) UpdateTranscript(w http.ResponseWriter, r *http.Request) {
	claims := middleware.GetUserClaims(r.Context())
	if claims == nil {
		respondError(w, http.StatusUnauthorized, "unauthorized")
		return
	}

	meetingID := r.PathValue("id")
	if meetingID == "" {
		respondError(w, http.StatusBadRequest, "meeting id required")
		return
	}

	// Verify meeting ownership (IDOR defense)
	_, err := h.meetingRepo.GetByID(r.Context(), meetingID, claims.UserID)
	if err != nil {
		if errors.Is(err, repository.ErrMeetingNotFound) {
			respondError(w, http.StatusNotFound, "meeting not found")
			return
		}
		respondError(w, http.StatusInternalServerError, "failed to get meeting")
		return
	}

	var req updateChunkRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		respondError(w, http.StatusBadRequest, "invalid request body")
		return
	}

	trimmed := strings.TrimSpace(req.RawText)
	if trimmed == "" {
		respondError(w, http.StatusBadRequest, "raw_text cannot be empty")
		return
	}

	if err := h.transcriptRepo.ReplaceFullTranscript(r.Context(), meetingID, trimmed); err != nil {
		respondError(w, http.StatusInternalServerError, "failed to update full transcript: "+err.Error())
		return
	}

	respondJSON(w, http.StatusOK, map[string]string{
		"status":  "ok",
		"message": "transcript updated successfully",
	})
}

type updateMeetingRequest struct {
	Title string `json:"title"`
}

func (h *MeetingHandler) Update(w http.ResponseWriter, r *http.Request) {
	claims := middleware.GetUserClaims(r.Context())
	if claims == nil {
		respondError(w, http.StatusUnauthorized, "unauthorized")
		return
	}

	id := r.PathValue("id")
	if id == "" {
		respondError(w, http.StatusBadRequest, "meeting id required")
		return
	}

	var req updateMeetingRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		respondError(w, http.StatusBadRequest, "invalid request body")
		return
	}

	trimmedTitle := strings.TrimSpace(req.Title)
	if trimmedTitle == "" {
		respondError(w, http.StatusBadRequest, "title cannot be empty")
		return
	}

	if err := h.meetingRepo.UpdateTitle(r.Context(), id, claims.UserID, trimmedTitle); err != nil {
		if errors.Is(err, repository.ErrMeetingNotFound) {
			respondError(w, http.StatusNotFound, "meeting not found")
			return
		}
		respondError(w, http.StatusInternalServerError, "failed to update meeting: "+err.Error())
		return
	}

	updated, err := h.meetingRepo.GetByID(r.Context(), id, claims.UserID)
	if err != nil {
		respondJSON(w, http.StatusOK, map[string]string{"status": "ok", "title": trimmedTitle})
		return
	}

	respondJSON(w, http.StatusOK, updated)
}
