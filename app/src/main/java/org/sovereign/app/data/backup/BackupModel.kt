package org.sovereign.app.data.backup

import com.google.gson.annotations.SerializedName
import org.sovereign.app.network.EnginePreset
import org.sovereign.app.network.LLMEndpointConfig
import org.sovereign.app.network.STTEndpointConfig

data class BackupMetadata(
    @SerializedName("app_name") val appName: String = "Sovereign",
    @SerializedName("app_version") val appVersion: String = "0.1.0",
    @SerializedName("schema_version") val schemaVersion: Int = 1,
    @SerializedName("exported_at") val exportedAt: String,
    @SerializedName("device_model") val deviceModel: String = "",
    @SerializedName("includes_api_keys") val includesApiKeys: Boolean = true
)

data class BackupGroup(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("color") val color: String = "#38BDF8",
    @SerializedName("description") val description: String? = "",
    @SerializedName("created_at") val createdAt: String
)

data class BackupMeeting(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("language") val language: String = "id",
    @SerializedName("target_language") val targetLanguage: String? = "",
    @SerializedName("status") val status: String = "COMPLETED",
    @SerializedName("duration_sec") val durationSec: Double = 0.0,
    @SerializedName("started_at") val startedAt: String,
    @SerializedName("ended_at") val endedAt: String? = null,
    @SerializedName("group_id") val groupId: String? = null,
    @SerializedName("updated_at") val updatedAt: String
)

data class BackupChunk(
    @SerializedName("id") val id: String,
    @SerializedName("meeting_id") val meetingId: String,
    @SerializedName("chunk_index") val chunkIndex: Int,
    @SerializedName("text") val text: String,
    @SerializedName("start_time_sec") val startTimeSec: Double,
    @SerializedName("end_time_sec") val endTimeSec: Double,
    @SerializedName("is_final") val isFinal: Int = 1,
    @SerializedName("created_at") val createdAt: String
)

data class BackupSummary(
    @SerializedName("id") val id: String,
    @SerializedName("meeting_id") val meetingId: String,
    @SerializedName("summary_text") val summaryText: String,
    @SerializedName("key_points") val keyPoints: String = "[]",
    @SerializedName("action_items") val actionItems: String = "[]",
    @SerializedName("created_at") val createdAt: String
)

data class BackupSettings(
    @SerializedName("stt_configs") val sttConfigs: List<STTEndpointConfig> = emptyList(),
    @SerializedName("llm_configs") val llmConfigs: List<LLMEndpointConfig> = emptyList(),
    @SerializedName("engine_presets") val enginePresets: List<EnginePreset> = emptyList(),
    @SerializedName("active_stt_id") val activeSttId: String? = null,
    @SerializedName("active_llm_id") val activeLlmId: String? = null,
    @SerializedName("active_engine_preset_id") val activeEnginePresetId: String? = null,
    @SerializedName("custom_presets_url") val customPresetsUrl: String? = null,
    @SerializedName("adaptive_streaming_beta") val adaptiveStreamingBeta: Boolean = true,
    @SerializedName("provider_api_keys") val providerApiKeys: Map<String, String> = emptyMap()
)

data class BackupPayload(
    @SerializedName("metadata") val metadata: BackupMetadata,
    @SerializedName("groups") val groups: List<BackupGroup> = emptyList(),
    @SerializedName("meetings") val meetings: List<BackupMeeting> = emptyList(),
    @SerializedName("chunks") val chunks: List<BackupChunk> = emptyList(),
    @SerializedName("summaries") val summaries: List<BackupSummary> = emptyList(),
    @SerializedName("settings") val settings: BackupSettings? = null
)

data class BackupInventory(
    val groupsCount: Int,
    val meetingsCount: Int,
    val chunksCount: Int,
    val summariesCount: Int,
    val sttConfigsCount: Int,
    val llmConfigsCount: Int,
    val presetsCount: Int
)

data class RestoreSummary(
    val groupsRestored: Int,
    val meetingsRestored: Int,
    val chunksRestored: Int,
    val summariesRestored: Int,
    val sttConfigsRestored: Int,
    val llmConfigsRestored: Int,
    val presetsRestored: Int,
    val settingsRestored: Boolean
)
