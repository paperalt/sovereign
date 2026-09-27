package org.sovereign.app.network

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.*

data class CreateMeetingRequest(
    @SerializedName("title") val title: String,
    @SerializedName("language") val language: String = "id",
    @SerializedName("target_language") val targetLanguage: String = "",
    @SerializedName("group_id") val groupId: String? = null
)

data class MeetingDto(
    @SerializedName("id") val id: String,
    @SerializedName("user_id") val userId: String,
    @SerializedName("group_id") val groupId: String? = null,
    @SerializedName("group_name") val groupName: String? = null,
    @SerializedName("title") val title: String,
    @SerializedName("language") val language: String,
    @SerializedName("target_language") val targetLanguage: String?,
    @SerializedName("status") val status: String,
    @SerializedName("started_at") val startedAt: String,
    @SerializedName("ended_at") val endedAt: String?,
    @SerializedName("duration_sec") val durationSec: Double,
    @SerializedName("summary") val summary: String?,
    @SerializedName("created_at") val createdAt: String
)

data class ActiveMeetingResponse(
    @SerializedName("active") val active: Boolean,
    @SerializedName("meeting") val meeting: MeetingDto?
)

data class MeetingListResponse(
    @SerializedName("meetings") val meetings: List<MeetingDto>,
    @SerializedName("limit") val limit: Int,
    @SerializedName("offset") val offset: Int
)

data class TranscriptChunkDto(
    @SerializedName("id") val id: Long,
    @SerializedName("meeting_id") val meetingId: String,
    @SerializedName("chunk_index") val chunkIndex: Int,
    @SerializedName("start_time_sec") val startTimeSec: Double,
    @SerializedName("end_time_sec") val endTimeSec: Double,
    @SerializedName("raw_text") val rawText: String,
    @SerializedName("created_at") val createdAt: String
)

data class ActionItemDto(
    @SerializedName("task") val task: String,
    @SerializedName("assignee") val assignee: String?,
    @SerializedName("status") val status: String?
)

data class MeetingSummaryDto(
    @SerializedName("id") val id: String,
    @SerializedName("meeting_id") val meetingId: String,
    @SerializedName("executive_summary") val executiveSummary: String,
    @SerializedName("key_points") val keyPoints: List<String>?,
    @SerializedName("action_items") val actionItems: List<ActionItemDto>?,
    @SerializedName("model_used") val modelUsed: String
)

data class FullTranscriptDto(
    @SerializedName("meeting_id") val meetingId: String,
    @SerializedName("title") val title: String,
    @SerializedName("language") val language: String,
    @SerializedName("target_language") val targetLanguage: String?,
    @SerializedName("status") val status: String,
    @SerializedName("started_at") val startedAt: String,
    @SerializedName("ended_at") val endedAt: String?,
    @SerializedName("duration_sec") val durationSec: Double,
    @SerializedName("summary") val summary: String?,
    @SerializedName("structured_summary") val structuredSummary: MeetingSummaryDto?,
    @SerializedName("full_text") val fullText: String,
    @SerializedName("total_chunks") val totalChunks: Int,
    @SerializedName("chunks") val chunks: List<TranscriptChunkDto>
)

data class SearchResultDto(
    @SerializedName("meeting_id") val meetingId: String,
    @SerializedName("meeting_title") val meetingTitle: String,
    @SerializedName("chunk_index") val chunkIndex: Int,
    @SerializedName("start_time_sec") val startTimeSec: Double,
    @SerializedName("end_time_sec") val endTimeSec: Double,
    @SerializedName("raw_text") val rawText: String
)

data class SearchResponse(
    @SerializedName("query") val query: String,
    @SerializedName("results") val results: List<SearchResultDto> = emptyList(),
    @SerializedName("meetings") val meetings: List<MeetingDto> = emptyList(),
    @SerializedName("total") val total: Int = 0
)

data class StatusResponse(
    @SerializedName("status") val status: String,
    @SerializedName("message") val message: String?
)

data class UserQuotaDto(
    @SerializedName("tier") val tier: String,
    @SerializedName("quota_seconds") val quotaSeconds: Int,
    @SerializedName("used_seconds") val usedSeconds: Int,
    @SerializedName("remaining_seconds") val remainingSeconds: Int,
    @SerializedName("remaining_minutes") val remainingMinutes: Int,
    @SerializedName("is_unlimited") val isUnlimited: Boolean
)

data class SubscriptionPlanDto(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("description") val description: String,
    @SerializedName("duration_min") val durationMin: Int,
    @SerializedName("price_idr") val priceIdr: Int,
    @SerializedName("badge") val badge: String?
)

data class PlansResponse(
    @SerializedName("plans") val plans: List<SubscriptionPlanDto>
)

