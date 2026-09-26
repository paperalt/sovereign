package repository

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"strings"
	"time"

	"github.com/google/uuid"
	"github.com/paperalt/sovereign-speech-intelligence/internal/model"
)

var (
	ErrMeetingNotFound = errors.New("meeting not found")
)

type MeetingRepository interface {
	Create(ctx context.Context, userID, title, language, targetLanguage string, groupID ...*string) (*model.Meeting, error)
	GetByID(ctx context.Context, id, userID string) (*model.Meeting, error)
	GetActiveMeeting(ctx context.Context, userID string) (*model.Meeting, error)
	ListByUser(ctx context.Context, userID string, limit, offset int) ([]model.Meeting, error)
	ListByGroup(ctx context.Context, userID, groupID string) ([]model.Meeting, error)
	SearchMeetings(ctx context.Context, userID, queryText string, limit int) ([]model.Meeting, error)
	AssignGroup(ctx context.Context, userID, meetingID string, groupID *string) error
	UpdateTitle(ctx context.Context, id, userID, title string) error
	UpdateStatus(ctx context.Context, id, userID, status string) error
	UpdateSummary(ctx context.Context, id, userID, summary string) error
	Delete(ctx context.Context, id, userID string) error
}

type sqlMeetingRepository struct {
	db *sql.DB
}

func NewMeetingRepository(db *sql.DB) MeetingRepository {
	return &sqlMeetingRepository{db: db}
}

func (r *sqlMeetingRepository) Create(ctx context.Context, userID, title, language, targetLanguage string, groupID ...*string) (*model.Meeting, error) {
	id := uuid.NewString()
	now := time.Now().UTC()

	if language == "" {
		language = "id"
	}

	var assignedGroup *string
	if len(groupID) > 0 && groupID[0] != nil && *groupID[0] != "" {
		assignedGroup = groupID[0]
	}

	var err error
	if assignedGroup != nil {
		query := `INSERT INTO meetings (id, user_id, title, language, target_language, status, started_at, created_at, group_id)
		          VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)`
		_, err = r.db.ExecContext(ctx, query, id, userID, title, language, targetLanguage, "IN_PROGRESS", now, now, *assignedGroup)
	} else {
		query := `INSERT INTO meetings (id, user_id, title, language, target_language, status, started_at, created_at)
		          VALUES ($1, $2, $3, $4, $5, $6, $7, $8)`
		_, err = r.db.ExecContext(ctx, query, id, userID, title, language, targetLanguage, "IN_PROGRESS", now, now)
	}

	if err != nil {
		return nil, fmt.Errorf("failed to create meeting: %w", err)
	}

	return &model.Meeting{
		ID:             id,
		UserID:         userID,
		GroupID:        assignedGroup,
		Title:          title,
		Language:       language,
		TargetLanguage: targetLanguage,
		Status:         "IN_PROGRESS",
		StartedAt:      now,
		CreatedAt:      now,
	}, nil
}

func (r *sqlMeetingRepository) GetByID(ctx context.Context, id, userID string) (*model.Meeting, error) {
	query := `SELECT m.id, m.user_id, m.title, m.language, m.target_language, m.status, m.started_at, m.ended_at, m.summary, m.created_at,
	                 m.group_id, COALESCE(g.name, '') as group_name
	          FROM meetings m
	          LEFT JOIN transcript_groups g ON m.group_id = g.id
	          WHERE m.id = $1 AND m.user_id = $2`

	var m model.Meeting
	var endedAt sql.NullTime
	var summary sql.NullString
	var grpID sql.NullString
	var grpName sql.NullString

	err := r.db.QueryRowContext(ctx, query, id, userID).Scan(
		&m.ID, &m.UserID, &m.Title, &m.Language, &m.TargetLanguage, &m.Status, &m.StartedAt, &endedAt, &summary, &m.CreatedAt,
		&grpID, &grpName,
	)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, ErrMeetingNotFound
		}
		return nil, err
	}

	if endedAt.Valid {
		m.EndedAt = &endedAt.Time
	}
	if summary.Valid {
		m.Summary = summary.String
	}
	if grpID.Valid && grpID.String != "" {
		m.GroupID = &grpID.String
	}
	if grpName.Valid {
		m.GroupName = grpName.String
	}

	return &m, nil
}

