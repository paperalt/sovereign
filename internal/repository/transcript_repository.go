package repository

import (
	"context"
	"database/sql"
	"fmt"
	"strings"
	"time"

	"github.com/paperalt/sovereign/internal/model"
)

type TranscriptRepository interface {
	AddChunk(ctx context.Context, meetingID string, chunkIndex int, startTimeSec, endTimeSec float64, rawText string) (*model.TranscriptChunk, error)
	GetChunksByMeeting(ctx context.Context, meetingID string) ([]model.TranscriptChunk, error)
	GetFullTranscript(ctx context.Context, meetingID string) (string, error)
	GetTranscriptWindow(ctx context.Context, meetingID string, windowMinutes int) (text string, durationSec float64, chunkCount int, err error)
	SearchChunks(ctx context.Context, userID string, meetingID string, queryText string, limit int) ([]model.SearchResultDTO, error)
	UpdateChunk(ctx context.Context, meetingID string, chunkID int64, rawText string) (*model.TranscriptChunk, error)
	ReplaceFullTranscript(ctx context.Context, meetingID string, rawText string) error
}

type sqlTranscriptRepository struct {
	db *sql.DB
}

func NewTranscriptRepository(db *sql.DB) TranscriptRepository {
	return &sqlTranscriptRepository{db: db}
}

func (r *sqlTranscriptRepository) AddChunk(
	ctx context.Context,
	meetingID string,
	chunkIndex int,
	startTimeSec, endTimeSec float64,
	rawText string,
) (*model.TranscriptChunk, error) {
	now := time.Now().UTC()

	query := `INSERT INTO transcript_chunks 
	          (meeting_id, chunk_index, start_time_sec, end_time_sec, raw_text, created_at)
	          VALUES ($1, $2, $3, $4, $5, $6)`

	res, err := r.db.ExecContext(ctx, query, meetingID, chunkIndex, startTimeSec, endTimeSec, rawText, now)
	if err != nil {
		return nil, fmt.Errorf("failed to insert transcript chunk: %w", err)
	}

	id, _ := res.LastInsertId()

	return &model.TranscriptChunk{
		ID:           id,
		MeetingID:    meetingID,
		ChunkIndex:   chunkIndex,
		StartTimeSec: startTimeSec,
		EndTimeSec:   endTimeSec,
		RawText:      rawText,
		CreatedAt:    now,
	}, nil
}

func (r *sqlTranscriptRepository) GetChunksByMeeting(ctx context.Context, meetingID string) ([]model.TranscriptChunk, error) {
	query := `SELECT id, meeting_id, chunk_index, start_time_sec, end_time_sec, raw_text, created_at
	          FROM transcript_chunks
	          WHERE meeting_id = $1
	          ORDER BY chunk_index ASC`

	rows, err := r.db.QueryContext(ctx, query, meetingID)
	if err != nil {
		return nil, fmt.Errorf("failed to get transcript chunks: %w", err)
	}
	defer rows.Close()

	var chunks []model.TranscriptChunk
	for rows.Next() {
		var c model.TranscriptChunk
		if err := rows.Scan(
			&c.ID, &c.MeetingID, &c.ChunkIndex, &c.StartTimeSec, &c.EndTimeSec, &c.RawText, &c.CreatedAt,
		); err != nil {
			return nil, err
		}
		chunks = append(chunks, c)
	}

	return chunks, rows.Err()
}

func (r *sqlTranscriptRepository) GetFullTranscript(ctx context.Context, meetingID string) (string, error) {
	chunks, err := r.GetChunksByMeeting(ctx, meetingID)
	if err != nil {
		return "", err
	}

	var sb strings.Builder
	for i, chunk := range chunks {
		if i > 0 {
			sb.WriteString(" ")
		}
		sb.WriteString(strings.TrimSpace(chunk.RawText))
	}

	return sb.String(), nil
}

