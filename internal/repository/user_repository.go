package repository

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"strings"
	"time"

	"github.com/google/uuid"
	"github.com/paperalt/sovereign/internal/model"
)

var (
	ErrUserNotFound      = errors.New("user not found")
	ErrUserAlreadyExists = errors.New("user with this email already exists")
	ErrQuotaExceeded     = errors.New("transcription quota exceeded")
)

type UserRepository interface {
	Create(ctx context.Context, email, passwordHash, fullName string) (*model.User, error)
	CreateOAuthUser(ctx context.Context, email, fullName, googleID string) (*model.User, error)
	GetByEmail(ctx context.Context, email string) (*model.User, error)
	GetByID(ctx context.Context, id string) (*model.User, error)
	GetByGoogleID(ctx context.Context, googleID string) (*model.User, error)
	LinkGoogleAccount(ctx context.Context, userID, googleID string) error
	DeductQuota(ctx context.Context, userID string, durationSeconds int) (remainingSeconds int, err error)
	AddQuota(ctx context.Context, userID string, planID, planName string, secondsAdded, amountIDR int, newTier string) error
	GetQuota(ctx context.Context, userID string) (*model.UserQuotaDTO, error)
	RedeemVoucher(ctx context.Context, userID, voucherCode string) (*model.UserQuotaDTO, string, error)
	CreateVoucher(ctx context.Context, code string, durationSeconds int, planName, tier string, maxUses int) error
	ListVouchers(ctx context.Context) ([]model.PrepaidVoucher, error)
	DeleteVoucher(ctx context.Context, code string) error
}

type sqlUserRepository struct {
	db *sql.DB
}

func NewUserRepository(db *sql.DB) UserRepository {
	return &sqlUserRepository{db: db}
}

func (r *sqlUserRepository) Create(ctx context.Context, email, passwordHash, fullName string) (*model.User, error) {
	id := uuid.NewString()
	now := time.Now().UTC()
	role := "user"
	tier := "free"
	quotaSeconds := 1800 // 30 minutes free starter quota
	usedSeconds := 0

	query := `INSERT INTO users (id, email, password_hash, full_name, role, tier, quota_seconds, used_seconds, created_at, updated_at) 
	          VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)`

	_, err := r.db.ExecContext(ctx, query, id, email, passwordHash, fullName, role, tier, quotaSeconds, usedSeconds, now, now)
	if err != nil {
		return nil, fmt.Errorf("failed to create user: %w", err)
	}

	return &model.User{
		ID:               id,
		Email:            email,
		PasswordHash:     &passwordHash,
		FullName:         fullName,
		Role:             role,
		Tier:             tier,
		QuotaSeconds:     quotaSeconds,
		UsedSeconds:      usedSeconds,
		RemainingSeconds: quotaSeconds,
		CreatedAt:        now,
		UpdatedAt:        now,
	}, nil
}

func (r *sqlUserRepository) CreateOAuthUser(ctx context.Context, email, fullName, googleID string) (*model.User, error) {
	id := uuid.NewString()
	now := time.Now().UTC()
	role := "user"
	tier := "free"
	quotaSeconds := 1800 // 30 minutes free starter quota
	usedSeconds := 0

	query := `INSERT INTO users (id, email, full_name, role, google_id, tier, quota_seconds, used_seconds, created_at, updated_at) 
	          VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)`

	_, err := r.db.ExecContext(ctx, query, id, email, fullName, role, googleID, tier, quotaSeconds, usedSeconds, now, now)
	if err != nil {
		return nil, fmt.Errorf("failed to create oauth user: %w", err)
	}

	return &model.User{
		ID:               id,
		Email:            email,
		FullName:         fullName,
		Role:             role,
		GoogleID:         &googleID,
		Tier:             tier,
		QuotaSeconds:     quotaSeconds,
		UsedSeconds:      usedSeconds,
		RemainingSeconds: quotaSeconds,
		CreatedAt:        now,
		UpdatedAt:        now,
	}, nil
}

