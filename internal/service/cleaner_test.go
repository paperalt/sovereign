package service

import (
	"testing"
)

func TestCleanTranscriptionText(t *testing.T) {
	tests := []struct {
		name     string
		input    string
		expected string
	}{
		{
			name: "Reasoning and breakdown with Transcribe verbatim header and duplicate",
			input: `The user wants an accurate Indonesian verbatim transcription of the provided audio.

Audio segment:
0:00 - 0:02: "Tes tes 1 2 3"
0:03 - 0:04: "Hiu"
0:05 - 0:08: "Halo halo tes tes tes"
0:08 - 0:10: "Lemot anjay"
0:10 - 0:13: "Anjay. Kempreng."
0:13 - 0:15: "Mas"
0:15 - 0:25: (Background noise/chatter in a station/public area)

Transcribe verbatim:
Tes tes 1 2 3. Hiu. Halo halo. Tes tes tes. Lemot anjay. Anjay. Kempreng. Mas.Tes tes 1 2 3. Hiu. Halo halo. Tes tes tes. Lemot anjay. Anjay. Kempreng. Mas.`,
			expected: "Tes tes 1 2 3. Hiu. Halo halo. Tes tes tes. Lemot anjay. Anjay. Kempreng. Mas.",
		},
		{
			name: "Thought prefix single line",
			input: `thought
Ada es krim`,
			expected: "Ada es krim",
		},
		{
			name: "Thought prefix with English reasoning",
			input: `thought
The speaker is mentioning ice cream in Indonesian.
Ada es krim`,
			expected: "Ada es krim",
		},
		{
			name: "Thought tag XML",
			input: `<thought>Listening to audio. Speaker says hello.</thought>Halo apa kabar`,
			expected: "Halo apa kabar",
		},
		{
			name: "Clean normal speech with short repetition",
			input:    "Panas ya? Panas ya?",
			expected: "Panas ya? Panas ya?",
		},
		{
			name:     "Audio description only",
			input:    "(Background noise/chatter in a station/public area)",
			expected: "",
		},
		{
			name: "Quoted speech",
			input:    `"Selamat pagi semuanya."`,
			expected: "Selamat pagi semuanya.",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got := CleanTranscriptionText(tt.input)
			if got != tt.expected {
				t.Errorf("\nInput:\n%s\nExpected:\n%q\nGot:\n%q", tt.input, tt.expected, got)
			}
		})
	}
}

func TestCleanExecutiveSummary(t *testing.T) {
	tests := []struct {
		name     string
		input    string
		expected string
	}{
		{
			name: "Preamble with bold header as in user screenshot",
			input: `Berikut adalah ringkasan eksekutif, poin-poin penting pembahasan, dan action items berdasarkan transkripsi:

**Ringkasan Eksekutif:**
Rekayasa Kebutuhan (Requirements Engineering/RE) dibahas sebagai fase krusial dan mendasar dalam Rekayasa Perangkat Lunak (RPL). Terdapat tiga tahapan utama: elisitasi kebutuhan, spesifikasi kebutuhan, dan validasi.`,
			expected: `Rekayasa Kebutuhan (Requirements Engineering/RE) dibahas sebagai fase krusial dan mendasar dalam Rekayasa Perangkat Lunak (RPL). Terdapat tiga tahapan utama: elisitasi kebutuhan, spesifikasi kebutuhan, dan validasi.`,
		},
		{
			name: "Tentu preamble single line",
			input: `Tentu, berikut adalah ringkasan eksekutif dari transkrip:
Fase elisitasi kebutuhan berfokus pada teknik wawancara dan observasi langsung.`,
			expected: `Fase elisitasi kebutuhan berfokus pada teknik wawancara dan observasi langsung.`,
		},
		{
			name: "Markdown header ## Ringkasan Eksekutif",
			input: `## Ringkasan Eksekutif:
Sistem pendaftaran klinik memerlukan integrasi validasi data nomor rekam medis secara real-time.`,
			expected: `Sistem pendaftaran klinik memerlukan integrasi validasi data nomor rekam medis secara real-time.`,
		},
		{
			name: "Executive summary in English",
			input: `Executive Summary:
The lecture covered requirement engineering phases including elicitation, specification, and validation.`,
			expected: `The lecture covered requirement engineering phases including elicitation, specification, and validation.`,
		},
		{
			name:     "Already clean text",
			input:    `Rekayasa Kebutuhan (Requirements Engineering/RE) adalah proses penemuan dan pemodelan kebutuhan sistem.`,
			expected: `Rekayasa Kebutuhan (Requirements Engineering/RE) adalah proses penemuan dan pemodelan kebutuhan sistem.`,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got := CleanExecutiveSummary(tt.input)
			if got != tt.expected {
				t.Errorf("\nInput:\n%s\nExpected:\n%q\nGot:\n%q", tt.input, tt.expected, got)
			}
		})
	}
}

func TestParseMarkdownSummaryFallback(t *testing.T) {
	input := `Berikut adalah ringkasan eksekutif, poin-poin penting pembahasan, dan action items berdasarkan transkripsi:

**Ringkasan Eksekutif:**
Rekayasa Kebutuhan (Requirements Engineering/RE) dibahas sebagai fase krusial dan mendasar dalam Rekayasa Perangkat Lunak (RPL).

**Poin-Poin Penting:**
* Pemahaman mendalam mengenai fase elisitasi, spesifikasi, dan validasi kebutuhan sistem.
* Studi kasus sistem informasi pendaftaran klinik dan manajemen stok inventori ritel.

**Action Items:**
- Melakukan analisis sistem as-is sebelum merancang sistem baru (Assignee: Tim Pengembang)
- Menyusun dokumen spesifikasi kebutuhan fungsional dan non-fungsional (Assignee: Budi)`

	dto := ParseMarkdownSummaryFallback(input, "test-model")
	if dto == nil {
		t.Fatalf("expected non-nil dto")
	}

	expectedExec := "Rekayasa Kebutuhan (Requirements Engineering/RE) dibahas sebagai fase krusial dan mendasar dalam Rekayasa Perangkat Lunak (RPL)."
	if dto.ExecutiveSummary != expectedExec {
		t.Errorf("expected exec summary %q, got %q", expectedExec, dto.ExecutiveSummary)
	}

	if string(dto.KeyPoints) == "[]" {
		t.Errorf("expected non-empty key_points")
	}
}
