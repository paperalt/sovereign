#!/usr/bin/env bash
# ==============================================================================
# DISASTER MITIGATION & ENCRYPTED OFFSITE / MULTI-TARGET BACKUP
# Transcribe System -> Encrypted Archive -> (Local / Rclone Cloud / Remote SSH)
# ==============================================================================

set -euo pipefail

TARGET_DEST="${1:-${BACKUP_TARGET:-gdrive:backups/transcribe}}"
TIMESTAMP=$(date -u +"%Y%m%d_%H%M%SZ")
STAGING_DIR="/var/tmp/transcribe_backups"
mkdir -p "$STAGING_DIR"
BACKUP_DIR="${STAGING_DIR}/transcribe_backup_${TIMESTAMP}"
ARCHIVE_NAME="transcribe_backup_${TIMESTAMP}.tar.gz"
ENCRYPTED_NAME="transcribe_backup_${TIMESTAMP}.enc"
KEY_FILE="/root/.transcribe_backup.key"
LOG_FILE="/var/log/transcribe_backup.log"
CONTAINER_NAME="test-postgres-verify"
RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-14}"

log() {
    echo "[$(date -u +"%Y-%m-%dT%H:%M:%SZ")] [BACKUP] $*" | tee -a "$LOG_FILE"
}

if [[ ! -f "$KEY_FILE" ]]; then
    log "FATAL: Encryption key $KEY_FILE not found. Aborting."
    exit 1
fi

mkdir -p "$BACKUP_DIR"
log "Starting disaster recovery backup pipeline..."
log "Target Destination: ${TARGET_DEST}"

# 1. PostgreSQL Database Dump (Custom binary format with compression)
if docker ps --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
    log "Dumping PostgreSQL database 'transcribe' from container '${CONTAINER_NAME}'..."
    docker exec "$CONTAINER_NAME" pg_dump -U postgres -d transcribe -Fc > "${BACKUP_DIR}/postgres_transcribe.dump"
    log "Database dump created: $(du -h "${BACKUP_DIR}/postgres_transcribe.dump" | cut -f1)"
else
    log "FATAL: Container ${CONTAINER_NAME} is not running. Cannot dump database."
    rm -rf "$BACKUP_DIR"
    exit 1
fi

# 2. Package Codebase & Configs (Excluding build caches, gradle, binaries, dumps)
log "Packaging application source and configurations..."
tar -czf "${BACKUP_DIR}/codebase.tar.gz" \
    --exclude=".git" \
    --exclude="bin" \
    --exclude="dist" \
    --exclude="*.db" \
    --exclude="*.log" \
    --exclude="android/.gradle" \
    --exclude="android/build" \
    --exclude="android/app/build" \
    --exclude="migrations" \
    -C /root/audio-transcribe-system .

# 3. Create Unified Tarball
log "Creating unified backup bundle..."
tar -czf "${STAGING_DIR}/${ARCHIVE_NAME}" -C "$BACKUP_DIR" .
rm -rf "$BACKUP_DIR"

# 4. Encrypt using AES-256-CBC with PBKDF2
log "Encrypting backup archive with AES-256-CBC..."
openssl enc -aes-256-cbc -salt -pbkdf2 -iter 100000 \
    -in "${STAGING_DIR}/${ARCHIVE_NAME}" \
    -out "${STAGING_DIR}/${ENCRYPTED_NAME}" \
    -pass file:"$KEY_FILE"

rm -f "${STAGING_DIR}/${ARCHIVE_NAME}"

# 5. Compute SHA-256 Hash for Integrity Verification
CHECKSUM=$(sha256sum "${STAGING_DIR}/${ENCRYPTED_NAME}" | cut -d ' ' -f 1)
log "Encrypted file size: $(du -h "${STAGING_DIR}/${ENCRYPTED_NAME}" | cut -f1), SHA256: ${CHECKSUM}"
echo "${CHECKSUM}  ${ENCRYPTED_NAME}" > "${STAGING_DIR}/${ENCRYPTED_NAME}.sha256"

# 6. Deliver to Configured Destination (Local / SSH / Rclone)
if [[ "$TARGET_DEST" =~ ^(\/|\.\/|\.\.\/|~) ]]; then
    # --- A. LOCAL DIRECTORY / MOUNT POINT ---
    log "Detected Local Directory / Mount Destination: ${TARGET_DEST}"
    mkdir -p "$TARGET_DEST"
    cp "${STAGING_DIR}/${ENCRYPTED_NAME}" "${TARGET_DEST}/"
    cp "${STAGING_DIR}/${ENCRYPTED_NAME}.sha256" "${TARGET_DEST}/"

    if [[ -f "${TARGET_DEST}/${ENCRYPTED_NAME}" ]]; then
        log "Local backup written and verified at ${TARGET_DEST}/${ENCRYPTED_NAME}"
    else
        log "FATAL: Failed to verify local copy."
        rm -f "${STAGING_DIR}/${ENCRYPTED_NAME}" "${STAGING_DIR}/${ENCRYPTED_NAME}.sha256"
        exit 1
    fi

    # Prune local backups older than retention days
    log "Pruning local backups older than ${RETENTION_DAYS} days in ${TARGET_DEST}..."
    find "$TARGET_DEST" -maxdepth 1 -name "transcribe_backup_*.enc*" -type f -mtime +"${RETENTION_DAYS}" -delete || true

elif [[ "$TARGET_DEST" =~ ^[a-zA-Z0-9._-]+@[a-zA-Z0-9.-]+: ]]; then
    # --- B. REMOTE SERVER VIA SSH / SCP / RSYNC ---
    log "Detected Remote SSH / SCP Destination: ${TARGET_DEST}"
    scp -o BatchMode=yes -o StrictHostKeyChecking=accept-new \
        "${STAGING_DIR}/${ENCRYPTED_NAME}" "${STAGING_DIR}/${ENCRYPTED_NAME}.sha256" "$TARGET_DEST/"
    log "Remote SSH copy completed successfully."

else
    # --- C. RCLONE REMOTE (Google Drive, S3, R2, OneDrive, WebDAV, etc.) ---
    log "Detected Rclone Cloud Storage Destination: ${TARGET_DEST}"
    rclone copy "${STAGING_DIR}/${ENCRYPTED_NAME}" "${TARGET_DEST}/"
    rclone copy "${STAGING_DIR}/${ENCRYPTED_NAME}.sha256" "${TARGET_DEST}/"

    if rclone ls "${TARGET_DEST}/${ENCRYPTED_NAME}" >/dev/null 2>&1; then
        log "Remote backup verified on Rclone remote successfully."
    else
        log "FATAL: Failed to verify uploaded backup on remote."
        rm -f "${STAGING_DIR}/${ENCRYPTED_NAME}" "${STAGING_DIR}/${ENCRYPTED_NAME}.sha256"
        exit 1
    fi

    # Apply Retention Policy on Cloud
    log "Applying Rclone retention policy: pruning backups older than ${RETENTION_DAYS} days..."
    rclone delete --min-age "${RETENTION_DAYS}d" "$TARGET_DEST" || true
fi

rm -f "${STAGING_DIR}/${ENCRYPTED_NAME}" "${STAGING_DIR}/${ENCRYPTED_NAME}.sha256"
log "Backup cycle completed successfully to: ${TARGET_DEST}"
