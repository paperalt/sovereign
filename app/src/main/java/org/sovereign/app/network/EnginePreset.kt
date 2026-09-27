package org.sovereign.app.network

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.sovereign.app.auth.TokenStorage
import java.util.UUID

data class EnginePreset(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Default Engine",
    val sttPresetId: String = "groq",
    val sttEndpoint: String = "",
    val sttModel: String = "",
    val sttKey: String = "",
    val llmPresetId: String = "groq",
    val llmEndpoint: String = "",
    val llmModel: String = "",
    val llmKey: String = "",
    val adaptiveStreaming: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun sttReady(): Boolean =
        sttEndpoint.isNotBlank() && sttModel.isNotBlank() &&
            (sttKey.isNotBlank() || isLocalEndpoint(sttEndpoint))

    fun llmReady(effectiveLlmKey: String = llmKey): Boolean =
        llmEndpoint.isNotBlank() && llmModel.isNotBlank() &&
            (effectiveLlmKey.isNotBlank() || sttKey.isNotBlank() ||
                isLocalEndpoint(llmEndpoint) || isLocalEndpoint(sttEndpoint))

    fun isComplete(): Boolean = sttReady() && llmReady()

    companion object {
        fun isLocalEndpoint(url: String): Boolean {
            val u = url.lowercase()
            return u.contains("localhost") || u.contains("10.0.2.2") || u.contains("127.0.0.1")
        }
    }
}

object EnginePresetStore {
    private val gson = Gson()
    private val listType = object : TypeToken<List<EnginePreset>>() {}.type

    fun loadAll(tokenStorage: TokenStorage): List<EnginePreset> {
        val raw = tokenStorage.getEnginePresetsJson()
        if (raw.isBlank()) return emptyList()
        return try {
            gson.fromJson<List<EnginePreset>>(raw, listType) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveAll(tokenStorage: TokenStorage, presets: List<EnginePreset>) {
        tokenStorage.setEnginePresetsJson(gson.toJson(presets))
    }

    fun ensureSeeded(tokenStorage: TokenStorage, templates: List<ProviderPreset>): List<EnginePreset> {
        val existing = loadAll(tokenStorage)
        if (existing.isNotEmpty()) return existing
        val groq = templates.firstOrNull { it.id == "groq" }
        val seed = EnginePreset(
            name = "Default Engine",
            sttPresetId = "groq",
            sttEndpoint = tokenStorage.getSTTEndpoint().ifBlank { groq?.stt?.endpoint ?: "" },
            sttModel = tokenStorage.getSTTModel().ifBlank { groq?.stt?.defaultModel ?: "" },
            sttKey = tokenStorage.getSTTKey(),
            llmPresetId = "groq",
            llmEndpoint = tokenStorage.getLLMEndpoint().ifBlank { groq?.llm?.endpoint ?: "" },
            llmModel = tokenStorage.getLLMModel().ifBlank { groq?.llm?.defaultModel ?: "" },
            llmKey = tokenStorage.getLLMKey(),
            adaptiveStreaming = tokenStorage.isAdaptiveStreamingBetaEnabled()
        )
        val list = listOf(seed)
        saveAll(tokenStorage, list)
        if (tokenStorage.getActiveEnginePresetId().isBlank()) {
            tokenStorage.setActiveEnginePresetId(seed.id)
        }
        return list
    }

    fun applyToStorage(tokenStorage: TokenStorage, engine: EnginePreset) {
        tokenStorage.setSTTPresetId(engine.sttPresetId)
        tokenStorage.setLLMPresetId(engine.llmPresetId)
        tokenStorage.setSelectedPreset(engine.id)
        tokenStorage.setSTTEndpoint(engine.sttEndpoint.trim())
        tokenStorage.setSTTModel(engine.sttModel.trim())
        tokenStorage.setSTTKey(engine.sttKey.trim())
        tokenStorage.setLLMEndpoint(engine.llmEndpoint.trim())
        tokenStorage.setLLMModel(engine.llmModel.trim())
        tokenStorage.setLLMKey(engine.llmKey.trim())
        tokenStorage.setSTTProvider(engine.sttPresetId.uppercase())
        tokenStorage.setLLMProvider(engine.llmPresetId.uppercase())
        val label = if (engine.sttPresetId == engine.llmPresetId) engine.name.uppercase()
        else "${engine.name.uppercase()} (${engine.sttPresetId.uppercase()}+${engine.llmPresetId.uppercase()})"
        tokenStorage.setAIProvider(label)
        tokenStorage.setAdaptiveStreamingBetaEnabled(engine.adaptiveStreaming)
        tokenStorage.setActiveEnginePresetId(engine.id)
    }
}
