package token

import (
	"testing"
	"time"
)

func TestArgon2id_HashAndVerify(t *testing.T) {
	rawPassword := "AkemiHomura#2026!SecureKey"

	// Fast params for unit test execution
	testParams := &Argon2Params{
		Memory:      8 * 1024,
		Iterations:  1,
		Parallelism: 2,
		SaltLength:  16,
		KeyLength:   32,
	}

	hash, err := HashPassword(rawPassword, testParams)
	if err != nil {
		t.Fatalf("failed to hash password: %v", err)
	}

	match, err := VerifyPassword(rawPassword, hash)
	if err != nil {
		t.Fatalf("failed to verify password: %v", err)
	}
	if !match {
		t.Errorf("expected password to match hash")
	}

	// Negative test: wrong password
	match, err = VerifyPassword("WrongPassword123", hash)
	if err != nil {
		t.Fatalf("unexpected error on wrong password verify: %v", err)
	}
	if match {
		t.Errorf("expected wrong password to not match")
	}

	// Negative test: invalid format
	_, err = VerifyPassword(rawPassword, "invalid$argon$hash")
	if err == nil {
		t.Errorf("expected error for malformed hash")
	}
}

func TestJWT_GenerateAndValidate(t *testing.T) {
	secret := "ultra-secure-jwt-secret-key-32bytes!!"
	userID := "0194eb87-c20d-71b5-93ec-e6b8c8d8b2d1"
	email := "user@eclipsegate.my.id"

	// Step 1: Valid token
	tokenStr, err := GenerateJWT(userID, email, secret, 15*time.Minute)
	if err != nil {
		t.Fatalf("failed to generate token: %v", err)
	}

	claims, err := ValidateJWT(tokenStr, secret)
	if err != nil {
		t.Fatalf("failed to validate token: %v", err)
	}

	if claims.UserID != userID {
		t.Errorf("expected userID %q, got %q", userID, claims.UserID)
	}
	if claims.Email != email {
		t.Errorf("expected email %q, got %q", email, claims.Email)
	}

	// Step 2: Negative test - invalid secret
	_, err = ValidateJWT(tokenStr, "wrong-secret-key-000000000000000000")
	if err == nil {
		t.Errorf("expected validation to fail with wrong secret")
	}

	// Step 3: Negative test - expired token
	expiredToken, err := GenerateJWT(userID, email, secret, -1*time.Minute)
	if err != nil {
		t.Fatalf("failed to generate expired token: %v", err)
	}

	_, err = ValidateJWT(expiredToken, secret)
	if err != ErrExpiredToken {
		t.Errorf("expected ErrExpiredToken, got %v", err)
	}

	// Step 4: Token Pair (Access + Refresh)
	access, refresh, err := GenerateTokenPair(userID, email, secret, 15*time.Minute, 7*24*time.Hour)
	if err != nil {
		t.Fatalf("failed to generate token pair: %v", err)
	}

	accessClaims, err := ValidateAccessToken(access, secret)
	if err != nil {
		t.Fatalf("failed to validate access token: %v", err)
	}
	if accessClaims.TokenType != TokenTypeAccess {
		t.Errorf("expected access token type, got %s", accessClaims.TokenType)
	}

	refreshClaims, err := ValidateRefreshToken(refresh, secret)
	if err != nil {
		t.Fatalf("failed to validate refresh token: %v", err)
	}
	if refreshClaims.TokenType != TokenTypeRefresh {
		t.Errorf("expected refresh token type, got %s", refreshClaims.TokenType)
	}

	// Negative test: Refresh token cannot be used as Access token
	if _, err := ValidateAccessToken(refresh, secret); err != ErrInvalidTokenType {
		t.Errorf("expected ErrInvalidTokenType when passing refresh token as access token, got %v", err)
	}
}

