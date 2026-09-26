-- BENCHMARK & EXPLAIN ANALYZE AUDIT SCRIPT

-- 1. Setup sample data
DO $$
DECLARE
    uid UUID := 'a0000000-0000-0000-0000-000000000001';
    mid UUID := 'b0000000-0000-0000-0000-000000000001';
    i INT;
BEGIN
    INSERT INTO users (id, email, password_hash, full_name, role)
    VALUES (uid, 'benchmark.user@sec.local', 'hash', 'Benchmark Auditor', 'user')
    ON CONFLICT DO NOTHING;

    -- Insert 500 meetings for this user to test list pagination
    FOR i IN 1..500 LOOP
        INSERT INTO meetings (id, user_id, title, status, created_at)
        VALUES (
            gen_random_uuid(),
            uid,
            'Meeting Record #' || i,
            'COMPLETED',
            NOW() - (i || ' hours')::INTERVAL
        );
    END LOOP;

    -- Insert 1 meeting for chunk benchmarking
    INSERT INTO meetings (id, user_id, title, status)
    VALUES (mid, uid, 'Heavy Audio Session', 'IN_PROGRESS')
    ON CONFLICT DO NOTHING;

    -- Insert 2,000 chunks (~5 hours of recording)
    FOR i IN 1..2000 LOOP
        INSERT INTO transcript_chunks (meeting_id, chunk_index, start_time_sec, end_time_sec, raw_text)
        VALUES (
            mid,
            i,
            (i * 10.0),
            (i * 10.0 + 9.5),
            'Chunk nomor ' || i || ' membahas arsitektur kriptografi, audit memori, dan keamanan backend streaming Go.'
        );
    END LOOP;

    -- Insert 1 summary
    INSERT INTO meeting_summaries (meeting_id, executive_summary, key_points, action_items)
    VALUES (
        mid,
        'Ringkasan eksekutif pengujian beban database.',
        '["Poin 1: Efisiensi memori", "Poin 2: Throughput stream"]'::jsonb,
        '[{"task": "Evaluasi index", "status": "PENDING"}]'::jsonb
    )
    ON CONFLICT DO NOTHING;
END $$;

VACUUM ANALYZE;

-- Profiling Query 1: List Meetings by User (Paginated)
EXPLAIN (ANALYZE, BUFFERS)
SELECT id, title, status, language, created_at 
FROM meetings 
WHERE user_id = 'a0000000-0000-0000-0000-000000000001'
ORDER BY created_at DESC 
LIMIT 20 OFFSET 0;

-- Profiling Query 2: Fetch Full Transcript for Meeting (Ordered chunks)
EXPLAIN (ANALYZE, BUFFERS)
SELECT chunk_index, start_time_sec, end_time_sec, raw_text 
FROM transcript_chunks 
WHERE meeting_id = 'b0000000-0000-0000-0000-000000000001'
ORDER BY chunk_index ASC;

-- Profiling Query 3: Full-Text Search across chunks
EXPLAIN (ANALYZE, BUFFERS)
SELECT chunk_index, start_time_sec, raw_text 
FROM transcript_chunks 
WHERE search_vector @@ to_tsquery('simple', 'kriptografi & streaming');

-- Profiling Query 4: Index Size & Redundancy Audit
SELECT
    relname AS table_name,
    indexrelname AS index_name,
    pg_size_pretty(pg_relation_size(i.indexrelid)) AS index_size,
    idx_scan AS number_of_scans
FROM pg_stat_user_indexes ui
JOIN pg_index i ON ui.indexrelid = i.indexrelid
JOIN pg_statio_user_indexes sui ON ui.indexrelid = sui.indexrelid
ORDER BY pg_relation_size(i.indexrelid) DESC;