func (r *sqlUserRepository) GetByEmail(ctx context.Context, email string) (*model.User, error) {
	query := `SELECT id, email, password_hash, full_name, role, google_id, tier, quota_seconds, used_seconds, created_at, updated_at FROM users WHERE email = $1`
	var u model.User
	var pwdHash, gID sql.NullString

	err := r.db.QueryRowContext(ctx, query, email).Scan(
		&u.ID, &u.Email, &pwdHash, &u.FullName, &u.Role, &gID, &u.Tier, &u.QuotaSeconds, &u.UsedSeconds, &u.CreatedAt, &u.UpdatedAt,
	)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, ErrUserNotFound
		}
		return nil, err
	}

	if pwdHash.Valid {
		u.PasswordHash = &pwdHash.String
	}
	if gID.Valid {
		u.GoogleID = &gID.String
	}
	rem := u.QuotaSeconds - u.UsedSeconds
	if rem < 0 {
		rem = 0
	}
	u.RemainingSeconds = rem

	return &u, nil
}

func (r *sqlUserRepository) GetByID(ctx context.Context, id string) (*model.User, error) {
	query := `SELECT id, email, password_hash, full_name, role, google_id, tier, quota_seconds, used_seconds, created_at, updated_at FROM users WHERE id = $1`
	var u model.User
	var pwdHash, gID sql.NullString

	err := r.db.QueryRowContext(ctx, query, id).Scan(
		&u.ID, &u.Email, &pwdHash, &u.FullName, &u.Role, &gID, &u.Tier, &u.QuotaSeconds, &u.UsedSeconds, &u.CreatedAt, &u.UpdatedAt,
	)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, ErrUserNotFound
		}
		return nil, err
	}

	if pwdHash.Valid {
		u.PasswordHash = &pwdHash.String
	}
	if gID.Valid {
		u.GoogleID = &gID.String
	}
	rem := u.QuotaSeconds - u.UsedSeconds
	if rem < 0 {
		rem = 0
	}
	u.RemainingSeconds = rem

	return &u, nil
}

func (r *sqlUserRepository) GetByGoogleID(ctx context.Context, googleID string) (*model.User, error) {
	query := `SELECT id, email, password_hash, full_name, role, google_id, tier, quota_seconds, used_seconds, created_at, updated_at FROM users WHERE google_id = $1`
	var u model.User
	var pwdHash, gID sql.NullString

	err := r.db.QueryRowContext(ctx, query, googleID).Scan(
		&u.ID, &u.Email, &pwdHash, &u.FullName, &u.Role, &gID, &u.Tier, &u.QuotaSeconds, &u.UsedSeconds, &u.CreatedAt, &u.UpdatedAt,
	)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, ErrUserNotFound
		}
		return nil, err
	}

	if pwdHash.Valid {
		u.PasswordHash = &pwdHash.String
	}
	if gID.Valid {
		u.GoogleID = &gID.String
	}
	rem := u.QuotaSeconds - u.UsedSeconds
	if rem < 0 {
		rem = 0
	}
	u.RemainingSeconds = rem

	return &u, nil
}

func (r *sqlUserRepository) LinkGoogleAccount(ctx context.Context, userID, googleID string) error {
	now := time.Now().UTC()
	query := `UPDATE users SET google_id = $1, updated_at = $2 WHERE id = $3 AND (google_id IS NULL OR google_id = '')`
	res, err := r.db.ExecContext(ctx, query, googleID, now, userID)
	if err != nil {
		return err
	}
	rows, _ := res.RowsAffected()
	if rows == 0 {
		return fmt.Errorf("user not found or google_id already linked")
	}
	return nil
}

func (r *sqlUserRepository) DeductQuota(ctx context.Context, userID string, durationSeconds int) (int, error) {
	if durationSeconds <= 0 {
		u, err := r.GetByID(ctx, userID)
		if err != nil {
			return 0, err
		}
		return u.RemainingSeconds, nil
	}

	query := `UPDATE users 
	          SET used_seconds = used_seconds + $1, updated_at = CURRENT_TIMESTAMP 
	          WHERE id = $2 
	          RETURNING tier, quota_seconds, used_seconds`
	var tier string
	var quotaSec, usedSec int
	err := r.db.QueryRowContext(ctx, query, durationSeconds, userID).Scan(&tier, &quotaSec, &usedSec)
	if err != nil {
		// Fallback for drivers that don't support RETURNING
		_, updateErr := r.db.ExecContext(ctx, `UPDATE users SET used_seconds = used_seconds + $1 WHERE id = $2`, durationSeconds, userID)
		if updateErr != nil {
			return 0, updateErr
		}
		u, getErr := r.GetByID(ctx, userID)
		if getErr != nil {
			return 0, getErr
		}
		return u.RemainingSeconds, nil
	}

	rem := quotaSec - usedSec
	if rem < 0 {
		rem = 0
	}
	return rem, nil
}

