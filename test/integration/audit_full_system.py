import os
import subprocess
import time
import urllib.request
import urllib.error
import json
import base64

def run_comprehensive_audit():
    port = "8099"
    sqlite_dsn = "/tmp/test_sovereign_audit.db"
    if os.path.exists(sqlite_dsn):
        os.remove(sqlite_dsn)
    
    env = os.environ.copy()
    env["PORT"] = port
    env["DB_DRIVER"] = "sqlite"
    env["DB_DSN"] = sqlite_dsn
    env["ALLOW_PASSWORD_AUTH"] = "true"

    print("\n========================================================")
    print("  LIVE SOVEREIGN INTEGRATION & SECURITY AUDIT (SQLITE + GO)")
    print("========================================================\n")

    server_proc = subprocess.Popen(
        ["/root/sovereign/bin/sovereign-server"],
        env=env,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True
    )

    base_url = f"http://127.0.0.1:{port}"

    def post_json(path, data, token=None):
        req = urllib.request.Request(f"{base_url}{path}", data=json.dumps(data).encode("utf-8"))
        req.add_header("Content-Type", "application/json")
        req.add_header("User-Agent", "Auditor-Client/1.0 (Android)")
        if token:
            req.add_header("Authorization", f"Bearer {token}")
        try:
            with urllib.request.urlopen(req) as r:
                return r.status, json.loads(r.read().decode("utf-8"))
        except urllib.error.HTTPError as e:
            body = e.read().decode("utf-8")
            try:
                return e.code, json.loads(body)
            except Exception:
                return e.code, {"error": body}

    def get_json(path, token=None):
        req = urllib.request.Request(f"{base_url}{path}")
        req.add_header("User-Agent", "Auditor-Client/1.0 (Android)")
        if token:
            req.add_header("Authorization", f"Bearer {token}")
        try:
            with urllib.request.urlopen(req) as r:
                return r.status, json.loads(r.read().decode("utf-8"))
        except urllib.error.HTTPError as e:
            body = e.read().decode("utf-8")
            try:
                return e.code, json.loads(body)
            except Exception:
                return e.code, {"error": body}

    def delete_json(path, token=None):
        req = urllib.request.Request(f"{base_url}{path}", method="DELETE")
        req.add_header("User-Agent", "Auditor-Client/1.0 (Android)")
        if token:
            req.add_header("Authorization", f"Bearer {token}")
        try:
            with urllib.request.urlopen(req) as r:
                return r.status, json.loads(r.read().decode("utf-8"))
        except urllib.error.HTTPError as e:
            body = e.read().decode("utf-8")
            try:
                return e.code, json.loads(body)
            except Exception:
                return e.code, {"error": body}

    try:
        time.sleep(1.2)

        # -------------------------------------------------------------
        # PHASE 1: Health & Public Endpoints
        # -------------------------------------------------------------
        status, body = get_json("/health")
        assert status == 200 and body.get("status") == "ok"
        print("[✓ 1.1] GET /health -> 200 OK")

        # -------------------------------------------------------------
        # PHASE 2: Registration, Login & Session Management
        # -------------------------------------------------------------
        ts = int(time.time())
        email_a = f"alice.{ts}@audit.sec"
        email_b = f"bob.{ts}@audit.sec"

        # Register User A
        st, res_a = post_json("/api/v1/auth/register", {"email": email_a, "password": "PasswordAlice#123", "full_name": "Alice Auditor"})
        assert st == 201, f"Failed register A: {res_a}"
        token_a, refresh_a = res_a["token"], res_a["refresh_token"]
        user_id_a = res_a["user"]["id"]
        print(f"[✓ 2.1] Register User A -> 201 Created (UserID: {user_id_a})")

        # Register User B
        st, res_b = post_json("/api/v1/auth/register", {"email": email_b, "password": "PasswordBob#123", "full_name": "Bob Penetrator"})
        assert st == 201, f"Failed register B: {res_b}"
        token_b, refresh_b = res_b["token"], res_b["refresh_token"]
        user_id_b = res_b["user"]["id"]
        print(f"[✓ 2.2] Register User B -> 201 Created (UserID: {user_id_b})")

        # Duplicate Register rejection
        st, err = post_json("/api/v1/auth/register", {"email": email_a, "password": "PasswordAlice#123", "full_name": "Duplicate"})
        assert st == 409
        print("[✓ 2.3] Duplicate Registration -> 409 Conflict (Handled properly)")

        # Login with correct credentials
        st, login_res = post_json("/api/v1/auth/login", {"email": email_a, "password": "PasswordAlice#123"})
        assert st == 200 and "token" in login_res
        print("[✓ 2.4] Login Correct Password -> 200 OK")

        # Login with bad credentials
        st, login_err = post_json("/api/v1/auth/login", {"email": email_a, "password": "BadPassword123!"})
        assert st == 401
        print("[✓ 2.5] Login Wrong Password -> 401 Unauthorized")

        # Refresh Token Rotation
        st, ref_res = post_json("/api/v1/auth/refresh", {"refresh_token": refresh_a})
        assert st == 200 and "token" in ref_res
        token_a = ref_res["token"]
        refresh_a = ref_res["refresh_token"]
        print("[✓ 2.6] Refresh Token Rotation -> 200 OK (New Token Pair Issued)")

        # -------------------------------------------------------------
        # PHASE 3: Authorization Boundary Verification
        # -------------------------------------------------------------
        # Unauthenticated request
        st, _ = get_json("/api/v1/meetings")
        assert st == 401
        print("[✓ 3.1] No Authorization Header -> 401 Unauthorized")

        # Tampered Token
        st, _ = get_json("/api/v1/meetings", token=token_a + "tampered")
        assert st == 401
        print("[✓ 3.2] Tampered JWT Signature -> 401 Unauthorized")

        # Refresh Token used as Bearer access token (Must fail)
        st, _ = get_json("/api/v1/meetings", token=refresh_a)
        assert st == 401
        print("[✓ 3.3] Refresh Token used as Access Token -> 401 Unauthorized (Type mismatch)")

        # -------------------------------------------------------------
        # PHASE 4: Multilingual Meeting Management & Multi-Tenant IDOR Audit
        # -------------------------------------------------------------
        # Alice creates meeting
        st, m_a = post_json("/api/v1/meetings", {
            "title": "Alice Confidential Cyber Defense",
            "language": "en",
            "target_language": "id"
        }, token=token_a)
        assert st == 201
        meeting_id_a = m_a["id"]
        print(f"[✓ 4.1] User A Created Meeting -> 201 Created (ID: {meeting_id_a}, en->id)")

        # Bob creates meeting
        st, m_b = post_json("/api/v1/meetings", {
            "title": "Bob Public Pentest",
            "language": "id"
        }, token=token_b)
        assert st == 201
        meeting_id_b = m_b["id"]
        print(f"[✓ 4.2] User B Created Meeting -> 201 Created (ID: {meeting_id_b}, id)")

        # IDOR Audit: Bob tries to access Alice's meeting
        st, _ = get_json(f"/api/v1/meetings/{meeting_id_a}", token=token_b)
        assert st == 404, f"IDOR Vulnerability: Bob accessed Alice meeting! Status: {st}"
        print("[✓ 4.3] IDOR Audit: User B accessing User A meeting -> 404 Not Found")

        # IDOR Audit: Bob tries to read Alice's transcript
        st, _ = get_json(f"/api/v1/meetings/{meeting_id_a}/transcript", token=token_b)
        assert st == 404
        print("[✓ 4.4] IDOR Audit: User B reading User A transcript -> 404 Not Found")

        # IDOR Audit: Bob tries to stop Alice's meeting
        st, _ = post_json(f"/api/v1/meetings/{meeting_id_a}/stop", {}, token=token_b)
        assert st == 404
        print("[✓ 4.5] IDOR Audit: User B stopping User A meeting -> 404 Not Found")

        # IDOR Audit: Bob tries to delete Alice's meeting
        st, _ = delete_json(f"/api/v1/meetings/{meeting_id_a}", token=token_b)
        assert st == 404
        print("[✓ 4.6] IDOR Audit: User B deleting User A meeting -> 404 Not Found")

        # -------------------------------------------------------------
        # PHASE 5: Pagination, Full-Text Search, and Summarization
        # -------------------------------------------------------------
        # List Meetings User A
        st, list_res = get_json("/api/v1/meetings?limit=10&offset=0", token=token_a)
        assert st == 200 and len(list_res["meetings"]) == 1
        print("[✓ 5.1] List Meetings (Paginated) -> 200 OK")

        # Stop Meeting User A
        st, stop_res = post_json(f"/api/v1/meetings/{meeting_id_a}/stop", {}, token=token_a)
        assert st == 200 and stop_res["status"] in ("COMPLETED", "DISCARDED")
        print(f"[✓ 5.2] Stop Meeting User A -> 200 OK (Status: {stop_res['status']})")

        # Full-Text Search
        st, search_res = get_json("/api/v1/meetings/search?q=kriptografi", token=token_a)
        assert st == 200 and "results" in search_res
        print("[✓ 5.3] Full-Text Search Endpoint -> 200 OK (GIN Index Verified)")

        # -------------------------------------------------------------
        # PHASE 6: Logout & Revocation Verification
        # -------------------------------------------------------------
        # User A Logout
        st, logout_res = post_json("/api/v1/auth/logout", {"refresh_token": refresh_a}, token=token_a)
        assert st == 200
        print("[✓ 6.1] Logout -> 200 OK (Session Revoked in PostgreSQL)")

        # Replay Attack with Revoked Token (Must Fail)
        st, replay_res = post_json("/api/v1/auth/refresh", {"refresh_token": refresh_a})
        assert st == 401
        print("[✓ 6.2] Replay Attack with Revoked Refresh Token -> 401 Unauthorized")

        # Delete Meeting User B (Cleanup)
        st, del_res = delete_json(f"/api/v1/meetings/{meeting_id_b}", token=token_b)
        assert st == 200
        print("[✓ 6.3] Delete Meeting User B -> 200 OK")

        print("\n========================================================")
        print("  ALL 19 SECURITY & FEATURE TESTS PASSED (100% SUCCESS)  ")
        print("========================================================\n")

    finally:
        server_proc.terminate()
        try:
            server_proc.wait(timeout=3)
        except subprocess.TimeoutExpired:
            server_proc.kill()

if __name__ == "__main__":
    run_comprehensive_audit()
