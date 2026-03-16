package com.mediagent.app.data.repository

import com.mediagent.app.data.api.AddMemberRequest
import com.mediagent.app.data.api.ChatRequest
import com.mediagent.app.data.api.ChetanaApiService
import com.mediagent.app.data.api.TriggerProcessingRequest
import com.mediagent.app.data.model.AuthPayload
import com.mediagent.app.data.model.ChatMessage
import com.mediagent.app.data.model.DiagnosticReport
import com.mediagent.app.data.model.FollowUp
import com.mediagent.app.data.model.InsightCard
import com.mediagent.app.data.model.InsightObservation
import com.mediagent.app.data.model.Member
import com.mediagent.app.data.model.Observation
import com.mediagent.app.util.MockDataLoader
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

// ── Interfaces ──

interface AuthRepository {
    suspend fun signIn(email: String, password: String): Result<Unit>
    suspend fun verifyOtp(code: String): Result<String>
}

interface FamilyRepository {
    suspend fun getMembers(familyId: String): Result<List<Member>>
    suspend fun getReports(familyId: String, memberId: String): Result<List<DiagnosticReport>>
    suspend fun getReport(reportId: String, familyId: String? = null, memberId: String? = null): Result<DiagnosticReport>
    suspend fun getObservations(familyId: String, memberId: String): Result<List<Observation>>
    suspend fun addMember(familyId: String, name: String, relationship: String): Result<Member>
    suspend fun deleteMember(familyId: String, memberId: String): Result<Unit>
    fun evictReportCache(reportId: String)
}

interface InsightRepository {
    suspend fun getInsights(familyId: String, memberId: String): Result<List<InsightCard>>
    suspend fun markAsRead(insightId: String): Result<Unit>
}

interface FollowUpRepository {
    suspend fun getFollowUps(familyId: String, memberId: String): Result<List<FollowUp>>
    suspend fun updateStatus(familyId: String, memberId: String, followUpId: String, status: String): Result<Unit>
}

interface ChatRepository {
    suspend fun sendMessage(
        familyId: String,
        memberId: String,
        text: String,
        sessionId: String?,
        reportId: String? = null,
        language: String? = null,
        responseFormat: String? = null,
    ): Result<ChatMessage>
}

interface UploadRepository {
    suspend fun getUploadUrl(
        familyId: String,
        memberId: String,
        contentType: String,
    ): Result<UploadUrlInfo>

    suspend fun uploadToS3(url: String, bytes: ByteArray, contentType: String): Result<Unit>

    suspend fun triggerProcessing(
        familyId: String,
        memberId: String,
        s3Key: String,
    ): Result<Unit>

    suspend fun getReportStatus(
        familyId: String,
        memberId: String,
        reportId: String,
    ): Result<String>

    suspend fun deleteReport(
        familyId: String,
        memberId: String,
        reportId: String,
    ): Result<Unit>

    suspend fun getDownloadUrl(
        familyId: String,
        memberId: String,
        reportId: String,
    ): Result<String>
}

data class UploadUrlInfo(
    val url: String,
    val s3Key: String,
    val reportId: String,
)

// ── Mock Auth (no live endpoint for login/OTP) ──

@Singleton
class MockAuthRepository @Inject constructor(
    private val loader: MockDataLoader,
) : AuthRepository {
    private val authPayload by lazy { loader.load<AuthPayload>("mock_auth.json") }

    override suspend fun signIn(email: String, password: String): Result<Unit> {
        delay(1200)
        val valid = authPayload.validCredentials
        return if (email == valid.email && password == valid.password) {
            Result.success(Unit)
        } else {
            Result.failure(Exception("Invalid email or password. Please try again."))
        }
    }

    override suspend fun verifyOtp(code: String): Result<String> {
        delay(1000)
        return if (code.length == 6) {
            Result.success(authPayload.otpSuccessResponse.familyId)
        } else {
            Result.failure(Exception("Incorrect code. Please try again."))
        }
    }
}

// ── Live Family Repository ──

