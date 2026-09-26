package service

import (
	"bufio"
	"bytes"
	"context"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"math"
	"net/http"
	"strings"
	"time"
)

// GeminiConfig holds configuration for the Gemini AI service.
type GeminiConfig struct {
	BaseURL string // e.g. "http://127.0.0.1:20128/v1"
	APIKey  string
	Model   string // e.g. "ag/gemini-3.8-flash-low"
	Timeout time.Duration
}

// TranscribeOptions specifies context and multilingual options for audio transcription.
type TranscribeOptions struct {
	ContextHint    string
	SourceLanguage string // e.g. "id", "en", "ja", "auto" (default: "id")
	TargetLanguage string // if set and != SourceLanguage, translates to TargetLanguage
}

// GeminiClient handles communication with the 9Router / Gemini endpoint.
type GeminiClient struct {
	config GeminiConfig
	client *http.Client
}

// NewGeminiClient instantiates a new GeminiClient with optimized connection pooling.
func NewGeminiClient(cfg GeminiConfig) *GeminiClient {
	if cfg.Model == "" {
		cfg.Model = "ag/gemini-3.8-flash-low"
	}
	if cfg.Timeout <= 0 {
		cfg.Timeout = 45 * time.Second
	}
	cfg.BaseURL = strings.TrimRight(cfg.BaseURL, "/")
	if !strings.HasSuffix(cfg.BaseURL, "/v1") {
		cfg.BaseURL += "/v1"
	}

	transport := &http.Transport{
		MaxIdleConns:        100,
		MaxIdleConnsPerHost: 25,
		IdleConnTimeout:     90 * time.Second,
		DisableCompression: false,
	}

	return &GeminiClient{
		config: cfg,
		client: &http.Client{
			Timeout:   cfg.Timeout,
			Transport: transport,
		},
	}
}

type chatMessage struct {
	Role    string      `json:"role"`
	Content interface{} `json:"content"`
}

type chatRequest struct {
	Model       string        `json:"model"`
	Stream      bool          `json:"stream"`
	Messages    []chatMessage `json:"messages"`
	Temperature *float64      `json:"temperature,omitempty"`
}

type textPart struct {
	Type string `json:"type"`
	Text string `json:"text"`
}

type audioData struct {
	Data   string `json:"data"`
	Format string `json:"format"`
}

type audioPart struct {
	Type       string    `json:"type"`
	InputAudio audioData `json:"input_audio"`
}

type chatCompletionResponse struct {
	Choices []struct {
		Message struct {
			Content string `json:"content"`
		} `json:"message"`
	} `json:"choices"`
	Error *struct {
		Message string `json:"message"`
		Code    string `json:"code"`
	} `json:"error,omitempty"`
}

type sseChunk struct {
	Choices []struct {
		Delta struct {
			Content string `json:"content"`
		} `json:"delta"`
	} `json:"choices"`
}

