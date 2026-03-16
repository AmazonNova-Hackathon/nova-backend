package com.mediagent.app.ui.family

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mediagent.app.data.model.DiagnosticReport
import com.mediagent.app.data.model.Observation
import com.mediagent.app.presentation.viewmodel.FamilyViewModel
import com.mediagent.app.presentation.viewmodel.InsightsViewModel
import com.mediagent.app.ui.components.InsightCardComponent
import com.mediagent.app.ui.components.MemberAvatar
import com.mediagent.app.ui.theme.*
import com.mediagent.app.util.FhirDateFormatter
import kotlinx.coroutines.launch
import android.util.Log

private val TabTitles = listOf("Reports", "Trends", "Insights", "Follow-ups")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberDetailScreen(
    memberId: String,
    onNavigateBack: () -> Unit,
    onNavigateToReport: (String) -> Unit,
    familyViewModel: FamilyViewModel = hiltViewModel(),
    insightsViewModel: InsightsViewModel = hiltViewModel(),
) {
    val familyState by familyViewModel.state.collectAsState()
    val insightsState by insightsViewModel.state.collectAsState()

    Log.d("MemberDetail", "memberId from route: $memberId, members count: ${familyState.members.size}, reports count: ${familyState.reports.size}")

    // Derive member and reports directly from memberId — no remember to avoid stale state
    val selectedMember by remember {
        derivedStateOf { familyState.members.find { it.id == memberId } }
    }
    val memberReports by remember {
        derivedStateOf { familyState.reports.filter { it.memberId == memberId } }
    }

    Log.d("MemberDetail", "selectedMember: ${selectedMember?.name}, memberReports: ${memberReports.size}")

    val pagerState = rememberPagerState(pageCount = { TabTitles.size })
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                title = {
                    Column {
                        Text(
                            text = selectedMember?.name ?: "",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                        )
                        selectedMember?.let { member ->
                            Text(
                                text = member.relationship,
                                fontSize = 12.sp,
                                color = TextTertiary,
                            )
                        }
                    }
                },
                actions = {
                    selectedMember?.let { member ->
                        MemberAvatar(
                            initials = member.displayInitials,
                            colorHex = member.displayAvatarColor,
                            size = 36.dp,
                            modifier = Modifier.padding(end = 12.dp),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SurfacePrimary,
                ),
            )
        },
        containerColor = SurfaceSecondary,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Tab Row
            TabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = SurfacePrimary,
                contentColor = BrandPurple,
                indicator = { tabPositions ->
                    if (pagerState.currentPage < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = BrandPurple,
                        )
                    }
                },
            ) {
                TabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = {
                            coroutineScope.launch { pagerState.animateScrollToPage(index) }
                        },
                        text = {
                            Text(
                                text = title,
                                fontSize = 13.sp,
                                fontWeight = if (pagerState.currentPage == index) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        },
                        selectedContentColor = BrandPurple,
                        unselectedContentColor = TextTertiary,
                    )
                }
            }

            // Pager content
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                when (page) {
                    0 -> ReportsTab(
                        reports = memberReports,
                        onNavigateToReport = onNavigateToReport,
                    )
                    1 -> TrendsTab(reports = memberReports)
                    2 -> InsightsTab(
                        memberId = memberId,
                        insightsViewModel = insightsViewModel,
                        insightsState = insightsState,
                    )
                    3 -> FollowUpsTab(
                        memberId = memberId,
                        insightsViewModel = insightsViewModel,
                        insightsState = insightsState,
                    )
                }
            }
        }
    }
}

// ──────────────────────────── Reports Tab ────────────────────────────

@Composable
private fun ReportsTab(
    reports: List<DiagnosticReport>,
    onNavigateToReport: (String) -> Unit,
) {
    if (reports.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text("No reports available.", fontSize = 14.sp, color = TextTertiary)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(reports, key = { it.reportId }) { report ->
            ReportCard(report = report, onClick = { onNavigateToReport(report.reportId) })
        }
    }
}

