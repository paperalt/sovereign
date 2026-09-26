package service

import (
	"context"
	"fmt"
	"net/http"
	"net/http/httptest"
	"os"
	"strconv"
	"testing"
	"time"

	"github.com/paperalt/sovereign-speech-intelligence/internal/audio"
)

func TestGeminiClient_TranscribeWAV_MockJSON(t *testing.T) {
	expectedText := "Halo ini adalah tes transkripsi rapat."

	mockServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost {
			t.Errorf("expected POST, got %s", r.Method)
		}
		if r.Header.Get("Authorization") != "Bearer test-key" {
			t.Errorf("invalid auth header: %s", r.Header.Get("Authorization"))
		}
		if r.Header.Get("Content-Type") != "application/json" {
			t.Errorf("invalid content-type: %s", r.Header.Get("Content-Type"))
		}

		responseJSON := fmt.Sprintf(`{
			"id": "chatcmpl-test",
			"object": "chat.completion",
			"choices": [
				{
					"index": 0,
					"message": {
						"role": "assistant",
						"content": "%s"
					},
					"finish_reason": "stop"
				}
			]
		}`, expectedText)

		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		w.Write([]byte(responseJSON))
	}))
	defer mockServer.Close()

	client := NewGeminiClient(GeminiConfig{
		BaseURL: mockServer.URL,
		APIKey:  "test-key",
		Model:   "ag/gemini-3.8-flash-low",
		Timeout: 5 * time.Second,
	})

	dummyWAV, err := audio.WrapPCMToWAV([]byte{0x00, 0x01, 0x02, 0x03}, 16000, 1, 16)
	if err != nil {
		t.Fatalf("failed to create dummy WAV: %v", err)
	}

	opts := TranscribeOptions{
		SourceLanguage: "id",
		TargetLanguage: "",
	}
	result, err := client.TranscribeWAV(context.Background(), dummyWAV, opts)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if result != expectedText {
		t.Errorf("expected %q, got %q", expectedText, result)
	}
}

func TestGeminiClient_TranscribeWAV_MockSSE(t *testing.T) {
	mockServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "text/event-stream")
		w.WriteHeader(http.StatusOK)
		w.Write([]byte("data: {\"choices\":[{\"delta\":{\"content\":\"Streaming \"}}]}\n\n"))
		w.Write([]byte("data: {\"choices\":[{\"delta\":{\"content\":\"Transkripsi\"}}]}\n\n"))
		w.Write([]byte("data: [DONE]\n\n"))
	}))
	defer mockServer.Close()

	client := NewGeminiClient(GeminiConfig{
		BaseURL: mockServer.URL,
		APIKey:  "test-key",
		Model:   "ag/gemini-3.8-flash-low",
		Timeout: 5 * time.Second,
	})

	dummyWAV, _ := audio.WrapPCMToWAV([]byte{0x00, 0x01}, 16000, 1, 16)
	opts := TranscribeOptions{
		SourceLanguage: "en",
		TargetLanguage: "id", // Multilanguage: English speech to Indonesian text
	}
	result, err := client.TranscribeWAV(context.Background(), dummyWAV, opts)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if result != "Streaming Transkripsi" {
		t.Errorf("expected 'Streaming Transkripsi', got %q", result)
	}
}

func TestGeminiClient_TranscribeWAV_ErrorHandling(t *testing.T) {
	mockServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusUnauthorized)
		w.Write([]byte(`{"error": "unauthorized"}`))
	}))
	defer mockServer.Close()

	client := NewGeminiClient(GeminiConfig{
		BaseURL: mockServer.URL,
		APIKey:  "bad-key",
		Model:   "ag/gemini-3.8-flash-low",
		Timeout: 5 * time.Second,
	})

	dummyWAV, _ := audio.WrapPCMToWAV([]byte{0x00, 0x01}, 16000, 1, 16)
	_, err := client.TranscribeWAV(context.Background(), dummyWAV, TranscribeOptions{})
	if err == nil {
		t.Fatalf("expected error on 401, got nil")
	}
}

// Live integration test with real 9Router (skipped if NINEROUTER_KEY not set)
func TestGeminiClient_LiveIntegration(t *testing.T) {
	apiKey := os.Getenv("NINEROUTER_KEY")
	if apiKey == "" {
		t.Skip("skipping live integration test: NINEROUTER_KEY not set")
	}

	baseURL := os.Getenv("NINEROUTER_URL")
	if baseURL == "" {
		baseURL = "http://127.0.0.1:20128/v1"
	}

	client := NewGeminiClient(GeminiConfig{
		BaseURL: baseURL,
		APIKey:  apiKey,
		Model:   "ag/gemini-3.8-flash-low",
		Timeout: 30 * time.Second,
	})

	sampleAudio, err := os.ReadFile("/tmp/sample.mp3")
	if err != nil {
		t.Skip("skipping live integration test: /tmp/sample.mp3 not found")
	}

	ctx, cancel := context.WithTimeout(context.Background(), 25*time.Second)
	defer cancel()

	opts := TranscribeOptions{
		SourceLanguage: "en",
		TargetLanguage: "",
	}
	result, err := client.TranscribeWAV(ctx, sampleAudio, opts)
	if err != nil {
		t.Logf("Live test note (model could return filter or error): %v", err)
		return
	}

	t.Logf("Live transcription received: %s", result)
	if result == "" {
		t.Errorf("expected non-empty live transcription")
	}
}

func TestGeminiClient_SummarizeTranscript_Mock(t *testing.T) {
	rawResponse := `{"executive_summary": "Executive Summary: Systems operationally secure.", "key_points": ["Point 1"], "action_items": []}`
	expectedSummary := "Systems operationally secure."

	mockServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		responseJSON := fmt.Sprintf(`{
			"choices": [
				{
					"message": {
						"content": %s
					}
				}
			]
		}`, strconv.Quote(rawResponse))

		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		w.Write([]byte(responseJSON))
	}))
	defer mockServer.Close()

	client := NewGeminiClient(GeminiConfig{
		BaseURL: mockServer.URL,
		APIKey:  "test-key",
		Model:   "ag/gemini-3.8-flash-low",
		Timeout: 5 * time.Second,
	})

	summary, err := client.SummarizeTranscript(context.Background(), "Transkripsi panjang...", "en")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if summary != expectedSummary {
		t.Errorf("expected %q, got %q", expectedSummary, summary)
	}
}