@Singleton
class LiveFamilyRepository @Inject constructor(
    private val api: ChetanaApiService,
) : FamilyRepository {

    private val reportCache = mutableMapOf<String, DiagnosticReport>()

    override suspend fun getMembers(familyId: String): Result<List<Member>> = runCatching {
        api.getMembers(familyId).members
    }

    override suspend fun getReports(familyId: String, memberId: String): Result<List<DiagnosticReport>> = runCatching {
        val reports = api.getReports(familyId, memberId).reports
        reports.forEach { reportCache[it.reportId] = it }
        reports
    }

    override suspend fun getReport(reportId: String, familyId: String?, memberId: String?): Result<DiagnosticReport> {
        reportCache[reportId]?.let { return Result.success(it) }
        // Cache miss — try fetching from API if we have context
        if (familyId != null && memberId != null) {
            return runCatching {
                val reports = api.getReports(familyId, memberId).reports
                reports.forEach { reportCache[it.reportId] = it }
                reports.find { it.reportId == reportId }
                    ?: throw Exception("Report not found.")
            }
        }
        return Result.failure(Exception("Report not found."))
    }

    override suspend fun getObservations(familyId: String, memberId: String): Result<List<Observation>> = runCatching {
        api.getObservations(familyId, memberId).observations
    }

    override suspend fun addMember(familyId: String, name: String, relationship: String): Result<Member> = runCatching {
        api.addMember(familyId, AddMemberRequest(name = name, relationship = relationship))
    }

    override suspend fun deleteMember(familyId: String, memberId: String): Result<Unit> = runCatching {
        api.deleteMember(familyId, memberId)
    }

    override fun evictReportCache(reportId: String) {
        reportCache.remove(reportId)
    }
}

// ── Live Insight Repository ──

@Singleton
class LiveInsightRepository @Inject constructor(
    private val api: ChetanaApiService,
) : InsightRepository {
    private val localReadState = mutableMapOf<String, Boolean>()

    override suspend fun getInsights(familyId: String, memberId: String): Result<List<InsightCard>> = runCatching {
        val insights = api.getInsights(familyId, memberId).insights
        insights.map { it.copy(read = localReadState[it.id] ?: it.read) }
    }

    override suspend fun markAsRead(insightId: String): Result<Unit> {
        localReadState[insightId] = true
        return Result.success(Unit)
    }
}

// ── Live Follow-Up Repository ──

@Singleton
class LiveFollowUpRepository @Inject constructor(
    private val api: ChetanaApiService,
) : FollowUpRepository {
    override suspend fun getFollowUps(familyId: String, memberId: String): Result<List<FollowUp>> = runCatching {
        api.getFollowUps(familyId, memberId).followups
    }

    override suspend fun updateStatus(familyId: String, memberId: String, followUpId: String, status: String): Result<Unit> = runCatching {
        api.updateFollowUp(familyId, memberId, followUpId, com.mediagent.app.data.api.UpdateFollowUpRequest(status = status))
    }
}

// ── Live Chat Repository ──

@Singleton
class LiveChatRepository @Inject constructor(
    private val api: ChetanaApiService,
) : ChatRepository {
    override suspend fun sendMessage(
        familyId: String,
        memberId: String,
        text: String,
        sessionId: String?,
        reportId: String?,
        language: String?,
        responseFormat: String?,
    ): Result<ChatMessage> = runCatching {
        val response = api.chat(familyId, memberId, ChatRequest(message = text, sessionId = sessionId, reportId = reportId, language = language, responseFormat = responseFormat))
        val cited = response.referencedObservations?.map {
            InsightObservation(name = it.name, loincCode = it.loincCode)
        } ?: emptyList()
        ChatMessage(
            role = "assistant",
            text = response.text,
            sessionId = response.sessionId,
            citedObservations = cited,
            audioUrl = response.audioUrl,
        )
    }
}

// ── Live Upload Repository ──

@Singleton
class LiveUploadRepository @Inject constructor(
    private val api: ChetanaApiService,
) : UploadRepository {
    // Plain client without API key interceptor for S3 presigned URL uploads
    private val s3Client = OkHttpClient.Builder().build()

    override suspend fun getUploadUrl(
        familyId: String,
        memberId: String,
        contentType: String,
    ): Result<UploadUrlInfo> = runCatching {
        val response = api.getUploadUrl(familyId, memberId, contentType)
        UploadUrlInfo(url = response.url, s3Key = response.s3Key, reportId = response.reportId)
    }

    override suspend fun uploadToS3(url: String, bytes: ByteArray, contentType: String): Result<Unit> = runCatching {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val body = bytes.toRequestBody(contentType.toMediaType())
            val request = Request.Builder()
                .url(url)
                .put(body)
                .addHeader("Content-Type", contentType)
                .build()
            val response = s3Client.newCall(request).execute()
            if (!response.isSuccessful) {
                val errorBody = response.body?.string()?.take(200) ?: "no body"
                throw Exception("S3 HTTP ${response.code}: $errorBody")
            }
        }
    }

    override suspend fun triggerProcessing(
        familyId: String,
        memberId: String,
        s3Key: String,
    ): Result<Unit> = runCatching {
        api.triggerProcessing(familyId, memberId, TriggerProcessingRequest(s3Key = s3Key))
    }

    override suspend fun getReportStatus(
        familyId: String,
        memberId: String,
        reportId: String,
    ): Result<String> = runCatching {
        api.getReportStatus(familyId, memberId, reportId).status
    }

    override suspend fun deleteReport(
        familyId: String,
        memberId: String,
        reportId: String,
    ): Result<Unit> = runCatching {
        api.deleteReport(familyId, memberId, reportId)
    }

    override suspend fun getDownloadUrl(
        familyId: String,
        memberId: String,
        reportId: String,
    ): Result<String> = runCatching {
        api.getDownloadUrl(familyId, memberId, reportId).url
    }
}

