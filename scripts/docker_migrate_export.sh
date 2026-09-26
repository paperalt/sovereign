#!/usr/bin/env bash
# ==============================================================================
# Transcribe Core - Automated Migration Export Tool
# Creates a portable, AES-256 encrypted archive for instant cross-server migration
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
MIGRATION_TEMP_DIR="/tmp/transcribe_migration_${TIMESTAMP}"
OUTPUT_DIR="${ROOT_DIR}/migrations"
ARCHIVE_NAME="transcribe_stack_${TIMESTAMP}.tar.gz"
ENCRYPTED_BUNDLE="${OUTPUT_DIR}/transcribe_stack_${TIMESTAMP}.migration.enc"

# Encryption key file or fallback
KEY_FILE="/root/.transcribe_backup.key"
if [[ ! -f "$KEY_FILE" ]]; then
    mkdir -p "$(dirname "$KEY_FILE")"
    openssl rand -hex 32 > "$KEY_FILE"
    chmod 600 "$KEY_FILE"
    echo "[INFO] Generated new AES-256 encryption key at ${KEY_FILE}"
fi

mkdir -p "$MIGRATION_TEMP_DIR" "$OUTPUT_DIR"
echo "================================================================="
echo " [MIGRATION EXPORT] Starting Full Stack Migration Export"
echo " Timestamp : ${TIMESTAMP}"
echo " Source Dir: ${ROOT_DIR}"
echo "================================================================="

# ------------------------------------------------------------------------------
# 1. Database Export
# ------------------------------------------------------------------------------
echo "[1/5] Dumping Database..."
DB_DUMP_FILE="${MIGRATION_TEMP_DIR}/database_dump.sql"

# Check if PostgreSQL is running via Docker or local host
if docker ps --format '{{.Names}}' | grep -q "transcribe-postgres"; then
    echo "      Detected active 'transcribe-postgres' container. Dumping via docker exec..."
    docker exec transcribe-postgres pg_dump -U postgres -d transcribe --clean --if-exists > "$DB_DUMP_FILE"
elif docker ps --format '{{.Names}}' | grep -q "test-postgres-verify"; then
    echo "      Detected active 'test-postgres-verify' container. Dumping via docker exec..."
    docker exec test-postgres-verify pg_dump -U postgres -d transcribe --clean --if-exists > "$DB_DUMP_FILE"
elif command -v pg_dump >/dev/null 2>&1; then
    echo "      Dumping via host pg_dump..."
    pg_dump -h 127.0.0.1 -p 5432 -U postgres -d transcribe --clean --if-exists > "$DB_DUMP_FILE" || true
fi

# Also check for SQLite file if present
if [[ -f "/data/transcribe.db" ]]; then
    echo "      Detected SQLite DB at /data/transcribe.db. Archiving..."
    cp "/data/transcribe.db" "${MIGRATION_TEMP_DIR}/transcribe.db"
fi

# ------------------------------------------------------------------------------
# 2. Package Environment & Configuration
# ------------------------------------------------------------------------------
echo "[2/5] Packaging Environment & Configs..."
if [[ -f "${ROOT_DIR}/.env" ]]; then
    cp "${ROOT_DIR}/.env" "${MIGRATION_TEMP_DIR}/.env"
elif [[ -f "/root/.transcribe_env" ]]; then
    cp "/root/.transcribe_env" "${MIGRATION_TEMP_DIR}/.env"
fi

# ------------------------------------------------------------------------------
# 3. Package Downloads & Static Assets (APKs, icons)
# ------------------------------------------------------------------------------
echo "[3/5] Packaging Static Assets & Downloads..."
mkdir -p "${MIGRATION_TEMP_DIR}/downloads"
if [[ -d "/var/www/downloads" ]]; then
    cp -r /var/www/downloads/* "${MIGRATION_TEMP_DIR}/downloads/" 2>/dev/null || true
fi

# ------------------------------------------------------------------------------
# 4. Create Metadata Manifest
# ------------------------------------------------------------------------------
echo "[4/5] Generating Manifest..."
cat << EOF > "${MIGRATION_TEMP_DIR}/manifest.json"
{
  "timestamp": "${TIMESTAMP}",
  "version": "1.0.0",
  "source_host": "$(hostname)",
  "docker_version": "$(docker --version 2>/dev/null || echo 'unknown')",
  "git_commit": "$(git -C "$ROOT_DIR" rev-parse HEAD 2>/dev/null || echo 'unknown')"
}
EOF

# ------------------------------------------------------------------------------
# 5. Compress and Encrypt Bundle
# ------------------------------------------------------------------------------
echo "[5/5] Encrypting Migration Bundle with AES-256-CBC..."
tar -czf "${MIGRATION_TEMP_DIR}/${ARCHIVE_NAME}" -C "$MIGRATION_TEMP_DIR" .env database_dump.sql manifest.json downloads

openssl enc -aes-256-cbc -pbkdf2 -iter 100000 \
    -in "${MIGRATION_TEMP_DIR}/${ARCHIVE_NAME}" \
    -out "$ENCRYPTED_BUNDLE" \
    -pass file:"$KEY_FILE"

SHA256_HASH=$(sha256sum "$ENCRYPTED_BUNDLE" | awk '{print $1}')
echo "$SHA256_HASH  $(basename "$ENCRYPTED_BUNDLE")" > "${ENCRYPTED_BUNDLE}.sha256"

# Cleanup temporary plain files
rm -rf "$MIGRATION_TEMP_DIR"

BUNDLE_SIZE=$(du -h "$ENCRYPTED_BUNDLE" | awk '{print $1}')
echo "================================================================="
echo " [SUCCESS] Migration Export Completed!"
echo " Encrypted Bundle: ${ENCRYPTED_BUNDLE}"
echo " Bundle Size     : ${BUNDLE_SIZE}"
echo " SHA-256         : ${SHA256_HASH}"
echo " Key File        : ${KEY_FILE}"
echo ""
echo " To migrate to a new server, copy both:"
echo " 1. ${ENCRYPTED_BUNDLE}"
echo " 2. ${KEY_FILE}"
echo " And run: ./scripts/docker_migrate_restore.sh <path-to-bundle>"
echo "================================================================="
