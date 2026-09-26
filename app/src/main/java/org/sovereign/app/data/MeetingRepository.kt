package org.sovereign.app.data

import org.sovereign.app.network.*

interface MeetingRepository {
    suspend fun createMeeting(title: String, language: String = "id", targetLanguage: String = "", groupId: String? = null): Result<MeetingDto>
    suspend fun listMeetings(limit: Int = 20, offset: Int = 0): Result<List<MeetingDto>>
    suspend fun getActiveMeeting(): Result<MeetingDto?>
    suspend fun getTranscript(meetingId: String): Result<FullTranscriptDto>
    suspend fun stopMeeting(meetingId: String): Result<String>
    suspend fun cancelMeeting(meetingId: String): Result<Unit>
    suspend fun summarizeMeeting(meetingId: String): Result<MeetingSummaryDto>
    suspend fun search(query: String): Result<List<SearchResultDto>>
    suspend fun searchFull(query: String): Result<SearchResponse>
    suspend fun deleteMeeting(meetingId: String): Result<Unit>
    suspend fun getUserQuota(): Result<UserQuotaDto>
    suspend fun getPlans(): Result<List<SubscriptionPlanDto>>
    suspend fun topUp(planId: String): Result<UserQuotaDto>
    suspend fun redeemVoucher(code: String): Result<String>
    suspend fun checkAppVersion(): Result<AppVersionDto>
    suspend fun suggestQuestions(meetingId: String, windowMinutes: Int = 0, focusTopic: String = ""): Result<QuestionSuggestionResponseDto>
    suspend fun validateAIKey(provider: String, apiKey: String): Result<ValidateKeyResponseDto>
    
    // Group Management & Hierarchy
    suspend fun listGroups(): Result<List<TranscriptGroupDto>>
    suspend fun createGroup(name: String, description: String = "", color: String = "#38BDF8"): Result<TranscriptGroupDto>
    suspend fun getGroup(groupId: String): Result<GroupDetailResponse>
    suspend fun updateGroup(groupId: String, name: String, description: String = "", color: String = "#38BDF8"): Result<TranscriptGroupDto>
    suspend fun deleteGroup(groupId: String, deleteMeetings: Boolean = false): Result<Unit>
    suspend fun assignMeetingGroup(meetingId: String, groupId: String?): Result<MeetingDto>

    // Batch Operations
    suspend fun batchAssignMeetingGroup(meetingIds: List<String>, groupId: String?): Result<Unit>
    suspend fun batchDeleteMeetings(meetingIds: List<String>): Result<Unit>
    suspend fun batchDeleteGroups(groupIds: List<String>, deleteMeetings: Boolean = false): Result<Unit>

    // Transcript Editing
    suspend fun updateChunk(meetingId: String, chunkId: Long, rawText: String): Result<TranscriptChunkDto>
    suspend fun updateFullTranscript(meetingId: String, rawText: String): Result<Unit>
    suspend fun updateMeetingTitle(meetingId: String, title: String): Result<MeetingDto>
}

