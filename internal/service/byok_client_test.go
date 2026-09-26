package service

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

func TestBYOKClient_GroqTranscribe(t *testing.T) {
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Header.Get("Authorization") != "Bearer gsk_test_key_123" {
			http.Error(w, "unauthorized", http.StatusUnauthorized)
			return
		}
		if !strings.Contains(r.Header.Get("Content-Type"), "multipart/form-data") {
			http.Error(w, "bad content type", http.StatusBadRequest)
			return
		}
		w.Header().Set("Content-Type", "application/json")
		w.Write([]byte(`{"text":"Hasil transkripsi Groq Whisper Turbo berhasil."}`))
	}))
	defer ts.Close()

	client := NewBYOKClient(WithCustomGroqURLs(ts.URL, ts.URL))
	text, err := client.TranscribeAudio(context.Background(), "GROQ", "gsk_test_key_123", []byte("RIFFdummywavbytes1234567890"), "id")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if text != "Hasil transkripsi Groq Whisper Turbo berhasil." {
		t.Fatalf("expected text match, got: %s", text)
	}
}

func TestBYOKClient_GroqCompletion(t *testing.T) {
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Header.Get("Authorization") != "Bearer gsk_test_key_123" {
			http.Error(w, "unauthorized", http.StatusUnauthorized)
			return
		}
		var req map[string]interface{}
		json.NewDecoder(r.Body).Decode(&req)
		if req["model"] != "llama-3.3-70b-versatile" {
			http.Error(w, "wrong model", http.StatusBadRequest)
			return
		}
		w.Header().Set("Content-Type", "application/json")
		w.Write([]byte(`{"choices":[{"message":{"content":"Ringkasan rapat Llama 3.3 70B selesai."}}]}`))
	}))
	defer ts.Close()

	client := NewBYOKClient(WithCustomGroqURLs(ts.URL, ts.URL))
	resp, err := client.GenerateCompletion(context.Background(), "GROQ", "gsk_test_key_123", "system prompt", "user query", 0.2)
	if err != nil {
		t.Fatalf("unexpected completion error: %v", err)
	}
	if resp != "Ringkasan rapat Llama 3.3 70B selesai." {
		t.Fatalf("expected completion match, got: %s", resp)
	}
}

func TestBYOKClient_OpenAITranscribeAndCompletion(t *testing.T) {
	sttServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Header.Get("Authorization") != "Bearer sk-test-key-456" {
			http.Error(w, "unauthorized", http.StatusUnauthorized)
			return
		}
		w.Header().Set("Content-Type", "application/json")
		w.Write([]byte(`{"text":"OpenAI Whisper transcription text."}`))
	}))
	defer sttServer.Close()

	chatServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Header.Get("Authorization") != "Bearer sk-test-key-456" {
			http.Error(w, "unauthorized", http.StatusUnauthorized)
			return
		}
		w.Header().Set("Content-Type", "application/json")
		w.Write([]byte(`{"choices":[{"message":{"content":"OpenAI GPT-4o-mini summary."}}]}`))
	}))
	defer chatServer.Close()

	client := NewBYOKClient(WithCustomOpenAIURLs(sttServer.URL, chatServer.URL))
	sttText, err := client.TranscribeAudio(context.Background(), "OPENAI", "sk-test-key-456", []byte("RIFFdummywavbytes123"), "en")
	if err != nil {
		t.Fatalf("unexpected OpenAI STT error: %v", err)
	}
	if sttText != "OpenAI Whisper transcription text." {
		t.Fatalf("expected sttText match, got: %s", sttText)
	}

	chatText, err := client.GenerateCompletion(context.Background(), "OPENAI", "sk-test-key-456", "", "summarize", 0.1)
	if err != nil {
		t.Fatalf("unexpected OpenAI chat error: %v", err)
	}
	if chatText != "OpenAI GPT-4o-mini summary." {
		t.Fatalf("expected chatText match, got: %s", chatText)
	}
}

func TestBYOKClient_GeminiTranscribeAndCompletion(t *testing.T) {
	geminiServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Header.Get("Authorization") != "Bearer AIzaSyDummyKey" {
			http.Error(w, "unauthorized", http.StatusUnauthorized)
			return
		}
		w.Header().Set("Content-Type", "application/json")
		w.Write([]byte(`{"choices":[{"message":{"content":"Gemini 2.5 Flash response."}}]}`))
	}))
	defer geminiServer.Close()

	client := NewBYOKClient(WithCustomGeminiURL(geminiServer.URL))
	sttText, err := client.TranscribeAudio(context.Background(), "GEMINI", "AIzaSyDummyKey", []byte("RIFFdummywavbytes123"), "id")
	if err != nil {
		t.Fatalf("unexpected Gemini STT error: %v", err)
	}
	if sttText != "Gemini 2.5 Flash response." {
		t.Fatalf("expected sttText match, got: %s", sttText)
	}

	chatText, err := client.GenerateCompletion(context.Background(), "GEMINI", "AIzaSyDummyKey", "", "summarize", 0.2)
	if err != nil {
		t.Fatalf("unexpected Gemini chat error: %v", err)
	}
	if chatText != "Gemini 2.5 Flash response." {
		t.Fatalf("expected chatText match, got: %s", chatText)
	}
}

func TestBYOKClient_EmptyKeyAndValidation(t *testing.T) {
	client := NewBYOKClient()
	_, err := client.TranscribeAudio(context.Background(), "GROQ", "", []byte("RIFF"), "id")
	if err == nil {
		t.Fatalf("expected error for empty key")
	}

	_, err = client.GenerateCompletion(context.Background(), "GROQ", "", "sys", "usr", 0.1)
	if err == nil {
		t.Fatalf("expected error for empty key in completion")
	}

	_, err = client.TranscribeAudio(context.Background(), "UNKNOWN", "key", []byte("RIFF"), "id")
	if err == nil {
		t.Fatalf("expected error for unsupported provider")
	}
}

func TestBYOKClient_ValidateKey(t *testing.T) {
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		auth := r.Header.Get("Authorization")
		if auth == "Bearer gsk_valid_key" {
			w.WriteHeader(http.StatusOK)
			w.Write([]byte(`{"data":[]}`))
			return
		}
		if auth == "Bearer gsk_rate_limited" {
			w.WriteHeader(http.StatusTooManyRequests)
			w.Write([]byte(`{"error":"rate limit"}`))
			return
		}
		w.WriteHeader(http.StatusUnauthorized)
		w.Write([]byte(`{"error":"unauthorized"}`))
	}))
	defer ts.Close()

	client := NewBYOKClient(WithCustomGroqURLs(ts.URL, ts.URL))

	// Valid key
	res, err := client.ValidateKey(context.Background(), "GROQ", "gsk_valid_key")
	if err != nil || !res.Valid {
		t.Fatalf("expected valid result, got: %+v, err: %v", res, err)
	}

	// Invalid key
	res, err = client.ValidateKey(context.Background(), "GROQ", "gsk_bad_key")
	if err != nil || res.Valid {
		t.Fatalf("expected invalid result, got: %+v, err: %v", res, err)
	}

	// Rate limited key
	res, err = client.ValidateKey(context.Background(), "GROQ", "gsk_rate_limited")
	if err != nil || res.Valid {
		t.Fatalf("expected invalid (rate limit) result, got: %+v, err: %v", res, err)
	}

	// Empty key
	res, err = client.ValidateKey(context.Background(), "GROQ", "")
	if err != nil || res.Valid {
		t.Fatalf("expected invalid result for empty key, got: %+v", res)
	}
}
