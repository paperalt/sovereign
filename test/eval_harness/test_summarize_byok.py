#!/usr/bin/env python3
"""
Test Summarize for each BYOK AI Provider via 9Router:
1. Groq: groq/openai/gpt-oss-120b & groq/llama-3.3-70b-versatile
2. Google Gemini: gemini/gemini-3.8-flash & gemini/gemini-3.5-flash-lite
3. OpenAI: openai/gpt-4o-mini & openai/gpt-4.1-mini
"""

import os
import sys
import json
import time
import urllib.request
import urllib.error

# Load environment configuration
ENV_FILE = "/root/.transcribe_env"
env_vars = {}
if os.path.exists(ENV_FILE):
    with open(ENV_FILE) as f:
        for line in f:
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                k, v = line.split("=", 1)
                env_vars[k.strip()] = v.strip()

API_KEY = env_vars.get("NINEROUTER_KEY")
BASE_URL = env_vars.get("NINEROUTER_URL", "http://127.0.0.1:20128/v1")
ENDPOINT = f"{BASE_URL.rstrip('/')}/chat/completions"

if not API_KEY:
    print("[FATAL] NINEROUTER_KEY not found in /root/.transcribe_env")
    sys.exit(1)

# Realistic Meeting Transcript in Indonesian
SAMPLE_TRANSCRIPT = (
    "Rapat evaluasi sprint mingguan dimulai pukul sembilan pagi. "
    "Budi melaporkan bahwa optimasi database PostgreSQL 16 dengan GIN index berhasil "
    "mempercepat full-text search transkrip dari 250 milidetik menjadi hanya 8 milidetik. "
    "Namun, Rina mencatat adanya kendala pada koneksi WebSocket saat pengguna berpindah jaringan "
    "dari Wi-Fi ke data seluler, di mana reconnection sering mengalami timeout. "
    "Andi menyampaikan bahwa fitur Bring Your Own Key untuk Groq Whisper dan GPT-OSS 120B "
    "sudah selesai diuji dan mampu memotong beban komputasi server hingga nol persen. "
    "Keputusan rapat menyepakati tiga aksi tindak lanjut: "
    "Pertama, Rina ditugaskan memasang exponential backoff pada WebSocket handler Android sebelum hari Rabu. "
    "Kedua, Budi diminta menyiapkan benchmark stress test konkuren 100 pengguna aktif sebelum hari Kamis. "
    "Ketiga, Andi harus menyelesaikan integrasi multi-storage token vault di Android Keystore pada hari Jumat."
)

SYSTEM_PROMPT = (
    "You are an elite executive intelligence analyst and academic synthesizer. "
    "Extract an exhaustive, complete, and structured summary from the meeting transcript in Indonesian. "
    "CRITICAL NEGATIVE CONSTRAINT: Strictly NEVER include introductory filler or preambles such as "
    "'Berikut adalah ringkasan...', 'Berikut ini rangkuman...', 'Tentu, ini adalah...', or any conversational opening. "
    "The value of 'executive_summary' MUST start immediately with substantive content. "
    "TOTAL COVERAGE: Capture every discussion point, metric, challenge, and decision from the transcript without omission. "
    "Output strictly valid JSON matching this schema:\n"
    "{\n"
    "  \"executive_summary\": \"Direct in-depth executive summary without any preamble (minimal 40 kata)\",\n"
    "  \"key_points\": [\"Comprehensive point 1\", \"Comprehensive point 2\", \"Comprehensive point 3\"],\n"
    "  \"action_items\": [\n"
    "    {\"task\": \"Deskripsi tugas\", \"assignee\": \"Nama orang\", \"status\": \"PENDING\"}\n"
    "  ]\n"
    "}\n"
    "Do not wrap in markdown code fences. Output raw JSON only."
)

USER_PROMPT = f"--- TRANSKRIP RAPAT ---\n{SAMPLE_TRANSCRIPT}\n--- AKHIR TRANSKRIP ---\n\nBuat ringkasan terstruktur dalam bahasa Indonesia:"

