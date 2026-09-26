# Disaster Recovery & Multi-Target Backup System
**Encrypted AES-256-CBC Backups to Cloud Storage (Rclone), Local Mounts, or Remote Servers (SSH)**

[![Release Version](https://img.shields.io/badge/Release-v2.1.0%20(Build%2042)-38BDF8?style=flat-square)](https://gate.eclipsegate.my.id/downloads/transcribe-core.apk)

---

> 🌐 **Language:** **English** | [Versi Bahasa Indonesia](DISASTER_RECOVERY_ID.md)

---

## 1. Executive Summary

To ensure high availability, zero data loss, and swift recovery in the event of a catastrophic host failure, the platform implements an automated disaster mitigation system:
1. **Automated Maintenance Cleaner (`scripts/cleaner.sh`):** Purges expired/revoked session tokens, closes dangling meetings (`IN_PROGRESS` > 24 hours), vacuums database indexes, and deletes temporary audio files.
2. **Encrypted Multi-Target Backup (`scripts/backup.sh`):**
   * Consistent binary PostgreSQL 16 snapshots (`pg_dump -Fc`).
   * Bundled application configurations and codebase.
   * Military-grade **AES-256-CBC (PBKDF2 100,000 iterations)** encryption via `/root/.transcribe_backup.key`.
   * Automatic **SHA-256 digest integrity verification**.
   * **Flexible Multi-Destination Support:**
     * **Cloud Object Storage (Rclone):** Google Drive (`gdrive:backups/transcribe`), AWS S3 (`s3:bucket`), Cloudflare R2 (`r2:bucket`), OneDrive, WebDAV.
     * **Local Directories & Disk Mounts:** `/var/backups/transcribe`, external HDD `/mnt/...`.
     * **Remote Servers (SSH / SCP):** `user@remote-host:/path/to/backups`.
   * **Retention Policy:** Automated pruning of backups older than 14 days across all targets.

---

## 2. Backup & Restoration Pipeline Topology

```
[ PostgreSQL 16 ] ──(pg_dump -Fc)──┐
                                   ├─► [ Unified Tarball ] ──► [ OpenSSL AES-256-CBC ]
[ Codebase & Configs ] ────────────┘                              │ (Key: ~/.transcribe_backup.key)
                                                                  ▼
                                                      [ Encrypted File (.enc) ]
                                                                  │
                                                      [ Generate SHA-256 Hash ]
                                                                  │
                                      ┌───────────────────────────┴───────────────────────────┐
                                      ▼                                                       ▼
                        [ Target A: Rclone Cloud Storage ]                     [ Target B: Local Directory / Mount ]
                        gdrive:backups/transcribe                              /var/backups/transcribe
                        s3:backup-bucket / r2:transcribe                       /mnt/external_storage/backups
                                      │                                                       │
                                      └───────────────────────────┬───────────────────────────┘
                                                                  ▼
                                                [ Target C: Remote Server (SSH/SCP) ]
                                                user@103.158.154.116:/home/alterpix/backups
```

---

## 3. Operational CLI Usage & Multi-Target Execution

### A. Encrypted Backup Execution (`scripts/backup.sh`)
The script automatically detects the destination format via CLI argument or the `BACKUP_TARGET` environment variable:

```bash
# 1. Backup to Google Drive (Default / Daily Cron at 02:00 UTC)
/root/audio-transcribe-system/scripts/backup.sh

# 2. Backup to Local Directory or Mounted Disk
/root/audio-transcribe-system/scripts/backup.sh /var/backups/transcribe

# 3. Backup to Cloud S3 / Cloudflare R2 via Rclone
/root/audio-transcribe-system/scripts/backup.sh r2:transcribe-backups/prod

# 4. Backup to Remote Server via SSH / SCP
/root/audio-transcribe-system/scripts/backup.sh alterpix@103.158.154.116:/home/alterpix/harddisk/backups
```

### B. Disaster Recovery & Data Restoration (`scripts/restore.sh`)
Restores database tables, relation integrity, and codebase from any target destination:

```bash
# 1. Automatic restoration from Google Drive (Fetches latest snapshot)
/root/audio-transcribe-system/scripts/restore.sh

# 2. Restore from Local Directory
/root/audio-transcribe-system/scripts/restore.sh /var/backups/transcribe

# 3. Restore specific archive from Rclone Remote
/root/audio-transcribe-system/scripts/restore.sh gdrive:backups/transcribe transcribe_backup_20260925_150536Z.enc
```

---

## 4. Systemd Timer Automation

```
● transcribe-cleaner.timer - Run Transcribe Cleaner Hourly
  Next Trigger: Every 1 hour (Synchronized)
  Unit Service: transcribe-cleaner.service

● transcribe-backup.timer - Run Transcribe Offsite Backup Daily at 02:00 UTC
  Next Trigger: Daily at 02:00:00 UTC
  Unit Service: transcribe-backup.service
```

---

## 5. Live Empirical Verification

1. **Local Directory Backup Verification:**
   `Local backup written and verified at /var/backups/transcribe/transcribe_backup_20260925_150536Z.enc. Encrypted size: 1.2M, SHA256: 1490182df50addf8...`
2. **Local Directory Restore Verification:**
   `Downloaded latest .enc. Checksum verified: OK. Decrypted AES-256. Database restored: 350 meetings restored successfully.`
3. **Cloud Rclone Backup Verification:**
   `Uploaded encrypted backup to gdrive:backups/transcribe. Remote backup verified on Google Drive successfully.`
