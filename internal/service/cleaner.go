package service

import (
	"encoding/json"
	"regexp"
	"strings"
)

var (
	thoughtTagRegex     = regexp.MustCompile(`(?is)<(?:thought|thinking|reasoning)>.*?</(?:thought|thinking|reasoning)>`)
	thoughtWordRegex    = regexp.MustCompile(`(?i)^thought\b`)
	thoughtPrefixStrip  = regexp.MustCompile(`(?i)^thought\s*[:\n\-]*`)
	thoughtMetaLead     = regexp.MustCompile(`(?i)^(the|this|audio|speaker|listening|we|i|user|it sounds|background|in this)\b`)
	metaHeaderRegex     = regexp.MustCompile(`(?i)(?:transcribe(?:d)?\s+verbatim|verbatim\s+transcript(?:ion)?|transcript(?:ion)?|hasil\s+transkripsi|teks)\s*:\s*`)
	metaLinePrefixRegex = regexp.MustCompile(`(?i)^(the user wants|audio (?:breakdown|segment|contains)|segment breakdown)`)
	timestampLineRegex  = regexp.MustCompile(`^\*?\s*\d+:\d+\s*[-–]\s*\d+:\d+`)
	audioNoiseDescRegex = regexp.MustCompile(`(?i)^\s*[\(\[](?:background\s+noise|noise|silence|chatter|inaudible|music|audio\s+ends|sound|applause|cough|laughter)[^\)\]]*[\)\]]\s*$`)

	summaryPreambleRegex  = regexp.MustCompile(`(?is)^[\s*#_\-]*(?:(?:tentu[,!]?\s*)?(?:berikut\s+(?:ini\s+)?(?:adalah\s+)?|ini\s+adalah\s+|berdasarkan\s+[^\n:]*|dari\s+[^\n:]*)(?:ringkasan|rangkuman|kesimpulan|poin|ulasan|hasil|analisis|laporan|executive\s+summary)[^\n:]*[:\n\-]+)\s*`)
	executiveHeadingRegex = regexp.MustCompile(`(?i)^[\s*#_\-]*(?:ringkasan(?:\s+eksekutif)?|executive\s+summary)\s*(?:\([^\)]*\))?\s*[:*#_\-\s]*\s*`)

	markdownKeyPointsRegex   = regexp.MustCompile(`(?is)\*{0,2}(?:poin[- ]poin\s+penting|key\s+points|poin\s+utama)[:*#_\-\s]*\n+(.*?)(?:\n+\*{0,2}(?:action\s+items|tugas|tindak\s+lanjut)|\z)`)
	markdownActionItemsRegex = regexp.MustCompile(`(?is)\*{0,2}(?:action\s+items|tindak\s+lanjut|daftar\s+tugas)[:*#_\-\s]*\n+(.*)`)
	markdownBulletCleanRegex = regexp.MustCompile(`^[\s*\-•\d\.\)]+\s*`)
	assigneeRegex            = regexp.MustCompile(`(?i)\((?:assignee|penanggung\s+jawab|pic)[:\s]+([^)]+)\)`)
)

// CleanTranscriptionText cleanses AI thought traces, meta-analyses, audio breakdowns,
// timestamps, and repetition hallucinations from verbatim speech transcription.
func CleanTranscriptionText(raw string) string {
	text := strings.TrimSpace(raw)
	if text == "" {
		return ""
	}

	// 1. Strip XML thought tags: <thought>...</thought>
	text = thoughtTagRegex.ReplaceAllString(text, "")
	text = strings.TrimSpace(text)

	// 2. If an explicit verbatim marker exists, extract text after the last marker
	locs := metaHeaderRegex.FindAllStringIndex(text, -1)
	if len(locs) > 0 {
		lastLoc := locs[len(locs)-1]
		candidate := strings.TrimSpace(text[lastLoc[1]:])
		if candidate != "" {
			text = candidate
		}
	}

	// 3. Process line-by-line to strip thought blocks, meta lines, and timestamps
	lines := strings.Split(text, "\n")
	var cleanLines []string
	inThought := false

	for _, l := range lines {
		trimmed := strings.TrimSpace(l)
		if trimmed == "" {
			continue
		}

		// Handle "thought" lines
		if thoughtWordRegex.MatchString(trimmed) {
			inThought = true
			remainder := strings.TrimSpace(thoughtPrefixStrip.ReplaceAllString(trimmed, ""))
			if remainder != "" && !thoughtMetaLead.MatchString(remainder) {
				cleanLines = append(cleanLines, remainder)
				inThought = false
			}
			continue
		}

		if inThought {
			// Skip English reasoning sentences inside thought block
			if thoughtMetaLead.MatchString(trimmed) {
				continue
			}
			inThought = false
		}

		// Meta line filters
		if metaLinePrefixRegex.MatchString(trimmed) {
			continue
		}
		if timestampLineRegex.MatchString(trimmed) {
			continue
		}
		if audioNoiseDescRegex.MatchString(trimmed) {
			continue
		}
		if strings.HasPrefix(trimmed, "*") && (strings.Contains(trimmed, "Silence") || strings.Contains(trimmed, "noise")) {
			continue
		}

		cleanLines = append(cleanLines, trimmed)
	}

	text = strings.Join(cleanLines, " ")
	text = strings.TrimSpace(text)

	// 4. Strip wrapping quotes
	text = strings.Trim(text, `"'“”„«»`)
	text = strings.TrimSpace(text)

	// 5. Deduplicate long repeated blocks (>= 70 total chars, each half >= 35 chars)
	text = deduplicateRepeatedString(text)

	// 6. If the entire remaining string is merely an acoustic description, discard it
	if audioNoiseDescRegex.MatchString(text) {
		return ""
	}

	return strings.TrimSpace(text)
}

