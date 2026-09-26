package id.eclipsegate.transcribe.network

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
     * Directly transcribes a WAV audio byte array via the selected provider.
     */
    suspend fun transcribeAudio(
        wavBytes: ByteArray,
        language: String,
        provider: String,
        apiKey: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
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

    private fun transcribeGroq(wavBytes: ByteArray, language: String, apiKey: String): Result<String> {
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", "whisper-large-v3-turbo")
            .addFormDataPart("response_format", "json")
            .addFormDataPart("language", if (language.isNotEmpty()) language else "id")
            .addFormDataPart(
                "file",
                "audio.wav",
                wavBytes.toRequestBody("audio/wav".toMediaType())
            )
            .build()

        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/audio/transcriptions")
            .header("Authorization", "Bearer $apiKey")
            .post(requestBody)
            .build()

        return executeTranscriptionRequest(request)
    }

    private fun transcribeOpenAI(wavBytes: ByteArray, language: String, apiKey: String): Result<String> {
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", "whisper-1")
            .addFormDataPart("response_format", "json")
            .addFormDataPart("language", if (language.isNotEmpty()) language else "id")
            .addFormDataPart(
                "file",
                "audio.wav",
                wavBytes.toRequestBody("audio/wav".toMediaType())
            )
            .build()

        val request = Request.Builder()
            .url("https://api.openai.com/v1/audio/transcriptions")
            .header("Authorization", "Bearer $apiKey")
            .post(requestBody)
            .build()

        return executeTranscriptionRequest(request)
    }

    private fun transcribeGemini(wavBytes: ByteArray, language: String, apiKey: String): Result<String> {
        val base64Audio = Base64.encodeToString(wavBytes, Base64.NO_WRAP)
        val prompt = "Transkripsikan audio berikut secara akurat dalam bahasa $language. Berikan HANYA teks transkripsi tanpa basa-basi."

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

        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$apiKey")
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
     * Generates a structured executive summary directly via LLM.
     */
    suspend fun generateSummary(
        fullTranscript: String,
        provider: String,
        apiKey: String
    ): Result<SummaryResult> = withContext(Dispatchers.IO) {
        if (fullTranscript.isBlank()) {
            return@withContext Result.success(SummaryResult("Tidak ada transkrip untuk dirangkum.", emptyList(), emptyList()))
        }

        val systemPrompt = """
            Kamu adalah asisten eksekutif cerdas dan analis rapat profesional.
            Tugasmu adalah menganalisis transkrip percakapan berikut dan menyusun ringkasan eksekutif berbobot tinggi.
            ATURAN MUTLAK:
            1. Jangan berikan teks pembuka atau basa-basi (DILARANG menulis 'Berikut adalah ringkasan...' atau 'Tentu!').
            2. Kembalikan HASIL HANYA dalam format JSON valid dengan struktur:
            {
              "summary": "Teks ringkasan komprehensif dalam beberapa paragraf padat.",
              "key_points": ["Poin utama 1", "Poin utama 2", "Poin utama 3"],
              "action_items": ["Tindakan nyata 1", "Tindakan nyata 2"]
            }
        """.trimIndent()

        try {
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
        provider: String,
        apiKey: String
    ): Result<List<QuestionItem>> = withContext(Dispatchers.IO) {
        if (transcriptContext.isBlank()) {
            return@withContext Result.success(emptyList())
        }

        val systemPrompt = """
            Kamu adalah penasihat strategis dan auditor diskusi profesional.
            Berdasarkan transkrip rapat terkini, buat 3 pertanyaan cerdas, kritis, dan berbobot tinggi untuk diajukan kepada pembicara.
            ATURAN GROUNDING:
            1. Setiap pertanyaan WAJIB memiliki rujukan kalimat asli ('context_ref') yang dikutip verbatim dari transkrip.
            2. Dilarang berhalusinasi atau menanyakan hal di luar konteks yang dibahas.
            3. Kembalikan HANYA format JSON valid tanpa pembuka/penutup markdown:
            [
              {
                "question": "Kalimat pertanyaan tajam",
                "category": "STRATEGIS / OPERASIONAL / TEKNIS",
                "rationale": "Mengapa pertanyaan ini krusial",
                "context_ref": "Kutipan kalimat asli dari transkrip"
              }
            ]
        """.trimIndent()

        val userPrompt = if (focusTopic.isNotBlank()) {
            "Fokus Topik: $focusTopic\n\nTranskrip Diskusi:\n$transcriptContext"
        } else {
            "Transkrip Diskusi:\n$transcriptContext"
        }

        try {
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

    private fun completeChatGroq(
        systemPrompt: String,
        userPrompt: String,
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

        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .post(gson.toJson(payload).toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return Result.failure(IOException("Groq Chat failed (${response.code}): ${response.body?.string()}"))
            }
            val body = response.body?.string() ?: ""
            val jsonObject = gson.fromJson(body, JsonObject::class.java)
            val content = jsonObject.getAsJsonArray("choices")[0].asJsonObject
                .getAsJsonObject("message")
                .get("content").asString
            return Result.success(content)
        }
    }

    private fun completeChatOpenAI(
        systemPrompt: String,
        userPrompt: String,
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

        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .post(gson.toJson(payload).toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return Result.failure(IOException("OpenAI Chat failed (${response.code}): ${response.body?.string()}"))
            }
            val body = response.body?.string() ?: ""
            val jsonObject = gson.fromJson(body, JsonObject::class.java)
            val content = jsonObject.getAsJsonArray("choices")[0].asJsonObject
                .getAsJsonObject("message")
                .get("content").asString
            return Result.success(content)
        }
    }

    private fun completeChatGemini(systemPrompt: String, userPrompt: String, apiKey: String): Result<String> {
        val payload = mapOf(
            "system_instruction" to mapOf("parts" to listOf(mapOf("text" to systemPrompt))),
            "contents" to listOf(
                mapOf("parts" to listOf(mapOf("text" to userPrompt)))
            )
        )

        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$apiKey")
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
