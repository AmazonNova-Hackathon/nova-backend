package com.mediagent.app.ui.chat

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mediagent.app.data.model.ChatMessage
import com.mediagent.app.presentation.viewmodel.ChatViewModel
import com.mediagent.app.ui.components.DisclaimerBar
import com.mediagent.app.ui.components.DisclaimerVariant
import com.mediagent.app.ui.components.LoadingDots
import com.mediagent.app.ui.components.MemberAvatar
import com.mediagent.app.ui.theme.*

private val SuggestedQuestions = listOf(
    "How is my glucose?",
    "Summarize last report",
    "What should I test next?",
    "Is my HbA1c improving?",
    "How is my cholesterol?",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onNavigateBack: () -> Unit,
    chatViewModel: ChatViewModel = hiltViewModel(),
) {
    val chatState by chatViewModel.state.collectAsState()
    val context = LocalContext.current
    val view = LocalView.current

    val activeMember = chatState.member

    var memberPickerExpanded by remember { mutableStateOf(false) }
    var isListening by remember { mutableStateOf(false) }

    // Speech-to-text locale mapping
    val speechLocale = remember(chatState.language) {
        when (chatState.language) {
            "hi" -> java.util.Locale.forLanguageTag("hi-IN")
            "mr" -> java.util.Locale.forLanguageTag("mr-IN")
            "ta" -> java.util.Locale.forLanguageTag("ta-IN")
            "bn" -> java.util.Locale.forLanguageTag("bn-IN")
            else -> java.util.Locale.ENGLISH
        }
    }

    // Speech recognizer result launcher
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isListening = false
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull() ?: ""
            if (spokenText.isNotBlank()) {
                chatViewModel.updateInput(spokenText)
                chatViewModel.send()
            }
        }
    }

    // Mic permission launcher
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, speechLocale.toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now...")
            }
            isListening = true
            speechLauncher.launch(intent)
        } else {
            Toast.makeText(context, "Microphone permission is required for voice input", Toast.LENGTH_SHORT).show()
        }
    }

    val listState = rememberLazyListState()

    // Auto-scroll to bottom on new message or typing change
    LaunchedEffect(chatState.messages.size, chatState.typing) {
        val targetIndex = chatState.messages.size - 1 + if (chatState.typing) 1 else 0
        if (targetIndex >= 0) {
            listState.animateScrollToItem(targetIndex)
        }
    }

    // Conversation is empty when only the initial welcome message exists
    val showSuggestions = chatState.messages.size == 1 &&
        chatState.messages.first().role == "assistant"

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
                    if (activeMember != null) {
                        Box {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { memberPickerExpanded = true },
                            ) {
                                MemberAvatar(
                                    initials = activeMember.displayInitials,
                                    colorHex = activeMember.displayAvatarColor,
                                    size = 32.dp,
                                )
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = activeMember.name,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary,
                                    )
                                    Text(
                                        text = "Tap to switch",
                                        fontSize = 11.sp,
                                        color = TextTertiary,
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Switch member",
                                    tint = TextTertiary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }

                            // Member picker dropdown
                            DropdownMenu(
                                expanded = memberPickerExpanded,
                                onDismissRequest = { memberPickerExpanded = false },
                            ) {
                                chatState.members.forEach { member ->
                                    val isActive = member.id == activeMember.id
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                MemberAvatar(
                                                    initials = member.displayInitials,
                                                    colorHex = member.displayAvatarColor,
                                                    size = 28.dp,
                                                )
                                                Spacer(Modifier.width(10.dp))
                                                Text(
                                                    text = member.name,
                                                    fontSize = 14.sp,
                                                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                                    color = TextPrimary,
                                                )
                                                if (isActive) {
                                                    Spacer(Modifier.width(8.dp))
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = "Active",
                                                        tint = BrandPurple,
                                                        modifier = Modifier.size(18.dp),
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            memberPickerExpanded = false
                                            if (!isActive) {
                                                chatViewModel.setMember(member)
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "Chat",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                        )
                    }
                },
                actions = {
                    if (activeMember != null) {
                        val langLabels = mapOf(
                            "en" to "EN", "hi" to "HI", "mr" to "MR", "ta" to "TA",
                            "bn" to "BN",
                        )
                        val langLabel = langLabels[chatState.language] ?: chatState.language.uppercase()
                        Text(
                            text = langLabel,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = BrandPurple,
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .clip(RoundedCornerShape(100.dp))
                                .background(BrandPurpleLight)
                                .padding(horizontal = 10.dp, vertical = 4.dp),
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
            if (activeMember == null) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    if (chatState.loading) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                color = BrandPurple,
                                modifier = Modifier.size(36.dp),
                                strokeWidth = 3.dp,
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "Loading...",
                                fontSize = 14.sp,
                                color = TextTertiary,
                            )
                        }
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 32.dp),
                        ) {
                            Text(
                                text = "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67",
                                fontSize = 48.sp,
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = chatState.error.ifEmpty { "No family members found" },
                                fontSize = 15.sp,
                                color = TextSecondary,
                                fontWeight = FontWeight.Medium,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Add a family member first to start chatting about health reports.",
                                fontSize = 13.sp,
                                color = TextTertiary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                }
            } else {
                // Messages list
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(chatState.messages, key = { index, msg -> "${msg.role}-${msg.text.hashCode()}-$index" }) { _, message ->
                        ChatBubble(message = message)
                    }
                    if (chatState.typing) {
                        item(key = "typing-indicator") {
                            TypingBubble()
                        }
                    }
                }

                // Suggested questions rail
                if (showSuggestions) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SuggestedQuestions.forEach { question ->
                            SuggestionChip(
                                onClick = {
                                    chatViewModel.updateInput(question)
                                    chatViewModel.send()
                                },
                                label = {
                                    Text(
                                        text = question,
                                        fontSize = 13.sp,
                                        color = BrandPurpleMid,
                                    )
                                },
                                shape = RoundedCornerShape(100.dp),
                                border = SuggestionChipDefaults.suggestionChipBorder(
                                    enabled = true,
                                    borderColor = BrandPurple.copy(alpha = 0.3f),
                                ),
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = BrandPurpleLight,
                                ),
                            )
                        }
                    }
                }

                // Input bar
                InputBar(
                    value = chatState.input,
                    onValueChange = { chatViewModel.updateInput(it) },
                    isListening = isListening,
                    onSend = {
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                    chatViewModel.send()
                },
                    onMicClick = {
                        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                            Toast.makeText(context, "Speech recognition not available on this device", Toast.LENGTH_SHORT).show()
                        } else if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, speechLocale.toLanguageTag())
                                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now...")
                            }
                            isListening = true
                            speechLauncher.launch(intent)
                        } else {
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                )
            }

            // Disclaimer -- always visible
            DisclaimerBar(variant = DisclaimerVariant.FULL)
        }
    }
}

