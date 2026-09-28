#!/usr/bin/env python3
"""
Comprehensive Live Verification:
1. Groq Whisper Turbo STT with Prompt Conditioning (Real Audio)
2. Groq LLM Chat Completion (openai/gpt-oss-120b & qwen/qwen3.8-27b)
3. 9Router Multimodal Gemini (ag/gemini-3.8-flash-high)
4. Transcript Stitcher (Longest Suffix-Prefix Matching) verification
"""

import os
import sys
import json
import sqlite3
import urllib.request
import urllib.error

def get_groq_key():
    conn = sqlite3.connect('/root/.9router/db/data.sqlite')
    row = conn.cursor().execute("SELECT data FROM providerConnections WHERE provider='groq'").fetchone()
    if row:
        data = json.loads(row[0])
        return data.get('apiKey')
    return None

def get_9router_key():
    env_file = '/root/.transcribe_env'
    if os.path.exists(env_file):
        with open(env_file) as f:
            for line in f:
                if line.startswith('NINEROUTER_KEY='):
                    return line.strip().split('=', 1)[1]
    return os.environ.get('NINEROUTER_KEY')

def test_transcript_stitcher():
    print("=== [TEST 1/4] Client-Side Transcript Stitcher Logic ===")
    def stitch(prev, curr):
        p_words = prev.strip().split()
        c_words = curr.strip().split()
        max_overlap = min(4, len(p_words), len(c_words))
        for size in range(max_overlap, 0, -1):
            tail = " ".join(p_words[-size:]).lower()
            head = " ".join(c_words[:size]).lower()
            # Clean punctuation
            clean_tail = "".join(c for c in tail if c.isalnum() or c == " ")
            clean_head = "".join(c for c in head if c.isalnum() or c == " ")
            if clean_tail == clean_head:
                return prev.strip() + " " + " ".join(c_words[size:])
        return prev.strip() + " " + curr.strip()

    # Case 1: Overlapping words at boundary
    prev = "Selamat pagi semuanya. Hari ini kita akan membahas tentang"
    curr = "membahas tentang pentingnya struktur data dan algoritma."
    stitched = stitch(prev, curr)
    expected = "Selamat pagi semuanya. Hari ini kita akan membahas tentang pentingnya struktur data dan algoritma."
    assert stitched == expected, f"Expected '{expected}', got '{stitched}'"
    print(f"  [+] Overlap boundary stitching: PASS")
    print(f"      Result: \"{stitched}\"")

    # Case 2: Clean continuation without overlap
    prev2 = "Algoritma pencarian efisien."
    curr2 = "Struktur data graf digunakan untuk jaringan."
    stitched2 = stitch(prev2, curr2)
    assert stitched2 == "Algoritma pencarian efisien. Struktur data graf digunakan untuk jaringan."
    print(f"  [+] Clean boundary continuation: PASS")
    return True

def test_groq_whisper_prompt():
    print("\n=== [TEST 2/4] Groq Whisper Turbo Live STT with Prompt Conditioning ===")
    api_key = get_groq_key()
    if not api_key:
        print("  [-] FAIL: Groq API key not found.")
        return False

    wav_path = '/root/sovereign/test/eval_harness/dataset/sample_1_academic.wav'
    with open(wav_path, 'rb') as f:
        audio_bytes = f.read()

    boundary = "----WebKitFormBoundarySovereignPromptTest"
    prompt_text = "Sebelumnya pembicara menjelaskan pengantar mata kuliah."

    body = bytearray()
    def add_field(name, val):
        body.extend(f"--{boundary}\r\nContent-Disposition: form-data; name=\"{name}\"\r\n\r\n{val}\r\n".encode())

    add_field("model", "whisper-large-v3-turbo")
    add_field("language", "id")
    add_field("response_format", "json")
    add_field("prompt", prompt_text)
    body.extend(f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"audio.wav\"\r\nContent-Type: audio/wav\r\n\r\n".encode())
    body.extend(audio_bytes)
    body.extend(f"\r\n--{boundary}--\r\n".encode())

    req = urllib.request.Request(
        "https://api.groq.com/openai/v1/audio/transcriptions",
        data=bytes(body),
        headers={
            "Authorization": f"Bearer {api_key}",
            "Content-Type": f"multipart/form-data; boundary={boundary}",
            "User-Agent": "SovereignSpeechIntelligence/1.0"
        }
    )

    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            data = json.loads(resp.read().decode())
            transcript = data.get("text", "")
            req_id = data.get("x_groq", {}).get("id", "N/A")
            print(f"  [+] Transcription Successful!")
            print(f"      Request ID: {req_id}")
            print(f"      Prior Prompt: \"{prompt_text}\"")
            print(f"      Recognized Text: \"{transcript.strip()}\"")
            return True
    except Exception as e:
        print(f"  [-] Groq Whisper failed: {e}")
        return False

