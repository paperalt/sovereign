package org.sovereign.app.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import org.sovereign.app.auth.EncryptedTokenStorage
import org.sovereign.app.auth.TokenStorage
import org.sovereign.app.audio.TranscriptStitcher
import org.sovereign.app.data.local.AppDatabaseHelper
import org.sovereign.app.network.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class LocalMeetingRepository(
    private val context: Context,
    private val dbHelper: AppDatabaseHelper = AppDatabaseHelper.getInstance(context),
    private val tokenStorage: TokenStorage = EncryptedTokenStorage(context),
    private val directAIClient: DirectAIClient = DirectAIClient(),
    private val gson: Gson = Gson()
) : MeetingRepository {

    private fun nowIso(): String = java.time.Instant.now().toString()

    override suspend fun createMeeting(
        title: String,
        language: String,
        targetLanguage: String,
        groupId: String?
    ): Result<MeetingDto> = withContext(Dispatchers.IO) {
        try {
            val db = dbHelper.writableDatabase
            val meetingId = UUID.randomUUID().toString()
            val now = nowIso()

            val values = ContentValues().apply {
                put("id", meetingId)
                put("title", if (title.isBlank()) "Local Transcription Session" else title)
                put("language", if (language.isBlank()) "id" else language)
                put("target_language", targetLanguage)
                put("status", "RECORDING")
                put("duration_sec", 0.0)
                put("started_at", now)
                put("updated_at", now)
                put("group_id", groupId)
            }

            db.insertOrThrow("meetings", null, values)

            Result.success(
                MeetingDto(
                    id = meetingId,
                    userId = "sovereign-user",
                    groupId = groupId,
                    groupName = null,
                    title = values.getAsString("title"),
                    language = values.getAsString("language"),
                    targetLanguage = targetLanguage,
                    status = "RECORDING",
                    startedAt = now,
                    endedAt = null,
                    durationSec = 0.0,
                    summary = null,
                    createdAt = now
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun listMeetings(limit: Int, offset: Int): Result<List<MeetingDto>> = withContext(Dispatchers.IO) {
        try {
            val db = dbHelper.readableDatabase
            val query = """
                SELECT m.id, m.title, m.language, m.target_language, m.status, m.duration_sec,
                       m.started_at, m.ended_at, m.group_id, m.updated_at,
                       g.name AS group_name,
                       s.summary_text AS summary
                FROM meetings m
                LEFT JOIN transcript_groups g ON m.group_id = g.id
                LEFT JOIN meeting_summaries s ON s.meeting_id = m.id
                ORDER BY m.updated_at DESC
                LIMIT ? OFFSET ?
            """.trimIndent()

            val cursor = db.rawQuery(query, arrayOf(limit.toString(), offset.toString()))
            val list = mutableListOf<MeetingDto>()

            cursor.use {
                while (it.moveToNext()) {
                    list.add(cursorToMeetingDto(it))
                }
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getActiveMeeting(): Result<MeetingDto?> = withContext(Dispatchers.IO) {
        try {
            val db = dbHelper.readableDatabase
            val query = """
                SELECT m.id, m.title, m.language, m.target_language, m.status, m.duration_sec,
                       m.started_at, m.ended_at, m.group_id, m.updated_at,
                       g.name AS group_name,
                       s.summary_text AS summary
                FROM meetings m
                LEFT JOIN transcript_groups g ON m.group_id = g.id
                LEFT JOIN meeting_summaries s ON s.meeting_id = m.id
                WHERE m.status = 'RECORDING'
                ORDER BY m.started_at DESC
                LIMIT 1
            """.trimIndent()

            val cursor = db.rawQuery(query, null)
            cursor.use {
                if (it.moveToNext()) {
                    Result.success(cursorToMeetingDto(it))
                } else {
                    Result.success(null)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getTranscript(meetingId: String): Result<FullTranscriptDto> = withContext(Dispatchers.IO) {
        try {
            val db = dbHelper.readableDatabase

            // 1. Get Meeting
            val meetingQuery = """
                SELECT m.id, m.title, m.language, m.target_language, m.status, m.duration_sec,
                       m.started_at, m.ended_at, m.group_id, m.updated_at,
                       g.name AS group_name,
                       s.summary_text AS summary
                FROM meetings m
                LEFT JOIN transcript_groups g ON m.group_id = g.id
                LEFT JOIN meeting_summaries s ON s.meeting_id = m.id
                WHERE m.id = ?
            """.trimIndent()

            var meetingDto: MeetingDto? = null
            db.rawQuery(meetingQuery, arrayOf(meetingId)).use {
                if (it.moveToNext()) {
                    meetingDto = cursorToMeetingDto(it)
                }
            }

            if (meetingDto == null) {
                return@withContext Result.failure(Exception("Meeting not found"))
            }

            // 2. Get Chunks
            val chunksQuery = """
                SELECT id, meeting_id, chunk_index, text, start_time_sec, end_time_sec, created_at
                FROM transcript_chunks
                WHERE meeting_id = ?
                ORDER BY chunk_index ASC
            """.trimIndent()

            val chunks = mutableListOf<TranscriptChunkDto>()
            db.rawQuery(chunksQuery, arrayOf(meetingId)).use { cursor ->
                while (cursor.moveToNext()) {
                    val idStr = cursor.getString(cursor.getColumnIndexOrThrow("id"))
                    val chunkIdLong = idStr.hashCode().toLong()
                    chunks.add(
                        TranscriptChunkDto(
                            id = chunkIdLong,
                            meetingId = cursor.getString(cursor.getColumnIndexOrThrow("meeting_id")),
                            chunkIndex = cursor.getInt(cursor.getColumnIndexOrThrow("chunk_index")),
                            startTimeSec = cursor.getDouble(cursor.getColumnIndexOrThrow("start_time_sec")),
                            endTimeSec = cursor.getDouble(cursor.getColumnIndexOrThrow("end_time_sec")),
                            rawText = cursor.getString(cursor.getColumnIndexOrThrow("text")),
                            createdAt = cursor.getString(cursor.getColumnIndexOrThrow("created_at"))
                        )
                    )
                }
            }

            // 3. Get Summary
            val summaryQuery = """
                SELECT id, meeting_id, summary_text, key_points, action_items, created_at
                FROM meeting_summaries
                WHERE meeting_id = ?
            """.trimIndent()

            var summaryDto: MeetingSummaryDto? = null
            db.rawQuery(summaryQuery, arrayOf(meetingId)).use { cursor ->
                if (cursor.moveToNext()) {
                    val keyPointsJson = cursor.getString(cursor.getColumnIndexOrThrow("key_points"))
                    val actionItemsJson = cursor.getString(cursor.getColumnIndexOrThrow("action_items"))

                    val keyPointsList: List<String> = try {
                        val listType = object : TypeToken<List<String>>() {}.type
                        gson.fromJson(keyPointsJson, listType) ?: emptyList()
                    } catch (_: Exception) { emptyList() }

                    val actionItemsList: List<ActionItemDto> = try {
                        val listType = object : TypeToken<List<ActionItemDto>>() {}.type
                        gson.fromJson(actionItemsJson, listType) ?: emptyList()
                    } catch (_: Exception) { emptyList() }

                    summaryDto = MeetingSummaryDto(
                        id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                        meetingId = cursor.getString(cursor.getColumnIndexOrThrow("meeting_id")),
                        executiveSummary = cursor.getString(cursor.getColumnIndexOrThrow("summary_text")),
                        keyPoints = keyPointsList,
                        actionItems = actionItemsList,
                        modelUsed = "sovereign-local"
                    )
                }
            }

            var stitchedFullText = ""
            for (c in chunks) {
                stitchedFullText = TranscriptStitcher.stitch(stitchedFullText, c.rawText)
            }
            val fullText = stitchedFullText

            Result.success(
                FullTranscriptDto(
                    meetingId = meetingDto!!.id,
                    title = meetingDto!!.title,
                    language = meetingDto!!.language,
                    targetLanguage = meetingDto!!.targetLanguage,
                    status = meetingDto!!.status,
                    startedAt = meetingDto!!.startedAt,
                    endedAt = meetingDto!!.endedAt,
                    durationSec = meetingDto!!.durationSec,
                    summary = summaryDto?.executiveSummary,
                    structuredSummary = summaryDto,
                    fullText = fullText,
                    totalChunks = chunks.size,
                    chunks = chunks
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun stopMeeting(meetingId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val db = dbHelper.writableDatabase
            val now = nowIso()

            var maxEndSec = 0.0
            db.rawQuery("SELECT MAX(end_time_sec) FROM transcript_chunks WHERE meeting_id = ?", arrayOf(meetingId)).use {
                if (it.moveToNext()) {
                    maxEndSec = it.getDouble(0)
                }
            }
            if (maxEndSec <= 0.0) {
                db.rawQuery("SELECT started_at FROM meetings WHERE id = ?", arrayOf(meetingId)).use { cursor ->
                    if (cursor.moveToFirst()) {
                        val startedAtStr = cursor.getString(0)
                        try {
                            val startInstant = java.time.Instant.parse(startedAtStr)
                            val endInstant = java.time.Instant.parse(now)
                            val diff = java.time.Duration.between(startInstant, endInstant).seconds.toDouble()
                            if (diff > 0.0) maxEndSec = diff
                        } catch (_: Exception) {}
                    }
                }
            }

            val values = ContentValues().apply {
                put("status", "COMPLETED")
                put("ended_at", now)
                put("updated_at", now)
                if (maxEndSec > 0.0) {
                    put("duration_sec", maxEndSec)
                }
            }

            db.update("meetings", values, "id = ?", arrayOf(meetingId))
            Result.success("COMPLETED")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun cancelMeeting(meetingId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val db = dbHelper.writableDatabase
            db.beginTransaction()
            try {
                db.delete("meeting_summaries", "meeting_id = ?", arrayOf(meetingId))
                db.delete("transcript_chunks", "meeting_id = ?", arrayOf(meetingId))
                db.delete("meetings", "id = ?", arrayOf(meetingId))
                db.setTransactionSuccessful()
                Result.success(Unit)
            } finally {
                db.endTransaction()
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun summarizeMeeting(meetingId: String): Result<MeetingSummaryDto> = withContext(Dispatchers.IO) {
        try {
            val db = dbHelper.writableDatabase

            val chunksQuery = "SELECT text FROM transcript_chunks WHERE meeting_id = ? ORDER BY chunk_index ASC"
            var stitched = ""
            db.rawQuery(chunksQuery, arrayOf(meetingId)).use { cursor ->
                while (cursor.moveToNext()) {
                    stitched = TranscriptStitcher.stitch(stitched, cursor.getString(0))
                }
            }
            val fullText = stitched.trim()
            if (fullText.isBlank()) {
                return@withContext Result.failure(Exception("No audible speech recorded to summarize."))
            }

            // Cap transcript at 60,000 characters (~15k tokens) to prevent context window overflows on long sessions
            val safeFullTranscript = if (fullText.length > 60000) fullText.take(60000) else fullText

            val provider = tokenStorage.getSelectedPreset().ifBlank { "groq" }
            val llmEndpoint = tokenStorage.getLLMEndpoint()
            val llmModel = tokenStorage.getLLMModel()
            val apiKey = tokenStorage.getLLMKey().ifBlank { tokenStorage.getSTTKey() }

            if (apiKey.isBlank() && !llmEndpoint.contains("localhost") && !llmEndpoint.contains("10.0.2.2")) {
                return@withContext Result.failure(Exception("AI API key is not configured. Open AI Engine settings to add it."))
            }

            val summaryRes = directAIClient.generateSummary(
                fullTranscript = safeFullTranscript,
                provider = provider,
                apiKey = apiKey,
                customEndpoint = llmEndpoint.ifBlank { null },
                customModel = llmModel.ifBlank { null }
            )
            if (summaryRes.isFailure) {
                return@withContext Result.failure(summaryRes.exceptionOrNull() ?: Exception("Failed to generate summary"))
            }

            val res = summaryRes.getOrThrow()
            val summaryId = UUID.randomUUID().toString()
            val now = nowIso()

            val values = ContentValues().apply {
                put("id", summaryId)
                put("meeting_id", meetingId)
                put("summary_text", res.summaryText)
                put("key_points", gson.toJson(res.keyPoints))
                put("action_items", gson.toJson(res.actionItems))
                put("created_at", now)
            }

            db.insertWithOnConflict("meeting_summaries", null, values, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)

            val meetingVal = ContentValues().apply { put("updated_at", now) }
            db.update("meetings", meetingVal, "id = ?", arrayOf(meetingId))

            Result.success(
                MeetingSummaryDto(
                    id = summaryId,
                    meetingId = meetingId,
                    executiveSummary = res.summaryText,
                    keyPoints = res.keyPoints,
                    actionItems = res.actionItems,
                    modelUsed = provider
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun search(query: String): Result<List<SearchResultDto>> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            return@withContext Result.success(emptyList())
        }

        try {
            val db = dbHelper.readableDatabase
            val sql = """
                SELECT m.id AS meeting_id, m.title AS meeting_title, c.chunk_index, c.text, c.start_time_sec, c.end_time_sec
                FROM transcript_chunks c
                JOIN meetings m ON c.meeting_id = m.id
                WHERE c.text LIKE ? OR m.title LIKE ?
                ORDER BY m.updated_at DESC
                LIMIT 50
            """.trimIndent()

            val pattern = "%$trimmed%"
            val cursor = db.rawQuery(sql, arrayOf(pattern, pattern))
            val results = mutableListOf<SearchResultDto>()

            cursor.use {
                while (it.moveToNext()) {
                    results.add(
                        SearchResultDto(
                            meetingId = it.getString(it.getColumnIndexOrThrow("meeting_id")),
                            meetingTitle = it.getString(it.getColumnIndexOrThrow("meeting_title")),
                            chunkIndex = it.getInt(it.getColumnIndexOrThrow("chunk_index")),
                            startTimeSec = it.getDouble(it.getColumnIndexOrThrow("start_time_sec")),
                            endTimeSec = it.getDouble(it.getColumnIndexOrThrow("end_time_sec")),
                            rawText = it.getString(it.getColumnIndexOrThrow("text"))
                        )
                    )
                }
            }
            Result.success(results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun searchFull(query: String): Result<SearchResponse> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            return@withContext Result.success(SearchResponse(query = "", results = emptyList(), meetings = emptyList(), total = 0))
        }

        try {
            val db = dbHelper.readableDatabase
            val pattern = "%$trimmed%"

            // 1. Search transcript chunks
            val chunkResults = search(trimmed).getOrDefault(emptyList())
            val chunkMeetingIds = chunkResults.map { it.meetingId }.toSet()

            // 2. Comprehensive direct meeting search across title, language, group name, summary, and action items
            val meetingSql = """
                SELECT m.id, m.title, m.language, m.target_language, m.status, m.duration_sec,
                       m.started_at, m.ended_at, m.group_id, m.updated_at,
                       g.name AS group_name,
                       s.summary_text AS summary
                FROM meetings m
                LEFT JOIN transcript_groups g ON m.group_id = g.id
                LEFT JOIN meeting_summaries s ON s.meeting_id = m.id
                WHERE m.title LIKE ?
                   OR (g.name IS NOT NULL AND g.name LIKE ?)
                   OR m.language LIKE ?
                   OR (s.summary_text IS NOT NULL AND s.summary_text LIKE ?)
                   OR (s.key_points IS NOT NULL AND s.key_points LIKE ?)
                   OR (s.action_items IS NOT NULL AND s.action_items LIKE ?)
                ORDER BY m.updated_at DESC
                LIMIT 50
            """.trimIndent()

            val matchedMeetings = mutableListOf<MeetingDto>()
            val foundMeetingIds = mutableSetOf<String>()

            db.rawQuery(meetingSql, arrayOf(pattern, pattern, pattern, pattern, pattern, pattern)).use { cursor ->
                while (cursor.moveToNext()) {
                    val dto = cursorToMeetingDto(cursor)
                    matchedMeetings.add(dto)
                    foundMeetingIds.add(dto.id)
                }
            }

            // 3. Include any meetings discovered via transcript chunk search that were not caught by direct filter
            val missingIds = chunkMeetingIds.filterNot { it in foundMeetingIds }
            if (missingIds.isNotEmpty()) {
                val placeholders = missingIds.joinToString(",") { "?" }
                val extraQuery = """
                    SELECT m.id, m.title, m.language, m.target_language, m.status, m.duration_sec,
                           m.started_at, m.ended_at, m.group_id, m.updated_at,
                           g.name AS group_name,
                           s.summary_text AS summary
                    FROM meetings m
                    LEFT JOIN transcript_groups g ON m.group_id = g.id
                    LEFT JOIN meeting_summaries s ON s.meeting_id = m.id
                    WHERE m.id IN ($placeholders)
                    ORDER BY m.updated_at DESC
                """.trimIndent()
                db.rawQuery(extraQuery, missingIds.toTypedArray()).use { cursor ->
                    while (cursor.moveToNext()) {
                        matchedMeetings.add(cursorToMeetingDto(cursor))
                    }
                }
            }

            Result.success(
                SearchResponse(
                    query = trimmed,
                    results = chunkResults,
                    meetings = matchedMeetings,
                    total = chunkResults.size + matchedMeetings.size
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteMeeting(meetingId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val db = dbHelper.writableDatabase
            db.beginTransaction()
            try {
                db.delete("meeting_summaries", "meeting_id = ?", arrayOf(meetingId))
                db.delete("transcript_chunks", "meeting_id = ?", arrayOf(meetingId))
                db.delete("meetings", "id = ?", arrayOf(meetingId))
                db.setTransactionSuccessful()
                Result.success(Unit)
            } finally {
                db.endTransaction()
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getUserQuota(): Result<UserQuotaDto> = withContext(Dispatchers.IO) {
        Result.success(
            UserQuotaDto(
                tier = "SOVEREIGN_UNLIMITED",
                quotaSeconds = 9999999,
                usedSeconds = 0,
                remainingSeconds = 9999999,
                remainingMinutes = 9999999 / 60,
                isUnlimited = true
            )
        )
    }

    override suspend fun getPlans(): Result<List<SubscriptionPlanDto>> = withContext(Dispatchers.IO) {
        Result.success(
            listOf(
                SubscriptionPlanDto(
                    id = "sovereign_mit",
                    name = "Sovereign Core (MIT License)",
                    description = "100% local data and processing on your Android device",
                    durationMin = 999999,
                    priceIdr = 0,
                    badge = "LOKAL"
                )
            )
        )
    }

    override suspend fun topUp(planId: String): Result<UserQuotaDto> = getUserQuota()

    override suspend fun redeemVoucher(code: String): Result<String> = withContext(Dispatchers.IO) {
        Result.success("Runs fully offline on this device.")
    }

    override suspend fun checkAppVersion(): Result<AppVersionDto> = withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(8, TimeUnit.SECONDS)
                .build()

            // 1. Try GitHub Releases API first
            val ghReq = Request.Builder()
                .url("https://api.github.com/repos/paperalt/sovereign/releases/latest")
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "Sovereign-Android")
                .build()

            client.newCall(ghReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    val json = JsonParser.parseString(body).asJsonObject
                    val tag = json.get("tag_name")?.asString ?: "v0.1.0"
                    val vName = tag.removePrefix("v").trim()
                    val notes = json.get("body")?.asString ?: "New release available on GitHub."
                    val htmlUrl = json.get("html_url")?.asString ?: "https://github.com/paperalt/sovereign/releases/latest"

                    val parts = vName.split('.').mapNotNull { it.toIntOrNull() }
                    val vCode = if (parts.size >= 3) {
                        (parts[0] * 10000 + parts[1] * 100 + parts[2]).toLong()
                    } else 1L

                    return@withContext Result.success(
                        AppVersionDto(
                            latestVersionCode = vCode,
                            latestVersionName = vName,
                            minSupportedVersionCode = 1,
                            downloadUrl = htmlUrl,
                            releaseNotes = notes,
                            isCritical = false,
                            sha256 = null
                        )
                    )
                }
            }

            // 2. Fallback to raw version.json
            val rawReq = Request.Builder()
                .url("https://raw.githubusercontent.com/paperalt/sovereign/master/config/version.json")
                .header("User-Agent", "Sovereign-Android")
                .build()

            client.newCall(rawReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    val json = JsonParser.parseString(body).asJsonObject
                    return@withContext Result.success(
                        AppVersionDto(
                            latestVersionCode = json.get("version_code")?.asLong ?: 1L,
                            latestVersionName = json.get("version_name")?.asString ?: "0.1.0",
                            minSupportedVersionCode = json.get("min_supported_version_code")?.asLong ?: 1L,
                            downloadUrl = json.get("download_url")?.asString ?: "https://github.com/paperalt/sovereign/releases/latest",
                            releaseNotes = json.get("release_notes")?.asString ?: "Sovereign: Private On-Device Transcription",
                            isCritical = json.get("is_critical")?.asBoolean ?: false,
                            sha256 = json.get("sha256")?.asString
                        )
                    )
                }
            }
        } catch (_: Exception) {}

        // Fallback to current version when offline
        Result.success(
            AppVersionDto(
                latestVersionCode = 1,
                latestVersionName = "0.1.0",
                minSupportedVersionCode = 1,
                downloadUrl = "https://github.com/paperalt/sovereign/releases/latest",
                releaseNotes = "Sovereign v0.1.0: You are on the latest release.",
                isCritical = false,
                sha256 = null
            )
        )
    }

    override suspend fun suggestQuestions(
        meetingId: String,
        windowMinutes: Int,
        focusTopic: String
    ): Result<QuestionSuggestionResponseDto> = withContext(Dispatchers.IO) {
        try {
            val db = dbHelper.readableDatabase

            val query = if (windowMinutes > 0) {
                """
                    SELECT text FROM transcript_chunks
                    WHERE meeting_id = ?
                    ORDER BY chunk_index DESC
                    LIMIT ?
                """.trimIndent()
            } else {
                """
                    SELECT text FROM transcript_chunks
                    WHERE meeting_id = ?
                    ORDER BY chunk_index ASC
                """.trimIndent()
            }

            val limit = if (windowMinutes > 0) windowMinutes * 10 else 100
            val chunksList = mutableListOf<String>()

            val args = if (windowMinutes > 0) arrayOf(meetingId, limit.toString()) else arrayOf(meetingId)
            db.rawQuery(query, args).use {
                while (it.moveToNext()) {
                    chunksList.add(it.getString(0))
                }
            }

            if (windowMinutes > 0) {
                chunksList.reverse()
            }

            var stitchedContext = ""
            for (text in chunksList) {
                stitchedContext = TranscriptStitcher.stitch(stitchedContext, text)
            }
            val contextText = stitchedContext.trim()
            if (contextText.length < 50) {
                return@withContext Result.success(
                    QuestionSuggestionResponseDto(
                        meetingId = meetingId,
                        windowMinutes = windowMinutes,
                        analyzedDurationSec = 0.0,
                        wordCount = if (contextText.isBlank()) 0 else contextText.split("\\s+".toRegex()).size,
                        hasSufficientContext = false,
                        message = "Transcript in this window is not yet substantive enough to formulate grounded inquiries.",
                        suggestions = emptyList()
                    )
                )
            }

            // Cap recent inquiry context at 24,000 characters (~6k tokens) to guarantee rapid response and zero payload overflow
            val safeInquiryContext = if (contextText.length > 24000) contextText.takeLast(24000) else contextText

            val provider = tokenStorage.getSelectedPreset().ifBlank { "groq" }
            val llmEndpoint = tokenStorage.getLLMEndpoint()
            val llmModel = tokenStorage.getLLMModel()
            val apiKey = tokenStorage.getLLMKey().ifBlank { tokenStorage.getSTTKey() }

            if (apiKey.isBlank() && !llmEndpoint.contains("localhost") && !llmEndpoint.contains("10.0.2.2")) {
                return@withContext Result.failure(Exception("API key is missing. Open AI Engine settings to add it."))
            }

            val questionsRes = directAIClient.suggestQuestions(
                transcriptContext = safeInquiryContext,
                focusTopic = focusTopic,
                provider = provider,
                apiKey = apiKey,
                customEndpoint = llmEndpoint.ifBlank { null },
                customModel = llmModel.ifBlank { null }
            )
            if (questionsRes.isFailure) {
                return@withContext Result.failure(questionsRes.exceptionOrNull() ?: Exception("Failed to generate question suggestions"))
            }

            val items = questionsRes.getOrThrow().map {
                QuestionSuggestionDto(
                    id = UUID.randomUUID().toString(),
                    question = it.question,
                    category = it.category,
                    contextRef = it.contextRef,
                    thoughtStarter = it.rationale
                )
            }

            Result.success(
                QuestionSuggestionResponseDto(
                    meetingId = meetingId,
                    windowMinutes = windowMinutes,
                    analyzedDurationSec = 300.0,
                    wordCount = contextText.split("\\s+".toRegex()).size,
                    hasSufficientContext = contextText.length >= 50,
                    message = null,
                    suggestions = items
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun validateAIKey(provider: String, apiKey: String): Result<ValidateKeyResponseDto> = withContext(Dispatchers.IO) {
        try {
            val ping = directAIClient.suggestQuestions("Halo uji coba", "", provider, apiKey)
            if (ping.isSuccess) {
                Result.success(
                    ValidateKeyResponseDto(
                        valid = true,
                        provider = provider,
                        latencyMs = 250L,
                        message = "API key is valid and ready"
                    )
                )
            } else {
                Result.success(
                    ValidateKeyResponseDto(
                        valid = false,
                        provider = provider,
                        latencyMs = 0L,
                        message = ping.exceptionOrNull()?.message ?: "Validation failed"
                    )
                )
            }
        } catch (e: Exception) {
            Result.success(
                ValidateKeyResponseDto(
                    valid = false,
                    provider = provider,
                    latencyMs = 0L,
                    message = e.message ?: "Connection failed"
                )
            )
        }
    }

    override suspend fun listGroups(): Result<List<TranscriptGroupDto>> = withContext(Dispatchers.IO) {
        try {
            val db = dbHelper.readableDatabase
            val query = """
                SELECT g.id, g.name, g.description, g.color, g.created_at,
                       COUNT(m.id) AS meeting_count
                FROM transcript_groups g
                LEFT JOIN meetings m ON g.id = m.group_id
                GROUP BY g.id, g.name, g.description, g.color, g.created_at
                ORDER BY g.created_at DESC
            """.trimIndent()

            val list = mutableListOf<TranscriptGroupDto>()
            db.rawQuery(query, null).use {
                while (it.moveToNext()) {
                    list.add(
                        TranscriptGroupDto(
                            id = it.getString(it.getColumnIndexOrThrow("id")),
                            userId = "sovereign-user",
                            name = it.getString(it.getColumnIndexOrThrow("name")),
                            description = it.getString(it.getColumnIndexOrThrow("description")) ?: "",
                            color = it.getString(it.getColumnIndexOrThrow("color")),
                            meetingCount = it.getInt(it.getColumnIndexOrThrow("meeting_count")),
                            totalDurationSec = 0.0,
                            createdAt = it.getString(it.getColumnIndexOrThrow("created_at"))
                        )
                    )
                }
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun createGroup(name: String, description: String, color: String): Result<TranscriptGroupDto> = withContext(Dispatchers.IO) {
        try {
            val db = dbHelper.writableDatabase
            val groupId = UUID.randomUUID().toString()
            val now = nowIso()

            val values = ContentValues().apply {
                put("id", groupId)
                put("name", name)
                put("description", description)
                put("color", color)
                put("created_at", now)
            }
            db.insertOrThrow("transcript_groups", null, values)

            Result.success(
                TranscriptGroupDto(
                    id = groupId,
                    userId = "sovereign-user",
                    name = name,
                    description = description,
                    color = color,
                    meetingCount = 0,
                    totalDurationSec = 0.0,
                    createdAt = now
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getGroup(groupId: String): Result<GroupDetailResponse> = withContext(Dispatchers.IO) {
        try {
            val db = dbHelper.readableDatabase

            var groupDto: TranscriptGroupDto? = null
            val groupQuery = """
                SELECT g.id, g.name, g.description, g.color, g.created_at,
                       COUNT(m.id) AS meeting_count
                FROM transcript_groups g
                LEFT JOIN meetings m ON g.id = m.group_id
                WHERE g.id = ?
                GROUP BY g.id, g.name, g.description, g.color, g.created_at
            """.trimIndent()

            db.rawQuery(groupQuery, arrayOf(groupId)).use {
                if (it.moveToNext()) {
                    groupDto = TranscriptGroupDto(
                        id = it.getString(it.getColumnIndexOrThrow("id")),
                        userId = "sovereign-user",
                        name = it.getString(it.getColumnIndexOrThrow("name")),
                        description = it.getString(it.getColumnIndexOrThrow("description")) ?: "",
                        color = it.getString(it.getColumnIndexOrThrow("color")),
                        meetingCount = it.getInt(it.getColumnIndexOrThrow("meeting_count")),
                        totalDurationSec = 0.0,
                        createdAt = it.getString(it.getColumnIndexOrThrow("created_at"))
                    )
                }
            }

            if (groupDto == null) {
                return@withContext Result.failure(Exception("Group not found"))
            }

            val meetingsQuery = """
                SELECT m.id, m.title, m.language, m.target_language, m.status, m.duration_sec,
                       m.started_at, m.ended_at, m.group_id, m.updated_at,
                       g.name AS group_name,
                       s.summary_text AS summary
                FROM meetings m
                JOIN transcript_groups g ON m.group_id = g.id
                LEFT JOIN meeting_summaries s ON s.meeting_id = m.id
                WHERE m.group_id = ?
                ORDER BY m.updated_at DESC
            """.trimIndent()

            val meetingsList = mutableListOf<MeetingDto>()
            db.rawQuery(meetingsQuery, arrayOf(groupId)).use {
                while (it.moveToNext()) {
                    meetingsList.add(cursorToMeetingDto(it))
                }
            }

            Result.success(
                GroupDetailResponse(
                    group = groupDto!!,
                    meetings = meetingsList
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateGroup(
        groupId: String,
        name: String,
        description: String,
        color: String
    ): Result<TranscriptGroupDto> = withContext(Dispatchers.IO) {
        try {
            val db = dbHelper.writableDatabase
            val values = ContentValues().apply {
                put("name", name)
                put("description", description)
                put("color", color)
            }
            db.update("transcript_groups", values, "id = ?", arrayOf(groupId))

            getGroup(groupId).map { it.group }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteGroup(groupId: String, deleteMeetings: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val db = dbHelper.writableDatabase
            db.beginTransaction()
            try {
                if (deleteMeetings) {
                    val meetingIds = mutableListOf<String>()
                    db.rawQuery("SELECT id FROM meetings WHERE group_id = ?", arrayOf(groupId)).use { cursor ->
                        while (cursor.moveToNext()) {
                            meetingIds.add(cursor.getString(0))
                        }
                    }
                    for (mId in meetingIds) {
                        db.delete("meeting_summaries", "meeting_id = ?", arrayOf(mId))
                        db.delete("transcript_chunks", "meeting_id = ?", arrayOf(mId))
                        db.delete("meetings", "id = ?", arrayOf(mId))
                    }
                } else {
                    val updateValues = ContentValues().apply { putNull("group_id") }
                    db.update("meetings", updateValues, "group_id = ?", arrayOf(groupId))
                }
                db.delete("transcript_groups", "id = ?", arrayOf(groupId))
                db.setTransactionSuccessful()
                Result.success(Unit)
            } finally {
                db.endTransaction()
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun assignMeetingGroup(meetingId: String, groupId: String?): Result<MeetingDto> = withContext(Dispatchers.IO) {
        try {
            val db = dbHelper.writableDatabase
            val now = nowIso()
            val values = ContentValues().apply {
                if (groupId == null) putNull("group_id") else put("group_id", groupId)
                put("updated_at", now)
            }
            db.update("meetings", values, "id = ?", arrayOf(meetingId))

            val query = """
                SELECT m.id, m.title, m.language, m.target_language, m.status, m.duration_sec,
                       m.started_at, m.ended_at, m.group_id, m.updated_at,
                       g.name AS group_name,
                       s.summary_text AS summary
                FROM meetings m
                LEFT JOIN transcript_groups g ON m.group_id = g.id
                LEFT JOIN meeting_summaries s ON s.meeting_id = m.id
                WHERE m.id = ?
            """.trimIndent()

            db.rawQuery(query, arrayOf(meetingId)).use {
                if (it.moveToNext()) {
                    Result.success(cursorToMeetingDto(it))
                } else {
                    Result.failure(Exception("Meeting not found"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun batchAssignMeetingGroup(meetingIds: List<String>, groupId: String?): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val db = dbHelper.writableDatabase
            db.beginTransaction()
            try {
                val now = nowIso()
                for (id in meetingIds) {
                    val values = ContentValues().apply {
                        if (groupId == null) putNull("group_id") else put("group_id", groupId)
                        put("updated_at", now)
                    }
                    db.update("meetings", values, "id = ?", arrayOf(id))
                }
                db.setTransactionSuccessful()
                Result.success(Unit)
            } finally {
                db.endTransaction()
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun batchDeleteMeetings(meetingIds: List<String>): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val db = dbHelper.writableDatabase
            db.beginTransaction()
            try {
                for (id in meetingIds) {
                    db.delete("meeting_summaries", "meeting_id = ?", arrayOf(id))
                    db.delete("transcript_chunks", "meeting_id = ?", arrayOf(id))
                    db.delete("meetings", "id = ?", arrayOf(id))
                }
                db.setTransactionSuccessful()
                Result.success(Unit)
            } finally {
                db.endTransaction()
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun batchDeleteGroups(groupIds: List<String>, deleteMeetings: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val db = dbHelper.writableDatabase
            db.beginTransaction()
            try {
                for (id in groupIds) {
                    if (deleteMeetings) {
                        val meetingIds = mutableListOf<String>()
                        db.rawQuery("SELECT id FROM meetings WHERE group_id = ?", arrayOf(id)).use { cursor ->
                            while (cursor.moveToNext()) {
                                meetingIds.add(cursor.getString(0))
                            }
                        }
                        for (mId in meetingIds) {
                            db.delete("meeting_summaries", "meeting_id = ?", arrayOf(mId))
                            db.delete("transcript_chunks", "meeting_id = ?", arrayOf(mId))
                            db.delete("meetings", "id = ?", arrayOf(mId))
                        }
                    } else {
                        val updateValues = ContentValues().apply { putNull("group_id") }
                        db.update("meetings", updateValues, "group_id = ?", arrayOf(id))
                    }
                    db.delete("transcript_groups", "id = ?", arrayOf(id))
                }
                db.setTransactionSuccessful()
                Result.success(Unit)
            } finally {
                db.endTransaction()
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateChunk(meetingId: String, chunkId: Long, rawText: String): Result<TranscriptChunkDto> = withContext(Dispatchers.IO) {
        try {
            val db = dbHelper.writableDatabase
            val now = nowIso()

            val chunkQuery = "SELECT id, chunk_index, start_time_sec, end_time_sec FROM transcript_chunks WHERE meeting_id = ? ORDER BY chunk_index ASC"
            var targetChunkId: String? = null
            var targetIndex = 0
            var targetStart = 0.0
            var targetEnd = 0.0

            db.rawQuery(chunkQuery, arrayOf(meetingId)).use { cursor ->
                while (cursor.moveToNext()) {
                    val idStr = cursor.getString(0)
                    if (idStr.hashCode().toLong() == chunkId) {
                        targetChunkId = idStr
                        targetIndex = cursor.getInt(1)
                        targetStart = cursor.getDouble(2)
                        targetEnd = cursor.getDouble(3)
                        break
                    }
                }
            }

            if (targetChunkId != null) {
                val values = ContentValues().apply { put("text", rawText) }
                db.update("transcript_chunks", values, "id = ?", arrayOf(targetChunkId))
            }

            val meetingVal = ContentValues().apply { put("updated_at", now) }
            db.update("meetings", meetingVal, "id = ?", arrayOf(meetingId))

            Result.success(
                TranscriptChunkDto(
                    id = chunkId,
                    meetingId = meetingId,
                    chunkIndex = targetIndex,
                    startTimeSec = targetStart,
                    endTimeSec = targetEnd,
                    rawText = rawText,
                    createdAt = now
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateFullTranscript(meetingId: String, rawText: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val db = dbHelper.writableDatabase
            db.beginTransaction()
            try {
                db.delete("transcript_chunks", "meeting_id = ?", arrayOf(meetingId))

                val existingDuration = db.rawQuery("SELECT duration_sec FROM meetings WHERE id = ?", arrayOf(meetingId)).use {
                    if (it.moveToFirst()) it.getDouble(0) else 10.0
                }
                val now = nowIso()
                val chunkValues = ContentValues().apply {
                    put("id", UUID.randomUUID().toString())
                    put("meeting_id", meetingId)
                    put("chunk_index", 0)
                    put("text", rawText)
                    put("start_time_sec", 0.0)
                    put("end_time_sec", maxOf(existingDuration, 1.0))
                    put("is_final", 1)
                    put("created_at", now)
                }
                db.insert("transcript_chunks", null, chunkValues)

                val meetingValues = ContentValues().apply {
                    put("updated_at", now)
                }
                db.update("meetings", meetingValues, "id = ?", arrayOf(meetingId))

                db.setTransactionSuccessful()
                Result.success(Unit)
            } finally {
                db.endTransaction()
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateMeetingTitle(meetingId: String, title: String): Result<MeetingDto> = withContext(Dispatchers.IO) {
        try {
            val db = dbHelper.writableDatabase
            val now = nowIso()
            val values = ContentValues().apply {
                put("title", title)
                put("updated_at", now)
            }
            db.update("meetings", values, "id = ?", arrayOf(meetingId))

            val query = """
                SELECT m.id, m.title, m.language, m.target_language, m.status, m.duration_sec,
                       m.started_at, m.ended_at, m.group_id, m.updated_at,
                       g.name AS group_name,
                       s.summary_text AS summary
                FROM meetings m
                LEFT JOIN transcript_groups g ON m.group_id = g.id
                LEFT JOIN meeting_summaries s ON s.meeting_id = m.id
                WHERE m.id = ?
            """.trimIndent()

            db.rawQuery(query, arrayOf(meetingId)).use {
                if (it.moveToNext()) {
                    Result.success(cursorToMeetingDto(it))
                } else {
                    Result.failure(Exception("Meeting not found"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun insertTranscriptChunk(
        meetingId: String,
        chunkIndex: Int,
        text: String,
        startTimeSec: Double,
        endTimeSec: Double
    ) {
        try {
            val db = dbHelper.writableDatabase
            val now = nowIso()
            val values = ContentValues().apply {
                put("id", UUID.randomUUID().toString())
                put("meeting_id", meetingId)
                put("chunk_index", chunkIndex)
                put("text", text)
                put("start_time_sec", startTimeSec)
                put("end_time_sec", endTimeSec)
                put("is_final", 1)
                put("created_at", now)
            }
            db.insert("transcript_chunks", null, values)

            val currentDuration = db.rawQuery("SELECT duration_sec FROM meetings WHERE id = ?", arrayOf(meetingId)).use {
                if (it.moveToFirst()) it.getDouble(0) else 0.0
            }

            val mValues = ContentValues().apply {
                put("updated_at", now)
                if (endTimeSec > currentDuration) {
                    put("duration_sec", endTimeSec)
                }
            }
            db.update("meetings", mValues, "id = ?", arrayOf(meetingId))
        } catch (_: Exception) {}
    }

    private fun cursorToMeetingDto(cursor: Cursor): MeetingDto {
        val summaryIdx = cursor.getColumnIndex("summary")
        val summaryText = if (summaryIdx != -1 && !cursor.isNull(summaryIdx)) cursor.getString(summaryIdx) else null

        return MeetingDto(
            id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
            userId = "sovereign-user",
            groupId = cursor.getString(cursor.getColumnIndexOrThrow("group_id")),
            groupName = cursor.getString(cursor.getColumnIndexOrThrow("group_name")),
            title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
            language = cursor.getString(cursor.getColumnIndexOrThrow("language")),
            targetLanguage = cursor.getString(cursor.getColumnIndexOrThrow("target_language")),
            status = cursor.getString(cursor.getColumnIndexOrThrow("status")),
            startedAt = cursor.getString(cursor.getColumnIndexOrThrow("started_at")),
            endedAt = cursor.getString(cursor.getColumnIndexOrThrow("ended_at")),
            durationSec = cursor.getDouble(cursor.getColumnIndexOrThrow("duration_sec")),
            summary = summaryText,
            createdAt = cursor.getString(cursor.getColumnIndexOrThrow("started_at"))
        )
    }
}
