# Technical Documentation Index
**Sovereign — Standalone On-Device Android Speech Intelligence**

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg?style=flat-square)](../LICENSE)
[![Security Vault](https://img.shields.io/badge/Security-Android%20Keystore%20AES--256--GCM-10B981?style=flat-square)]()
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B%20%28API%2026%2B%29-3DDC84?style=flat-square&logo=android)](https://developer.android.com/)
[![Android Client](https://img.shields.io/badge/Kotlin-2.0%20%7C%20Jetpack%20Compose-7F52FF?style=flat-square&logo=kotlin)](https://developer.android.com/jetpack/compose)

---

> 🌐 **Language:** **English** | [Versi Bahasa Indonesia](README_ID.md)

---

## Technical Overview

This directory contains the architecture specifications, design systems, and engineering analyses for the **Sovereign** mobile platform. The application is completely standalone, running 100% on-device without any centralized backend server or intermediate proxy.

---

## Documentation Index

| Document | Technical Scope & Topics |
| :--- | :--- |
| **[`FRONTEND_ARCHITECTURE.md`](./FRONTEND_ARCHITECTURE.md)** | **Android Client Architecture:** Jetpack Compose MVI pattern, native SQLite 3 persistence, FSM Scroll Anchor, 60 FPS canvas waveform, and AudioRecord Foreground Service. |
| **[`UI_DESIGN.md`](./UI_DESIGN.md)** | **Industrial UI Design Standards:** Monochromatic dark theme (`#0A0D12`), WCAG AAA contrast, safe-area protection, OEM emoji elimination, and responsive segmented controls. |
| **[`AUTO_QUESTION_SUGGESTION_ENGINE.md`](./AUTO_QUESTION_SUGGESTION_ENGINE.md)** | **In-Meeting Inquiry Engine:** Live sliding window analysis, zero-hallucination temperature 0.2 guardrails, verbatim `context_ref` quotes, and $\ge 35$ word threshold. |
| **[`BYOK_ARCHITECTURE_AND_MARKET_ANALYSIS.md`](./BYOK_ARCHITECTURE_AND_MARKET_ANALYSIS.md)** | **BYOK Architecture & Market Analysis:** AI rate limits benchmark (Groq LPU Llama 3.3 70B & Whisper Turbo, Google Gemini 2.0 Flash, OpenAI), competitor comparison, and zero-knowledge vault. |
| **[`MIGRATION_GUIDE.md`](./MIGRATION_GUIDE.md)** | **Architecture Evolution Guide:** Architectural journey from server-managed pipeline to 100% on-device standalone deployment. |

---

### Additional References
* **[`../README.md`](../README.md)** — Main Project Overview & Architecture Guide (English).
* **[`../README_ID.md`](../README_ID.md)** — Dokumentasi Utama Proyek (Bahasa Indonesia).
