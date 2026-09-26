package service

import (
	"bytes"
	"context"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"io"
	"math"
	"mime/multipart"
	"net/http"
	"strings"
	"time"
)

// KeyValidationResult holds the outcome of a live ping check against an AI provider.
type KeyValidationResult struct {
	Valid     bool   `json:"valid"`
	Provider  string `json:"provider"`
	LatencyMS int64  `json:"latency_ms"`
	Message   string `json:"message"`
}

// BYOKService defines the interface for Bring Your Own Key external AI providers.
type BYOKService interface {
	TranscribeAudio(ctx context.Context, provider, apiKey string, wavBytes []byte, lang string) (string, error)
	GenerateCompletion(ctx context.Context, provider, apiKey string, systemPrompt, userPrompt string, temperature float64) (string, error)
	ValidateKey(ctx context.Context, provider, apiKey string) (*KeyValidationResult, error)
}

// BYOKClient implements BYOKService for Groq, OpenAI, and Gemini.
type BYOKClient struct {
	httpClient          *http.Client
	customGroqSTTURL    string
	customGroqChatURL   string
	customOpenAISTTURL  string
	customOpenAIChatURL string
	customGeminiBaseURL string
}

// BYOKOption configures BYOKClient.
type BYOKOption func(*BYOKClient)

// WithCustomGroqURLs overrides default Groq endpoints (useful for testing/mocking).
func WithCustomGroqURLs(sttURL, chatURL string) BYOKOption {
	return func(c *BYOKClient) {
		c.customGroqSTTURL = sttURL
		c.customGroqChatURL = chatURL
	}
}

// WithCustomOpenAIURLs overrides default OpenAI endpoints.
func WithCustomOpenAIURLs(sttURL, chatURL string) BYOKOption {
	return func(c *BYOKClient) {
		c.customOpenAISTTURL = sttURL
		c.customOpenAIChatURL = chatURL
	}
}

// WithCustomGeminiURL overrides default Gemini OpenAI-compatible base URL.
func WithCustomGeminiURL(baseURL string) BYOKOption {
	return func(c *BYOKClient) {
		c.customGeminiBaseURL = baseURL
	}
}

// NewBYOKClient instantiates a BYOKClient.
func NewBYOKClient(opts ...BYOKOption) *BYOKClient {
	c := &BYOKClient{
		httpClient: &http.Client{
			Timeout: 45 * time.Second,
		},
	}
	for _, opt := range opts {
		opt(c)
	}
	return c
}

// TranscribeAudio transcribes PCM WAV audio using the specified provider and API key.
func (c *BYOKClient) TranscribeAudio(ctx context.Context, provider, apiKey string, wavBytes []byte, lang string) (string, error) {
	if len(wavBytes) == 0 {
		return "", fmt.Errorf("wavBytes cannot be empty")
	}
	apiKey = strings.TrimSpace(apiKey)
	if apiKey == "" {
		return "", fmt.Errorf("API key is required for provider %s", provider)
	}

	// RMS Silence Guard: Discard empty dead-air audio to prevent hallucinations and save user API calls
	if len(wavBytes) > 16044 {
		pcm := wavBytes[44:]
		samples := len(pcm) / 2
		if samples > 0 {
			var sum float64
			for i := 0; i < len(pcm)-1; i += 2 {
				val := int16(pcm[i]) | (int16(pcm[i+1]) << 8)
				sum += float64(val) * float64(val)
			}
			rms := math.Sqrt(sum / float64(samples))
			if rms < 50.0 {
				return "", nil
			}
		}
	}

	upperProvider := strings.ToUpper(strings.TrimSpace(provider))
	switch upperProvider {
	case "GROQ":
		endpoint := "https://api.groq.com/openai/v1/audio/transcriptions"
		if c.customGroqSTTURL != "" {
			endpoint = c.customGroqSTTURL
		}
		return c.transcribeMultipart(ctx, endpoint, apiKey, "whisper-large-v3-turbo", wavBytes, lang)

	case "OPENAI":
		endpoint := "https://api.openai.com/v1/audio/transcriptions"
		if c.customOpenAISTTURL != "" {
			endpoint = c.customOpenAISTTURL
		}
		return c.transcribeMultipart(ctx, endpoint, apiKey, "whisper-1", wavBytes, lang)

	case "GEMINI":
		endpoint := "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions"
		if c.customGeminiBaseURL != "" {
			endpoint = fmt.Sprintf("%s/chat/completions", strings.TrimRight(c.customGeminiBaseURL, "/"))
		}
		return c.transcribeGeminiMultimodal(ctx, endpoint, apiKey, wavBytes, lang)

	default:
		return "", fmt.Errorf("unsupported BYOK provider: %s", provider)
	}
}

