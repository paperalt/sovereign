# Automatic Question Suggestion Engine
**Grounded In-Meeting Inquiry Recommendation Architecture**  
*Analysis, System Architecture, & Modular Design Documentation*

[![Release Version](https://img.shields.io/badge/Release-v2.1.0%20(Build%2042)-38BDF8?style=flat-square)](https://gate.eclipsegate.my.id/downloads/transcribe-core.apk)

---

> 🌐 **Language:** **English** | [Versi Bahasa Indonesia](AUTO_QUESTION_SUGGESTION_ENGINE_ID.md)

---

## 1. Background & Problem Statement

In corporate meetings, technical discussions, and academic lectures, participants frequently encounter cognitive overload:
1. **High Cognitive Load:** Attendees concentrate on listening and note-taking, making it challenging to formulate sharp, structured questions on the spot during open Q&A sessions.
2. **Contextual Recency Decay:** Waiting until the end of a 1–2 hour session often causes participants to forget critical nuances discussed early on.
3. **Sliding Time-Windowing Necessity:** Discussion topics shift rapidly. Relevant inquiries might concern a formula explained in the last 5 minutes or an overarching architecture module covered across the last 30 minutes.

---

## 2. Technical Token & Context Feasibility

* **Average Speech Velocity:** 130–160 words per minute.
* **Volume for 30-Minute Window:**
  * Estimated word count: $3,900 - 4,800\text{ words}$.
  * Estimated token count: $\approx 5,500 - 7,200\text{ tokens}$.
* **Gemini 3.8 Flash Context Window:**
  * Model Window: $1,000,000+\text{ tokens}$.
  * A 7,200-token payload consumes **< 0.8%** of the model's total capacity.
  * Pure text completion latency: **$\approx 1.2 - 2.0\text{ seconds}$**.
* **Conclusion:** Ingesting 30 minutes of transcript text creates zero risk of context exhaustion or unacceptable latency.

---

## 3. Anti-Hallucination Guardrails

AI question generation risks fabricating external concepts (*parametric knowledge bleed*) or magnifying ASR phoneme recognition errors. To achieve near-zero hallucination:

1. **Strict Context Grounding (Temperature = 0.2):**
   * Locks the LLM in high-precision deterministic generation mode.
   * Prompts explicitly forbid assuming unmentioned facts, fictitious tools, or external methodologies.
2. **Mandatory Verbatim Quote Citations (`context_ref`):**
   * Every generated question **must include an exact citation or quote** from the speaker's transcript explaining why the question was formulated.
3. **Weighted In-Depth Categorization:**
   * `clarification`: Clarifying core concepts, ambiguous statements, or unelaborated assumptions.
   * `critical_edge_case`: Stress-testing edge cases, limitations, or potential failure modes of the explained system.
   * `practical_impact`: Real-world execution, cost, integration trade-offs, or comparison against standard alternatives.
4. **Context Insufficiency Guardrail ($\ge 35$ Words):**
   * If the selected audio window contains only pleasantries, silence, or fewer than 35 substantive words, the engine refuses to fabricate generic questions and returns:
     `"Konteks materi pada rentang waktu ini belum cukup padat (minimal 35 kata) untuk merumuskan pertanyaan spesifik."`

---

## 4. Modular Design & Contract

### A. Sliding Time-Window Selector
Configured via `window_minutes` parameter:
* `0` : Full session from opening to current time.
* `5` : Last 5 minutes (recent slide or immediate formula).
* `10` : Last 10 minutes.
* `15` : Last 15 minutes.
* `30` : Last 30 minutes (full chapter or topic evaluation).

### B. Response Schema (JSON Contract)
```json
{
  "meeting_id": "cf09116b-1f99-4641-a682-993bf5310dda",
  "window_minutes": 15,
  "analyzed_duration_sec": 900.0,
  "word_count": 2150,
  "has_sufficient_context": true,
  "message": "Berhasil merumuskan 3 saran pertanyaan relevan.",
  "suggestions": [
    {
      "id": "q1",
      "question": "How does the system mitigate race conditions during token refresh if mobile network signal drops?",
      "category": "critical_edge_case",
      "context_ref": "Speaker stated that tokens are rotated in the background every 15 minutes without blocking UI threads.",
      "thought_starter": "Ask when the presenter discusses network synchronization architecture."
    }
  ]
}
```
