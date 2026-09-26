#!/usr/bin/env python3
import asyncio
import json
import time
import math
import struct
import urllib.request
import urllib.error
import hmac
import hashlib
import base64
import os
import subprocess
import websockets

def load_jwt_secret():
    secret = os.environ.get("JWT_SECRET", "")
    if not secret and os.path.exists("/root/.transcribe_env"):
        with open("/root/.transcribe_env") as f:
            for line in f:
                if line.startswith("JWT_SECRET="):
                    secret = line.strip().split("=", 1)[1]
    return secret or "production-transcribe-jwt-secret-key-32bytes!!"

def make_jwt(user_id: str, email: str, secret: str) -> str:
    h = base64.urlsafe_b64encode(json.dumps({"alg": "HS256", "typ": "JWT"}).encode()).rstrip(b'=').decode()
    p = base64.urlsafe_b64encode(json.dumps({
        "user_id": user_id,
        "email": email,
        "role": "user",
        "type": "access",
        "exp": int(time.time()) + 3600,
        "iat": int(time.time())
    }).encode()).rstrip(b'=').decode()
    sig = hmac.new(secret.encode(), f"{h}.{p}".encode(), hashlib.sha256).digest()
    s = base64.urlsafe_b64encode(sig).rstrip(b'=').decode()
    return f"{h}.{p}.{s}"

def get_or_create_test_user():
    cmd = [
        "docker", "exec", "test-postgres-verify", "psql", "-U", "postgres", "-d", "transcribe", "-t", "-A", "-c",
        "INSERT INTO users (email, full_name, role, tier, quota_seconds) VALUES ('beta.auditor@eclipsegate.local', 'Beta Pipeline Auditor', 'user', 'pro', 7200) ON CONFLICT (email) DO UPDATE SET quota_seconds = 7200 RETURNING id;"
    ]
    out = subprocess.check_output(cmd).decode().strip()
    user_id = out.splitlines()[0].strip()
    return user_id, "beta.auditor@eclipsegate.local"

def generate_pcm_sine(duration_sec=3.0, freq=440.0, sample_rate=16000, amplitude=12000):
    total_samples = int(duration_sec * sample_rate)
    pcm = bytearray()
    for i in range(total_samples):
        sample = int(amplitude * math.sin(2.0 * math.pi * freq * (i / sample_rate)))
        sample = max(-32768, min(32767, sample))
        pcm.extend(struct.pack('<h', sample))
    return bytes(pcm)