// TranscribeWAV sends base64 WAV audio to the endpoint and returns verbatim transcription.
func (c *GeminiClient) TranscribeWAV(ctx context.Context, wavBytes []byte, opts TranscribeOptions) (string, error) {
	if len(wavBytes) == 0 {
		return "", fmt.Errorf("wavBytes cannot be empty")
	}

	// Silence guard: if audio is substantial (>= 0.5s) but contains dead silence (RMS < 50),
	// discard immediately to eliminate Gemini dead-air hallucinations and save tokens.
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

	audioB64 := base64.StdEncoding.EncodeToString(wavBytes)
	systemPrompt := buildSystemPrompt(opts)
	userPrompt := buildUserPrompt(opts)
	temperature := 0.1

	reqPayload := chatRequest{
		Model:       c.config.Model,
		Stream:      false,
		Temperature: &temperature,
		Messages: []chatMessage{
			{
				Role:    "system",
				Content: systemPrompt,
			},
			{
				Role: "user",
				Content: []interface{}{
					textPart{Type: "text", Text: userPrompt},
					audioPart{
						Type: "input_audio",
						InputAudio: audioData{
							Data:   audioB64,
							Format: "wav",
						},
					},
				},
			},
		},
	}

	payloadBytes, err := json.Marshal(reqPayload)
	if err != nil {
		return "", fmt.Errorf("failed to marshal request payload: %w", err)
	}

	endpointURL := fmt.Sprintf("%s/chat/completions", c.config.BaseURL)
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, endpointURL, bytes.NewReader(payloadBytes))
	if err != nil {
		return "", fmt.Errorf("failed to create http request: %w", err)
	}

	req.Header.Set("Authorization", fmt.Sprintf("Bearer %s", c.config.APIKey))
	req.Header.Set("Content-Type", "application/json")

	resp, err := c.client.Do(req)
	if err != nil {
		return "", fmt.Errorf("http request failed: %w", err)
	}
	defer resp.Body.Close()

	bodyBytes, err := io.ReadAll(resp.Body)
	if err != nil {
		return "", fmt.Errorf("failed to read response body: %w", err)
	}

	if resp.StatusCode != http.StatusOK {
		return "", fmt.Errorf("api error (status %d): %s", resp.StatusCode, string(bodyBytes))
	}

	// Case 1: Standard JSON completion
	var completion chatCompletionResponse
	if err := json.Unmarshal(bodyBytes, &completion); err == nil && len(completion.Choices) > 0 {
		if completion.Choices[0].Message.Content != "" {
			return CleanTranscriptionText(completion.Choices[0].Message.Content), nil
		}
	}

	// Case 2: SSE Stream fallback
	if strings.Contains(string(bodyBytes), "data: ") {
		var sb strings.Builder
		scanner := bufio.NewScanner(bytes.NewReader(bodyBytes))
		for scanner.Scan() {
			line := strings.TrimSpace(scanner.Text())
			if strings.HasPrefix(line, "data: ") && line != "data: [DONE]" {
				var chunk sseChunk
				jsonStr := strings.TrimPrefix(line, "data: ")
				if err := json.Unmarshal([]byte(jsonStr), &chunk); err == nil {
					if len(chunk.Choices) > 0 && chunk.Choices[0].Delta.Content != "" {
						sb.WriteString(chunk.Choices[0].Delta.Content)
					}
				}
			}
		}
		result := CleanTranscriptionText(sb.String())
		if result != "" {
			return result, nil
		}
	}

	// Case 3: Safety filter or empty response (silent audio) -> return empty string gracefully
	if strings.Contains(string(bodyBytes), "promptFeedback") || strings.Contains(string(bodyBytes), "blockReason") {
		log.Printf("[GeminiClient] Audio chunk filtered by safety policy or silent: %s", string(bodyBytes))
		return "", nil
	}

	return "", nil
}

// GenerateTextCompletion sends a prompt to Gemini with system instructions and returns the generated text.
func (c *GeminiClient) GenerateTextCompletion(ctx context.Context, systemPrompt, userPrompt string, temperature float64) (string, error) {
	if strings.TrimSpace(userPrompt) == "" {
		return "", fmt.Errorf("userPrompt cannot be empty")
	}

	var messages []map[string]interface{}
	if strings.TrimSpace(systemPrompt) != "" {
		messages = append(messages, map[string]interface{}{
			"role":    "system",
			"content": systemPrompt,
		})
	}
	messages = append(messages, map[string]interface{}{
		"role":    "user",
		"content": userPrompt,
	})

	reqPayload := map[string]interface{}{
		"model":       c.config.Model,
		"stream":      false,
		"temperature": temperature,
		"messages":    messages,
	}

	payloadBytes, err := json.Marshal(reqPayload)
	if err != nil {
		return "", fmt.Errorf("failed to marshal request payload: %w", err)
	}

	endpointURL := fmt.Sprintf("%s/chat/completions", c.config.BaseURL)
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, endpointURL, bytes.NewReader(payloadBytes))
	if err != nil {
		return "", fmt.Errorf("failed to create http request: %w", err)
	}

	req.Header.Set("Authorization", fmt.Sprintf("Bearer %s", c.config.APIKey))
	req.Header.Set("Content-Type", "application/json")

	resp, err := c.client.Do(req)
	if err != nil {
		return "", fmt.Errorf("http request failed: %w", err)
	}
	defer resp.Body.Close()

	bodyBytes, err := io.ReadAll(resp.Body)
	if err != nil {
		return "", fmt.Errorf("failed to read response body: %w", err)
	}

	if resp.StatusCode != http.StatusOK {
		return "", fmt.Errorf("api error (status %d): %s", resp.StatusCode, string(bodyBytes))
	}

	var completion chatCompletionResponse
	if err := json.Unmarshal(bodyBytes, &completion); err == nil && len(completion.Choices) > 0 {
		if completion.Choices[0].Message.Content != "" {
			return strings.TrimSpace(completion.Choices[0].Message.Content), nil
		}
	}

	if strings.Contains(string(bodyBytes), "data: ") {
		var sb strings.Builder
		scanner := bufio.NewScanner(bytes.NewReader(bodyBytes))
		for scanner.Scan() {
			line := strings.TrimSpace(scanner.Text())
			if strings.HasPrefix(line, "data: ") && line != "data: [DONE]" {
				var chunk sseChunk
				jsonStr := strings.TrimPrefix(line, "data: ")
				if err := json.Unmarshal([]byte(jsonStr), &chunk); err == nil {
					if len(chunk.Choices) > 0 && chunk.Choices[0].Delta.Content != "" {
						sb.WriteString(chunk.Choices[0].Delta.Content)
					}
				}
			}
		}
		result := strings.TrimSpace(sb.String())
		if result != "" {
			return result, nil
		}
	}

	return "", fmt.Errorf("empty or unparseable text completion: %s", string(bodyBytes))
}

