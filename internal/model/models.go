package model

import (
	"encoding/json"
	"time"
)

// User represents an authenticated account holder.
type User struct {
	ID               string    `json:"id"`
	Email            string    `json:"email"`
	PasswordHash     *string   `json:"-"`
	FullName         string    `json:"full_name"`
	Role             string    `json:"role"`
	GoogleID         *string   `json:"google_id,omitempty"`
	Tier             string    `json:"tier"`              // "free", "starter", "pro", "enterprise"
	QuotaSeconds     int       `json:"quota_seconds"`     // total allocated seconds
	UsedSeconds      int       `json:"used_seconds"`      // seconds consumed
	RemainingSeconds int       `json:"remaining_seconds"` // quota_seconds - used_seconds
	CreatedAt        time.Time `json:"created_at"`
	UpdatedAt        time.Time `json:"updated_at"`
}

// UserQuotaDTO represents real-time quota status for the mobile app.
type UserQuotaDTO struct {
	Tier             string `json:"tier"`
	QuotaSeconds     int    `json:"quota_seconds"`
	UsedSeconds      int    `json:"used_seconds"`
	RemainingSeconds int    `json:"remaining_seconds"`
	RemainingMinutes int    `json:"remaining_minutes"`
	IsUnlimited      bool   `json:"is_unlimited"`
}

// SubscriptionPlan represents a selectable purchase/top-up plan.
type SubscriptionPlan struct {
	ID          string `json:"id"`
	Name        string `json:"name"`
	Description string `json:"description"`
	DurationMin int    `json:"duration_min"`
	PriceIDR    int    `json:"price_idr"`
	Badge       string `json:"badge,omitempty"`
}

// QuotaTransaction represents an audit log of a quota top-up or redemption.
type QuotaTransaction struct {
	ID           string    `json:"id"`
	UserID       string    `json:"user_id"`
	PlanID       string    `json:"plan_id"`
	PlanName     string    `json:"plan_name"`
	SecondsAdded int       `json:"seconds_added"`
	AmountIDR    int       `json:"amount_idr"`
	Status       string    `json:"status"`
	CreatedAt    time.Time `json:"created_at"`
}

// PrepaidVoucher represents an administrative redeemable token for quota addition.
type PrepaidVoucher struct {
	Code            string    `json:"code"`
	DurationSeconds int       `json:"duration_seconds"`
	DurationMinutes int       `json:"duration_minutes"`
	PlanName        string    `json:"plan_name"`
	Tier            string    `json:"tier"`
	MaxUses         int       `json:"max_uses"`
	UsedCount       int       `json:"used_count"`
	IsActive        bool      `json:"is_active"`
	CreatedAt       time.Time `json:"created_at"`
}

// RefreshToken represents a persisted, revocable device session.
type RefreshToken struct {
	ID         string     `json:"id"`
	UserID     string     `json:"user_id"`
	TokenHash  string     `json:"-"`
	DeviceInfo string     `json:"device_info"`
	IPAddress  string     `json:"ip_address,omitempty"`
	ExpiresAt  time.Time  `json:"expires_at"`
	RevokedAt  *time.Time `json:"revoked_at,omitempty"`
	CreatedAt  time.Time  `json:"created_at"`
}

// TranscriptGroup represents a category or folder for organizing meetings.
type TranscriptGroup struct {
	ID               string    `json:"id"`
	UserID           string    `json:"user_id"`
	Name             string    `json:"name"`
	Description      string    `json:"description"`
	Color            string    `json:"color"`
	MeetingCount     int       `json:"meeting_count"`
	TotalDurationSec float64   `json:"total_duration_sec"`
	CreatedAt        time.Time `json:"created_at"`
	UpdatedAt        time.Time `json:"updated_at"`
}

// CreateGroupRequest represents request payload to create a new group.
type CreateGroupRequest struct {
	Name        string `json:"name"`
	Description string `json:"description,omitempty"`
	Color       string `json:"color,omitempty"`
}

// UpdateGroupRequest represents request payload to modify an existing group.
type UpdateGroupRequest struct {
	Name        string `json:"name"`
	Description string `json:"description,omitempty"`
	Color       string `json:"color,omitempty"`
}

// AssignMeetingGroupRequest represents request payload to assign/remove a meeting from a group.
type AssignMeetingGroupRequest struct {
	GroupID *string `json:"group_id"` // null to unassign
}

// BatchAssignMeetingGroupRequest represents request payload to assign/remove multiple meetings to a group.
type BatchAssignMeetingGroupRequest struct {
	MeetingIDs []string `json:"meeting_ids"`
	GroupID    *string  `json:"group_id"` // null to unassign
}

// BatchDeleteMeetingsRequest represents request payload to delete multiple meetings.
type BatchDeleteMeetingsRequest struct {
	MeetingIDs []string `json:"meeting_ids"`
}