// GenerateCompletion calls the provider's LLM completion endpoint for summarization or inquiry generation.
func (c *BYOKClient) GenerateCompletion(ctx context.Context, provider, apiKey string, systemPrompt, userPrompt string, temperature float64) (string, error) {
	apiKey = strings.TrimSpace(apiKey)
	if apiKey == "" {
		return "", fmt.Errorf("API key is required for provider %s", provider)
	}

	upperProvider := strings.ToUpper(strings.TrimSpace(provider))
	var endpoint string
	var modelName string

	switch upperProvider {
	case "GROQ":
		endpoint = "https://api.groq.com/openai/v1/chat/completions"
		if c.customGroqChatURL != "" {
			endpoint = c.customGroqChatURL
		}
		modelName = "llama-3.3-70b-versatile"

	case "OPENAI":
		endpoint = "https://api.openai.com/v1/chat/completions"
		if c.customOpenAIChatURL != "" {
			endpoint = c.customOpenAIChatURL
		}
		modelName = "gpt-4o-mini"

	case "GEMINI":
		endpoint = "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions"
		if c.customGeminiBaseURL != "" {
			endpoint = fmt.Sprintf("%s/chat/completions", strings.TrimRight(c.customGeminiBaseURL, "/"))
		}
		modelName = "gemini-2.0-flash"

	default:
		return "", fmt.Errorf("unsupported BYOK provider: %s", provider)
	}

	messages := []map[string]string{}
	if systemPrompt != "" {
		messages = append(messages, map[string]string{
			"role":    "system",
			"content": systemPrompt,
		})
	}
	messages = append(messages, map[string]string{
		"role":    "user",
		"content": userPrompt,
	})

	reqPayload := map[string]interface{}{
		"model":       modelName,
		"stream":      false,
		"temperature": temperature,
		"messages":    messages,
	}

	payloadBytes, err := json.Marshal(reqPayload)
	if err != nil {
		return "", fmt.Errorf("failed to marshal completion request: %w", err)
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, endpoint, bytes.NewReader(payloadBytes))
	if err != nil {
		return "", fmt.Errorf("failed to create http request: %w", err)
	}
	req.Header.Set("Authorization", fmt.Sprintf("Bearer %s", apiKey))
	req.Header.Set("Content-Type", "application/json")

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return "", fmt.Errorf("byok %s completion call failed: %w", upperProvider, err)
	}
	defer resp.Body.Close()

	bodyBytes, err := io.ReadAll(resp.Body)
	if err != nil {
		return "", fmt.Errorf("failed to read completion response body: %w", err)
	}

	if resp.StatusCode != http.StatusOK {
		return "", fmt.Errorf("%s API error (HTTP %d): %s", upperProvider, resp.StatusCode, string(bodyBytes))
	}

	var chatResp struct {
		Choices []struct {
			Message struct {
				Content string `json:"content"`
			} `json:"message"`
		} `json:"choices"`
	}

	if err := json.Unmarshal(bodyBytes, &chatResp); err != nil {
		return "", fmt.Errorf("failed to parse %s completion response: %w", upperProvider, err)
	}

	if len(chatResp.Choices) == 0 {
		return "", fmt.Errorf("no completion choices returned from %s", upperProvider)
	}

	return strings.TrimSpace(chatResp.Choices[0].Message.Content), nil
}

