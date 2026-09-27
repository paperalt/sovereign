package org.sovereign.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import org.sovereign.app.data.MeetingRepository
import org.sovereign.app.network.FullTranscriptDto
import org.sovereign.app.network.QuestionSuggestionResponseDto
import org.sovereign.app.network.TranscriptChunkDto
import kotlinx.coroutines.launch
import java.util.Locale

private val OnyxBlack = Color(0xFF0A0D12)
private val DarkSlate = Color(0xFF141A22)
private val SteelBorder = Color(0xFF283342)
private val TextPrimary = Color(0xFFF0F4F8)
private val TextSecondary = Color(0xFF94A3B8)
private val TextMuted = Color(0xFF64748B)
private val AccentPrimary = Color(0xFF38BDF8)
private val CrimsonAlert = Color(0xFFE11D48)

@Composable
fun MeetingDetailScreen(
    meetingId: String,
    meetingRepository: MeetingRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var transcriptData by remember { mutableStateOf<FullTranscriptDto?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableStateOf(0) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var editingChunk by remember { mutableStateOf<TranscriptChunkDto?>(null) }
    var showEditFullDialog by remember { mutableStateOf(false) }
    var isSavingEdit by remember { mutableStateOf(false) }
    var isRegeneratingSummary by remember { mutableStateOf(false) }

    fun reloadMeetingData() {
        scope.launch {
            val res = meetingRepository.getTranscript(meetingId)
            transcriptData = res.getOrNull()
        }
    }

    LaunchedEffect(meetingId) {
        isLoading = true
        val res = meetingRepository.getTranscript(meetingId)
        transcriptData = res.getOrNull()
        isLoading = false
    }

    // Edit Individual Chunk Dialog
    if (editingChunk != null) {
        val chunk = editingChunk!!
        var editedText by remember(chunk.id) { mutableStateOf(chunk.rawText) }

        AlertDialog(
            onDismissRequest = { if (!isSavingEdit) editingChunk = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            containerColor = DarkSlate,
            shape = RoundedCornerShape(8.dp),
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "EDIT TRANSCRIPT SEGMENT",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = OnyxBlack,
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, SteelBorder)
                    ) {
                        Text(
                            text = "[${formatSeconds(chunk.startTimeSec)} - ${formatSeconds(chunk.endTimeSec)}]",
                            color = AccentPrimary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            },
            text = {
                Column {
                    Text(
                        text = "Correct terms or words misrecognized by the ASR engine:",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = editedText,
                        onValueChange = { editedText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp, max = 220.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = AccentPrimary,
                            unfocusedBorderColor = SteelBorder,
                            cursorColor = AccentPrimary,
                            focusedContainerColor = OnyxBlack,
                            unfocusedContainerColor = OnyxBlack
                        ),
                        shape = RoundedCornerShape(6.dp),
                        textStyle = TextStyle(fontSize = 13.sp, lineHeight = 18.sp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = editedText.trim()
                        if (trimmed.isNotBlank() && !isSavingEdit) {
                            isSavingEdit = true
                            scope.launch {
                                val res = meetingRepository.updateChunk(meetingId, chunk.id, trimmed)
                                if (res.isSuccess) {
                                    transcriptData = transcriptData?.let { current ->
                                        val newChunks = current.chunks.map { c ->
                                            if (c.id == chunk.id) c.copy(rawText = trimmed) else c
                                        }
                                        val newFullText = newChunks.joinToString(" ") { it.rawText }
                                        current.copy(chunks = newChunks, fullText = newFullText)
                                    }
                                    Toast.makeText(context, "Transcript segment updated", Toast.LENGTH_SHORT).show()
                                    editingChunk = null
                                } else {
                                    Toast.makeText(context, "Failed to update: ${res.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                                }
                                isSavingEdit = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                    shape = RoundedCornerShape(4.dp),
                    enabled = !isSavingEdit && editedText.isNotBlank()
                ) {
                    Text(if (isSavingEdit) "SAVING..." else "SAVE", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { editingChunk = null },
                    border = BorderStroke(1.dp, SteelBorder),
                    shape = RoundedCornerShape(4.dp),
                    enabled = !isSavingEdit
                ) {
                    Text("CANCEL", color = TextPrimary, fontSize = 12.sp)
                }
            }
        )
    }

    // Edit Full Transcript Dialog
    if (showEditFullDialog && transcriptData != null) {
        val currentFull = transcriptData!!.fullText.ifBlank {
            transcriptData!!.chunks.joinToString(" ") { it.rawText }
        }
        var editedFullText by remember(showEditFullDialog, currentFull) { mutableStateOf(currentFull) }

        AlertDialog(
            onDismissRequest = { if (!isSavingEdit) showEditFullDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            containerColor = DarkSlate,
            shape = RoundedCornerShape(8.dp),
            title = {
                Text(
                    text = "EDIT FULL TRANSCRIPT",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Edit the full session transcript document:",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = editedFullText,
                        onValueChange = { editedFullText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 160.dp, max = 320.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = AccentPrimary,
                            unfocusedBorderColor = SteelBorder,
                            cursorColor = AccentPrimary,
                            focusedContainerColor = OnyxBlack,
                            unfocusedContainerColor = OnyxBlack
                        ),
                        shape = RoundedCornerShape(6.dp),
                        textStyle = TextStyle(fontSize = 13.sp, lineHeight = 18.sp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = editedFullText.trim()
                        if (trimmed.isNotBlank() && !isSavingEdit) {
                            isSavingEdit = true
                            scope.launch {
                                val res = meetingRepository.updateFullTranscript(meetingId, trimmed)
                                if (res.isSuccess) {
                                    Toast.makeText(context, "Full transcript saved successfully", Toast.LENGTH_SHORT).show()
                                    showEditFullDialog = false
                                    reloadMeetingData()
                                } else {
                                    Toast.makeText(context, "Failed to update: ${res.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                                }
                                isSavingEdit = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                    shape = RoundedCornerShape(4.dp),
                    enabled = !isSavingEdit && editedFullText.isNotBlank()
                ) {
                    Text(if (isSavingEdit) "SAVING..." else "SAVE CHANGES", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showEditFullDialog = false },
                    border = BorderStroke(1.dp, SteelBorder),
                    shape = RoundedCornerShape(4.dp),
                    enabled = !isSavingEdit
                ) {
                    Text("CANCEL", color = TextPrimary, fontSize = 12.sp)
                }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            containerColor = DarkSlate,
            shape = RoundedCornerShape(8.dp),
            title = { Text("DELETE THIS SESSION?", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            text = { Text("All recorded audio and transcript data will be permanently deleted.", color = TextSecondary, fontSize = 13.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        scope.launch {
                            val res = meetingRepository.deleteMeeting(meetingId)
                            if (res.isSuccess) {
                                Toast.makeText(context, "Session deleted successfully", Toast.LENGTH_SHORT).show()
                                onBack()
                            } else {
                                Toast.makeText(context, "Failed to delete session: ${res.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonAlert),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("DELETE", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showDeleteDialog = false },
                    border = BorderStroke(1.dp, SteelBorder),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("CANCEL", color = TextPrimary, fontSize = 12.sp)
                }
            }
        )
    }

    if (showRenameDialog && transcriptData != null) {
        var newTitle by remember(showRenameDialog, transcriptData!!.title) { mutableStateOf(transcriptData!!.title) }

        AlertDialog(
            onDismissRequest = { if (!isSavingEdit) showRenameDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            containerColor = DarkSlate,
            shape = RoundedCornerShape(8.dp),
            title = {
                Text(
                    text = "RENAME SESSION",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Update the session title:",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = AccentPrimary,
                            unfocusedBorderColor = SteelBorder,
                            cursorColor = AccentPrimary,
                            focusedContainerColor = OnyxBlack,
                            unfocusedContainerColor = OnyxBlack
                        ),
                        shape = RoundedCornerShape(6.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = newTitle.trim()
                        if (trimmed.isNotBlank() && !isSavingEdit) {
                            isSavingEdit = true
                            scope.launch {
                                val res = meetingRepository.updateMeetingTitle(meetingId, trimmed)
                                if (res.isSuccess) {
                                    transcriptData = transcriptData?.copy(title = trimmed)
                                    Toast.makeText(context, "Session title renamed successfully", Toast.LENGTH_SHORT).show()
                                    showRenameDialog = false
                                } else {
                                    Toast.makeText(context, "Failed to rename session: ${res.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                                }
                                isSavingEdit = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                    shape = RoundedCornerShape(4.dp),
                    enabled = !isSavingEdit && newTitle.isNotBlank()
                ) {
                    Text(if (isSavingEdit) "SAVING..." else "SAVE", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showRenameDialog = false },
                    border = BorderStroke(1.dp, SteelBorder),
                    shape = RoundedCornerShape(4.dp),
                    enabled = !isSavingEdit
                ) {
                    Text("CANCEL", color = TextPrimary, fontSize = 12.sp)
                }
            }
        )
    }

    Scaffold(
        containerColor = OnyxBlack,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSlate)
                    .border(1.dp, SteelBorder)
                    .statusBarsPadding()
                    .padding(top = 6.dp)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                    Text(
                        text = transcriptData?.title ?: "Session Details",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    IconButton(
                        onClick = { showRenameDialog = true },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Rename Session",
                            tint = TextSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Export Markdown Action
                    IconButton(onClick = {
                        transcriptData?.let { data ->
                            val md = buildMarkdown(data)
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Transcript Markdown", md))
                            Toast.makeText(context, "Copied in Markdown format", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy Markdown", tint = AccentPrimary)
                    }

                    // Delete Action
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = CrimsonAlert)
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Tab Header
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = DarkSlate,
                contentColor = AccentPrimary,
                divider = { HorizontalDivider(color = SteelBorder) }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    modifier = Modifier.height(38.dp),
                    text = { Text("TRANSCRIPT", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    modifier = Modifier.height(38.dp),
                    text = { Text("SUMMARY", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    modifier = Modifier.height(38.dp),
                    text = { Text("ACTION ITEMS", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false) }
                )
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    modifier = Modifier.height(38.dp),
                    text = { Text("INQUIRY", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false) }
                )
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AccentPrimary)
                }
            } else if (transcriptData == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Failed to load transcript.", color = TextMuted)
                }
            } else {
                val data = transcriptData!!

                when (selectedTab) {
                    0 -> {
                        // Transcript Chunks List with Edit actions
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "SEGMENTS (${data.chunks.size})",
                                        color = TextSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedButton(
                                        onClick = { showEditFullDialog = true },
                                        border = BorderStroke(1.dp, SteelBorder),
                                        shape = RoundedCornerShape(4.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = null,
                                            tint = AccentPrimary,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "EDIT FULL",
                                            color = AccentPrimary,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            softWrap = false
                                        )
                                    }
                                }
                            }

                            items(data.chunks, key = { it.id }) { chunk ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, SteelBorder, RoundedCornerShape(6.dp))
                                        .clickable { editingChunk = chunk },
                                    colors = CardDefaults.cardColors(containerColor = DarkSlate)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Text(
                                            text = formatSeconds(chunk.startTimeSec),
                                            color = TextMuted,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.width(48.dp)
                                        )
                                        Text(
                                            text = chunk.rawText,
                                            color = TextPrimary,
                                            fontSize = 14.sp,
                                            lineHeight = 20.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        IconButton(
                                            onClick = { editingChunk = chunk },
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Edit potongan",
                                                tint = TextSecondary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    1 -> {
                        // Executive Summary & Key Points
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            item {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, SteelBorder, RoundedCornerShape(6.dp)),
                                    colors = CardDefaults.cardColors(containerColor = DarkSlate)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "EXECUTIVE SUMMARY",
                                                color = AccentPrimary,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.weight(1f)
                                            )
                                            OutlinedButton(
                                                onClick = {
                                                    if (!isRegeneratingSummary) {
                                                        isRegeneratingSummary = true
                                                        scope.launch {
                                                            val res = meetingRepository.summarizeMeeting(meetingId)
                                                            if (res.isSuccess) {
                                                                Toast.makeText(context, "Summary updated successfully", Toast.LENGTH_SHORT).show()
                                                                reloadMeetingData()
                                                            } else {
                                                                Toast.makeText(context, "Failed to generate summary: ${res.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                                                            }
                                                            isRegeneratingSummary = false
                                                        }
                                                    }
                                                },
                                                border = BorderStroke(1.dp, SteelBorder),
                                                shape = RoundedCornerShape(4.dp),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                modifier = Modifier.height(26.dp),
                                                enabled = !isRegeneratingSummary
                                            ) {
                                                Icon(
                                                    Icons.Default.Refresh,
                                                    contentDescription = null,
                                                    tint = AccentPrimary,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = if (isRegeneratingSummary) "PROCESSING..." else "REGENERATE",
                                                    color = AccentPrimary,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    softWrap = false
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        val rawSummary = data.structuredSummary?.executiveSummary ?: (data.summary ?: "No executive summary available.")
                                        val cleanSummary = remember(rawSummary) {
                                            cleanExecutiveSummaryText(rawSummary)
                                        }
                                        Text(
                                            text = cleanSummary,
                                            color = TextPrimary,
                                            fontSize = 14.sp,
                                            lineHeight = 22.sp
                                        )
                                    }
                                }
                            }

                            data.structuredSummary?.keyPoints?.let { points ->
                                item {
                                    Text("KEY DISCUSSION POINTS", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                items(points) { point ->
                                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                        Text("• ", color = AccentPrimary, fontSize = 14.sp)
                                        Text(
                                            text = point,
                                            color = TextPrimary,
                                            fontSize = 14.sp,
                                            lineHeight = 20.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    2 -> {
                        // Action Items
                        val items = data.structuredSummary?.actionItems ?: emptyList()
                        if (items.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No action items detected.", color = TextMuted, fontSize = 13.sp)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(items) { item ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(1.dp, SteelBorder, RoundedCornerShape(6.dp)),
                                        colors = CardDefaults.cardColors(containerColor = DarkSlate)
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            Text(
                                                text = item.task,
                                                color = TextPrimary,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                lineHeight = 18.sp
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Assignee: ${item.assignee ?: "-"}",
                                                    color = TextMuted,
                                                    fontSize = 11.sp,
                                                    modifier = Modifier.weight(1f),
                                                    maxLines = 1,
                                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = item.status ?: "PENDING",
                                                    color = AccentPrimary,
                                                    fontSize = 11.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    3 -> {
                        QuestionSuggestionTabContent(
                            meetingId = meetingId,
                            meetingRepository = meetingRepository
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuestionSuggestionTabContent(
    meetingId: String,
    meetingRepository: MeetingRepository
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedWindow by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var resultData by remember { mutableStateOf<QuestionSuggestionResponseDto?>(null) }

    fun loadQuestions(windowMinutes: Int) {
        scope.launch {
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
    }

    LaunchedEffect(meetingId, selectedWindow) {
        loadQuestions(selectedWindow)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Window selector chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                5 to "5 Min",
                15 to "15 Min",
                30 to "30 Min",
                0 to "All Sessions"
            ).forEach { (win, label) ->
                val isSelected = selectedWindow == win
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (isSelected) AccentPrimary.copy(alpha = 0.2f) else DarkSlate,
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

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = AccentPrimary, modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Formulating inquiry based on discussion...", color = TextMuted, fontSize = 12.sp)
                }
            }
        } else if (errorMessage != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(errorMessage ?: "Terjadi kesalahan", color = CrimsonAlert, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { loadQuestions(selectedWindow) },
                        shape = RoundedCornerShape(4.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SteelBorder)
                    ) {
                        Text("Coba Lagi", color = TextPrimary, fontSize = 12.sp)
                    }
                }
            }
        } else if (resultData != null) {
            val res = resultData!!
            if (!res.hasSufficientContext) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E1E14), RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .padding(14.dp)
                ) {
                    Column {
                        Text(
                            text = "INSUFFICIENT CONTEXT",
                            color = Color(0xFFF59E0B),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = res.message ?: "Not enough material in this range to formulate specific questions.",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            } else if (res.suggestions.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No question suggestions yet.", color = TextMuted, fontSize = 12.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(res.suggestions) { item ->
                        QuestionCard(
                            index = res.suggestions.indexOf(item) + 1,
                            item = item,
                            onCopy = { q ->
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Meeting Questions", q))
                                Toast.makeText(context, "Question copied to clipboard", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun formatSeconds(seconds: Double): String {
    val safeSeconds = if (seconds.isNaN() || seconds < 0) 0 else seconds.toInt()
    val m = safeSeconds / 60
    val sec = safeSeconds % 60
    return String.format(Locale.US, "%02d:%02d", m, sec)
}

private fun buildMarkdown(data: FullTranscriptDto): String {
    val sb = StringBuilder()
    sb.append("# ${data.title}\n\n")
    sb.append("**Date:** ${data.startedAt.take(10)} | **Duration:** ${data.durationSec.toInt()}s | **Language:** ${data.language.uppercase()}\n\n")

    if (!data.summary.isNullOrBlank()) {
        sb.append("## Executive Summary\n")
        sb.append("${data.summary}\n\n")
    }

    data.structuredSummary?.actionItems?.let { items ->
        if (items.isNotEmpty()) {
            sb.append("## Action Items\n")
            items.forEach {
                sb.append("- [ ] **${it.task}** (Assignee: ${it.assignee ?: "Unassigned"})\n")
            }
            sb.append("\n")
        }
    }

    sb.append("## Full Transcript\n\n")
    data.chunks.forEach { chunk ->
        sb.append("**[${formatSeconds(chunk.startTimeSec)}]** ${chunk.rawText}\n\n")
    }

    return sb.toString()
}

internal fun cleanExecutiveSummaryText(raw: String?): String {
    if (raw.isNullOrBlank()) return "No summary yet."
    var text = raw.trim()
    val preambleRegex = Regex("""(?is)^[\s*#_\-]*(?:(?:sure[,!]?\s*|certainly[,!]?\s*|here\s+is\s+|below\s+is\s+|this\s+is\s+|tentu[,!]?\s*|berikut\s+(?:ini\s+)?(?:adalah\s+)?|ini\s+adalah\s+|berdasarkan\s+[^\n:]*|dari\s+[^\n:]*)[^\n:]*(?:summary|overview|analysis|ringkasan|rangkuman|kesimpulan|poin|ulasan|hasil|analisis|laporan|executive\s+summary)[^\n:]*[:\n\-]+)\s*""")
    val headingRegex = Regex("""(?i)^[\s*#_\-]*(?:ringkasan(?:\s+eksekutif)?|executive\s+summary|summary)\s*(?:\([^\)]*\))?\s*[:*#_\-\s]*\s*""")

    var changed = true
    while (changed) {
        val old = text
        text = text.replace(preambleRegex, "").trim()
        text = text.replace(headingRegex, "").trim()
        changed = (text != old)
    }
    return text.replace(Regex("""[*#_`]"""), "").trim()
}
