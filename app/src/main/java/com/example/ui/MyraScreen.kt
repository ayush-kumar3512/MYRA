package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.DiagnosticLogger
import com.example.ui.components.DiagnosticLogsSheet
import com.example.ui.components.SettingsDialog
import com.example.ui.components.TranscriptCard
import com.example.ui.components.VoiceVisualizerOrb

@Composable
fun MyraScreen(
    viewModel: AssistantViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val status by viewModel.status.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val transcripts by viewModel.transcripts.collectAsState()
    val amplitude by viewModel.visualizerAmplitude.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val logs by DiagnosticLogger.logs.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val liveHearingText by viewModel.liveHearingText.collectAsState()

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showLogsSheet by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    // Scroll to bottom when new transcripts arrive
    LaunchedEffect(transcripts.size) {
        if (transcripts.isNotEmpty()) {
            listState.animateScrollToItem(transcripts.size - 1)
        }
    }

    // Permission launchers
    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            DiagnosticLogger.i("MyraScreen", "RECORD_AUDIO permission granted")
            viewModel.startVoiceSession()
        } else {
            DiagnosticLogger.w("MyraScreen", "RECORD_AUDIO permission denied")
        }
    }

    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            DiagnosticLogger.i("MyraScreen", "READ_CONTACTS permission granted")
        } else {
            DiagnosticLogger.w("MyraScreen", "READ_CONTACTS permission denied")
        }
    }

    // Function to handle mic click with permission check
    val onMicClick: () -> Unit = {
        val hasMicPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasMicPermission) {
            recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            viewModel.onMicButtonClicked()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF090D16), // Dark void
                            Color(0xFF110D24), // Subtle violet
                            Color(0xFF090D16)
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 16.dp)
            ) {
                // Top App Bar
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.myra_logo),
                            contentDescription = "MYRA Logo",
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .border(
                                    width = 2.dp,
                                    color = when (status) {
                                        AssistantStatus.LISTENING -> Color(0xFF06B6D4)
                                        AssistantStatus.SPEAKING -> Color(0xFFEC4899)
                                        AssistantStatus.CONNECTING -> Color(0xFFF59E0B)
                                        AssistantStatus.ERROR -> Color(0xFFEF4444)
                                        else -> Color(0xFF818CF8)
                                    },
                                    shape = CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.assistant_title),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFF8FAFC)
                            )
                            Text(
                                text = stringResource(R.string.assistant_subtitle),
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    // Action buttons: Speaker test, Diagnostics, Settings
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Speaker Diagnostic Test Button (Spec Section 15 & 44)
                        Surface(
                            onClick = { viewModel.testSpeaker() },
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xFF1E293B),
                            modifier = Modifier.testTag("btn_test_speaker")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = stringResource(R.string.btn_test_speaker),
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "440Hz",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF38BDF8)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Diagnostics Logs Button
                        IconButton(
                            onClick = { showLogsSheet = true },
                            modifier = Modifier.testTag("btn_diagnostics")
                        ) {
                            Icon(
                                imageVector = Icons.Default.BugReport,
                                contentDescription = stringResource(R.string.btn_diagnostics),
                                tint = Color(0xFFCBD5E1),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Settings Button
                        IconButton(
                            onClick = { showSettingsDialog = true },
                            modifier = Modifier.testTag("btn_settings")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = stringResource(R.string.btn_settings),
                                tint = Color(0xFFCBD5E1),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                // Error Banner (if any)
                AnimatedVisibility(
                    visible = errorMessage != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    errorMessage?.let { error ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF450A0A)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { viewModel.dismissError() }
                                .testTag("error_banner")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Text(
                                    text = error,
                                    color = Color(0xFFFCA5A5),
                                    fontSize = 12.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "Dismiss",
                                    color = Color(0xFFF87171),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Central Hero: VoiceVisualizerOrb
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                ) {
                    VoiceVisualizerOrb(
                        status = status,
                        amplitude = amplitude,
                        onClick = onMicClick
                    )
                }

                // Status Badge & Message
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Status Pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                when (status) {
                                    AssistantStatus.LISTENING -> Color(0xFF0E7490)
                                    AssistantStatus.SPEAKING -> Color(0xFFBE185D)
                                    AssistantStatus.CONNECTING -> Color(0xFFB45309)
                                    AssistantStatus.ERROR -> Color(0xFF991B1B)
                                    else -> Color(0xFF334155)
                                }
                            )
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .testTag("status_indicator_pill")
                    ) {
                        Text(
                            text = status.name,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 1.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = statusMessage,
                        fontSize = 13.sp,
                        color = Color(0xFFE2E8F0),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )

                    AnimatedVisibility(visible = liveHearingText.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF0C4A6E),
                            modifier = Modifier.padding(top = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = null,
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = liveHearingText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFFE0F2FE)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Transcript Stream
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("transcripts_list")
                ) {
                    items(transcripts, key = { it.id }) { item ->
                        TranscriptCard(item = item)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Quick Prompt Chips
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 4.dp)
                ) {
                    val promptSuggestions = listOf(
                        "Torch jalao",
                        "Set alarm for 7 AM",
                        "WhatsApp kholo",
                        "Call Mom",
                        "Open YouTube",
                        "5 min ka timer lagao",
                        "Hindi mein baat karo",
                        "Tell me a witty joke"
                    )
                    promptSuggestions.forEach { suggestion ->
                        Surface(
                            onClick = {
                                if (suggestion == "Call Mom") {
                                    val hasContacts = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.READ_CONTACTS
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (!hasContacts) {
                                        contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                                    }
                                }
                                viewModel.onQuickPromptSelected(suggestion)
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFF1E293B),
                            modifier = Modifier
                                .border(1.dp, Color(0xFF334155), RoundedCornerShape(16.dp))
                                .testTag("quick_chip_${suggestion.replace(" ", "_")}")
                        ) {
                            Text(
                                text = suggestion,
                                fontSize = 12.sp,
                                color = Color(0xFFCBD5E1),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Bottom Floating Action Dock
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    // Central Glowing Microphone / Power Button
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(72.dp)
                            .shadow(
                                elevation = 16.dp,
                                shape = CircleShape,
                                spotColor = if (status == AssistantStatus.LISTENING) Color(0xFF06B6D4) else Color(0xFFA855F7)
                            )
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = when (status) {
                                        AssistantStatus.LISTENING -> listOf(Color(0xFF06B6D4), Color(0xFF3B82F6))
                                        AssistantStatus.SPEAKING -> listOf(Color(0xFFEC4899), Color(0xFFA855F7))
                                        AssistantStatus.CONNECTING -> listOf(Color(0xFFF59E0B), Color(0xFFFB923C))
                                        else -> listOf(Color(0xFFA855F7), Color(0xFF6366F1))
                                    }
                                )
                            )
                            .clickable(onClick = onMicClick)
                            .testTag("mic_power_button")
                    ) {
                        Icon(
                            imageVector = if (status == AssistantStatus.LISTENING) Icons.Default.Mic else Icons.Default.MicOff,
                            contentDescription = "Microphone Toggle",
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }
            }
        }
    }

    // Settings Dialog
    if (showSettingsDialog) {
        SettingsDialog(
            settings = settings,
            onSelectVoice = { newVoice -> viewModel.updateVoice(newVoice) },
            onDismiss = { showSettingsDialog = false }
        )
    }

    // Diagnostic Logs Sheet
    if (showLogsSheet) {
        DiagnosticLogsSheet(
            logs = logs,
            onDismiss = { showLogsSheet = false }
        )
    }
}