// transcribeMultipart executes an OpenAI-compatible multipart audio transcription request.
func (c *BYOKClient) transcribeMultipart(ctx context.Context, endpoint, apiKey, modelName string, wavBytes []byte, lang string) (string, error) {
	var body bytes.Buffer
	writer := multipart.NewWriter(&body)

	// Model field
	if err := writer.WriteField("model", modelName); err != nil {
		return "", err
	}

	// Language field
	if lang != "" && lang != "auto" {
		if err := writer.WriteField("language", lang); err != nil {
			return "", err
		}
	}

	// Response format field
	if err := writer.WriteField("response_format", "json"); err != nil {
		return "", err
	}

	// Audio file field
	part, err := writer.CreateFormFile("file", "audio.wav")
	if err != nil {
		return "", fmt.Errorf("failed to create multipart form file: %w", err)
	}
	if _, err := io.Copy(part, bytes.NewReader(wavBytes)); err != nil {
		return "", fmt.Errorf("failed to write audio bytes to form: %w", err)
	}

	if err := writer.Close(); err != nil {
		return "", fmt.Errorf("failed to close multipart writer: %w", err)
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, endpoint, &body)
	if err != nil {
		return "", fmt.Errorf("failed to create multipart request: %w", err)
	}

	req.Header.Set("Authorization", fmt.Sprintf("Bearer %s", apiKey))
	req.Header.Set("Content-Type", writer.FormDataContentType())

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return "", fmt.Errorf("transcription request failed: %w", err)
	}
	defer resp.Body.Close()

	bodyBytes, err := io.ReadAll(resp.Body)
	if err != nil {
		return "", fmt.Errorf("failed to read response body: %w", err)
	}

	if resp.StatusCode != http.StatusOK {
		return "", fmt.Errorf("transcription API error (HTTP %d): %s", resp.StatusCode, string(bodyBytes))
	}

	var parsed struct {
		Text string `json:"text"`
	}
	if err := json.Unmarshal(bodyBytes, &parsed); err != nil {
		return "", fmt.Errorf("failed to parse transcription response: %w", err)
	}

	return strings.TrimSpace(parsed.Text), nil
}

// transcribeGeminiMultimodal executes a multimodal audio transcription request for Gemini.
func (c *BYOKClient) transcribeGeminiMultimodal(ctx context.Context, endpoint, apiKey string, wavBytes []byte, lang string) (string, error) {
	audioB64 := base64.StdEncoding.EncodeToString(wavBytes)
	systemPrompt := "You are a specialized verbatim Speech-To-Text (ASR) engine. Output strictly the exact spoken words in the audio verbatim without commentary, timestamps, or preamble."
	if lang == "id" {
		systemPrompt += " Transcribe verbatim in Indonesian."
	}

	reqPayload := map[string]interface{}{
		"model":       "gemini-2.0-flash",
		"stream":      false,
		"temperature": 0.1,
		"messages": []map[string]interface{}{
			{
				"role":    "system",
				"content": systemPrompt,
			},
			{
				"role": "user",
				"content": []map[string]interface{}{
					{"type": "text", "text": "Transcribe verbatim."},
					{
						"type": "input_audio",
						"input_audio": map[string]string{
							"data":   audioB64,
							"format": "wav",
						},
					},
				},
			},
		},
	}

	payloadBytes, err := json.Marshal(reqPayload)
	if err != nil {
		return "", fmt.Errorf("failed to marshal gemini payload: %w", err)
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, endpoint, bytes.NewReader(payloadBytes))
	if err != nil {
		return "", fmt.Errorf("failed to create gemini request: %w", err)
	}

	req.Header.Set("Authorization", fmt.Sprintf("Bearer %s", apiKey))
	req.Header.Set("Content-Type", "application/json")

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return "", fmt.Errorf("gemini audio transcription request failed: %w", err)
	}
	defer resp.Body.Close()

	bodyBytes, err := io.ReadAll(resp.Body)
	if err != nil {
		return "", fmt.Errorf("failed to read gemini response body: %w", err)
	}

	if resp.StatusCode != http.StatusOK {
		return "", fmt.Errorf("gemini API error (HTTP %d): %s", resp.StatusCode, string(bodyBytes))
	}

	var chatResp struct {
		Choices []struct {
			Message struct {
				Content string `json:"content"`
			} `json:"message"`
		} `json:"choices"`
	}

	if err := json.Unmarshal(bodyBytes, &chatResp); err != nil {
		return "", fmt.Errorf("failed to parse gemini transcription: %w", err)
	}

	if len(chatResp.Choices) == 0 {
		return "", fmt.Errorf("no transcription choices returned from gemini")
	}

	return strings.TrimSpace(chatResp.Choices[0].Message.Content), nil
}