func (r *sqlUserRepository) AddQuota(ctx context.Context, userID string, planID, planName string, secondsAdded, amountIDR int, newTier string) error {
	tx, err := r.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()

	if newTier != "" {
		updateQuery := `UPDATE users 
		               SET quota_seconds = quota_seconds + $1, tier = $2, updated_at = CURRENT_TIMESTAMP 
		               WHERE id = $3`
		_, err = tx.ExecContext(ctx, updateQuery, secondsAdded, newTier, userID)
	} else {
		updateQuery := `UPDATE users 
		               SET quota_seconds = quota_seconds + $1, updated_at = CURRENT_TIMESTAMP 
		               WHERE id = $2`
		_, err = tx.ExecContext(ctx, updateQuery, secondsAdded, userID)
	}
	if err != nil {
		return fmt.Errorf("failed to update user quota: %w", err)
	}

	txID := uuid.NewString()
	insertTx := `INSERT INTO quota_transactions (id, user_id, plan_id, plan_name, seconds_added, amount_idr, status, created_at)
	             VALUES ($1, $2, $3, $4, $5, $6, 'COMPLETED', CURRENT_TIMESTAMP)`
	_, err = tx.ExecContext(ctx, insertTx, txID, userID, planID, planName, secondsAdded, amountIDR)
	if err != nil {
		return fmt.Errorf("failed to record quota transaction: %w", err)
	}

	return tx.Commit()
}

func (r *sqlUserRepository) GetQuota(ctx context.Context, userID string) (*model.UserQuotaDTO, error) {
	u, err := r.GetByID(ctx, userID)
	if err != nil {
		return nil, err
	}

	rem := u.QuotaSeconds - u.UsedSeconds
	if rem < 0 {
		rem = 0
	}

	return &model.UserQuotaDTO{
		Tier:             u.Tier,
		QuotaSeconds:     u.QuotaSeconds,
		UsedSeconds:      u.UsedSeconds,
		RemainingSeconds: rem,
		RemainingMinutes: rem / 60,
		IsUnlimited:      u.Tier == "enterprise",
	}, nil
}

func (r *sqlUserRepository) RedeemVoucher(ctx context.Context, userID, voucherCode string) (*model.UserQuotaDTO, string, error) {
	code := strings.ToUpper(strings.TrimSpace(voucherCode))
	if code == "" {
		return nil, "", fmt.Errorf("kode voucher tidak boleh kosong")
	}

	// 1. Fetch voucher definition
	var durSec, maxUses, usedCount int
	var planName, tier string
	var isActive bool
	query := `SELECT duration_seconds, plan_name, tier, max_uses, used_count, is_active 
	          FROM prepaid_vouchers WHERE code = $1`
	err := r.db.QueryRowContext(ctx, query, code).Scan(&durSec, &planName, &tier, &maxUses, &usedCount, &isActive)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, "", fmt.Errorf("kode voucher '%s' tidak terdaftar atau tidak valid", code)
		}
		return nil, "", err
	}

	if !isActive {
		return nil, "", fmt.Errorf("voucher '%s' sudah tidak aktif", code)
	}

	if maxUses > 0 && usedCount >= maxUses {
		return nil, "", fmt.Errorf("voucher '%s' telah mencapai batas kuota penukaran global", code)
	}

	planID := "voucher_" + code

	// 2. Check if this user already claimed this specific voucher
	var alreadyClaimed bool
	checkQuery := `SELECT EXISTS(SELECT 1 FROM quota_transactions WHERE user_id = $1 AND plan_id = $2)`
	err = r.db.QueryRowContext(ctx, checkQuery, userID, planID).Scan(&alreadyClaimed)
	if err == nil && alreadyClaimed {
		return nil, "", fmt.Errorf("Anda sudah pernah menukarkan voucher '%s'", code)
	}

	// 3. Atomically apply voucher in transaction
	tx, err := r.db.BeginTx(ctx, nil)
	if err != nil {
		return nil, "", err
	}
	defer tx.Rollback()

	// Update user quota
	updateUser := `UPDATE users 
	               SET quota_seconds = quota_seconds + $1, tier = $2, updated_at = CURRENT_TIMESTAMP 
	               WHERE id = $3`
	if _, err := tx.ExecContext(ctx, updateUser, durSec, tier, userID); err != nil {
		return nil, "", fmt.Errorf("gagal menambahkan kuota: %w", err)
	}

	// Increment voucher usage
	updateVoucher := `UPDATE prepaid_vouchers SET used_count = used_count + 1 WHERE code = $1`
	if _, err := tx.ExecContext(ctx, updateVoucher, code); err != nil {
		return nil, "", fmt.Errorf("gagal memperbarui status voucher: %w", err)
	}

	// Record transaction log
	txID := uuid.NewString()
	insertTx := `INSERT INTO quota_transactions (id, user_id, plan_id, plan_name, seconds_added, amount_idr, status, created_at)
	             VALUES ($1, $2, $3, $4, $5, 0, 'COMPLETED', CURRENT_TIMESTAMP)`
	if _, err := tx.ExecContext(ctx, insertTx, txID, userID, planID, planName, durSec); err != nil {
		return nil, "", fmt.Errorf("gagal mencatat transaksi voucher: %w", err)
	}

	if err := tx.Commit(); err != nil {
		return nil, "", err
	}

	updatedQuota, _ := r.GetQuota(ctx, userID)
	msg := fmt.Sprintf("Voucher %s berhasil diklaim! Tambahan kuota: %d menit", code, durSec/60)
	return updatedQuota, msg, nil
}

