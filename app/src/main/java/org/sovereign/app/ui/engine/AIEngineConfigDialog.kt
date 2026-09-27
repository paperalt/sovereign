package org.sovereign.app.ui.engine

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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tune
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
import kotlinx.coroutines.launch
import org.sovereign.app.auth.TokenStorage
import org.sovereign.app.data.MeetingRepository
import org.sovereign.app.network.EnginePreset
import org.sovereign.app.network.EnginePresetStore
import org.sovereign.app.network.ProviderPreset
import org.sovereign.app.network.ProviderPresetManager

@Composable
fun AIEngineConfigDialog(
    tokenStorage: TokenStorage,
    meetingRepository: MeetingRepository,
    onDismiss: () -> Unit,
    onSaved: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var presets by remember { mutableStateOf<List<ProviderPreset>>(emptyList()) }
    var isLoadingPresets by remember { mutableStateOf(true) }

    // Segmented tab index: 0 = COMBINE, 1 = VOICE (STT), 2 = REASONING (LLM)
    var selectedTab by remember { mutableIntStateOf(0) }

    // STT State
    var sttPresetId by remember { mutableStateOf(tokenStorage.getSTTPresetId()) }
    var sttEndpoint by remember { mutableStateOf(tokenStorage.getSTTEndpoint()) }
    var sttModel by remember { mutableStateOf(tokenStorage.getSTTModel()) }
    var sttKey by remember { mutableStateOf(tokenStorage.getSTTKey()) }
    var adaptiveStreaming by remember { mutableStateOf(tokenStorage.isAdaptiveStreamingBetaEnabled()) }

    // LLM State
    var llmPresetId by remember { mutableStateOf(tokenStorage.getLLMPresetId()) }
    var llmEndpoint by remember { mutableStateOf(tokenStorage.getLLMEndpoint()) }
    var llmModel by remember { mutableStateOf(tokenStorage.getLLMModel()) }
    var llmKey by remember { mutableStateOf(tokenStorage.getLLMKey()) }

    // Saved engine profiles & active profile ID
    var savedProfiles by remember { mutableStateOf<List<EnginePreset>>(emptyList()) }
    var activeProfileId by remember { mutableStateOf(tokenStorage.getActiveEnginePresetId()) }

    // Modals for Preset Sources
    var showSourceSheet by remember { mutableStateOf(false) }
    var showPasteJsonDialog by remember { mutableStateOf(false) }

    // JSON file picker launcher via SAF
    val jsonFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val jsonText = inputStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                val imported = ProviderPresetManager.parsePresetsJson(jsonText)
                if (imported.isNotEmpty()) {
                    presets = imported
                    Toast.makeText(context, "Loaded ${imported.size} presets from file", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Invalid preset file", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Initial loading of presets and engine profiles
    LaunchedEffect(Unit) {
        val list = ProviderPresetManager.loadPresets(context)
        presets = list
        isLoadingPresets = false

        // Seed default profiles if not present
        savedProfiles = EnginePresetStore.ensureSeeded(tokenStorage, list)
        activeProfileId = tokenStorage.getActiveEnginePresetId()

        // Populate initial fields if empty
        if (sttEndpoint.isBlank()) {
            val groq = list.firstOrNull { it.id == "groq" }
            if (groq != null) {
                sttEndpoint = groq.stt.endpoint
                sttModel = groq.stt.defaultModel
                sttPresetId = "groq"
            }
        }
        if (llmEndpoint.isBlank()) {
            val groq = list.firstOrNull { it.id == "groq" }
            if (groq != null) {
                llmEndpoint = groq.llm.endpoint
                llmModel = groq.llm.defaultModel
                llmPresetId = "groq"
            }
        }
    }

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
                // 1. Dialog Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "AI ENGINE CONFIGURATION",
                            color = EngineColors.TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "Independent STT & LLM Pipeline Architecture",
                            color = EngineColors.TextMuted,
                            fontSize = 11.sp
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Preset Sources button
                        IconButton(
                            onClick = { showSourceSheet = true },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Preset Sources",
                                tint = EngineColors.AccentPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = EngineColors.TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(color = EngineColors.SteelBorder.copy(alpha = 0.6f))

                // 2. Segmented Tab Selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .background(EngineColors.CardBackground, RoundedCornerShape(8.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TabSegmentButton(
                        title = "COMBINE",
                        subtitle = "Pipeline",
                        isSelected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        modifier = Modifier.weight(1f)
                    )
                    TabSegmentButton(
                        title = "VOICE",
                        subtitle = "STT",
                        isSelected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        modifier = Modifier.weight(1f)
                    )
                    TabSegmentButton(
                        title = "REASONING",
                        subtitle = "LLM",
                        isSelected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        modifier = Modifier.weight(1f)
                    )
                }

                // 3. Tab Content Area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    if (isLoadingPresets) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = EngineColors.AccentPrimary)
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            when (selectedTab) {
                                0 -> {
                                    // Combine Card & Pipeline Manager
                                    CombinedEngineCard(
                                        context = context,
                                        sttPresetId = sttPresetId,
                                        sttEndpoint = sttEndpoint,
                                        sttModel = sttModel,
                                        sttKey = sttKey,
                                        llmPresetId = llmPresetId,
                                        llmEndpoint = llmEndpoint,
                                        llmModel = llmModel,
                                        llmKey = llmKey,
                                        adaptiveStreaming = adaptiveStreaming,
                                        presets = presets,
                                        savedProfiles = savedProfiles,
                                        activeProfileId = activeProfileId,
                                        onApplyProfile = { profile ->
                                            sttPresetId = profile.sttPresetId
                                            sttEndpoint = profile.sttEndpoint
                                            sttModel = profile.sttModel
                                            sttKey = profile.sttKey
                                            llmPresetId = profile.llmPresetId
                                            llmEndpoint = profile.llmEndpoint
                                            llmModel = profile.llmModel
                                            llmKey = profile.llmKey
                                            adaptiveStreaming = profile.adaptiveStreaming
                                            activeProfileId = profile.id
                                        },
                                        onSaveProfile = { newProfile ->
                                            val updated = savedProfiles + newProfile
                                            savedProfiles = updated
                                            EnginePresetStore.saveAll(tokenStorage, updated)
                                            activeProfileId = newProfile.id
                                        },
                                        onDeleteProfile = { profileId ->
                                            val updated = savedProfiles.filterNot { it.id == profileId }
                                            savedProfiles = updated
                                            EnginePresetStore.saveAll(tokenStorage, updated)
                                            if (activeProfileId == profileId) {
                                                activeProfileId = updated.firstOrNull()?.id ?: ""
                                            }
                                        },
                                        onNavigateToTab = { targetTab -> selectedTab = targetTab }
                                    )
                                }
                                1 -> {
                                    // Voice (STT) Endpoint Config
                                    EndpointConfigSection(
                                        context = context,
                                        sectionTitle = "VOICE TRANSCRIBER (STT)",
                                        sectionSubtitle = "Audio ingestion, frame chunking & transcription",
                                        isSTT = true,
                                        presets = presets,
                                        selectedProviderId = sttPresetId,
                                        onProviderSelected = { preset ->
                                            sttPresetId = preset.id
                                            sttEndpoint = preset.stt.endpoint
                                            sttModel = preset.stt.defaultModel
                                        },
                                        endpointUrl = sttEndpoint,
                                        onEndpointUrlChange = { sttEndpoint = it },
                                        modelName = sttModel,
                                        onModelNameChange = { sttModel = it },
                                        apiKey = sttKey,
                                        onApiKeyChange = { sttKey = it },
                                        adaptiveStreaming = adaptiveStreaming,
                                        onAdaptiveStreamingChange = { adaptiveStreaming = it }
                                    )
                                }
                                2 -> {
                                    // Reasoning (LLM) Endpoint Config
                                    EndpointConfigSection(
                                        context = context,
                                        sectionTitle = "REASONING ENGINE (LLM)",
                                        sectionSubtitle = "Executive summarization, action items & question formulation",
                                        isSTT = false,
                                        presets = presets,
                                        selectedProviderId = llmPresetId,
                                        onProviderSelected = { preset ->
                                            llmPresetId = preset.id
                                            llmEndpoint = preset.llm.endpoint
                                            llmModel = preset.llm.defaultModel
                                        },
                                        endpointUrl = llmEndpoint,
                                        onEndpointUrlChange = { llmEndpoint = it },
                                        modelName = llmModel,
                                        onModelNameChange = { llmModel = it },
                                        apiKey = llmKey,
                                        onApiKeyChange = { llmKey = it },
                                        counterpartApiKey = sttKey,
                                        onUseCounterpartKey = { llmKey = sttKey }
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = EngineColors.SteelBorder.copy(alpha = 0.6f))

                // 4. Sticky Bottom Action Bar
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

                    val canSave = sttEndpoint.isNotBlank() && llmEndpoint.isNotBlank()
                    Button(
                        onClick = {
                            val activeEngine = EnginePreset(
                                id = activeProfileId.ifBlank { java.util.UUID.randomUUID().toString() },
                                name = generateEngineName(sttPresetId, llmPresetId, presets),
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

                            // Save active engine to persistent token storage
                            EnginePresetStore.applyToStorage(tokenStorage, activeEngine)

                            // Update profiles list
                            val existingIndex = savedProfiles.indexOfFirst { it.id == activeEngine.id }
                            val updatedList = if (existingIndex >= 0) {
                                savedProfiles.toMutableList().apply { set(existingIndex, activeEngine) }
                            } else {
                                savedProfiles + activeEngine
                            }
                            savedProfiles = updatedList
                            EnginePresetStore.saveAll(tokenStorage, updatedList)

                            val displayLabel = activeEngine.name
                            Toast.makeText(context, "Engine configuration saved & activated", Toast.LENGTH_SHORT).show()
                            onSaved(displayLabel)
                            onDismiss()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EngineColors.AccentPrimary),
                        enabled = canSave
                    ) {
                        Text("Save & Activate", color = EngineColors.OnyxBlack, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Modal dialog for Preset Sources
    if (showSourceSheet) {
        PresetSourceSheet(
            context = context,
            tokenStorage = tokenStorage,
            presetCount = presets.size,
            onPresetsUpdated = { updated -> presets = updated },
            onLaunchFilePicker = { jsonFilePicker.launch("application/json") },
            onOpenPasteJson = { showPasteJsonDialog = true },
            onDismiss = { showSourceSheet = false }
        )
    }

    // Modal dialog for pasting raw JSON presets
    if (showPasteJsonDialog) {
        PastePresetsDialog(
            onDismiss = { showPasteJsonDialog = false },
            onImported = { imported ->
                presets = imported
                showPasteJsonDialog = false
                Toast.makeText(context, "Applied ${imported.size} presets from JSON", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
private fun TabSegmentButton(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                if (isSelected) EngineColors.AccentPrimary.copy(alpha = 0.18f) else EngineColors.CardBackground,
                shape = RoundedCornerShape(6.dp)
            )
            .border(
                1.dp,
                if (isSelected) EngineColors.AccentPrimary else EngineColors.BorderSubtle,
                shape = RoundedCornerShape(6.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                color = if (isSelected) EngineColors.AccentPrimary else EngineColors.TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Text(
                text = subtitle,
                color = if (isSelected) EngineColors.AccentPrimary else EngineColors.TextMuted,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

private fun generateEngineName(sttId: String, llmId: String, presets: List<ProviderPreset>): String {
    val sttName = presets.firstOrNull { it.id == sttId }?.name ?: sttId.uppercase()
    val llmName = presets.firstOrNull { it.id == llmId }?.name ?: llmId.uppercase()
    return if (sttId == llmId) sttName else "$sttName + $llmName"
}