func (r *sqlMeetingRepository) GetActiveMeeting(ctx context.Context, userID string) (*model.Meeting, error) {
	query := `SELECT m.id, m.user_id, m.title, m.language, m.target_language, m.status, m.started_at, m.ended_at, m.summary, m.created_at,
	                 m.group_id, COALESCE(g.name, '') as group_name
	          FROM meetings m
	          LEFT JOIN transcript_groups g ON m.group_id = g.id
	          WHERE m.user_id = $1 AND m.status = 'IN_PROGRESS' 
	          ORDER BY m.created_at DESC LIMIT 1`

	var m model.Meeting
	var endedAt sql.NullTime
	var summary sql.NullString
	var grpID sql.NullString
	var grpName sql.NullString

	err := r.db.QueryRowContext(ctx, query, userID).Scan(
		&m.ID, &m.UserID, &m.Title, &m.Language, &m.TargetLanguage, &m.Status, &m.StartedAt, &endedAt, &summary, &m.CreatedAt,
		&grpID, &grpName,
	)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, ErrMeetingNotFound
		}
		return nil, err
	}

	if endedAt.Valid {
		m.EndedAt = &endedAt.Time
	}
	if summary.Valid {
		m.Summary = summary.String
	}
	if grpID.Valid && grpID.String != "" {
		m.GroupID = &grpID.String
	}
	if grpName.Valid {
		m.GroupName = grpName.String
	}

	return &m, nil
}

func (r *sqlMeetingRepository) ListByUser(ctx context.Context, userID string, limit, offset int) ([]model.Meeting, error) {
	if limit <= 0 {
		limit = 50
	}
	if offset < 0 {
		offset = 0
	}

	query := `SELECT m.id, m.user_id, m.title, m.language, m.target_language, m.status, m.started_at, m.ended_at, m.summary, m.created_at,
	                 m.group_id, COALESCE(g.name, '') as group_name
	          FROM meetings m
	          LEFT JOIN transcript_groups g ON m.group_id = g.id
	          WHERE m.user_id = $1 
	          ORDER BY m.created_at DESC 
	          LIMIT $2 OFFSET $3`

	rows, err := r.db.QueryContext(ctx, query, userID, limit, offset)
	if err != nil {
		return nil, fmt.Errorf("failed to list meetings: %w", err)
	}
	defer rows.Close()

	var meetings []model.Meeting
	for rows.Next() {
		var m model.Meeting
		var endedAt sql.NullTime
		var summary sql.NullString
		var grpID sql.NullString
		var grpName sql.NullString

		if err := rows.Scan(
			&m.ID, &m.UserID, &m.Title, &m.Language, &m.TargetLanguage, &m.Status, &m.StartedAt, &endedAt, &summary, &m.CreatedAt,
			&grpID, &grpName,
		); err != nil {
			return nil, err
		}

		if endedAt.Valid {
			m.EndedAt = &endedAt.Time
		}
		if summary.Valid {
			m.Summary = summary.String
		}
		if grpID.Valid && grpID.String != "" {
			m.GroupID = &grpID.String
		}
		if grpName.Valid {
			m.GroupName = grpName.String
		}

		meetings = append(meetings, m)
	}

	return meetings, rows.Err()
}

