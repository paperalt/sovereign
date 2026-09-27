package org.sovereign.app.data.backup

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.sovereign.app.auth.TokenStorage
import org.sovereign.app.data.local.AppDatabaseHelper
import org.sovereign.app.network.EndpointConfigStore
import org.sovereign.app.network.EnginePresetStore
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

object BackupManager {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    /**
     * Inspects current on-device data count for preview before exporting.
     */
    suspend fun getInventory(
        context: Context,
        tokenStorage: TokenStorage
    ): BackupInventory = withContext(Dispatchers.IO) {
        val db = AppDatabaseHelper.getInstance(context).readableDatabase

        fun countTable(tableName: String): Int {
            return try {
                db.rawQuery("SELECT COUNT(*) FROM $tableName", null).use { cursor ->
                    if (cursor.moveToFirst()) cursor.getInt(0) else 0
                }
            } catch (_: Exception) {
                0
            }
        }

        val groups = countTable("transcript_groups")
        val meetings = countTable("meetings")
        val chunks = countTable("transcript_chunks")
        val summaries = countTable("meeting_summaries")

        val sttConfigs = EndpointConfigStore.loadSTTConfigs(tokenStorage).size
        val llmConfigs = EndpointConfigStore.loadLLMConfigs(tokenStorage).size
        val presets = EnginePresetStore.loadAll(tokenStorage).size

        BackupInventory(
            groupsCount = groups,
            meetingsCount = meetings,
            chunksCount = chunks,
            summariesCount = summaries,
            sttConfigsCount = sttConfigs,
            llmConfigsCount = llmConfigs,
            presetsCount = presets
        )
    }

    /**
     * Exports entire SQLite database and configured endpoints to a structured JSON string.
     */
    suspend fun exportBackup(
        context: Context,
        tokenStorage: TokenStorage,
        includeApiKeys: Boolean = true
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabaseHelper.getInstance(context).readableDatabase

            // 1. Export Groups
            val groupsList = mutableListOf<BackupGroup>()
            db.rawQuery(
                "SELECT id, name, color, description, created_at FROM transcript_groups ORDER BY created_at ASC",
                null
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    groupsList.add(
                        BackupGroup(
                            id = cursor.getString(0),
                            name = cursor.getString(1),
                            color = cursor.getString(2) ?: "#38BDF8",
                            description = cursor.getString(3) ?: "",
                            createdAt = cursor.getString(4)
                        )
                    )
                }
            }