// BatchDeleteGroupsRequest represents request payload to delete multiple groups.
type BatchDeleteGroupsRequest struct {
	GroupIDs       []string `json:"group_ids"`
	DeleteMeetings bool     `json:"delete_meetings"`
}

// Meeting represents a recorded session (meeting, lecture, discussion).
type Meeting struct {
	ID             string     `json:"id"`
	UserID         string     `json:"user_id"`
	GroupID        *string    `json:"group_id,omitempty"`
	GroupName      string     `json:"group_name,omitempty"`
	Title          string     `json:"title"`
	Language       string     `json:"language"`        // Source language, e.g. "id", "en", "ja", "auto"
	TargetLanguage string     `json:"target_language"` // Optional translation target, e.g. "id", "en"
	Status         string     `json:"status"`          // IN_PROGRESS, COMPLETED, FAILED
	StartedAt      time.Time  `json:"started_at"`
	EndedAt        *time.Time `json:"ended_at,omitempty"`
	DurationSec    float64    `json:"duration_sec"`
	Summary        string     `json:"summary,omitempty"`
	CreatedAt      time.Time  `json:"created_at"`
	UpdatedAt      time.Time  `json:"updated_at"`
}

// TranscriptChunk represents a transcribed segment of an ongoing or completed meeting.
type TranscriptChunk struct {
	ID           int64     `json:"id"`
	MeetingID    string    `json:"meeting_id"`
	ChunkIndex   int       `json:"chunk_index"`
	StartTimeSec float64   `json:"start_time_sec"`
	EndTimeSec   float64   `json:"end_time_sec"`
	RawText      string    `json:"raw_text"`
	CreatedAt    time.Time `json:"created_at"`
}

// MeetingSummary represents an AI-generated structured analysis for a meeting.
type MeetingSummary struct {
	ID               string          `json:"id"`
	MeetingID        string          `json:"meeting_id"`
	ExecutiveSummary string          `json:"executive_summary"`
	KeyPoints        json.RawMessage `json:"key_points"`   // JSON array of strings
	ActionItems      json.RawMessage `json:"action_items"` // JSON array of task objects
	ModelUsed        string          `json:"model_used"`
	CreatedAt        time.Time       `json:"created_at"`
}

// SearchResultDTO encapsulates a full-text search match in transcripts.
type SearchResultDTO struct {
	MeetingID    string  `json:"meeting_id"`
	MeetingTitle string  `json:"meeting_title"`
	ChunkIndex   int     `json:"chunk_index"`
	StartTimeSec float64 `json:"start_time_sec"`
	EndTimeSec   float64 `json:"end_time_sec"`
	RawText      string  `json:"raw_text"`
}

// FullTranscriptDTO aggregates all chunks and structured intelligence for a meeting.
type FullTranscriptDTO struct {
	MeetingID         string            `json:"meeting_id"`
	Title             string            `json:"title"`
	Language          string            `json:"language"`
	TargetLanguage    string            `json:"target_language,omitempty"`
	Status            string            `json:"status"`
	StartedAt         time.Time         `json:"started_at"`
	EndedAt           *time.Time        `json:"ended_at,omitempty"`
	DurationSec       float64           `json:"duration_sec"`
	Summary           string            `json:"summary,omitempty"`
	StructuredSummary *MeetingSummary   `json:"structured_summary,omitempty"`
	FullText          string            `json:"full_text"`
	TotalChunks       int               `json:"total_chunks"`
	Chunks            []TranscriptChunk `json:"chunks"`
}

// QuestionSuggestion represents a grounded, critical question generated from transcript context.
type QuestionSuggestion struct {
	ID             string `json:"id"`
	Question       string `json:"question"`
	Category       string `json:"category"`        // clarification, critical_edge_case, practical_impact
	ContextRef     string `json:"context_ref"`     // Direct quote or reference from transcript
	ThoughtStarter string `json:"thought_starter"` // Guidance on when or how to ask this question
}

// QuestionSuggestionResponse represents the payload delivered to client when requesting question suggestions.
type QuestionSuggestionResponse struct {
	MeetingID             string               `json:"meeting_id"`
	WindowMinutes         int                  `json:"window_minutes"`
	AnalyzedDurationSec   float64              `json:"analyzed_duration_sec"`
	WordCount             int                  `json:"word_count"`
	HasSufficientContext  bool                 `json:"has_sufficient_context"`
	Message               string               `json:"message,omitempty"`
	Suggestions           []QuestionSuggestion `json:"suggestions"`
}

// SuggestQuestionsRequest represents request body for on-demand question generation.
type SuggestQuestionsRequest struct {
	WindowMinutes int    `json:"window_minutes"` // 0 = all/full session, 5, 10, 15, 30
	FocusTopic    string `json:"focus_topic,omitempty"`
}
