package org.sovereign.app.ui.engine

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.sovereign.app.network.DirectAIClient
import org.sovereign.app.network.EnginePreset
import org.sovereign.app.network.ProviderPreset
import java.util.UUID

@Composable
fun CombinedEngineCard(
    context: Context,
    sttPresetId: String,
    sttEndpoint: String,
    sttModel: String,
    sttKey: String,
    llmPresetId: String,
    llmEndpoint: String,
    llmModel: String,
    llmKey: String,
    adaptiveStreaming: Boolean,
    presets: List<ProviderPreset>,
    savedProfiles: List<EnginePreset>,
    activeProfileId: String,
    onApplyProfile: (EnginePreset) -> Unit,
    onSaveProfile: (EnginePreset) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onNavigateToTab: (Int) -> Unit
) {
    val scope = rememberCoroutineScope()
    var isTestingPipeline by remember { mutableStateOf(false) }
    var testResultText by remember { mutableStateOf<String?>(null) }
    var showSaveProfileDialog by remember { mutableStateOf(false) }
    var profileToDelete by remember { mutableStateOf<EnginePreset?>(null) }

    val sttPreset = presets.firstOrNull { it.id == sttPresetId }
    val llmPreset = presets.firstOrNull { it.id == llmPresetId }

    val sttName = sttPreset?.name ?: sttPresetId.uppercase()
    val llmName = llmPreset?.name ?: llmPresetId.uppercase()

    val sttReady = sttEndpoint.isNotBlank() && sttModel.isNotBlank() && (sttKey.isNotBlank() || isLocalEndpoint(sttEndpoint))
    val effectiveLlmKey = llmKey.ifBlank { sttKey }
    val llmReady = llmEndpoint.isNotBlank() && llmModel.isNotBlank() && (effectiveLlmKey.isNotBlank() || isLocalEndpoint(llmEndpoint))
    val pipelineReady = sttReady && llmReady

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Hero Combined Pipeline Card
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = EngineColors.CardBackground),
            border = BorderStroke(1.dp, if (pipelineReady) EngineColors.AccentPrimary else EngineColors.SteelBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "COMBINED AI ENGINE PIPELINE",
                            color = EngineColors.TextMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "$sttName + $llmName",
                            color = EngineColors.TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

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
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = if (pipelineReady) "READY" else "NEEDS SETUP",
                            color = if (pipelineReady) EngineColors.EmeraldSuccess else EngineColors.AmberWarning,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                HorizontalDivider(color = EngineColors.SteelBorder.copy(alpha = 0.5f))

                // Pipeline Flow Diagram
                // Stage 1: Voice (STT) Node
                PipelineNodeItem(
                    icon = Icons.Default.Mic,
                    stage = "STAGE 1 • VOICE (STT)",
                    provider = sttName,
                    model = sttModel.ifBlank { "No model selected" },
                    host = hostOf(sttEndpoint).ifBlank { "No endpoint set" },
                    isReady = sttReady,
                    onConfigureClick = { onNavigateToTab(1) }
                )

                // Connector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .background(EngineColors.DarkSlate, RoundedCornerShape(12.dp))
                            .border(1.dp, EngineColors.SteelBorder, RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ArrowDownward,
                                contentDescription = null,
                                tint = EngineColors.AccentPrimary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (adaptiveStreaming) "VAD Audio Frames" else "Raw Audio",
                                color = EngineColors.AccentPrimary,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Stage 2: Reasoning (LLM) Node
                PipelineNodeItem(
                    icon = Icons.Default.Psychology,
                    stage = "STAGE 2 • REASONING (LLM)",
                    provider = llmName,
                    model = llmModel.ifBlank { "No model selected" },
                    host = hostOf(llmEndpoint).ifBlank { "No endpoint set" },
                    isReady = llmReady,
                    onConfigureClick = { onNavigateToTab(2) }
                )

                HorizontalDivider(color = EngineColors.SteelBorder.copy(alpha = 0.5f))

                // Diagnostic Test & Save actions
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

                                val sttPingDeferred = async { client.pingEndpoint(sttEndpoint, sttKey) }
                                val llmPingDeferred = async { client.pingEndpoint(llmEndpoint, effectiveLlmKey) }

                                val sttRes = sttPingDeferred.await()
                                val llmRes = llmPingDeferred.await()
                                isTestingPipeline = false

                                val sttPart = if (sttRes.isSuccess) "STT: ${sttRes.getOrNull()}ms (OK)" else "STT: Failed"
                                val llmPart = if (llmRes.isSuccess) "LLM: ${llmRes.getOrNull()}ms (OK)" else "LLM: Failed"
                                testResultText = "$sttPart  |  $llmPart"
                            }
                        },
                        enabled = !isTestingPipeline && sttEndpoint.isNotBlank() && llmEndpoint.isNotBlank(),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EngineColors.AccentPrimary.copy(alpha = 0.15f),
                            contentColor = EngineColors.AccentPrimary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                    ) {
                        if (isTestingPipeline) {
                            CircularProgressIndicator(
                                color = EngineColors.AccentPrimary,
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Test Pipeline", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    OutlinedButton(
                        onClick = { showSaveProfileDialog = true },
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, EngineColors.SteelBorder),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = EngineColors.TextPrimary, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save Profile", color = EngineColors.TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                // Test result banner
                if (testResultText != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(EngineColors.DarkSlate, RoundedCornerShape(6.dp))
                            .border(1.dp, EngineColors.SteelBorder, RoundedCornerShape(6.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
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
        }

        // 2. Curated Architecture Templates (1-Tap quick pairing)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "CURATED ARCHITECTURES (1-TAP PAIRING)",
                color = EngineColors.TextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ArchitectureChip(
                    name = "Groq Turbo",
                    sub = "Whisper + Llama 3.3",
                    badge = "FASTEST",
                    onClick = {
                        val groq = presets.firstOrNull { it.id == "groq" }
                        if (groq != null) {
                            onApplyProfile(
                                EnginePreset(
                                    name = "Groq Turbo",
                                    sttPresetId = "groq",
                                    sttEndpoint = groq.stt.endpoint,
                                    sttModel = groq.stt.defaultModel,
                                    sttKey = sttKey,
                                    llmPresetId = "groq",
                                    llmEndpoint = groq.llm.endpoint,
                                    llmModel = groq.llm.defaultModel,
                                    llmKey = llmKey.ifBlank { sttKey },
                                    adaptiveStreaming = true
                                )
                            )
                            Toast.makeText(context, "Applied Groq Turbo pair", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                ArchitectureChip(
                    name = "DeepSeek Analyst",
                    sub = "Groq STT + DeepSeek Chat",
                    badge = "REASONING",
                    onClick = {
                        val ds = presets.firstOrNull { it.id == "deepseek" }
                        if (ds != null) {
                            onApplyProfile(
                                EnginePreset(
                                    name = "DeepSeek Analyst",
                                    sttPresetId = "deepseek",
                                    sttEndpoint = ds.stt.endpoint,
                                    sttModel = ds.stt.defaultModel,
                                    sttKey = sttKey,
                                    llmPresetId = "deepseek",
                                    llmEndpoint = ds.llm.endpoint,
                                    llmModel = ds.llm.defaultModel,
                                    llmKey = llmKey,
                                    adaptiveStreaming = true
                                )
                            )
                            Toast.makeText(context, "Applied DeepSeek Analyst pair", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                ArchitectureChip(
                    name = "Google Gemini 2.0",
                    sub = "Flash Multimodal (1M ctx)",
                    badge = "MULTIMODAL",
                    onClick = {
                        val gem = presets.firstOrNull { it.id == "gemini" }
                        if (gem != null) {
                            onApplyProfile(
                                EnginePreset(
                                    name = "Google Gemini 2.0",
                                    sttPresetId = "gemini",
                                    sttEndpoint = gem.stt.endpoint,
                                    sttModel = gem.stt.defaultModel,
                                    sttKey = sttKey,
                                    llmPresetId = "gemini",
                                    llmEndpoint = gem.llm.endpoint,
                                    llmModel = gem.llm.defaultModel,
                                    llmKey = llmKey.ifBlank { sttKey },
                                    adaptiveStreaming = true
                                )
                            )
                            Toast.makeText(context, "Applied Google Gemini pair", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                ArchitectureChip(
                    name = "OpenAI Official",
                    sub = "Whisper-1 + GPT-4o-Mini",
                    badge = "PRECISION",
                    onClick = {
                        val oai = presets.firstOrNull { it.id == "openai" }
                        if (oai != null) {
                            onApplyProfile(
                                EnginePreset(
                                    name = "OpenAI Official",
                                    sttPresetId = "openai",
                                    sttEndpoint = oai.stt.endpoint,
                                    sttModel = oai.stt.defaultModel,
                                    sttKey = sttKey,
                                    llmPresetId = "openai",
                                    llmEndpoint = oai.llm.endpoint,
                                    llmModel = oai.llm.defaultModel,
                                    llmKey = llmKey.ifBlank { sttKey },
                                    adaptiveStreaming = true
                                )
                            )
                            Toast.makeText(context, "Applied OpenAI pair", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                ArchitectureChip(
                    name = "Ollama Localhost",
                    sub = "100% On-Premise",
                    badge = "OFFLINE",
                    onClick = {
                        val ollama = presets.firstOrNull { it.id == "ollama" }
                        if (ollama != null) {
                            onApplyProfile(
                                EnginePreset(
                                    name = "Ollama Localhost",
                                    sttPresetId = "ollama",
                                    sttEndpoint = ollama.stt.endpoint,
                                    sttModel = ollama.stt.defaultModel,
                                    sttKey = "",
                                    llmPresetId = "ollama",
                                    llmEndpoint = ollama.llm.endpoint,
                                    llmModel = ollama.llm.defaultModel,
                                    llmKey = "",
                                    adaptiveStreaming = false
                                )
                            )
                            Toast.makeText(context, "Applied Ollama local pair", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }

        // 3. Saved Custom Engine Profiles List
        if (savedProfiles.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "SAVED ENGINE PROFILES (${savedProfiles.size})",
                    color = EngineColors.TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                savedProfiles.forEach { profile ->
                    val isActive = profile.id == activeProfileId
                    SavedProfileRow(
                        profile = profile,
                        presets = presets,
                        isActive = isActive,
                        onActivate = {
                            onApplyProfile(profile)
                            Toast.makeText(context, "Activated profile \"${profile.name}\"", Toast.LENGTH_SHORT).show()
                        },
                        onDelete = { profileToDelete = profile }
                    )
                }
            }
        }
    }

    // Save Profile Modal Dialog
    if (showSaveProfileDialog) {
        SaveProfileDialog(
            defaultName = "$sttName + $llmName",
            onDismiss = { showSaveProfileDialog = false },
            onSave = { name ->
                showSaveProfileDialog = false
                val newProfile = EnginePreset(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    sttPresetId = sttPresetId,
                    sttEndpoint = sttEndpoint,
                    sttModel = sttModel,
                    sttKey = sttKey,
                    llmPresetId = llmPresetId,
                    llmEndpoint = llmEndpoint,
                    llmModel = llmModel,
                    llmKey = llmKey,
                    adaptiveStreaming = adaptiveStreaming
                )
                onSaveProfile(newProfile)
                Toast.makeText(context, "Profile \"$name\" saved", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (profileToDelete != null) {
        val target = profileToDelete!!
        AlertDialog(
            onDismissRequest = { profileToDelete = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            containerColor = EngineColors.DarkSlate,
            shape = RoundedCornerShape(8.dp),
            title = {
                Text(
                    text = "DELETE SAVED PROFILE?",
                    color = EngineColors.CrimsonAlert,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete profile \"${target.name}\"? This pipeline profile will be removed.",
                    color = EngineColors.TextPrimary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteProfile(target.id)
                        profileToDelete = null
                        Toast.makeText(context, "Profile deleted", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EngineColors.CrimsonAlert),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("DELETE", color = androidx.compose.ui.graphics.Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { profileToDelete = null },
                    border = BorderStroke(1.dp, EngineColors.SteelBorder),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("CANCEL", color = EngineColors.TextSecondary, fontSize = 12.sp)
                }
            }
        )
    }
}

@Composable
private fun PipelineNodeItem(
    icon: ImageVector,
    stage: String,
    provider: String,
    model: String,
    host: String,
    isReady: Boolean,
    onConfigureClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(EngineColors.DarkSlate, RoundedCornerShape(8.dp))
            .border(1.dp, if (isReady) EngineColors.SteelBorder else EngineColors.AmberWarning.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .clickable { onConfigureClick() }
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(EngineColors.CardBackground, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isReady) EngineColors.AccentPrimary else EngineColors.AmberWarning,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = stage,
                    color = EngineColors.TextMuted,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$provider • $model",
                    color = EngineColors.TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = host,
                    color = EngineColors.TextSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        OutlinedButton(
            onClick = onConfigureClick,
            shape = RoundedCornerShape(4.dp),
            border = BorderStroke(1.dp, EngineColors.SteelBorder),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
            modifier = Modifier.height(34.dp)
        ) {
            Icon(Icons.Default.Edit, contentDescription = null, tint = EngineColors.TextSecondary, modifier = Modifier.size(12.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Edit", color = EngineColors.TextSecondary, fontSize = 10.sp)
        }
    }
}

@Composable
private fun ArchitectureChip(
    name: String,
    sub: String,
    badge: String,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = EngineColors.DarkSlate),
        border = BorderStroke(1.dp, EngineColors.SteelBorder),
        modifier = Modifier
            .widthIn(min = 160.dp, max = 200.dp)
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = name,
                    color = EngineColors.TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    modifier = Modifier
                        .background(EngineColors.CardBackground, RoundedCornerShape(3.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = badge,
                        color = EngineColors.AccentPrimary,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Text(
                text = sub,
                color = EngineColors.TextMuted,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SavedProfileRow(
    profile: EnginePreset,
    presets: List<ProviderPreset>,
    isActive: Boolean,
    onActivate: () -> Unit,
    onDelete: () -> Unit
) {
    val sttName = presets.firstOrNull { it.id == profile.sttPresetId }?.name ?: profile.sttPresetId.uppercase()
    val llmName = presets.firstOrNull { it.id == profile.llmPresetId }?.name ?: profile.llmPresetId.uppercase()

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) EngineColors.AccentPrimary.copy(alpha = 0.10f) else EngineColors.CardBackground
        ),
        border = BorderStroke(1.dp, if (isActive) EngineColors.AccentPrimary else EngineColors.SteelBorder),
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = profile.name,
                        color = if (isActive) EngineColors.AccentPrimary else EngineColors.TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isActive) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .background(EngineColors.EmeraldSuccess.copy(alpha = 0.2f), RoundedCornerShape(3.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text("ACTIVE", color = EngineColors.EmeraldSuccess, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "STT: $sttName (${profile.sttModel}) • LLM: $llmName (${profile.llmModel})",
                    color = EngineColors.TextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!isActive) {
                    Button(
                        onClick = onActivate,
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EngineColors.AccentPrimary),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("Use", color = EngineColors.OnyxBlack, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete profile",
                        tint = EngineColors.CrimsonAlert,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SaveProfileDialog(
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
                Text(
                    text = "Save Profile Name",
                    color = EngineColors.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Name this custom combination of Voice and Reasoning settings:",
                    color = EngineColors.TextMuted,
                    fontSize = 11.sp
                )

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
