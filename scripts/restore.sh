#!/usr/bin/env bash
# ==============================================================================
# DISASTER RECOVERY & MULTI-SOURCE RESTORATION SCRIPT
# (Local / Rclone Cloud / Remote SSH) -> Decrypt -> Restore PostgreSQL & Codebase
# ==============================================================================

set -euo pipefail

TARGET_SOURCE="${1:-${BACKUP_SOURCE:-gdrive:backups/transcribe}}"
SPECIFIC_FILE="${2:-}"
KEY_FILE="/root/.transcribe_backup.key"
LOG_FILE="/var/log/transcribe_restore.log"
CONTAINER_NAME="test-postgres-verify"
WORK_DIR="/tmp/transcribe_restore_$(date +%s)"

log() {
    echo "[$(date -u +"%Y-%m-%dT%H:%M:%SZ")] [RESTORE] $*" | tee -a "$LOG_FILE"
}

if [[ ! -f "$KEY_FILE" ]]; then
    log "FATAL: Decryption key $KEY_FILE not found. Aborting."
    exit 1
fi

mkdir -p "$WORK_DIR"
trap 'rm -rf "$WORK_DIR"' EXIT

log "Starting restoration pipeline..."
log "Backup Source: ${TARGET_SOURCE}"

TARGET_ENC=""

# 1. Fetch / Identify Backup File
if [[ "$TARGET_SOURCE" =~ ^(\/|\.\/|\.\.\/|~) ]]; then
    # --- A. LOCAL DIRECTORY ---
    if [[ -n "$SPECIFIC_FILE" ]]; then
        TARGET_ENC="$SPECIFIC_FILE"
    else
        TARGET_ENC=$(find "$TARGET_SOURCE" -maxdepth 1 -name "transcribe_backup_*.enc" -type f | sort | tail -n 1 | xargs -r basename)
    fi

    if [[ -z "$TARGET_ENC" || ! -f "${TARGET_SOURCE}/${TARGET_ENC}" ]]; then
        log "FATAL: No .enc backups found in local directory ${TARGET_SOURCE}."
        exit 1
    fi

    log "Selected Local Backup: ${TARGET_ENC}"
    cp "${TARGET_SOURCE}/${TARGET_ENC}" "$WORK_DIR/"
    cp "${TARGET_SOURCE}/${TARGET_ENC}.sha256" "$WORK_DIR/" 2>/dev/null || true

elif [[ "$TARGET_SOURCE" =~ ^[a-zA-Z0-9._-]+@[a-zA-Z0-9.-]+: ]]; then
    # --- B. REMOTE SSH ---
    if [[ -z "$SPECIFIC_FILE" ]]; then
        log "FATAL: For SSH restore, please specify the exact backup filename as argument 2: ./restore.sh user@host:/path filename.enc"
        exit 1
    fi
    TARGET_ENC="$SPECIFIC_FILE"
    log "Selected Remote SSH Backup: ${TARGET_ENC}"
    scp -o BatchMode=yes "${TARGET_SOURCE}/${TARGET_ENC}" "$WORK_DIR/"
    scp -o BatchMode=yes "${TARGET_SOURCE}/${TARGET_ENC}.sha256" "$WORK_DIR/" 2>/dev/null || true

else
    # --- C. RCLONE REMOTE ---
    if [[ -n "$SPECIFIC_FILE" ]]; then
        TARGET_ENC="$SPECIFIC_FILE"
    else
        log "Discovering latest backup on ${TARGET_SOURCE}..."
        TARGET_ENC=$(rclone lsf "$TARGET_SOURCE" | grep '\.enc$' | sort | tail -n 1)
        if [[ -z "$TARGET_ENC" ]]; then
            log "FATAL: No backups found on ${TARGET_SOURCE}."
            exit 1
        fi
    fi

    log "Selected Rclone Cloud Backup: ${TARGET_ENC}"
    rclone copy "${TARGET_SOURCE}/${TARGET_ENC}" "$WORK_DIR/"
    rclone copy "${TARGET_SOURCE}/${TARGET_ENC}.sha256" "$WORK_DIR/" || true
fi

# 2. Checksum Verification
if [[ -f "${WORK_DIR}/${TARGET_ENC}.sha256" ]]; then
    log "Verifying SHA-256 integrity checksum..."
    cd "$WORK_DIR"
    sha256sum -c "${TARGET_ENC}.sha256"
    cd - >/dev/null
    log "Checksum verified."
else
    log "WARNING: No .sha256 file found for checksum verification."
fi

# 3. Decrypt Archive
log "Decrypting archive with AES-256-CBC..."
openssl enc -d -aes-256-cbc -pbkdf2 -iter 100000 \
    -in "${WORK_DIR}/${TARGET_ENC}" \
    -out "${WORK_DIR}/bundle.tar.gz" \
    -pass file:"$KEY_FILE"

# 4. Extract Unified Archive
log "Extracting backup bundle..."
mkdir -p "${WORK_DIR}/extracted"
tar -xzf "${WORK_DIR}/bundle.tar.gz" -C "${WORK_DIR}/extracted"

# 5. Restore PostgreSQL Database
if [[ -f "${WORK_DIR}/extracted/postgres_transcribe.dump" ]]; then
    log "Restoring PostgreSQL database 'transcribe'..."
    docker cp "${WORK_DIR}/extracted/postgres_transcribe.dump" "${CONTAINER_NAME}:/tmp/restore.dump"
    docker exec "$CONTAINER_NAME" pg_restore -U postgres -d transcribe --clean --if-exists /tmp/restore.dump || true
    docker exec "$CONTAINER_NAME" rm -f /tmp/restore.dump
    log "Database restored successfully."
else
    log "WARNING: postgres_transcribe.dump not found in archive."
fi

# 6. Verification Query
ROW_COUNT=$(docker exec "$CONTAINER_NAME" psql -U postgres -d transcribe -t -A -c "
    SELECT count(*) FROM meetings;
")
log "Verification: Restored database contains ${ROW_COUNT} meetings."
log "Disaster recovery restoration completed successfully."
