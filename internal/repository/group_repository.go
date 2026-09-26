package repository

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"time"

	"github.com/google/uuid"
	"github.com/paperalt/sovereign-speech-intelligence/internal/model"
)

var (
	ErrGroupNotFound = errors.New("transcript group not found")
)

type GroupRepository interface {
	Create(ctx context.Context, group *model.TranscriptGroup) (*model.TranscriptGroup, error)
	GetByID(ctx context.Context, id, userID string) (*model.TranscriptGroup, error)
	ListByUser(ctx context.Context, userID string) ([]model.TranscriptGroup, error)
	Update(ctx context.Context, id, userID, name, description, color string) (*model.TranscriptGroup, error)
	Delete(ctx context.Context, id, userID string, deleteMeetings bool) error
}

type sqlGroupRepository struct {
	db *sql.DB
}

func NewGroupRepository(db *sql.DB) GroupRepository {
	return &sqlGroupRepository{db: db}
}

func (r *sqlGroupRepository) Create(ctx context.Context, group *model.TranscriptGroup) (*model.TranscriptGroup, error) {
	if group.ID == "" {
		group.ID = uuid.NewString()
	}
	now := time.Now().UTC()
	group.CreatedAt = now
	group.UpdatedAt = now

	if group.Color == "" {
		group.Color = "#38BDF8"
	}

	query := `INSERT INTO transcript_groups (id, user_id, name, description, color, created_at, updated_at)
	          VALUES ($1, $2, $3, $4, $5, $6, $7)`

	_, err := r.db.ExecContext(ctx, query, group.ID, group.UserID, group.Name, group.Description, group.Color, now, now)
	if err != nil {
		return nil, fmt.Errorf("failed to create transcript group: %w", err)
	}

	return group, nil
}

func (r *sqlGroupRepository) GetByID(ctx context.Context, id, userID string) (*model.TranscriptGroup, error) {
	query := `SELECT g.id, g.user_id, g.name, g.description, g.color, g.created_at, g.updated_at,
	                 COUNT(m.id) as meeting_count,
	                 COALESCE(SUM(m.duration_sec), 0.0) as total_duration_sec
	          FROM transcript_groups g
	          LEFT JOIN meetings m ON m.group_id = g.id
	          WHERE g.id = $1 AND g.user_id = $2
	          GROUP BY g.id, g.user_id, g.name, g.description, g.color, g.created_at, g.updated_at`

	var g model.TranscriptGroup
	err := r.db.QueryRowContext(ctx, query, id, userID).Scan(
		&g.ID, &g.UserID, &g.Name, &g.Description, &g.Color, &g.CreatedAt, &g.UpdatedAt,
		&g.MeetingCount, &g.TotalDurationSec,
	)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, ErrGroupNotFound
		}
		return nil, err
	}

	return &g, nil
}

func (r *sqlGroupRepository) ListByUser(ctx context.Context, userID string) ([]model.TranscriptGroup, error) {
	query := `SELECT g.id, g.user_id, g.name, g.description, g.color, g.created_at, g.updated_at,
	                 COUNT(m.id) as meeting_count,
	                 COALESCE(SUM(m.duration_sec), 0.0) as total_duration_sec
	          FROM transcript_groups g
	          LEFT JOIN meetings m ON m.group_id = g.id
	          WHERE g.user_id = $1
	          GROUP BY g.id, g.user_id, g.name, g.description, g.color, g.created_at, g.updated_at
	          ORDER BY g.created_at DESC`

	rows, err := r.db.QueryContext(ctx, query, userID)
	if err != nil {
		return nil, fmt.Errorf("failed to list transcript groups: %w", err)
	}
	defer rows.Close()

	var groups []model.TranscriptGroup
	for rows.Next() {
		var g model.TranscriptGroup
		if err := rows.Scan(
			&g.ID, &g.UserID, &g.Name, &g.Description, &g.Color, &g.CreatedAt, &g.UpdatedAt,
			&g.MeetingCount, &g.TotalDurationSec,
		); err != nil {
			return nil, err
		}
		groups = append(groups, g)
	}

	return groups, rows.Err()
}

func (r *sqlGroupRepository) Update(ctx context.Context, id, userID, name, description, color string) (*model.TranscriptGroup, error) {
	now := time.Now().UTC()
	query := `UPDATE transcript_groups 
	          SET name = $1, description = $2, color = $3, updated_at = $4
	          WHERE id = $5 AND user_id = $6`

	res, err := r.db.ExecContext(ctx, query, name, description, color, now, id, userID)
	if err != nil {
		return nil, err
	}

	affected, err := res.RowsAffected()
	if err != nil {
		return nil, err
	}
	if affected == 0 {
		return nil, ErrGroupNotFound
	}

	return r.GetByID(ctx, id, userID)
}

func (r *sqlGroupRepository) Delete(ctx context.Context, id, userID string, deleteMeetings bool) error {
	tx, err := r.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()

	if deleteMeetings {
		// Delete meetings in group
		_, err := tx.ExecContext(ctx, `DELETE FROM meetings WHERE group_id = $1 AND user_id = $2`, id, userID)
		if err != nil {
			return fmt.Errorf("failed to delete meetings in group: %w", err)
		}
	} else {
		// Unset group_id in meetings
		_, err := tx.ExecContext(ctx, `UPDATE meetings SET group_id = NULL WHERE group_id = $1 AND user_id = $2`, id, userID)
		if err != nil {
			return fmt.Errorf("failed to unassign meetings from group: %w", err)
		}
	}

	res, err := tx.ExecContext(ctx, `DELETE FROM transcript_groups WHERE id = $1 AND user_id = $2`, id, userID)
	if err != nil {
		return fmt.Errorf("failed to delete group: %w", err)
	}

	affected, err := res.RowsAffected()
	if err != nil {
		return err
	}
	if affected == 0 {
		return ErrGroupNotFound
	}

	return tx.Commit()
}