func (r *sqlUserRepository) CreateVoucher(ctx context.Context, code string, durationSeconds int, planName, tier string, maxUses int) error {
	code = strings.ToUpper(strings.TrimSpace(code))
	if code == "" {
		return fmt.Errorf("kode voucher tidak boleh kosong")
	}
	if durationSeconds <= 0 {
		return fmt.Errorf("durasi kuota harus lebih besar dari 0")
	}
	if maxUses <= 0 {
		maxUses = 1
	}
	if tier == "" {
		tier = "pro"
	}
	if planName == "" {
		planName = fmt.Sprintf("Voucher Kuota %d Menit", durationSeconds/60)
	}

	query := `INSERT INTO prepaid_vouchers (code, duration_seconds, plan_name, tier, max_uses, used_count, is_active, created_at)
	          VALUES ($1, $2, $3, $4, $5, 0, true, CURRENT_TIMESTAMP)
	          ON CONFLICT (code) DO UPDATE 
	          SET duration_seconds = EXCLUDED.duration_seconds,
	              plan_name = EXCLUDED.plan_name,
	              tier = EXCLUDED.tier,
	              max_uses = EXCLUDED.max_uses,
	              is_active = true`
	_, err := r.db.ExecContext(ctx, query, code, durationSeconds, planName, tier, maxUses)
	return err
}

func (r *sqlUserRepository) ListVouchers(ctx context.Context) ([]model.PrepaidVoucher, error) {
	query := `SELECT code, duration_seconds, plan_name, tier, max_uses, used_count, is_active, created_at 
	          FROM prepaid_vouchers ORDER BY created_at DESC`
	rows, err := r.db.QueryContext(ctx, query)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var list []model.PrepaidVoucher
	for rows.Next() {
		var v model.PrepaidVoucher
		var isActiveInt sql.NullInt64
		var isActiveBool sql.NullBool

		// Support both SQLite integer boolean and Postgres native bool
		err := rows.Scan(&v.Code, &v.DurationSeconds, &v.PlanName, &v.Tier, &v.MaxUses, &v.UsedCount, &isActiveBool, &v.CreatedAt)
		if err != nil {
			_ = rows.Scan(&v.Code, &v.DurationSeconds, &v.PlanName, &v.Tier, &v.MaxUses, &v.UsedCount, &isActiveInt, &v.CreatedAt)
			v.IsActive = isActiveInt.Int64 == 1
		} else {
			v.IsActive = isActiveBool.Bool
		}
		v.DurationMinutes = v.DurationSeconds / 60
		list = append(list, v)
	}

	if list == nil {
		list = []model.PrepaidVoucher{}
	}
	return list, nil
}

func (r *sqlUserRepository) DeleteVoucher(ctx context.Context, code string) error {
	code = strings.ToUpper(strings.TrimSpace(code))
	query := `UPDATE prepaid_vouchers SET is_active = false WHERE code = $1`
	_, err := r.db.ExecContext(ctx, query, code)
	return err
}
