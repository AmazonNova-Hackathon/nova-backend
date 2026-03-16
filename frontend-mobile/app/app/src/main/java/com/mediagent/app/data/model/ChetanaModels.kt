package com.mediagent.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private val avatarColors = listOf("#7F77DD", "#1D9E75", "#D85A30", "#3B82F6", "#EF4444", "#8B5CF6", "#F59E0B")

@Serializable
data class Member(
    val resourceType: String = "Patient",
    val id: String,
    val name: String,
    val initials: String = "",
    val relationship: String = "",
    val preferredLanguage: String = "en",
    val avatarColor: String = "",
) {
    /** Computed initials — uses explicit value if provided, otherwise derives from name */
    val displayInitials: String
        get() = initials.ifEmpty {
            name.split(" ")
                .filter { it.isNotBlank() }
                .take(2)
                .joinToString("") { it.first().uppercase() }
                .ifEmpty { "?" }
        }

    /** Computed avatar color — uses explicit value if provided, otherwise derives from id hash */
    val displayAvatarColor: String
        get() = avatarColor.ifEmpty {
            avatarColors[kotlin.math.abs(id.hashCode()) % avatarColors.size]
        }
}

@Serializable
data class Observation(
    val resourceType: String = "Observation",
    val id: String = "",
    val name: String = "",
    val loincCode: String = "",
    val value: Double = 0.0,
    val unit: String = "",
    val normalLow: Double = 0.0,
    val normalHigh: Double = 0.0,
    val interpretation: String = "N",
    @SerialName("date") val effectiveDateTime: String = "",
    val reportId: String = "",
)

@Serializable
data class DiagnosticReport(
    val resourceType: String = "DiagnosticReport",
    val reportId: String = "",
    val status: String = "completed",
    val memberId: String = "",
    val memberName: String = "",
    val labName: String = "",
    @SerialName("date") val effectiveDateTime: String = "",
    val totalObservations: Int = 0,
    val abnormalCount: Int = 0,
    val extractionConfidence: Float = 0f,
    val observations: List<Observation> = emptyList(),
    val s3Key: String = "",
)

@Serializable
data class InsightObservation(
    val name: String,
    @SerialName("loincCode") val loincCode: String,
)

@Serializable
data class InsightCard(
    val id: String = "",
    val memberId: String = "",
    val memberName: String = "",
    val severity: String = "informational",
    val title: String = "",
    val summary: String = "",
    val suggestedAction: String = "",
    val read: Boolean = false,
    val generatedAt: String = "",
    val citedObservations: List<String> = emptyList(),
)

@Serializable
data class FollowUp(
    val id: String,
    val memberId: String = "",
    val memberName: String = "",
    val testName: String = "",
    val loincCode: String = "",
    val reason: String = "",
    val suggestedDate: String = "",
    val status: String = "",
)

@Serializable
data class InsightsPayload(
    val familyId: String,
    val unreadCount: Int = 0,
    val total: Int = 0,
    val insights: List<InsightCard> = emptyList(),
)

@Serializable
data class FollowUpsPayload(
    val familyId: String,
    val total: Int = 0,
    @SerialName("followUps") val followUps: List<FollowUp> = emptyList(),
)

@Serializable
data class AuthCredentials(
    val email: String,
    val password: String,
)

@Serializable
data class AuthSuccess(
    val userId: String,
    val email: String,
    val familyId: String,
)

@Serializable
data class AuthPayload(
    val validCredentials: AuthCredentials,
    val loginSuccessResponse: AuthSuccess,
    val otpSuccessResponse: AuthSuccess,
)

@Serializable
data class ChatMessage(
    val role: String,
    val text: String,
    val citedObservations: List<InsightObservation> = emptyList(),
    val sessionId: String? = null,
    val audioUrl: String? = null,
)
