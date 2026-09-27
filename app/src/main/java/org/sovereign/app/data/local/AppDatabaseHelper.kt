package org.sovereign.app.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class AppDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "sovereign_transcribe.db"
        private const val DATABASE_VERSION = 1

        @Volatile
        private var INSTANCE: AppDatabaseHelper? = null

        fun getInstance(context: Context): AppDatabaseHelper {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppDatabaseHelper(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        // 1. Transcript Groups
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS transcript_groups (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                color TEXT NOT NULL DEFAULT '#38BDF8',
                description TEXT DEFAULT '',
                created_at TEXT NOT NULL
            );
        """.trimIndent())

        // 2. Meetings
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS meetings (
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                language TEXT NOT NULL DEFAULT 'id',
                target_language TEXT DEFAULT '',
                status TEXT NOT NULL DEFAULT 'RECORDING',
                duration_sec REAL DEFAULT 0.0,
                started_at TEXT NOT NULL,
                ended_at TEXT,
                group_id TEXT,
                updated_at TEXT NOT NULL,
                FOREIGN KEY (group_id) REFERENCES transcript_groups(id) ON DELETE SET NULL
            );
        """.trimIndent())

        // 3. Transcript Chunks
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS transcript_chunks (
                id TEXT PRIMARY KEY,
                meeting_id TEXT NOT NULL,
                chunk_index INTEGER NOT NULL,
                text TEXT NOT NULL,
                start_time_sec REAL NOT NULL,
                end_time_sec REAL NOT NULL,
                is_final INTEGER DEFAULT 1,
                created_at TEXT NOT NULL,
                FOREIGN KEY (meeting_id) REFERENCES meetings(id) ON DELETE CASCADE
            );
        """.trimIndent())

        // 4. Meeting Summaries
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS meeting_summaries (
                id TEXT PRIMARY KEY,
                meeting_id TEXT UNIQUE NOT NULL,
                summary_text TEXT NOT NULL,
                key_points TEXT NOT NULL DEFAULT '[]',
                action_items TEXT NOT NULL DEFAULT '[]',
                created_at TEXT NOT NULL,
                FOREIGN KEY (meeting_id) REFERENCES meetings(id) ON DELETE CASCADE
            );
        """.trimIndent())

        // Indexes for lightning-fast queries
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_meetings_updated ON meetings(updated_at DESC);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_meetings_group ON meetings(group_id);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_chunks_meeting ON transcript_chunks(meeting_id, chunk_index ASC);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_groups_created ON transcript_groups(created_at DESC);")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Incremental migration steps if database version bumps
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }
}
