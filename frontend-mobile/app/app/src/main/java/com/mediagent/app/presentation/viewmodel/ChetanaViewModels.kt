package com.mediagent.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediagent.app.data.model.ChatMessage
import com.mediagent.app.data.model.DiagnosticReport
import com.mediagent.app.data.model.FollowUp
import com.mediagent.app.data.model.InsightCard
import com.mediagent.app.data.model.Member
import com.mediagent.app.data.repository.AuthRepository
import com.mediagent.app.data.repository.ChatRepository
import com.mediagent.app.data.repository.FamilyRepository
import com.mediagent.app.data.repository.FollowUpRepository
import com.mediagent.app.data.repository.InsightRepository
import com.mediagent.app.data.repository.UploadRepository
import com.mediagent.app.session.SessionManager
import com.mediagent.app.util.ChatResponseValidator
import com.mediagent.app.util.SaMDStringHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {
    data class AuthUiState(
        val email: String = "",
        val password: String = "",
        val loading: Boolean = false,
        val error: String = "",
        val otp: String = "",
        val isOtpVerified: Boolean = false,
    )

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    val isLoggedIn = sessionManager.isLoggedIn.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false,
    )

    fun updateEmail(value: String) = _state.update { it.copy(email = value, error = "") }
    fun updatePassword(value: String) = _state.update { it.copy(password = value, error = "") }
    fun updateOtp(value: String) = _state.update { it.copy(otp = value.take(6), error = "") }

    fun fillDemoCredentials() {
        _state.update { it.copy(email = "demo@chetana.health", password = "demo1234", error = "") }
    }

    fun signIn(onSuccess: () -> Unit) {
        viewModelScope.launch {
            val current = _state.value
            if (current.email.isBlank() || current.password.isBlank()) {
                _state.update { it.copy(error = "Please fill in all fields.") }
                return@launch
            }
            _state.update { it.copy(loading = true, error = "") }
            authRepository.signIn(current.email, current.password)
                .onSuccess {
                    _state.update { it.copy(loading = false) }
                    onSuccess()
                }
                .onFailure { ex ->
                    _state.update { it.copy(loading = false, error = ex.message.orEmpty()) }
                }
        }
    }

    fun verifyOtp(onSuccess: () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = "") }
            authRepository.verifyOtp(_state.value.otp)
                .onSuccess { familyId ->
                    sessionManager.saveSession(_state.value.email, familyId)
                    _state.update { it.copy(loading = false, isOtpVerified = true) }
                    onSuccess()
                }
                .onFailure { ex ->
                    _state.update { it.copy(loading = false, error = ex.message.orEmpty()) }
                }
        }
    }
}

