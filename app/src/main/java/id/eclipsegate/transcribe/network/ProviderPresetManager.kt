package id.eclipsegate.transcribe.network

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class ModelEndpointConfig(
    val endpoint: String,
    val defaultModel: String,
    val modelsEndpoint: String
)

data class ProviderPreset(
    val id: String,
    val name: String,
    val badge: String,
    val stt: ModelEndpointConfig,
    val llm: ModelEndpointConfig,
    val apiKeyUrl: String,
    val description: String
)

object ProviderPresetManager {

    private const val GITHUB_RAW_URL = "https://raw.githubusercontent.com/paperalt/sovereign/master/config/providers.json"
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    // Bundled fallback presets available offline out of the box
    private val fallbackPresets = listOf(
        ProviderPreset(
            id = "groq",
            name = "Groq Cloud (LPU Whisper Turbo)",
            badge = "LPU SPEED",
            stt = ModelEndpointConfig(
                endpoint = "https://api.groq.com/openai/v1/audio/transcriptions",
                defaultModel = "whisper-large-v3-turbo",
                modelsEndpoint = "https://api.groq.com/openai/v1/models"
            ),
            llm = ModelEndpointConfig(
                endpoint = "https://api.groq.com/openai/v1/chat/completions",
                defaultModel = "llama-3.3-70b-versatile",
                modelsEndpoint = "https://api.groq.com/openai/v1/models"
            ),
            apiKeyUrl = "https://console.groq.com/keys",
            description = "Latensi STT ~300ms dan penalaran ~1.8s. Kuota gratis harian 8 jam audio."
        ),
        ProviderPreset(
            id = "gemini",
            name = "Google AI Studio (Gemini)",
            badge = "1M CONTEXT",
            stt = ModelEndpointConfig(
                endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent",
                defaultModel = "gemini-2.0-flash",
                modelsEndpoint = "https://generativelanguage.googleapis.com/v1beta/models"
            ),
            llm = ModelEndpointConfig(
                endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent",
                defaultModel = "gemini-2.0-flash",
                modelsEndpoint = "https://generativelanguage.googleapis.com/v1beta/models"
            ),
            apiKeyUrl = "https://aistudio.google.com/app/apikey",
            description = "Konteks masif hingga 1 juta token, cocok untuk rapat panjang berjam-jam."
        ),
        ProviderPreset(
            id = "openai",
            name = "OpenAI Official Platform",
            badge = "INDUSTRY STD",
            stt = ModelEndpointConfig(
                endpoint = "https://api.openai.com/v1/audio/transcriptions",
                defaultModel = "whisper-1",
                modelsEndpoint = "https://api.openai.com/v1/models"
            ),
            llm = ModelEndpointConfig(
                endpoint = "https://api.openai.com/v1/chat/completions",
                defaultModel = "gpt-4o-mini",
                modelsEndpoint = "https://api.openai.com/v1/models"
            ),
            apiKeyUrl = "https://platform.openai.com/api-keys",
            description = "Whisper-1 standar industri dan GPT-4o-Mini untuk akurasi tinggi."
        ),
        ProviderPreset(
            id = "deepseek",
            name = "DeepSeek (LLM Nalar) + Groq STT",
            badge = "REASONING",
            stt = ModelEndpointConfig(
                endpoint = "https://api.groq.com/openai/v1/audio/transcriptions",
                defaultModel = "whisper-large-v3-turbo",
                modelsEndpoint = "https://api.groq.com/openai/v1/models"
            ),
            llm = ModelEndpointConfig(
                endpoint = "https://api.deepseek.com/chat/completions",
                defaultModel = "deepseek-chat",
                modelsEndpoint = "https://api.deepseek.com/models"
            ),
            apiKeyUrl = "https://platform.deepseek.com/api_keys",
            description = "Model penalaran DeepSeek-V3 / R1 dengan harga sangat terjangkau."
        ),
        ProviderPreset(
            id = "openrouter",
            name = "OpenRouter Universal Router",
            badge = "MULTI-MODEL",
            stt = ModelEndpointConfig(
                endpoint = "https://api.groq.com/openai/v1/audio/transcriptions",
                defaultModel = "whisper-large-v3-turbo",
                modelsEndpoint = "https://api.groq.com/openai/v1/models"
            ),
            llm = ModelEndpointConfig(
                endpoint = "https://openrouter.ai/api/v1/chat/completions",
                defaultModel = "meta-llama/llama-3.3-70b-instruct",
                modelsEndpoint = "https://openrouter.ai/api/v1/models"
            ),
            apiKeyUrl = "https://openrouter.ai/keys",
            description = "Akses ke ratusan model open-source dan komersial dengan satu API key."
        ),
        ProviderPreset(
            id = "ollama",
            name = "Ollama / Localhost Server",
            badge = "100% OFFLINE",
            stt = ModelEndpointConfig(
                endpoint = "http://10.0.2.2:11434/v1/audio/transcriptions",
                defaultModel = "whisper",
                modelsEndpoint = "http://10.0.2.2:11434/v1/models"
            ),
            llm = ModelEndpointConfig(
                endpoint = "http://10.0.2.2:11434/v1/chat/completions",
                defaultModel = "llama3.2",
                modelsEndpoint = "http://10.0.2.2:11434/v1/models"
            ),
            apiKeyUrl = "",
            description = "Server lokal Ollama di jaringan LAN atau emulator tanpa koneksi internet."
        ),
        ProviderPreset(
            id = "custom",
            name = "Konfigurasi Universal (Kustom Penuh)",
            badge = "UNIVERSAL",
            stt = ModelEndpointConfig(
                endpoint = "",
                defaultModel = "",
                modelsEndpoint = ""
            ),
            llm = ModelEndpointConfig(
                endpoint = "",
                defaultModel = "",
                modelsEndpoint = ""
            ),
            apiKeyUrl = "",
            description = "Masukkan endpoint API, model, dan API key secara manual sesuai kebutuhan."
        )
    )

    suspend fun loadPresets(forceRemote: Boolean = false): List<ProviderPreset> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(GITHUB_RAW_URL)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val parsed = parsePresetsJson(body)
                    if (parsed.isNotEmpty()) {
                        return@withContext parsed
                    }
                }
            }
        } catch (_: Exception) {}

        fallbackPresets
    }

    fun parsePresetsJson(jsonStr: String): List<ProviderPreset> {
        return try {
            val root = gson.fromJson(jsonStr, JsonObject::class.java)
            val array = root.getAsJsonArray("providers")
            val list = mutableListOf<ProviderPreset>()

            for (elem in array) {
                val obj = elem.asJsonObject
                val sttObj = obj.getAsJsonObject("stt")
                val llmObj = obj.getAsJsonObject("llm")

                list.add(
                    ProviderPreset(
                        id = obj.get("id").asString,
                        name = obj.get("name").asString,
                        badge = obj.get("badge")?.asString ?: "AI",
                        stt = ModelEndpointConfig(
                            endpoint = sttObj.get("endpoint").asString,
                            defaultModel = sttObj.get("default_model").asString,
                            modelsEndpoint = sttObj.get("models_endpoint")?.asString ?: ""
                        ),
                        llm = ModelEndpointConfig(
                            endpoint = llmObj.get("endpoint").asString,
                            defaultModel = llmObj.get("default_model").asString,
                            modelsEndpoint = llmObj.get("models_endpoint")?.asString ?: ""
                        ),
                        apiKeyUrl = obj.get("api_key_url")?.asString ?: "",
                        description = obj.get("description")?.asString ?: ""
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }
}
