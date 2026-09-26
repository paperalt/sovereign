package repository

import (
	"context"
	"crypto/sha256"
	"database/sql"
	"encoding/hex"
	"errors"
	"fmt"
	"time"

	"github.com/google/uuid"
	"github.com/paperalt/sovereign-speech-intelligence/internal/model"
)

var (
	ErrTokenNotFound = errors.New("refresh token not found or revoked")
)

type TokenRepository interface {
	CreateRefreshToken(ctx context.Context, userID, rawToken, deviceInfo, ipAddress string, expiresAt time.Time) (*model.RefreshToken, error)
	GetActiveRefreshToken(ctx context.Context, rawToken string) (*model.RefreshToken, error)
	RevokeRefreshToken(ctx context.Context, rawToken string) error
	RevokeAllUserTokens(ctx context.Context, userID string) error
}

type sqlTokenRepository struct {
	db *sql.DB
}

func NewTokenRepository(db *sql.DB) TokenRepository {
	return &sqlTokenRepository{db: db}
}

// HashToken produces a SHA-256 hex string from raw token for safe storage.
func HashToken(rawToken string) string {
	hash := sha256.Sum256([]byte(rawToken))
	return hex.EncodeToString(hash[:])
}

func (r *sqlTokenRepository) CreateRefreshToken(
	ctx context.Context,
	userID, rawToken, deviceInfo, ipAddress string,
	expiresAt time.Time,
) (*model.RefreshToken, error) {
	id := uuid.NewString()
	tokenHash := HashToken(rawToken)
	now := time.Now().UTC()

	query := `INSERT INTO refresh_tokens (id, user_id, token_hash, device_info, ip_address, expires_at, created_at)
	          VALUES ($1, $2, $3, $4, $5, $6, $7)`

	_, err := r.db.ExecContext(ctx, query, id, userID, tokenHash, deviceInfo, ipAddress, expiresAt, now)
	if err != nil {
		return nil, fmt.Errorf("failed to persist refresh token: %w", err)
	}

	return &model.RefreshToken{
		ID:         id,
		UserID:     userID,
		TokenHash:  tokenHash,
		DeviceInfo: deviceInfo,
		IPAddress:  ipAddress,
		ExpiresAt:  expiresAt,
		CreatedAt:  now,
	}, nil
}

func (r *sqlTokenRepository) GetActiveRefreshToken(ctx context.Context, rawToken string) (*model.RefreshToken, error) {
	tokenHash := HashToken(rawToken)

	query := `SELECT id, user_id, token_hash, device_info, ip_address, expires_at, revoked_at, created_at
	          FROM refresh_tokens
	          WHERE token_hash = $1 AND revoked_at IS NULL AND expires_at > CURRENT_TIMESTAMP`

	var t model.RefreshToken
	var ip sql.NullString
	var revokedAt sql.NullTime

	err := r.db.QueryRowContext(ctx, query, tokenHash).Scan(
		&t.ID, &t.UserID, &t.TokenHash, &t.DeviceInfo, &ip, &t.ExpiresAt, &revokedAt, &t.CreatedAt,
	)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, ErrTokenNotFound
		}
		return nil, err
	}

	if ip.Valid {
		t.IPAddress = ip.String
	}
	if revokedAt.Valid {
		t.RevokedAt = &revokedAt.Time
	}

	return &t, nil
}

func (r *sqlTokenRepository) RevokeRefreshToken(ctx context.Context, rawToken string) error {
	tokenHash := HashToken(rawToken)
	now := time.Now().UTC()

	query := `UPDATE refresh_tokens SET revoked_at = $1 WHERE token_hash = $2 AND revoked_at IS NULL`
	res, err := r.db.ExecContext(ctx, query, now, tokenHash)
	if err != nil {
		return fmt.Errorf("failed to revoke token: %w", err)
	}

	rows, _ := res.RowsAffected()
	if rows == 0 {
		return ErrTokenNotFound
	}
	return nil
}

func (r *sqlTokenRepository) RevokeAllUserTokens(ctx context.Context, userID string) error {
	now := time.Now().UTC()
	query := `UPDATE refresh_tokens SET revoked_at = $1 WHERE user_id = $2 AND revoked_at IS NULL`
	_, err := r.db.ExecContext(ctx, query, now, userID)
	return err
}
