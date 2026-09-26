package service

import (
	"context"
	"encoding/json"
	"fmt"
	"strings"

	"github.com/google/uuid"
	"github.com/paperalt/sovereign-speech-intelligence/internal/model"
	"github.com/paperalt/sovereign-speech-intelligence/internal/repository"
)

// QuestionEngine defines the interface for generating grounded, intelligent question suggestions.
type QuestionEngine interface {
	SuggestQuestions(ctx context.Context, meetingID, userID string, windowMinutes int, focusTopic string) (*model.QuestionSuggestionResponse, error)
}

type questionEngine struct {
	geminiClient   *GeminiClient
	transcriptRepo repository.TranscriptRepository
	meetingRepo    repository.MeetingRepository
}

// NewQuestionEngine initializes a new QuestionEngine instance.
func NewQuestionEngine(
	gemini *GeminiClient,
	transcriptRepo repository.TranscriptRepository,
	meetingRepo repository.MeetingRepository,
) QuestionEngine {
	return &questionEngine{
		geminiClient:   gemini,
		transcriptRepo: transcriptRepo,
		meetingRepo:    meetingRepo,
	}
}

// SuggestQuestions analyzes the specified time window of transcripts and returns high-signal questions.
func (e *questionEngine) SuggestQuestions(
	ctx context.Context,
	meetingID, userID string,
	windowMinutes int,
	focusTopic string,
) (*model.QuestionSuggestionResponse, error) {
	// 1. Verify meeting ownership
	meeting, err := e.meetingRepo.GetByID(ctx, meetingID, userID)
	if err != nil {
		return nil, fmt.Errorf("meeting not found or unauthorized: %w", err)
	}

	// 2. Normalize windowMinutes: 0 means full meeting
	if windowMinutes < 0 {
		windowMinutes = 0
	}

	// 3. Retrieve transcript window from repository
	transcriptText, durationSec, _, err := e.transcriptRepo.GetTranscriptWindow(ctx, meetingID, windowMinutes)
	if err != nil {
		return nil, fmt.Errorf("failed to retrieve transcript window: %w", err)
	}

	words := strings.Fields(transcriptText)
	wordCount := len(words)

	// 4. Insufficiency Guardrail: Check if context is sufficiently substantive
	if wordCount < 35 {
		return &model.QuestionSuggestionResponse{
			MeetingID:            meetingID,
			WindowMinutes:        windowMinutes,
			AnalyzedDurationSec:  durationSec,
			WordCount:            wordCount,
			HasSufficientContext: false,
			Message:              "Konteks materi pada rentang waktu ini belum cukup padat (minimal 35 kata) untuk merumuskan pertanyaan spesifik.",
			Suggestions:          []model.QuestionSuggestion{},
		}, nil
	}

	// 5. Build Grounded Prompts
	systemPrompt := `You are an expert academic and professional inquiry engine designed to generate grounded, insightful questions for live lectures, technical presentations, and executive meetings.
Your fundamental principle is ZERO HALLUCINATION.

CRITICAL ANTI-HALLUCINATION RULES:
1. Every question MUST be strictly grounded in what the speaker actually stated in the provided transcript.
2. For EVERY question, you MUST provide an exact context reference or quote from the transcript in 'context_ref' explaining what was said that triggered the question.
3. NEVER assume external facts, fictitious speakers, or unmentioned tools/methods.
4. Avoid shallow trivia or obvious definitional questions. Formulate questions in 3 distinct categories:
   - "clarification": Clarifying core concepts, ambiguous statements, or unelaborated assumptions.
   - "critical_edge_case": Stress-testing edge cases, limitations, or potential failure modes of the explained system/idea.
   - "practical_impact": Real-world execution, cost, integration trade-offs, or comparison against standard alternatives.
5. Generate 2 to 4 high-value questions in the meeting's spoken language (Indonesian by default).
6. The output MUST be a valid JSON array matching this exact schema:
[
  {
    "id": "q1",
    "question": "Kalimat pertanyaan yang tajam, kritis, dan sopan untuk ditanyakan ke pemateri",
    "category": "clarification",
    "context_ref": "Kutipan atau rujukan spesifik dari transkrip yang memicu pertanyaan ini",
    "thought_starter": "Momen yang tepat untuk menanyakan hal ini kepada pemateri"
  }
]
Output strictly raw JSON without markdown backticks or commentary.`

	var userPromptBuilder strings.Builder
	userPromptBuilder.WriteString(fmt.Sprintf("Bahasa Rapat: %s\n", meeting.Language))
	if windowMinutes > 0 {
		userPromptBuilder.WriteString(fmt.Sprintf("Rentang Analisis: %d Menit Terakhir (Durasi Rekaman: %.1f detik, Total: %d kata)\n", windowMinutes, durationSec, wordCount))
	} else {
		userPromptBuilder.WriteString(fmt.Sprintf("Rentang Analisis: Seluruh Sesi (Durasi: %.1f detik, Total: %d kata)\n", durationSec, wordCount))
	}

	if focusTopic != "" {
		userPromptBuilder.WriteString(fmt.Sprintf("Topik Fokus Khusus: \"%s\"\n", focusTopic))
	}

	userPromptBuilder.WriteString("\n--- TRANSKRIP PERBINCANGAN ---\n")
	userPromptBuilder.WriteString(transcriptText)
	userPromptBuilder.WriteString("\n--- AKHIR TRANSKRIP ---\n\nBuat 2 hingga 4 pertanyaan terbaik dalam format JSON:")

	// 6. Call Gemini with low temperature for deterministic precision
	if e.geminiClient == nil {
		return nil, fmt.Errorf("ai engine not available")
	}
	rawJSON, err := e.geminiClient.GenerateTextCompletion(ctx, systemPrompt, userPromptBuilder.String(), 0.2)
	if err != nil {
		return nil, fmt.Errorf("ai engine failed to generate questions: %w", err)
	}

	// 7. Sanitize markdown wrappers if present
	cleaned := strings.TrimSpace(rawJSON)
	if strings.HasPrefix(cleaned, "```json") {
		cleaned = strings.TrimPrefix(cleaned, "```json")
		cleaned = strings.TrimSuffix(cleaned, "```")
		cleaned = strings.TrimSpace(cleaned)
	} else if strings.HasPrefix(cleaned, "```") {
		cleaned = strings.TrimPrefix(cleaned, "```")
		cleaned = strings.TrimSuffix(cleaned, "```")
		cleaned = strings.TrimSpace(cleaned)
	}

	var suggestions []model.QuestionSuggestion
	if err := json.Unmarshal([]byte(cleaned), &suggestions); err != nil {
		return nil, fmt.Errorf("failed to parse question suggestion json (%w): %s", err, cleaned)
	}

	// Ensure each item has an ID and validated category
	for i := range suggestions {
		if suggestions[i].ID == "" {
			suggestions[i].ID = fmt.Sprintf("q_%s", uuid.NewString()[:8])
		}
		cat := strings.ToLower(suggestions[i].Category)
		if cat != "clarification" && cat != "critical_edge_case" && cat != "practical_impact" {
			suggestions[i].Category = "clarification"
		}
	}

	return &model.QuestionSuggestionResponse{
		MeetingID:            meetingID,
		WindowMinutes:        windowMinutes,
		AnalyzedDurationSec:  durationSec,
		WordCount:            wordCount,
		HasSufficientContext: true,
		Message:              fmt.Sprintf("Berhasil merumuskan %d saran pertanyaan relevan.", len(suggestions)),
		Suggestions:          suggestions,
	}, nil
}
