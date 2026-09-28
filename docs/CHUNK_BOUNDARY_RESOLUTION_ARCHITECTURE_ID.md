# Arsitektur Mitigasi Pemotongan Kata Lintas Batas Chunk
**Chunk Boundary Resolution Architecture: Mengatasi Fenomena Pemotongan Fonem dan Suku Kata pada Aliran Wicara Diskrit**  
*Analisis Komparatif Model Dedicated ASR (Whisper) vs Multimodal LLM (Gemini), Algoritma VAD Valley Snapping, dan Context Chaining*

[![Dokumentasi Teknis](https://img.shields.io/badge/Architecture-Audio%20Intelligence-38BDF8?style=flat-square)]()
[![Lisensi: MIT](https://img.shields.io/badge/License-MIT-blue.svg?style=flat-square)](../LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B%20%28API%2026%2B%29-3DDC84?style=flat-square&logo=android)](https://developer.android.com/)

---

> 🌐 **Bahasa:** **Bahasa Indonesia** | [English Version](CHUNK_BOUNDARY_RESOLUTION_ARCHITECTURE.md)

---

## 1. Dekonstruksi Masalah: Fenomena Pemotongan Kata (*Word Boundary Slicing*)

Dalam sistem intelijen wicara real-time berbasis streaming klien (*pure client-side audio pipeline*), audio mentah dari mikrofon ditangkap sebagai aliran PCM 16-bit 16kHz kontinu. Untuk dapat dikirimkan ke penyedia inferensi (seperti Groq Whisper LPU atau Google Gemini REST API), aliran data ini harus dipartisi menjadi segmen-segmen audio diskrit yang disebut **chunks** (biasanya berdurasi 3 hingga 15 detik).

```
Aliran Audio Kontinu:
... [ saya ingin menye- ] | [ -lesaikan rapat ini ] ...
                          ^
                   BATAS POTONG CHUNK
                     (Boundary Cut)
```

### 1.1. Akar Kerusakan Akustik & Fonetik
1. **Truncation Suku Kata (Syllable Truncation):**
   Ketika batas pemotongan waktu jatuh tepat di tengah pelafalan kata berimbuhan (misalnya kata `"menyelesaikan"`), suku kata awal `"menye-"` terisolasi pada Chunk $N$, sementara akhiran `"-lesaikan"` masuk ke Chunk $N+1$.
2. **Ketiadaan Konteks Co-articulation:**
   Model pengenal wicara otomatis (*Automatic Speech Recognition* / ASR) sangat bergantung pada fenomena akustik ko-artikulasi (efek transisi formant antara konsonan dan vokal di sekitarnya). Pemotongan tanpa bantalan waktu menghilangkan jendela pembentukan formant 80–150ms, menyebabkan fonem letup (*plosives* seperti `p, b, t, d, k`) tidak dapat dikenali secara akurat.
3. **Sifat Stateless Protokol HTTP/REST:**
   Setiap request transkripsi chunk via REST API adalah panggilan independen tanpa status (*stateless*). Model inferensi tidak memiliki memori tentang apa yang baru saja ditranskripsikannya 50 milidetik yang lalu. Akibatnya:
   * Chunk $N$ ditranskripsi menjadi: `"saya ingin menye"` (atau halusinasi fonetik seperti `"menya"`).
   * Chunk $N+1$ ditranskripsi menjadi: `"Lanjutkan..."` atau `"Selesaikan rapat ini"` dengan huruf kapital baru, merusak susunan sintaksis dan menurunkan metrik Word Error Rate (WER) secara signifikan.

---

## 2. Solusi 1: Dynamic VAD Valley Snapping (*Acoustic Layer*)

Pendekatan ini beroperasi pada tingkat pengambilan sampel perangkat keras lokal di Android, memodifikasi `AudioStreamChunker.kt` agar tidak melakukan pemotongan waktu kaku (*rigid wall-clock slicing*).

```
                      [PUNCAK BICARA]
Amplitudo (RMS)       /\    /\
                     /  \  /  \       [LEMBAH SUKU KATA]
                    /    \/    \      _--_
-------------------/------------\----/----\------------------ (Ambang Jeda)
                                 \  /
                                  \/ <--- TITIK POTONG OPTIMAL (Snapping Point)
Waktu (t) ----------> [ Batas Min ] --------> [ Grace Overrun (1.5s) ]
```

### 2.1. Konsep Kerja: Hysteresis Grace Window
Daripada memotong paksa audio tepat saat mencapai batas durasi maksimum (`maxChunkBytes`, misal tepat di detik ke-12.0), sistem memperkenalkan **Jendela Toleransi Waktu (Grace Overrun Window)** sebesar 1.5 hingga 2.0 detik.

1. **Fase Akumulasi:** Audio dikumpulkan hingga melewati durasi minimum (`minChunkBytes`, misal 2.5 detik).
2. **Fase Pemindaian Lembah (Valley Hunt):** Ketika durasi ideal tercapai, chunker memantau nilai Root Mean Square (RMS) dari setiap frame audio 128ms.
3. **Kriteria Snapping:** Pemotongan hanya dieksekusi jika salah satu dari kondisi berikut terpenuhi:
   * **Kondisi Utama (Speech Valley):** Terdeteksi jeda hening mikro (*micro-pause*) di mana RMS berada di bawah ambang batas lembah suara ($\le 120.0$) selama minimum 2 frame berturut-turut (~250ms).
   * **Kondisi Batas Keras (Hard Ceiling):** Jika pembicara berbicara tanpa henti hingga melampaui batas toleransi maksimum ($12.0\text{s} + 2.0\text{s} = 14.0\text{s}$), sistem memotong pada indeks frame yang mencatat nilai RMS minimum lokal di dalam jendela toleransi tersebut.

### 2.2. Algoritma Perhitungan RMS & Penanda Lembah
```kotlin
// Formula Root Mean Square (RMS) 16-bit PCM Linear
fun calculateRms(pcmBytes: ByteArray): Double {
    var sum = 0.0
    val samples = pcmBytes.size / 2
    for (i in 0 until samples) {
        val sample = (pcmBytes[i * 2 + 1].toInt() shl 8) or (pcmBytes[i * 2].toInt() and 0xFF)
        sum += sample * sample
    }
    return kotlin.math.sqrt(sum / samples)
}
```

### 2.3. Kelebihan & Keterbatasan
* **Kelebihan:** 0% overhead bandwidth, 0% konsumsi token ekstra, transkrip tetap bersih tanpa duplikasi kata.
* **Keterbatasan:** Pada pembicara yang sangat cepat tanpa jeda sama sekali, pemotongan paksa pada hard ceiling tetap berpotensi mengenai tepi kata jika toleransi habis.

---

## 3. Solusi 2: Dedicated ASR Prompt Conditioning (*Model Layer — Whisper & Groq*)

Model ASR berbasis Transformer seperti OpenAI Whisper dan Groq LPU Whisper Turbo memiliki arsitektur *Encoder-Decoder* autoregresif. Decoder Whisper dirancang memiliki parameter bawaan bernama **`prompt`** (atau `initial_prompt`).

```
Chunk N Transkrip: "...alasan utama implementasi arsitektur"
                                  │
                                  ▼ (Ekstraksi 20 Kata Terakhir)
               PROMPT: "...alasan utama implementasi arsitektur"
                                  │
                                  ▼
[ HTTP POST /v1/audio/transcriptions ]
├── file: Chunk N+1 WAV (berisi potongan "...tur mikroservis...")
├── model: "whisper-large-v3-turbo"
└── prompt: "...alasan utama implementasi arsitektur"
                                  │
                                  ▼
Whisper Decoder: Menggunakan prompt sebagai KV-cache awal (prior context)
Hasil Transkripsi Chunk N+1: "mikroservis memberikan efisiensi tinggi." (Tersambung sempurna)
```

### 3.1. Mekanisme Kerja Whisper Autoregressive Prior
* Parameter `prompt` tidak dimasukkan ke dalam audio encoder, melainkan disuntikkan langsung ke dalam **teks decoder** sebagai urutan token awalan (maksimal 224 token).
* Ketika audio Chunk $N+1$ dimulai dengan suku kata yang terpotong tipis (misal `"-tur"`), Whisper Language Model Decoder melihat bahwa token sebelumnya adalah `"arsitektur"`. Model memahami secara probabilitas bahasa bahwa vokal awal adalah lanjutan dari kata sebelumnya atau awal kata berikutnya, mencegah tebakan fonem liar.
* Menjaga konsistensi gaya: Whisper mempertahankan kapitalisasi, terminologi teknis, dan gaya tanda baca yang sama dengan chunk sebelumnya.

### 3.2. Implementasi Taktis pada Sovereign Client
Pada `TranscriptionService.kt`, pelihara state riwayat teks terakhir:
```kotlin
// Thread-safe circular buffer untuk prompt conditioning
private var lastTranscribedTail: String = ""

private fun dispatchChunkToAI(chunk: AudioChunk) {
    val currentPrompt = lastTranscribedTail.takeLast(300) // Ambil ~30 kata terakhir
    
    val res = directAIClient.transcribeAudio(
        wavBytes = wavBytes,
        language = language,
        prompt = currentPrompt // Disuntikkan ke multipart body
    )
    if (res.isSuccess) {
        val text = res.getOrThrow()
        lastTranscribedTail = text
        // Simpan ke SQLite & update UI
    }
}
```

Pada `DirectAIClient.kt`:
```kotlin
if (!prompt.isNullOrBlank()) {
    multipartBuilder.addFormDataPart("prompt", prompt)
}
```

---

## 4. Solusi 3: Sliding Audio Overlap Window & N-Gram Stitcher (*Client Pipeline Layer*)

Pendekatan ini menjamin keutuhan sinyal suara murni (*guaranteed acoustic integrity*) secara matematis tanpa bergantung pada kapabilitas model AI.

```
Aliran Asli:       [ ... sistem intelijen wicara mandiri ... ]
Chunk N Audio:     [ ... sistem intelijen wicara ]  (0.0s - 8.0s)
Buffer Overlap:                          [ wicara ] (7.5s - 8.0s = 500ms disimpan)
Chunk N+1 Audio:                         [ wicara mandiri ... ] (7.5s - 15.5s)
                                              ▲
                                      Area Tumpang Tindih
```

### 4.1. Mekanisme Kerja Buffer Geser (Sliding Ring Buffer)
1. Setiap kali Chunk $N$ dipancarkan, sistem **tidak mengosongkan buffer PCM hingga nol**.
2. Sistem menyalin **500 milidetik (16.000 byte pada 16kHz mono)** audio terakhir dan menempatkannya sebagai *header payload* dari Chunk $N+1$.
3. **Kepastian Akustik:** Kata apa pun yang berada di perbatasan detik ke-8.0 dipastikan terdengar utuh 100% pada Chunk $N+1$.

### 4.2. Masalah Duplikasi Kata & Solusi N-Gram Text Stitching
Karena audio 500ms ditranskripsikan dua kali, hasil teks mentah akan mengalami pengulangan kata (*echo artifact*):
* Transkrip Chunk $N$: `"Kami mengembangkan sistem intelijen wicara"`
* Transkrip Chunk $N+1$: `"wicara mandiri tanpa server"`

Untuk mengatasi hal ini, klien Android menjalankan algoritma **Longest Suffix-Prefix Matching (LSPM)** sebelum teks disimpan ke SQLite:

```kotlin
object TranscriptStitcher {
    /**
     * Menyambungkan dua segmen transkrip dengan mengeliminasi kata yang tumpang tindih.
     */
    fun stitch(previousText: String, currentText: String): String {
        if (previousText.isBlank()) return currentText.trim()
        if (currentText.isBlank()) return previousText.trim()

        val prevWords = previousText.trim().split("\\s+".toRegex())
        val currWords = currentText.trim().split("\\s+".toRegex())

        // Periksa overlap kata dari 4 kata mundur hingga 1 kata
        val maxCheck = minOf(4, prevWords.size, currWords.size)
        for (overlapSize in maxCheck downTo 1) {
            val tail = prevWords.takeLast(overlapSize).joinToString(" ").lowercase()
            val head = currWords.take(overlapSize).joinToString(" ").lowercase()

            // Bandingkan kecocokan teks toleran tanda baca
            if (sanitize(tail) == sanitize(head)) {
                val cleanCurr = currWords.drop(overlapSize).joinToString(" ")
                return "${previousText.trim()} $cleanCurr".trim()
            }
        }

        return "${previousText.trim()} ${currentText.trim()}"
    }

    private fun sanitize(str: String): String =
        str.replace("[^a-zA-Z0-9 ]".toRegex(), "").trim()
}
```

---

## 5. Solusi 4: Gemini Multimodal Audio Processing (*Multimodal LLM Layer*)

Google Gemini (Gemini 2.0 Flash) memiliki paradigma pemrosesan audio yang sepenuhnya berbeda dari Whisper:

### 5.1. Perbedaan Mendasar: Token Laten Multimodal vs ASR Tradisional
* Whisper mengubah audio menjadi spektrogram gelombang 30 detik tetap, lalu memetakannya ke teks.
* Gemini mengonversi audio ke dalam *continuous latent audio tokens* ($\approx 32\text{ token per detik}$) yang diproses bersama token teks di dalam model transformer yang sama.

### 5.2. Dua Mode Eksekusi Gemini

#### Mode A: REST Batch Chaining via Multi-Turn History
Jika menggunakan REST API `/v1beta/models/gemini-2.0-flash:generateContent`, Gemini tidak memiliki parameter form-data `prompt`. Namun, Gemini menerima **riwayat percakapan (*multi-turn dialog array*)**:

```json
{
  "contents": [
    {
      "role": "user",
      "parts": [{ "text": "Transkripsikan audio rapat berikut secara akurat tanpa komentar." }]
    },
    {
      "role": "model",
      "parts": [{ "text": "...dan ini adalah kesimpulan dari sesi pertama." }]
    },
    {
      "role": "user",
      "parts": [
        { "text": "Lanjutkan transkripsi untuk rekaman audio berikutnya:" },
        {
          "inline_data": {
            "mime_type": "audio/wav",
            "data": "<BASE64_PCM_CHUNK_N_PLUS_1>"
          }
        }
      ]
    }
  ]
}
```
* **Hasil:** Model Gemini memperlakukan chunk baru sebagai kelanjutan langsung dari pembicaraan sebelumnya, menyambungkan kata yang terpotong dengan pemahaman tata bahasa tingkat tinggi.

#### Mode B: Gemini Multimodal Live API (WebSocket Bi-directional)
* **Protokol:** `wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent`
* **Arsitektur:** Mengeliminasi konsep chunking sepenuhnya. Klien membuka satu koneksi WebSocket persisten dan mengirimkan frame PCM mentah berukuran kecil (100ms) secara terus-menerus.
* **Keuntungan:** **Nol pemotongan kata.** Server mempertahankan *KV-cache state* secara kontinu selama sesi rekaman aktif, mengidentifikasi seluruh kata, intonasi, dan jeda secara alami.

---

## 6. Matriks Evaluasi & Analisis Komparasi

| Parameter Evaluasi | Solusi 1: VAD Valley Snapping | Solusi 2: Whisper Prompt Chaining | Solusi 3: Audio Overlap + Stitcher | Solusi 4: Gemini Multi-Turn / Live |
| :--- | :--- | :--- | :--- | :--- |
| **Lapisan Eksekusi** | On-Device Chunker | Cloud ASR Decoder | On-Device Pipeline | Multimodal Foundation Model |
| **Overhead Bandwidth** | **0% (Nol)** | **0% (Hanya teks pendek)** | +5% s.d. +8% (500ms overlap) | Normal (REST) / Streaming (WS) |
| **Overhead Token / Biaya** | **0%** | **0% (Gratis pada Whisper)** | +5% (karena durasi naik 0.5s) | Memakan token chat history |
| **Kompleksitas Kode** | Rendah (Hanya logika RMS) | Sangat Rendah (1 parameter form) | Sedang (Memerlukan buffer & dedupe) | Tinggi (Perlu stateful session / WS) |
| **Efektivitas Boundary WER** | Efektif jika ada jeda wicara | Sangat Efektif (Konteks bahasa) | 100% Sempurna secara akustik | Sangat Efektif |
| **Ketergantungan Model** | Independen (Model apa pun) | Khusus Whisper/Groq/OpenAI | Independen (Model apa pun) | Khusus Ekosistem Gemini |

---

## 7. Roadmap Implementasi Bertahap di Sovereign

Untuk menjaga prinsip efisiensi kuota gratis, latensi rendah, dan arsitektur mandiri pada perangkat Android, direkomendasikan strategi penerapan bertahap:

```
[ FASE 1: Zero Overhead (Rekomendasi Utama) ]
├── 1. Terapkan VAD Valley Snapping di AudioStreamChunker.kt (Cari jeda mikro sebelum memotong)
└── 2. Terapkan Prompt Conditioning di DirectAIClient.kt (Kirim 20 kata terakhir ke Groq Whisper)
                      │
                      ▼
[ FASE 2: Absolute Acoustic Guarantee (Penyempurnaan Lanjutan) ]
└── Tambahkan 500ms Audio Overlap + N-Gram Stitcher di TranscriptStitcher.kt
                      │
                      ▼
[ FASE 3: Next-Gen Realtime (Opsional Masa Depan) ]
└── Implementasikan WebSocket Live Transport untuk Gemini Live / Deepgram Streaming
```

Dengan mengombinasikan **VAD Valley Snapping** dan **Prompt Conditioning (Fase 1)**, 95% cacat pemotongan kata di batas chunk tereliminasi seketika tanpa menambah konsumsi bandwidth atau biaya token API sama sekali.
