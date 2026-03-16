package com.mediagent.app.ui.report

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.hilt.navigation.compose.hiltViewModel
import com.mediagent.app.data.model.DiagnosticReport
import com.mediagent.app.presentation.viewmodel.FamilyViewModel
import com.mediagent.app.presentation.viewmodel.UploadViewModel
import com.mediagent.app.ui.components.ConfidenceBadge
import com.mediagent.app.ui.components.DisclaimerBar
import com.mediagent.app.ui.components.DisclaimerVariant
import com.mediagent.app.ui.components.ObservationRow
import com.mediagent.app.ui.theme.*
import com.mediagent.app.util.FhirDateFormatter
import com.mediagent.app.util.SaMDStringHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportDetailScreen(
    reportId: String,
    onNavigateBack: () -> Unit,
    onNavigateToChat: () -> Unit,
    onDelete: () -> Unit,
    familyViewModel: FamilyViewModel = hiltViewModel(),
    uploadViewModel: UploadViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val familyState by familyViewModel.state.collectAsState()

    var showDeleteConfirm by remember { mutableStateOf(false) }

    val uploadState by uploadViewModel.state.collectAsState()

    // Trigger full report loading (with observations) once the summary report is available
    val reportExists = familyState.reports.any { it.reportId == reportId }
    LaunchedEffect(reportId, reportExists) {
        if (reportExists) {
            familyViewModel.loadReportDetail(reportId)
        }
    }

    val report: DiagnosticReport? = familyState.reportDetail?.takeIf { it.reportId == reportId }

    // Fetch download URL when report is available
    LaunchedEffect(report?.reportId, report?.memberId) {
        if (report != null && report.memberId.isNotEmpty()) {
            uploadViewModel.fetchDownloadUrl(report.reportId, report.memberId)
        }
    }

    val downloadUrl = uploadState.downloadUrl

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
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                title = {
                    Text(
                        text = "Report detail",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                    )
                },
                actions = {
                    IconButton(onClick = {
                        if (report != null) {
                            val shareText = buildString {
                                append("Lab Report: ${report.labName}\n")
                                append("Date: ${FhirDateFormatter.toDisplay(report.effectiveDateTime)}\n")
                                append("Patient: ${report.memberName}\n")
                                append("Tests: ${report.totalObservations}, Abnormal: ${report.abnormalCount}\n")
                                append("\nGenerated by Chetana Health")
                            }
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share report summary"))
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            tint = TextSecondary,
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
                    color = SurfacePrimary,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = onNavigateToChat,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BrandPurple,
                                contentColor = Color.White,
                            ),
                            contentPadding = PaddingValues(vertical = 14.dp),
                        ) {
                            Text(
                                text = "Chat with Nova about report",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }

                        OutlinedButton(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            border = ButtonDefaults.outlinedButtonBorder(enabled = true),
                            contentPadding = PaddingValues(vertical = 14.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                tint = HighText,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Delete",
                                fontSize = 15.sp,
                                color = HighText,
                            )
                        }
                    }
                }

                // Disclaimer bar
                DisclaimerBar(variant = DisclaimerVariant.SHORT)
            }
        },
        containerColor = SurfaceSecondary,
    ) { padding ->
        if (report == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = BrandPurple)
            }
            return@Scaffold
        }

        val severityPriority = mapOf("HH" to 0, "LL" to 1, "H" to 2, "L" to 3, "N" to 4)
        val sortedObservations = remember(report.observations) {
            report.observations.sortedBy { severityPriority[it.interpretation] ?: 5 }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Report image preview with download overlay
            item(key = "image-preview") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .background(BrandDeep),
                    contentAlignment = Alignment.Center,
                ) {
                    if (downloadUrl != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(downloadUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Report document",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = "Document",
                                tint = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(48.dp),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Loading preview\u2026",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.5f),
                            )
                        }
                    }

                    // Download icon overlay
                    if (downloadUrl != null) {
                        IconButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl))
                                context.startActivity(intent)
                            },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(12.dp)
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.Black.copy(alpha = 0.5f)),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Download report",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }

            // Processing status banner
            if (report.status != "completed" && report.status != "ready") {
                item(key = "processing-banner") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(AttentionBg)
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = AttentionText,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "This report is still ${report.status}. Results will appear once processing is complete.",
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = AttentionText,
                        )
                    }
                }
            }

            // FHIR Patient match banner
            item(key = "patient-match") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(BrandPurpleLight)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "FHIR Patient match",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = BrandPurpleMid,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "${report.memberName} \u00B7 ${report.labName} \u00B7 ${FhirDateFormatter.toDisplay(report.effectiveDateTime)}",
                            fontSize = 12.sp,
                            color = BrandPurpleMid.copy(alpha = 0.8f),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // Section header: Lab results
            item(key = "section-header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Lab results \u00B7 FHIR Observation",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                        )
                    }
                    if (report.abnormalCount > 0) {
                        Text(
                            text = "${report.abnormalCount} outside range",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = HighText,
                            modifier = Modifier
                                .clip(RoundedCornerShape(100.dp))
                                .background(HighBg)
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // Observation rows — sorted by severity: critical > abnormal > normal
            items(sortedObservations, key = { it.id }) { observation ->
                ObservationRow(
                    obs = observation,
                    modifier = Modifier.padding(vertical = 0.5.dp),
                )
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = BorderSubtle,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            // Extraction disclaimer card
            item(key = "extraction-disclaimer") {
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(BrandPurpleLight)
                        .padding(14.dp),
                ) {
                    Text(
                        text = SaMDStringHelper.extractionDisclaimer,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = BrandPurpleMid,
                        textAlign = TextAlign.Start,
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