@Composable
private fun ReportCard(
    report: DiagnosticReport,
    onClick: () -> Unit,
) {
    val isComplete = report.status == "completed" || report.status == "ready"
    val allWithinRange = report.abnormalCount == 0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfacePrimary),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = FhirDateFormatter.toDisplay(report.effectiveDateTime),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                )
                Spacer(Modifier.weight(1f))
                if (!isComplete) {
                    Text(
                        text = report.status.replaceFirstChar { it.uppercase() },
                        fontSize = 11.sp,
                        color = AttentionText,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(100.dp))
                            .background(AttentionBg)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                } else if (allWithinRange) {
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
                        text = "${report.abnormalCount} outside range",
                        fontSize = 11.sp,
                        color = HighText,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(100.dp))
                            .background(HighBg)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = report.labName,
                fontSize = 14.sp,
                color = TextSecondary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${report.totalObservations} tests",
                fontSize = 12.sp,
                color = TextTertiary,
            )
        }
    }
}

// ──────────────────────────── Trends Tab ────────────────────────────

/**
 * Groups observations from all reports by LOINC code and draws a simple
 * line chart per analyte using Canvas. Green reference band between
 * normalLow and normalHigh; red dots for abnormal values.
 */
@Composable
private fun TrendsTab(reports: List<DiagnosticReport>) {
    val allObservations = remember(reports) {
        reports.flatMap { it.observations }
    }

    // Group by LOINC code, sorted by observation date within each group
    val groupedByLoinc: Map<String, List<Observation>> = remember(allObservations) {
        allObservations
            .groupBy { it.loincCode }
            .mapValues { (_, obs) -> obs.sortedBy { it.effectiveDateTime } }
            .filter { it.value.size >= 1 }
    }

    if (groupedByLoinc.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text("No trend data available yet.", fontSize = 14.sp, color = TextTertiary)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(groupedByLoinc.entries.toList(), key = { it.key }) { (loincCode, observations) ->
            TrendChartCard(loincCode = loincCode, observations = observations)
        }
    }
}

@Composable
private fun TrendChartCard(
    loincCode: String,
    observations: List<Observation>,
) {
    val first = observations.first()
    val latest = observations.last()
    val previousValue = if (observations.size >= 2) observations[observations.size - 2].value else null
    val trendingUp = previousValue == null || latest.value >= previousValue

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfacePrimary),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: test name, LOINC code, current value + trend
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = first.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary,
                    )
                    Text(
                        text = loincCode,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextTertiary,
                    )
                }
                Text(
                    text = "${latest.value} ${latest.unit}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (latest.interpretation == "N") BrandForest else HighText,
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = if (trendingUp) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                    contentDescription = null,
                    tint = if (latest.interpretation == "N") BrandForest else HighText,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.height(12.dp))

            // Canvas chart
            TrendLineChart(
                observations = observations,
                normalLow = first.normalLow,
                normalHigh = first.normalHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
            )
        }
    }
}

@Composable
private fun TrendLineChart(
    observations: List<Observation>,
    normalLow: Double,
    normalHigh: Double,
    modifier: Modifier = Modifier,
) {
    val values = remember(observations) { observations.map { it.value.toFloat() } }
    val interpretations = remember(observations) { observations.map { it.interpretation } }
    val dateLabels = remember(observations) { observations.map { FhirDateFormatter.toDisplay(it.effectiveDateTime) } }

    // Calculate y-axis range with padding
    val allValues = values + normalLow.toFloat() + normalHigh.toFloat()
    val yMin = remember(allValues) { (allValues.min() - (allValues.max() - allValues.min()) * 0.15f) }
    val yMax = remember(allValues) { (allValues.max() + (allValues.max() - allValues.min()) * 0.15f) }
    val yRange = (yMax - yMin).coerceAtLeast(0.01f)

    val normalBandColor = BrandForestLight
    val lineColor = BrandPurple
    val normalDotColor = BrandForest
    val abnormalDotColor = HighText
    val labelColor = TextTertiary

    Canvas(modifier = modifier) {
        val chartLeft = 0f
        val chartRight = size.width
        val chartTop = 8f
        val chartBottom = size.height - 24f
        val chartHeight = chartBottom - chartTop

        // Draw green reference band
        val bandTop = chartTop + (1f - (normalHigh.toFloat() - yMin) / yRange) * chartHeight
        val bandBottom = chartTop + (1f - (normalLow.toFloat() - yMin) / yRange) * chartHeight
        drawRect(
            color = normalBandColor,
            topLeft = Offset(chartLeft, bandTop.coerceAtLeast(chartTop)),
            size = androidx.compose.ui.geometry.Size(
                chartRight - chartLeft,
                (bandBottom - bandTop).coerceAtLeast(0f),
            ),
        )

        if (values.size == 1) {
            // Single data point: draw a dot in the center
            val cx = size.width / 2f
            val cy = chartTop + (1f - (values[0] - yMin) / yRange) * chartHeight
            val isAbnormal = interpretations[0] != "N"
            drawCircle(
                color = if (isAbnormal) abnormalDotColor else normalDotColor,
                radius = 6f,
                center = Offset(cx, cy),
            )
        } else {
            // Multiple points: draw the line path
            val xStep = (chartRight - chartLeft) / (values.size - 1).coerceAtLeast(1)

            val path = Path()
            val points = values.mapIndexed { i, v ->
                val x = chartLeft + i * xStep
                val y = chartTop + (1f - (v - yMin) / yRange) * chartHeight
                Offset(x, y)
            }

            points.forEachIndexed { index, point ->
                if (index == 0) path.moveTo(point.x, point.y)
                else path.lineTo(point.x, point.y)
            }

            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(width = 2.5f, cap = StrokeCap.Round),
            )

            // Draw data point dots
            points.forEachIndexed { index, point ->
                val isAbnormal = interpretations[index] != "N"
                drawCircle(
                    color = if (isAbnormal) abnormalDotColor else normalDotColor,
                    radius = if (isAbnormal) 6f else 4.5f,
                    center = point,
                )
            }

            // Draw date labels along the bottom
            val paint = android.graphics.Paint().apply {
                color = labelColor.hashCode()
                textSize = 22f
                textAlign = android.graphics.Paint.Align.CENTER
                isAntiAlias = true
            }
            points.forEachIndexed { index, point ->
                if (index == 0 || index == points.lastIndex || (values.size <= 5)) {
                    drawContext.canvas.nativeCanvas.drawText(
                        dateLabels[index].take(6),
                        point.x,
                        size.height,
                        paint,
                    )
                }
            }
        }
    }
}