func (r *sqlTranscriptRepository) GetTranscriptWindow(ctx context.Context, meetingID string, windowMinutes int) (string, float64, int, error) {
	chunks, err := r.GetChunksByMeeting(ctx, meetingID)
	if err != nil {
		return "", 0, 0, err
	}
	if len(chunks) == 0 {
		return "", 0, 0, nil
	}

	var selected []model.TranscriptChunk
	if windowMinutes <= 0 {
		selected = chunks
	} else {
		maxEnd := chunks[len(chunks)-1].EndTimeSec
		cutoff := maxEnd - float64(windowMinutes*60)
		if cutoff < 0 {
			cutoff = 0
		}
		for _, c := range chunks {
			if c.EndTimeSec >= cutoff {
				selected = append(selected, c)
			}
		}
	}

	if len(selected) == 0 {
		return "", 0, 0, nil
	}

	duration := selected[len(selected)-1].EndTimeSec - selected[0].StartTimeSec
	if duration < 0 {
		duration = 0
	}

	var sb strings.Builder
	for i, c := range selected {
		t := strings.TrimSpace(c.RawText)
		if t == "" {
			continue
		}
		if i > 0 && sb.Len() > 0 {
			sb.WriteString(" ")
		}
		sb.WriteString(t)
	}

	return sb.String(), duration, len(selected), nil
}

func (r *sqlTranscriptRepository) SearchChunks(
	ctx context.Context,
	userID string,
	meetingID string,
	queryText string,
	limit int,
) ([]model.SearchResultDTO, error) {
	if limit <= 0 {
		limit = 20
	}

	queryText = strings.TrimSpace(queryText)
	if queryText == "" {
		return []model.SearchResultDTO{}, nil
	}

	var query string
	var args []interface{}

	if meetingID != "" {
		query = `SELECT c.meeting_id, m.title, c.chunk_index, c.start_time_sec, c.end_time_sec, c.raw_text
		         FROM transcript_chunks c
		         JOIN meetings m ON m.id = c.meeting_id
		         WHERE m.user_id = $1 AND m.id = $2 AND (c.search_vector @@ plainto_tsquery('simple', $3) OR c.raw_text ILIKE '%' || $3 || '%')
		         ORDER BY c.chunk_index ASC LIMIT $4`
		args = []interface{}{userID, meetingID, queryText, limit}
	} else {
		query = `SELECT c.meeting_id, m.title, c.chunk_index, c.start_time_sec, c.end_time_sec, c.raw_text
		         FROM transcript_chunks c
		         JOIN meetings m ON m.id = c.meeting_id
		         WHERE m.user_id = $1 AND (c.search_vector @@ plainto_tsquery('simple', $2) OR c.raw_text ILIKE '%' || $2 || '%')
		         ORDER BY m.created_at DESC, c.chunk_index ASC LIMIT $3`
		args = []interface{}{userID, queryText, limit}
	}

	rows, err := r.db.QueryContext(ctx, query, args...)
	if err != nil {
		// SQLite fallback using LIKE
		if meetingID != "" {
			query = `SELECT c.meeting_id, m.title, c.chunk_index, c.start_time_sec, c.end_time_sec, c.raw_text
			         FROM transcript_chunks c
			         JOIN meetings m ON m.id = c.meeting_id
			         WHERE m.user_id = $1 AND m.id = $2 AND c.raw_text LIKE '%' || $3 || '%'
			         ORDER BY c.chunk_index ASC LIMIT $4`
			args = []interface{}{userID, meetingID, queryText, limit}
		} else {
			query = `SELECT c.meeting_id, m.title, c.chunk_index, c.start_time_sec, c.end_time_sec, c.raw_text
			         FROM transcript_chunks c
			         JOIN meetings m ON m.id = c.meeting_id
			         WHERE m.user_id = $1 AND c.raw_text LIKE '%' || $2 || '%'
			         ORDER BY m.created_at DESC, c.chunk_index ASC LIMIT $3`
			args = []interface{}{userID, queryText, limit}
		}
		var fallbackErr error
		rows, fallbackErr = r.db.QueryContext(ctx, query, args...)
		if fallbackErr != nil {
			return nil, fmt.Errorf("search failed: %w", fallbackErr)
		}
	}
	defer rows.Close()

	var results []model.SearchResultDTO
	for rows.Next() {
		var res model.SearchResultDTO
		if err := rows.Scan(
			&res.MeetingID, &res.MeetingTitle, &res.ChunkIndex,
			&res.StartTimeSec, &res.EndTimeSec, &res.RawText,
		); err != nil {
			return nil, err
		}
		results = append(results, res)
	}

	if results == nil {
		results = []model.SearchResultDTO{}
	}

	return results, rows.Err()
}

