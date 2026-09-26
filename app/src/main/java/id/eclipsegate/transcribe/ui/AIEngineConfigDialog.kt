package id.eclipsegate.transcribe.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import id.eclipsegate.transcribe.auth.TokenStorage
import id.eclipsegate.transcribe.data.MeetingRepository
import id.eclipsegate.transcribe.network.DirectAIClient
import id.eclipsegate.transcribe.network.ProviderPreset
import id.eclipsegate.transcribe.network.ProviderPresetManager
import kotlinx.coroutines.launch

private val OnyxBlack = Color(0xFF0A0D12)
private val DarkSlate = Color(0xFF141A22)
private val CardBackground = Color(0xFF0F151E)
private val SteelBorder = Color(0xFF283342)
private val TextPrimary = Color(0xFFF0F4F8)
private val TextSecondary = Color(0xFF94A3B8)
private val TextMuted = Color(0xFF64748B)
private val AccentPrimary = Color(0xFF38BDF8)
private val EmeraldSuccess = Color(0xFF10B981)
private val CrimsonAlert = Color(0xFFE11D48)

@Composable
fun AIEngineConfigDialog(
    tokenStorage: TokenStorage,
    meetingRepository: MeetingRepository? = null,
    onDismiss: () -> Unit,
    onSaved: (provider: String) -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val directAIClient = remember { DirectAIClient() }

    // Active Navigation Tab: 0 = PRESETS, 1 = VOICE / STT, 2 = LLM / NALAR
    var activeTab by remember { mutableStateOf(0) }

    // Presets state
    var presets by remember { mutableStateOf(emptyList<ProviderPreset>()) }
    var selectedPresetId by remember { mutableStateOf(tokenStorage.getSelectedPreset()) }
    var customUrlInput by remember { mutableStateOf(tokenStorage.getCustomPresetsUrl()) }
    var isFetchingPresets by remember { mutableStateOf(false) }
    var showPasteJsonModal by remember { mutableStateOf(false) }

    // STT state
    var sttEndpoint by remember { mutableStateOf(tokenStorage.getSTTEndpoint()) }
    var sttModel by remember { mutableStateOf(tokenStorage.getSTTModel()) }
    var sttKey by remember { mutableStateOf(tokenStorage.getSTTKey()) }
    var isSttKeyVisible by remember { mutableStateOf(false) }

    // LLM state
    var llmEndpoint by remember { mutableStateOf(tokenStorage.getLLMEndpoint()) }
    var llmModel by remember { mutableStateOf(tokenStorage.getLLMModel()) }
    var llmKey by remember { mutableStateOf(tokenStorage.getLLMKey()) }
    var isLlmKeyVisible by remember { mutableStateOf(false) }

    // Adaptive streaming toggle
    var isAdaptiveBeta by remember { mutableStateOf(tokenStorage.isAdaptiveStreamingBetaEnabled()) }

    // Model Picker dialog state
    var isDetectingModels by remember { mutableStateOf(false) }
    var detectedModels by remember { mutableStateOf(emptyList<String>()) }
    var showModelPickerDialog by remember { mutableStateOf(false) }
    var targetModelField by remember { mutableStateOf("LLM") }

    // File picker launcher for importing JSON
    val jsonFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    val jsonContent = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                    if (jsonContent.isNotBlank()) {
                        val res = ProviderPresetManager.importFromJsonString(jsonContent, context)
                        if (res.isSuccess) {
                            presets = res.getOrThrow()
                            Toast.makeText(context, "Berhasil mengimpor ${presets.size} preset dari berkas", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, res.exceptionOrNull()?.message ?: "Gagal memproses berkas JSON", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Gagal membaca berkas: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Load initial presets
    LaunchedEffect(Unit) {
        presets = ProviderPresetManager.loadPresets(context)
        if (sttEndpoint.isBlank() && presets.isNotEmpty()) {
            val p = presets.firstOrNull { it.id == selectedPresetId } ?: presets.first()
            sttEndpoint = p.stt.endpoint
            sttModel = p.stt.defaultModel
            llmEndpoint = p.llm.endpoint
            llmModel = p.llm.defaultModel
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f)),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 20.dp)
                    .heightIn(max = 660.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = OnyxBlack),
                border = BorderStroke(1.dp, SteelBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    // 1. Header Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "PENGATURAN MODEL AI",
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "Konfigurasi Endpoint, Model, dan Kunci API",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Tutup",
                                tint = TextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 2. Segmented Tab Bar (Uncluttered 3-tab layout)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, SteelBorder, RoundedCornerShape(8.dp))
                            .background(DarkSlate, RoundedCornerShape(8.dp))
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val tabs = listOf(
                            "PRESET" to 0,
                            "SUARA (STT)" to 1,
                            "NALAR (LLM)" to 2
                        )
                        tabs.forEach { (title, index) ->
                            val isSelected = activeTab == index
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                                    .background(
                                        color = if (isSelected) AccentPrimary else Color.Transparent,
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .clickable { activeTab = index },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "[$title]",
                                    color = if (isSelected) OnyxBlack else TextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 3. Tab Contents (Wrapped in weight so Bottom Bar stays fixed)
                    Box(modifier = Modifier.weight(1f, fill = false)) {
                        when (activeTab) {
                            0 -> PresetManagementTab(
                                presets = presets,
                                selectedPresetId = selectedPresetId,
                                customUrlInput = customUrlInput,
                                isFetchingPresets = isFetchingPresets,
                                onUrlChange = { customUrlInput = it },
                                onFetchFromUrl = {
                                    scope.launch {
                                        isFetchingPresets = true
                                        val res = ProviderPresetManager.fetchFromUrl(customUrlInput, context)
                                        isFetchingPresets = false
                                        if (res.isSuccess) {
                                            presets = res.getOrThrow()
                                            tokenStorage.setCustomPresetsUrl(customUrlInput)
                                            Toast.makeText(context, "Daftar preset berhasil dimuat (${presets.size} preset)", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, res.exceptionOrNull()?.message ?: "Gagal memuat URL", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                onImportFileClick = {
                                    jsonFilePickerLauncher.launch("application/json")
                                },
                                onOpenPasteJson = {
                                    showPasteJsonModal = true
                                },
                                onResetDefault = {
                                    presets = ProviderPresetManager.resetToDefault(context)
                                    customUrlInput = ProviderPresetManager.DEFAULT_GITHUB_RAW_URL
                                    tokenStorage.setCustomPresetsUrl(customUrlInput)
                                    Toast.makeText(context, "Daftar preset dikembalikan ke bawaan", Toast.LENGTH_SHORT).show()
                                },
                                onSelectPreset = { preset ->
                                    selectedPresetId = preset.id
                                    if (preset.id != "custom") {
                                        sttEndpoint = preset.stt.endpoint
                                        sttModel = preset.stt.defaultModel
                                        llmEndpoint = preset.llm.endpoint
                                        llmModel = preset.llm.defaultModel
                                    }
                                    Toast.makeText(context, "Preset [${preset.name}] diterapkan", Toast.LENGTH_SHORT).show()
                                }
                            )

                            1 -> VoiceSttTab(
                                sttEndpoint = sttEndpoint,
                                onSttEndpointChange = { sttEndpoint = it },
                                sttModel = sttModel,
                                onSttModelChange = { sttModel = it },
                                sttKey = sttKey,
                                onSttKeyChange = { sttKey = it },
                                isSttKeyVisible = isSttKeyVisible,
                                onToggleSttKeyVisibility = { isSttKeyVisible = !isSttKeyVisible },
                                isAdaptiveBeta = isAdaptiveBeta,
                                onToggleAdaptiveBeta = { isAdaptiveBeta = it },
                                isDetectingModels = isDetectingModels && targetModelField == "STT",
                                onDetectModels = {
                                    if (sttEndpoint.isBlank()) {
                                        Toast.makeText(context, "Masukkan STT Endpoint terlebih dahulu", Toast.LENGTH_SHORT).show()
                                        return@VoiceSttTab
                                    }
                                    scope.launch {
                                        isDetectingModels = true
                                        targetModelField = "STT"
                                        val res = directAIClient.fetchModels(sttEndpoint, sttKey)
                                        isDetectingModels = false
                                        if (res.isSuccess) {
                                            detectedModels = res.getOrThrow()
                                            showModelPickerDialog = true
                                        } else {
                                            Toast.makeText(context, res.exceptionOrNull()?.message ?: "Gagal deteksi model", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            )

                            2 -> LlmReasoningTab(
                                llmEndpoint = llmEndpoint,
                                onLlmEndpointChange = { llmEndpoint = it },
                                llmModel = llmModel,
                                onLlmModelChange = { llmModel = it },
                                llmKey = llmKey,
                                onLlmKeyChange = { llmKey = it },
                                isLlmKeyVisible = isLlmKeyVisible,
                                onToggleLlmKeyVisibility = { isLlmKeyVisible = !isLlmKeyVisible },
                                sttKey = sttKey,
                                isDetectingModels = isDetectingModels && targetModelField == "LLM",
                                onDetectModels = {
                                    if (llmEndpoint.isBlank()) {
                                        Toast.makeText(context, "Masukkan LLM Endpoint terlebih dahulu", Toast.LENGTH_SHORT).show()
                                        return@LlmReasoningTab
                                    }
                                    scope.launch {
                                        isDetectingModels = true
                                        targetModelField = "LLM"
                                        val keyToUse = if (llmKey.isNotBlank()) llmKey else sttKey
                                        val res = directAIClient.fetchModels(llmEndpoint, keyToUse)
                                        isDetectingModels = false
                                        if (res.isSuccess) {
                                            detectedModels = res.getOrThrow()
                                            showModelPickerDialog = true
                                        } else {
                                            Toast.makeText(context, res.exceptionOrNull()?.message ?: "Gagal deteksi model", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 4. Fixed Bottom Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, SteelBorder)
                        ) {
                            Text(
                                text = "[BATAL]",
                                color = TextSecondary,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                        }

                        Button(
                            onClick = {
                                tokenStorage.setSelectedPreset(selectedPresetId)
                                tokenStorage.setSTTEndpoint(sttEndpoint)
                                tokenStorage.setSTTModel(sttModel)
                                tokenStorage.setSTTKey(sttKey)

                                tokenStorage.setLLMEndpoint(llmEndpoint)
                                tokenStorage.setLLMModel(llmModel)
                                tokenStorage.setLLMKey(llmKey)

                                tokenStorage.setAdaptiveStreamingBetaEnabled(isAdaptiveBeta)
                                tokenStorage.setCustomPresetsUrl(customUrlInput)

                                Toast.makeText(context, "Pengaturan AI berhasil disimpan", Toast.LENGTH_SHORT).show()
                                onSaved(selectedPresetId)
                                onDismiss()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary)
                        ) {
                            Text(
                                text = "[SIMPAN]",
                                color = OnyxBlack,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal Dialog: Model Picker
    if (showModelPickerDialog) {
        Dialog(
            onDismissRequest = { showModelPickerDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            var modelFilterQuery by remember { mutableStateOf("") }
            val filteredList = remember(detectedModels, modelFilterQuery) {
                if (modelFilterQuery.isBlank()) detectedModels
                else detectedModels.filter { it.contains(modelFilterQuery, ignoreCase = true) }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.8f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .heightIn(max = 500.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSlate),
                    border = BorderStroke(1.dp, SteelBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "PILIH MODEL ($targetModelField)",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            IconButton(
                                onClick = { showModelPickerDialog = false },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Tutup",
                                    tint = TextSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = modelFilterQuery,
                            onValueChange = { modelFilterQuery = it },
                            placeholder = { Text("Filter model...", fontSize = 11.sp) },
                            leadingIcon = {
                                Icon(imageVector = Icons.Default.Search, contentDescription = "Search", tint = TextMuted)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentPrimary,
                                unfocusedBorderColor = SteelBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "${filteredList.size} model terdeteksi:",
                            color = TextSecondary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                        ) {
                            items(filteredList) { modelName ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (targetModelField == "STT") {
                                                sttModel = modelName
                                            } else {
                                                llmModel = modelName
                                            }
                                            showModelPickerDialog = false
                                        }
                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = modelName,
                                        color = TextPrimary,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "[PILIH]",
                                        color = AccentPrimary,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                HorizontalDivider(color = SteelBorder.copy(alpha = 0.5f), thickness = 0.5.dp)
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Dialog: Paste JSON
    if (showPasteJsonModal) {
        Dialog(
            onDismissRequest = { showPasteJsonModal = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            var rawJsonText by remember { mutableStateOf("") }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.8f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .heightIn(max = 500.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSlate),
                    border = BorderStroke(1.dp, SteelBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "TEMPEL JSON PRESET",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            IconButton(
                                onClick = { showPasteJsonModal = false },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Tutup", tint = TextSecondary)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = rawJsonText,
                            onValueChange = { rawJsonText = it },
                            placeholder = { Text("{\n  \"providers\": [...]\n}", fontSize = 11.sp) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentPrimary,
                                unfocusedBorderColor = SteelBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    clipboardManager.getText()?.text?.let { rawJsonText = it }
                                },
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, SteelBorder),
                                modifier = Modifier.height(40.dp)
                            ) {
                                Text("[PASTE CLIPBOARD]", color = TextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            }

                            Button(
                                onClick = {
                                    scope.launch {
                                        val res = ProviderPresetManager.importFromJsonString(rawJsonText, context)
                                        if (res.isSuccess) {
                                            presets = res.getOrThrow()
                                            Toast.makeText(context, "Preset berhasil dimuat (${presets.size} preset)", Toast.LENGTH_SHORT).show()
                                            showPasteJsonModal = false
                                        } else {
                                            Toast.makeText(context, res.exceptionOrNull()?.message ?: "Format JSON tidak valid", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                            ) {
                                Text("[IMPOR PRESET]", color = OnyxBlack, fontWeight = FontWeight.Bold, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==============================================================================
// SUB-COMPONENT: TAB 1 (PRESET & MANAGEMENT)
// ==============================================================================
@Composable
private fun PresetManagementTab(
    presets: List<ProviderPreset>,
    selectedPresetId: String,
    customUrlInput: String,
    isFetchingPresets: Boolean,
    onUrlChange: (String) -> Unit,
    onFetchFromUrl: () -> Unit,
    onImportFileClick: () -> Unit,
    onOpenPasteJson: () -> Unit,
    onResetDefault: () -> Unit,
    onSelectPreset: (ProviderPreset) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        // Source URL Input Card
        Text(
            text = "SUMBER PRESET",
            color = AccentPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedTextField(
                value = customUrlInput,
                onValueChange = { onUrlChange(it) },
                label = { Text("URL Presets", fontSize = 10.sp) },
                placeholder = { Text("https://.../providers.json", fontSize = 10.sp) },
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentPrimary,
                    unfocusedBorderColor = SteelBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp)
            )

            OutlinedButton(
                onClick = onFetchFromUrl,
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, AccentPrimary),
                modifier = Modifier.height(52.dp),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Fetch",
                    tint = AccentPrimary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isFetchingPresets) "LOAD..." else "[FETCH]",
                    color = AccentPrimary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Preset action tools row (Import, Paste, Reset)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedButton(
                onClick = onImportFileClick,
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, SteelBorder),
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                Icon(imageVector = Icons.Default.FileOpen, contentDescription = "Import", tint = TextSecondary, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(3.dp))
                Text("[IMPOR FILE]", color = TextSecondary, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            }

            OutlinedButton(
                onClick = onOpenPasteJson,
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, SteelBorder),
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                Icon(imageVector = Icons.Default.ContentPaste, contentDescription = "Paste", tint = TextSecondary, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(3.dp))
                Text("[TEMPEL JSON]", color = TextSecondary, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            }

            OutlinedButton(
                onClick = onResetDefault,
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, SteelBorder),
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = "Reset", tint = CrimsonAlert, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(3.dp))
                Text("[RESET]", color = CrimsonAlert, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Preset items list
        Text(
            text = "DAFTAR PRESET (${presets.size})",
            color = AccentPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(6.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            presets.forEach { preset ->
                val isSelected = preset.id == selectedPresetId
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectPreset(preset) },
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) AccentPrimary.copy(alpha = 0.12f) else CardBackground
                    ),
                    border = BorderStroke(1.dp, if (isSelected) AccentPrimary else SteelBorder)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Text(
                                    text = preset.name,
                                    color = if (isSelected) AccentPrimary else TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "[${preset.badge}]",
                                    color = if (isSelected) AccentPrimary else TextMuted,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            if (isSelected) {
                                Text(
                                    text = "[AKTIF]",
                                    color = EmeraldSuccess,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        if (preset.description.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = preset.description,
                                color = TextSecondary,
                                fontSize = 10.sp,
                                lineHeight = 14.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "STT: ${preset.stt.defaultModel.ifBlank { "Custom" }}",
                                color = TextMuted,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "LLM: ${preset.llm.defaultModel.ifBlank { "Custom" }}",
                                color = TextMuted,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==============================================================================
// SUB-COMPONENT: TAB 2 (VOICE / STT)
// ==============================================================================
@Composable
private fun VoiceSttTab(
    sttEndpoint: String,
    onSttEndpointChange: (String) -> Unit,
    sttModel: String,
    onSttModelChange: (String) -> Unit,
    sttKey: String,
    onSttKeyChange: (String) -> Unit,
    isSttKeyVisible: Boolean,
    onToggleSttKeyVisibility: () -> Unit,
    isAdaptiveBeta: Boolean,
    onToggleAdaptiveBeta: (Boolean) -> Unit,
    isDetectingModels: Boolean,
    onDetectModels: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "ENDPOINT & MODEL SUARA (STT)",
            color = AccentPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(6.dp))

        // STT Endpoint Input
        OutlinedTextField(
            value = sttEndpoint,
            onValueChange = { onSttEndpointChange(it) },
            label = { Text("Endpoint URL", fontSize = 11.sp) },
            placeholder = { Text("https://api.groq.com/openai/v1/audio/transcriptions", fontSize = 11.sp) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentPrimary,
                unfocusedBorderColor = SteelBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // STT Model Input + Auto-detect Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = sttModel,
                onValueChange = { onSttModelChange(it) },
                label = { Text("Model", fontSize = 11.sp) },
                placeholder = { Text("whisper-large-v3-turbo", fontSize = 11.sp) },
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentPrimary,
                    unfocusedBorderColor = SteelBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            )

            OutlinedButton(
                onClick = onDetectModels,
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, SteelBorder),
                modifier = Modifier.height(52.dp),
                contentPadding = PaddingValues(horizontal = 10.dp)
            ) {
                Text(
                    text = if (isDetectingModels) "MENCARI..." else "[DETEKSI]",
                    color = AccentPrimary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // STT API Key Input
        OutlinedTextField(
            value = sttKey,
            onValueChange = { onSttKeyChange(it) },
            label = { Text("API Key STT", fontSize = 11.sp) },
            placeholder = { Text("Masukkan API Key STT", fontSize = 11.sp) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentPrimary,
                unfocusedBorderColor = SteelBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            singleLine = true,
            visualTransformation = if (isSttKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            clipboardManager.getText()?.text?.let { onSttKeyChange(it.trim()) }
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(imageVector = Icons.Default.ContentPaste, contentDescription = "Paste", tint = AccentPrimary, modifier = Modifier.size(16.dp))
                    }
                    IconButton(
                        onClick = onToggleSttKeyVisibility,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = if (isSttKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle",
                            tint = TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            },
            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Adaptive Streaming Switch
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, SteelBorder, RoundedCornerShape(8.dp))
                .background(CardBackground, RoundedCornerShape(8.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "STREAMING ADAPTIF",
                    color = TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "Deteksi jeda bicara (VAD) untuk efisiensi transmisi data.",
                    color = TextSecondary,
                    fontSize = 10.sp
                )
            }
            Switch(
                checked = isAdaptiveBeta,
                onCheckedChange = onToggleAdaptiveBeta,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = AccentPrimary,
                    checkedTrackColor = AccentPrimary.copy(alpha = 0.3f),
                    uncheckedThumbColor = TextMuted,
                    uncheckedTrackColor = DarkSlate
                )
            )
        }
    }
}

// ==============================================================================
// SUB-COMPONENT: TAB 3 (LLM / NALAR)
// ==============================================================================
@Composable
private fun LlmReasoningTab(
    llmEndpoint: String,
    onLlmEndpointChange: (String) -> Unit,
    llmModel: String,
    onLlmModelChange: (String) -> Unit,
    llmKey: String,
    onLlmKeyChange: (String) -> Unit,
    isLlmKeyVisible: Boolean,
    onToggleLlmKeyVisibility: () -> Unit,
    sttKey: String,
    isDetectingModels: Boolean,
    onDetectModels: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ENDPOINT & MODEL BAHASA (LLM)",
                color = AccentPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            if (sttKey.isNotBlank() && llmKey != sttKey) {
                Text(
                    text = "[SALIN DARI STT]",
                    color = AccentPrimary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .clickable { onLlmKeyChange(sttKey) }
                        .padding(2.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))

        // LLM Endpoint Input
        OutlinedTextField(
            value = llmEndpoint,
            onValueChange = { onLlmEndpointChange(it) },
            label = { Text("Endpoint URL", fontSize = 11.sp) },
            placeholder = { Text("https://api.groq.com/openai/v1/chat/completions", fontSize = 11.sp) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentPrimary,
                unfocusedBorderColor = SteelBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // LLM Model Input + Auto-detect Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = llmModel,
                onValueChange = { onLlmModelChange(it) },
                label = { Text("Model", fontSize = 11.sp) },
                placeholder = { Text("llama-3.3-70b-versatile", fontSize = 11.sp) },
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentPrimary,
                    unfocusedBorderColor = SteelBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            )

            OutlinedButton(
                onClick = onDetectModels,
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, SteelBorder),
                modifier = Modifier.height(52.dp),
                contentPadding = PaddingValues(horizontal = 10.dp)
            ) {
                Text(
                    text = if (isDetectingModels) "MENCARI..." else "[DETEKSI]",
                    color = AccentPrimary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // LLM API Key Input
        OutlinedTextField(
            value = llmKey,
            onValueChange = { onLlmKeyChange(it) },
            label = { Text("API Key LLM", fontSize = 11.sp) },
            placeholder = { Text("Masukkan API Key LLM", fontSize = 11.sp) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentPrimary,
                unfocusedBorderColor = SteelBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            singleLine = true,
            visualTransformation = if (isLlmKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            clipboardManager.getText()?.text?.let { onLlmKeyChange(it.trim()) }
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(imageVector = Icons.Default.ContentPaste, contentDescription = "Paste", tint = AccentPrimary, modifier = Modifier.size(16.dp))
                    }
                    IconButton(
                        onClick = onToggleLlmKeyVisibility,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = if (isLlmKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle",
                            tint = TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            },
            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        )
    }
}
