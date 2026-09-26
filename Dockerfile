# ==============================================================================
# Multi-stage Dockerfile for Transcribe Core Backend
# Production-ready, minimal attack surface, scratch/alpine base
# ==============================================================================

# Stage 1: Build binary
FROM golang:1.26-alpine AS builder

WORKDIR /src

# Install build dependencies
RUN apk add --no-cache ca-certificates tzdata git

# Cache Go modules
COPY go.mod go.sum ./
RUN go mod download

# Copy source tree
COPY . .

# Compile static binary with optimizations (-s -w strips debug symbols)
RUN CGO_ENABLED=0 GOOS=linux GOARCH=amd64 \
    go build -ldflags="-s -w -X main.version=1.0.0" -o /bin/transcribe-server ./cmd/server

# Stage 2: Minimal runtime image
FROM alpine:3.21

WORKDIR /app

# Install runtime dependencies (ca-certificates for external HTTPS calls, tzdata for accurate logs)
RUN apk add --no-cache ca-certificates tzdata wget curl \
    && addgroup -S appgroup && adduser -S appuser -G appgroup \
    && mkdir -p /app/downloads /data \
    && chown -R appuser:appgroup /app /data

# Copy compiled binary from builder
COPY --from=builder /bin/transcribe-server /app/transcribe-server

# Switch to unprivileged user
USER appuser

# Expose HTTP port
EXPOSE 8080

# Environment defaults
ENV PORT=8080 \
    DOWNLOADS_DIR=/app/downloads \
    SQLITE_PATH=/data/transcribe.db

# Health check
HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://127.0.0.1:8080/health || exit 1

# Entrypoint
ENTRYPOINT ["/app/transcribe-server"]
