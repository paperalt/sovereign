# Panduan Migrasi Cepat & Arsitektur Tahan Bencana (Migration-Resilient Stack)

Dokumen ini adalah SOP (*Standard Operating Procedure*) untuk memindahkan seluruh sistem **Transcribe Core Backend** (Go, Database, Konfigurasi, dan Aset APK) ke server atau penyedia VPS baru dalam waktu **< 3 menit** dengan **zero-data-loss** dan **biaya minimal**.

---

## 1. Prinsip Desain Tahan Migrasi (Portability Principles)

1. **Cloud-Agnostic & Containerized:**
   * Stack dikemas dalam Docker Compose v2 (`docker-compose.yml`) menggunakan multi-stage build Alpine minimalis (~15.5 MB).
   * Berjalan identik di penyedia VPS mana pun (DigitalOcean, Hetzner, AWS, GCP, Linode, Tencent Cloud, atau Server Pribadi/Homelab).
2. **Dual Database Engine (Fleksibilitas Biaya Server):**
   * **Mode Produksi Standar (PostgreSQL 16):** Cocok untuk server $\ge$ 2 GB RAM dengan kebutuhan multi-user dan full-text search intensif (`DB_DRIVER=postgres`).
   * **Mode Hemat Ekstrem (Embedded SQLite):** Jika biaya server membengkak dan Anda ingin pindah ke VPS murah $2–$4/bulan (512 MB – 1 GB RAM), backend dapat berjalan 100% menggunakan embedded SQLite murni (`modernc.org/sqlite` tanpa CGO) dengan konsumsi RAM < 35 MB (`DB_DRIVER=sqlite`).
3. **Decoupled Configuration (`.env`):**
   * Semua parameter runtime (DB, JWT, Google OAuth, 9Router, Port) diatur melalui satu berkas `.env`.
   * Tidak ada hardcoded IP atau kredensial di dalam biner aplikasi.
4. **Enkripsi Snapshot AES-256-CBC:**
   * Setiap proses ekspor menghasilkan satu berkas tunggal terenkripsi militer (*PBKDF2 100.000 iterasi*). Kredensial dan data pengguna aman dipindahkan melalui jaringan publik.
5. **Agilitas Klien Android (Tanpa Recompile APK):**
   * Aplikasi Android telah dilengkapi **In-App Server Config Dialog** (ikon gerigi di layar masuk). Jika IP atau domain server berpindah, pengguna cukup mengganti URL di aplikasi tanpa perlu mengompilasi ulang APK.

---

## 2. Prosedur Ekspor dari Server Lama (Server A)

Jalankan perintah ekspor 1-baris pada server sumber:

```bash
cd /root/audio-transcribe-system
./scripts/docker_migrate_export.sh
```

**Hasil Ekspor:**
* Berkas terenkripsi: `/root/audio-transcribe-system/migrations/transcribe_stack_<TIMESTAMP>.migration.enc`
* Kunci pembuka: `/root/.transcribe_backup.key`
* Berkas memuat: *Dump Database PostgreSQL/SQLite, berkas `.env`, manifest sistem, dan seluruh aset `/var/www/downloads/` (APK & Zip)*.

---

## 3. Prosedur Impor ke Server Baru (Server B)

### Langkah 1: Siapkan Server Baru (1-Click Bootstrap)
Pada VPS baru (Ubuntu/Debian bersih), jalankan:

```bash
# Clone atau salin repositori project ke /opt/audio-transcribe-system
git clone https://github.com/paperalt/sovereign.git /opt/audio-transcribe-system
cd /opt/audio-transcribe-system

# Jalankan bootstrap (otomatis pasang Docker & Compose)
chmod +x ./scripts/*.sh
./scripts/bootstrap_node.sh
```

### Langkah 2: Transfer Bundel Migrasi & Kunci
Kirimkan berkas migrasi dari Server A ke Server B:

```bash
# Contoh via SCP dari laptop / server:
scp user@server-lama:/root/audio-transcribe-system/migrations/*.migration.enc /opt/audio-transcribe-system/
scp user@server-lama:/root/.transcribe_backup.key /root/.transcribe_backup.key
```

### Langkah 3: Eksekusi Restorasi Otomatis
Jalankan perintah restorasi:

```bash
cd /opt/audio-transcribe-system
./scripts/docker_migrate_restore.sh transcribe_stack_<TIMESTAMP>.migration.enc /root/.transcribe_backup.key
```

**Proses Otomatis yang Berjalan:**
1. Mendekripsi bundel dengan kunci AES-256.
2. Memulihkan berkas `.env` dan aset download ke sistem.
3. Membangun dan menyalakan container Docker (`transcribe-backend` & `transcribe-postgres`).
4. Mengimpor seluruh data pengguna, token, rapat, dan transkripsi ke PostgreSQL baru.
5. Menjalankan verifikasi kesehatan (`GET /health`) dan menampilkan jumlah record data.

---

## 4. Opsi Penekanan Biaya Server (Skema Hemat SQLite)

Jika Anda ingin memotong biaya operasional server hingga seminimal mungkin:

1. **Ubah Konfigurasi `.env`:**
   ```bash
   DB_DRIVER=sqlite
   SQLITE_PATH=/data/transcribe.db
   ```
2. **Nyalakan Stack Khusus SQLite:**
   ```bash
   docker compose -f docker-compose.sqlite.yml up -d --build
   ```
   *Keuntungan:* Tidak memerlukan container PostgreSQL sama sekali. Penggunaan CPU mendekati 0% saat idle, dan penggunaan memori RAM stabil di bawah 40 MB.

---

## 5. Cutover Domain & SSL di Server Baru

Jika memindahkan domain `gate.eclipsegate.my.id`:
1. Ubah **DNS A Record** domain di Cloudflare / DNS Provider ke IP server baru.
2. Pasang Nginx & Certbot di server baru:
   ```bash
   apt-get install -y nginx certbot python3-certbot-nginx
   certbot --nginx -d gate.eclipsegate.my.id --non-interactive --agree-tos -m admin@eclipsegate.my.id
   ```
3. Arahkan reverse proxy Nginx ke port `8080` (lokal).
4. Selesai. Seluruh client Android otomatis terhubung kembali ke backend baru tanpa gangguan.