data class TopUpRequest(
    @SerializedName("plan_id") val planId: String
)

data class RedeemRequest(
    @SerializedName("voucher_code") val voucherCode: String
)

data class TopUpResponse(
    @SerializedName("message") val message: String,
    @SerializedName("quota") val quota: UserQuotaDto
)

data class AppVersionDto(
    @SerializedName("latest_version_code") val latestVersionCode: Long,
    @SerializedName("latest_version_name") val latestVersionName: String,
    @SerializedName("min_supported_version_code") val minSupportedVersionCode: Long,
    @SerializedName("download_url") val downloadUrl: String,
    @SerializedName("release_notes") val releaseNotes: String,
    @SerializedName("is_critical") val isCritical: Boolean = false,
    @SerializedName("sha256") val sha256: String? = null
)

data class QuestionSuggestionDto(
    @SerializedName("id") val id: String,
    @SerializedName("question") val question: String,
    @SerializedName("category") val category: String, // clarification, critical_edge_case, practical_impact
    @SerializedName("context_ref") val contextRef: String,
    @SerializedName("thought_starter") val thoughtStarter: String?
)

data class QuestionSuggestionResponseDto(
    @SerializedName("meeting_id") val meetingId: String,
    @SerializedName("window_minutes") val windowMinutes: Int,
    @SerializedName("analyzed_duration_sec") val analyzedDurationSec: Double,
    @SerializedName("word_count") val wordCount: Int,
    @SerializedName("has_sufficient_context") val hasSufficientContext: Boolean,
    @SerializedName("message") val message: String?,
    @SerializedName("suggestions") val suggestions: List<QuestionSuggestionDto>
)

data class SuggestQuestionsRequestDto(
    @SerializedName("window_minutes") val windowMinutes: Int = 0,
    @SerializedName("focus_topic") val focusTopic: String = ""
)

data class TranscriptGroupDto(
    @SerializedName("id") val id: String,
    @SerializedName("user_id") val userId: String,
    @SerializedName("name") val name: String,
    @SerializedName("description") val description: String = "",
    @SerializedName("color") val color: String = "#38BDF8",
    @SerializedName("meeting_count") val meetingCount: Int = 0,
    @SerializedName("total_duration_sec") val totalDurationSec: Double = 0.0,
    @SerializedName("created_at") val createdAt: String = ""
)

data class GroupsListResponse(
    @SerializedName("groups") val groups: List<TranscriptGroupDto>
)

data class GroupDetailResponse(
    @SerializedName("group") val group: TranscriptGroupDto,
    @SerializedName("meetings") val meetings: List<MeetingDto>
)

data class CreateGroupRequestDto(
    @SerializedName("name") val name: String,
    @SerializedName("description") val description: String = "",
    @SerializedName("color") val color: String = "#38BDF8"
)

data class UpdateGroupRequestDto(
    @SerializedName("name") val name: String,
    @SerializedName("description") val description: String = "",
    @SerializedName("color") val color: String = "#38BDF8"
)

data class AssignGroupRequestDto(
    @SerializedName("group_id") val groupId: String?
)

data class BatchGroupRequestDto(
    @SerializedName("meeting_ids") val meetingIds: List<String>,
    @SerializedName("group_id") val groupId: String?
)

data class BatchDeleteMeetingsRequestDto(
    @SerializedName("meeting_ids") val meetingIds: List<String>
)

data class BatchDeleteGroupsRequestDto(
    @SerializedName("group_ids") val groupIds: List<String>,
    @SerializedName("delete_meetings") val deleteMeetings: Boolean = false
)

data class UpdateChunkRequestDto(
    @SerializedName("raw_text") val rawText: String
)

data class UpdateTranscriptRequestDto(
    @SerializedName("raw_text") val rawText: String
)

data class UpdateMeetingRequestDto(
    @SerializedName("title") val title: String
)

data class BatchActionResponseDto(
    @SerializedName("success") val success: Boolean,
    @SerializedName("count") val count: Int? = null,
    @SerializedName("updated_count") val updatedCount: Int? = null,
    @SerializedName("deleted_count") val deletedCount: Int? = null
)

interface MeetingApi {
    @POST("/api/v1/meetings")
    suspend fun createMeeting(@Body request: CreateMeetingRequest): Response<MeetingDto>

    @GET("/api/v1/meetings")
    suspend fun listMeetings(
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0
    ): Response<MeetingListResponse>

    @GET("/api/v1/meetings/active")
    suspend fun getActiveMeeting(): Response<ActiveMeetingResponse>

    @GET("/api/v1/meetings/{id}")
    suspend fun getMeeting(@Path("id") id: String): Response<MeetingDto>

