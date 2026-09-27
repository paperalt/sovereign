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
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
                    parseError = parseRes.exceptionOrNull()?.message ?: "Invalid backup file"
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
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = OnyxBlack),
            border = BorderStroke(1.dp, SteelBorder),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(horizontal = 16.dp, vertical = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(OnyxBlack)
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
                                .size(34.dp)
                                .background(AccentPrimary.copy(alpha = 0.15f), RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Backup, contentDescription = null, tint = AccentPrimary, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "BACKUP & RESTORE DATA",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "Migrate or protect meetings, transcripts & keys",
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
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .background(CardBackground, RoundedCornerShape(8.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    BackupTabButton(
                        icon = Icons.Default.FileDownload,
                        title = "EXPORT BACKUP",
                        subtitle = "Save to file / cloud",
                        isSelected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        modifier = Modifier.weight(1f)
                    )
                    BackupTabButton(
                        icon = Icons.Default.Restore,
                        title = "RESTORE DATA",
                        subtitle = "Import from file",
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
                            .padding(vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
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
                                                    "Restored ${summary.meetingsRestored} meetings & ${summary.chunksRestored} transcripts!",
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

                // Footer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Text("Done", color = OnyxBlack, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            border = BorderStroke(1.dp, SteelBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "DATA INVENTORY READY FOR EXPORT",
                    color = AccentPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                HorizontalDivider(color = SteelBorder.copy(alpha = 0.4f))

                if (inventory != null) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        DataStatBadge("MEETINGS", "${inventory.meetingsCount}")
                        DataStatBadge("TRANSCRIPTS", "${inventory.chunksCount}")
                        DataStatBadge("SUMMARIES", "${inventory.summariesCount}")
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        DataStatBadge("GROUPS", "${inventory.groupsCount}")
                        DataStatBadge("STT CONFIGS", "${inventory.sttConfigsCount}")
                        DataStatBadge("LLM CONFIGS", "${inventory.llmConfigsCount}")
                    }
                } else {
                    CircularProgressIndicator(color = AccentPrimary, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }
        }

        // Include API Keys Switch
        Card(
            shape = RoundedCornerShape(10.dp),
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
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Exports saved Groq, Gemini, and OpenAI keys so you can switch phones without re-entering credentials.",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
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
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onExportToFile,
                enabled = !isExporting,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
            ) {
                if (isExporting) {
                    CircularProgressIndicator(color = OnyxBlack, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Download, contentDescription = null, tint = OnyxBlack, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Export File", color = OnyxBlack, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            OutlinedButton(
                onClick = onShareDirectly,
                enabled = !isExporting,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, SteelBorder),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Share / Send", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        // Info Note
        Text(
            text = "Tip: Save your backup JSON in Google Drive, local storage, or send it to your new device via messaging/email.",
            color = TextMuted,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 14.sp
        )
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
                shape = RoundedCornerShape(10.dp),
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
                            .size(44.dp)
                            .background(DarkSlate, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = null, tint = AccentPrimary, modifier = Modifier.size(22.dp))
                    }
                    Text(
                        text = "SELECT BACKUP FILE TO RESTORE",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Choose a previously exported sovereign_backup_*.json file from your device or cloud storage.",
                        color = TextMuted,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = onSelectFile,
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Text("Select Backup JSON", color = OnyxBlack, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    if (parseError != null) {
                        Text(
                            text = parseError,
                            color = AmberWarning,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        } else {
            // Backup File Verified Card
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                border = BorderStroke(1.dp, EmeraldSuccess),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("VALID FORMAT", color = EmeraldSuccess, fontSize = 9.sp, fontWeight = FontWeight.Bold)
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

                    Spacer(modifier = Modifier.height(2.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        DataStatBadge("MEETINGS", "${parsedPayload.meetings.size}")
                        DataStatBadge("CHUNKS", "${parsedPayload.chunks.size}")
                        DataStatBadge("SUMMARIES", "${parsedPayload.summaries.size}")
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        DataStatBadge("GROUPS", "${parsedPayload.groups.size}")
                        DataStatBadge("STT CONFIGS", "${parsedPayload.settings?.sttConfigs?.size ?: 0}")
                        DataStatBadge("LLM CONFIGS", "${parsedPayload.settings?.llmConfigs?.size ?: 0}")
                    }
                }
            }

            // Restore Options
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                border = BorderStroke(1.dp, SteelBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "RESTORE STRATEGY",
                        color = AccentPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    HorizontalDivider(color = SteelBorder.copy(alpha = 0.4f))

                    // Wipe / Replace Option
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
                            Text("Recommended when moving to a fresh new device", color = TextMuted, fontSize = 10.sp)
                        }
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
                            Text("Applies configured STT/LLM endpoints and keys from backup", color = TextMuted, fontSize = 10.sp)
                        }
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
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onSelectFile,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, SteelBorder),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                ) {
                    Text("Change File", color = TextSecondary, fontSize = 11.sp)
                }

                Button(
                    onClick = onApplyRestore,
                    enabled = !isRestoring,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                ) {
                    if (isRestoring) {
                        CircularProgressIndicator(color = OnyxBlack, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Check, contentDescription = null, tint = OnyxBlack, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Apply Restore", color = OnyxBlack, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun DataStatBadge(label: String, count: String) {
    Column(
        modifier = Modifier
            .background(DarkSlate, RoundedCornerShape(4.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(text = label, color = TextMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        Text(text = count, color = TextPrimary, fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BackupTabButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                if (isSelected) AccentPrimary.copy(alpha = 0.18f) else CardBackground,
                shape = RoundedCornerShape(6.dp)
            )
            .border(
                1.dp,
                if (isSelected) AccentPrimary else Color.Transparent,
                shape = RoundedCornerShape(6.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) AccentPrimary else TextMuted,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Column {
                Text(
                    text = title,
                    color = if (isSelected) AccentPrimary else TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = subtitle,
                    color = if (isSelected) AccentPrimary else TextMuted,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
