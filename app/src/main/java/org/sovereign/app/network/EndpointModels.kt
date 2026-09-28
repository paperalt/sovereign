package org.sovereign.app.network

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.sovereign.app.auth.TokenStorage
import java.util.UUID

data class STTEndpointConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val providerId: String = "groq",
    val endpoint: String,
    val model: String,
    val apiKey: String = "",
    val adaptiveStreaming: Boolean = true,
    val isDeletable: Boolean = true
)

data class LLMEndpointConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val providerId: String = "groq",
    val endpoint: String,
    val model: String,
    val apiKey: String = "",
    val isDeletable: Boolean = true
)

object EndpointConfigStore {
    private val gson = Gson()
    private val sttListType = object : TypeToken<List<STTEndpointConfig>>() {}.type
    private val llmListType = object : TypeToken<List<LLMEndpointConfig>>() {}.type

    fun loadSTTConfigs(tokenStorage: TokenStorage): List<STTEndpointConfig> {
        val raw = tokenStorage.getSTTConfigsJson()
        val list = if (raw.isBlank()) {
            ensureDefaultSTTConfigs(tokenStorage)
        } else {
            try {
                val parsed = gson.fromJson<List<STTEndpointConfig>>(raw, sttListType)
                if (parsed.isNullOrEmpty()) ensureDefaultSTTConfigs(tokenStorage) else parsed
            } catch (_: Exception) {
                ensureDefaultSTTConfigs(tokenStorage)
            }
        }

        // Hydrate secret API keys from dedicated Keystore vault
        return list.map { cfg ->
            val secretKey = tokenStorage.getEndpointSecretKey(cfg.id).ifBlank {
                if (cfg.apiKey.isNotBlank()) {
                    tokenStorage.setEndpointSecretKey(cfg.id, cfg.apiKey)
                    cfg.apiKey
                } else {
                    tokenStorage.getProviderApiKey(cfg.providerId) ?: ""
                }
            }
            cfg.copy(apiKey = secretKey)
        }
    }

    fun saveSTTConfigs(tokenStorage: TokenStorage, list: List<STTEndpointConfig>) {
        // 1. Isolate and save secret keys in Android Keystore vault
        for (cfg in list) {
            tokenStorage.setEndpointSecretKey(cfg.id, cfg.apiKey)
            if (cfg.apiKey.isNotBlank()) {
                tokenStorage.setProviderApiKey(cfg.providerId, cfg.apiKey)
            }
        }
        // 2. Persist metadata with sanitized empty API key in JSON string
        val sanitized = list.map { it.copy(apiKey = "") }
        tokenStorage.setSTTConfigsJson(gson.toJson(sanitized))
    }

    fun loadLLMConfigs(tokenStorage: TokenStorage): List<LLMEndpointConfig> {
        val raw = tokenStorage.getLLMConfigsJson()
        val list = if (raw.isBlank()) {
            ensureDefaultLLMConfigs(tokenStorage)
        } else {
            try {
                val parsed = gson.fromJson<List<LLMEndpointConfig>>(raw, llmListType)
                if (parsed.isNullOrEmpty()) ensureDefaultLLMConfigs(tokenStorage) else parsed
            } catch (_: Exception) {
                ensureDefaultLLMConfigs(tokenStorage)
            }
        }

        // Hydrate secret API keys from dedicated Keystore vault
        return list.map { cfg ->
            val secretKey = tokenStorage.getEndpointSecretKey(cfg.id).ifBlank {
                if (cfg.apiKey.isNotBlank()) {
                    tokenStorage.setEndpointSecretKey(cfg.id, cfg.apiKey)
                    cfg.apiKey
                } else {
                    tokenStorage.getProviderApiKey(cfg.providerId) ?: ""
                }
            }
            cfg.copy(apiKey = secretKey)
        }
    }

    fun saveLLMConfigs(tokenStorage: TokenStorage, list: List<LLMEndpointConfig>) {
        // 1. Isolate and save secret keys in Android Keystore vault
        for (cfg in list) {
            tokenStorage.setEndpointSecretKey(cfg.id, cfg.apiKey)
            if (cfg.apiKey.isNotBlank()) {
                tokenStorage.setProviderApiKey(cfg.providerId, cfg.apiKey)
            }
        }
        // 2. Persist metadata with sanitized empty API key in JSON string
        val sanitized = list.map { it.copy(apiKey = "") }
        tokenStorage.setLLMConfigsJson(gson.toJson(sanitized))
    }

