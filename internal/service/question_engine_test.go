package service

import (
	"context"
	"fmt"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/paperalt/sovereign/internal/model"
	"github.com/paperalt/sovereign/internal/repository"
)

// mockMeetingRepo implements repository.MeetingRepository for testing.
type mockMeetingRepo struct {
	meetings map[string]*model.Meeting
}

func (m *mockMeetingRepo) Create(ctx context.Context, userID, title, language, targetLanguage string, groupID ...*string) (*model.Meeting, error) {
	return nil, nil
}
func (m *mockMeetingRepo) GetByID(ctx context.Context, id, userID string) (*model.Meeting, error) {
	meet, exists := m.meetings[id]
	if !exists || meet.UserID != userID {
		return nil, fmt.Errorf("meeting not found")
	}
	return meet, nil
}
func (m *mockMeetingRepo) GetActiveMeeting(ctx context.Context, userID string) (*model.Meeting, error) {
	return nil, nil
}
func (m *mockMeetingRepo) ListByUser(ctx context.Context, userID string, limit, offset int) ([]model.Meeting, error) {
	return nil, nil
}
func (m *mockMeetingRepo) ListByGroup(ctx context.Context, userID, groupID string) ([]model.Meeting, error) {
	return nil, nil
}
func (m *mockMeetingRepo) SearchMeetings(ctx context.Context, userID, queryText string, limit int) ([]model.Meeting, error) {
	return nil, nil
}
func (m *mockMeetingRepo) AssignGroup(ctx context.Context, userID, meetingID string, groupID *string) error {
	return nil
}
func (m *mockMeetingRepo) UpdateTitle(ctx context.Context, id, userID, title string) error {
	if mtg, ok := m.meetings[id]; ok {
		mtg.Title = title
		return nil
	}
	return repository.ErrMeetingNotFound
}
func (m *mockMeetingRepo) UpdateStatus(ctx context.Context, id, userID, status string) error {
	return nil
}
func (m *mockMeetingRepo) UpdateSummary(ctx context.Context, id, userID, summary string) error {
	return nil
}
func (m *mockMeetingRepo) Delete(ctx context.Context, id, userID string) error {
	return nil
}

// mockTranscriptRepo implements repository.TranscriptRepository for testing.
type mockTranscriptRepo struct {
	text     string
	duration float64
	chunks   int
}

func (m *mockTranscriptRepo) AddChunk(ctx context.Context, meetingID string, chunkIndex int, startTimeSec, endTimeSec float64, rawText string) (*model.TranscriptChunk, error) {
	return nil, nil
}
func (m *mockTranscriptRepo) GetChunksByMeeting(ctx context.Context, meetingID string) ([]model.TranscriptChunk, error) {
	return nil, nil
}
func (m *mockTranscriptRepo) GetFullTranscript(ctx context.Context, meetingID string) (string, error) {
	return m.text, nil
}
func (m *mockTranscriptRepo) GetTranscriptWindow(ctx context.Context, meetingID string, windowMinutes int) (string, float64, int, error) {
	return m.text, m.duration, m.chunks, nil
}
func (m *mockTranscriptRepo) SearchChunks(ctx context.Context, userID string, meetingID string, queryText string, limit int) ([]model.SearchResultDTO, error) {
	return nil, nil
}
func (m *mockTranscriptRepo) UpdateChunk(ctx context.Context, meetingID string, chunkID int64, rawText string) (*model.TranscriptChunk, error) {
	return &model.TranscriptChunk{ID: chunkID, MeetingID: meetingID, RawText: rawText}, nil
}
func (m *mockTranscriptRepo) ReplaceFullTranscript(ctx context.Context, meetingID string, rawText string) error {
	m.text = rawText
	return nil
}