// ── Sample data kept for fallback ──

object SampleData {
    val members = listOf(
        Member(id = "member-demo-001", name = "Rahul Sharma", initials = "RS", relationship = "Self", avatarColor = "#7F77DD"),
        Member(id = "member-demo-002", name = "Sunita Sharma", initials = "SS", relationship = "Mother", avatarColor = "#1D9E75"),
        Member(id = "member-demo-003", name = "Vikram Sharma", initials = "VS", relationship = "Father", avatarColor = "#D85A30"),
    )

    private val rahulObs = listOf(
        Observation(id = "obs-001-01", name = "Glucose (Fasting)", loincCode = "2339-0", value = 110.0, unit = "mg/dL", normalLow = 70.0, normalHigh = 100.0, interpretation = "H", effectiveDateTime = "2024-03-15", reportId = "report-demo-001"),
        Observation(id = "obs-001-02", name = "HbA1c", loincCode = "4548-4", value = 6.2, unit = "%", normalLow = 4.0, normalHigh = 5.6, interpretation = "H", effectiveDateTime = "2024-03-15", reportId = "report-demo-001"),
        Observation(id = "obs-001-03", name = "Total Cholesterol", loincCode = "2093-3", value = 185.0, unit = "mg/dL", normalLow = 0.0, normalHigh = 200.0, interpretation = "N", effectiveDateTime = "2024-03-15", reportId = "report-demo-001"),
        Observation(id = "obs-001-04", name = "LDL Cholesterol", loincCode = "18262-6", value = 112.0, unit = "mg/dL", normalLow = 0.0, normalHigh = 100.0, interpretation = "H", effectiveDateTime = "2024-03-15", reportId = "report-demo-001"),
        Observation(id = "obs-001-05", name = "HDL Cholesterol", loincCode = "2085-9", value = 52.0, unit = "mg/dL", normalLow = 40.0, normalHigh = 60.0, interpretation = "N", effectiveDateTime = "2024-03-15", reportId = "report-demo-001"),
        Observation(id = "obs-001-06", name = "Triglycerides", loincCode = "2571-8", value = 145.0, unit = "mg/dL", normalLow = 0.0, normalHigh = 150.0, interpretation = "N", effectiveDateTime = "2024-03-15", reportId = "report-demo-001"),
        Observation(id = "obs-001-07", name = "Vitamin D (25-OH)", loincCode = "1989-3", value = 28.0, unit = "ng/mL", normalLow = 30.0, normalHigh = 100.0, interpretation = "L", effectiveDateTime = "2024-03-15", reportId = "report-demo-001"),
        Observation(id = "obs-001-08", name = "TSH", loincCode = "3016-3", value = 2.1, unit = "uIU/mL", normalLow = 0.4, normalHigh = 4.0, interpretation = "N", effectiveDateTime = "2024-03-15", reportId = "report-demo-001"),
    )

    val reports = listOf(
        DiagnosticReport(reportId = "report-demo-001", memberId = "member-demo-001", memberName = "Rahul Sharma", labName = "City Diagnostics", effectiveDateTime = "2024-03-15", totalObservations = 8, abnormalCount = 3, extractionConfidence = 0.95f, observations = rahulObs),
        DiagnosticReport(reportId = "report-demo-002", memberId = "member-demo-001", memberName = "Rahul Sharma", labName = "Apollo Labs", effectiveDateTime = "2024-02-10", totalObservations = 6, abnormalCount = 2, extractionConfidence = 0.93f, observations = rahulObs),
        DiagnosticReport(reportId = "report-demo-003", memberId = "member-demo-001", memberName = "Rahul Sharma", labName = "SRL Diagnostics", effectiveDateTime = "2024-01-05", totalObservations = 7, abnormalCount = 1, extractionConfidence = 0.92f, observations = rahulObs),
        DiagnosticReport(reportId = "report-demo-004", memberId = "member-demo-002", memberName = "Sunita Sharma", labName = "Apollo Labs", effectiveDateTime = "2024-02-28", totalObservations = 7, abnormalCount = 2, extractionConfidence = 0.89f, observations = emptyList()),
        DiagnosticReport(reportId = "report-demo-005", memberId = "member-demo-003", memberName = "Vikram Sharma", labName = "City Diagnostics", effectiveDateTime = "2024-01-10", totalObservations = 6, abnormalCount = 0, extractionConfidence = 0.94f, observations = emptyList()),
    )
}
