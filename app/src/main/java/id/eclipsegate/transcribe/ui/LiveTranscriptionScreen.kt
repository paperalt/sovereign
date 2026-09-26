package id.eclipsegate.transcribe.ui

import android.app.Activity
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import id.eclipsegate.transcribe.data.MeetingRepository
import id.eclipsegate.transcribe.service.TranscriptionService

// Design Tokens (Monochrome Industrial Theme)
private val OnyxBlack = Color(0xFF0A0D12)
private val DarkSlate = Color(0xFF141A22)
private val SteelBorder = Color(0xFF283342)
private val TextPrimary = Color(0xFFF0F4F8)
private val TextSecondary = Color(0xFF94A3B8)
private val TextMuted = Color(0xFF64748B)
private val AccentPrimary = Color(0xFF38BDF8)
private val CrimsonAlert = Color(0xFFE11D48)
private val EmeraldConnected = Color(0xFF10B981)
private val AmberWarning = Color(0xFFF59E0B)
private val PillBackground = Color(0xFF1E2632)

data class TranscriptItem(
    val index: Int,
    val timeLabel: String,
    val text: String
)

@Composable
fun LiveTranscriptionScreen(
    meetingId: String = "",
    meetingRepository: MeetingRepository? = null,
    meetingTitle: String,
    elapsedTime: String,
    transcriptItems: List<TranscriptItem>,
    isRecording: Boolean,
    isPaused: Boolean = false,
    isFinalizing: Boolean = false,
    remainingQuotaSeconds: Int? = null,
    aiProvider: String = "DEFAULT",
    amplitudeSupplier: () -> FloatArray,
    onStopClicked: () -> Unit,
    onPauseClicked: () -> Unit,
    onCancelConfirmed: () -> Unit
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scrollController = rememberScrollAnchorController(listState)
    var showCancelDialog by remember { mutableStateOf(false) }
    var showQuestionDialog by remember { mutableStateOf(false) }
    var isKeepScreenOn by remember { mutableStateOf(true) }

    val isAdaptiveBeta by TranscriptionService.isAdaptiveBeta.collectAsState()
    val vadState by TranscriptionService.vadState.collectAsState()

    // Always-On Display (Keep screen awake while recording/transcribing)
    DisposableEffect(isKeepScreenOn) {
        val activity = context as? Activity
        if (isKeepScreenOn) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Intercept hardware/gesture Back button during active recording or finalizing
    BackHandler(enabled = true) {
        if (isFinalizing) {
            // Absorb back press while finalizing AI summary
        } else if (showQuestionDialog) {
            showQuestionDialog = false
        } else {
            showCancelDialog = true
        }
    }

    // Notify scroll anchor controller of new items
    LaunchedEffect(transcriptItems.size) {
        scrollController.onNewItemAppended(transcriptItems.size)
    }

    // Question Suggestion Dialog (Live in-meeting inquiry assistance)
    if (showQuestionDialog && meetingRepository != null && meetingId.isNotBlank()) {
        QuestionSuggestionDialog(
            meetingId = meetingId,
            meetingRepository = meetingRepository,
            onDismiss = { showQuestionDialog = false },
            initialWindowMinutes = 15
        )
    }

    // Cancellation Defense Dialog (Protects against accidental taps)
    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            containerColor = DarkSlate,
            shape = RoundedCornerShape(12.dp),
            title = {
                Text(
                    text = "Batalkan Sesi Transkripsi?",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Seluruh rekaman dan teks transkripsi dari sesi ini akan dihapus secara permanen dan tidak disimpan ke server.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCancelDialog = false
                        onCancelConfirmed()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonAlert),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("YA, BATALKAN", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showCancelDialog = false },
                    border = BorderStroke(1.dp, SteelBorder),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("TETAP REKAM", color = TextPrimary, fontSize = 12.sp)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OnyxBlack)
            .statusBarsPadding()
            .padding(top = 6.dp)
            .navigationBarsPadding()
    ) {
        // 1. Top Bar Header (Safe Area Resilient)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Action: Cancel
            OutlinedButton(
                onClick = { showCancelDialog = true },
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, SteelBorder),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Text("BATAL", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            // Center: Title and Live Indicator
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            ) {
                Text(
                    text = meetingTitle,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(
                                when {
                                    isFinalizing -> AmberWarning
                                    isPaused -> AmberWarning
                                    isRecording -> CrimsonAlert
                                    else -> EmeraldConnected
                                },
                                shape = RoundedCornerShape(3.dp)
                            )
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    val baseStatus = when {
                        isFinalizing -> "PROSES"
                        isPaused -> "DIJEDA"
                        isRecording && isAdaptiveBeta -> {
                            when (vadState) {
                                "SPEAKING" -> "VAD: AKTIF"
                                "SUPPRESSED" -> "VAD: HEMAT"
                                else -> "STREAM"
                            }
                        }
                        isRecording -> "STREAM"
                        else -> "SIAP"
                    }
                    val statusLabel = baseStatus
                    val metaLabel = if (aiProvider != "DEFAULT") {
                        "$statusLabel • $aiProvider"
                    } else if (remainingQuotaSeconds != null) {
                        "$statusLabel • ${formatQuotaHuman(remainingQuotaSeconds)}"
                    } else {
                        statusLabel
                    }
                    Text(
                        text = metaLabel,
                        color = if (isPaused) AmberWarning else if (aiProvider != "DEFAULT") AccentPrimary else TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }

            // Right Actions: AOD Toggle + Question Suggestions Trigger
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Always-On Display toggle chip
                OutlinedButton(
                    onClick = {
                        isKeepScreenOn = !isKeepScreenOn
                        val msg = if (isKeepScreenOn) "Always On Display: AKTIF (Layar tetap menyala)" else "Always On Display: NONAKTIF"
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, if (isKeepScreenOn) EmeraldConnected.copy(alpha = 0.8f) else SteelBorder),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (isKeepScreenOn) EmeraldConnected else TextMuted
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text(
                        text = if (isKeepScreenOn) "AOD ON" else "AOD OFF",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (meetingRepository != null && meetingId.isNotBlank()) {
                    Button(
                        onClick = { showQuestionDialog = true },
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentPrimary.copy(alpha = 0.15f),
                            contentColor = AccentPrimary
                        ),
                        border = BorderStroke(1.dp, AccentPrimary.copy(alpha = 0.7f)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("IDE TANYA", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 2. Audio Waveform Monitor (Seamless Studio Strip)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .height(28.dp)
        ) {
            AudioWaveformVisualizer(
                amplitudeSupplier = amplitudeSupplier,
                barColor = if (isRecording && !isPaused) AccentPrimary else TextMuted.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 3. Transcript Stream Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .background(DarkSlate, RoundedCornerShape(12.dp))
                .border(1.dp, SteelBorder, RoundedCornerShape(12.dp))
                .padding(14.dp)
        ) {
            if (transcriptItems.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Mendengarkan audio perkuliahan / rapat...",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Teks transkripsi akan muncul otomatis secara real-time.",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(transcriptItems, key = { it.index }) { item ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F141C)),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, SteelBorder.copy(alpha = 0.7f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .background(Color(0xFF1E2632), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = item.timeLabel,
                                            color = AccentPrimary,
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(
                                        text = "#${item.index + 1}",
                                        color = TextMuted,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = item.text,
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    lineHeight = 22.sp,
                                    fontWeight = FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }

            // Unread indicator pill when viewport is scrolled up
            if (scrollController.hasUnreadBelow) {
                Button(
                    onClick = { scrollController.scrollToBottom(transcriptItems.size) },
                    colors = ButtonDefaults.buttonColors(containerColor = PillBackground),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, SteelBorder),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ArrowDownward,
                            contentDescription = null,
                            tint = AccentPrimary,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("TEKS BARU", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 4. Bottom Control Console (Balanced & Ergonomic)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSlate),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, SteelBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Digital Monospace Timer
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = "DURASI REKAMAN",
                        color = TextMuted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = elapsedTime,
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Action Buttons
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = onPauseClicked,
                        enabled = !isFinalizing,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, if (isPaused) AccentPrimary else SteelBorder),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (isPaused) AccentPrimary else TextPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Text(
                            text = if (isPaused) "LANJUT" else "JEDA",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            softWrap = false
                        )
                    }

                    Button(
                        onClick = onStopClicked,
                        enabled = !isFinalizing,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentPrimary,
                            disabledContainerColor = AccentPrimary.copy(alpha = 0.5f),
                            contentColor = OnyxBlack,
                            disabledContentColor = TextMuted
                        ),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(38.dp)
                    ) {
                        if (isFinalizing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = OnyxBlack,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("FINALISASI...", color = OnyxBlack, fontSize = 11.sp, fontWeight = FontWeight.Bold, softWrap = false)
                        } else {
                            Text("SELESAIKAN", color = OnyxBlack, fontSize = 11.sp, fontWeight = FontWeight.Bold, softWrap = false)
                        }
                    }
                }
            }
        }
    }
}

// Formats seconds into human-digestible format (e.g. "25j 28m" or "28m 35s")
private fun formatQuotaHuman(seconds: Int): String {
    if (seconds <= 0) return "0m"
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return if (hours > 0) {
        "${hours}j ${minutes}m"
    } else {
        "${minutes}m ${seconds % 60}s"
    }
}