async def test_adaptive_beta():
    print("========================================================")
    print("  ADAPTIVE STREAMING BETA PIPELINE END-TO-END AUDIT   ")
    print("========================================================")

    secret = load_jwt_secret()
    user_id, email = get_or_create_test_user()
    token = make_jwt(user_id, email, secret)
    print(f"[✓ 1] Auth Token generated for {email} (User ID: {user_id})")

    # 1. Create a meeting via REST API
    base_url = "http://127.0.0.1:8080"
    create_req = urllib.request.Request(
        f"{base_url}/api/v1/meetings",
        data=json.dumps({"title": "Automated Beta Adaptive Stress Test", "language": "id"}).encode("utf-8")
    )
    create_req.add_header("Content-Type", "application/json")
    create_req.add_header("Authorization", f"Bearer {token}")

    with urllib.request.urlopen(create_req) as r:
        meeting_data = json.loads(r.read().decode("utf-8"))
        meeting_id = meeting_data["id"]
        print(f"[✓ 2] Meeting created via REST: ID={meeting_id}")

    # 2. Connect to WebSocket with pipeline_mode=adaptive_beta
    ws_url = f"ws://127.0.0.1:8080/ws/transcribe?token={token}&meeting_id={meeting_id}&pipeline_mode=adaptive_beta"
    print(f"[→ 3] Connecting to WebSocket: pipeline_mode=adaptive_beta...")

    async with websockets.connect(ws_url) as ws:
        # Step A: Expect PIPELINE_MODE handshake
        msg1_raw = await asyncio.wait_for(ws.recv(), timeout=5.0)
        msg1 = json.loads(msg1_raw)
        assert msg1.get("event") == "PIPELINE_MODE", f"Expected PIPELINE_MODE, got {msg1}"
        assert msg1.get("status") == "ADAPTIVE_BETA", f"Expected ADAPTIVE_BETA, got {msg1}"
        print(f"[✓ 4] Received PIPELINE_MODE Handshake: {msg1.get('message')}")

        # Step B: Expect initial STATUS CONNECTED
        msg2_raw = await asyncio.wait_for(ws.recv(), timeout=5.0)
        msg2 = json.loads(msg2_raw)
        assert msg2.get("event") == "STATUS", f"Expected STATUS, got {msg2}"
        assert msg2.get("status") == "CONNECTED", f"Expected CONNECTED, got {msg2}"
        print(f"[✓ 5] Ingestion pipeline CONNECTED: {msg2.get('message')}")

        # Step C: Stream 4096-byte batched audio frames (Client Frame Compaction)
        pcm_audio = generate_pcm_sine(duration_sec=3.0, freq=440.0, amplitude=14000)
        batch_size = 4096 # 128ms per packet
        print(f"[→ 6] Streaming {len(pcm_audio)} bytes PCM in {batch_size}-byte compacted frames...")

        for offset in range(0, len(pcm_audio), batch_size):
            frame = pcm_audio[offset:offset+batch_size]
            await ws.send(frame)
            await asyncio.sleep(0.04)

        # Step D: Client VAD Gating Simulation (Silence & Keepalive)
        print(f"[→ 7] Simulating VAD silence period & sending VAD_SILENCE keepalive frame...")
        await ws.send(json.dumps({"action": "VAD_SILENCE"}))
        
        # Expect PONG
        pong_raw = await asyncio.wait_for(ws.recv(), timeout=5.0)
        pong = json.loads(pong_raw)
        assert pong.get("event") == "PONG", f"Expected PONG, got {pong}"
        print(f"[✓ 8] VAD_SILENCE keepalive acknowledged: PONG received")

        # Step E: Test PAUSE command
        print(f"[→ 9] Testing PAUSE action...")
        await ws.send(json.dumps({"action": "PAUSE"}))
        pause_ack_raw = await asyncio.wait_for(ws.recv(), timeout=5.0)
        pause_ack = json.loads(pause_ack_raw)
        assert pause_ack.get("status") == "PAUSED", f"Expected PAUSED, got {pause_ack}"
        print(f"[✓ 10] Audio stream paused on server: {pause_ack.get('message')}")

        # Step F: Test RESUME command
        print(f"[→ 11] Testing RESUME action...")
        await ws.send(json.dumps({"action": "RESUME"}))
        resume_ack_raw = await asyncio.wait_for(ws.recv(), timeout=5.0)
        resume_ack = json.loads(resume_ack_raw)
        assert resume_ack.get("status") == "STREAMING", f"Expected STREAMING, got {resume_ack}"
        print(f"[✓ 12] Audio stream resumed on server: {resume_ack.get('message')}")

        # Step G: Send STOP command to finalize meeting
        print(f"[→ 13] Sending STOP action to finalize meeting...")
        await ws.send(json.dumps({"action": "STOP"}))

        # Collect events until COMPLETED
        finalized = False
        start_wait = time.time()
        while time.time() - start_wait < 30.0:
            try:
                evt_raw = await asyncio.wait_for(ws.recv(), timeout=5.0)
                evt = json.loads(evt_raw)
                print(f"    [WS Event] {evt.get('event')}: {evt.get('status', evt.get('message', ''))}")
                if evt.get("status") == "COMPLETED":
                    finalized = True
                    break
            except asyncio.TimeoutError:
                break

        assert finalized, "Meeting was not finalized to COMPLETED status!"
        print(f"[✓ 14] Meeting successfully finalized to COMPLETED")

    # 3. Verify database state via REST
    verify_req = urllib.request.Request(f"{base_url}/api/v1/meetings/{meeting_id}")
    verify_req.add_header("Authorization", f"Bearer {token}")
    with urllib.request.urlopen(verify_req) as r:
        final_meeting = json.loads(r.read().decode("utf-8"))
        assert final_meeting["status"] == "COMPLETED", f"Expected COMPLETED, got {final_meeting['status']}"
        print(f"[✓ 15] Database verification passed: Status={final_meeting['status']}, Duration={final_meeting.get('duration_sec')}s")

    # 4. Clean up test meeting
    del_req = urllib.request.Request(f"{base_url}/api/v1/meetings/{meeting_id}", method="DELETE")
    del_req.add_header("Authorization", f"Bearer {token}")
    with urllib.request.urlopen(del_req) as r:
        assert r.status == 200
        print(f"[✓ 16] Test meeting cleanly purged")

    print("\n========================================================")
    print("  ALL 16 BETA PIPELINE AUDIT STEPS PASSED (100% STABLE) ")
    print("========================================================\n")

if __name__ == "__main__":
    asyncio.run(test_adaptive_beta())
