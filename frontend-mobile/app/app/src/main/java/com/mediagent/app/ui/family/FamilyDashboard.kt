package com.mediagent.app.ui.family

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mediagent.app.data.model.DiagnosticReport
import com.mediagent.app.data.model.Member
import com.mediagent.app.presentation.viewmodel.FamilyViewModel
import com.mediagent.app.ui.components.MemberAvatar
import com.mediagent.app.ui.theme.*
import com.mediagent.app.util.FhirDateFormatter
import android.util.Log

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyDashboard(
    onNavigateToMember: (String) -> Unit,
    onNavigateToCapture: () -> Unit,
    onNavigateToSettings: () -> Unit,
    familyViewModel: FamilyViewModel = hiltViewModel(),
    settingsViewModel: com.mediagent.app.presentation.viewmodel.SettingsViewModel = hiltViewModel(),
) {
    val state by familyViewModel.state.collectAsState()
    val selectedLanguage by settingsViewModel.language.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {},
                navigationIcon = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 16.dp),
                    ) {
                        Text(
                            text = "\uD83C\uDF3F",
                            fontSize = 22.sp,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "\u091A\u0947\u0924\u0928\u093E",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = BrandDeep,
                        )
                    }
                },
                actions = {
                    AssistChip(
                        onClick = onNavigateToSettings,
                        label = { Text(selectedLanguage.uppercase(), fontSize = 12.sp) },
                        modifier = Modifier.height(28.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = TextSecondary,
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = SurfacePrimary,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToCapture,
                containerColor = BrandPurple,
                contentColor = Color.White,
                shape = CircleShape,
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = "Capture report")
            }
        },
        containerColor = SurfaceSecondary,
    ) { padding ->
        if (state.loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = BrandPurple)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState()),
            ) {
                Spacer(Modifier.height(8.dp))

                // --- Family Health Score Card ---
                FamilyHealthScoreCard(
                    members = state.members,
                    reports = state.reports,
                )

                Spacer(Modifier.height(20.dp))

                // --- Member Scroll Rail ---
                Text(
                    text = "Family Members",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(10.dp))
                MemberScrollRail(
                    members = state.members,
                    reports = state.reports,
                    onMemberClick = onNavigateToMember,
                )

                Spacer(Modifier.height(20.dp))

                // --- Recent Reports ---
                Text(
                    text = "Recent Reports",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(10.dp))
                RecentReportsList(
                    members = state.members,
                    reports = state.reports.filter { it.status == "completed" }.take(3),
                )

                Spacer(Modifier.height(80.dp))
            }
        }
    }
}

@Composable
private fun FamilyHealthScoreCard(
    members: List<Member>,
    reports: List<DiagnosticReport>,
) {
    val totalObservations = reports.sumOf { it.totalObservations }
    val totalAbnormals = reports.sumOf { it.abnormalCount }
    val hasData = totalObservations > 0
    val score = remember(totalObservations, totalAbnormals) {
        if (hasData) {
            (100 - (totalAbnormals.toFloat() / totalObservations * 100)).toInt().coerceIn(0, 100)
        } else {
            0
        }
    }
    val scoreColor = when {
        !hasData -> TextTertiary
        score >= 80 -> BrandForest
        score >= 60 -> AttentionText
        else -> UrgentText
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfacePrimary),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
        ) {
            Text(
                text = "Family Health Score",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = TextSecondary,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = if (hasData) "$score" else "--",
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    color = scoreColor,
                )
                Text(
                    text = " / 100",
                    fontSize = 16.sp,
                    color = TextTertiary,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                Spacer(Modifier.weight(1f))
                if (hasData) {
                    // Trend arrow (compare heuristic: score >= 70 means trending up)
                    Icon(
                        imageVector = if (score >= 70) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                        contentDescription = if (score >= 70) "Trending up" else "Trending down",
                        tint = if (score >= 70) BrandForest else UrgentText,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Based on ${reports.size} reports \u00B7 ${members.size} members",
                fontSize = 12.sp,
                color = TextTertiary,
            )
        }
    }
}

@Composable
private fun MemberScrollRail(
    members: List<Member>,
    reports: List<DiagnosticReport>,
    onMemberClick: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        members.forEach { member ->
            MemberRailCard(
                member = member,
                reports = reports.filter { it.memberId == member.id && it.status == "completed" },
                onClick = {
                    Log.d("FamilyDashboard", "Tapped member: ${member.name} id=${member.id}")
                    onMemberClick(member.id)
                },
            )
        }
    }
}

@Composable
private fun MemberRailCard(
    member: Member,
    reports: List<DiagnosticReport>,
    onClick: () -> Unit,
) {
    val abnormalCount = reports.sumOf { it.abnormalCount }
    val lastReportDate = reports.maxByOrNull { it.effectiveDateTime }?.effectiveDateTime
    val allWithinRange = abnormalCount == 0

    Card(
        modifier = Modifier
            .width(130.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfacePrimary),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MemberAvatar(
                initials = member.displayInitials,
                colorHex = member.displayAvatarColor,
                size = 44.dp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = member.name.split(" ").firstOrNull()?.ifEmpty { member.name } ?: member.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = member.relationship,
                fontSize = 11.sp,
                color = TextTertiary,
                modifier = Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .background(SurfaceTertiary)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
            Spacer(Modifier.height(6.dp))
            if (allWithinRange) {
                Text(
                    text = "All within range",
                    fontSize = 11.sp,
                    color = NormalText,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .background(NormalBg)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            } else {
                Text(
                    text = "$abnormalCount outside range",
                    fontSize = 11.sp,
                    color = HighText,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .background(HighBg)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
            if (lastReportDate != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = FhirDateFormatter.toDisplay(lastReportDate),
                    fontSize = 10.sp,
                    color = TextTertiary,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun RecentReportsList(
    members: List<Member>,
    reports: List<DiagnosticReport>,
) {
    val memberMap = remember(members) { members.associateBy { it.id } }

    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (reports.isEmpty()) {
            Text(
                text = "No reports yet. Tap the camera button to capture your first lab report.",
                fontSize = 13.sp,
                color = TextTertiary,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }
        reports.forEach { report ->
            val member = memberMap[report.memberId]
            RecentReportRow(
                report = report,
                member = member,
            )
        }
    }
}

@Composable
private fun RecentReportRow(
    report: DiagnosticReport,
    member: Member?,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfacePrimary),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (member != null) {
                MemberAvatar(
                    initials = member.displayInitials,
                    colorHex = member.displayAvatarColor,
                    size = 36.dp,
                )
                Spacer(Modifier.width(10.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = member?.name?.split(" ")?.firstOrNull()?.ifEmpty { member.name } ?: report.memberName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${report.labName} \u00B7 ${FhirDateFormatter.toDisplay(report.effectiveDateTime)}",
                    fontSize = 12.sp,
                    color = TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            val isComplete = report.status == "completed" || report.status == "ready"
            if (!isComplete) {
                Text(
                    text = report.status.replaceFirstChar { it.uppercase() },
                    fontSize = 11.sp,
                    color = AttentionText,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .background(AttentionBg)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            } else if (report.abnormalCount > 0) {
                Text(
                    text = "${report.abnormalCount} abnormal",
                    fontSize = 11.sp,
                    color = HighText,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .background(HighBg)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            } else {
                Text(
                    text = "Normal",
                    fontSize = 11.sp,
                    color = NormalText,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .background(NormalBg)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
    }
}
