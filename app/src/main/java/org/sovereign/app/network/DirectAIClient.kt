package org.sovereign.app.network

import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

data class SummaryResult(
    val summaryText: String,
    val keyPoints: List<String>,
    val actionItems: List<ActionItemDto>
)

data class QuestionItem(
    val question: String,
    val category: String,
    val rationale: String,
    val contextRef: String
)

class DirectAIClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build(),
    private val gson: Gson = Gson()
) {

    /**
     * Directly transcribes a WAV audio byte array via the selected provider or universal endpoint.
     */
    suspend fun transcribeAudio(
        wavBytes: ByteArray,
        language: String,
        provider: String = "groq",
        apiKey: String,
        customEndpoint: String? = null,
        customModel: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!customEndpoint.isNullOrBlank()) {
                if (customEndpoint.contains("generativelanguage.googleapis.com")) {
                    return@withContext transcribeGemini(wavBytes, language, apiKey, customEndpoint)
                }
                val model = if (!customModel.isNullOrBlank()) customModel else "whisper-large-v3-turbo"
                return@withContext transcribeOpenAICompatible(wavBytes, language, customEndpoint, model, apiKey)
            }

            when (provider.lowercase()) {
                "groq" -> transcribeGroq(wavBytes, language, apiKey)
                "openai" -> transcribeOpenAI(wavBytes, language, apiKey)
                "gemini" -> transcribeGemini(wavBytes, language, apiKey)
                else -> transcribeGroq(wavBytes, language, apiKey)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun transcribeOpenAICompatible(
        wavBytes: ByteArray,
        language: String,
        endpoint: String,
        model: String,
        apiKey: String
    ): Result<String> {
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", model)
            .addFormDataPart("response_format", "json")
            .addFormDataPart("language", if (language.isNotEmpty()) language else "id")
            .addFormDataPart(
                "file",
                "audio.wav",
                wavBytes.toRequestBody("audio/wav".toMediaType())
            )
            .build()

        val reqBuilder = Request.Builder()
            .url(endpoint)
            .post(requestBody)

        if (apiKey.isNotBlank()) {
            reqBuilder.header("Authorization", "Bearer $apiKey")
        }

        return executeTranscriptionRequest(reqBuilder.build())
    }

    private fun transcribeGroq(wavBytes: ByteArray, language: String, apiKey: String): Result<String> {
        return transcribeOpenAICompatible(
            wavBytes,
            language,
            "https://api.groq.com/openai/v1/audio/transcriptions",
            "whisper-large-v3-turbo",
            apiKey
        )
    }

    private fun transcribeOpenAI(wavBytes: ByteArray, language: String, apiKey: String): Result<String> {
        return transcribeOpenAICompatible(
            wavBytes,
            language,
            "https://api.openai.com/v1/audio/transcriptions",
            "whisper-1",
            apiKey
        )
    }

    private fun transcribeGemini(
        wavBytes: ByteArray,
        language: String,
        apiKey: String,
        endpoint: String = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"
    ): Result<String> {
        val base64Audio = Base64.encodeToString(wavBytes, Base64.NO_WRAP)
        val prompt = "Transcribe the following audio accurately in $language. Output ONLY the raw transcript text without preamble or commentary."

        val json = """
            {
              "contents": [
                {
                  "parts": [
                    {"text": "$prompt"},
                    {
                      "inline_data": {
                        "mime_type": "audio/wav",
                        "data": "$base64Audio"
                      }
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val fullUrl = if (endpoint.contains("key=")) endpoint else {
            val sep = if (endpoint.contains("?")) "&" else "?"
            "$endpoint${sep}key=$apiKey"
        }

        val request = Request.Builder()
            .url(fullUrl)
            .post(json.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return Result.failure(IOException("Gemini STT failed with code ${response.code}: ${response.body?.string()}"))
            }
            val bodyString = response.body?.string() ?: ""
            val jsonObject = gson.fromJson(bodyString, JsonObject::class.java)
            val candidates = jsonObject.getAsJsonArray("candidates")
            if (candidates != null && candidates.size() > 0) {
                val text = candidates[0].asJsonObject
                    .getAsJsonObject("content")
                    .getAsJsonArray("parts")[0].asJsonObject
                    .get("text").asString
                return Result.success(text.trim())
            }
            return Result.success("")
        }
    }

    private fun executeTranscriptionRequest(request: Request): Result<String> {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errBody = response.body?.string() ?: "Unknown error"
                return Result.failure(IOException("Transcription failed (${response.code}): $errBody"))
            }
            val bodyString = response.body?.string() ?: ""
            val jsonObject = gson.fromJson(bodyString, JsonObject::class.java)
            val text = jsonObject.get("text")?.asString ?: ""
            return Result.success(text.trim())
        }
    }

    /**
     * Generates a structured executive summary directly via LLM or universal endpoint.
     */
    suspend fun generateSummary(
        fullTranscript: String,
        provider: String = "groq",
        apiKey: String,
        customEndpoint: String? = null,
        customModel: String? = null
    ): Result<SummaryResult> = withContext(Dispatchers.IO) {
        if (fullTranscript.isBlank()) {
            return@withContext Result.success(SummaryResult("No transcript available to summarize.", emptyList(), emptyList()))
        }

        val systemPrompt = """
            You are an elite executive assistant and meeting intelligence analyst.
            Analyze the discussion transcript and generate a high-impact executive summary in the same language as the transcript (match the transcript's language).
            ABSOLUTE RULES:
            1. Do not provide conversational filler or preamble (NEVER write 'Here is the summary' or 'Sure!').
            2. Return ONLY valid JSON with this exact schema:
            {
              "summary": "Comprehensive summary text in concise, structured paragraphs.",
              "key_points": ["Key Point 1", "Key Point 2", "Key Point 3"],
              "action_items": ["Action Item 1", "Action Item 2"]
            }
        """.trimIndent()

        try {
            if (!customEndpoint.isNullOrBlank()) {
                if (customEndpoint.contains("generativelanguage.googleapis.com")) {
                    return@withContext completeChatGemini(systemPrompt, fullTranscript, apiKey, customEndpoint).map { parseSummaryJson(it) }
                }
                val model = if (!customModel.isNullOrBlank()) customModel else "llama-3.3-70b-versatile"
                return@withContext completeChatOpenAICompatible(systemPrompt, fullTranscript, customEndpoint, model, apiKey).map { parseSummaryJson(it) }
            }

            when (provider.lowercase()) {
                "groq" -> completeChatGroq(systemPrompt, fullTranscript, "llama-3.3-70b-versatile", apiKey)
                "openai" -> completeChatOpenAI(systemPrompt, fullTranscript, "gpt-4o-mini", apiKey)
                "gemini" -> completeChatGemini(systemPrompt, fullTranscript, apiKey)
                else -> completeChatGroq(systemPrompt, fullTranscript, "llama-3.3-70b-versatile", apiKey)
            }.map { jsonString ->
                parseSummaryJson(jsonString)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Generates grounded in-meeting questions based on recent transcript context.
     */
    suspend fun suggestQuestions(
        transcriptContext: String,
        focusTopic: String,
        provider: String = "groq",
        apiKey: String,
        customEndpoint: String? = null,
        customModel: String? = null
    ): Result<List<QuestionItem>> = withContext(Dispatchers.IO) {
        if (transcriptContext.isBlank()) {
            return@withContext Result.success(emptyList())
        }

        val systemPrompt = """
            You are a strategic advisor and professional meeting facilitator.
            Based on the provided transcript, generate 3 sharp, critical, high-impact inquiry questions to ask the speaker. Use the same language as the transcript.
            GROUNDING RULES:
            1. Every question MUST include a verbatim quote ('context_ref') from the transcript proving the factual basis.
            2. Zero hallucinations. Do not ask about topics outside the transcript context.
            3. Return ONLY valid JSON with this structure:
            [
              {
                "question": "Sharp analytical question",
                "category": "STRATEGIC / OPERATIONAL / TECHNICAL",
                "rationale": "Why this inquiry is critical",
                "context_ref": "Direct verbatim quote from transcript"
              }
            ]
        """.trimIndent()

        val userPrompt = if (focusTopic.isNotBlank()) {
            "Focus Topic: $focusTopic\n\nDiscussion Transcript:\n$transcriptContext"
        } else {
            "Discussion Transcript:\n$transcriptContext"
        }

        try {
            if (!customEndpoint.isNullOrBlank()) {
                if (customEndpoint.contains("generativelanguage.googleapis.com")) {
                    return@withContext completeChatGemini(systemPrompt, userPrompt, apiKey, customEndpoint).map { parseQuestionsJson(it) }
                }
                val model = if (!customModel.isNullOrBlank()) customModel else "llama-3.3-70b-versatile"
                return@withContext completeChatOpenAICompatible(systemPrompt, userPrompt, customEndpoint, model, apiKey, temperature = 0.2).map { parseQuestionsJson(it) }
            }

            when (provider.lowercase()) {
                "groq" -> completeChatGroq(systemPrompt, userPrompt, "llama-3.3-70b-versatile", apiKey, temperature = 0.2)
                "openai" -> completeChatOpenAI(systemPrompt, userPrompt, "gpt-4o-mini", apiKey, temperature = 0.2)
                "gemini" -> completeChatGemini(systemPrompt, userPrompt, apiKey)
                else -> completeChatGroq(systemPrompt, userPrompt, "llama-3.3-70b-versatile", apiKey, temperature = 0.2)
            }.map { jsonString ->
                parseQuestionsJson(jsonString)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Pings an endpoint via /models and measures round-trip latency in milliseconds.
     */
    suspend fun pingEndpoint(endpointUrl: String, apiKey: String): Result<Long> = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val res = fetchModels(endpointUrl, apiKey)
        val latency = System.currentTimeMillis() - start
        if (res.isSuccess) {
            Result.success(latency)
        } else {
            Result.failure(res.exceptionOrNull() ?: IOException("Connection failed"))
        }
    }

    /**
     * Auto-detects models from any standard /models endpoint.
     */
    suspend fun fetchModels(endpointOrBaseUrl: String, apiKey: String): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val url = resolveModelsUrl(endpointOrBaseUrl, apiKey)
            val reqBuilder = Request.Builder().url(url)
            if (apiKey.isNotBlank() && !url.contains("key=")) {
                reqBuilder.header("Authorization", "Bearer $apiKey")
            }

            client.newCall(reqBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    val err = response.body?.string() ?: ""
                    return@withContext Result.failure(IOException("Model detection failed (${response.code}): $err"))
                }
                val body = response.body?.string() ?: ""
                val models = parseModelsList(body)
                if (models.isEmpty()) {
                    return@withContext Result.failure(IOException("No models found at this endpoint."))
                }
                Result.success(models)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun resolveModelsUrl(inputUrl: String, apiKey: String): String {
        var base = inputUrl.trim().trimEnd('/')
        if (base.contains("generativelanguage.googleapis.com")) {
            val cleanBase = "https://generativelanguage.googleapis.com/v1beta/models"
            return if (apiKey.isNotBlank()) "$cleanBase?key=$apiKey" else cleanBase
        }

        if (base.endsWith("/chat/completions")) {
            base = base.removeSuffix("/chat/completions")
        } else if (base.endsWith("/audio/transcriptions")) {
            base = base.removeSuffix("/audio/transcriptions")
        }

        return if (base.endsWith("/models")) base else "$base/models"
    }

    private fun parseModelsList(body: String): List<String> {
        val list = mutableListOf<String>()
        try {
            val root = gson.fromJson(body, JsonObject::class.java)
            if (root.has("data") && root.get("data").isJsonArray) {
                for (item in root.getAsJsonArray("data")) {
                    val id = item.asJsonObject.get("id")?.asString
                    if (!id.isNullOrBlank()) list.add(id)
                }
            } else if (root.has("models") && root.get("models").isJsonArray) {
                for (item in root.getAsJsonArray("models")) {
                    val obj = item.asJsonObject
                    val name = obj.get("name")?.asString ?: obj.get("id")?.asString
                    if (!name.isNullOrBlank()) {
                        list.add(name.removePrefix("models/"))
                    }
                }
            }
        } catch (_: Exception) {}
        return list.sorted()
    }

    private fun completeChatOpenAICompatible(
        systemPrompt: String,
        userPrompt: String,
        endpoint: String,
        model: String,
        apiKey: String,
        temperature: Double = 0.5
    ): Result<String> {
        val messages = listOf(
            mapOf("role" to "system", "content" to systemPrompt),
            mapOf("role" to "user", "content" to userPrompt)
        )
        val payload = mapOf(
            "model" to model,
            "messages" to messages,
            "temperature" to temperature
        )

        val reqBuilder = Request.Builder()
            .url(endpoint)
            .post(gson.toJson(payload).toRequestBody("application/json".toMediaType()))

        if (apiKey.isNotBlank()) {
            reqBuilder.header("Authorization", "Bearer $apiKey")
        }

        client.newCall(reqBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                return Result.failure(IOException("Chat completion failed (${response.code}): ${response.body?.string()}"))
            }
            val body = response.body?.string() ?: ""
            val jsonObject = gson.fromJson(body, JsonObject::class.java)
            val content = jsonObject.getAsJsonArray("choices")[0].asJsonObject
                .getAsJsonObject("message")
                .get("content").asString
            return Result.success(content)
        }
    }

    private fun completeChatGroq(
        systemPrompt: String,
        userPrompt: String,
        model: String,
        apiKey: String,
        temperature: Double = 0.5
    ): Result<String> {
        return completeChatOpenAICompatible(
            systemPrompt,
            userPrompt,
            "https://api.groq.com/openai/v1/chat/completions",
            model,
            apiKey,
            temperature
        )
    }

    private fun completeChatOpenAI(
        systemPrompt: String,
        userPrompt: String,
        model: String,
        apiKey: String,
        temperature: Double = 0.5
    ): Result<String> {
        return completeChatOpenAICompatible(
            systemPrompt,
            userPrompt,
            "https://api.openai.com/v1/chat/completions",
            model,
            apiKey,
            temperature
        )
    }

    private fun completeChatGemini(
        systemPrompt: String,
        userPrompt: String,
        apiKey: String,
        endpoint: String = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"
    ): Result<String> {
        val payload = mapOf(
            "system_instruction" to mapOf("parts" to listOf(mapOf("text" to systemPrompt))),
            "contents" to listOf(
                mapOf("parts" to listOf(mapOf("text" to userPrompt)))
            )
        )

        val fullUrl = if (endpoint.contains("key=")) endpoint else {
            val sep = if (endpoint.contains("?")) "&" else "?"
            "$endpoint${sep}key=$apiKey"
        }

        val request = Request.Builder()
            .url(fullUrl)
            .post(gson.toJson(payload).toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return Result.failure(IOException("Gemini Chat failed (${response.code}): ${response.body?.string()}"))
            }
            val body = response.body?.string() ?: ""
            val jsonObject = gson.fromJson(body, JsonObject::class.java)
            val candidates = jsonObject.getAsJsonArray("candidates")
            if (candidates != null && candidates.size() > 0) {
                val content = candidates[0].asJsonObject
                    .getAsJsonObject("content")
                    .getAsJsonArray("parts")[0].asJsonObject
                    .get("text").asString
                return Result.success(content)
            }
            return Result.success("")
        }
    }

    private fun parseSummaryJson(rawText: String): SummaryResult {
        val clean = cleanJsonMarkdown(rawText)
        return try {
            val obj = gson.fromJson(clean, JsonObject::class.java)
            val summary = obj.get("summary")?.asString ?: rawText
            val keyPoints = obj.getAsJsonArray("key_points")?.map { it.asString } ?: emptyList()
            val rawActionItems = obj.getAsJsonArray("action_items")?.map { it.asString } ?: emptyList()
            val actionItems = rawActionItems.map { ActionItemDto(task = it, assignee = null, status = "PENDING") }
            SummaryResult(summary, keyPoints, actionItems)
        } catch (_: Exception) {
            SummaryResult(rawText, emptyList(), emptyList())
        }
    }

    private fun parseQuestionsJson(rawText: String): List<QuestionItem> {
        val clean = cleanJsonMarkdown(rawText)
        return try {
            val array = gson.fromJson(clean, com.google.gson.JsonArray::class.java)
            array.map { elem ->
                val obj = elem.asJsonObject
                QuestionItem(
                    question = obj.get("question")?.asString ?: "",
                    category = obj.get("category")?.asString ?: "STRATEGIS",
                    rationale = obj.get("rationale")?.asString ?: "",
                    contextRef = obj.get("context_ref")?.asString ?: ""
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun cleanJsonMarkdown(raw: String): String {
        var str = raw.trim()
        val firstBrace = str.indexOf('{')
        val lastBrace = str.lastIndexOf('}')
        val firstBracket = str.indexOf('[')
        val lastBracket = str.lastIndexOf(']')

        if (firstBracket != -1 && lastBracket > firstBracket && (firstBrace == -1 || firstBracket < firstBrace)) {
            return str.substring(firstBracket, lastBracket + 1).trim()
        }
        if (firstBrace != -1 && lastBrace > firstBrace) {
            return str.substring(firstBrace, lastBrace + 1).trim()
        }
        if (str.startsWith("```json")) {
            str = str.removePrefix("```json")
        } else if (str.startsWith("```")) {
            str = str.removePrefix("```")
        }
        if (str.endsWith("```")) {
            str = str.removeSuffix("```")
        }
        return str.trim()
    }
}
