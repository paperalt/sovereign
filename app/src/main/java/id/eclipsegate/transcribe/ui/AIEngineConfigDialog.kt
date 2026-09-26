package id.eclipsegate.transcribe.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
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
    meetingRepository: id.eclipsegate.transcribe.data.MeetingRepository? = null,
    onDismiss: () -> Unit,
    onSaved: (provider: String) -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val directAIClient = remember { DirectAIClient() }

    // State for presets
    var presets by remember { mutableStateOf(emptyList<ProviderPreset>()) }
    var selectedPresetId by remember { mutableStateOf(tokenStorage.getSelectedPreset()) }
    var isSyncingPresets by remember { mutableStateOf(false) }

    // State for STT
    var sttEndpoint by remember { mutableStateOf(tokenStorage.getSTTEndpoint()) }
    var sttModel by remember { mutableStateOf(tokenStorage.getSTTModel()) }
    var sttKey by remember { mutableStateOf(tokenStorage.getSTTKey()) }
    var isSttKeyVisible by remember { mutableStateOf(false) }

    // State for LLM
    var llmEndpoint by remember { mutableStateOf(tokenStorage.getLLMEndpoint()) }
    var llmModel by remember { mutableStateOf(tokenStorage.getLLMModel()) }
    var llmKey by remember { mutableStateOf(tokenStorage.getLLMKey()) }
    var isLlmKeyVisible by remember { mutableStateOf(false) }

    // Adaptive streaming toggle
    var isAdaptiveBeta by remember { mutableStateOf(tokenStorage.isAdaptiveStreamingBetaEnabled()) }

    // State for Model Auto-Detection Picker
    var isDetectingModels by remember { mutableStateOf(false) }
    var detectedModels by remember { mutableStateOf(emptyList<String>()) }
    var showModelPickerDialog by remember { mutableStateOf(false) }
    var targetModelField by remember { mutableStateOf("LLM") } // "STT" or "LLM"

    // Load initial presets from Manager
    LaunchedEffect(Unit) {
        presets = ProviderPresetManager.loadPresets(forceRemote = false)
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
                    .padding(horizontal = 20.dp, vertical = 24.dp)
                    .heightIn(max = 680.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = OnyxBlack),
                border = BorderStroke(1.dp, SteelBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    // 1. Header Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "KONFIGURASI AI UNIVERSAL",
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "Kedaulatan Lokal • Direct Endpoint & Auto-Detect",
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

                    Spacer(modifier = Modifier.height(16.dp))

                    // 2. Preset Selection Section
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "PRESET PENYEDIA (GITHUB SYNC)",
                            color = AccentPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable {
                                    scope.launch {
                                        isSyncingPresets = true
                                        val remote = ProviderPresetManager.loadPresets(forceRemote = true)
                                        presets = remote
                                        isSyncingPresets = false
                                        Toast.makeText(context, "Preset AI tersinkronisasi dari GitHub", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Sinkronisasi",
                                tint = AccentPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isSyncingPresets) "SINKRONISASI..." else "UPDATE PRESET",
                                color = AccentPrimary,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Presets Horizontal Row
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(presets) { preset ->
                            val isSelected = preset.id == selectedPresetId
                            Box(
                                modifier = Modifier
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) AccentPrimary else SteelBorder,
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .background(
                                        if (isSelected) AccentPrimary.copy(alpha = 0.15f) else DarkSlate,
                                        RoundedCornerShape(6.dp)
                                    )
                                    .clickable {
                                        selectedPresetId = preset.id
                                        if (preset.id != "custom") {
                                            sttEndpoint = preset.stt.endpoint
                                            sttModel = preset.stt.defaultModel
                                            llmEndpoint = preset.llm.endpoint
                                            llmModel = preset.llm.defaultModel
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
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
                                    if (preset.description.isNotBlank()) {
                                        Text(
                                            text = preset.description,
                                            color = TextSecondary,
                                            fontSize = 10.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 3. Section STT / Voice Configuration
                    Text(
                        text = "1. VOICE / STT (AUDIO KE TEKS)",
                        color = AccentPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    // STT Endpoint Input
                    OutlinedTextField(
                        value = sttEndpoint,
                        onValueChange = { sttEndpoint = it },
                        label = { Text("STT API Endpoint URL", fontSize = 11.sp) },
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

                    Spacer(modifier = Modifier.height(6.dp))

                    // STT Model Input + Auto-detect Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = sttModel,
                            onValueChange = { sttModel = it },
                            label = { Text("STT Model Name", fontSize = 11.sp) },
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
                            onClick = {
                                if (sttEndpoint.isBlank()) {
                                    Toast.makeText(context, "Masukkan STT Endpoint terlebih dahulu", Toast.LENGTH_SHORT).show()
                                    return@OutlinedButton
                                }
                                scope.launch {
                                    isDetectingModels = true
                                    val res = directAIClient.fetchModels(sttEndpoint, sttKey)
                                    isDetectingModels = false
                                    if (res.isSuccess) {
                                        detectedModels = res.getOrThrow()
                                        targetModelField = "STT"
                                        showModelPickerDialog = true
                                    } else {
                                        Toast.makeText(context, res.exceptionOrNull()?.message ?: "Gagal deteksi model", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, SteelBorder),
                            modifier = Modifier.height(52.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp)
                        ) {
                            Text(
                                text = if (isDetectingModels && targetModelField == "STT") "MENCARI..." else "[DETEKSI]",
                                color = AccentPrimary,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // STT API Key Input
                    OutlinedTextField(
                        value = sttKey,
                        onValueChange = { sttKey = it },
                        label = { Text("STT API Key (Android Keystore Encrypted)", fontSize = 11.sp) },
                        placeholder = { Text("gsk_... / AIzaSy... / sk-...", fontSize = 11.sp) },
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
                                        clipboardManager.getText()?.text?.let { sttKey = it.trim() }
                                    },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentPaste,
                                        contentDescription = "Paste",
                                        tint = AccentPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { isSttKeyVisible = !isSttKeyVisible },
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

                    Spacer(modifier = Modifier.height(16.dp))

                    // 4. Section LLM / Reasoning Configuration
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "2. LLM / NALAR & PERTANYAAN RAPAT",
                            color = AccentPrimary,
                            fontSize = 12.sp,
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
                                    .clickable { llmKey = sttKey }
                                    .padding(2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))

                    // LLM Endpoint Input
                    OutlinedTextField(
                        value = llmEndpoint,
                        onValueChange = { llmEndpoint = it },
                        label = { Text("LLM API Endpoint URL", fontSize = 11.sp) },
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

                    Spacer(modifier = Modifier.height(6.dp))

                    // LLM Model Input + Auto-detect Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = llmModel,
                            onValueChange = { llmModel = it },
                            label = { Text("LLM Model Name", fontSize = 11.sp) },
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
                            onClick = {
                                if (llmEndpoint.isBlank()) {
                                    Toast.makeText(context, "Masukkan LLM Endpoint terlebih dahulu", Toast.LENGTH_SHORT).show()
                                    return@OutlinedButton
                                }
                                scope.launch {
                                    isDetectingModels = true
                                    val keyToUse = if (llmKey.isNotBlank()) llmKey else sttKey
                                    val res = directAIClient.fetchModels(llmEndpoint, keyToUse)
                                    isDetectingModels = false
                                    if (res.isSuccess) {
                                        detectedModels = res.getOrThrow()
                                        targetModelField = "LLM"
                                        showModelPickerDialog = true
                                    } else {
                                        Toast.makeText(context, res.exceptionOrNull()?.message ?: "Gagal deteksi model", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, SteelBorder),
                            modifier = Modifier.height(52.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp)
                        ) {
                            Text(
                                text = if (isDetectingModels && targetModelField == "LLM") "MENCARI..." else "[DETEKSI]",
                                color = AccentPrimary,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // LLM API Key Input
                    OutlinedTextField(
                        value = llmKey,
                        onValueChange = { llmKey = it },
                        label = { Text("LLM API Key (Android Keystore Encrypted)", fontSize = 11.sp) },
                        placeholder = { Text("Kunci API khusus penalaran...", fontSize = 11.sp) },
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
                                        clipboardManager.getText()?.text?.let { llmKey = it.trim() }
                                    },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentPaste,
                                        contentDescription = "Paste",
                                        tint = AccentPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { isLlmKeyVisible = !isLlmKeyVisible },
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

                    Spacer(modifier = Modifier.height(16.dp))

                    // 5. Streaming VAD Switch
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
                                text = "STREAMING ADAPTIF (VAD GATED)",
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "Supresi hening < 280 RMS. Menghemat ~78% transmisi audio.",
                                color = TextSecondary,
                                fontSize = 10.sp
                            )
                        }
                        Switch(
                            checked = isAdaptiveBeta,
                            onCheckedChange = { isAdaptiveBeta = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = AccentPrimary,
                                checkedTrackColor = AccentPrimary.copy(alpha = 0.3f),
                                uncheckedThumbColor = TextMuted,
                                uncheckedTrackColor = DarkSlate
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // 6. Action Buttons
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

                                Toast.makeText(context, "Konfigurasi AI tersimpan di Keystore", Toast.LENGTH_SHORT).show()
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

                        // Search box for filtering models
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
                            text = "${filteredList.size} model ditemukan:",
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
}
