package com.mediagent.app.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mediagent.app.presentation.viewmodel.AuthViewModel
import com.mediagent.app.ui.theme.BrandDeep
import com.mediagent.app.ui.theme.BrandForest
import com.mediagent.app.ui.theme.BrandMid
import com.mediagent.app.ui.theme.BrandPurple
import com.mediagent.app.ui.theme.BrandPurpleLight
import com.mediagent.app.ui.theme.BrandPurpleMid
import com.mediagent.app.ui.theme.TextSecondary

@Composable
fun OtpScreen(
    onVerified: () -> Unit,
    onBack: () -> Unit = {},
    authViewModel: AuthViewModel = hiltViewModel(),
) {
    val state by authViewModel.state.collectAsState()
    val view = LocalView.current
    val otp = state.otp
    val focusRequesters = remember { List(6) { FocusRequester() } }

    val headerGradient = Brush.verticalGradient(
        colors = listOf(BrandDeep, BrandMid, BrandForest),
    )

    // Auto-focus the first box on launch
    LaunchedEffect(Unit) {
        focusRequesters[0].requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
    ) {
        // Gradient header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .background(headerGradient),
        ) {
            // Back button
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(4.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.align(Alignment.Center),
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            color = BrandForest,
                            shape = RoundedCornerShape(14.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Spa,
                        contentDescription = "Chetana",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp),
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "CHETANA",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    letterSpacing = 4.sp,
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Verify your identity",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1C1C1A),
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Enter the 6-digit code sent to your email",
                fontSize = 14.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(32.dp))

            // OTP input boxes
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                for (i in 0 until 6) {
                    val char = otp.getOrNull(i)?.toString() ?: ""
                    val isActive = otp.length == i

                    BasicTextField(
                        value = char,
                        onValueChange = { value ->
                            val digit = value.filter { it.isDigit() }.take(1)
                            if (digit.isNotEmpty()) {
                                val newOtp = buildString {
                                    append(otp.take(i))
                                    append(digit)
                                    if (i + 1 < otp.length) append(otp.substring(i + 1))
                                }
                                authViewModel.updateOtp(newOtp)
                                // Auto-focus next
                                if (i < 5) {
                                    focusRequesters[i + 1].requestFocus()
                                }
                            }
                        },
                        modifier = Modifier
                            .size(width = 44.dp, height = 52.dp)
                            .focusRequester(focusRequesters[i])
                            .onKeyEvent { event ->
                                if (event.key == Key.Backspace && char.isEmpty() && i > 0) {
                                    // Remove previous digit and focus previous
                                    val newOtp = otp.take(i - 1) + otp.drop(i)
                                    authViewModel.updateOtp(newOtp)
                                    focusRequesters[i - 1].requestFocus()
                                    true
                                } else if (event.key == Key.Backspace && char.isNotEmpty()) {
                                    val newOtp = otp.take(i) + otp.drop(i + 1)
                                    authViewModel.updateOtp(newOtp)
                                    true
                                } else {
                                    false
                                }
                            }
                            .background(
                                color = if (isActive) BrandPurpleLight else Color(0xFFF5F5F5),
                                shape = RoundedCornerShape(10.dp),
                            )
                            .border(
                                width = if (isActive) 2.dp else 1.dp,
                                color = if (isActive) BrandPurple else Color(0xFFE0E0E0),
                                shape = RoundedCornerShape(10.dp),
                            ),
                        textStyle = TextStyle(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1C1C1A),
                            textAlign = TextAlign.Center,
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        decorationBox = { innerTextField ->
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                innerTextField()
                            }
                        },
                    )

                    if (i < 5) {
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                }
            }

            // Error message
            if (state.error.isNotBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = state.error,
                    fontSize = 13.sp,
                    color = Color(0xFFA32D2D),
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Verify CTA
            Button(
                onClick = {
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
                    authViewModel.verifyOtp(onSuccess = onVerified)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = otp.length == 6 && !state.loading,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(BrandPurple, BrandPurpleMid),
                            ),
                            shape = RoundedCornerShape(14.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = Color.White,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(
                            text = "Verify & Enter \u2192",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Resend link
            Text(
                text = "Didn't receive a code? Resend",
                fontSize = 13.sp,
                color = BrandPurple,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
