package middleware

import (
	"context"
	"encoding/json"
	"net/http"
	"strings"

	"github.com/paperalt/sovereign-speech-intelligence/pkg/token"
)

type contextKey string

const UserContextKey contextKey = "user_claims"

// RequireAuth validates incoming Bearer JWT tokens and attaches claims to the request context.
func RequireAuth(jwtSecret string) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			authHeader := r.Header.Get("Authorization")
			if authHeader == "" {
				respondJSONError(w, http.StatusUnauthorized, "missing authorization header")
				return
			}

			parts := strings.SplitN(authHeader, " ", 2)
			if len(parts) != 2 || strings.ToLower(parts[0]) != "bearer" {
				respondJSONError(w, http.StatusUnauthorized, "invalid authorization header format")
				return
			}

			claims, err := token.ValidateAccessToken(parts[1], jwtSecret)
			if err != nil {
				respondJSONError(w, http.StatusUnauthorized, "invalid or expired token")
				return
			}

			ctx := context.WithValue(r.Context(), UserContextKey, claims)
			next.ServeHTTP(w, r.WithContext(ctx))
		})
	}
}

// GetUserClaims retrieves user claims from context.
func GetUserClaims(ctx context.Context) *token.UserClaims {
	claims, ok := ctx.Value(UserContextKey).(*token.UserClaims)
	if !ok {
		return nil
	}
	return claims
}

func respondJSONError(w http.ResponseWriter, status int, message string) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	json.NewEncoder(w).Encode(map[string]string{"error": message})
}