class DefaultMeetingRepository(
    private val meetingApi: MeetingApi
) : MeetingRepository {

    override suspend fun createMeeting(title: String, language: String, targetLanguage: String, groupId: String?): Result<MeetingDto> {
        return safeApiCall {
            meetingApi.createMeeting(CreateMeetingRequest(title, language, targetLanguage, groupId))
        }
    }

    override suspend fun listMeetings(limit: Int, offset: Int): Result<List<MeetingDto>> {
        return safeApiCall {
            meetingApi.listMeetings(limit, offset)
        }.map { it.meetings }
    }

    override suspend fun getActiveMeeting(): Result<MeetingDto?> {
        return safeApiCall {
            meetingApi.getActiveMeeting()
        }.map { it.meeting }
    }

    override suspend fun getTranscript(meetingId: String): Result<FullTranscriptDto> {
        return safeApiCall {
            meetingApi.getTranscript(meetingId)
        }
    }

    override suspend fun stopMeeting(meetingId: String): Result<String> {
        return safeApiCall {
            meetingApi.stopMeeting(meetingId)
        }.map { it.status }
    }

    override suspend fun cancelMeeting(meetingId: String): Result<Unit> {
        return safeApiCall {
            meetingApi.cancelMeeting(meetingId)
        }.map { Unit }
    }

    override suspend fun summarizeMeeting(meetingId: String): Result<MeetingSummaryDto> {
        return safeApiCall {
            meetingApi.summarizeMeeting(meetingId)
        }
    }

    override suspend fun search(query: String): Result<List<SearchResultDto>> {
        return searchFull(query).map { it.results }
    }

    override suspend fun searchFull(query: String): Result<SearchResponse> {
        return safeApiCall {
            meetingApi.searchMeetings(query)
        }
    }

    override suspend fun deleteMeeting(meetingId: String): Result<Unit> {
        return safeApiCall {
            meetingApi.deleteMeeting(meetingId)
        }.map { Unit }
    }

    override suspend fun getUserQuota(): Result<UserQuotaDto> {
        return safeApiCall {
            meetingApi.getUserQuota()
        }
    }

    override suspend fun getPlans(): Result<List<SubscriptionPlanDto>> {
        return safeApiCall {
            meetingApi.getPlans()
        }.map { it.plans }
    }

    override suspend fun topUp(planId: String): Result<UserQuotaDto> {
        return safeApiCall {
            meetingApi.topUp(TopUpRequest(planId))
        }.map { it.quota }
    }

    override suspend fun redeemVoucher(code: String): Result<String> {
        return safeApiCall {
            meetingApi.redeemVoucher(RedeemRequest(code))
        }.map { it.message }
    }

    override suspend fun checkAppVersion(): Result<AppVersionDto> {
        return safeApiCall {
            meetingApi.checkAppVersion()
        }
    }

    override suspend fun suggestQuestions(
        meetingId: String,
        windowMinutes: Int,
        focusTopic: String
    ): Result<QuestionSuggestionResponseDto> {
        return safeApiCall {
            meetingApi.suggestQuestions(meetingId, SuggestQuestionsRequestDto(windowMinutes, focusTopic))
        }
    }

    override suspend fun validateAIKey(provider: String, apiKey: String): Result<ValidateKeyResponseDto> {
        return safeApiCall {
            meetingApi.validateAIKey(ValidateKeyRequestDto(provider, apiKey))
        }
    }

    override suspend fun listGroups(): Result<List<TranscriptGroupDto>> {
        return safeApiCall {
            meetingApi.listGroups()
        }.map { it.groups }
    }

    override suspend fun createGroup(name: String, description: String, color: String): Result<TranscriptGroupDto> {
        return safeApiCall {
            meetingApi.createGroup(CreateGroupRequestDto(name, description, color))
        }
    }

    override suspend fun getGroup(groupId: String): Result<GroupDetailResponse> {
        return safeApiCall {
            meetingApi.getGroup(groupId)
        }
    }

    override suspend fun updateGroup(groupId: String, name: String, description: String, color: String): Result<TranscriptGroupDto> {
        return safeApiCall {
            meetingApi.updateGroup(groupId, UpdateGroupRequestDto(name, description, color))
        }
    }

    override suspend fun deleteGroup(groupId: String, deleteMeetings: Boolean): Result<Unit> {
        return safeApiCall {
            meetingApi.deleteGroup(groupId, deleteMeetings)
        }.map { Unit }
    }

    override suspend fun assignMeetingGroup(meetingId: String, groupId: String?): Result<MeetingDto> {
        return safeApiCall {
            meetingApi.assignMeetingGroup(meetingId, AssignGroupRequestDto(groupId))
        }
    }

    override suspend fun batchAssignMeetingGroup(meetingIds: List<String>, groupId: String?): Result<Unit> {
        return safeApiCall {
            meetingApi.batchAssignMeetingGroup(BatchGroupRequestDto(meetingIds, groupId))
        }.map { Unit }
    }

    override suspend fun batchDeleteMeetings(meetingIds: List<String>): Result<Unit> {
        return safeApiCall {
            meetingApi.batchDeleteMeetings(BatchDeleteMeetingsRequestDto(meetingIds))
        }.map { Unit }
    }

    override suspend fun batchDeleteGroups(groupIds: List<String>, deleteMeetings: Boolean): Result<Unit> {
        return safeApiCall {
            meetingApi.batchDeleteGroups(BatchDeleteGroupsRequestDto(groupIds, deleteMeetings))
        }.map { Unit }
    }

    override suspend fun updateChunk(meetingId: String, chunkId: Long, rawText: String): Result<TranscriptChunkDto> {
        return safeApiCall {
            meetingApi.updateChunk(meetingId, chunkId, UpdateChunkRequestDto(rawText))
        }
    }

    override suspend fun updateFullTranscript(meetingId: String, rawText: String): Result<Unit> {
        return safeApiCall {
            meetingApi.updateFullTranscript(meetingId, UpdateTranscriptRequestDto(rawText))
        }.map { Unit }
    }

    override suspend fun updateMeetingTitle(meetingId: String, title: String): Result<MeetingDto> {
        return safeApiCall {
            meetingApi.updateMeeting(meetingId, UpdateMeetingRequestDto(title))
        }
    }

    private suspend fun <T> safeApiCall(call: suspend () -> retrofit2.Response<T>): Result<T> {
        return try {
            val response = call()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Permintaan gagal (${response.code()})"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
