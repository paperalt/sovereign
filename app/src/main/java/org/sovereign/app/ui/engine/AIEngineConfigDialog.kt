package org.sovereign.app.ui.engine

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.sovereign.app.auth.TokenStorage
import org.sovereign.app.data.MeetingRepository
import org.sovereign.app.network.*
import java.util.UUID

@Composable
fun AIEngineConfigDialog(
    tokenStorage: TokenStorage,
    meetingRepository: MeetingRepository,
    onDismiss: () -> Unit,
    onSaved: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Load available STT and LLM configs
    var sttList by remember { mutableStateOf(EndpointConfigStore.loadSTTConfigs(tokenStorage)) }
    var llmList by remember { mutableStateOf(EndpointConfigStore.loadLLMConfigs(tokenStorage)) }

    // Active STT & LLM selected
    val activeSTTId = tokenStorage.getActiveSTTConfigId().ifBlank { sttList.firstOrNull()?.id ?: "" }
    val activeLLMId = tokenStorage.getActiveLLMConfigId().ifBlank { llmList.firstOrNull()?.id ?: "" }

    var selectedSTT by remember { mutableStateOf(sttList.firstOrNull { it.id == activeSTTId } ?: sttList.first()) }
    var selectedLLM by remember { mutableStateOf(llmList.firstOrNull { it.id == activeLLMId } ?: llmList.first()) }

    // Presets list
    var savedPresets by remember { mutableStateOf(EnginePresetStore.loadAll(tokenStorage)) }

    // Modals
    var showSTTManager by remember { mutableStateOf(false) }
    var showLLMManager by remember { mutableStateOf(false) }
    var showSavePresetDialog by remember { mutableStateOf(false) }
    var isTestingPipeline by remember { mutableStateOf(false) }
    var testResultText by remember { mutableStateOf<String?>(null) }

    val pipelineReady = selectedSTT.endpoint.isNotBlank() && selectedSTT.model.isNotBlank() &&
            selectedLLM.endpoint.isNotBlank() && selectedLLM.model.isNotBlank()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = EngineColors.DarkSlate),
            border = BorderStroke(1.dp, EngineColors.SteelBorder),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(EngineColors.DarkSlate)
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "AI ENGINE COMBINE & PRESETS",
                            color = EngineColors.TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "Combine Voice STT and LLM Reasoning into a unified pipeline",
                            color = EngineColors.TextMuted,
                            fontSize = 11.sp
                        )
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = EngineColors.TextSecondary, modifier = Modifier.size(18.dp))
                    }
                }

                HorizontalDivider(color = EngineColors.SteelBorder.copy(alpha = 0.6f))

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. Live Combine Pipeline Flow Card
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = EngineColors.CardBackground),
                        border = BorderStroke(1.dp, if (pipelineReady) EngineColors.AccentPrimary else EngineColors.SteelBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "ACTIVE PIPELINE",
                                    color = EngineColors.TextMuted,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (pipelineReady) EngineColors.EmeraldSuccess.copy(alpha = 0.15f)
                                            else EngineColors.AmberWarning.copy(alpha = 0.15f),
                                            RoundedCornerShape(4.dp)
                                        )
                                        .border(
                                            1.dp,
                                            if (pipelineReady) EngineColors.EmeraldSuccess else EngineColors.AmberWarning,
                                            RoundedCornerShape(4.dp)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (pipelineReady) "CONFIGURED" else "INCOMPLETE",
                                        color = if (pipelineReady) EngineColors.EmeraldSuccess else EngineColors.AmberWarning,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // STT Node
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(EngineColors.DarkSlate, RoundedCornerShape(8.dp))
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Mic, contentDescription = null, tint = EngineColors.AccentPrimary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("VOICE (STT): ${selectedSTT.name}", color = EngineColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text("Model: ${selectedSTT.model} • ${hostOf(selectedSTT.endpoint)}", color = EngineColors.TextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                }
                            }

                            // Connector
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = EngineColors.AccentPrimary, modifier = Modifier.size(14.dp))
                            }

                            // LLM Node
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(EngineColors.DarkSlate, RoundedCornerShape(8.dp))
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Psychology, contentDescription = null, tint = EngineColors.IndigoAccent, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("REASONING (LLM): ${selectedLLM.name}", color = EngineColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text("Model: ${selectedLLM.model} • ${hostOf(selectedLLM.endpoint)}", color = EngineColors.TextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                }
                            }

                            // Diagnostic Ping
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        scope.launch {
                                            isTestingPipeline = true
                                            testResultText = null
                                            val client = DirectAIClient()
                                            val sttPing = async { client.pingEndpoint(selectedSTT.endpoint, selectedSTT.apiKey) }
                                            val llmPing = async { client.pingEndpoint(selectedLLM.endpoint, selectedLLM.apiKey.ifBlank { selectedSTT.apiKey }) }
                                            val sRes = sttPing.await()
                                            val lRes = llmPing.await()
                                            isTestingPipeline = false
                                            val sStr = if (sRes.isSuccess) "STT: ${sRes.getOrNull()}ms (OK)" else "STT: Failed"
                                            val lStr = if (lRes.isSuccess) "LLM: ${lRes.getOrNull()}ms (OK)" else "LLM: Failed"
                                            testResultText = "$sStr  |  $lStr"
                                        }
                                    },
                                    enabled = !isTestingPipeline && pipelineReady,
                                    shape = RoundedCornerShape(6.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = EngineColors.AccentPrimary.copy(alpha = 0.15f),
                                        contentColor = EngineColors.AccentPrimary
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(34.dp)
                                ) {
                                    if (isTestingPipeline) {
                                        CircularProgressIndicator(color = EngineColors.AccentPrimary, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Test Pipeline Latency", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                OutlinedButton(
                                    onClick = { showSavePresetDialog = true },
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, EngineColors.SteelBorder),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, tint = EngineColors.TextPrimary, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Save Preset", color = EngineColors.TextPrimary, fontSize = 11.sp)
                                }
                            }

                            if (testResultText != null) {
                                Text(
                                    text = testResultText ?: "",
                                    color = if (testResultText!!.contains("Failed")) EngineColors.AmberWarning else EngineColors.EmeraldSuccess,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // 2. Select Voice (STT) Section
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "SELECT VOICE (STT) ENDPOINT",
                                color = EngineColors.TextMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Row(
                                modifier = Modifier
                                    .clickable { showSTTManager = true }
                                    .padding(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = null, tint = EngineColors.AccentPrimary, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Manage STT", color = EngineColors.AccentPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // STT Chips Selector
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            sttList.forEach { item ->
                                val isSelected = item.id == selectedSTT.id
                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (isSelected) EngineColors.AccentPrimary.copy(alpha = 0.18f) else EngineColors.CardBackground,
                                            RoundedCornerShape(6.dp)
                                        )
                                        .border(
                                            1.dp,
                                            if (isSelected) EngineColors.AccentPrimary else EngineColors.SteelBorder,
                                            RoundedCornerShape(6.dp)
                                        )
                                        .clickable { selectedSTT = item }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Column {
                                        Text(
                                            text = item.name,
                                            color = if (isSelected) EngineColors.AccentPrimary else EngineColors.TextPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        )
                                        Text(
                                            text = item.model,
                                            color = EngineColors.TextMuted,
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 3. Select Reasoning (LLM) Section
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "SELECT REASONING (LLM) ENDPOINT",
                                color = EngineColors.TextMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Row(
                                modifier = Modifier
                                    .clickable { showLLMManager = true }
                                    .padding(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = null, tint = EngineColors.IndigoAccent, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Manage LLM", color = EngineColors.IndigoAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // LLM Chips Selector
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            llmList.forEach { item ->
                                val isSelected = item.id == selectedLLM.id
                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (isSelected) EngineColors.IndigoAccent.copy(alpha = 0.18f) else EngineColors.CardBackground,
                                            RoundedCornerShape(6.dp)
                                        )
                                        .border(
                                            1.dp,
                                            if (isSelected) EngineColors.IndigoAccent else EngineColors.SteelBorder,
                                            RoundedCornerShape(6.dp)
                                        )
                                        .clickable { selectedLLM = item }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Column {
                                        Text(
                                            text = item.name,
                                            color = if (isSelected) EngineColors.IndigoAccent else EngineColors.TextPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        )
                                        Text(
                                            text = item.model,
                                            color = EngineColors.TextMuted,
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 4. Saved Combinations Presets
                    if (savedPresets.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "SAVED COMBINATION PRESETS (${savedPresets.size})",
                                color = EngineColors.TextMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )

                            savedPresets.forEach { preset ->
                                Card(
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(containerColor = EngineColors.CardBackground),
                                    border = BorderStroke(1.dp, EngineColors.SteelBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(preset.name, color = EngineColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            Text("${preset.sttModel} + ${preset.llmModel}", color = EngineColors.TextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Button(
                                                onClick = {
                                                    // Find matching STT or create
                                                    val matchedSTT = sttList.firstOrNull { it.endpoint == preset.sttEndpoint } ?: selectedSTT
                                                    val matchedLLM = llmList.firstOrNull { it.endpoint == preset.llmEndpoint } ?: selectedLLM
                                                    selectedSTT = matchedSTT
                                                    selectedLLM = matchedLLM
                                                    Toast.makeText(context, "Loaded preset: ${preset.name}", Toast.LENGTH_SHORT).show()
                                                },
                                                shape = RoundedCornerShape(4.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = EngineColors.AccentPrimary),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                                modifier = Modifier.height(34.dp)
                                            ) {
                                                Text("Apply", color = EngineColors.OnyxBlack, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                            IconButton(
                                                onClick = {
                                                    val updated = savedPresets.filterNot { it.id == preset.id }
                                                    savedPresets = updated
                                                    EnginePresetStore.saveAll(tokenStorage, updated)
                                                },
                                                modifier = Modifier.size(34.dp)
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = EngineColors.CrimsonAlert, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = EngineColors.SteelBorder.copy(alpha = 0.6f))

                // Footer Actions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, EngineColors.SteelBorder)
                    ) {
                        Text("Cancel", color = EngineColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = {
                            // Apply both STT and LLM configurations
                            EndpointConfigStore.applySTT(tokenStorage, selectedSTT)
                            EndpointConfigStore.applyLLM(tokenStorage, selectedLLM)

                            val combinedName = "${selectedSTT.name} + ${selectedLLM.name}"
                            tokenStorage.setAIProvider(combinedName)

                            Toast.makeText(context, "AI Pipeline Activated", Toast.LENGTH_SHORT).show()
                            onSaved(combinedName)
                            onDismiss()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EngineColors.AccentPrimary),
                        enabled = pipelineReady
                    ) {
                        Text("Apply & Activate", color = EngineColors.OnyxBlack, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Modal STT Manager
    if (showSTTManager) {
        STTEndpointsDialog(
            tokenStorage = tokenStorage,
            onDismiss = {
                sttList = EndpointConfigStore.loadSTTConfigs(tokenStorage)
                val curActive = tokenStorage.getActiveSTTConfigId()
                selectedSTT = sttList.firstOrNull { it.id == curActive } ?: selectedSTT
                showSTTManager = false
            },
            onActiveChanged = { newActive ->
                selectedSTT = newActive
                sttList = EndpointConfigStore.loadSTTConfigs(tokenStorage)
            }
        )
    }

    // Modal LLM Manager
    if (showLLMManager) {
        LLMEndpointsDialog(
            tokenStorage = tokenStorage,
            onDismiss = {
                llmList = EndpointConfigStore.loadLLMConfigs(tokenStorage)
                val curActive = tokenStorage.getActiveLLMConfigId()
                selectedLLM = llmList.firstOrNull { it.id == curActive } ?: selectedLLM
                showLLMManager = false
            },
            onActiveChanged = { newActive ->
                selectedLLM = newActive
                llmList = EndpointConfigStore.loadLLMConfigs(tokenStorage)
            }
        )
    }

    // Modal Save Combination Preset
    if (showSavePresetDialog) {
        SavePresetDialog(
            defaultName = "${selectedSTT.name} + ${selectedLLM.name}",
            onDismiss = { showSavePresetDialog = false },
            onSave = { name ->
                showSavePresetDialog = false
                val newPreset = EnginePreset(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    sttPresetId = selectedSTT.providerId,
                    sttEndpoint = selectedSTT.endpoint,
                    sttModel = selectedSTT.model,
                    sttKey = selectedSTT.apiKey,
                    llmPresetId = selectedLLM.providerId,
                    llmEndpoint = selectedLLM.endpoint,
                    llmModel = selectedLLM.model,
                    llmKey = selectedLLM.apiKey,
                    adaptiveStreaming = selectedSTT.adaptiveStreaming
                )
                val updated = savedPresets + newPreset
                savedPresets = updated
                EnginePresetStore.saveAll(tokenStorage, updated)
                Toast.makeText(context, "Preset \"$name\" saved", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
private fun SavePresetDialog(
    defaultName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var name by remember { mutableStateOf(defaultName) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = EngineColors.DarkSlate),
            border = BorderStroke(1.dp, EngineColors.SteelBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Save Preset Combination", color = EngineColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("Name this pair of Voice and Reasoning endpoints:", color = EngineColors.TextMuted, fontSize = 11.sp)

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = EngineColors.TextPrimary,
                        unfocusedTextColor = EngineColors.TextPrimary,
                        focusedBorderColor = EngineColors.AccentPrimary,
                        unfocusedBorderColor = EngineColors.SteelBorder,
                        focusedContainerColor = EngineColors.OnyxBlack,
                        unfocusedContainerColor = EngineColors.OnyxBlack,
                        cursorColor = EngineColors.AccentPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, EngineColors.SteelBorder)
                    ) {
                        Text("Cancel", color = EngineColors.TextSecondary, fontSize = 12.sp)
                    }
                    Button(
                        onClick = { onSave(name.trim().ifBlank { defaultName }) },
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EngineColors.AccentPrimary)
                    ) {
                        Text("Save", color = EngineColors.OnyxBlack, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
