package org.sovereign.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import org.sovereign.app.auth.TokenStorage
import org.sovereign.app.data.backup.BackupInventory
import org.sovereign.app.data.backup.BackupManager
import org.sovereign.app.data.backup.BackupPayload
import java.text.SimpleDateFormat
import java.util.*

private val OnyxBlack = Color(0xFF0A0D12)
private val DarkSlate = Color(0xFF141A22)
private val CardBackground = Color(0xFF0F151E)
private val SteelBorder = Color(0xFF283342)
private val TextPrimary = Color(0xFFF0F4F8)
private val TextSecondary = Color(0xFF94A3B8)
private val TextMuted = Color(0xFF64748B)
private val AccentPrimary = Color(0xFF38BDF8)
private val EmeraldSuccess = Color(0xFF10B981)
private val AmberWarning = Color(0xFFF59E0B)
private val CrimsonAlert = Color(0xFFEF4444)

@Composable
fun BackupRestoreDialog(
    tokenStorage: TokenStorage,
    onDismiss: () -> Unit,
    onDataRestored: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = EXPORT, 1 = RESTORE

    // Export State
    var inventory by remember { mutableStateOf<BackupInventory?>(null) }
    var includeKeysInExport by remember { mutableStateOf(true) }
    var isExporting by remember { mutableStateOf(false) }

    // Restore State
    var loadedJsonText by remember { mutableStateOf<String?>(null) }
    var parsedPayload by remember { mutableStateOf<BackupPayload?>(null) }
    var parseError by remember { mutableStateOf<String?>(null) }
    var replaceExistingData by remember { mutableStateOf(false) }
    var restoreSettingsAndKeys by remember { mutableStateOf(true) }
    var isRestoring by remember { mutableStateOf(false) }

    // Export File Launcher (Create Document)
    val createDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isExporting = true
                val res = BackupManager.exportBackup(context, tokenStorage, includeKeysInExport)
                isExporting = false
                res.onSuccess { jsonStr ->
                    try {
                        context.contentResolver.openOutputStream(uri)?.use { os ->
                            os.write(jsonStr.toByteArray(Charsets.UTF_8))
                            os.flush()
                        }
                        Toast.makeText(context, "Backup exported successfully", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Write error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }.onFailure { err ->
                    Toast.makeText(context, "Export failed: ${err.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Import File Launcher (Get Content)
    val openDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val text = inputStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                loadedJsonText = text
                val parseRes = BackupManager.parseBackup(text)
                if (parseRes.isSuccess) {
                    parsedPayload = parseRes.getOrNull()
                    parseError = null
                } else {
                    parsedPayload = null
                    parseError = parseRes.exceptionOrNull()?.message ?: "Invalid backup file structure"
                }
            } catch (e: Exception) {
                parseError = "Read error: ${e.message}"
            }
        }
    }

    // Load inventory on appear
    LaunchedEffect(Unit) {
        inventory = BackupManager.getInventory(context, tokenStorage)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.78f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSlate),
                border = BorderStroke(1.dp, SteelBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f)
                    .padding(horizontal = 16.dp, vertical = 20.dp)
                    .clickable(enabled = false) {} // Prevent dismiss when tapping card content
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(DarkSlate)
                ) {
                    // Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(AccentPrimary.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Backup, contentDescription = null, tint = AccentPrimary, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "BACKUP & RESTORE DATA",
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                                Text(
                                    text = "Local database migration & endpoint protection",
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        IconButton(onClick = onDismiss, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary, modifier = Modifier.size(18.dp))
                        }
                    }

                    HorizontalDivider(color = SteelBorder.copy(alpha = 0.6f))

                    // Segmented Tabs
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                            .background(CardBackground, RoundedCornerShape(8.dp))
                            .border(1.dp, SteelBorder.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        BackupTabButton(
                            icon = Icons.Default.FileDownload,
                            title = "EXPORT DATA",
                            isSelected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            modifier = Modifier.weight(1f)
                        )
                        BackupTabButton(
                            icon = Icons.Default.Restore,
                            title = "RESTORE DATA",
                            isSelected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Body Content
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (selectedTab == 0) {
                                // EXPORT TAB CONTENT
                                ExportTabSection(
                                    inventory = inventory,
                                    includeKeys = includeKeysInExport,
                                    onIncludeKeysChange = { includeKeysInExport = it },
                                    isExporting = isExporting,
                                    onExportToFile = {
                                        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                                        createDocLauncher.launch("sovereign_backup_${timestamp}.json")
                                    },
                                    onShareDirectly = {
                                        scope.launch {
                                            isExporting = true
                                            val res = BackupManager.exportBackup(context, tokenStorage, includeKeysInExport)
                                            isExporting = false
                                            res.onSuccess { jsonStr ->
                                                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "application/json"
                                                    putExtra(Intent.EXTRA_SUBJECT, "Sovereign Backup - $timestamp")
                                                    putExtra(Intent.EXTRA_TEXT, jsonStr)
                                                }
                                                context.startActivity(Intent.createChooser(sendIntent, "Share Sovereign Backup"))
                                            }.onFailure {
                                                Toast.makeText(context, "Export error: ${it.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                )
                            } else {
                                // RESTORE TAB CONTENT
                                RestoreTabSection(
                                    parsedPayload = parsedPayload,
                                    parseError = parseError,
                                    replaceExisting = replaceExistingData,
                                    onReplaceExistingChange = { replaceExistingData = it },
                                    restoreSettings = restoreSettingsAndKeys,
                                    onRestoreSettingsChange = { restoreSettingsAndKeys = it },
                                    isRestoring = isRestoring,
                                    onSelectFile = {
                                        openDocLauncher.launch("application/json")
                                    },
                                    onApplyRestore = {
                                        if (parsedPayload != null) {
                                            scope.launch {
                                                isRestoring = true
                                                val res = BackupManager.applyBackup(
                                                    context = context,
                                                    tokenStorage = tokenStorage,
                                                    payload = parsedPayload!!,
                                                    replaceExisting = replaceExistingData,
                                                    restoreSettings = restoreSettingsAndKeys
                                                )
                                                isRestoring = false
                                                res.onSuccess { summary ->
                                                    Toast.makeText(
                                                        context,
                                                        "Restored ${summary.meetingsRestored} meetings & ${summary.chunksRestored} transcripts",
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                    onDataRestored()
                                                    onDismiss()
                                                }.onFailure { err ->
                                                    Toast.makeText(context, "Restore failed: ${err.message}", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = SteelBorder.copy(alpha = 0.6f))

                    // Footer Action
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Button(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                        ) {
                            Text("CLOSE", color = OnyxBlack, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExportTabSection(
    inventory: BackupInventory?,
    includeKeys: Boolean,
    onIncludeKeysChange: (Boolean) -> Unit,
    isExporting: Boolean,
    onExportToFile: () -> Unit,
    onShareDirectly: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Current Inventory Card
        Card(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            border = BorderStroke(1.dp, SteelBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LOCAL DATA INVENTORY",
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Box(
                        modifier = Modifier
                            .background(AccentPrimary.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .border(1.dp, AccentPrimary.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("READY TO EXPORT", color = AccentPrimary, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                }

                HorizontalDivider(color = SteelBorder.copy(alpha = 0.4f))

                if (inventory != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DataStatBadge("MEETINGS", "${inventory.meetingsCount}", Modifier.weight(1f))
                        DataStatBadge("TRANSCRIPTS", "${inventory.chunksCount}", Modifier.weight(1f))
                        DataStatBadge("SUMMARIES", "${inventory.summariesCount}", Modifier.weight(1f))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DataStatBadge("GROUPS", "${inventory.groupsCount}", Modifier.weight(1f))
                        DataStatBadge("STT CONFIGS", "${inventory.sttConfigsCount}", Modifier.weight(1f))
                        DataStatBadge("LLM CONFIGS", "${inventory.llmConfigsCount}", Modifier.weight(1f))
                    }
                } else {
                    Box(modifier = Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AccentPrimary, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
            }
        }

        // Include API Keys Switch
        Card(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            border = BorderStroke(1.dp, SteelBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Include API Keys in Backup",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "Package active provider credentials into the backup for seamless multi-device migration without re-entering keys.",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = includeKeys,
                    onCheckedChange = onIncludeKeysChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = OnyxBlack,
                        checkedTrackColor = AccentPrimary,
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = DarkSlate
                    )
                )
            }
        }

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onExportToFile,
                enabled = !isExporting,
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
            ) {
                if (isExporting) {
                    CircularProgressIndicator(color = OnyxBlack, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Download, contentDescription = null, tint = OnyxBlack, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "EXPORT FILE",
                        color = OnyxBlack,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            OutlinedButton(
                onClick = onShareDirectly,
                enabled = !isExporting,
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, SteelBorder),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "SHARE FILE",
                    color = TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        // Enterprise Info Card
        Card(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            border = BorderStroke(1.dp, SteelBorder.copy(alpha = 0.6f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Exported JSON files contain your complete meeting history, transcript segments, and customized pipeline configurations.",
                    color = TextMuted,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

@Composable
private fun RestoreTabSection(
    parsedPayload: BackupPayload?,
    parseError: String?,
    replaceExisting: Boolean,
    onReplaceExistingChange: (Boolean) -> Unit,
    restoreSettings: Boolean,
    onRestoreSettingsChange: (Boolean) -> Unit,
    isRestoring: Boolean,
    onSelectFile: () -> Unit,
    onApplyRestore: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (parsedPayload == null) {
            // No file selected yet
            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                border = BorderStroke(1.dp, SteelBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .background(DarkSlate, RoundedCornerShape(8.dp))
                            .border(1.dp, SteelBorder, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = null, tint = AccentPrimary, modifier = Modifier.size(22.dp))
                    }
                    Text(
                        text = "SELECT BACKUP JSON FILE",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "Choose a previously exported sovereign_backup_*.json file from your device storage or cloud drive to inspect and restore.",
                        color = TextMuted,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = onSelectFile,
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                        modifier = Modifier.height(40.dp)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, tint = OnyxBlack, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "BROWSE BACKUP FILE",
                            color = OnyxBlack,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    if (parseError != null) {
                        Text(
                            text = parseError,
                            color = CrimsonAlert,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else {
            // Backup File Verified Card
            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                border = BorderStroke(1.dp, EmeraldSuccess),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "BACKUP FILE VERIFIED",
                            color = EmeraldSuccess,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Box(
                            modifier = Modifier
                                .background(EmeraldSuccess.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .border(1.dp, EmeraldSuccess.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("VALID FORMAT", color = EmeraldSuccess, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                    }

                    HorizontalDivider(color = SteelBorder.copy(alpha = 0.4f))

                    Text(
                        text = "Exported: ${parsedPayload.metadata.exportedAt.take(19).replace('T', ' ')} UTC",
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    if (parsedPayload.metadata.deviceModel.isNotBlank()) {
                        Text(
                            text = "Source Device: ${parsedPayload.metadata.deviceModel}",
                            color = TextSecondary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DataStatBadge("MEETINGS", "${parsedPayload.meetings.size}", Modifier.weight(1f))
                        DataStatBadge("TRANSCRIPTS", "${parsedPayload.chunks.size}", Modifier.weight(1f))
                        DataStatBadge("SUMMARIES", "${parsedPayload.summaries.size}", Modifier.weight(1f))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DataStatBadge("GROUPS", "${parsedPayload.groups.size}", Modifier.weight(1f))
                        DataStatBadge("STT CONFIGS", "${parsedPayload.settings?.sttConfigs?.size ?: 0}", Modifier.weight(1f))
                        DataStatBadge("LLM CONFIGS", "${parsedPayload.settings?.llmConfigs?.size ?: 0}", Modifier.weight(1f))
                    }
                }
            }

            // Restore Strategy Card
            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                border = BorderStroke(1.dp, SteelBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "RESTORE STRATEGY",
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    HorizontalDivider(color = SteelBorder.copy(alpha = 0.4f))

                    // Clean Replace Switch
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onReplaceExistingChange(!replaceExisting) }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Clean Replace (Wipe Existing Data)", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (replaceExisting) "Caution: Current on-device recordings will be erased before restoring"
                                       else "Merge backup records into existing database",
                                color = if (replaceExisting) AmberWarning else TextMuted,
                                fontSize = 10.sp,
                                fontWeight = if (replaceExisting) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Switch(
                            checked = replaceExisting,
                            onCheckedChange = onReplaceExistingChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = OnyxBlack,
                                checkedTrackColor = AmberWarning,
                                uncheckedThumbColor = TextMuted,
                                uncheckedTrackColor = DarkSlate
                            )
                        )
                    }

                    HorizontalDivider(color = SteelBorder.copy(alpha = 0.3f))

                    // Restore Settings Switch
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onRestoreSettingsChange(!restoreSettings) }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Restore AI Endpoints & API Keys", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("Applies configured STT/LLM endpoints and keys from backup", color = TextMuted, fontSize = 10.sp)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Switch(
                            checked = restoreSettings,
                            onCheckedChange = onRestoreSettingsChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = OnyxBlack,
                                checkedTrackColor = AccentPrimary,
                                uncheckedThumbColor = TextMuted,
                                uncheckedTrackColor = DarkSlate
                            )
                        )
                    }
                }
            }

            // Restore Execution Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onSelectFile,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, SteelBorder),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                ) {
                    Text(
                        text = "CHANGE FILE",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                Button(
                    onClick = onApplyRestore,
                    enabled = !isRestoring,
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                ) {
                    if (isRestoring) {
                        CircularProgressIndicator(color = OnyxBlack, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Check, contentDescription = null, tint = OnyxBlack, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "APPLY RESTORE",
                            color = OnyxBlack,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
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

@Composable
private fun DataStatBadge(
    label: String,
    count: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(DarkSlate, RoundedCornerShape(6.dp))
            .border(1.dp, SteelBorder.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = TextMuted,
            fontSize = 8.5.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = count,
            color = TextPrimary,
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun BackupTabButton(
    icon: ImageVector,
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                if (isSelected) AccentPrimary.copy(alpha = 0.15f) else Color.Transparent,
                shape = RoundedCornerShape(6.dp)
            )
            .border(
                1.dp,
                if (isSelected) AccentPrimary.copy(alpha = 0.8f) else Color.Transparent,
                shape = RoundedCornerShape(6.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) AccentPrimary else TextMuted,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                color = if (isSelected) AccentPrimary else TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
