package com.mediagent.app.ui.auth

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
fun LoginScreen(
    onNavigateToOtp: () -> Unit,
    authViewModel: AuthViewModel = hiltViewModel(),
) {
    val state by authViewModel.state.collectAsState()
    val context = LocalContext.current
    var passwordVisible by remember { mutableStateOf(false) }

    val headerGradient = Brush.verticalGradient(
        colors = listOf(BrandDeep, BrandMid, BrandForest),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .verticalScroll(rememberScrollState()),
    ) {
        // Gradient header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(headerGradient),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(
                            color = BrandForest,
                            shape = RoundedCornerShape(16.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Spa,
                        contentDescription = "Chetana",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp),
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "CHETANA",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    letterSpacing = 4.sp,
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
        ) {
            Text(
                text = "Welcome back",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1C1C1A),
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Sign in to access your family health dashboard",
                fontSize = 14.sp,
                color = TextSecondary,
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Email field
            OutlinedTextField(
                value = state.email,
                onValueChange = authViewModel::updateEmail,
                label = { Text("Email") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = null,
                        tint = BrandPurpleMid,
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BrandPurple,
                    cursorColor = BrandPurple,
                ),
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Password field
            OutlinedTextField(
                value = state.password,
                onValueChange = authViewModel::updatePassword,
                label = { Text("Password") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = BrandPurpleMid,
                    )
                },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password",
                            tint = TextSecondary,
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BrandPurple,
                    cursorColor = BrandPurple,
                ),
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Demo credentials hint card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { authViewModel.fillDemoCredentials() },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = BrandPurpleLight),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Demo Credentials (tap to auto-fill)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = BrandPurpleMid,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "demo@chetana.health / demo1234",
                        fontSize = 13.sp,
                        color = BrandPurple,
                    )
                }
            }

            // Error message
            if (state.error.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = state.error,
                    fontSize = 13.sp,
                    color = Color(0xFFA32D2D),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Continue CTA
            Button(
                onClick = { authViewModel.signIn(onSuccess = onNavigateToOtp) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = !state.loading,
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
                            text = "Continue \u2192",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Divider
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Divider(modifier = Modifier.weight(1f), color = Color(0xFFE0E0E0))
                Text(
                    text = "  or continue with  ",
                    fontSize = 12.sp,
                    color = TextSecondary,
                )
                Divider(modifier = Modifier.weight(1f), color = Color(0xFFE0E0E0))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Google button
            OutlinedButton(
                onClick = {
                    Toast.makeText(context, "Google sign-in coming post-hackathon", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    text = "Continue with Google",
                    fontSize = 14.sp,
                    color = Color(0xFF1C1C1A),
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Phone OTP button
            OutlinedButton(
                onClick = {
                    Toast.makeText(context, "Phone OTP coming post-hackathon", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Phone,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = TextSecondary,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Continue with Phone OTP",
                    fontSize = 14.sp,
                    color = Color(0xFF1C1C1A),
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Sign up link
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "Don't have an account? ",
                    fontSize = 13.sp,
                    color = TextSecondary,
                )
                Text(
                    text = "Sign up",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = BrandPurple,
                    modifier = Modifier.clickable {
                        Toast.makeText(context, "Sign up coming post-hackathon", Toast.LENGTH_SHORT).show()
                    },
                )
            }
        }
    }
}
