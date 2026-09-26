package handler

import (
	"encoding/json"
	"errors"
	"net/http"
	"strings"
	"time"

	"github.com/paperalt/sovereign-speech-intelligence/internal/repository"
	"github.com/paperalt/sovereign-speech-intelligence/internal/service"
	"github.com/paperalt/sovereign-speech-intelligence/pkg/token"
)

type AuthHandler struct {
	userRepo          repository.UserRepository
	tokenRepo         repository.TokenRepository
	googleValidator   service.GoogleTokenValidator
	jwtSecret         string
	accessTTL         time.Duration
	refreshTTL        time.Duration
	allowPasswordAuth bool
}

func NewAuthHandler(
	userRepo repository.UserRepository,
	tokenRepo repository.TokenRepository,
	googleValidator service.GoogleTokenValidator,
	jwtSecret string,
	accessTTL, refreshTTL time.Duration,
) *AuthHandler {
	if accessTTL <= 0 {
		accessTTL = 15 * time.Minute
	}
	if refreshTTL <= 0 {
		refreshTTL = 7 * 24 * time.Hour
	}
	return &AuthHandler{
		userRepo:          userRepo,
		tokenRepo:         tokenRepo,
		googleValidator:   googleValidator,
		jwtSecret:         jwtSecret,
		accessTTL:         accessTTL,
		refreshTTL:        refreshTTL,
		allowPasswordAuth: false,
	}
}

func (h *AuthHandler) SetAllowPasswordAuth(allow bool) {
	h.allowPasswordAuth = allow
}

type registerRequest struct {
	Email    string `json:"email"`
	Password string `json:"password"`
	FullName string `json:"full_name"`
}

type loginRequest struct {
	Email    string `json:"email"`
	Password string `json:"password"`
}

type tokenRequest struct {
	RefreshToken string `json:"refresh_token"`
}

type googleLoginRequest struct {
	IDToken string `json:"id_token"`
}

func (h *AuthHandler) Register(w http.ResponseWriter, r *http.Request) {
	if !h.allowPasswordAuth {
		respondError(w, http.StatusForbidden, "registrasi email/password dinonaktifkan; silakan masuk menggunakan akun Google")
		return
	}

	var req registerRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		respondError(w, http.StatusBadRequest, "invalid request body")
		return
	}

	req.Email = strings.TrimSpace(strings.ToLower(req.Email))
	req.FullName = strings.TrimSpace(req.FullName)

	if req.Email == "" || req.Password == "" || req.FullName == "" {
		respondError(w, http.StatusBadRequest, "email, password, and full_name are required")
		return
	}

	if len(req.Password) < 8 {
		respondError(w, http.StatusBadRequest, "password must be at least 8 characters")
		return
	}

	pwdHash, err := token.HashPassword(req.Password, nil)
	if err != nil {
		respondError(w, http.StatusInternalServerError, "failed to process credentials")
		return
	}

	user, err := h.userRepo.Create(r.Context(), req.Email, pwdHash, req.FullName)
	if err != nil {
		if errors.Is(err, repository.ErrUserAlreadyExists) || strings.Contains(err.Error(), "UNIQUE") || strings.Contains(err.Error(), "duplicate") {
			respondError(w, http.StatusConflict, "user with this email already exists")
			return
		}
		respondError(w, http.StatusInternalServerError, "failed to register user")
		return
	}

	accessToken, refreshToken, err := token.GenerateTokenPair(user.ID, user.Email, h.jwtSecret, h.accessTTL, h.refreshTTL)
	if err != nil {
		respondError(w, http.StatusInternalServerError, "failed to issue authentication token")
		return
	}

	// Persist refresh token session in database
	deviceInfo := r.UserAgent()
	if deviceInfo == "" {
		deviceInfo = "Mobile Client"
	}
	expiresAt := time.Now().Add(h.refreshTTL)
	if _, err := h.tokenRepo.CreateRefreshToken(r.Context(), user.ID, refreshToken, deviceInfo, r.RemoteAddr, expiresAt); err != nil {
		respondError(w, http.StatusInternalServerError, "failed to register session")
		return
	}

	respondJSON(w, http.StatusCreated, map[string]interface{}{
		"token":         accessToken,
		"refresh_token": refreshToken,
		"user":          user,
	})
}

