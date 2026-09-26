# Sistem Mitigasi Bencana & Pencadangan Multi-Target (Disaster Recovery)
**Pencadangan Terenkripsi AES-256-CBC ke Cloud (Rclone), Direktori Lokal, atau Server Remote (SSH)**

[![Release Version](https://img.shields.io/badge/Release-v2.1.0%20(Build%2042)-38BDF8?style=flat-square)](https://gate.eclipsegate.my.id/downloads/transcribe-core.apk)

---

> 🌐 **Bahasa:** **Bahasa Indonesia** | [English Version](DISASTER_RECOVERY.md)

---

## 1. Ringkasan Eksekutif

Untuk menjamin ketersediaan tinggi (*high availability*), kebersihan data, dan integritas jika terjadi kegagalan server total (*catastrophic host failure*), sistem telah dilengkapi dengan:
1. **Pembersih Otomatis (Maintenance Cleaner):** Membersihkan token sesi usang, menutup rapat menggantung, dan membuang file audio sementara di memori/disk.
2. **Mitigasi Bencana & Pencadangan Multi-Target:**
   - Snapshot konsisten PostgreSQL 16 (format binary terkompresi).
   - Packaging codebase & konfigurasi produksi.
   - Enkripsi kuat **AES-256-CBC (PBKDF2 100.000 iterasi)** dengan kunci kriptografis lokal (`/root/.transcribe_backup.key`).
   - Verifikasi integritas otomatis berbasis **SHA-256 digest**.
   - **Dukungan Lokasi Target Fleksibel:**
     * **Cloud Object Storage (Rclone):** Google Drive (`gdrive:backups/transcribe`), AWS S3 (`s3:bucket`), Cloudflare R2 (`r2:bucket`), OneDrive, WebDAV.
     * **Direktori Lokal / Mount Disk Tambahan:** `/var/backups/transcribe`, mount HDD eksternal `/mnt/...`.
     * **Remote Server (SSH / SCP):** `user@remote-node:/path/to/backups`.
   - *Retention Policy:* Pruning otomatis cadangan lebih dari 14 hari.

---

## 2. Arsitektur Pipeline Cadangan & Restorasi

```
[ PostgreSQL 16 ] ──(pg_dump -Fc)──┐
                                   ├─► [ Unified Tarball ] ──► [ OpenSSL AES-256-CBC ]
[ Codebase & Configs ] ────────────┘                              │ (Kunci: ~/.transcribe_backup.key)
                                                                  ▼
                                                      [ File Terenkripsi (.enc) ]
                                                                  │
                                                      [ Generate SHA-256 Hash ]
                                                                  │
                                      ┌───────────────────────────┴───────────────────────────┐
                                      ▼                                                       ▼
                         [ Lokasi A: Rclone Cloud ]                              [ Lokasi B: Direktori Lokal / Mount ]
                         gdrive:backups/transcribe                               /var/backups/transcribe
                         s3:backup-bucket / r2:transcribe                        /mnt/external_hdd/backups
                                      │                                                       │
                                      └───────────────────────────┬───────────────────────────┘
                                                                  ▼
                                                 [ Lokasi C: Remote Server (SSH/SCP) ]
                                                 user@103.158.154.116:/home/alterpix/backups
```

---

## 3. Komponen Script & Operasional Multi-Target

### A. Pencadangan Terenkripsi (`scripts/backup.sh`)
Script mendeteksi tipe destinasi secara dinamis berdasarkan format argumen atau variabel `BACKUP_TARGET`:

```bash
# 1. Pencadangan ke Google Drive (Default / Cron Harian 02:00 UTC)
/root/audio-transcribe-system/scripts/backup.sh

# 2. Pencadangan ke Direktori Lokal / Mount HDD Eksternal
/root/audio-transcribe-system/scripts/backup.sh /var/backups/transcribe

# 3. Pencadangan ke Cloud S3 / Cloudflare R2 via Rclone
/root/audio-transcribe-system/scripts/backup.sh r2:transcribe-backups/prod

# 4. Pencadangan ke Remote Server via SSH / SCP
/root/audio-transcribe-system/scripts/backup.sh alterpix@103.158.154.116:/home/alterpix/harddisk/backups
```

### B. Restorasi Bencana / Pemulihan Data (`scripts/restore.sh`)
Script mendukung pemulihan langsung dari lokasi sumber yang diinginkan:

```bash
# 1. Restorasi otomatis dari Google Drive (Mengambil snapshot terbaru)
/root/audio-transcribe-system/scripts/restore.sh

# 2. Restorasi dari Direktori Lokal
/root/audio-transcribe-system/scripts/restore.sh /var/backups/transcribe

# 3. Restorasi berkas spesifik dari Cloud Rclone
/root/audio-transcribe-system/scripts/restore.sh gdrive:backups/transcribe transcribe_backup_20260925_150536Z.enc
```

---

## 4. Status Otomasi Systemd

```
● transcribe-cleaner.timer - Run Transcribe Cleaner Hourly
  Next Trigger: Setiap 1 jam (Tersinkronisasi)
  Unit Service: transcribe-cleaner.service

● transcribe-backup.timer - Run Transcribe Offsite Backup Daily at 02:00 UTC
  Next Trigger: Harian 02:00:00 UTC
  Unit Service: transcribe-backup.service
```

---

## 5. Bukti Pengujian Live

1. **Pengujian Backup Lokal:**
   `Local backup written and verified at /var/backups/transcribe/transcribe_backup_20260925_150536Z.enc. Encrypted size: 1.2M, SHA256: 1490182df50addf8...`
2. **Pengujian Restore Lokal:**
   `Downloaded latest .enc. Checksum verified: OK. Decrypted AES-256. Database restored: 350 meetings restored successfully.`
3. **Pengujian Backup Cloud (Google Drive via Rclone):**
   `Uploaded encrypted backup to gdrive:backups/transcribe. Remote backup verified on Google Drive successfully.`