// ---- Chat Bubble ----

@Composable
private fun ChatBubble(message: ChatMessage) {
    val isUser = message.role == "user"
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    val mediaPlayer = remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(Unit) {
        onDispose { mediaPlayer.value?.release() }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(BrandForestLight),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "\uD83C\uDF3F", fontSize = 14.sp)
            }
            Spacer(Modifier.width(6.dp))
        }

        Column(
            modifier = Modifier.widthIn(max = 280.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
        ) {
            Box(
                modifier = Modifier
                    .clip(
                        if (isUser) {
                            RoundedCornerShape(
                                topStart = 16.dp,
                                topEnd = 16.dp,
                                bottomStart = 16.dp,
                                bottomEnd = 2.dp,
                            )
                        } else {
                            RoundedCornerShape(
                                topStart = 2.dp,
                                topEnd = 16.dp,
                                bottomStart = 16.dp,
                                bottomEnd = 16.dp,
                            )
                        },
                    )
                    .background(if (isUser) BrandPurple else SurfacePrimary)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(
                    text = message.text,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = if (isUser) Color.White else TextPrimary,
                )
            }

            // Audio play button for voice responses
            if (!isUser && !message.audioUrl.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(BrandForestLight)
                        .clickable {
                            if (isPlaying) {
                                mediaPlayer.value?.stop()
                                mediaPlayer.value?.release()
                                mediaPlayer.value = null
                                isPlaying = false
                            } else {
                                try {
                                    mediaPlayer.value?.release()
                                    mediaPlayer.value = MediaPlayer().apply {
                                        setDataSource(message.audioUrl)
                                        setOnPreparedListener { start() }
                                        setOnCompletionListener {
                                            isPlaying = false
                                            release()
                                            mediaPlayer.value = null
                                        }
                                        setOnErrorListener { _, _, _ ->
                                            isPlaying = false
                                            Toast
                                                .makeText(context, "Could not play audio", Toast.LENGTH_SHORT)
                                                .show()
                                            release()
                                            mediaPlayer.value = null
                                            true
                                        }
                                        prepareAsync()
                                    }
                                    isPlaying = true
                                } catch (e: Exception) {
                                    Toast
                                        .makeText(context, "Could not play audio", Toast.LENGTH_SHORT)
                                        .show()
                                }
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Stop" else "Play audio",
                        tint = BrandForest,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (isPlaying) "Stop" else "Play audio",
                        fontSize = 12.sp,
                        color = BrandForest,
                    )
                }
            }

            if (!isUser && message.citedObservations.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                message.citedObservations.forEach { obs ->
                    Text(
                        text = "${obs.name} \u00B7 ${obs.loincCode}",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = BrandForest,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(BrandForestLight)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        }

        if (isUser) {
            Spacer(Modifier.width(6.dp))
        }
    }
}

// ---- Typing Indicator ----

@Composable
private fun TypingBubble() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(BrandForestLight),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "\uD83C\uDF3F", fontSize = 14.sp)
        }
        Spacer(Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .clip(
                    RoundedCornerShape(
                        topStart = 2.dp,
                        topEnd = 16.dp,
                        bottomStart = 16.dp,
                        bottomEnd = 16.dp,
                    ),
                )
                .background(SurfacePrimary)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            LoadingDots()
        }
    }
}

// ---- Input Bar ----

@Composable
private fun InputBar(
    value: String,
    onValueChange: (String) -> Unit,
    isListening: Boolean = false,
    onSend: () -> Unit,
    onMicClick: () -> Unit,
) {
    Surface(
        tonalElevation = 2.dp,
        color = SurfacePrimary,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onMicClick) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = if (isListening) "Listening..." else "Voice input",
                    tint = if (isListening) BrandPurple else TextTertiary,
                )
            }

            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = TextStyle(
                    fontSize = 14.sp,
                    color = TextPrimary,
                ),
                singleLine = false,
                maxLines = 4,
                cursorBrush = SolidColor(BrandPurple),
                modifier = Modifier.weight(1f),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .background(SurfaceSecondary)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        if (value.isEmpty()) {
                            Text(
                                text = "Ask about your health...",
                                fontSize = 14.sp,
                                color = TextTertiary,
                            )
                        }
                        innerTextField()
                    }
                },
            )

            Spacer(Modifier.width(4.dp))

            IconButton(
                onClick = onSend,
                enabled = value.isNotBlank(),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (value.isNotBlank()) BrandPurple else TextTertiary.copy(alpha = 0.4f),
                )
            }
        }
    }
}
