# Sistem Saran Pertanyaan Otomatis (Auto Question Suggestion Engine)
**Rancang Bangun Mesin Rekomendasi Pertanyaan Rapat Kontekstual Berbasis Transkripsi Nyata**  
*Mitigasi Halusinasi, Filter Substantif Konteks, dan Rujukan Kutipan Verbatim*

[![Release Version](https://img.shields.io/badge/Release-v2.1.0%20(Build%2042)-38BDF8?style=flat-square)](https://gate.eclipsegate.my.id/downloads/transcribe-core.apk)

---

> 🌐 **Bahasa:** **Bahasa Indonesia** | [English Version](AUTO_QUESTION_SUGGESTION_ENGINE.md)

---

## 1. Latar Belakang & Analisis Masalah

Dalam skenario perkuliahan, seminar akademik, maupun rapat korporat/teknis, terdapat kesenjangan (*gap*) psikologis dan kognitif:
1. **Beban Kognitif Tinggi:** Peserta fokus mendengarkan dan mencatat, sehingga sulit merumuskan pertanyaan yang tajam secara instan saat sesi tanya jawab (Q&A) dibuka mendadak.
2. **Keterbatasan Waktu:** Menunggu akhir sesi (1–2 jam) sering kali membuat konteks materi di awal terlupakan.
3. **Kebutuhan Analisis Rentang Waktu (Windowing):** Materi bahasan sering berpindah topik. Pertanyaan yang relevan bisa berasal dari pembahasan 5 menit terakhir (spesifik rumus/tahap) atau 30 menit terakhir (satu bab atau modul konsep utuh).

---

## 2. Analisis Teknis: Kapasitas Konteks & Token

* **Kecepatan Pembicaraan:** Rata-rata 130–160 kata/menit (Bahasa Indonesia).
* **Volume 30 Menit Pembicaraan:**
  * Estimasi kata: $3.900 - 4.800\text{ kata}$.
  * Estimasi token teks: $\approx 5.500 - 7.200\text{ token}$.
* **Kapasitas Gemini 3.8 Flash:**
  * Context Window: $1.000.000+\text{ token}$.
  * Utilisasi $7.200\text{ token}$ hanya mengonsumsi **< 0.8%** kapasitas model.
  * Latensi komputasi teks murni (bukan audio): **$\approx 1,2 - 2,0\text{ detik}$**.
* **Kesimpulan:** Menelan seluruh 30 menit konteks rapat tidak menimbulkan masalah memori (*out of context*) maupun pembengkakan latensi.

---

## 3. Mitigasi Halusinasi (Anti-Hallucination Guardrails)

Pertanyaan AI berisiko menjadi "halu" jika model mengarang konsep di luar apa yang dibicarakan pemateri (*parametric knowledge bleed*) atau terbawa kesalahan dengar ASR. Untuk mengeliminasi risiko halusinasi mendekati 0%:

1. **Strict Context Grounding (Temperature = 0.2):**
   * Mengunci model pada mode deterministik presisi tinggi.
   * Model secara tegas dilarang mengasumsikan fakta di luar transkrip yang diberikan.
2. **Wajib Bukti Kutipan Asli (*Mandatory Context Quote / Grounding*):**
   * Setiap pertanyaan yang dihasilkan **wajib menyertakan kutipan/referensi kalimat asli** dari transkrip yang mendasarinya (`context_ref`).
3. **Klasifikasi Kategori Berbobot (Bukan Pertanyaan Trivia Dangkal):**
   * `clarification`: Klarifikasi konsep atau asumsi yang disinggung tapi belum tuntas.
   * `critical_edge_case`: Pengujian skenario ekstrem atau batasan metode (*stress-test*).
   * `practical_impact`: Dampak penerapan nyata, efisiensi, regulasi, atau biaya.
4. **Insufficiency Guardrail (Ambang Batas Minimum Konteks):**
   * Jika segmen audio yang dipilih hanya berisi basa-basi, jeda hening, atau kurang dari 35 kata padat materi, engine menolak menghasilkan pertanyaan palsu dan mengembalikan status informasi:
     `"Materi pada rentang waktu ini belum cukup padat untuk merumuskan pertanyaan spesifik."`

---

## 4. Rancang Bangun Modular (Architecture Design)

### A. Modular Time-Windowing
Engine mendukung pemilihan rentang waktu fleksibel melalui parameter `window_minutes`:
* `0` : Seluruh sesi dari awal hingga detik terakhir (*Full Session*).
* `5` : 5 Menit terakhir (konsep atau slide baru).
* `10` : 10 Menit terakhir.
* `15` : 15 Menit terakhir.
* `30` : 30 Menit terakhir (evaluasi bab/modul menyeluruh).

### B. Kontrak Data Respons (JSON Schema)
```json
{
  "meeting_id": "cf09116b-1f99-4641-a682-993bf5310dda",
  "window_minutes": 15,
  "analyzed_duration_sec": 900.0,
  "word_count": 2150,
  "has_sufficient_context": true,
  "message": "Berhasil merumuskan 3 saran pertanyaan relevan.",
  "suggestions": [
    {
      "id": "q1",
      "question": "Bagaimana strategi mitigasi race condition pada pengiriman token jika terjadi fluktuasi jaringan seluler?",
      "category": "critical_edge_case",
      "context_ref": "Pembicara menyebutkan bahwa token di-refresh secara background setiap 15 menit tanpa blocking UI.",
      "thought_starter": "Tanyakan saat pemateri membahas arsitektur sinkronisasi jaringan."
    }
  ]
}
```
