package id.eclipsegate.transcribe.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
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

data class EngineChoice(
    val id: String,
    val tabLabel: String,
    val modelSpec: String
)

@Composable
fun AIEngineConfigDialog(
    tokenStorage: TokenStorage,
    meetingRepository: MeetingRepository? = null,
    onDismiss: () -> Unit,
    onSaved: (sttProvider: String) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var selectedSTT by remember { mutableStateOf(tokenStorage.getSTTProvider()) }
    var selectedLLM by remember { mutableStateOf(tokenStorage.getLLMProvider()) }
    var isAdaptiveBeta by remember { mutableStateOf(tokenStorage.isAdaptiveStreamingBetaEnabled()) }

    // Symmetrical 4-option engine grid
    val sttOptions = listOf(
        EngineChoice("DEFAULT", "SERVER", "Gemini 3.8 Flash (Server Cloud • Kuota Voucher)"),
        EngineChoice("GROQ", "GROQ", "Whisper Large Turbo (Groq LPU 0.3s • Bebas Kuota)"),
        EngineChoice("GEMINI", "GEMINI", "Gemini 2.0 Flash Multimodal (Google Studio • Bebas Kuota)"),
        EngineChoice("OPENAI", "OPENAI", "Whisper-1 (OpenAI Platform • Bebas Kuota)")
    )

    val llmOptions = listOf(
        EngineChoice("DEFAULT", "SERVER", "Gemini 3.8 Flash High (Server Bawaan Terkelola)"),
        EngineChoice("GROQ", "GROQ", "Llama 3.3 70B Versatile (Groq LPU Nalar Cepat)"),
        EngineChoice("GEMINI", "GEMINI", "Gemini 2.0 Flash (Google Studio 1M Token Context)"),
        EngineChoice("OPENAI", "OPENAI", "GPT-4o Mini (OpenAI Reasoning Platform)")
    )

    // Determine which custom provider keys are needed
    val neededProviders = remember(selectedSTT, selectedLLM) {
        val list = mutableListOf<String>()
        if (selectedSTT != "DEFAULT") list.add(selectedSTT)
        if (selectedLLM != "DEFAULT" && !list.contains(selectedLLM)) list.add(selectedLLM)
        list
    }

    var activeKeyTab by remember(neededProviders) {
        mutableStateOf(neededProviders.firstOrNull() ?: "GROQ")
    }

    // Key states mapped per provider
    val providerKeys = remember {
        mutableStateMapOf(
            "GROQ" to (tokenStorage.getProviderApiKey("GROQ") ?: ""),
            "GEMINI" to (tokenStorage.getProviderApiKey("GEMINI") ?: ""),
            "OPENAI" to (tokenStorage.getProviderApiKey("OPENAI") ?: "")
        )
    }
    val apiKeyText = providerKeys[activeKeyTab] ?: ""
    var isKeyVisible by remember { mutableStateOf(false) }

    // Test Key Ping States
    var isTestingKey by remember { mutableStateOf(false) }
    var testResultStatus by remember { mutableStateOf<String?>(null) }
    var isTestSuccessful by remember { mutableStateOf<Boolean?>(null) }

    val keyPrefix = when (activeKeyTab) {
        "GROQ" -> "gsk_"
        "GEMINI" -> "AIza"
        "OPENAI" -> "sk-"
        else -> ""
    }

    val isKeyValid = remember(apiKeyText, keyPrefix) {
        val trimmed = apiKeyText.trim()
        trimmed.isNotBlank() && (keyPrefix.isEmpty() || trimmed.startsWith(keyPrefix)) && trimmed.length >= 20
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = OnyxBlack)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SteelBorder, RoundedCornerShape(12.dp))
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header (Symmetrical alignment)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "PENGATURAN MESIN AI",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Kombinasi bebas mesin audio STT & nalar analisis",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Tutup",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // SECTION 1: Audio STT Engine (4 equal columns)
                Text(
                    text = "1. MESIN TRANSKRIPSI AUDIO (STT)",
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    sttOptions.forEach { opt ->
                        val isChosen = (selectedSTT == opt.id)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .background(
                                    if (isChosen) DarkSlate else CardBackground,
                                    RoundedCornerShape(6.dp)
                                )
                                .border(
                                    1.dp,
                                    if (isChosen) AccentPrimary else SteelBorder,
                                    RoundedCornerShape(6.dp)
                                )
                                .clickable { selectedSTT = opt.id },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = opt.tabLabel,
                                color = if (isChosen) AccentPrimary else TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Medium,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = sttOptions.firstOrNull { it.id == selectedSTT }?.modelSpec ?: sttOptions.first().modelSpec,
                    color = AccentPrimary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(14.dp))

                // SECTION 2: Reasoning LLM Engine (4 equal columns - identical to Row 1)
                Text(
                    text = "2. MESIN RINGKASAN & ANALISIS (LLM)",
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    llmOptions.forEach { opt ->
                        val isChosen = (selectedLLM == opt.id)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .background(
                                    if (isChosen) DarkSlate else CardBackground,
                                    RoundedCornerShape(6.dp)
                                )
                                .border(
                                    1.dp,
                                    if (isChosen) AccentPrimary else SteelBorder,
                                    RoundedCornerShape(6.dp)
                                )
                                .clickable { selectedLLM = opt.id },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = opt.tabLabel,
                                color = if (isChosen) AccentPrimary else TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Medium,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = llmOptions.firstOrNull { it.id == selectedLLM }?.modelSpec ?: llmOptions.first().modelSpec,
                    color = AccentPrimary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(14.dp))

                // SECTION 3: Key Vault Input
                if (neededProviders.isEmpty()) {
                    // Both are server managed
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CardBackground, RoundedCornerShape(6.dp))
                            .border(1.dp, SteelBorder, RoundedCornerShape(6.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Text(
                                text = "INFRASTRUKTUR SERVER TERKELOLA",
                                color = TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Audio dan analisis diproses langsung oleh server. Durasi rapat memotong saldo kuota voucher akun Anda.",
                                color = TextSecondary,
                                fontSize = 10.sp,
                                lineHeight = 14.sp
                            )
                        }
                    }
                } else {
                    // Custom key required
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "KUNCI API (${activeKeyTab})",
                            color = TextMuted,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )

                        // If user uses 2 different custom providers, show comfortable tabs
                        if (neededProviders.size > 1) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                neededProviders.forEach { prov ->
                                    val isCurrentTab = (activeKeyTab == prov)
                                    Box(
                                        modifier = Modifier
                                            .background(if (isCurrentTab) Color(0xFF1E2632) else CardBackground, RoundedCornerShape(4.dp))
                                            .border(1.dp, if (isCurrentTab) AccentPrimary else SteelBorder, RoundedCornerShape(4.dp))
                                            .clickable {
                                                activeKeyTab = prov
                                                testResultStatus = null
                                                isTestSuccessful = null
                                            }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = prov,
                                            color = if (isCurrentTab) AccentPrimary else TextSecondary,
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Input Row with internal Paste + External UJI KUNCI
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = apiKeyText,
                            onValueChange = {
                                providerKeys[activeKeyTab] = it
                                testResultStatus = null
                                isTestSuccessful = null
                            },
                            modifier = Modifier.weight(1f),
                            placeholder = {
                                Text(
                                    text = "$keyPrefix...",
                                    color = TextMuted,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            },
                            visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // 1-Tap Paste inside field
                                    IconButton(
                                        onClick = {
                                            val clip = clipboardManager.getText()
                                            if (!clip.isNullOrBlank()) {
                                                val pasted = clip.text.trim()
                                                providerKeys[activeKeyTab] = pasted
                                                testResultStatus = null
                                                isTestSuccessful = null
                                            }
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentPaste,
                                            contentDescription = "Tempel",
                                            tint = AccentPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    // Visibility toggle
                                    IconButton(
                                        onClick = { isKeyVisible = !isKeyVisible },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = "Toggle",
                                            tint = TextSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = if (isKeyValid) EmeraldSuccess else AccentPrimary,
                                unfocusedBorderColor = if (isKeyValid) EmeraldSuccess else SteelBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedContainerColor = DarkSlate,
                                unfocusedContainerColor = CardBackground
                            ),
                            shape = RoundedCornerShape(6.dp)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // UJI KUNCI Button
                        Button(
                            onClick = {
                                val keyToTest = apiKeyText.trim()
                                if (keyToTest.isNotBlank() && meetingRepository != null) {
                                    isTestingKey = true
                                    testResultStatus = null
                                    scope.launch {
                                        val res = meetingRepository.validateAIKey(activeKeyTab, keyToTest)
                                        isTestingKey = false
                                        res.onSuccess { data ->
                                            isTestSuccessful = data.valid
                                            testResultStatus = if (data.valid) "[VALID] ${data.latencyMs}ms" else "[GAGAL] ${data.message}"
                                        }.onFailure { err ->
                                            isTestSuccessful = false
                                            testResultStatus = "[GAGAL] Tidak terhubung"
                                        }
                                    }
                                } else if (keyToTest.isNotBlank()) {
                                    isTestSuccessful = isKeyValid
                                    testResultStatus = if (isKeyValid) "[SESUAI] Siap Diuji" else "[FORMAT] Salah"
                                }
                            },
                            enabled = !isTestingKey && apiKeyText.isNotBlank(),
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isTestSuccessful == true) Color(0xFF0D281E) else Color(0xFF1E2632),
                                contentColor = if (isTestSuccessful == true) EmeraldSuccess else AccentPrimary,
                                disabledContainerColor = Color(0xFF141A22),
                                disabledContentColor = TextMuted
                            ),
                            border = BorderStroke(1.dp, if (isTestSuccessful == true) EmeraldSuccess else SteelBorder),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp)
                        ) {
                            if (isTestingKey) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = AccentPrimary, strokeWidth = 2.dp)
                            } else {
                                Text("UJI", fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(5.dp))

                    // Status Footnote
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val portal = when (activeKeyTab) {
                            "GROQ" -> "console.groq.com"
                            "GEMINI" -> "aistudio.google.com"
                            "OPENAI" -> "platform.openai.com"
                            else -> ""
                        }
                        Text(
                            text = portal,
                            color = TextMuted,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        testResultStatus?.let { status ->
                            Text(
                                text = status,
                                color = if (isTestSuccessful == true) EmeraldSuccess else CrimsonAlert,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } ?: run {
                            Text(
                                text = if (isKeyValid) "Tersimpan & Siap Dites" else if (apiKeyText.isBlank()) "Belum Dikonfigurasi" else "Awalan: $keyPrefix",
                                color = if (isKeyValid) EmeraldSuccess else TextMuted,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // SUMMARY COMBO CARD (Fixed branding strings)
                val audioSummary = when (selectedSTT) {
                    "DEFAULT" -> "Server Cloud (Kuota Voucher)"
                    "GROQ" -> "Groq LPU (Whisper Turbo • Bebas Kuota)"
                    "GEMINI" -> "Google Studio (2.5 Flash • Bebas Kuota)"
                    "OPENAI" -> "OpenAI (Whisper-1 • Bebas Kuota)"
                    else -> selectedSTT
                }
                val llmSummary = when (selectedLLM) {
                    "DEFAULT" -> "Server Cloud (Gemini 3.8 Flash High)"
                    "GROQ" -> "Groq LPU (GPT-OSS 120B Nalar)"
                    "GEMINI" -> "Google Studio (2.5 Flash 1M Context)"
                    "OPENAI" -> "OpenAI (GPT-4o Mini)"
                    else -> selectedLLM
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F151E), RoundedCornerShape(8.dp))
                        .border(1.dp, SteelBorder, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "KOMBINASI AKTIF",
                            color = TextMuted,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Audio:", color = TextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = audioSummary,
                                color = TextPrimary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Nalar:", color = TextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = llmSummary,
                                color = TextPrimary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Beta Feature Card: Adaptive Streaming Pipeline (VAD + Frame Compaction + Low Latency)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, if (isAdaptiveBeta) AccentPrimary else SteelBorder, RoundedCornerShape(8.dp)),
                    colors = CardDefaults.cardColors(containerColor = if (isAdaptiveBeta) Color(0xFF0E2236) else DarkSlate)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "STREAMING ADAPTIF",
                                color = if (isAdaptiveBeta) AccentPrimary else TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Penggabungan VAD Client (hemat data 80%), konsolidasi paket audio, dan latensi respons cepat 2.5s.",
                                color = TextSecondary,
                                fontSize = 10.sp,
                                lineHeight = 14.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Switch(
                            checked = isAdaptiveBeta,
                            onCheckedChange = { isAdaptiveBeta = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = OnyxBlack,
                                checkedTrackColor = AccentPrimary,
                                uncheckedThumbColor = TextMuted,
                                uncheckedTrackColor = Color(0xFF1E2632)
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Actions: Symmetrical 50/50 Balanced Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, SteelBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                    ) {
                        Text(text = "BATAL", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = {
                            tokenStorage.setSTTProvider(selectedSTT)
                            tokenStorage.setLLMProvider(selectedLLM)
                            tokenStorage.setAdaptiveStreamingBetaEnabled(isAdaptiveBeta)
                            for (prov in neededProviders) {
                                val key = providerKeys[prov]?.trim()
                                if (!key.isNullOrBlank()) {
                                    tokenStorage.setProviderApiKey(prov, key)
                                } else {
                                    tokenStorage.setProviderApiKey(prov, null)
                                }
                            }
                            onSaved(selectedSTT)
                            onDismiss()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentPrimary,
                            contentColor = Color(0xFF04121E)
                        )
                    ) {
                        Text(text = "TERAPKAN", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
