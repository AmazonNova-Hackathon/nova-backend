package com.mediagent.app.data.api

import com.mediagent.app.data.model.*
import kotlinx.serialization.Serializable
import retrofit2.http.*

interface ChetanaApiService {

    // ── Families ──
    @GET("families")
    suspend fun getFamilies(): FamiliesListResponse

    @POST("families")
    suspend fun createFamily(@Body body: CreateFamilyRequest): FamilyResponse

    @GET("families/{fid}/members")
    suspend fun getMembers(@Path("fid") familyId: String): MembersListResponse

    @POST("families/{fid}/members")
    suspend fun addMember(
        @Path("fid") familyId: String,
        @Body body: AddMemberRequest,
    ): Member

    @DELETE("families/{fid}/members/{mid}")
    suspend fun deleteMember(
        @Path("fid") familyId: String,
        @Path("mid") memberId: String,
    )

    // ── Reports & Uploads ──
    @GET("families/{fid}/members/{mid}/reports/upload-url")
    suspend fun getUploadUrl(
        @Path("fid") familyId: String,
        @Path("mid") memberId: String,
        @Query("contentType") contentType: String = "image/jpeg",
    ): UploadUrlResponse

    @GET("families/{fid}/members/{mid}/reports")
    suspend fun getReports(
        @Path("fid") familyId: String,
        @Path("mid") memberId: String,
    ): ReportsListResponse

    @GET("families/{fid}/members/{mid}/reports/{reportId}/status")
    suspend fun getReportStatus(
        @Path("fid") familyId: String,
        @Path("mid") memberId: String,
        @Path("reportId") reportId: String,
    ): ReportStatusResponse

    @GET("families/{fid}/members/{mid}/reports/{reportId}/download")
    suspend fun getDownloadUrl(
        @Path("fid") familyId: String,
        @Path("mid") memberId: String,
        @Path("reportId") reportId: String,
    ): DownloadUrlResponse

    @DELETE("families/{fid}/members/{mid}/reports/{reportId}")
    suspend fun deleteReport(
        @Path("fid") familyId: String,
        @Path("mid") memberId: String,
        @Path("reportId") reportId: String,
    )

    @POST("families/{fid}/members/{mid}/reports/upload")
    suspend fun triggerProcessing(
        @Path("fid") familyId: String,
        @Path("mid") memberId: String,
        @Body body: TriggerProcessingRequest,
    ): TriggerProcessingResponse

    // ── Observations ──
    @GET("families/{fid}/members/{mid}/observations")
    suspend fun getObservations(
        @Path("fid") familyId: String,
        @Path("mid") memberId: String,
    ): ObservationsListResponse

    // ── Insights ──
    @GET("families/{fid}/members/{mid}/insights")
    suspend fun getInsights(
        @Path("fid") familyId: String,
        @Path("mid") memberId: String,
    ): InsightsListResponse

    // ── Follow-ups ──
    @GET("families/{fid}/members/{mid}/followups")
    suspend fun getFollowUps(
        @Path("fid") familyId: String,
        @Path("mid") memberId: String,
        @Query("status") status: String = "all",
    ): FollowUpsListResponse

    @PATCH("families/{fid}/members/{mid}/followups/{followUpId}")
    suspend fun updateFollowUp(
        @Path("fid") familyId: String,
        @Path("mid") memberId: String,
        @Path("followUpId") followUpId: String,
        @Body body: UpdateFollowUpRequest,
    )

    // ── Chat ──
    @POST("families/{fid}/members/{mid}/chat")
    suspend fun chat(
        @Path("fid") familyId: String,
        @Path("mid") memberId: String,
        @Body body: ChatRequest,
    ): ChatApiResponse
}

// ── Request/Response DTOs ──

@Serializable
data class FamiliesListResponse(
    val families: List<FamilyResponse> = emptyList(),
)

@Serializable
data class FamilyResponse(
    val id: String,
    val name: String? = null,
)

@Serializable
data class MembersListResponse(
    val members: List<Member> = emptyList(),
    val count: Int = 0,
)

@Serializable
data class ReportsListResponse(
    val reports: List<DiagnosticReport> = emptyList(),
    val total: Int = 0,
)

@Serializable
data class ObservationsListResponse(
    val observations: List<Observation> = emptyList(),
)

@Serializable
data class InsightsListResponse(
    val insights: List<InsightCard> = emptyList(),
    val total: Int = 0,
)

@Serializable
data class CreateFamilyRequest(val name: String)

@Serializable
data class AddMemberRequest(
    val name: String,
    val age: Int? = null,
    val gender: String? = null,
    val relationship: String,
)

@Serializable
data class UploadUrlResponse(
    val url: String,
    val s3Key: String,
    val reportId: String,
)

@Serializable
data class ReportStatusResponse(
    val reportId: String? = null,
    val status: String,
)

@Serializable
data class DownloadUrlResponse(
    val url: String,
)

@Serializable
data class TriggerProcessingRequest(
    val s3Key: String,
    val reportType: String = "lab_report",
)

@Serializable
data class TriggerProcessingResponse(
    val reportId: String? = null,
    val status: String? = null,
)

@Serializable
data class ChatRequest(
    val message: String,
    val sessionId: String? = null,
    val reportId: String? = null,
    val language: String? = null,
    val responseFormat: String? = null,
)

@Serializable
data class ChatApiResponse(
    val sessionId: String? = null,
    val response: String? = null,
    val reply: String? = null,
    val referencedObservations: List<ReferencedObservation>? = null,
    val disclaimer: String? = null,
    val audioUrl: String? = null,
) {
    /** Backend may use either "response" or "reply" key */
    val text: String get() = response ?: reply ?: ""
}

@Serializable
data class ReferencedObservation(
    val name: String = "",
    val loincCode: String = "",
    val value: Double? = null,
    val unit: String = "",
)

@Serializable
data class FollowUpsListResponse(
    val followups: List<com.mediagent.app.data.model.FollowUp> = emptyList(),
    val total: Int = 0,
)

@Serializable
data class UpdateFollowUpRequest(
    val status: String,
    val suggestedDate: String? = null,
)

@Serializable
data class ApiErrorBody(
    val error: ApiErrorDetail? = null,
)

@Serializable
data class ApiErrorDetail(
    val code: String = "",
    val message: String = "",
)
