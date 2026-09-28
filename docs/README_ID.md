# Indeks Dokumentasi Teknis
**Sovereign — Intelijen Wicara Mandiri 100% On-Device di Android**

[![Lisensi: MIT](https://img.shields.io/badge/License-MIT-blue.svg?style=flat-square)](../LICENSE)
[![Brankas Keamanan](https://img.shields.io/badge/Security-Android%20Keystore%20AES--256--GCM-10B981?style=flat-square)]()
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B%20%28API%2026%2B%29-3DDC84?style=flat-square&logo=android)](https://developer.android.com/)
[![Klien Android](https://img.shields.io/badge/Kotlin-2.0%20%7C%20Jetpack%20Compose-7F52FF?style=flat-square&logo=kotlin)](https://developer.android.com/jetpack/compose)

---

> 🌐 **Bahasa:** [English](README.md) | **Bahasa Indonesia**

---

## Ringkasan Teknis

Direktori ini memuat spesifikasi arsitektur teknis, sistem desain, dan analisis rekayasa perangkat lunak untuk aplikasi **Sovereign**. Sistem ini beroperasi 100% mandiri (*pure standalone*) langsung pada perangkat ponsel Android tanpa ketergantungan server backend atau perantara cloud apa pun.

---

## Indeks Dokumentasi

| Dokumen | Ruang Lingkup Teknis & Topik |
| :--- | :--- |
| **[`FRONTEND_ARCHITECTURE_ID.md`](./FRONTEND_ARCHITECTURE_ID.md)** | **Arsitektur Klien Android:** Pola Jetpack Compose MVI, penyimpanan native SQLite 3 lokal, FSM Scroll Anchor, visualisasi waveform 60 FPS, dan Foreground Service AudioRecord. |
| **[`UI_DESIGN.md`](./UI_DESIGN.md)** | **Standar Desain Antarmuka Industri:** Tema monokromatik gelap (`#0A0D12`), kontras WCAG AAA, proteksi safe-area, eliminasi emoji OEM, dan segmented control responsif. |
| **[`AUTO_QUESTION_SUGGESTION_ENGINE_ID.md`](./AUTO_QUESTION_SUGGESTION_ENGINE_ID.md)** | **Mesin Rekomendasi Pertanyaan Rapat:** Analisis konteks sliding window real-time, guardrail zero-hallucination temperature 0.2, kutipan verbatim `context_ref`, dan ambang batas $\ge 35$ kata. |
| **[`BYOK_ARCHITECTURE_AND_MARKET_ANALYSIS_ID.md`](./BYOK_ARCHITECTURE_AND_MARKET_ANALYSIS_ID.md)** | **Arsitektur BYOK & Analisis Pasar:** Benchmark kuota gratis (Groq LPU Llama 3.3 70B & Whisper Turbo, Google Gemini 2.0 Flash, OpenAI), perbandingan kompetitor, dan brankas zero-knowledge. |
| **[`CHUNK_BOUNDARY_RESOLUTION_ARCHITECTURE_ID.md`](./CHUNK_BOUNDARY_RESOLUTION_ARCHITECTURE_ID.md)** | **Mitigasi Pemotongan Kata Chunk:** Dynamic VAD Valley Snapping, Whisper/Groq Prompt Conditioning, Sliding Audio Overlap + N-gram Stitching, dan Gemini Multi-turn Audio Context. |
| **[`MIGRATION_GUIDE.md`](./MIGRATION_GUIDE.md)** | **Panduan Evolusi Arsitektur:** Perjalanan refaktor dari sistem server-managed menuju arsitektur 100% on-device standalone. |

---

### Referensi Tambahan
* **[`../README.md`](../README.md)** — Dokumentasi Utama Proyek (English).
* **[`../README_ID.md`](../README_ID.md)** — Dokumentasi Utama Proyek (Bahasa Indonesia).