func (r *sqlMeetingRepository) ListByGroup(ctx context.Context, userID, groupID string) ([]model.Meeting, error) {
	query := `SELECT m.id, m.user_id, m.title, m.language, m.target_language, m.status, m.started_at, m.ended_at, m.summary, m.created_at,
	                 m.group_id, COALESCE(g.name, '') as group_name
	          FROM meetings m
	          LEFT JOIN transcript_groups g ON m.group_id = g.id
	          WHERE m.user_id = $1 AND m.group_id = $2
	          ORDER BY m.created_at DESC`

	rows, err := r.db.QueryContext(ctx, query, userID, groupID)
	if err != nil {
		return nil, fmt.Errorf("failed to list meetings by group: %w", err)
	}
	defer rows.Close()

	var meetings []model.Meeting
	for rows.Next() {
		var m model.Meeting
		var endedAt sql.NullTime
		var summary sql.NullString
		var grpID sql.NullString
		var grpName sql.NullString

		if err := rows.Scan(
			&m.ID, &m.UserID, &m.Title, &m.Language, &m.TargetLanguage, &m.Status, &m.StartedAt, &endedAt, &summary, &m.CreatedAt,
			&grpID, &grpName,
		); err != nil {
			return nil, err
		}

		if endedAt.Valid {
			m.EndedAt = &endedAt.Time
		}
		if summary.Valid {
			m.Summary = summary.String
		}
		if grpID.Valid && grpID.String != "" {
			m.GroupID = &grpID.String
		}
		if grpName.Valid {
			m.GroupName = grpName.String
		}

		meetings = append(meetings, m)
	}

	return meetings, rows.Err()
}

func (r *sqlMeetingRepository) SearchMeetings(ctx context.Context, userID, queryText string, limit int) ([]model.Meeting, error) {
	if limit <= 0 {
		limit = 50
	}
	queryText = strings.TrimSpace(queryText)
	if queryText == "" {
		return []model.Meeting{}, nil
	}

	query := `SELECT m.id, m.user_id, m.title, m.language, m.target_language, m.status, m.started_at, m.ended_at, m.summary, m.created_at,
	                 m.group_id, COALESCE(g.name, '') as group_name
	          FROM meetings m
	          LEFT JOIN transcript_groups g ON m.group_id = g.id
	          WHERE m.user_id = $1 AND (
	              m.title ILIKE '%' || $2 || '%'
	              OR m.summary ILIKE '%' || $2 || '%'
	              OR g.name ILIKE '%' || $2 || '%'
	              OR EXISTS (
	                  SELECT 1 FROM transcript_chunks c
	                  WHERE c.meeting_id = m.id AND (
	                      c.raw_text ILIKE '%' || $2 || '%'
	                      OR c.search_vector @@ plainto_tsquery('simple', $2)
	                  )
	              )
	              OR EXISTS (
	                  SELECT 1 FROM meeting_summaries s
	                  WHERE s.meeting_id = m.id AND s.executive_summary ILIKE '%' || $2 || '%'
	              )
	          )
	          ORDER BY m.created_at DESC LIMIT $3`

	rows, err := r.db.QueryContext(ctx, query, userID, queryText, limit)
	if err != nil {
		// SQLite fallback using LIKE
		query = `SELECT m.id, m.user_id, m.title, m.language, m.target_language, m.status, m.started_at, m.ended_at, m.summary, m.created_at,
		                 m.group_id, COALESCE(g.name, '') as group_name
		          FROM meetings m
		          LEFT JOIN transcript_groups g ON m.group_id = g.id
		          WHERE m.user_id = $1 AND (
		              m.title LIKE '%' || $2 || '%'
		              OR m.summary LIKE '%' || $2 || '%'
		              OR g.name LIKE '%' || $2 || '%'
		              OR EXISTS (
		                  SELECT 1 FROM transcript_chunks c
		                  WHERE c.meeting_id = m.id AND c.raw_text LIKE '%' || $2 || '%'
		              )
		              OR EXISTS (
		                  SELECT 1 FROM meeting_summaries s
		                  WHERE s.meeting_id = m.id AND s.executive_summary LIKE '%' || $2 || '%'
		              )
		          )
		          ORDER BY m.created_at DESC LIMIT $3`
		var fallbackErr error
		rows, fallbackErr = r.db.QueryContext(ctx, query, userID, queryText, limit)
		if fallbackErr != nil {
			return nil, fmt.Errorf("search meetings failed: %w", fallbackErr)
		}
	}
	defer rows.Close()

	var meetings []model.Meeting
	for rows.Next() {
		var m model.Meeting
		var endedAt sql.NullTime
		var summary sql.NullString
		var grpID sql.NullString
		var grpName sql.NullString

		if err := rows.Scan(
			&m.ID, &m.UserID, &m.Title, &m.Language, &m.TargetLanguage, &m.Status, &m.StartedAt, &endedAt, &summary, &m.CreatedAt,
			&grpID, &grpName,
		); err != nil {
			return nil, err
		}

		if endedAt.Valid {
			m.EndedAt = &endedAt.Time
		}
		if summary.Valid {
			m.Summary = summary.String
		}
		if grpID.Valid && grpID.String != "" {
			m.GroupID = &grpID.String
		}
		if grpName.Valid {
			m.GroupName = grpName.String
		}

		meetings = append(meetings, m)
	}

	if meetings == nil {
		meetings = []model.Meeting{}
	}

	return meetings, rows.Err()
}

