package token

import (
	"errors"
	"fmt"
	"time"

	"github.com/golang-jwt/jwt/v5"
	"github.com/google/uuid"
)

const (
	TokenTypeAccess  = "access"
	TokenTypeRefresh = "refresh"
)

var (
	ErrInvalidToken     = errors.New("token is invalid")
	ErrExpiredToken     = errors.New("token has expired")
	ErrInvalidTokenType = errors.New("invalid token type")
)

// UserClaims defines JWT custom claims for authenticated users.
type UserClaims struct {
	UserID    string `json:"user_id"`
	Email     string `json:"email"`
	TokenType string `json:"token_type"`
	jwt.RegisteredClaims
}

// GenerateJWT creates a standard access token for backward compatibility.
func GenerateJWT(userID, email, secret string, ttl time.Duration) (string, error) {
	return generateTypedJWT(userID, email, secret, TokenTypeAccess, ttl)
}

// GenerateTokenPair generates both an access token and a refresh token.
func GenerateTokenPair(userID, email, secret string, accessTTL, refreshTTL time.Duration) (string, string, error) {
	if accessTTL <= 0 {
		accessTTL = 15 * time.Minute
	}
	if refreshTTL <= 0 {
		refreshTTL = 7 * 24 * time.Hour
	}

	accessToken, err := generateTypedJWT(userID, email, secret, TokenTypeAccess, accessTTL)
	if err != nil {
		return "", "", fmt.Errorf("failed to generate access token: %w", err)
	}

	refreshToken, err := generateTypedJWT(userID, email, secret, TokenTypeRefresh, refreshTTL)
	if err != nil {
		return "", "", fmt.Errorf("failed to generate refresh token: %w", err)
	}

	return accessToken, refreshToken, nil
}

func generateTypedJWT(userID, email, secret, tokenType string, ttl time.Duration) (string, error) {
	if secret == "" {
		return "", errors.New("jwt secret cannot be empty")
	}

	claims := UserClaims{
		UserID:    userID,
		Email:     email,
		TokenType: tokenType,
		RegisteredClaims: jwt.RegisteredClaims{
			ID:        uuid.NewString(),
			ExpiresAt: jwt.NewNumericDate(time.Now().Add(ttl)),
			IssuedAt:  jwt.NewNumericDate(time.Now()),
			NotBefore: jwt.NewNumericDate(time.Now()),
			Subject:   userID,
		},
	}

	token := jwt.NewWithClaims(jwt.SigningMethodHS256, claims)
	return token.SignedString([]byte(secret))
}

// ValidateJWT parses and validates a JWT token string.
func ValidateJWT(tokenString, secret string) (*UserClaims, error) {
	token, err := jwt.ParseWithClaims(tokenString, &UserClaims{}, func(t *jwt.Token) (interface{}, error) {
		if _, ok := t.Method.(*jwt.SigningMethodHMAC); !ok {
			return nil, fmt.Errorf("unexpected signing method: %v", t.Header["alg"])
		}
		return []byte(secret), nil
	})

	if err != nil {
		if errors.Is(err, jwt.ErrTokenExpired) {
			return nil, ErrExpiredToken
		}
		return nil, ErrInvalidToken
	}

	claims, ok := token.Claims.(*UserClaims)
	if !ok || !token.Valid {
		return nil, ErrInvalidToken
	}

	return claims, nil
}

// ValidateAccessToken parses and validates an access token specifically.
func ValidateAccessToken(tokenString, secret string) (*UserClaims, error) {
	claims, err := ValidateJWT(tokenString, secret)
	if err != nil {
		return nil, err
	}
	if claims.TokenType != "" && claims.TokenType != TokenTypeAccess {
		return nil, ErrInvalidTokenType
	}
	return claims, nil
}

// ValidateRefreshToken parses and validates a refresh token specifically.
func ValidateRefreshToken(tokenString, secret string) (*UserClaims, error) {
	claims, err := ValidateJWT(tokenString, secret)
	if err != nil {
		return nil, err
	}
	if claims.TokenType != TokenTypeRefresh {
		return nil, ErrInvalidTokenType
	}
	return claims, nil
}