def call_summarize(model_id):
    payload = {
        "model": model_id,
        "stream": False,
        "temperature": 0.2,
        "messages": [
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": USER_PROMPT}
        ]
    }
    req = urllib.request.Request(
        ENDPOINT,
        data=json.dumps(payload).encode("utf-8"),
        headers={
            "Authorization": f"Bearer {API_KEY}",
            "Content-Type": "application/json"
        }
    )
    t0 = time.time()
    try:
        with urllib.request.urlopen(req, timeout=40) as resp:
            elapsed = time.time() - t0
            raw_body = resp.read().decode("utf-8")
            data = json.loads(raw_body)
            msg = data["choices"][0]["message"]["content"].strip()
            usage = data.get("usage", {})
            return True, elapsed, msg, usage, ""
    except Exception as e:
        elapsed = time.time() - t0
        return False, elapsed, "", {}, str(e)

def main():
    print("=" * 70)
    print("  EVALUASI KOMPARATIF SUMMARIZE TIAP ENDPOINT BYOK (VIA 9ROUTER)")
    print("=" * 70)

    test_models = [
        {"provider": "GROQ (GPT-OSS)", "model": "groq/openai/gpt-oss-120b"},
        {"provider": "GROQ (Llama 3.3)", "model": "groq/llama-3.3-70b-versatile"},
        {"provider": "GEMINI (Flash 3.8)", "model": "gemini/gemini-3.8-flash"},
        {"provider": "GEMINI (Lite 3.5)", "model": "gemini/gemini-3.5-flash-lite"},
        {"provider": "OPENAI (4o-mini)", "model": "openai/gpt-4o-mini"},
        {"provider": "OPENAI (4.1-mini)", "model": "openai/gpt-4.1-mini"}
    ]

    results = []

    for item in test_models:
        prov = item["provider"]
        m = item["model"]
        print(f"\n[*] Menguji Provider: {prov} -> Model: '{m}'")
        ok, elapsed, content, usage, err = call_summarize(m)
        
        if not ok:
            print(f"    [-] GAGAL: {err} ({elapsed:.2f}s)")
            results.append({
                "provider": prov,
                "model": m,
                "status": "FAIL",
                "elapsed": elapsed,
                "error": err
            })
            continue

        # Clean markdown code fences if model output wrapped in ```json
        cleaned = content
        if cleaned.startswith("```json"):
            cleaned = cleaned[7:].strip()
        if cleaned.startswith("```"):
            cleaned = cleaned[3:].strip()
        if cleaned.endswith("```"):
            cleaned = cleaned[:-3].strip()

        # Parse JSON
        valid_json = False
        summary_text = ""
        key_points = []
        action_items = []
        try:
            parsed = json.loads(cleaned)
            summary_text = parsed.get("executive_summary", "")
            key_points = parsed.get("key_points", [])
            action_items = parsed.get("action_items", [])
            valid_json = bool(summary_text and isinstance(key_points, list) and isinstance(action_items, list))
        except Exception as pe:
            print(f"    [-] JSON Parse Error: {pe}")

        print(f"    [+] Status           : {'BERHASIL (JSON Valid 100%)' if valid_json else 'INVALID JSON'}")
        print(f"    [+] Latensi Eksekusi : {elapsed:.2f} detik")
        if usage:
            print(f"    [+] Penggunaan Token : {usage.get('total_tokens', '-')} token (Prompt: {usage.get('prompt_tokens', '-')}, Comp: {usage.get('completion_tokens', '-')})")
        print(f"    [+] Ringkasan        : {summary_text[:90]}...")
        print(f"    [+] Poin Utama       : {len(key_points)} poin")
        print(f"    [+] Action Items     : {len(action_items)} tugas:")
        for act in action_items:
            print(f"        - {act.get('task')} (Assignee: {act.get('assignee')}, Status: {act.get('status')})")

        results.append({
            "provider": prov,
            "model": m,
            "status": "PASS" if valid_json else "INVALID_JSON",
            "elapsed": elapsed,
            "tokens": usage.get("total_tokens", 0),
            "action_items_count": len(action_items),
            "summary_sample": summary_text
        })

    print("\n" + "=" * 70)
    print("                    RINGKASAN HASIL EVALUASI")
    print("=" * 70)
    print(f"{'PROVIDER':<22} | {'MODEL':<32} | {'STATUS':<8} | {'LATENSI':<8}")
    print("-" * 70)
    for r in results:
        status_text = r["status"]
        print(f"{r['provider']:<22} | {r['model']:<32} | {status_text:<8} | {r['elapsed']:.2f}s")
    print("=" * 70)

if __name__ == "__main__":
    main()