// SummarizeTranscript generates an executive summary directly without preamble from a full transcript.
func (c *GeminiClient) SummarizeTranscript(ctx context.Context, fullText string, lang string) (string, error) {
	dto, err := c.SummarizeStructured(ctx, fullText, lang)
	if err != nil {
		return "", err
	}
	return dto.ExecutiveSummary, nil
}

// StructuredSummaryDTO holds structured analysis extracted from a transcript.
type StructuredSummaryDTO struct {
	ExecutiveSummary string          `json:"executive_summary"`
	KeyPoints        json.RawMessage `json:"key_points"`
	ActionItems      json.RawMessage `json:"action_items"`
	ModelUsed        string          `json:"model_used"`
}

// SummarizeStructured requests structured JSON intelligence (executive summary, key points, action items)
// without conversational filler or preambles, and with exhaustive coverage of all transcript points.
func (c *GeminiClient) SummarizeStructured(ctx context.Context, fullText string, lang string) (*StructuredSummaryDTO, error) {
	if strings.TrimSpace(fullText) == "" {
		return nil, fmt.Errorf("fullText cannot be empty")
	}

	sysPrompt := BuildSummarySystemPrompt(lang)
	userPrompt := BuildSummaryUserPrompt(fullText, lang)

	rawResp, err := c.GenerateTextCompletion(ctx, sysPrompt, userPrompt, 0.2)
	if err != nil {
		return nil, err
	}

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

	var dto StructuredSummaryDTO
	if err := json.Unmarshal([]byte(cleaned), &dto); err == nil && dto.ExecutiveSummary != "" {
		dto.ExecutiveSummary = CleanExecutiveSummary(dto.ExecutiveSummary)
		dto.ModelUsed = c.config.Model
		if len(dto.KeyPoints) == 0 {
			dto.KeyPoints = json.RawMessage("[]")
		}
		if len(dto.ActionItems) == 0 {
			dto.ActionItems = json.RawMessage("[]")
		}
		return &dto, nil
	}

	// Fallback if model responded in markdown prose
	return ParseMarkdownSummaryFallback(rawResp, c.config.Model), nil
}

func buildSystemPrompt(opts TranscribeOptions) string {
	src := strings.TrimSpace(strings.ToLower(opts.SourceLanguage))
	tgt := strings.TrimSpace(strings.ToLower(opts.TargetLanguage))

	var sb strings.Builder
	sb.WriteString("You are a specialized, verbatim Speech-To-Text (ASR) engine. Output strictly the exact spoken words in the audio verbatim without any commentary, notes, greetings, timestamps, audio descriptions, thinking process, or preamble.\n")
	sb.WriteString("1. Output strictly the transcribed words. Do not output thoughts or reasoning.\n")
	sb.WriteString("2. Do not output timestamps, audio segment breakdowns, or audio analysis.\n")
	sb.WriteString("3. If the audio is silent or unintelligible, output nothing.\n")
	sb.WriteString("4. Prior speech context may be provided solely to resolve technical terms, spelling, and sentence continuity; do not repeat or re-output the prior context itself.\n")

	if tgt != "" && tgt != src && src != "auto" && src != "" {
		sb.WriteString(fmt.Sprintf("5. Translate the spoken %s audio directly into %s text.\n", getLangName(src), getLangName(tgt)))
	} else if src == "auto" && tgt != "" {
		sb.WriteString(fmt.Sprintf("5. Detect the spoken language and translate directly into %s text.\n", getLangName(tgt)))
	} else {
		lang := src
		if lang == "" || lang == "auto" {
			lang = "id"
		}
		sb.WriteString(fmt.Sprintf("5. Transcribe verbatim in %s.\n", getLangName(lang)))
	}

	return sb.String()
}