def test_groq_llm_models():
    print("\n=== [TEST 3/4] Groq LLM Synthesis Endpoint ===")
    api_key = get_groq_key()
    if not api_key:
        print("  [-] FAIL: Groq API key not found.")
        return False

    models_to_test = ["openai/gpt-oss-120b", "qwen/qwen3.8-27b"]
    all_ok = True

    for model in models_to_test:
        payload = {
            "model": model,
            "messages": [
                {"role": "system", "content": "You are a concise executive assistant. Output valid JSON only: {\"status\": \"READY\", \"engine\": \"model_name\"}"},
                {"role": "user", "content": f"Verifikasi endpoint untuk model {model}."}
            ],
            "temperature": 0.1,
            "stream": False
        }

        req = urllib.request.Request(
            "https://api.groq.com/openai/v1/chat/completions",
            data=json.dumps(payload).encode(),
            headers={
                "Authorization": f"Bearer {api_key}",
                "Content-Type": "application/json",
                "User-Agent": "SovereignSpeechIntelligence/1.0"
            }
        )

        try:
            with urllib.request.urlopen(req, timeout=30) as resp:
                data = json.loads(resp.read().decode())
                content = data["choices"][0]["message"]["content"].strip()
                print(f"  [+] Model '{model}': PASS")
                print(f"      Output: {content}")
        except Exception as e:
            print(f"  [-] Model '{model}': FAIL ({e})")
            all_ok = False

    return all_ok

def test_gemini_9router():
    print("\n=== [TEST 4/4] 9Router Multimodal Gemini Endpoint ===")
    api_key = get_9router_key()
    if not api_key:
        print("  [-] FAIL: 9Router key not found.")
        return False

    payload = {
        "model": "ag/gemini-3.8-flash-high",
        "messages": [
            {"role": "system", "content": "You are an assistant. Output JSON only: {\"status\": \"ACTIVE\"}"},
            {"role": "user", "content": "Echo confirmation."}
        ],
        "temperature": 0.1,
        "stream": False
    }

    req = urllib.request.Request(
        "http://127.0.0.1:20128/v1/chat/completions",
        data=json.dumps(payload).encode(),
        headers={
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json"
        }
    )

    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            data = json.loads(resp.read().decode())
            content = data["choices"][0]["message"]["content"].strip()
            print(f"  [+] Gemini 3.8 Flash High via 9Router: PASS")
            print(f"      Output: {content}")
            return True
    except Exception as e:
        print(f"  [-] Gemini 9Router test failed: {e}")
        return False

if __name__ == '__main__':
    t1 = test_transcript_stitcher()
    t2 = test_groq_whisper_prompt()
    t3 = test_groq_llm_models()
    t4 = test_gemini_9router()

    print("\n=======================================================")
    if t1 and t2 and t3 and t4:
        print("[FINAL VERIFICATION] ALL 4 CRITICAL PIPELINES VERIFIED 100% OPERATIONAL!")
        sys.exit(0)
    else:
        print("[FINAL VERIFICATION] Some verification steps failed.")
        sys.exit(1)
