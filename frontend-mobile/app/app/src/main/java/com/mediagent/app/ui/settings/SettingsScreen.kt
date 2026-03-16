package com.mediagent.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mediagent.app.data.model.Member
import com.mediagent.app.presentation.viewmodel.FamilyViewModel
import com.mediagent.app.presentation.viewmodel.SettingsViewModel
import com.mediagent.app.ui.components.MemberAvatar
import com.mediagent.app.ui.theme.*
import com.mediagent.app.util.SaMDStringHelper

private data class LanguageOption(
    val code: String,
    val label: String,
    val voiceSupported: Boolean,
)

private val languages = listOf(
    LanguageOption("en", "English", voiceSupported = true),
    LanguageOption("hi", "\u0939\u093F\u0902\u0926\u0940", voiceSupported = true),
    LanguageOption("mr", "\u092E\u0930\u093E\u0920\u0940", voiceSupported = false),
    LanguageOption("ta", "\u0BA4\u0BAE\u0BBF\u0BB4\u0BCD", voiceSupported = false),
    LanguageOption("bn", "\u09AC\u09BE\u0982\u09B2\u09BE", voiceSupported = false),
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onLogout: () -> Unit,
    familyViewModel: FamilyViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
) {
    val familyState by familyViewModel.state.collectAsState()
    val selectedLanguage by settingsViewModel.language.collectAsState()
    val voiceEnabled by settingsViewModel.voiceEnabled.collectAsState()
    var showAddMemberDialog by remember { mutableStateOf(false) }
    var newMemberName by remember { mutableStateOf("") }
    var newMemberRelationship by remember { mutableStateOf("") }

    val currentLang = remember(selectedLanguage) {
        languages.first { it.code == selectedLanguage }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = BrandDeep,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary,
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
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(16.dp))

            // ===== 1. Language & Voice =====
            SectionHeader("Nova Language & Voice")
            Spacer(Modifier.height(10.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SurfacePrimary),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    // Language picker grid
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        languages.forEach { lang ->
                            val isSelected = selectedLanguage == lang.code
                            Text(
                                text = lang.label,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isSelected) Color.White else TextPrimary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) BrandPurple else SurfaceTertiary)
                                    .clickable { settingsViewModel.setLanguage(lang.code) }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = SurfaceTertiary)
                    Spacer(Modifier.height(10.dp))

                    // Voice toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Voice responses",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextPrimary,
                            )
                            if (!currentLang.voiceSupported) {
                                Text(
                                    text = "Voice is available for English and Hindi only",
                                    fontSize = 11.sp,
                                    color = TextTertiary,
                                )
                            }
                        }
                        Switch(
                            checked = voiceEnabled && currentLang.voiceSupported,
                            onCheckedChange = { settingsViewModel.setVoiceEnabled(it) },
                            enabled = currentLang.voiceSupported,
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = BrandPurple,
                            ),
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // ===== 2. Family =====
            SectionHeader("Family")
            Spacer(Modifier.height(10.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SurfacePrimary),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    familyState.members.forEachIndexed { index, member ->
                        if (index > 0) {
                            HorizontalDivider(
                                color = SurfaceTertiary,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }
                        MemberSettingsRow(member)
                    }

                    if (familyState.members.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                    }

                    // Add member button (dashed style)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .border(
                                width = 1.dp,
                                color = BrandPurple.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(10.dp),
                            )
                            .clickable { showAddMemberDialog = true }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = BrandPurple,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Add member",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = BrandPurple,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // ===== 3. Data Standards =====
            SectionHeader("Data Standards")
            Spacer(Modifier.height(10.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SurfacePrimary),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    StandardRow(
                        label = "FHIR R4",
                        detail = "DiagnosticReport, Observation, Patient",
                    )
                    HorizontalDivider(color = SurfaceTertiary)
                    StandardRow(
                        label = "LOINC",
                        detail = "Laboratory test coding",
                    )
                    HorizontalDivider(color = SurfaceTertiary)
                    StandardRow(
                        label = "HL7 v3",
                        detail = "Interpretation codes N / H / L / HH / LL",
                    )
                    HorizontalDivider(color = SurfaceTertiary)
                    StandardRow(
                        label = "Class I SaMD",
                        detail = "Informational, non-diagnostic",
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // ===== Nova AI Stack =====
            SectionHeader("Nova AI Stack")
            Spacer(Modifier.height(10.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SurfacePrimary),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    NovaModelRow(
                        name = "Nova 2 Lite",
                        role = "Brain & Eyes",
                        detail = "Multimodal engine for extracting structured data from report images. Powers the Bedrock Agent for reasoning over health history.",
                    )
                    HorizontalDivider(color = SurfaceTertiary)
                    NovaModelRow(
                        name = "Nova Embeddings",
                        role = "Memory",
                        detail = "RAG across report images and clinical guidelines, grounding insights in real medical literature.",
                    )
                    HorizontalDivider(color = SurfaceTertiary)
                    NovaModelRow(
                        name = "Nova 2 Sonic",
                        role = "Voice",
                        detail = "Bidirectional, low-latency Hindi & English voice experience for elderly and low-literacy users.",
                    )
                    HorizontalDivider(color = SurfaceTertiary)
                    NovaModelRow(
                        name = "Nova Micro",
                        role = "Speed",
                        detail = "Ultra-fast translations into Marathi, Tamil, and Bengali.",
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // ===== 4. About =====
            SectionHeader("About")
            Spacer(Modifier.height(10.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SurfacePrimary),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Chetana v1.0.0",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = SaMDStringHelper.disclaimerFull,
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 18.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Powered by Amazon Bedrock \u00B7 Nova AI",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextTertiary,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // ===== Logout =====
            Button(
                onClick = {
                    settingsViewModel.logout()
                    onLogout()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = UrgentBg,
                    contentColor = UrgentText,
                ),
            ) {
                Icon(
                    imageVector = Icons.Default.Logout,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Logout",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(Modifier.height(40.dp))
        }
    }

    // Add member dialog
    if (showAddMemberDialog) {
        AlertDialog(
            onDismissRequest = { showAddMemberDialog = false },
            title = { Text("Add Family Member") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = newMemberName,
                        onValueChange = { newMemberName = it },
                        label = { Text("Full name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = newMemberRelationship,
                        onValueChange = { newMemberRelationship = it },
                        label = { Text("Relationship (e.g. Mother, Father, Self)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (familyState.error.isNotBlank()) {
                        Text(
                            text = familyState.error,
                            fontSize = 13.sp,
                            color = Color(0xFFA32D2D),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newMemberName.isNotBlank() && newMemberRelationship.isNotBlank()) {
                            familyViewModel.addMember(newMemberName.trim(), newMemberRelationship.trim()) {
                                showAddMemberDialog = false
                                newMemberName = ""
                                newMemberRelationship = ""
                            }
                        }
                    },
                ) {
                    Text("Add", color = BrandPurple)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddMemberDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        color = TextPrimary,
    )
}

@Composable
private fun MemberSettingsRow(member: Member) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MemberAvatar(
            initials = member.displayInitials,
            colorHex = member.displayAvatarColor,
            size = 40.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = member.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
            )
            Text(
                text = member.relationship,
                fontSize = 12.sp,
                color = TextSecondary,
            )
            Text(
                text = "FHIR Patient/${member.id}",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = TextTertiary,
            )
        }
    }
}

@Composable
private fun StandardRow(label: String, detail: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = BrandPurpleMid,
            modifier = Modifier.width(90.dp),
        )
        Text(
            text = detail,
            fontSize = 13.sp,
            color = TextSecondary,
        )
    }
}

@Composable
private fun NovaModelRow(name: String, role: String, detail: String) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = name,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = role,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = BrandPurple,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(BrandPurpleLight)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = detail,
            fontSize = 12.sp,
            color = TextSecondary,
            lineHeight = 17.sp,
        )
    }
}