func buildUserPrompt(opts TranscribeOptions) string {
	var sb strings.Builder
	sb.WriteString("Transcribe verbatim.")
	if opts.ContextHint != "" {
		sb.WriteString(fmt.Sprintf(" Prior context: \"%s\"", opts.ContextHint))
	}
	return sb.String()
}

func buildTranscribePrompt(opts TranscribeOptions) string {
	return buildSystemPrompt(opts)
}

// BuildSummarySystemPrompt generates the system prompt enforcing zero preamble and exhaustive coverage.
func BuildSummarySystemPrompt(lang string) string {
	langName := getLangName(lang)
	return fmt.Sprintf(`You are an elite executive intelligence analyst and academic synthesizer.
Your mission is to produce an exhaustive, thorough, and structured summary from the provided meeting/lecture transcript in %s.

CRITICAL DIRECTIVES:
1. STRICT NEGATIVE CONSTRAINT (ZERO PREAMBLE / NO CONVERSATIONAL FILLER):
   - You MUST NEVER write any opening filler, greeting, or introductory remark such as:
     * "Berikut adalah ringkasan..."
     * "Berikut ini rangkuman..."
     * "Tentu, ini adalah ringkasan..."
     * "Berdasarkan transkrip di atas..."
     * "Berikut poin-poin..."
   - The value of "executive_summary" MUST start IMMEDIATELY with the substantive subject matter and key concept.

2. TOTAL COVERAGE (ZERO OMISSION POLICY):
   - Ensure the summary is comprehensive, in-depth, and faithful to every single topic, discussion point, example, and conclusion in the transcript.
   - Do not write shallow, brief, or generic summaries.
   - In "executive_summary": Write a rich, detailed synthesis paragraph explaining the core problem, theoretical concepts, real-world case studies or practical analogies (e.g., specific domain systems discussed), current ("as-is") system analyses, non-functional/architectural requirements, and strategic recommendations.
   - In "key_points": Provide an exhaustive array of detailed strings covering EACH distinct topic and sub-topic chronologically from beginning to end so that NO discussion points are omitted.
   - In "action_items": Extract all concrete tasks, directions, deadlines, and designated assignees/roles mentioned in the transcript. If no explicit tasks were assigned, extract key recommended next steps.

3. OUTPUT FORMAT:
   - Output strictly a valid, raw JSON object matching the schema below.
   - Do NOT wrap in markdown code blocks (NO `+"```json"+` or `+"```"+`).
   - Schema:
{
  "executive_summary": "Direct, deep, and comprehensive summary paragraph in %s without any introductory preamble.",
  "key_points": [
    "Comprehensive explanation of topic 1 with context and specifics",
    "Comprehensive explanation of topic 2 with context and specifics",
    "..."
  ],
  "action_items": [
    {"task": "Action description", "assignee": "Person or role", "status": "PENDING"}
  ]
}`, langName, langName)
}

// BuildSummaryUserPrompt wraps the transcript with clear boundary markers.
func BuildSummaryUserPrompt(fullText, lang string) string {
	langName := getLangName(lang)
	return fmt.Sprintf(`--- TRANSCRIPT START ---
%s
--- TRANSCRIPT END ---

Extract exhaustive, structured intelligence in %s according to the schema. Start the executive summary directly without any introductory phrase:`, fullText, langName)
}

func buildSummaryPrompt(fullText, lang string) string {
	return BuildSummaryUserPrompt(fullText, lang)
}

func getLangName(code string) string {
	switch strings.ToLower(code) {
	case "id", "indonesia", "indonesian":
		return "Indonesian"
	case "en", "eng", "english":
		return "English"
	case "ja", "jpn", "japanese":
		return "Japanese"
	case "ar", "arabic":
		return "Arabic"
	case "zh", "chinese":
		return "Chinese"
	case "ko", "korean":
		return "Korean"
	case "de", "german":
		return "German"
	case "fr", "french":
		return "French"
	case "es", "spanish":
		return "Spanish"
	default:
		if code == "" {
			return "Indonesian"
		}
		return code
	}
}
