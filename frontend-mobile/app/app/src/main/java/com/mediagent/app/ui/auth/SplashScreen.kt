package com.mediagent.app.ui.auth

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mediagent.app.presentation.viewmodel.AuthViewModel
import com.mediagent.app.presentation.viewmodel.SettingsViewModel
import com.mediagent.app.ui.theme.BrandDeep
import com.mediagent.app.ui.theme.BrandForest
import com.mediagent.app.ui.theme.BrandMid
import com.mediagent.app.ui.theme.BrandPurple
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onNavigateToLogin: () -> Unit,
    onNavigateToMain: () -> Unit,
    onNavigateToOnboarding: () -> Unit,
    authViewModel: AuthViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
) {
    val isLoggedIn by authViewModel.isLoggedIn.collectAsState()
    val onboardingComplete by settingsViewModel.onboardingComplete.collectAsState()

    var appeared by remember { mutableStateOf(false) }
    val contentAlpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(durationMillis = 800),
        label = "splashAlpha",
    )

    LaunchedEffect(isLoggedIn, onboardingComplete) {
        appeared = true
        delay(2_000)
        if (!onboardingComplete) {
            onNavigateToOnboarding()
        } else if (isLoggedIn) {
            onNavigateToMain()
        } else {
            onNavigateToLogin()
        }
    }

    val gradient = Brush.verticalGradient(
        colors = listOf(BrandDeep, BrandMid, BrandForest),
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradient),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .alpha(contentAlpha)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Logo mark
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .background(
                        color = BrandForest,
                        shape = RoundedCornerShape(20.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Spa,
                    contentDescription = "Chetana logo",
                    tint = Color.White,
                    modifier = Modifier.size(48.dp),
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Hindi title
            Text(
                text = "\u091A\u0947\u0924\u0928\u093E",
                fontSize = 32.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(4.dp))

            // English title
            Text(
                text = "CHETANA",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.85f),
                letterSpacing = 6.sp,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Tagline
            Text(
                text = "Awaken to your health",
                fontSize = 17.sp,
                fontStyle = FontStyle.Italic,
                color = Color.White.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Get Started button
            Button(
                onClick = {
                    if (isLoggedIn) onNavigateToMain() else onNavigateToLogin()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandPurple,
                ),
            ) {
                Text(
                    text = "Get Started \u2192",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
            }
        }

        // Powered by footer
        Text(
            text = "Powered by Amazon Bedrock \u00B7 Nova AI",
            fontSize = 11.sp,
            color = Color.White.copy(alpha = 0.45f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .fillMaxWidth(),
        )
    }
}
