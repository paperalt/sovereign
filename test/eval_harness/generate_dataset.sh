#!/usr/bin/env bash
set -e

DATASET_DIR="/root/audio-transcribe-system/test/eval_harness/dataset"
mkdir -p "$DATASET_DIR"

EDGE_TTS="/usr/local/lib/hermes-agent/venv/bin/edge-tts"

echo "[1/5] Generating Sample 1: Academic Lecture..."
TEXT_1="Selamat pagi semuanya, hari ini kita akan membahas tentang pentingnya struktur data dan algoritma dalam rekayasa perangkat lunak."
$EDGE_TTS --voice "id-ID-ArdiNeural" --text "$TEXT_1" --write-media "$DATASET_DIR/temp_s1.mp3"
ffmpeg -y -i "$DATASET_DIR/temp_s1.mp3" -ar 16000 -ac 1 -f wav "$DATASET_DIR/sample_1_academic.wav" >/dev/null 2>&1
rm -f "$DATASET_DIR/temp_s1.mp3"

echo "[2/5] Generating Sample 2: Technical Terminology..."
TEXT_2="Sistem backend menggunakan PostgreSQL enam belas dengan koneksi WebSocket dan enkripsi AES dua ratus lima puluh enam."
$EDGE_TTS --voice "id-ID-GadisNeural" --text "$TEXT_2" --write-media "$DATASET_DIR/temp_s2.mp3"
ffmpeg -y -i "$DATASET_DIR/temp_s2.mp3" -ar 16000 -ac 1 -f wav "$DATASET_DIR/sample_2_technical.wav" >/dev/null 2>&1
rm -f "$DATASET_DIR/temp_s2.mp3"

echo "[3/5] Generating Sample 3: Numbers & Dates..."
TEXT_3="Rapat koordinasi dimulai pada tanggal lima belas September pukul empat belas tiga puluh dengan total peserta dua puluh lima orang."
$EDGE_TTS --voice "id-ID-ArdiNeural" --text "$TEXT_3" --write-media "$DATASET_DIR/temp_s3.mp3"
ffmpeg -y -i "$DATASET_DIR/temp_s3.mp3" -ar 16000 -ac 1 -f wav "$DATASET_DIR/sample_3_numbers.wav" >/dev/null 2>&1
rm -f "$DATASET_DIR/temp_s3.mp3"

echo "[4/5] Generating Sample 4: Code-Switching..."
TEXT_4="Proses database migration sudah berjalan lancar dan semua unit test berhasil passing tanpa error."
$EDGE_TTS --voice "id-ID-GadisNeural" --text "$TEXT_4" --write-media "$DATASET_DIR/temp_s4.mp3"
ffmpeg -y -i "$DATASET_DIR/temp_s4.mp3" -ar 16000 -ac 1 -f wav "$DATASET_DIR/sample_4_mixed.wav" >/dev/null 2>&1
rm -f "$DATASET_DIR/temp_s4.mp3"

echo "[5/5] Generating Sample 5: Silence Rejection..."
ffmpeg -y -f lavfi -i anullsrc=r=16000:cl=mono -t 3 -f wav "$DATASET_DIR/sample_5_silence.wav" >/dev/null 2>&1

echo "Dataset generation complete!"
ls -lh "$DATASET_DIR"/*.wav