func (h *AuthHandler) Login(w http.ResponseWriter, r *http.Request) {
	if !h.allowPasswordAuth {
		respondError(w, http.StatusForbidden, "login email/password dinonaktifkan; silakan masuk menggunakan akun Google")
		return
	}

	var req loginRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		respondError(w, http.StatusBadRequest, "invalid request body")
		return
	}

	req.Email = strings.TrimSpace(strings.ToLower(req.Email))
	if req.Email == "" || req.Password == "" {
		respondError(w, http.StatusBadRequest, "email and password are required")
		return
	}

	user, err := h.userRepo.GetByEmail(r.Context(), req.Email)
	if err != nil {
		respondError(w, http.StatusUnauthorized, "invalid email or password")
		return
	}

	if user.PasswordHash == nil {
		respondError(w, http.StatusBadRequest, "this account uses Google login; please sign in with Google")
		return
	}

	match, err := token.VerifyPassword(req.Password, *user.PasswordHash)
	if err != nil || !match {
		respondError(w, http.StatusUnauthorized, "invalid email or password")
		return
	}

	accessToken, refreshToken, err := token.GenerateTokenPair(user.ID, user.Email, h.jwtSecret, h.accessTTL, h.refreshTTL)
	if err != nil {
		respondError(w, http.StatusInternalServerError, "failed to issue authentication token")
		return
	}

	// Persist refresh token session in database
	deviceInfo := r.UserAgent()
	if deviceInfo == "" {
		deviceInfo = "Mobile Client"
	}
	expiresAt := time.Now().Add(h.refreshTTL)
	if _, err := h.tokenRepo.CreateRefreshToken(r.Context(), user.ID, refreshToken, deviceInfo, r.RemoteAddr, expiresAt); err != nil {
		respondError(w, http.StatusInternalServerError, "failed to register session")
		return
	}

	respondJSON(w, http.StatusOK, map[string]interface{}{
		"token":         accessToken,
		"refresh_token": refreshToken,
		"user":          user,
	})
}

func (h *AuthHandler) GoogleLogin(w http.ResponseWriter, r *http.Request) {
	var req googleLoginRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		respondError(w, http.StatusBadRequest, "invalid request body")
		return
	}

	req.IDToken = strings.TrimSpace(req.IDToken)
	if req.IDToken == "" {
		respondError(w, http.StatusBadRequest, "id_token is required")
		return
	}

	if h.googleValidator == nil {
		respondError(w, http.StatusServiceUnavailable, "google oauth is not configured on this server")
		return
	}

	claims, err := h.googleValidator.ValidateToken(r.Context(), req.IDToken)
	if err != nil {
		respondError(w, http.StatusUnauthorized, "invalid google id_token: "+err.Error())
		return
	}

	if !claims.EmailVerified {
		respondError(w, http.StatusUnauthorized, "google email is not verified")
		return
	}

	// 1. Check if user exists by GoogleID
	user, err := h.userRepo.GetByGoogleID(r.Context(), claims.GoogleID)
	if err != nil {
		// 2. Check if user exists by Email (Account linking)
		user, err = h.userRepo.GetByEmail(r.Context(), claims.Email)
		if err == nil {
			// Link Google ID to existing user account
			_ = h.userRepo.LinkGoogleAccount(r.Context(), user.ID, claims.GoogleID)
			user.GoogleID = &claims.GoogleID
		} else {
			// 3. Create new OAuth User
			name := claims.FullName
			if name == "" {
				name = strings.Split(claims.Email, "@")[0]
			}
			user, err = h.userRepo.CreateOAuthUser(r.Context(), claims.Email, name, claims.GoogleID)
			if err != nil {
				respondError(w, http.StatusInternalServerError, "failed to register oauth user: "+err.Error())
				return
			}
		}
	}

	accessToken, refreshToken, err := token.GenerateTokenPair(user.ID, user.Email, h.jwtSecret, h.accessTTL, h.refreshTTL)
	if err != nil {
		respondError(w, http.StatusInternalServerError, "failed to issue authentication token")
		return
	}

	deviceInfo := r.UserAgent()
	if deviceInfo == "" {
		deviceInfo = "Google OAuth Client"
	}
	expiresAt := time.Now().Add(h.refreshTTL)
	if _, err := h.tokenRepo.CreateRefreshToken(r.Context(), user.ID, refreshToken, deviceInfo, r.RemoteAddr, expiresAt); err != nil {
		respondError(w, http.StatusInternalServerError, "failed to persist oauth session")
		return
	}

	respondJSON(w, http.StatusOK, map[string]interface{}{
		"token":         accessToken,
		"refresh_token": refreshToken,
		"user":          user,
	})
}

