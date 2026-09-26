#!/usr/bin/env bash
# ==============================================================================
# AUTOMATED CLEANER & MAINTENANCE SCRIPT
# Transcribe System - Database Maintenance & Temp Purge
# ==============================================================================

set -euo pipefail

LOG_FILE="/var/log/transcribe_cleaner.log"
TIMESTAMP=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

log() {
    echo "[$TIMESTAMP] [CLEANER] $*" | tee -a "$LOG_FILE"
}

log "Starting maintenance cycle..."

# 1. Purge Revoked & Expired Refresh Tokens from PostgreSQL
CONTAINER_NAME="test-postgres-verify"
if docker ps --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
    log "Purging revoked and expired sessions from PostgreSQL..."
    PURGED_TOKENS=$(docker exec "$CONTAINER_NAME" psql -U postgres -d transcribe -t -A -c "
        WITH deleted AS (
            DELETE FROM refresh_tokens 
            WHERE revoked_at IS NOT NULL OR expires_at < CURRENT_TIMESTAMP
            RETURNING id
        )
        SELECT count(*) FROM deleted;
    ")
    log "Purged ${PURGED_TOKENS} expired/revoked session tokens."

    # 2. Mark Stale Meetings (> 24 hours in progress) as FAILED
    STALE_MEETINGS=$(docker exec "$CONTAINER_NAME" psql -U postgres -d transcribe -t -A -c "
        WITH updated AS (
            UPDATE meetings 
            SET status = 'FAILED', ended_at = CURRENT_TIMESTAMP 
            WHERE status = 'IN_PROGRESS' AND started_at < (CURRENT_TIMESTAMP - INTERVAL '24 hours')
            RETURNING id
        )
        SELECT count(*) FROM updated;
    ")
    log "Marked ${STALE_MEETINGS} abandoned meetings as FAILED."

    # 3. Optimize PostgreSQL table storage
    docker exec "$CONTAINER_NAME" psql -U postgres -d transcribe -c "VACUUM ANALYZE refresh_tokens; VACUUM ANALYZE meetings;" >/dev/null 2>&1 || true
    log "Table VACUUM ANALYZE completed."
else
    log "WARNING: PostgreSQL container ${CONTAINER_NAME} is not running. Skipping DB purge."
fi

# 4. Purge Dangling Temporary Audio Files in /tmp (> 2 hours old)
log "Cleaning dangling temporary audio files in /tmp..."
DELETED_FILES=0
while IFS= read -r file; do
    if [[ -f "$file" ]]; then
        rm -f "$file"
        DELETED_FILES=$((DELETED_FILES + 1))
    fi
done < <(find /tmp -maxdepth 1 -type f \( -name "*.mp3" -o -name "*.ogg" -o -name "*.wav" -o -name "*.pcm" \) -mmin +120 2>/dev/null || true)

log "Purged ${DELETED_FILES} dangling temporary audio files."
log "Maintenance cycle completed successfully."