func (r *sqlTranscriptRepository) UpdateChunk(ctx context.Context, meetingID string, chunkID int64, rawText string) (*model.TranscriptChunk, error) {
	updateQuery := `UPDATE transcript_chunks SET raw_text = $1 WHERE id = $2 AND meeting_id = $3`
	res, err := r.db.ExecContext(ctx, updateQuery, rawText, chunkID, meetingID)
	if err != nil {
		return nil, fmt.Errorf("failed to update transcript chunk: %w", err)
	}
	rowsAffected, _ := res.RowsAffected()
	if rowsAffected == 0 {
		return nil, sql.ErrNoRows
	}

	now := time.Now().UTC()
	_, _ = r.db.ExecContext(ctx, `UPDATE meetings SET updated_at = $1 WHERE id = $2`, now, meetingID)

	selectQuery := `SELECT id, meeting_id, chunk_index, start_time_sec, end_time_sec, raw_text, created_at
	                FROM transcript_chunks WHERE id = $1 AND meeting_id = $2`
	var c model.TranscriptChunk
	err = r.db.QueryRowContext(ctx, selectQuery, chunkID, meetingID).Scan(
		&c.ID, &c.MeetingID, &c.ChunkIndex, &c.StartTimeSec, &c.EndTimeSec, &c.RawText, &c.CreatedAt,
	)
	if err != nil {
		return nil, fmt.Errorf("failed to load updated chunk: %w", err)
	}
	return &c, nil
}

func (r *sqlTranscriptRepository) ReplaceFullTranscript(ctx context.Context, meetingID string, rawText string) error {
	trimmed := strings.TrimSpace(rawText)
	if trimmed == "" {
		return fmt.Errorf("transcript cannot be empty")
	}

	tx, err := r.db.BeginTx(ctx, nil)
	if err != nil {
		return fmt.Errorf("failed to start transaction: %w", err)
	}
	defer tx.Rollback()

	delQuery := `DELETE FROM transcript_chunks WHERE meeting_id = $1`
	if _, err := tx.ExecContext(ctx, delQuery, meetingID); err != nil {
		return fmt.Errorf("failed to delete old chunks: %w", err)
	}

	var durationSec float64
	var startedAt, endedAt sql.NullTime
	_ = tx.QueryRowContext(ctx, `SELECT COALESCE(duration_sec, 0.0), started_at, ended_at FROM meetings WHERE id = $1`, meetingID).Scan(&durationSec, &startedAt, &endedAt)
	if durationSec <= 0 && startedAt.Valid && endedAt.Valid {
		durationSec = endedAt.Time.Sub(startedAt.Time).Seconds()
		if durationSec < 0 {
			durationSec = 0
		}
	}

	now := time.Now().UTC()
	insQuery := `INSERT INTO transcript_chunks 
	             (meeting_id, chunk_index, start_time_sec, end_time_sec, raw_text, created_at)
	             VALUES ($1, $2, $3, $4, $5, $6)`
	if _, err := tx.ExecContext(ctx, insQuery, meetingID, 0, 0.0, durationSec, trimmed, now); err != nil {
		return fmt.Errorf("failed to insert replaced chunk: %w", err)
	}

	if _, err := tx.ExecContext(ctx, `UPDATE meetings SET updated_at = $1 WHERE id = $2`, now, meetingID); err != nil {
		return fmt.Errorf("failed to update meeting updated_at: %w", err)
	}

	return tx.Commit()
}