func (r *sqlMeetingRepository) AssignGroup(ctx context.Context, userID, meetingID string, groupID *string) error {
	var query string
	var err error

	if groupID != nil && *groupID != "" {
		query = `UPDATE meetings SET group_id = $1 WHERE id = $2 AND user_id = $3`
		res, execErr := r.db.ExecContext(ctx, query, *groupID, meetingID, userID)
		err = execErr
		if err == nil {
			rows, _ := res.RowsAffected()
			if rows == 0 {
				return ErrMeetingNotFound
			}
		}
	} else {
		query = `UPDATE meetings SET group_id = NULL WHERE id = $1 AND user_id = $2`
		res, execErr := r.db.ExecContext(ctx, query, meetingID, userID)
		err = execErr
		if err == nil {
			rows, _ := res.RowsAffected()
			if rows == 0 {
				return ErrMeetingNotFound
			}
		}
	}

	return err
}

func (r *sqlMeetingRepository) UpdateTitle(ctx context.Context, id, userID, title string) error {
	now := time.Now().UTC()
	query := `UPDATE meetings SET title = $1, updated_at = $2 WHERE id = $3 AND user_id = $4`
	res, err := r.db.ExecContext(ctx, query, title, now, id, userID)
	if err != nil {
		return err
	}
	rows, _ := res.RowsAffected()
	if rows == 0 {
		return ErrMeetingNotFound
	}
	return nil
}

func (r *sqlMeetingRepository) UpdateStatus(ctx context.Context, id, userID, status string) error {
	now := time.Now().UTC()
	var query string
	var err error

	if status == "COMPLETED" || status == "FAILED" {
		query = `UPDATE meetings SET status = $1, ended_at = $2 WHERE id = $3 AND user_id = $4`
		res, execErr := r.db.ExecContext(ctx, query, status, now, id, userID)
		err = execErr
		if err == nil {
			rows, _ := res.RowsAffected()
			if rows == 0 {
				return ErrMeetingNotFound
			}
		}
	} else {
		query = `UPDATE meetings SET status = $1 WHERE id = $2 AND user_id = $3`
		res, execErr := r.db.ExecContext(ctx, query, status, id, userID)
		err = execErr
		if err == nil {
			rows, _ := res.RowsAffected()
			if rows == 0 {
				return ErrMeetingNotFound
			}
		}
	}

	return err
}

func (r *sqlMeetingRepository) UpdateSummary(ctx context.Context, id, userID, summary string) error {
	query := `UPDATE meetings SET summary = $1 WHERE id = $2 AND user_id = $3`
	res, err := r.db.ExecContext(ctx, query, summary, id, userID)
	if err != nil {
		return err
	}
	rows, _ := res.RowsAffected()
	if rows == 0 {
		return ErrMeetingNotFound
	}
	return nil
}

func (r *sqlMeetingRepository) Delete(ctx context.Context, id, userID string) error {
	query := `DELETE FROM meetings WHERE id = $1 AND user_id = $2`
	res, err := r.db.ExecContext(ctx, query, id, userID)
	if err != nil {
		return err
	}
	rows, _ := res.RowsAffected()
	if rows == 0 {
		return ErrMeetingNotFound
	}
	return nil
}
