-- VERIFIKASI KOMPREHENSIF SKEMA BASIS DATA

BEGIN;

-- 1. Test Users Insertion
INSERT INTO users (id, email, password_hash, full_name, role)
VALUES ('00000000-0000-0000-0000-000000000001', 'homura@mitakihara.sec', '$argon2id$v=19$dummy', 'Akemi Homura', 'admin');

-- Test Users Role Check Constraint (Must Fail)
DO $$
BEGIN
    BEGIN
        INSERT INTO users (email, password_hash, full_name, role)
        VALUES ('fail@sec.local', 'hash', 'Test', 'invalid_role');
        RAISE EXCEPTION 'Constraint chk_user_role FAILED to block invalid role!';
    EXCEPTION WHEN check_violation THEN
        RAISE NOTICE '[✓] chk_user_role successfully blocked invalid role.';
    END;
END $$;

-- Test Users Unique Email Constraint (Must Fail)
DO $$
BEGIN
    BEGIN
        INSERT INTO users (email, password_hash, full_name, role)
        VALUES ('homura@mitakihara.sec', 'hash2', 'Duplicate', 'user');
        RAISE EXCEPTION 'Constraint uq_users_email FAILED to block duplicate email!';
    EXCEPTION WHEN unique_violation THEN
        RAISE NOTICE '[✓] uq_users_email successfully blocked duplicate email.';
    END;
END $$;

-- 2. Test Refresh Token
INSERT INTO refresh_tokens (user_id, token_hash, device_info, expires_at)
VALUES ('00000000-0000-0000-0000-000000000001', 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855', 'Samsung Galaxy S24 (Android 14)', NOW() + INTERVAL '7 days');

-- 3. Test Meetings Insertion
INSERT INTO meetings (id, user_id, title, language, target_language, status)
VALUES ('11111111-1111-1111-1111-111111111111', '00000000-0000-0000-0000-000000000001', 'Rapat Arsitektur Backend', 'en', 'id', 'IN_PROGRESS');

-- Test Meeting Status Constraint (Must Fail)
DO $$
BEGIN
    BEGIN
        INSERT INTO meetings (user_id, title, status)
        VALUES ('00000000-0000-0000-0000-000000000001', 'Invalid Meeting', 'UNKNOWN_STATUS');
        RAISE EXCEPTION 'Constraint chk_meeting_status FAILED to block invalid status!';
    EXCEPTION WHEN check_violation THEN
        RAISE NOTICE '[✓] chk_meeting_status successfully blocked invalid status.';
    END;
END $$;

-- 4. Test Transcript Chunks & Full-Text Search Vector
INSERT INTO transcript_chunks (meeting_id, chunk_index, start_time_sec, end_time_sec, raw_text)
VALUES 
('11111111-1111-1111-1111-111111111111', 0, 0.0, 15.2, 'Inisialisasi sistem otentikasi dual token berbasis Argon2id dan JWT.'),
('11111111-1111-1111-1111-111111111111', 1, 15.2, 30.5, 'Evaluasi performa database PostgreSQL dengan indeks GIN untuk full text search.');

-- Test Composite Unique Constraint (meeting_id, chunk_index) (Must Fail)
DO $$
BEGIN
    BEGIN
        INSERT INTO transcript_chunks (meeting_id, chunk_index, start_time_sec, end_time_sec, raw_text)
        VALUES ('11111111-1111-1111-1111-111111111111', 0, 30.5, 45.0, 'Duplicate chunk index');
        RAISE EXCEPTION 'Constraint uq_meeting_chunk FAILED to block duplicate chunk index!';
    EXCEPTION WHEN unique_violation THEN
        RAISE NOTICE '[✓] uq_meeting_chunk successfully blocked duplicate chunk index.';
    END;
END $$;

-- Verify Full Text Search Vector Generation & GIN Query
DO $$
DECLARE
    found_count INT;
BEGIN
    SELECT COUNT(*) INTO found_count
    FROM transcript_chunks
    WHERE search_vector @@ to_tsquery('simple', 'argon2id & otentikasi');
    
    IF found_count = 1 THEN
        RAISE NOTICE '[✓] Full Text Search (GIN search_vector) successfully queried token keywords.';
    ELSE
        RAISE EXCEPTION 'Full Text Search failed to match keywords!';
    END IF;
END $$;

-- 5. Test Meeting Summaries with JSONB Action Items & Key Points
INSERT INTO meeting_summaries (meeting_id, executive_summary, key_points, action_items)
VALUES (
    '11111111-1111-1111-1111-111111111111',
    'Rapat berhasil menetapkan standar PostgreSQL 16 dan dual-token auth.',
    '["Implementasi Argon2id selesai", "Konkurensi WebSocket terisolasi"]'::jsonb,
    '[{"task": "Audit skema DB", "assignee": "Homura", "status": "DONE"}, {"task": "Build Android APK", "assignee": "paperalt", "status": "PENDING"}]'::jsonb
);

-- Test JSONB Querying
DO $$
DECLARE
    pending_task TEXT;
BEGIN
    SELECT item->>'task' INTO pending_task
    FROM meeting_summaries s
    CROSS JOIN jsonb_array_elements(s.action_items) AS item
    WHERE item->>'status' = 'PENDING';

    IF pending_task = 'Build Android APK' THEN
        RAISE NOTICE '[✓] JSONB Query successfully extracted structured action item: %', pending_task;
    ELSE
        RAISE EXCEPTION 'JSONB query failed, got: %', pending_task;
    END IF;
END $$;

-- 6. Test Tags & Meeting Tags (N:M)
INSERT INTO tags (id, user_id, name) VALUES (101, '00000000-0000-0000-0000-000000000001', 'CyberSecurity');
INSERT INTO tags (id, user_id, name) VALUES (102, '00000000-0000-0000-0000-000000000001', 'Arsitektur');

INSERT INTO meeting_tags (meeting_id, tag_id) VALUES ('11111111-1111-1111-1111-111111111111', 101);
INSERT INTO meeting_tags (meeting_id, tag_id) VALUES ('11111111-1111-1111-1111-111111111111', 102);

-- 7. Test Cascade Deletion Integrity (Deleting User must cascade delete Meetings, Chunks, Summaries, Tokens, Tags)
DELETE FROM users WHERE id = '00000000-0000-0000-0000-000000000001';

DO $$
DECLARE
    m_count INT;
    c_count INT;
    s_count INT;
    t_count INT;
BEGIN
    SELECT COUNT(*) INTO m_count FROM meetings;
    SELECT COUNT(*) INTO c_count FROM transcript_chunks;
    SELECT COUNT(*) INTO s_count FROM meeting_summaries;
    SELECT COUNT(*) INTO t_count FROM refresh_tokens;

    IF m_count = 0 AND c_count = 0 AND s_count = 0 AND t_count = 0 THEN
        RAISE NOTICE '[✓] Foreign Key ON DELETE CASCADE cleanly purged all child records.';
    ELSE
        RAISE EXCEPTION 'Cascade deletion failed: m=% c=% s=% t=%', m_count, c_count, s_count, t_count;
    END IF;
END $$;

ROLLBACK;
