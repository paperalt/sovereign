# Test Suites & Quality Assurance Harness
**Transcribe Core — Verification, Integration & Adversarial Test Suites**

---

## 1. Struktur Direktori Pengujian

```
test/
├── eval_harness/               # Evaluasi AI Kuantitatif (WER/CER, RTF, Grounding, JSON Schema)
│   ├── dataset/                # Audio acuan uji (akademik, teknikal, angka, hening)
│   ├── reports/                # Laporan evaluasi historis (JSON)
│   ├── generate_dataset.sh     # Generator dataset audio via edge-tts & ffmpeg
│   ├── runner.py               # Automated AI evaluation runner
│   └── test_summarize_byok.py  # Benchmark ringkasan AI per-provider via 9Router
├── integration/                # Pengujian Integrasi Sistem & Skenario Edge-Case
│   ├── audit_full_system.py    # Audit keamanan 19-poin (IDOR, token tampering, multi-tenant)
│   ├── test_user_edge_cases.py # Pengujian konkurensi, ghost sessions & crash recovery
│   ├── verify_android_auth_contract.py # Verifikasi kontrak payload Google OIDC
│   └── verify_live.py          # Verifikasi live ingestion WebSocket & transkripsi
└── benchmarks/                 # Pengujian Beban & Optimasi Database
    ├── benchmark_db.sql        # Pengukuran query plan EXPLAIN ANALYZE
    ├── load_test.sql           # Skrip simulasi konkurensi tinggi PostgreSQL
    └── verify_schema.sql       # Verifikasi integritas tabel & foreign keys
```

---

## 2. Panduan Menjalankan Pengujian

### A. Unit Tests (Go Backend)
Menjalankan seluruh unit test internal Go:
```bash
go test -v ./...
```
*Mencakup paket: `internal/audio`, `internal/database`, `internal/handler`, `internal/repository`, `internal/service`, `pkg/token`.*

### B. Automated AI Evaluation Harness
Mengukur akurasi transkripsi ASR dan validasi grounding:
```bash
python3 test/eval_harness/runner.py
```
*Metrik:*
* Word Error Rate (WER) & Character Error Rate (CER) via Levenshtein distance ($\le 5\%$).
* Real-Time Factor (RTF) ($< 0.3$).
* Keselarasan grounding kutipan materi nyata ($\ge 90\%$).
* Kepatuhan format JSON eksekutif (100%).

### C. Benchmark Ringkasan AI Multi-Provider
Menguji latensi dan ketajaman nalar model ringkasan:
```bash
python3 test/eval_harness/test_summarize_byok.py
```

### D. Audit Keamanan & Stress Testing
```bash
# Audit keamanan perimeter, IDOR, dan token
python3 test/integration/audit_full_system.py

# Pengujian skenario kegagalan pengguna
python3 test/integration/test_user_edge_cases.py

# Adversarial load & stress testing
go run cmd/stress_test/main.go
```