            // 2. Export Meetings
            val meetingsList = mutableListOf<BackupMeeting>()
            db.rawQuery(
                """
                    SELECT id, title, language, target_language, status, duration_sec,
                           started_at, ended_at, group_id, updated_at
                    FROM meetings ORDER BY started_at ASC
                """.trimIndent(),
                null
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    meetingsList.add(
                        BackupMeeting(
                            id = cursor.getString(0),
                            title = cursor.getString(1),
                            language = cursor.getString(2) ?: "id",
                            targetLanguage = cursor.getString(3) ?: "",
                            status = cursor.getString(4) ?: "COMPLETED",
                            durationSec = cursor.getDouble(5),
                            startedAt = cursor.getString(6),
                            endedAt = cursor.getString(7),
                            groupId = cursor.getString(8),
                            updatedAt = cursor.getString(9)
                        )
                    )
                }
            }

            // 3. Export Chunks
            val chunksList = mutableListOf<BackupChunk>()
            db.rawQuery(
                """
                    SELECT id, meeting_id, chunk_index, text, start_time_sec, end_time_sec, is_final, created_at
                    FROM transcript_chunks ORDER BY meeting_id, chunk_index ASC
                """.trimIndent(),
                null
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    chunksList.add(
                        BackupChunk(
                            id = cursor.getString(0),
                            meetingId = cursor.getString(1),
                            chunkIndex = cursor.getInt(2),
                            text = cursor.getString(3),
                            startTimeSec = cursor.getDouble(4),
                            endTimeSec = cursor.getDouble(5),
                            isFinal = cursor.getInt(6),
                            createdAt = cursor.getString(7)
                        )
                    )
                }
            }

            // 4. Export Summaries
            val summariesList = mutableListOf<BackupSummary>()
            db.rawQuery(
                """
                    SELECT id, meeting_id, summary_text, key_points, action_items, created_at
                    FROM meeting_summaries ORDER BY created_at ASC
                """.trimIndent(),
                null
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    summariesList.add(
                        BackupSummary(
                            id = cursor.getString(0),
                            meetingId = cursor.getString(1),
                            summaryText = cursor.getString(2),
                            keyPoints = cursor.getString(3) ?: "[]",
                            actionItems = cursor.getString(4) ?: "[]",
                            createdAt = cursor.getString(5)
                        )
                    )
                }
            }

            // 5. Export Settings & Endpoints
            val rawStt = EndpointConfigStore.loadSTTConfigs(tokenStorage)
            val rawLlm = EndpointConfigStore.loadLLMConfigs(tokenStorage)
            val rawPresets = EnginePresetStore.loadAll(tokenStorage)

            val sttConfigs = if (includeApiKeys) rawStt else rawStt.map { it.copy(apiKey = "") }
            val llmConfigs = if (includeApiKeys) rawLlm else rawLlm.map { it.copy(apiKey = "") }
            val enginePresets = if (includeApiKeys) rawPresets else rawPresets.map { it.copy(sttKey = "", llmKey = "") }
            val providerKeys = if (includeApiKeys) tokenStorage.getAllProviderApiKeys() else emptyMap()

            val settings = BackupSettings(
                sttConfigs = sttConfigs,
                llmConfigs = llmConfigs,
                enginePresets = enginePresets,
                activeSttId = tokenStorage.getActiveSTTConfigId(),
                activeLlmId = tokenStorage.getActiveLLMConfigId(),
                activeEnginePresetId = tokenStorage.getActiveEnginePresetId(),
                customPresetsUrl = tokenStorage.getCustomPresetsUrl(),
                adaptiveStreamingBeta = tokenStorage.isAdaptiveStreamingBetaEnabled(),
                providerApiKeys = providerKeys
            )

            val metadata = BackupMetadata(
                appName = "Sovereign",
                appVersion = "0.1.0",
                schemaVersion = 1,
                exportedAt = isoFormat.format(Date()),
                deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
                includesApiKeys = includeApiKeys
            )

            val payload = BackupPayload(
                metadata = metadata,
                groups = groupsList,
                meetings = meetingsList,
                chunks = chunksList,
                summaries = summariesList,
                settings = settings
            )

            val jsonOutput = gson.toJson(payload)
            Result.success(jsonOutput)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Parses and validates raw JSON backup payload.
     */
    fun parseBackup(jsonString: String): Result<BackupPayload> {
        return try {
            val payload = gson.fromJson(jsonString, BackupPayload::class.java)
                ?: return Result.failure(IOException("Failed to parse backup JSON: format invalid or empty."))

            val meta = payload.metadata as BackupMetadata?
            if (meta == null || meta.appName != "Sovereign") {
                return Result.failure(IOException("Unrecognized backup file: Sovereign signature missing."))
            }

            Result.success(payload)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Applies parsed backup payload into SQLite and TokenStorage atomically.
     */
    suspend fun applyBackup(
        context: Context,
        tokenStorage: TokenStorage,
        payload: BackupPayload,
        replaceExisting: Boolean = false,
        restoreSettings: Boolean = true
    ): Result<RestoreSummary> = withContext(Dispatchers.IO) {
        val db = AppDatabaseHelper.getInstance(context).writableDatabase
        db.beginTransaction()

        var groupsRestored = 0
        var meetingsRestored = 0
        var chunksRestored = 0
        var summariesRestored = 0

        try {
            if (replaceExisting) {
                db.delete("meeting_summaries", null, null)
                db.delete("transcript_chunks", null, null)
                db.delete("meetings", null, null)
                db.delete("transcript_groups", null, null)
            }

            // 1. Restore Groups
            for (g in payload.groups) {
                val cv = ContentValues().apply {
                    put("id", g.id)
                    put("name", g.name)
                    put("color", g.color)
                    put("description", g.description ?: "")
                    put("created_at", g.createdAt)
                }
                val rowId = db.insertWithOnConflict("transcript_groups", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
                if (rowId != -1L) groupsRestored++
            }

            // 2. Restore Meetings
            for (m in payload.meetings) {
                val cv = ContentValues().apply {
                    put("id", m.id)
                    put("title", m.title)
                    put("language", m.language)
                    put("target_language", m.targetLanguage ?: "")
                    put("status", m.status)
                    put("duration_sec", m.durationSec)
                    put("started_at", m.startedAt)
                    put("ended_at", m.endedAt)
                    put("group_id", m.groupId)
                    put("updated_at", m.updatedAt)
                }
                val rowId = db.insertWithOnConflict("meetings", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
                if (rowId != -1L) meetingsRestored++
            }

            // 3. Restore Chunks
            for (c in payload.chunks) {
                val cv = ContentValues().apply {
                    put("id", c.id)
                    put("meeting_id", c.meetingId)
                    put("chunk_index", c.chunkIndex)
                    put("text", c.text)
                    put("start_time_sec", c.startTimeSec)
                    put("end_time_sec", c.endTimeSec)
                    put("is_final", c.isFinal)
                    put("created_at", c.createdAt)
                }
                val rowId = db.insertWithOnConflict("transcript_chunks", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
                if (rowId != -1L) chunksRestored++
            }

            // 4. Restore Summaries
            for (s in payload.summaries) {
                val cv = ContentValues().apply {
                    put("id", s.id)
                    put("meeting_id", s.meetingId)
                    put("summary_text", s.summaryText)
                    put("key_points", s.keyPoints)
                    put("action_items", s.actionItems)
                    put("created_at", s.createdAt)
                }
                val rowId = db.insertWithOnConflict("meeting_summaries", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
                if (rowId != -1L) summariesRestored++
            }

            db.setTransactionSuccessful()
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        } finally {
            db.endTransaction()
        }

        // 5. Restore Settings if requested
        var sttCount = 0
        var llmCount = 0
        var presetCount = 0
        var settingsRestored = false

        if (restoreSettings && payload.settings != null) {
            val s = payload.settings

            if (s.sttConfigs.isNotEmpty()) {
                val existing = if (replaceExisting) emptyList() else EndpointConfigStore.loadSTTConfigs(tokenStorage)
                val merged = (existing.filterNot { ex -> s.sttConfigs.any { it.id == ex.id } } + s.sttConfigs)
                EndpointConfigStore.saveSTTConfigs(tokenStorage, merged)
                sttCount = s.sttConfigs.size
            }

            if (s.llmConfigs.isNotEmpty()) {
                val existing = if (replaceExisting) emptyList() else EndpointConfigStore.loadLLMConfigs(tokenStorage)
                val merged = (existing.filterNot { ex -> s.llmConfigs.any { it.id == ex.id } } + s.llmConfigs)
                EndpointConfigStore.saveLLMConfigs(tokenStorage, merged)
                llmCount = s.llmConfigs.size
            }

            if (s.enginePresets.isNotEmpty()) {
                val existing = if (replaceExisting) emptyList() else EnginePresetStore.loadAll(tokenStorage)
                val merged = (existing.filterNot { ex -> s.enginePresets.any { it.id == ex.id } } + s.enginePresets)
                EnginePresetStore.saveAll(tokenStorage, merged)
                presetCount = s.enginePresets.size
            }

            if (!s.activeSttId.isNullOrBlank()) {
                tokenStorage.setActiveSTTConfigId(s.activeSttId)
            }
            if (!s.activeLlmId.isNullOrBlank()) {
                tokenStorage.setActiveLLMConfigId(s.activeLlmId)
            }
            if (!s.activeEnginePresetId.isNullOrBlank()) {
                tokenStorage.setActiveEnginePresetId(s.activeEnginePresetId)
            }
            if (!s.customPresetsUrl.isNullOrBlank()) {
                tokenStorage.setCustomPresetsUrl(s.customPresetsUrl)
            }
            tokenStorage.setAdaptiveStreamingBetaEnabled(s.adaptiveStreamingBeta)

            if (s.providerApiKeys.isNotEmpty()) {
                tokenStorage.restoreProviderApiKeys(s.providerApiKeys)
            }

            settingsRestored = true
        }

        Result.success(
            RestoreSummary(
                groupsRestored = groupsRestored,
                meetingsRestored = meetingsRestored,
                chunksRestored = chunksRestored,
                summariesRestored = summariesRestored,
                sttConfigsRestored = sttCount,
                llmConfigsRestored = llmCount,
                presetsRestored = presetCount,
                settingsRestored = settingsRestored
            )
        )
    }
}
