package repository

import (
	"context"
	"database/sql"
	"encoding/json"
	"errors"
	"fmt"
	"time"

	"github.com/google/uuid"
	"github.com/paperalt/sovereign/internal/model"
)

var (
	ErrSummaryNotFound = errors.New("summary not found")
)

type SummaryRepository interface {
	SaveSummary(ctx context.Context, meetingID, executiveSummary string, keyPoints, actionItems json.RawMessage, modelUsed string) (*model.MeetingSummary, error)
	GetSummaryByMeeting(ctx context.Context, meetingID string) (*model.MeetingSummary, error)
}

type sqlSummaryRepository struct {
	db *sql.DB
}

func NewSummaryRepository(db *sql.DB) SummaryRepository {
	return &sqlSummaryRepository{db: db}
}

func (r *sqlSummaryRepository) SaveSummary(
	ctx context.Context,
	meetingID, executiveSummary string,
	keyPoints, actionItems json.RawMessage,
	modelUsed string,
) (*model.MeetingSummary, error) {
	id := uuid.NewString()
	now := time.Now().UTC()

	if len(keyPoints) == 0 {
		keyPoints = json.RawMessage("[]")
	}
	if len(actionItems) == 0 {
		actionItems = json.RawMessage("[]")
	}
	if modelUsed == "" {
		modelUsed = "ag/gemini-3.8-flash-low"
	}

	// Upsert query for PostgreSQL and SQLite
	query := `INSERT INTO meeting_summaries (id, meeting_id, executive_summary, key_points, action_items, model_used, created_at)
	          VALUES ($1, $2, $3, $4, $5, $6, $7)
	          ON CONFLICT(meeting_id) DO UPDATE SET
	              executive_summary = EXCLUDED.executive_summary,
	              key_points = EXCLUDED.key_points,
	              action_items = EXCLUDED.action_items,
	              model_used = EXCLUDED.model_used,
	              created_at = EXCLUDED.created_at`

	_, err := r.db.ExecContext(ctx, query, id, meetingID, executiveSummary, string(keyPoints), string(actionItems), modelUsed, now)
	if err != nil {
		return nil, fmt.Errorf("failed to save meeting summary: %w", err)
	}

	return &model.MeetingSummary{
		ID:               id,
		MeetingID:        meetingID,
		ExecutiveSummary: executiveSummary,
		KeyPoints:        keyPoints,
		ActionItems:      actionItems,
		ModelUsed:        modelUsed,
		CreatedAt:        now,
	}, nil
}

func (r *sqlSummaryRepository) GetSummaryByMeeting(ctx context.Context, meetingID string) (*model.MeetingSummary, error) {
	query := `SELECT id, meeting_id, executive_summary, key_points, action_items, model_used, created_at
	          FROM meeting_summaries
	          WHERE meeting_id = $1`

	var s model.MeetingSummary
	var kpStr, aiStr string

	err := r.db.QueryRowContext(ctx, query, meetingID).Scan(
		&s.ID, &s.MeetingID, &s.ExecutiveSummary, &kpStr, &aiStr, &s.ModelUsed, &s.CreatedAt,
	)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, ErrSummaryNotFound
		}
		return nil, err
	}

	s.KeyPoints = json.RawMessage(kpStr)
	s.ActionItems = json.RawMessage(aiStr)

	return &s, nil
}
