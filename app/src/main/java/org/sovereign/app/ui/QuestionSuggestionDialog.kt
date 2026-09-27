package org.sovereign.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import org.sovereign.app.data.MeetingRepository
import org.sovereign.app.network.QuestionSuggestionDto
import org.sovereign.app.network.QuestionSuggestionResponseDto
import kotlinx.coroutines.launch

private val OnyxBlack = Color(0xFF0A0D12)
private val DarkSlate = Color(0xFF141A22)
private val SteelBorder = Color(0xFF283342)
private val TextPrimary = Color(0xFFF0F4F8)
private val TextSecondary = Color(0xFF94A3B8)
private val TextMuted = Color(0xFF64748B)
private val AccentPrimary = Color(0xFF38BDF8)
private val EmeraldClarify = Color(0xFF10B981)
private val AmberEdgeCase = Color(0xFFF59E0B)
private val IndigoImpact = Color(0xFF818CF8)
private val CrimsonAlert = Color(0xFFEF4444)

@Composable
fun QuestionSuggestionDialog(
    meetingId: String,
    meetingRepository: MeetingRepository,
    onDismiss: () -> Unit,
    initialWindowMinutes: Int = 15
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedWindow by remember { mutableStateOf(initialWindowMinutes) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var resultData by remember { mutableStateOf<QuestionSuggestionResponseDto?>(null) }

    suspend fun loadQuestions(windowMinutes: Int) {
        isLoading = true
        errorMessage = null
        val res = meetingRepository.suggestQuestions(meetingId, windowMinutes)
        if (res.isSuccess) {
            resultData = res.getOrNull()
        } else {
            errorMessage = res.exceptionOrNull()?.message ?: "Failed to generate questions"
        }
        isLoading = false
    }

    LaunchedEffect(meetingId, selectedWindow) {
        loadQuestions(selectedWindow)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        containerColor = DarkSlate,
        shape = RoundedCornerShape(12.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "IN-MEETING INQUIRY",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Context-grounded inquiries with source citations",
                        color = TextMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
            ) {
                // 1. Modular Time Window Selector Chips
                Text(
                    text = "DISCUSSION WINDOW:",
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        5 to "5 Min",
                        15 to "15 Min",
                        30 to "30 Min",
                        0 to "Full Session"
                    ).forEach { (win, label) ->
                        val isSelected = selectedWindow == win
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    if (isSelected) AccentPrimary.copy(alpha = 0.2f) else Color(0xFF0F141C),
                                    RoundedCornerShape(6.dp)
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) AccentPrimary else SteelBorder,
                                    RoundedCornerShape(6.dp)
                                )
                                .clickable(enabled = !isLoading) {
                                    if (selectedWindow != win) {
                                        selectedWindow = win
                                    }
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) AccentPrimary else TextSecondary,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = SteelBorder)
                Spacer(modifier = Modifier.height(12.dp))

                // 2. Content Area
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = AccentPrimary,
                                strokeWidth = 3.dp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Analyzing transcript & formulating questions...",
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                        }
                    }
                } else if (errorMessage != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = errorMessage ?: "An unexpected error occurred",
                                color = CrimsonAlert,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { scope.launch { loadQuestions(selectedWindow) } },
                                shape = RoundedCornerShape(4.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, SteelBorder),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("RETRY", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else if (resultData != null) {
                    val res = resultData!!
                    if (!res.hasSufficientContext) {
                        // Insufficient Context Guard Warning Card
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF1E1E14), RoundedCornerShape(8.dp))
                                .border(1.dp, AmberEdgeCase.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                .padding(14.dp)
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(AmberEdgeCase, RoundedCornerShape(4.dp))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "INSUFFICIENT TRANSCRIPT CONTEXT",
                                        color = AmberEdgeCase,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = res.message ?: "Transcript in this window is not yet substantive enough to formulate grounded inquiries.",
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Recommendation: Continue recording, or select 'Full Session' to analyze from the start.",
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    } else if (res.suggestions.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No question suggestions available yet.", color = TextMuted, fontSize = 12.sp)
                        }
                    } else {
                        // Display Question Cards
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            res.suggestions.forEachIndexed { index, item ->
                                QuestionCard(
                                    index = index + 1,
                                    item = item,
                                    onCopy = { q ->
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Inquiry", q))
                                        Toast.makeText(context, "Question copied to clipboard", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(6.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, SteelBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                Text(
                    text = "CLOSE",
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
        }
    )
}

@Composable
fun QuestionCard(
    index: Int,
    item: QuestionSuggestionDto,
    onCopy: (String) -> Unit
) {
    val (badgeText, badgeColor) = when (item.category.lowercase()) {
        "critical_edge_case" -> "CRITICAL / EDGE-CASE" to AmberEdgeCase
        "practical_impact" -> "PRACTICAL IMPACT" to IndigoImpact
        else -> "CONCEPT CLARIFICATION" to EmeraldClarify
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, SteelBorder, RoundedCornerShape(8.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F141C)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header: Category Badge & Copy Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .background(badgeColor.copy(alpha = 0.15f), RoundedCornerShape(3.dp))
                        .border(1.dp, badgeColor, RoundedCornerShape(3.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = badgeText,
                        color = badgeColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                IconButton(
                    onClick = { onCopy(item.question) },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy Question",
                        tint = AccentPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Main Question Text
            Text(
                text = "${index}. \"${item.question}\"",
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 20.sp
            )

            // Grounding Context Reference
            if (item.contextRef.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF141A22), RoundedCornerShape(4.dp))
                        .border(1.dp, SteelBorder, RoundedCornerShape(4.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Column {
                        Text(
                            text = "SOURCE CONTEXT (VERBATIM):",
                            color = TextMuted,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "“${item.contextRef}”",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontStyle = FontStyle.Italic,
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            // Thought starter / Moment advice
            item.thoughtStarter?.let { starter ->
                if (starter.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Recommendation: $starter",
                        color = TextMuted,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }
}
