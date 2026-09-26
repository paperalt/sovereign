package service

import (
	"context"
	"errors"
	"fmt"

	"google.golang.org/api/idtoken"
)

// GoogleClaims encapsulates verified identity claims issued by Google.
type GoogleClaims struct {
	GoogleID      string
	Email         string
	FullName      string
	EmailVerified bool
}

// GoogleTokenValidator abstracts token validation for production and testing.
type GoogleTokenValidator interface {
	ValidateToken(ctx context.Context, tokenString string) (*GoogleClaims, error)
}

// ProductionGoogleValidator validates Google OpenID Connect ID tokens against Google's certificates.
type ProductionGoogleValidator struct {
	clientID string
}

func NewProductionGoogleValidator(clientID string) GoogleTokenValidator {
	return &ProductionGoogleValidator{clientID: clientID}
}

func (v *ProductionGoogleValidator) ValidateToken(ctx context.Context, tokenString string) (*GoogleClaims, error) {
	if tokenString == "" {
		return nil, errors.New("empty id_token")
	}

	payload, err := idtoken.Validate(ctx, tokenString, v.clientID)
	if err != nil {
		return nil, fmt.Errorf("google token validation failed: %w", err)
	}

	email, _ := payload.Claims["email"].(string)
	name, _ := payload.Claims["name"].(string)
	emailVerified, _ := payload.Claims["email_verified"].(bool)

	return &GoogleClaims{
		GoogleID:      payload.Subject,
		Email:         email,
		FullName:      name,
		EmailVerified: emailVerified,
	}, nil
}

// MockGoogleValidator allows deterministic testing of OAuth flows.
type MockGoogleValidator struct {
	ExpectedClaims map[string]*GoogleClaims
}

func NewMockGoogleValidator() *MockGoogleValidator {
	return &MockGoogleValidator{
		ExpectedClaims: make(map[string]*GoogleClaims),
	}
}

func (m *MockGoogleValidator) ValidateToken(ctx context.Context, tokenString string) (*GoogleClaims, error) {
	claims, ok := m.ExpectedClaims[tokenString]
	if !ok {
		return nil, errors.New("invalid or unmocked google token")
	}
	return claims, nil
}
