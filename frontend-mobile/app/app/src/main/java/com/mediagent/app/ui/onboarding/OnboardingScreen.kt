package com.mediagent.app.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediagent.app.ui.theme.*
import kotlinx.coroutines.launch

private data class OnboardingPage(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val description: String,
)

private val pages = listOf(
    OnboardingPage(
        icon = Icons.Default.Spa,
        title = "\u091A\u0947\u0924\u0928\u093E",
        subtitle = "Your Family Health Companion",
        description = "Chetana helps you understand lab reports, track health trends, and get AI-powered insights \u2014 all in one place.",
    ),
    OnboardingPage(
        icon = Icons.Default.CameraAlt,
        title = "Scan & Extract",
        subtitle = "Upload lab reports instantly",
        description = "Take a photo or upload a PDF of any lab report. Our AI extracts results into structured FHIR data automatically.",
    ),
    OnboardingPage(
        icon = Icons.Default.Group,
        title = "Family Health",
        subtitle = "Track everyone's health",
        description = "Add family members and manage their reports in one place. Each member gets personalized insights and trends.",
    ),
    OnboardingPage(
        icon = Icons.Default.ChatBubbleOutline,
        title = "Ask Chetana",
        subtitle = "Chat in your language",
        description = "Ask questions about your reports in English, Hindi, or 6 other Indian languages. Get clear, easy-to-understand explanations.",
    ),
)

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == pages.lastIndex

    val gradient = Brush.verticalGradient(
        colors = listOf(BrandDeep, BrandMid),
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradient),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Skip button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .statusBarsPadding(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onComplete) {
                    Text(
                        text = "Skip",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                    )
                }
            }

            // Pager content
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
            ) { page ->
                OnboardingPageContent(pages[page])
            }

            // Page indicators
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 24.dp),
            ) {
                repeat(pages.size) { index ->
                    Box(
                        modifier = Modifier
                            .size(if (index == pagerState.currentPage) 24.dp else 8.dp, 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (index == pagerState.currentPage) BrandPurple
                                else Color.White.copy(alpha = 0.3f),
                            ),
                    )
                }
            }

            // Bottom button
            Button(
                onClick = {
                    if (isLastPage) {
                        onComplete()
                    } else {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .padding(bottom = 48.dp)
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandPurple),
            ) {
                AnimatedContent(targetState = isLastPage, label = "btn") { last ->
                    Text(
                        text = if (last) "Get Started \u2192" else "Next",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingPageContent(page: OnboardingPage) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(BrandForest, RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = page.icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(48.dp),
            )
        }

        Spacer(Modifier.height(32.dp))

        Text(
            text = page.title,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = page.subtitle,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.85f),
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = page.description,
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.65f),
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
        )
    }
}
