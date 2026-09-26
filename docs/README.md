# Technical Documentation Index
**Transcribe Core — Enterprise Real-Time Speech Intelligence Stack**

[![Release Version](https://img.shields.io/badge/Release-v2.1.0%20(Build%2042)-38BDF8?style=flat-square)](https://gate.eclipsegate.my.id/downloads/transcribe-core.apk)
[![Security Vault](https://img.shields.io/badge/Security-Android%20Keystore%20AES--256--GCM-10B981?style=flat-square)]()
[![Go Backend](https://img.shields.io/badge/Go-1.22%2B-00ADD8?style=flat-square&logo=go)](https://go.dev/)
[![Android Client](https://img.shields.io/badge/Android-Kotlin%202.0%20%7C%20Jetpack%20Compose-3DDC84?style=flat-square&logo=android)](https://developer.android.com/jetpack/compose)

---

> 🌐 **Language:** **English** | [Versi Bahasa Indonesia](README_ID.md)

---

## Technical Overview

This directory contains the comprehensive technical specifications, engineering blueprints, database architectures, market analyses, and operational runbooks for the **Transcribe Core** platform. Each document details a core subsystem of the distributed heterogeneous speech-to-intelligence ecosystem.

---

## Documentation Index

| Document | Technical Scope & Topics |
| :--- | :--- |
| **[`BLUEPRINT.md`](./BLUEPRINT.md)** | **System Architecture Blueprint:** Detailed Go 1.22+ backend specs, adaptive audio streaming, isolated FIFO queue, heterogeneous model routing, and Android Keystore vault. |
| **[`DATABASE_DESIGN.md`](./DATABASE_DESIGN.md)** | **Production Database Design:** PostgreSQL 16 & SQLite 3 schemas, GIN Full-Text Search tsvector index, composite pagination indexes, and anti-IDOR tenant isolation. |
| **[`FRONTEND_ARCHITECTURE.md`](./FRONTEND_ARCHITECTURE.md)** | **Android Client Architecture:** Jetpack Compose MVI pattern, visual defect mitigations, FSM Scroll Anchor, 60 FPS canvas isolation, and AudioRecord Foreground Service. |
| **[`UI_DESIGN.md`](./UI_DESIGN.md)** | **Industrial UI Design Standards:** Monochromatic dark theme (`#0A0D12`), WCAG AAA contrast, safe-area protection, OEM emoji elimination, and zero-collision layouts. |
| **[`AUTO_QUESTION_SUGGESTION_ENGINE.md`](./AUTO_QUESTION_SUGGESTION_ENGINE.md)** | **In-Meeting Inquiry Engine:** Live sliding window analysis, zero-hallucination temperature 0.2 guardrails, verbatim `context_ref` quotes, and $\ge 35$ word threshold. |
| **[`BYOK_ARCHITECTURE_AND_MARKET_ANALYSIS.md`](./BYOK_ARCHITECTURE_AND_MARKET_ANALYSIS.md)** | **BYOK Architecture & Market Analysis:** AI rate limits benchmark (Groq LPU Llama 3.3 70B & Whisper Turbo, Google Gemini 2.0 Flash, OpenAI), competitor comparison, and zero-knowledge vault. |
| **[`MIGRATION_GUIDE.md`](./MIGRATION_GUIDE.md)** | **Cloud-Agnostic Migration Guide:** SOP for cross-server migrations, encrypted AES-256-CBC snapshots, multi-stage Docker builds, and Docker Compose orchestration. |
| **[`DISASTER_RECOVERY.md`](./DISASTER_RECOVERY.md)** | **Disaster Recovery Runbook:** Scheduled daily backups (02:00 UTC), PBKDF2/AES-256 encryption, SHA-256 verification, and multi-target delivery (Local mounts, Remote SSH, Rclone Multi-Cloud). |

---

### Additional References
* **[`../README.md`](../README.md)** — Main Project README & API Specifications (English).
* **[`../test/README.md`](../test/README.md)** — Automated QA Test Harness & AI Evaluation Guide.
