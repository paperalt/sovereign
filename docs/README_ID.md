# Dokumentasi Teknis Sistem (Technical Documentation)
**Transcribe Core — Ekosistem Intelijen Percakapan & Transkripsi Audio Real-Time Enterprise**

[![Release Version](https://img.shields.io/badge/Release-v2.1.0%20(Build%2042)-38BDF8?style=flat-square)](https://gate.eclipsegate.my.id/downloads/transcribe-core.apk)
[![Security Vault](https://img.shields.io/badge/Security-Android%20Keystore%20AES--256--GCM-10B981?style=flat-square)]()
[![Go Backend](https://img.shields.io/badge/Go-1.22%2B-00ADD8?style=flat-square&logo=go)](https://go.dev/)
[![Android Client](https://img.shields.io/badge/Android-Kotlin%202.0%20%7C%20Jetpack%20Compose-3DDC84?style=flat-square&logo=android)](https://developer.android.com/jetpack/compose)

---

> 🌐 **Bahasa:** **Bahasa Indonesia** | [English Version](README.md)

---

## Gambaran Umum

Direktori ini memuat seluruh spesifikasi teknis, cetak biru rekayasa, arsitektur basis data, analisis pasar global, dan panduan operasional sistem **Transcribe Core**. Setiap dokumen menguraikan subsistem inti dari ekosistem kecerdasan audio terdistribusi silang-model.

---

## Indeks Berkas Dokumentasi

| Dokumen | Topik & Cakupan Rekayasa |
| :--- | :--- |
| **[`BLUEPRINT_ID.md`](./BLUEPRINT_ID.md)** | **Cetak Biru Arsitektur Menyeluruh:** Spesifikasi backend Go 1.22+, streaming audio adaptif, antrean FIFO terisolasi, pemisahan model heterogen, dan brankas Android Keystore. |
| **[`DATABASE_DESIGN_ID.md`](./DATABASE_DESIGN_ID.md)** | **Desain Basis Data Produksi:** Skema relasional PostgreSQL 16 & SQLite 3, GIN Full-Text Search (`tsvector`), indeks paginasi, dan isolasi multi-tenant anti-IDOR. |
| **[`FRONTEND_ARCHITECTURE_ID.md`](./FRONTEND_ARCHITECTURE_ID.md)** | **Arsitektur Klien Android:** Pola MVI Jetpack Compose, mitigasi visual flicker, FSM Scroll Anchor anti-jumping, isolasi render 60 FPS, dan Foreground Service AudioRecord. |
| **[`UI_DESIGN.md`](./UI_DESIGN.md)** | **Standar Desain Antarmuka Anti-AI Slop:** Palet monokromatik gelap (`#0A0D12`), kontras WCAG AAA, safe-area padding, eliminasi emoji OEM, dan tata letak anti-tabrakan. |
| **[`AUTO_QUESTION_SUGGESTION_ENGINE_ID.md`](./AUTO_QUESTION_SUGGESTION_ENGINE_ID.md)** | **Mesin Rekomendasi Pertanyaan Rapat:** Analisis konteks materi berjalan, mitigasi halusinasi suhu 0.2, bukti kutipan asli `context_ref`, dan ambang batas kata $\ge 35$. |
| **[`BYOK_ARCHITECTURE_AND_MARKET_ANALYSIS_ID.md`](./BYOK_ARCHITECTURE_AND_MARKET_ANALYSIS_ID.md)** | **Analisis Pasar Global & Kunci Pribadi:** Evaluasi rate limit model AI (Groq LPU Llama 3.3 70B & Whisper Turbo, Google Gemini 2.0 Flash, OpenAI), komparasi kompetitor, dan arsitektur *zero-knowledge*. |
| **[`MIGRATION_GUIDE.md`](./MIGRATION_GUIDE.md)** | **Panduan Migrasi Cloud-Agnostic:** SOP pemindahan sistem antar-server/node, ekspor snapshot terenkripsi AES-256-CBC, kontainerisasi multi-stage Docker, dan orkestrasi Compose. |
| **[`DISASTER_RECOVERY_ID.md`](./DISASTER_RECOVERY_ID.md)** | **Prosedur Pemulihan Bencana:** Runbook pencadangan harian (02:00 UTC), enkripsi snapshot PBKDF2/AES-256, verifikasi SHA-256, dan dukungan multi-target (Direktori lokal, Remote SSH, Rclone Multi-Cloud). |

---

### Panduan Tambahan
* **[`../README_ID.md`](../README_ID.md)** — Dokumentasi utama proyek & spesifikasi API (Bahasa Indonesia).
* **[`../test/README.md`](../test/README.md)** — Panduan eksekusi pengujian & jaminan kualitas (QA Harness).
