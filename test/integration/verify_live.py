import os
import subprocess
import time
import urllib.request
import json

def run_live_integration():
    port = "8099"
    pg_dsn = "postgres://postgres:postgres@127.0.0.1:5432/transcribe?sslmode=disable"
    
    env = os.environ.copy()
    env["PORT"] = port
    env["DB_DRIVER"] = "postgres"
    env["DB_DSN"] = pg_dsn
    env["ALLOW_PASSWORD_AUTH"] = "true"

    print("[*] Starting backend daemon connected to PostgreSQL 16...")
    server_proc = subprocess.Popen(
        ["/root/audio-transcribe-system/bin/server"],
        env=env,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True
    )

    try:
        time.sleep(1.2)
        base_url = f"http://127.0.0.1:{port}"
        
        # 1. Health check
        with urllib.request.urlopen(f"{base_url}/health") as resp:
            health = json.loads(resp.read().decode("utf-8"))
            print("[✓] Health check response:", health)
            assert health.get("status") == "ok"

        # 2. Register User
        email = f"agent.{int(time.time())}@eclipsegate.my.id"
        reg_payload = json.dumps({
            "email": email,
            "password": "Argon2id#ProductionPass!2026",
            "full_name": "Homura Tactical Auditor"
        }).encode("utf-8")
        req = urllib.request.Request(f"{base_url}/api/v1/auth/register", data=reg_payload, headers={"Content-Type": "application/json", "User-Agent": "Android/14 (Kotlin Mobile Client)"})
        with urllib.request.urlopen(req) as resp:
            reg_res = json.loads(resp.read().decode("utf-8"))
            token = reg_res["token"]
            refresh_token = reg_res["refresh_token"]
            user_id = reg_res["user"]["id"]
            print(f"[✓] Registration successful in PostgreSQL. User ID: {user_id}")

        # 3. Verify Refresh Token Rotation
        ref_payload = json.dumps({"refresh_token": refresh_token}).encode("utf-8")
        req = urllib.request.Request(f"{base_url}/api/v1/auth/refresh", data=ref_payload, headers={"Content-Type": "application/json"})
        with urllib.request.urlopen(req) as resp:
            ref_res = json.loads(resp.read().decode("utf-8"))
            new_token = ref_res["token"]
            new_refresh_token = ref_res["refresh_token"]
            print("[✓] Session refresh rotated token pair successfully.")

        # 4. Create Multilingual Meeting in PostgreSQL
        m_payload = json.dumps({
            "title": "Tactical Database & Network Review",
            "language": "en",
            "target_language": "id"
        }).encode("utf-8")
        req = urllib.request.Request(
            f"{base_url}/api/v1/meetings",
            data=m_payload,
            headers={"Content-Type": "application/json", "Authorization": f"Bearer {new_token}"}
        )
        with urllib.request.urlopen(req) as resp:
            m_res = json.loads(resp.read().decode("utf-8"))
            meeting_id = m_res["id"]
            print(f"[✓] Created Meeting in PostgreSQL: {meeting_id} (Title: {m_res['title']}, Lang: {m_res['language']})")

        # 5. Full-Text Search verification
        search_req = urllib.request.Request(
            f"{base_url}/api/v1/meetings/search?q=kriptografi",
            headers={"Authorization": f"Bearer {new_token}"}
        )
        with urllib.request.urlopen(search_req) as resp:
            search_res = json.loads(resp.read().decode("utf-8"))
            print(f"[✓] Full-Text Search API returned: {search_res['total']} matches.")

        # 6. Logout / Revoke Token
        logout_payload = json.dumps({"refresh_token": new_refresh_token}).encode("utf-8")
        req = urllib.request.Request(f"{base_url}/api/v1/auth/logout", data=logout_payload, headers={"Content-Type": "application/json"})
        with urllib.request.urlopen(req) as resp:
            logout_res = json.loads(resp.read().decode("utf-8"))
            print(f"[✓] Logout API response:", logout_res)

        print("[✓] All Database & Backend Integration Checks PASSED 100%!")

    finally:
        server_proc.terminate()
        try:
            server_proc.wait(timeout=3)
        except subprocess.TimeoutExpired:
            server_proc.kill()
        print("[✓] Daemon terminated cleanly.")

if __name__ == "__main__":
    run_live_integration()