func (h *AuthHandler) Refresh(w http.ResponseWriter, r *http.Request) {
	var req tokenRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		respondError(w, http.StatusBadRequest, "invalid request body")
		return
	}

	req.RefreshToken = strings.TrimSpace(req.RefreshToken)
	if req.RefreshToken == "" {
		respondError(w, http.StatusBadRequest, "refresh_token is required")
		return
	}

	// 1. Verify cryptographic validity
	claims, err := token.ValidateRefreshToken(req.RefreshToken, h.jwtSecret)
	if err != nil {
		respondError(w, http.StatusUnauthorized, "invalid or expired refresh token")
		return
	}

	// 2. Verify state in database (checks active & not revoked)
	dbToken, err := h.tokenRepo.GetActiveRefreshToken(r.Context(), req.RefreshToken)
	if err != nil {
		respondError(w, http.StatusUnauthorized, "refresh token revoked or expired in database")
		return
	}

	user, err := h.userRepo.GetByID(r.Context(), claims.UserID)
	if err != nil {
		respondError(w, http.StatusUnauthorized, "user account not found")
		return
	}

	// 3. Token Rotation: Revoke existing token and issue new token pair
	if err := h.tokenRepo.RevokeRefreshToken(r.Context(), req.RefreshToken); err != nil {
		respondError(w, http.StatusInternalServerError, "failed to rotate session")
		return
	}

	accessToken, newRefreshToken, err := token.GenerateTokenPair(user.ID, user.Email, h.jwtSecret, h.accessTTL, h.refreshTTL)
	if err != nil {
		respondError(w, http.StatusInternalServerError, "failed to rotate token pair")
		return
	}

	expiresAt := time.Now().Add(h.refreshTTL)
	if _, err := h.tokenRepo.CreateRefreshToken(r.Context(), user.ID, newRefreshToken, dbToken.DeviceInfo, r.RemoteAddr, expiresAt); err != nil {
		respondError(w, http.StatusInternalServerError, "failed to persist new session: "+err.Error())
		return
	}

	respondJSON(w, http.StatusOK, map[string]interface{}{
		"token":         accessToken,
		"refresh_token": newRefreshToken,
	})
}

func (h *AuthHandler) Logout(w http.ResponseWriter, r *http.Request) {
	var req tokenRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		respondError(w, http.StatusBadRequest, "invalid request body")
		return
	}

	req.RefreshToken = strings.TrimSpace(req.RefreshToken)
	if req.RefreshToken != "" {
		_ = h.tokenRepo.RevokeRefreshToken(r.Context(), req.RefreshToken)
	}

	respondJSON(w, http.StatusOK, map[string]string{"message": "successfully logged out"})
}