// ──────────────────────────── Insights Tab ────────────────────────────

@Composable
private fun InsightsTab(
    memberId: String,
    insightsViewModel: InsightsViewModel,
    insightsState: InsightsViewModel.InsightsUiState,
) {
    val memberInsights = remember(insightsState.insights, memberId) {
        insightsState.insights.filter { it.memberId == memberId }
    }

    if (insightsState.loading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = BrandPurple)
        }
        return
    }

    if (memberInsights.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text("No insights for this member yet.", fontSize = 14.sp, color = TextTertiary)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(memberInsights, key = { it.id }) { insight ->
            InsightCardComponent(
                insight = insight,
                onMarkRead = { insightsViewModel.markRead(it) },
            )
        }
    }
}

// ──────────────────────────── Follow-ups Tab ────────────────────────────

@Composable
private fun FollowUpsTab(
    memberId: String,
    insightsViewModel: InsightsViewModel,
    insightsState: InsightsViewModel.InsightsUiState,
) {
    val memberFollowUps = remember(insightsState.followUps, memberId) {
        insightsState.followUps.filter { it.memberId == memberId }
    }

    if (insightsState.loading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = BrandPurple)
        }
        return
    }

    if (memberFollowUps.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text("No follow-ups for this member.", fontSize = 14.sp, color = TextTertiary)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(memberFollowUps, key = { it.id }) { followUp ->
            FollowUpCard(
                testName = followUp.testName,
                loincCode = followUp.loincCode,
                reason = followUp.reason,
                suggestedDate = followUp.suggestedDate,
                status = followUp.status,
                onMarkDone = { insightsViewModel.updateFollowUp(followUp.id, "completed") },
            )
        }
    }
}

@Composable
private fun FollowUpCard(
    testName: String,
    loincCode: String,
    reason: String,
    suggestedDate: String,
    status: String,
    onMarkDone: () -> Unit,
) {
    val isCompleted = status == "completed"
    val statusBg = if (isCompleted) NormalBg else AttentionBg
    val statusText = if (isCompleted) NormalText else AttentionText
    val statusLabel = if (isCompleted) "Completed" else status.replaceFirstChar { it.uppercase() }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfacePrimary),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = testName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = loincCode,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextTertiary,
                    )
                }
                Text(
                    text = statusLabel,
                    fontSize = 11.sp,
                    color = statusText,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .background(statusBg)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = reason,
                fontSize = 13.sp,
                color = TextSecondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Suggested: ${FhirDateFormatter.toDisplay(suggestedDate)}",
                    fontSize = 12.sp,
                    color = TextTertiary,
                )
                Spacer(Modifier.weight(1f))
                if (!isCompleted) {
                    TextButton(
                        onClick = onMarkDone,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    ) {
                        Text("Mark done", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