@HiltViewModel
class FamilyViewModel @Inject constructor(
    private val familyRepository: FamilyRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {
    data class FamilyUiState(
        val familyId: String = "cf7eb826-c613-4850-9cc0-b960ebfd7f5b",
        val members: List<Member> = emptyList(),
        val reports: List<DiagnosticReport> = emptyList(),
        val selectedMember: Member? = null,
        val memberReports: List<DiagnosticReport> = emptyList(),
        val loading: Boolean = true,
        val error: String = "",
        val reportDetail: DiagnosticReport? = null,
        val reportDetailLoading: Boolean = false,
    )

    private val _state = MutableStateFlow(FamilyUiState())
    val state: StateFlow<FamilyUiState> = _state.asStateFlow()

    init {
        loadFamily()
    }

    fun loadFamily() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = "") }
            val familyId = sessionManager.familyId.first()
            _state.update { it.copy(familyId = familyId) }

            val membersResult = familyRepository.getMembers(familyId)
            val members = membersResult.getOrDefault(emptyList())

            if (members.isEmpty()) {
                _state.update { it.copy(loading = false, error = "No family members found. Add a member to get started.") }
                return@launch
            }

            val allReports = members.flatMap { member ->
                familyRepository.getReports(familyId, member.id).getOrDefault(emptyList())
            }.sortedByDescending { it.effectiveDateTime }

            _state.update {
                it.copy(
                    members = members,
                    reports = allReports,
                    selectedMember = members.firstOrNull(),
                    memberReports = allReports.filter { report -> report.memberId == members.firstOrNull()?.id },
                    loading = false,
                )
            }
        }
    }

    fun selectMember(member: Member) {
        viewModelScope.launch {
            val familyId = _state.value.familyId
            val reports = familyRepository.getReports(familyId, member.id).getOrDefault(
                _state.value.reports.filter { it.memberId == member.id }
            )
            _state.update {
                it.copy(
                    selectedMember = member,
                    memberReports = reports,
                )
            }
        }
    }

    fun addMember(name: String, relationship: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val familyId = _state.value.familyId
            familyRepository.addMember(familyId, name, relationship)
                .onSuccess {
                    loadFamily()
                    onSuccess()
                }
                .onFailure { ex ->
                    _state.update { it.copy(error = "Could not add member. Please try again.") }
                }
        }
    }

    fun deleteMember(memberId: String) {
        viewModelScope.launch {
            val familyId = _state.value.familyId
            familyRepository.deleteMember(familyId, memberId)
                .onSuccess { loadFamily() }
                .onFailure { _state.update { it.copy(error = "Could not remove member.") } }
        }
    }

    fun loadReportDetail(reportId: String) {
        viewModelScope.launch {
            _state.update { it.copy(reportDetailLoading = true, reportDetail = null) }
            val familyId = _state.value.familyId
            val report = _state.value.reports.find { it.reportId == reportId }
            if (report == null) {
                _state.update { it.copy(reportDetailLoading = false) }
                return@launch
            }
            val allObservations = familyRepository.getObservations(familyId, report.memberId)
                .getOrDefault(emptyList())
            // Filter by reportId; if API doesn't return reportId, use all observations
            val reportObs = allObservations.filter { it.reportId == reportId }
            // Deduplicate exact duplicates (same test name + value + unit)
            val observations = (reportObs.ifEmpty { allObservations })
                .distinctBy { "${it.name.lowercase()}_${it.value}_${it.unit.lowercase()}" }
            val enriched = report.copy(
                observations = observations,
                memberName = report.memberName.ifEmpty {
                    _state.value.members.find { it.id == report.memberId }?.name ?: ""
                },
            )
            _state.update { it.copy(reportDetail = enriched, reportDetailLoading = false) }
        }
    }
}