func deduplicateRepeatedString(s string) string {
	n := len(s)
	if n < 70 {
		return s
	}

	// Check if there's an exact repeated paragraph or monologue
	for length := n / 2; length >= 35; length-- {
		first := s[:length]
		rest := strings.TrimSpace(s[length:])
		if first == rest {
			return first
		}
	}

	return s
}

// CleanExecutiveSummary removes conversational preambles ("Berikut adalah...", "Tentu, ini adalah...", etc.)
// and redundant headers so that the executive summary directly presents substantive discussion points.
func CleanExecutiveSummary(raw string) string {
	text := strings.TrimSpace(raw)
	if text == "" {
		return ""
	}

	// 1. Strip XML thought tags: <thought>...</thought>
	text = thoughtTagRegex.ReplaceAllString(text, "")
	text = strings.TrimSpace(text)

	// 2. Iteratively strip conversational preambles and redundant section headers from beginning of text
	for {
		old := text
		text = summaryPreambleRegex.ReplaceAllString(text, "")
		text = strings.TrimSpace(text)
		text = executiveHeadingRegex.ReplaceAllString(text, "")
		text = strings.TrimSpace(text)
		if text == old {
			break
		}
	}

	// 3. Strip wrapping quotes
	text = strings.Trim(text, `"'“”„«»`)
	return strings.TrimSpace(text)
}

// ParseMarkdownSummaryFallback parses markdown-formatted summary prose into structured DTO
// when a model returns markdown text instead of strict JSON.
func ParseMarkdownSummaryFallback(raw string, model string) *StructuredSummaryDTO {
	raw = strings.TrimSpace(raw)
	if raw == "" {
		return &StructuredSummaryDTO{
			ExecutiveSummary: "",
			KeyPoints:        json.RawMessage("[]"),
			ActionItems:      json.RawMessage("[]"),
			ModelUsed:        model,
		}
	}

	execPart := raw
	var keyPoints []string
	var actionItems []map[string]string

	// Extract Key Points if present
	if loc := markdownKeyPointsRegex.FindStringSubmatchIndex(raw); len(loc) >= 4 {
		execPart = strings.TrimSpace(raw[:loc[0]])
		pointsText := raw[loc[2]:loc[3]]
		lines := strings.Split(pointsText, "\n")
		for _, l := range lines {
			cleanedLine := strings.TrimSpace(markdownBulletCleanRegex.ReplaceAllString(l, ""))
			if cleanedLine != "" {
				keyPoints = append(keyPoints, cleanedLine)
			}
		}
	}

	// Extract Action Items if present
	if loc := markdownActionItemsRegex.FindStringSubmatchIndex(raw); len(loc) >= 4 {
		if execPart == raw {
			execPart = strings.TrimSpace(raw[:loc[0]])
		}
		actionsText := raw[loc[2]:loc[3]]
		lines := strings.Split(actionsText, "\n")
		for _, l := range lines {
			cleanedLine := strings.TrimSpace(markdownBulletCleanRegex.ReplaceAllString(l, ""))
			if cleanedLine != "" {
				assignee := "-"
				task := cleanedLine
				if m := assigneeRegex.FindStringSubmatch(cleanedLine); len(m) >= 2 {
					assignee = strings.TrimSpace(m[1])
					task = strings.TrimSpace(assigneeRegex.ReplaceAllString(cleanedLine, ""))
				}
				actionItems = append(actionItems, map[string]string{
					"task":     task,
					"assignee": assignee,
					"status":   "PENDING",
				})
			}
		}
	}

	cleanedExec := CleanExecutiveSummary(execPart)
	if cleanedExec == "" {
		cleanedExec = CleanExecutiveSummary(raw)
	}

	if len(keyPoints) == 0 {
		keyPoints = []string{"Analisis materi selesai"}
	}
	if actionItems == nil {
		actionItems = []map[string]string{}
	}

	keyPointsJSON, _ := json.Marshal(keyPoints)
	actionItemsJSON, _ := json.Marshal(actionItems)

	return &StructuredSummaryDTO{
		ExecutiveSummary: cleanedExec,
		KeyPoints:        json.RawMessage(keyPointsJSON),
		ActionItems:      json.RawMessage(actionItemsJSON),
		ModelUsed:        model,
	}
}