// ValidateKey performs a real live lightweight check against the provider's model API to verify key validity.
func (c *BYOKClient) ValidateKey(ctx context.Context, provider, apiKey string) (*KeyValidationResult, error) {
	apiKey = strings.TrimSpace(apiKey)
	if apiKey == "" {
		return &KeyValidationResult{
			Valid:    false,
			Provider: provider,
			Message:  "API key tidak boleh kosong",
		}, nil
	}

	upperProvider := strings.ToUpper(strings.TrimSpace(provider))
	var reqURL string
	var authHeader string

	switch upperProvider {
	case "GROQ":
		reqURL = "https://api.groq.com/openai/v1/models"
		if c.customGroqChatURL != "" {
			reqURL = c.customGroqChatURL
		}
		authHeader = "Bearer " + apiKey

	case "OPENAI":
		reqURL = "https://api.openai.com/v1/models"
		if c.customOpenAIChatURL != "" {
			reqURL = c.customOpenAIChatURL
		}
		authHeader = "Bearer " + apiKey

	case "GEMINI":
		reqURL = fmt.Sprintf("https://generativelanguage.googleapis.com/v1beta/models?key=%s", apiKey)
		if c.customGeminiBaseURL != "" {
			reqURL = fmt.Sprintf("%s/models", strings.TrimRight(c.customGeminiBaseURL, "/"))
			authHeader = "Bearer " + apiKey
		}

	default:
		return &KeyValidationResult{
			Valid:    false,
			Provider: provider,
			Message:  fmt.Sprintf("Penyedia %s tidak didukung", provider),
		}, nil
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, reqURL, nil)
	if err != nil {
		return nil, fmt.Errorf("failed to create validation request: %w", err)
	}
	if authHeader != "" {
		req.Header.Set("Authorization", authHeader)
	}

	t0 := time.Now()
	resp, err := c.httpClient.Do(req)
	latency := time.Since(t0).Milliseconds()

	if err != nil {
		return &KeyValidationResult{
			Valid:     false,
			Provider:  upperProvider,
			LatencyMS: latency,
			Message:   fmt.Sprintf("Gagal terhubung ke server %s", upperProvider),
		}, nil
	}
	defer resp.Body.Close()

	if resp.StatusCode == http.StatusOK {
		return &KeyValidationResult{
			Valid:     true,
			Provider:  upperProvider,
			LatencyMS: latency,
			Message:   fmt.Sprintf("Koneksi %s Aktif & Terverifikasi (%dms)", upperProvider, latency),
		}, nil
	}

	if resp.StatusCode == http.StatusUnauthorized || resp.StatusCode == http.StatusForbidden {
		return &KeyValidationResult{
			Valid:     false,
			Provider:  upperProvider,
			LatencyMS: latency,
			Message:   fmt.Sprintf("Kunci %s tidak valid (HTTP %d)", upperProvider, resp.StatusCode),
		}, nil
	}

	if resp.StatusCode == http.StatusTooManyRequests {
		return &KeyValidationResult{
			Valid:     false,
			Provider:  upperProvider,
			LatencyMS: latency,
			Message:   fmt.Sprintf("Kunci valid namun limit rate %s terlampaui (HTTP 429)", upperProvider),
		}, nil
	}

	return &KeyValidationResult{
		Valid:     false,
		Provider:  upperProvider,
		LatencyMS: latency,
		Message:   fmt.Sprintf("Respons server %s (HTTP %d)", upperProvider, resp.StatusCode),
	}, nil
}
