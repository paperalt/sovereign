# Panduan & Bahan Penulisan Paper Ilmiah CIASTECH 2026
**The 9th Conference on Innovation and Application of Science and Technology (CIASTECH 2026)**  
*Topik: Sistem Transkripsi Audio Real-Time Heterogen Silang-Model dengan Pipa Streaming Adaptif & Rekomendasi Pertanyaan Rapat*

[![Release Version](https://img.shields.io/badge/Release-v2.1.0%20(Build%2042)-38BDF8?style=flat-square)](https://gate.eclipsegate.my.id/downloads/transcribe-core.apk)
[![Prefix Bidang](https://img.shields.io/badge/Prefix-RTR%20(Riset%20Teknologi%20%26%20Rekayasa)-10B981?style=flat-square)]()
[![Target Halaman](https://img.shields.io/badge/Target-5%20s.d.%2010%20Halaman-blue?style=flat-square)]()

---

## 1. Matriks Kesiapan Ketentuan Penulisan Paper

| Bagian Dokumen | Ketentuan Template CIASTECH 2026 | Status | Detail Kesiapan / Hal yang Perlu Dilengkapi |
| :--- | :--- | :---: | :--- |
| **Format Halaman** | Ukuran Letter (21,59 x 27,94 cm), Margin: Atas 3cm, Bawah 3cm, Kiri 2,5cm, Kanan 2,5cm. Single space. | **READY** | Dikonfigurasi di template LaTeX (`paper/ciastech_paper.tex`) dan Word (`template_ciastech_2026.docx`). |
| **Panjang Naskah** | Minimal 5 halaman, maksimal 10 halaman. | **READY** | Data teknis, arsitektur, rumus, dan hasil benchmark mencukupi untuk 6–8 halaman padat. |
| **Similarity Rate** | Maksimal 25% similarity index. | **READY** | Seluruh uraian arsitektur dan analisis ditulis orisinal berbasis data proyek sendiri. |
| **Judul Naskah** | Font Cambria 14pt, Bold, Center, UPPERCASE, maksimal 15 kata. | **DRAFT** | **Usulan Judul (13 Kata):**<br/>`SISTEM TRANSKRIPSI AUDIO REAL-TIME HETEROGEN DENGAN PIPA ADAPTIF DAN REKOMENDASI PERTANYAAN RAPAT` |
| **Identitas Penulis** | Penulis 1, Penulis 2, Afiliasi, Program Studi, Fakultas, Kampus, Email Korespondensi. | **ACTION** | Penulis utama: Asmaul Khusna (STI ITB Yadika Pasuruan). Perlu konfirmasi nama dosen pembimbing / co-author. |
| **Abstrak & Keywords**| Bahasa Indonesia, 150–200 kata, 1 paragraf, Cambria 10pt spasi 1.0, maks 5 kata kunci, tanpa sitasi. | **DRAFT** | Draft abstrak telah dirancang memuat urgensi, metode, data hasil pengujian, dan kesimpulan. |
| **Bab 1: Pendahuluan** | Urgensi, kebaruan (*novelty*), batasan masalah, dan tujuan penelitian. | **READY** | Komparasi terhadap keterbatasan SaaS konvensional ($17–$18/bln) dan kebaruan pipa heterogen & BYOK. |
| **Bab 2: Metode** | Rancangan rekayasa, alur pipa audio, formula matematis equation, diagram topologi. | **READY** | Formula RMS (1), Formula RTF (2), Formula Efisiensi Bandwidth (3), dan ERD basis data. |
| **Bab 3: Hasil** | Tabel benchmark kuantitatif, analisis latensi, efisiensi data seluler, dan evaluasi AI. | **READY** | Data empiris ASR WER 0.0%, RTF 0.03x–0.42x, efisiensi supresi hening 78.2%, dan DB time 0.040ms. |
| **Bab 4: Kesimpulan** | Fakta capaian, jawaban rumusan masalah, dan saran pengembangan (tanpa referensi). | **READY** | Merangkum keberhasilan integrasi cross-model, penghematan bandwidth, dan stabilitas latensi. |
| **Bab 5: Referensi** | Format IEEE Numbering `[1]`, `[2]`, min. 12 pustaka, min. 80% primer 5 tahun terakhir (2021–2026). | **READY** | Disusun 12 rujukan jurnal/prosiding primer IEEE/ArXiv (Whisper, WebSockets, VAD, LLM In-context). |

---

## 2. Kompilasi Data Empiris & Bahan Pengujian (Benchmark Evidence)

Seluruh angka di bawah ini ditarik langsung dari artifak pengujian riil sistem (`test/eval_harness/` dan `test/benchmarks/`):

### A. Uji Akurasi & Latensi Speech-to-Text (ASR Evaluation Suite)
*Metrik diuji menggunakan dataset audio terstandar berdurasi bervariasi dengan bahasa Indonesia & istilah teknis:*

| ID Uji | Karakteristik Audio | Durasi Audio | Latensi Inferensi | Real-Time Factor (RTF) | Word Error Rate (WER) | Karakter Error Rate (CER) | Status |
| :---: | :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| **ASR-01** | Kuliah Akademik Formal | 7,20 s | 3,75 s | 0,521x | 0,0% | 0,0% | **PASS** |
| **ASR-02** | Istilah Teknis (PostgreSQL, AES-256) | 9,20 s | 4,95 s | 0,538x | 0,0% | 0,0% | **PASS** |
| **ASR-03** | Angka, Tanggal, & Timestamp | 6,80 s | 4,01 s | 0,590x | 0,0% | 0,0% | **PASS** |
| **ASR-04** | Code-Switching (Indo-English Tech) | 7,30 s | 3,41 s | 0,467x | 0,0% | 0,0% | **PASS** |
| **ASR-05** | Audio Hening & Noise Latar | 3,00 s | 0,002 s (2 ms) | 0,00067x | 0,0% | 0,0% | **PASS** |
| **RATAAN** | **Kombinasi Seluruh Kasus** | **6,70 s** | **3,22 s** | **0,423x** | **0,0%** | **0,0%** | **100% OK** |

### B. Tolok Ukur Latensi Inferensi Silang-Model (Cross-Model Hardware Benchmarks)

| Mesin / Provider AI | Peran Komputasi | Model yang Digunakan | Rata-Rata Latensi | Karakteristik Biaya & Kuota |
| :--- | :--- | :--- | :---: | :--- |
| **Groq Cloud LPU** | Audio Ingestion (STT) | `whisper-large-v3-turbo` | **0,30 detik (RTF 0,03x)** | Gratis 8 jam audio/hari (Bebas Kuota Server) |
| **Groq Cloud LPU** | Reasoning & Summary | `llama-3.3-70b-versatile` | **1,80 detik** | Penalaran ultra cepat pada hardware LPU |
| **Google Gemini Studio** | Audio Ingestion (STT) | `gemini-2.0-flash` (Multimodal) | **1,50 – 2,80 detik** | Konteks raksasa 1 juta token |
| **Server Managed** | Audio & Nalar Bawaan | `ag/gemini-3.8-flash` | **1,50 – 3,20 detik** | Terkelola server dengan kuota voucher |
| **OpenAI Platform** | Audio Ingestion (STT) | `whisper-1` | **1,80 – 2,80 detik** | Presisi korporat standar OpenAI |
| **OpenAI Platform** | Reasoning & Summary | `gpt-4o-mini` | **2,10 – 3,00 detik** | Nalar terstruktur ekstraksi action items |

### C. Efisiensi Bandwidth Jaringan (Pipa Standar vs. Pipa Adaptif)

| Parameter Streaming | Pipa Standar (Lossless PCM) | Pipa Adaptif (Client VAD Gated) | Selisih / Efisiensi Penghematan |
| :--- | :---: | :---: | :---: |
| **Laju Bit Rata-Rata** | 256 kbps (konstan) | 55,6 kbps (dinamis ter-gate) | **Turun 78,2%** |
| **Konsumsi Kuota per Jam** | ~115,2 MB / jam | ~25,1 MB / jam | **Hemat ~90,1 MB per jam** |
| **Ukuran Paket Transmisi** | 2048 bytes (64 ms) | 4096 bytes (128 ms) | **Frekuensi I/O Syscall turun 50%** |
| **Penanganan Keheningan** | Terus streaming audio kosong | Supresi hening (< 280 RMS) + Keepalive 4s | Mencegah halusinasi model AI |
| **Proteksi Fonem Awal** | Mengikuti jeda server | Pre-roll ring buffer 256 ms (4 frame) | Fonem awal 100% terjaga |

### D. Benchmark Throughput & Efisiensi Basis Data (PostgreSQL 16)
*Berdasarkan profiling query `EXPLAIN (ANALYZE, BUFFERS)` pada data beban riil:*

| Kueri & Operasi Basis Data | Volume Dataset Uji | Waktu Eksekusi | Penggunaan Buffer Memori |
| :--- | :---: | :---: | :---: |
| **Paginasi Daftar Pertemuan (Limit 20)** | 500 baris pertemuan | **0,040 ms** | 4 hit shared buffers (100% in-memory) |
| **Pengambilan Transkrip Penuh (Ordered Chunks)** | 2.000 chunks (~5 jam rekaman) | **1,354 ms** | 107 hit shared buffers, sort memory 314 kB |
| **Full-Text Search Vektor GIN (`tsvector`)** | 2.000 chunks audio | **0,757 ms** | 107 hit shared buffers (Indeks GIN sederhana) |

---

## 3. Toolchain Penulisan Ilmiah yang Tersedia

Sistem telah dilengkapi dengan 4 engine compiler akademik:

1. **LaTeX Engine (`pdflatex` - TeX Live 2025):**
   * Lokasi: `/usr/bin/pdflatex`
   * Mampu mengompilasi naskah format prosiding/jurnal secara langsung menjadi PDF siap cetak.
2. **Pandoc Converter (`pandoc 3.7.0.2`):**
   * Lokasi: `/usr/bin/pandoc`
   * Mampu mengonversi naskah Markdown/LaTeX langsung menjadi berkas Word `.docx` yang mengikuti style dari template CIASTECH:
     ```bash
     pandoc input.md --reference-doc=paper/template_ciastech_2026.docx -o output.docx
     ```
3. **Typst Engine (`typst 0.15.1`):**
   * Lokasi: `/usr/local/bin/typst`
   * Alternatif modern untuk LaTeX dengan kecepatan kompilasi sub-detik dan dukungan tabel/persamaan presisi tinggi.
4. **Python-Docx Automation (Python 3.14 + `python-docx 1.2.0`):**
   * Interpreter: `/root/.academic_env/bin/python`
   * Pustaka: `python-docx`
   * Mampu memanipulasi, menyisipkan tabel, mengatur margin, serta menyusun paragraf langsung ke dalam file template `.docx`.

---

## 4. Alur Kerja Kompilasi Naskah (`paper/build_paper.sh`)

Naskah artikel dapat ditulis dalam format LaTeX (`paper/ciastech_paper.tex`) atau Markdown (`paper/paper_content.md`), kemudian diekspor secara simultan ke:
- `paper/output/paper_ciastech_2026.pdf` (via `pdflatex`)
- `paper/output/paper_ciastech_2026.docx` (via `pandoc` / `python-docx` berbasis `template_ciastech_2026.docx`)