    fun ensureDefaultSTTConfigs(tokenStorage: TokenStorage): List<STTEndpointConfig> {
        val currentKey = tokenStorage.getSTTKey()
        val defaultList = listOf(
            STTEndpointConfig(
                id = "stt-groq-default",
                name = "Groq Whisper Turbo (Free Tier)",
                providerId = "groq",
                endpoint = "https://api.groq.com/openai/v1/audio/transcriptions",
                model = "whisper-large-v3-turbo",
                apiKey = currentKey,
                adaptiveStreaming = true,
                isDeletable = false
            ),
            STTEndpointConfig(
                id = "stt-gemini-default",
                name = "Google Gemini 2.0 Flash (Free Tier)",
                providerId = "gemini",
                endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent",
                model = "gemini-2.0-flash",
                apiKey = tokenStorage.getProviderApiKey("gemini") ?: "",
                adaptiveStreaming = true,
                isDeletable = true
            ),
            STTEndpointConfig(
                id = "stt-openai-default",
                name = "OpenAI Whisper-1",
                providerId = "openai",
                endpoint = "https://api.openai.com/v1/audio/transcriptions",
                model = "whisper-1",
                apiKey = tokenStorage.getProviderApiKey("openai") ?: "",
                adaptiveStreaming = true,
                isDeletable = true
            ),
            STTEndpointConfig(
                id = "stt-ollama-default",
                name = "Ollama Localhost (Private)",
                providerId = "ollama",
                endpoint = "http://10.0.2.2:11434/v1/audio/transcriptions",
                model = "whisper",
                apiKey = "",
                adaptiveStreaming = false,
                isDeletable = true
            )
        )
        saveSTTConfigs(tokenStorage, defaultList)
        if (tokenStorage.getActiveSTTConfigId().isBlank()) {
            tokenStorage.setActiveSTTConfigId(defaultList.first().id)
        }
        return defaultList
    }

    fun ensureDefaultLLMConfigs(tokenStorage: TokenStorage): List<LLMEndpointConfig> {
        val currentKey = tokenStorage.getLLMKey()
        val defaultList = listOf(
            LLMEndpointConfig(
                id = "llm-groq-default",
                name = "Groq Llama 3.3 70B (Free Tier)",
                providerId = "groq",
                endpoint = "https://api.groq.com/openai/v1/chat/completions",
                model = "llama-3.3-70b-versatile",
                apiKey = currentKey.ifBlank { tokenStorage.getSTTKey() },
                isDeletable = false
            ),
            LLMEndpointConfig(
                id = "llm-gemini-default",
                name = "Google Gemini 2.0 Flash (Free Tier)",
                providerId = "gemini",
                endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent",
                model = "gemini-2.0-flash",
                apiKey = tokenStorage.getProviderApiKey("gemini") ?: "",
                isDeletable = true
            ),
            LLMEndpointConfig(
                id = "llm-openrouter-free-default",
                name = "OpenRouter Free Models Router",
                providerId = "openrouter",
                endpoint = "https://openrouter.ai/api/v1/chat/completions",
                model = "meta-llama/llama-3.3-70b-instruct:free",
                apiKey = tokenStorage.getProviderApiKey("openrouter") ?: "",
                isDeletable = true
            ),
            LLMEndpointConfig(
                id = "llm-deepseek-default",
                name = "DeepSeek Chat (V3)",
                providerId = "deepseek",
                endpoint = "https://api.deepseek.com/chat/completions",
                model = "deepseek-chat",
                apiKey = tokenStorage.getProviderApiKey("deepseek") ?: "",
                isDeletable = true
            ),
            LLMEndpointConfig(
                id = "llm-xai-grok-default",
                name = "xAI Grok 2",
                providerId = "xai",
                endpoint = "https://api.x.ai/v1/chat/completions",
                model = "grok-2-1212",
                apiKey = tokenStorage.getProviderApiKey("xai") ?: "",
                isDeletable = true
            ),
            LLMEndpointConfig(
                id = "llm-openai-default",
                name = "OpenAI GPT-4o-Mini",
                providerId = "openai",
                endpoint = "https://api.openai.com/v1/chat/completions",
                model = "gpt-4o-mini",
                apiKey = tokenStorage.getProviderApiKey("openai") ?: "",
                isDeletable = true
            ),
            LLMEndpointConfig(
                id = "llm-ollama-default",
                name = "Ollama Localhost (Private)",
                providerId = "ollama",
                endpoint = "http://10.0.2.2:11434/v1/chat/completions",
                model = "llama3.2",
                apiKey = "",
                isDeletable = true
            )
        )
        saveLLMConfigs(tokenStorage, defaultList)
        if (tokenStorage.getActiveLLMConfigId().isBlank()) {
            tokenStorage.setActiveLLMConfigId(defaultList.first().id)
        }
        return defaultList
    }

    fun applySTT(tokenStorage: TokenStorage, config: STTEndpointConfig) {
        tokenStorage.setActiveSTTConfigId(config.id)
        tokenStorage.setSTTPresetId(config.providerId)
        tokenStorage.setSTTEndpoint(config.endpoint.trim())
        tokenStorage.setSTTModel(config.model.trim())
        tokenStorage.setSTTKey(config.apiKey.trim())
        if (config.apiKey.isNotBlank()) {
            tokenStorage.setProviderApiKey(config.providerId, config.apiKey.trim())
        }
        tokenStorage.setSTTProvider(config.name)
        tokenStorage.setAdaptiveStreamingBetaEnabled(config.adaptiveStreaming)
    }

    fun applyLLM(tokenStorage: TokenStorage, config: LLMEndpointConfig) {
        tokenStorage.setActiveLLMConfigId(config.id)
        tokenStorage.setLLMPresetId(config.providerId)
        tokenStorage.setLLMEndpoint(config.endpoint.trim())
        tokenStorage.setLLMModel(config.model.trim())
        tokenStorage.setLLMKey(config.apiKey.trim())
        if (config.apiKey.isNotBlank()) {
            tokenStorage.setProviderApiKey(config.providerId, config.apiKey.trim())
        }
        tokenStorage.setLLMProvider(config.name)
    }
}
