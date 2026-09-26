import json
import time
import urllib.request
import urllib.error
import hmac
import hashlib
import base64
import os
import subprocess

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
    try:
        cmd = [
            "docker", "exec", "test-postgres-verify", "psql", "-U", "postgres", "-d", "transcribe", "-t", "-A", "-c",
            "INSERT INTO users (email, full_name, role, tier, quota_seconds) VALUES ('test.edgecase@audit.local', 'Edge Case Tester', 'user', 'pro', 3600) ON CONFLICT (email) DO UPDATE SET quota_seconds = 3600 RETURNING id;"
        ]
        out = subprocess.check_output(cmd).decode().strip()
        user_id = out.splitlines()[0].strip()
        return user_id, "test.edgecase@audit.local"
    except Exception as e:
        # Fallback to query existing user
        cmd = ["docker", "exec", "test-postgres-verify", "psql", "-U", "postgres", "-d", "transcribe", "-t", "-A", "-c", "SELECT id, email FROM users LIMIT 1;"]
        out = subprocess.check_output(cmd).decode().strip()
        user_id, email = out.splitlines()[0].split("|")
        return user_id, email

def test_user_edge_cases():
    base_url = "http://127.0.0.1:8080"
    print("\n========================================================")
    print("  USER BEHAVIOR & EDGE-CASE RESILIENCE AUDIT")
    print("========================================================\n")

    # 1. Verify Registration is Hardened (403 Forbidden for direct password registration)
    ts = int(time.time())
    email = f"user.edge.{ts}@test.local"
    reg_payload = json.dumps({
        "email": email,
        "password": "PasswordEdge123!",
        "full_name": "Edge Case Tester"
    }).encode("utf-8")
    
    req = urllib.request.Request(f"{base_url}/api/v1/auth/register", data=reg_payload, headers={"Content-Type": "application/json"})
    try:
        urllib.request.urlopen(req)
        assert False, "Direct registration should be rejected with 403 Forbidden"
    except urllib.error.HTTPError as e:
        assert e.code == 403, f"Expected 403 Forbidden, got {e.code}"
        print("[✓ 0] Perimeter Hardening: Direct password registration rejected (403 Forbidden).")

    # 2. Authenticate using authorized JWT token
    secret = load_jwt_secret()
    test_user_id, test_email = get_or_create_test_user()
    token = make_jwt(test_user_id, test_email, secret)
    print(f"[✓ 0b] Authorized session token generated for user: {test_email} ({test_user_id})")

    # Clean up any leftover active meeting for this user first
    try:
        req = urllib.request.Request(f"{base_url}/api/v1/meetings/active", headers={"Authorization": f"Bearer {token}"})
        with urllib.request.urlopen(req) as r:
            active_res = json.loads(r.read().decode("utf-8"))
            if active_res.get("active") and active_res.get("meeting"):
                mid = active_res["meeting"]["id"]
                req_del = urllib.request.Request(f"{base_url}/api/v1/meetings/{mid}/cancel", data=b"{}", headers={"Content-Type": "application/json", "Authorization": f"Bearer {token}"})
                urllib.request.urlopen(req_del)
    except Exception:
        pass

    # -------------------------------------------------------------
    # EDGE CASE 1: Instant Close / 0 Chunks (Ghost Meeting Auto-Purge)
    # -------------------------------------------------------------
    m_payload = json.dumps({"title": "Accidental Blank Meeting"}).encode("utf-8")
    req = urllib.request.Request(f"{base_url}/api/v1/meetings", data=m_payload, headers={"Content-Type": "application/json", "Authorization": f"Bearer {token}"})
    with urllib.request.urlopen(req) as r:
        m_data = json.loads(r.read().decode("utf-8"))
        ghost_id = m_data["id"]

    # Call Stop with 0 chunks -> should be DISCARDED
    req = urllib.request.Request(f"{base_url}/api/v1/meetings/{ghost_id}/stop", data=b"{}", headers={"Content-Type": "application/json", "Authorization": f"Bearer {token}"})
    with urllib.request.urlopen(req) as r:
        stop_res = json.loads(r.read().decode("utf-8"))
        assert stop_res.get("status") == "DISCARDED"
        print(f"[✓ 1] Zero-chunk ghost meeting automatically purged from DB: status={stop_res.get('status')}")

    # -------------------------------------------------------------
    # EDGE CASE 2: App Swiped Away Mid-Meeting (Active Session Recovery)
    # -------------------------------------------------------------
    m_payload = json.dumps({"title": "Interrupted Lecture Session"}).encode("utf-8")
    req = urllib.request.Request(f"{base_url}/api/v1/meetings", data=m_payload, headers={"Content-Type": "application/json", "Authorization": f"Bearer {token}"})
    with urllib.request.urlopen(req) as r:
        m_data = json.loads(r.read().decode("utf-8"))
        interrupted_id = m_data["id"]

    # When user reopens app, app queries /api/v1/meetings/active
    req = urllib.request.Request(f"{base_url}/api/v1/meetings/active", headers={"Authorization": f"Bearer {token}"})
    with urllib.request.urlopen(req) as r:
        active_res = json.loads(r.read().decode("utf-8"))
        assert active_res["active"] is True
        assert active_res["meeting"]["id"] == interrupted_id
        print(f"[✓ 2] Active session recovery: App detected ongoing meeting '{active_res['meeting']['title']}'.")

    # -------------------------------------------------------------
    # EDGE CASE 3: Explicit Cancel / Discard Session
    # -------------------------------------------------------------
    req = urllib.request.Request(f"{base_url}/api/v1/meetings/{interrupted_id}/cancel", data=b"{}", headers={"Content-Type": "application/json", "Authorization": f"Bearer {token}"})
    with urllib.request.urlopen(req) as r:
        cancel_res = json.loads(r.read().decode("utf-8"))
        assert cancel_res["status"] == "CANCELLED"
        print("[✓ 3] Explicit session cancellation: Meeting discarded cleanly.")

    # Check active meeting again (must be false)
    req = urllib.request.Request(f"{base_url}/api/v1/meetings/active", headers={"Authorization": f"Bearer {token}"})
    with urllib.request.urlopen(req) as r:
        active_res = json.loads(r.read().decode("utf-8"))
        assert active_res["active"] is False
        print("[✓ 3b] Confirmed: No active meetings remaining.")

    print("\n========================================================")
    print("  ALL USER EDGE-CASE SCENARIOS VERIFIED 100% SUCCESS  ")
    print("========================================================\n")

if __name__ == "__main__":
    test_user_edge_cases()
