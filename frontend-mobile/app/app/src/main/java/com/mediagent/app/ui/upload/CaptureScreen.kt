package com.mediagent.app.ui.upload

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mediagent.app.presentation.viewmodel.UploadViewModel
import com.mediagent.app.ui.components.MemberAvatar
import androidx.compose.ui.platform.LocalView
import com.mediagent.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureScreen(
    onDismiss: () -> Unit,
    onCaptured: (reportId: String, memberId: String) -> Unit,
    uploadViewModel: UploadViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val view = LocalView.current
    val uploadState by uploadViewModel.state.collectAsState()
    val cornerLen = 28.dp
    val cornerStroke = 2.dp
    val cornerColor = Color.White.copy(alpha = 0.7f)

    // Navigate when reportId becomes available
    LaunchedEffect(uploadState.reportId) {
        uploadState.reportId?.let { reportId ->
            val memberId = uploadState.memberId
            uploadViewModel.resetReportId()
            onCaptured(reportId, memberId)
        }
    }

    // Show error via toast
    LaunchedEffect(uploadState.error) {
        if (uploadState.error.isNotEmpty()) {
            Toast.makeText(context, uploadState.error, Toast.LENGTH_LONG).show()
        }
    }

    fun handleUri(uri: Uri, contentType: String) {
        val inputStream = context.contentResolver.openInputStream(uri)
        val bytes = inputStream?.readBytes()
        inputStream?.close()
        if (bytes != null) {
            val resolvedType = context.contentResolver.getType(uri) ?: contentType
            uploadViewModel.uploadFile(bytes, resolvedType)
        } else {
            Toast.makeText(context, "Could not read file", Toast.LENGTH_SHORT).show()
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) handleUri(uri, "image/jpeg")
    }

    val documentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) handleUri(uri, "application/pdf")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BrandDeep)
            .statusBarsPadding(),
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = Color.White,
                )
            }
            Text(
                text = "Scan Report",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
            Spacer(Modifier.weight(1f))

            // Member picker chip
            val member = uploadState.selectedMember
            if (member != null) {
                var memberPickerExpanded by remember { mutableStateOf(false) }
                Box {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(100.dp))
                            .background(Color.White.copy(alpha = 0.15f))
                            .clickable { memberPickerExpanded = true }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        MemberAvatar(
                            initials = member.displayInitials,
                            colorHex = member.displayAvatarColor,
                            size = 22.dp,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = member.name.split(" ").first(),
                            fontSize = 12.sp,
                            color = Color.White,
                        )
                    }
                    DropdownMenu(
                        expanded = memberPickerExpanded,
                        onDismissRequest = { memberPickerExpanded = false },
                    ) {
                        uploadState.members.forEach { m ->
                            DropdownMenuItem(
                                text = { Text(m.name, fontSize = 14.sp) },
                                onClick = {
                                    memberPickerExpanded = false
                                    uploadViewModel.selectMember(m)
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
            }
        }

        // Uploading overlay or viewfinder
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (uploadState.uploading) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = BrandPurple,
                        modifier = Modifier.size(48.dp),
                        strokeWidth = 4.dp,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Uploading report...",
                        fontSize = 16.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.7f)
                        .background(Color(0xFF111111), RoundedCornerShape(4.dp))
                        .drawBehind {
                            val cLen = cornerLen.toPx()
                            val sWidth = cornerStroke.toPx()
                            val w = size.width
                            val h = size.height

                            drawLine(cornerColor, Offset(0f, 0f), Offset(cLen, 0f), sWidth)
                            drawLine(cornerColor, Offset(0f, 0f), Offset(0f, cLen), sWidth)

                            drawLine(cornerColor, Offset(w, 0f), Offset(w - cLen, 0f), sWidth)
                            drawLine(cornerColor, Offset(w, 0f), Offset(w, cLen), sWidth)

                            drawLine(cornerColor, Offset(0f, h), Offset(cLen, h), sWidth)
                            drawLine(cornerColor, Offset(0f, h), Offset(0f, h - cLen), sWidth)

                            drawLine(cornerColor, Offset(w, h), Offset(w - cLen, h), sWidth)
                            drawLine(cornerColor, Offset(w, h), Offset(w, h - cLen), sWidth)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Position the full report\nwithin the frame",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp,
                    )
                }
            }
        }

        // Bottom controls (hidden during upload)
        if (!uploadState.uploading) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                            galleryLauncher.launch("image/*")
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = "Gallery",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Gallery",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.6f),
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .border(3.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                            .padding(4.dp)
                            .clip(CircleShape)
                            .background(BrandPurple)
                            .clickable {
                                view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                                Toast
                                    .makeText(context, "Camera capture coming soon. Use Gallery or PDF.", Toast.LENGTH_SHORT)
                                    .show()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.3f)),
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                            documentLauncher.launch("application/pdf")
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = "Document",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "PDF",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.6f),
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Snap a photo or pick from gallery / files",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.4f),
                )
            }
        }
    }
}