@HiltViewModel
class UploadViewModel @Inject constructor(
    private val familyRepository: FamilyRepository,
    private val uploadRepository: UploadRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    data class UploadUiState(
        val familyId: String = "",
        val memberId: String = "",
        val members: List<Member> = emptyList(),
        val selectedMember: Member? = null,
        val uploading: Boolean = false,
        val reportId: String? = null,
        val s3Key: String? = null,
        val processingStatus: String? = null,
        val currentReport: DiagnosticReport? = null,
        val downloadUrl: String? = null,
        val error: String = "",
    )

    private val _state = MutableStateFlow(UploadUiState())
    val state: StateFlow<UploadUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val familyId = sessionManager.familyId.first()
            _state.update { it.copy(familyId = familyId) }
            val members = familyRepository.getMembers(familyId).getOrDefault(emptyList())
            val first = members.firstOrNull()
            _state.update {
                it.copy(
                    members = members,
                    selectedMember = first,
                    memberId = first?.id.orEmpty(),
                )
            }
        }
    }

    fun selectMember(member: Member) {
        _state.update { it.copy(selectedMember = member, memberId = member.id) }
    }

    fun clearError() {
        _state.update { it.copy(error = "") }
    }

    fun uploadFile(bytes: ByteArray, contentType: String) {
        val current = _state.value
        if (current.familyId.isBlank() || current.memberId.isBlank()) {
            _state.update { it.copy(error = "No member selected. Please select a family member first.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(uploading = true, error = "") }
            android.util.Log.d("Upload", "Starting upload: ${bytes.size} bytes, type=$contentType, member=${current.memberId}")

            // Step 1: Get presigned URL
            val urlResult = uploadRepository.getUploadUrl(current.familyId, current.memberId, contentType)
            val urlInfo = urlResult.getOrElse { ex ->
                android.util.Log.e("Upload", "Step 1 failed: get upload URL", ex)
                _state.update { it.copy(uploading = false, error = "Could not get upload URL: ${ex.message}") }
                return@launch
            }

            // Step 2: Upload to S3
            uploadRepository.uploadToS3(urlInfo.url, bytes, contentType).onFailure { ex ->
                android.util.Log.e("Upload", "Step 2 failed: S3 upload", ex)
                _state.update { it.copy(uploading = false, error = "Upload to storage failed: ${ex.message}") }
                return@launch
            }

            // Step 3: Trigger processing (reportId already obtained from Step 1)
            uploadRepository.triggerProcessing(current.familyId, current.memberId, urlInfo.s3Key).onFailure { ex ->
                android.util.Log.w("Upload", "Step 3 trigger processing: ${ex.message}")
                // Non-fatal — backend may auto-process on S3 event; proceed with reportId from Step 1
            }

            _state.update {
                it.copy(
                    uploading = false,
                    reportId = urlInfo.reportId,
                    s3Key = urlInfo.s3Key,
                )
            }
        }
    }

    fun resetReportId() {
        _state.update { it.copy(reportId = null) }
    }

    fun pollStatus(reportId: String, memberId: String? = null, onComplete: () -> Unit, onError: (() -> Unit)? = null) {
        viewModelScope.launch {
            // Wait for familyId to be initialized from SessionManager
            var familyId = _state.value.familyId
            var waitAttempts = 0
            while (familyId.isBlank() && waitAttempts < 10) {
                delay(200L)
                familyId = _state.value.familyId
                waitAttempts++
            }
            if (familyId.isBlank()) {
                familyId = sessionManager.familyId.first()
                _state.update { it.copy(familyId = familyId) }
            }
            var effectiveMemberId = memberId ?: _state.value.memberId
            if (effectiveMemberId.isBlank()) {
                // memberId may not be loaded yet — wait briefly
                var mWait = 0
                while (effectiveMemberId.isBlank() && mWait < 10) {
                    delay(200L)
                    effectiveMemberId = _state.value.memberId
                    mWait++
                }
            }
            android.util.Log.d("PollStatus", "Starting poll: familyId=$familyId, memberId=$effectiveMemberId, reportId=$reportId")
            var attempts = 0
            var consecutiveErrors = 0
            while (attempts < 30) {
                delay(3000L)
                attempts++
                val result = uploadRepository.getReportStatus(familyId, effectiveMemberId, reportId)
                val status = result.getOrNull()
                if (status != null) {
                    consecutiveErrors = 0
                    android.util.Log.d("PollStatus", "Attempt $attempts: status=$status")
                    _state.update { it.copy(processingStatus = status) }
                    if (status == "completed" || status == "ready") {
                        onComplete()
                        return@launch
                    }
                    if (status == "failed" || status == "error") {
                        _state.update { it.copy(error = "Report processing failed. Please try uploading again.") }
                        onError?.invoke()
                        return@launch
                    }
                } else {
                    val ex = result.exceptionOrNull()
                    consecutiveErrors++
                    android.util.Log.e("PollStatus", "Attempt $attempts failed ($consecutiveErrors consecutive): ${ex?.message}")
                    if (consecutiveErrors >= 5) {
                        _state.update { it.copy(error = "Could not check report status: ${ex?.message}") }
                        onError?.invoke()
                        return@launch
                    }
                }
            }
            // Timeout
            _state.update { it.copy(error = "Processing is taking longer than expected. Please check back later.") }
            onError?.invoke()
        }
    }

    fun loadReport(reportId: String, memberId: String? = null) {
        viewModelScope.launch {
            // Wait for familyId to be initialized
            var familyId = _state.value.familyId
            var waitAttempts = 0
            while (familyId.isBlank() && waitAttempts < 10) {
                delay(200L)
                familyId = _state.value.familyId
                waitAttempts++
            }
            if (familyId.isBlank()) {
                familyId = sessionManager.familyId.first()
            }
            val effectiveMemberId = memberId?.ifEmpty { null }
                ?: _state.value.memberId.ifEmpty { null }
            familyRepository.getReport(
                reportId = reportId,
                familyId = familyId.ifEmpty { null },
                memberId = effectiveMemberId,
            ).onSuccess { report ->
                _state.update { it.copy(currentReport = report) }
            }.onFailure {
                _state.update { it.copy(error = "Could not load report details.") }
            }
        }
    }

    fun deleteReport(reportId: String, onDeleted: () -> Unit) {
        val current = _state.value
        viewModelScope.launch {
            uploadRepository.deleteReport(current.familyId, current.memberId, reportId).onSuccess {
                familyRepository.evictReportCache(reportId)
                onDeleted()
            }.onFailure {
                _state.update { it.copy(error = "Could not delete report.") }
            }
        }
    }

    fun fetchDownloadUrl(reportId: String, memberId: String) {
        val current = _state.value
        viewModelScope.launch {
            _state.update { it.copy(downloadUrl = null) }
            uploadRepository.getDownloadUrl(
                familyId = current.familyId,
                memberId = memberId,
                reportId = reportId,
            ).onSuccess { url ->
                _state.update { it.copy(downloadUrl = url) }
            }
        }
    }
}

@HiltViewModel
class InsightsViewModel @Inject constructor(
    private val insightRepository: InsightRepository,
    private val followUpRepository: FollowUpRepository,
    private val familyRepository: FamilyRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {
    data class InsightsUiState(
        val insights: List<InsightCard> = emptyList(),
        val followUps: List<FollowUp> = emptyList(),
        val loading: Boolean = true,
        val error: String = "",
    )

    private val _state = MutableStateFlow(InsightsUiState())
    val state: StateFlow<InsightsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = "") }
            val familyId = sessionManager.familyId.first()

            val membersResult = familyRepository.getMembers(familyId)
            if (membersResult.isFailure) {
                _state.update { it.copy(loading = false, error = "Could not load insights. Pull down to retry.") }
                return@launch
            }
            val members = membersResult.getOrDefault(emptyList())
            val memberMap = members.associateBy { it.id }
            val allInsights = members.flatMap { member ->
                insightRepository.getInsights(familyId, member.id).getOrDefault(emptyList()).map { insight ->
                    insight.copy(memberName = insight.memberName.ifEmpty { memberMap[insight.memberId]?.name ?: member.name })
                }
            }

            val allFollowUps = members.flatMap { member ->
                followUpRepository.getFollowUps(familyId, member.id).getOrDefault(emptyList()).map { followUp ->
                    followUp.copy(memberName = followUp.memberName.ifEmpty { memberMap[followUp.memberId]?.name ?: member.name })
                }
            }
            _state.update { it.copy(insights = allInsights, followUps = allFollowUps, loading = false) }
        }
    }

    fun markRead(id: String) {
        viewModelScope.launch {
            insightRepository.markAsRead(id)
            refresh()
        }
    }

    fun updateFollowUp(id: String, status: String) {
        viewModelScope.launch {
            val familyId = sessionManager.familyId.first()
            val memberId = _state.value.followUps.find { it.id == id }?.memberId ?: return@launch
            followUpRepository.updateStatus(familyId, memberId, id, status)
            refresh()
        }
    }
}

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val familyRepository: FamilyRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {
    data class ChatUiState(
        val familyId: String = "cf7eb826-c613-4850-9cc0-b960ebfd7f5b",
        val members: List<Member> = emptyList(),
        val member: Member? = null,
        val input: String = "",
        val messages: List<ChatMessage> = emptyList(),
        val typing: Boolean = false,
        val currentSessionId: String? = null,
        val reportId: String? = null,
        val loading: Boolean = true,
        val error: String = "",
        val language: String = "en",
        val voiceEnabled: Boolean = false,
    )

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private val languageCodeToName = mapOf(
        "en" to "English", "hi" to "Hindi", "mr" to "Marathi", "ta" to "Tamil",
        "bn" to "Bengali",
    )

    init {
        viewModelScope.launch {
            val familyId = sessionManager.familyId.first()
            val lang = sessionManager.language.first()
            _state.update { it.copy(familyId = familyId, language = lang) }
            val result = familyRepository.getMembers(familyId)
            val members = result.getOrDefault(emptyList())
            if (members.isEmpty()) {
                val msg = "No family members found. Add a member from the Family tab to start chatting."
                _state.update { it.copy(loading = false, error = msg) }
                return@launch
            }
            _state.update { it.copy(members = members, loading = false) }
            setMember(members.first())
        }
        // Keep language and voice in sync when changed from Settings
        viewModelScope.launch {
            sessionManager.language.collect { lang ->
                _state.update { it.copy(language = lang) }
            }
        }
        viewModelScope.launch {
            sessionManager.voiceEnabled.collect { enabled ->
                _state.update { it.copy(voiceEnabled = enabled) }
            }
        }
    }

    fun setMember(member: Member) {
        _state.update {
            it.copy(
                member = member,
                messages = listOf(
                    ChatMessage(
                        role = "assistant",
                        text = "Hi ${member.name.split(" ").firstOrNull()?.ifEmpty { "there" } ?: "there"}! I can help you understand your lab trends and follow-ups."
                    )
                ),
                input = "",
                currentSessionId = null,
            )
        }
        // Auto-fetch latest report for this member so reportId context is available
        viewModelScope.launch {
            val familyId = _state.value.familyId
            if (familyId.isNotBlank() && _state.value.reportId == null) {
                familyRepository.getReports(familyId, member.id).onSuccess { reports ->
                    val latest = reports.maxByOrNull { it.effectiveDateTime }
                    if (latest != null) {
                        _state.update { it.copy(reportId = latest.reportId) }
                    }
                }
            }
        }
    }

    fun updateInput(value: String) {
        _state.update { it.copy(input = value) }
    }

    fun setReportContext(reportId: String?) {
        _state.update { it.copy(reportId = reportId) }
    }

    fun send() {
        val prompt = _state.value.input.trim()
        if (prompt.isBlank()) return
        val currentState = _state.value
        val member = currentState.member ?: return
        _state.update {
            it.copy(
                input = "",
                typing = true,
                messages = it.messages + ChatMessage(role = "user", text = prompt),
            )
        }
        viewModelScope.launch {
            val langName = languageCodeToName[currentState.language]
            val voiceSupported = currentState.language in listOf("en", "hi")
            val responseFormat = when {
                currentState.voiceEnabled && voiceSupported -> "both"
                else -> "text"
            }
            val message = chatRepository.sendMessage(
                familyId = currentState.familyId,
                memberId = member.id,
                text = prompt,
                sessionId = currentState.currentSessionId,
                reportId = currentState.reportId,
                language = if (langName != null && langName != "English") langName else null,
                responseFormat = responseFormat,
            ).getOrElse {
                ChatMessage(role = "assistant", text = "I'm having trouble connecting right now. Please try again in a moment.")
            }

            // Update sessionId from response
            val newSessionId = message.sessionId ?: currentState.currentSessionId

            val validated = when (val result = ChatResponseValidator.validate(message.text)) {
                is ChatResponseValidator.ValidationResult.Safe -> message.copy(text = result.text)
                is ChatResponseValidator.ValidationResult.Blocked -> message.copy(text = result.fallback)
            }
            _state.update {
                it.copy(
                    typing = false,
                    currentSessionId = newSessionId,
                    messages = it.messages + validated.copy(text = "${validated.text}\n\n${SaMDStringHelper.disclaimerShort}"),
                )
            }
        }
    }
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val sessionManager: SessionManager,
) : ViewModel() {
    val language: StateFlow<String> = sessionManager.language.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = "en",
    )

    val voiceEnabled: StateFlow<Boolean> = sessionManager.voiceEnabled.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false,
    )

    val onboardingComplete: StateFlow<Boolean> = sessionManager.onboardingComplete.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = true,
    )

    fun setLanguage(code: String) {
        viewModelScope.launch { sessionManager.setLanguage(code) }
    }

    fun setVoiceEnabled(enabled: Boolean) {
        viewModelScope.launch { sessionManager.setVoiceEnabled(enabled) }
    }

    fun completeOnboarding() {
        viewModelScope.launch { sessionManager.setOnboardingComplete() }
    }

    fun logout() {
        viewModelScope.launch { sessionManager.clearSession() }
    }
}
