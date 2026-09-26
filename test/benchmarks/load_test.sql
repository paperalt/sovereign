-- MULTI-MEETING REALISTIC LOAD TEST (50,000 Chunks across 50 Meetings)

DO $$
DECLARE
    u_other UUID;
    m_id UUID;
    i INT;
    j INT;
BEGIN
    FOR i IN 1..50 LOOP
        u_other := gen_random_uuid();
        INSERT INTO users (id, email, password_hash, full_name, role)
        VALUES (u_other, 'user.' || i || '@loadtest.local', 'hash', 'User ' || i, 'user');

        m_id := gen_random_uuid();
        INSERT INTO meetings (id, user_id, title, status, created_at)
        VALUES (m_id, u_other, 'Session #' || i, 'COMPLETED', NOW() - (i || ' days')::INTERVAL);

        -- Insert 100 chunks per meeting
        FOR j IN 1..100 LOOP
            INSERT INTO transcript_chunks (meeting_id, chunk_index, start_time_sec, end_time_sec, raw_text)
            VALUES (m_id, j, (j * 5.0), (j * 5.0 + 4.8), 'Transkripsi sesi percakapan nomor ' || j || ' dengan topik evaluasi performa.');
        END LOOP;
    END LOOP;
END $$;

VACUUM ANALYZE;
