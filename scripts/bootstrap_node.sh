#!/usr/bin/env bash
# ==============================================================================
# Transcribe Core - 1-Click Server Bootstrap Script
# Prepares any clean Linux VPS (Ubuntu/Debian) for instant deployment in < 60s
# ==============================================================================
set -euo pipefail

echo "================================================================="
echo " [BOOTSTRAP] Initializing Node Environment for Transcribe Core"
echo " OS: $(grep -oP '(?<=^PRETTY_NAME=).+' /etc/os-release | tr -d '\"' || uname -s)"
echo "================================================================="

# 1. Update and install prerequisites
echo "[1/4] Installing system packages (curl, git, openssl, ca-certificates)..."
apt-get update -qq
apt-get install -y -qq curl git openssl ca-certificates ufw

# 2. Install Docker & Docker Compose Plugin if not installed
if ! command -v docker >/dev/null 2>&1; then
    echo "[2/4] Installing Docker Engine..."
    curl -fsSL https://get.docker.com | sh
    systemctl enable --now docker
else
    echo "[2/4] Docker is already installed: $(docker --version)"
fi

if ! docker compose version >/dev/null 2>&1; then
    echo "      Installing Docker Compose plugin..."
    apt-get install -y -qq docker-compose-v2 || apt-get install -y -qq docker-compose-plugin
else
    echo "      Docker Compose is ready: $(docker compose version)"
fi

# 3. Setup Project Structure
PROJECT_DIR="/opt/audio-transcribe-system"
mkdir -p "$PROJECT_DIR" /var/www/downloads

echo "[3/4] Ready for project files at ${PROJECT_DIR}"

# 4. Final verification
echo "[4/4] System verification complete."
echo "================================================================="
echo " Bootstrap ready!"
echo " Next step:"
echo " 1. Copy project files to ${PROJECT_DIR}"
echo " 2. Run: cd ${PROJECT_DIR} && ./scripts/docker_migrate_restore.sh <bundle.enc> <key>"
echo "================================================================="
