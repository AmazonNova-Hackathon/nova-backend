package com.mediagent.app.ui.upload

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mediagent.app.presentation.viewmodel.UploadViewModel
import com.mediagent.app.ui.components.LoadingDots
import com.mediagent.app.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun ProcessingScreen(
    reportId: String,
    memberId: String,
    onComplete: (String) -> Unit,
    onDismiss: () -> Unit,
    uploadViewModel: UploadViewModel = hiltViewModel(),
) {
    val uploadState by uploadViewModel.state.collectAsState()
    val hasError = uploadState.error.isNotEmpty()

    val statusMessages = listOf(
        "Reading report header...",
        "Identifying test values...",
        "Mapping to LOINC codes...",
        "Cross-referencing your history...",
        "Generating insights...",
    )

    var currentIndex by remember { mutableIntStateOf(0) }

    // Cycle status messages every 2.2 seconds (only while processing)
    LaunchedEffect(hasError) {
        if (!hasError) {
            while (true) {
                delay(2200L)
                currentIndex = (currentIndex + 1) % statusMessages.size
            }
        }
    }

    // Poll real report status API every 3s
    LaunchedEffect(reportId) {
        uploadViewModel.pollStatus(
            reportId = reportId,
            memberId = memberId,
            onComplete = { onComplete(reportId) },
            onError = { /* error state handled via uploadState.error */ },
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfacePrimary),
        contentAlignment = Alignment.Center,
    ) {
        if (hasError) {
            // Error state
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 32.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Error",
                    tint = AttentionText,
                    modifier = Modifier.size(64.dp),
                )

                Spacer(Modifier.height(20.dp))

                Text(
                    text = uploadState.error,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandPurple,
                        contentColor = Color.White,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Back to Home",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }

                Spacer(Modifier.height(10.dp))

                OutlinedButton(
                    onClick = {
                        // Clear error and re-poll
                        uploadViewModel.clearError()
                        uploadViewModel.pollStatus(
                            reportId = reportId,
                            memberId = memberId,
                            onComplete = { onComplete(reportId) },
                            onError = {},
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Retry",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }
        } else {
            // Normal processing state
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 32.dp),
            ) {
                // Logo mark
                Text(
                    text = "\uD83C\uDF3F",
                    fontSize = 80.sp,
                )

                Spacer(Modifier.height(24.dp))

                // Title
                Text(
                    text = "Nova is reading your report",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(24.dp))

                // Cycling status message with crossfade
                Box(
                    modifier = Modifier.height(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    AnimatedContent(
                        targetState = currentIndex,
                        transitionSpec = {
                            fadeIn(animationSpec = androidx.compose.animation.core.tween(400)) togetherWith
                                fadeOut(animationSpec = androidx.compose.animation.core.tween(400))
                        },
                        label = "statusMessage",
                    ) { index ->
                        Text(
                            text = statusMessages[index],
                            fontSize = 14.sp,
                            color = TextSecondary,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Animated loading dots
                LoadingDots()

                Spacer(Modifier.height(32.dp))

                // Caption
                Text(
                    text = "This takes 10\u201320 seconds",
                    fontSize = 13.sp,
                    color = TextTertiary,
                )

                Spacer(Modifier.height(16.dp))

                // Dismiss link
                Text(
                    text = "You can navigate away \u2197",
                    fontSize = 13.sp,
                    color = BrandPurple,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable { onDismiss() },
                )
            }
        }
    }
}
