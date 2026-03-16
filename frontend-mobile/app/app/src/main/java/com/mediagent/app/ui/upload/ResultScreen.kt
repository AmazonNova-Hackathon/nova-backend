package com.mediagent.app.ui.upload

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mediagent.app.presentation.viewmodel.InsightsViewModel
import com.mediagent.app.presentation.viewmodel.UploadViewModel
import com.mediagent.app.ui.components.*
import com.mediagent.app.ui.theme.*
import com.mediagent.app.util.FhirDateFormatter
import com.mediagent.app.util.SaMDStringHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    reportId: String,
    memberId: String = "",
    onNavigateToChat: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
    uploadViewModel: UploadViewModel = hiltViewModel(),
    insightsViewModel: InsightsViewModel = hiltViewModel(),
) {
    val view = LocalView.current
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(reportId) {
        uploadViewModel.loadReport(reportId, memberId)
        insightsViewModel.refresh()
    }

    val uploadState by uploadViewModel.state.collectAsState()
    val insightsState by insightsViewModel.state.collectAsState()
    val report = uploadState.currentReport

    if (report == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = BrandPurple)
        }
        return
    }

    val observations = remember(report.observations) {
        val priority = mapOf("HH" to 0, "LL" to 1, "H" to 2, "L" to 3, "N" to 4)
        report.observations.sortedBy { priority[it.interpretation] ?: 5 }
    }
    val outsideRange = observations.count { it.interpretation != "N" }
    val withinRange = observations.count { it.interpretation == "N" }
    // Filter insights for this member
    val memberInsights = insightsState.insights.filter { it.memberId == report.memberId }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete report?") },
            text = { Text("This action cannot be undone. All extracted data and insights for this report will be permanently removed.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    uploadViewModel.deleteReport(reportId) { onDelete() }
                }) {
                    Text("Delete", color = HighText)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Report Results", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SurfacePrimary,
                ),
            )
        },
        bottomBar = {
            Column {
                // Sticky bottom buttons
                Surface(
                    tonalElevation = 4.dp,
                    shadowElevation = 8.dp,
                    color = SurfacePrimary,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Button(
                            onClick = {
                                view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                                onNavigateToChat()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BrandPurple,
                                contentColor = Color.White,
                            ),
                        ) {
                            Text(
                                text = "Chat with Nova about report \u2192",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = TextSecondary,
                            ),
                        ) {
                            Text(
                                text = "Delete",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }
                    }
                }

                // Disclaimer bar
                DisclaimerBar(variant = DisclaimerVariant.SHORT)
            }
        },
        containerColor = SurfaceSecondary,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            // Green success banner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BrandForestLight)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Success",
                    tint = BrandForest,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = "\u2713 Report extracted successfully",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = BrandForest,
                    )
                    Text(
                        text = "${report.labName} \u00B7 ${FhirDateFormatter.toDisplay(report.effectiveDateTime)}",
                        fontSize = 12.sp,
                        color = BrandForest.copy(alpha = 0.8f),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // FHIR Patient match banner
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(BrandPurpleLight)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Patient",
                    tint = BrandPurpleMid,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Matched to ${report.memberName}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = BrandPurpleMid,
                )
            }

            Spacer(Modifier.height(16.dp))

            // Section header: Lab results
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = "Lab results \u00B7 FHIR Observation",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "$outsideRange outside range",
                        fontSize = 12.sp,
                        color = HighText,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "$withinRange within range",
                        fontSize = 12.sp,
                        color = NormalText,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Observation rows with stagger animation
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(0.5.dp, Color(0x14000000), RoundedCornerShape(12.dp)),
            ) {
                observations.forEachIndexed { index, obs ->
                    var visible by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        kotlinx.coroutines.delay(index * 80L)
                        visible = true
                    }
                    AnimatedVisibility(
                        visible = visible,
                        enter = fadeIn(animationSpec = tween(300)) +
                            slideInVertically(
                                animationSpec = tween(300),
                                initialOffsetY = { it / 4 },
                            ),
                    ) {
                        ObservationRow(obs = obs)
                    }
                    if (index < observations.lastIndex) {
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = Color(0x14000000),
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // AI insights section header
            Text(
                text = "AI insights",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Spacer(Modifier.height(8.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                if (memberInsights.isEmpty()) {
                    Text(
                        text = "Insights will appear here once processing is complete.",
                        fontSize = 13.sp,
                        color = TextTertiary,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                } else {
                    memberInsights.forEach { insight ->
                        InsightCardComponent(
                            insight = insight,
                            onMarkRead = { insightsViewModel.markRead(it) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Extraction disclaimer card (purple tinted)
            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(BrandPurpleLight)
                    .padding(14.dp),
            ) {
                Text(
                    text = SaMDStringHelper.extractionDisclaimer,
                    fontSize = 12.sp,
                    color = BrandPurpleMid,
                    lineHeight = 18.sp,
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