func TestQuestionEngine_InsufficientContext(t *testing.T) {
	mRepo := &mockMeetingRepo{
		meetings: map[string]*model.Meeting{
			"m-1": {ID: "m-1", UserID: "u-1", Title: "Kuliah Ringkas", Language: "id"},
		},
	}
	// Less than 35 words
	tRepo := &mockTranscriptRepo{
		text:     "Halo selamat pagi semuanya. Hari ini kita santai saja dulu ya.",
		duration: 10.0,
		chunks:   1,
	}

	engine := NewQuestionEngine(nil, tRepo, mRepo)
	resp, err := engine.SuggestQuestions(context.Background(), "m-1", "u-1", 5, "")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if resp.HasSufficientContext {
		t.Errorf("expected HasSufficientContext to be false, got true")
	}
	if len(resp.Suggestions) != 0 {
		t.Errorf("expected 0 suggestions on insufficient context, got %d", len(resp.Suggestions))
	}
	if resp.WordCount != 11 {
		t.Errorf("expected word count 11, got %d", resp.WordCount)
	}
}

func TestQuestionEngine_SufficientContext_Success(t *testing.T) {
	mRepo := &mockMeetingRepo{
		meetings: map[string]*model.Meeting{
			"m-2": {ID: "m-2", UserID: "u-1", Title: "Kuliah Rekayasa Perangkat Lunak", Language: "id"},
		},
	}

	richText := `Pada pertemuan hari ini kita akan membahas tentang arsitektur Microservices versus Monolith. 
Dalam microservices setiap modul dipisahkan secara independen dan berkomunikasi melalui protokol HTTP atau gRPC. 
Tantangan utamanya adalah kompleksitas orkestrasi jaringan, konsistensi data terdistribusi yang membutuhkan pola Saga, 
serta latensi antar layanan yang dapat meningkat secara signifikan jika routing tidak didesain dengan benar. 
Biaya infrastruktur juga cenderung melonjak karena setiap microservice memerlukan instance container dan sistem monitoring mandiri.`

	tRepo := &mockTranscriptRepo{
		text:     richText,
		duration: 300.0,
		chunks:   5,
	}

	mockResponseJSON := `[
		{
			"id": "q1",
			"question": "Bagaimana cara menangani konsistensi data antar layanan jika salah satu step pada pola Saga mengalami kegagalan?",
			"category": "critical_edge_case",
			"context_ref": "Tantangan utamanya adalah konsistensi data terdistribusi yang membutuhkan pola Saga",
			"thought_starter": "Tanyakan saat dosen membahas kelemahan konsistensi data"
		},
		{
			"id": "q2",
			"question": "Kapan batasan pasti sebuah aplikasi sebaiknya tetap menggunakan Monolith dibanding migrasi ke Microservices mengingat lonjakan biayanya?",
			"category": "practical_impact",
			"context_ref": "Biaya infrastruktur juga cenderung melonjak karena setiap microservice memerlukan instance container",
			"thought_starter": "Bagus untuk menanyakan perbandingan trade-off biaya"
		}
	]`

	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		fmt.Fprintf(w, `{"choices":[{"message":{"content":%q}}]}`, mockResponseJSON)
	}))
	defer server.Close()

	gemini := NewGeminiClient(GeminiConfig{
		BaseURL: server.URL,
		APIKey:  "test-key",
		Model:   "test-model",
		Timeout: 5 * time.Second,
	})

	engine := NewQuestionEngine(gemini, tRepo, mRepo)
	resp, err := engine.SuggestQuestions(context.Background(), "m-2", "u-1", 15, "konsistensi data")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if !resp.HasSufficientContext {
		t.Fatalf("expected sufficient context, got false")
	}
	if len(resp.Suggestions) != 2 {
		t.Fatalf("expected 2 suggestions, got %d", len(resp.Suggestions))
	}
	if resp.Suggestions[0].Category != "critical_edge_case" {
		t.Errorf("expected category critical_edge_case, got %s", resp.Suggestions[0].Category)
	}
	if resp.Suggestions[1].Category != "practical_impact" {
		t.Errorf("expected category practical_impact, got %s", resp.Suggestions[1].Category)
	}
	if resp.Suggestions[0].ContextRef == "" {
		t.Errorf("expected non-empty context_ref for grounding")
	}
}