    @PUT("/api/v1/meetings/{id}")
    suspend fun updateMeeting(
        @Path("id") id: String,
        @Body req: UpdateMeetingRequestDto
    ): Response<MeetingDto>

    @GET("/api/v1/meetings/{id}/transcript")
    suspend fun getTranscript(@Path("id") id: String): Response<FullTranscriptDto>

    @PUT("/api/v1/meetings/{id}/chunks/{chunk_id}")
    suspend fun updateChunk(
        @Path("id") meetingId: String,
        @Path("chunk_id") chunkId: Long,
        @Body req: UpdateChunkRequestDto
    ): Response<TranscriptChunkDto>

    @PUT("/api/v1/meetings/{id}/transcript")
    suspend fun updateFullTranscript(
        @Path("id") meetingId: String,
        @Body req: UpdateTranscriptRequestDto
    ): Response<Map<String, String>>

    @GET("/api/v1/meetings/search")
    suspend fun searchMeetings(
        @Query("q") query: String,
        @Query("limit") limit: Int = 20
    ): Response<SearchResponse>

    @POST("/api/v1/meetings/{id}/stop")
    suspend fun stopMeeting(@Path("id") id: String): Response<StatusResponse>

    @POST("/api/v1/meetings/{id}/cancel")
    suspend fun cancelMeeting(@Path("id") id: String): Response<StatusResponse>

    @POST("/api/v1/meetings/{id}/summarize")
    suspend fun summarizeMeeting(@Path("id") id: String): Response<MeetingSummaryDto>

    @DELETE("/api/v1/meetings/{id}")
    suspend fun deleteMeeting(@Path("id") id: String): Response<MessageResponse>

    // Quota & Subscriptions
    @GET("/api/v1/user/quota")
    suspend fun getUserQuota(): Response<UserQuotaDto>

    @GET("/api/v1/subscription/plans")
    suspend fun getPlans(): Response<PlansResponse>

    @POST("/api/v1/subscription/topup")
    suspend fun topUp(@Body req: TopUpRequest): Response<TopUpResponse>

    @POST("/api/v1/subscription/redeem")
    suspend fun redeemVoucher(@Body req: RedeemRequest): Response<TopUpResponse>

    // App Version Check
    @GET("/api/v1/app/version")
    suspend fun checkAppVersion(): Response<AppVersionDto>

    // Question Suggestion Engine
    @POST("/api/v1/meetings/{id}/suggest-questions")
    suspend fun suggestQuestions(
        @Path("id") id: String,
        @Body req: SuggestQuestionsRequestDto
    ): Response<QuestionSuggestionResponseDto>

    // AI Key Live Validation
    @POST("/api/v1/ai/validate-key")
    suspend fun validateAIKey(
        @Body req: ValidateKeyRequestDto
    ): Response<ValidateKeyResponseDto>

    // Transcript Groups & Hierarchy Management
    @GET("/api/v1/groups")
    suspend fun listGroups(): Response<GroupsListResponse>

    @POST("/api/v1/groups")
    suspend fun createGroup(@Body req: CreateGroupRequestDto): Response<TranscriptGroupDto>

    @GET("/api/v1/groups/{id}")
    suspend fun getGroup(@Path("id") id: String): Response<GroupDetailResponse>

    @PUT("/api/v1/groups/{id}")
    suspend fun updateGroup(
        @Path("id") id: String,
        @Body req: UpdateGroupRequestDto
    ): Response<TranscriptGroupDto>

    @DELETE("/api/v1/groups/{id}")
    suspend fun deleteGroup(
        @Path("id") id: String,
        @Query("delete_meetings") deleteMeetings: Boolean = false
    ): Response<MessageResponse>

    @PUT("/api/v1/meetings/{id}/group")
    suspend fun assignMeetingGroup(
        @Path("id") id: String,
        @Body req: AssignGroupRequestDto
    ): Response<MeetingDto>

    @POST("/api/v1/meetings/batch-group")
    suspend fun batchAssignMeetingGroup(@Body req: BatchGroupRequestDto): Response<BatchActionResponseDto>

    @POST("/api/v1/meetings/batch-delete")
    suspend fun batchDeleteMeetings(@Body req: BatchDeleteMeetingsRequestDto): Response<BatchActionResponseDto>

    @POST("/api/v1/groups/batch-delete")
    suspend fun batchDeleteGroups(@Body req: BatchDeleteGroupsRequestDto): Response<BatchActionResponseDto>
}

data class ValidateKeyRequestDto(
    @SerializedName("provider") val provider: String,
    @SerializedName("api_key") val apiKey: String
)

data class ValidateKeyResponseDto(
    @SerializedName("valid") val valid: Boolean,
    @SerializedName("provider") val provider: String,
    @SerializedName("latency_ms") val latencyMs: Long,
    @SerializedName("message") val message: String
)
