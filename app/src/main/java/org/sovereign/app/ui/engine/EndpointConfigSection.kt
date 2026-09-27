package org.sovereign.app.ui.engine

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.sovereign.app.network.DirectAIClient
import org.sovereign.app.network.ProviderPreset
import java.net.URI

@Composable
fun EndpointConfigSection(
    context: Context,
    sectionTitle: String,
    sectionSubtitle: String,
    isSTT: Boolean,
    presets: List<ProviderPreset>,
    selectedProviderId: String,
    onProviderSelected: (ProviderPreset) -> Unit,
    endpointUrl: String,
    onEndpointUrlChange: (String) -> Unit,
    modelName: String,
    onModelNameChange: (String) -> Unit,
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    counterpartApiKey: String? = null,
    onUseCounterpartKey: (() -> Unit)? = null,
    adaptiveStreaming: Boolean = false,
    onAdaptiveStreamingChange: ((Boolean) -> Unit)? = null
) {
    var showPassword by remember { mutableStateOf(false) }
    var isDetectingModels by remember { mutableStateOf(false) }
    var detectedModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var showModelPicker by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    val currentPreset = presets.firstOrNull { it.id == selectedProviderId }
    val targetConfig = if (isSTT) currentPreset?.stt else currentPreset?.llm

    // Recommended models for quick chips
    val quickModels = remember(selectedProviderId, isSTT) {
        getQuickModelSuggestions(selectedProviderId, isSTT)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Section Title & Host Banner
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = sectionTitle,
                    color = EngineColors.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = sectionSubtitle,
                    color = EngineColors.TextMuted,
                    fontSize = 11.sp
                )
            }

            val host = hostOf(endpointUrl)
            if (host.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .background(EngineColors.CardBackground, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = host,
                        color = EngineColors.AccentPrimary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // 1. Provider Chips Row
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "SERVICE PROVIDER",
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
                presets.forEach { preset ->
                    val isSelected = preset.id == selectedProviderId
                    val isApplicable = if (isSTT) preset.stt.endpoint.isNotBlank() || preset.id == "custom"
                    else preset.llm.endpoint.isNotBlank() || preset.id == "custom"

                    if (isApplicable) {
                        Box(
                            modifier = Modifier
                                .background(
                                    if (isSelected) EngineColors.AccentPrimary.copy(alpha = 0.18f)
                                    else EngineColors.CardBackground,
                                    RoundedCornerShape(6.dp)
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) EngineColors.AccentPrimary else EngineColors.SteelBorder,
                                    RoundedCornerShape(6.dp)
                                )
                                .clickable { onProviderSelected(preset) }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = preset.name,
                                color = if (isSelected) EngineColors.AccentPrimary else EngineColors.TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // 2. Model Selection Card
        Card(
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = EngineColors.CardBackground),
            border = BorderStroke(1.dp, EngineColors.SteelBorder),
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
                        text = if (isSTT) "SPEECH-TO-TEXT MODEL" else "REASONING MODEL",
                        color = EngineColors.TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )

                    // Auto-detect button
                    Button(
                        onClick = {
                            scope.launch {
                                isDetectingModels = true
                                val client = DirectAIClient()
                                val res = client.fetchModels(endpointUrl, apiKey)
                                isDetectingModels = false
                                if (res.isSuccess) {
                                    val list = res.getOrThrow()
                                    detectedModels = list
                                    showModelPicker = true
                                    Toast.makeText(context, "${list.size} models discovered", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(
                                        context,
                                        res.exceptionOrNull()?.message ?: "Failed to detect models",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        },
                        enabled = !isDetectingModels && endpointUrl.isNotBlank(),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EngineColors.AccentPrimary.copy(alpha = 0.15f),
                            contentColor = EngineColors.AccentPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        if (isDetectingModels) {
                            CircularProgressIndicator(
                                color = EngineColors.AccentPrimary,
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Detect /models", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Model name input
                OutlinedTextField(
                    value = modelName,
                    onValueChange = onModelNameChange,
                    singleLine = true,
                    placeholder = {
                        Text(
                            text = if (isSTT) "e.g. whisper-large-v3-turbo" else "e.g. llama-3.3-70b-versatile",
                            color = EngineColors.TextMuted,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    },
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

                // Quick model suggestion chips
                if (quickModels.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        quickModels.forEach { m ->
                            val isChosen = modelName.equals(m, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (isChosen) EngineColors.AccentPrimary.copy(alpha = 0.2f)
                                        else EngineColors.DarkSlate,
                                        RoundedCornerShape(4.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (isChosen) EngineColors.AccentPrimary else EngineColors.BorderSubtle,
                                        RoundedCornerShape(4.dp)
                                    )
                                    .clickable { onModelNameChange(m) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = m,
                                    color = if (isChosen) EngineColors.AccentPrimary else EngineColors.TextSecondary,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Endpoint URL Card
        Card(
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = EngineColors.CardBackground),
            border = BorderStroke(1.dp, EngineColors.SteelBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ENDPOINT URL",
                        color = EngineColors.TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )

                    // Reset endpoint button if changed
                    val defaultEndpoint = targetConfig?.endpoint ?: ""
                    if (defaultEndpoint.isNotBlank() && endpointUrl != defaultEndpoint) {
                        IconButton(
                            onClick = { onEndpointUrlChange(defaultEndpoint) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reset endpoint",
                                tint = EngineColors.TextSecondary,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = endpointUrl,
                    onValueChange = onEndpointUrlChange,
                    singleLine = true,
                    placeholder = {
                        Text(
                            text = "https://api.provider.com/v1/...",
                            color = EngineColors.TextMuted,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    },
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
            }
        }

        // 4. API Key Card
        val isLocal = isLocalEndpoint(endpointUrl)
        Card(
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = EngineColors.CardBackground),
            border = BorderStroke(1.dp, EngineColors.SteelBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "API KEY (ENCRYPTED ON-DEVICE)",
                        color = EngineColors.TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )

                    // Portal link
                    val keyUrl = currentPreset?.apiKeyUrl ?: ""
                    if (keyUrl.isNotBlank()) {
                        Row(
                            modifier = Modifier
                                .clickable {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(keyUrl))
                                    context.startActivity(intent)
                                }
                                .padding(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Get Key", color = EngineColors.AccentPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.width(3.dp))
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = EngineColors.AccentPrimary, modifier = Modifier.size(12.dp))
                        }
                    }
                }

                if (isLocal) {
                    Text(
                        text = "Localhost endpoint detected: API Key is optional.",
                        color = EngineColors.EmeraldSuccess,
                        fontSize = 11.sp
                    )
                }

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = onApiKeyChange,
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    placeholder = {
                        Text(
                            text = if (isLocal) "Optional local key" else "Enter secret key",
                            color = EngineColors.TextMuted,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { showPassword = !showPassword }, modifier = Modifier.size(34.dp)) {
                                Icon(
                                    imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle visibility",
                                    tint = EngineColors.TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = {
                                    val text = clipboardManager.getText()?.text ?: ""
                                    if (text.isNotBlank()) {
                                        onApiKeyChange(text.trim())
                                        Toast.makeText(context, "Key pasted from clipboard", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentPaste,
                                    contentDescription = "Paste key",
                                    tint = EngineColors.TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
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

                // Shortcut button to reuse counterpart key if applicable
                if (counterpartApiKey != null && counterpartApiKey.isNotBlank() && counterpartApiKey != apiKey && onUseCounterpartKey != null) {
                    OutlinedButton(
                        onClick = onUseCounterpartKey,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, EngineColors.SteelBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(34.dp)
                    ) {
                        Text("Reuse Voice (STT) API Key", color = EngineColors.AccentPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // 5. Adaptive Streaming Toggle (STT Only)
        if (isSTT && onAdaptiveStreamingChange != null) {
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = EngineColors.CardBackground),
                border = BorderStroke(1.dp, EngineColors.SteelBorder),
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
                            text = "Adaptive Streaming (RMS VAD Gated)",
                            color = EngineColors.TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Filters out background silence on-device; only transmits active voice frames (~78% bandwidth reduction).",
                            color = EngineColors.TextMuted,
                            fontSize = 11.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = adaptiveStreaming,
                        onCheckedChange = onAdaptiveStreamingChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = EngineColors.OnyxBlack,
                            checkedTrackColor = EngineColors.AccentPrimary,
                            uncheckedThumbColor = EngineColors.TextMuted,
                            uncheckedTrackColor = EngineColors.DarkSlate
                        )
                    )
                }
            }
        }
    }

    if (showModelPicker) {
        ModelPickerSheet(
            title = if (isSTT) "Select STT Model" else "Select LLM Model",
            currentModel = modelName,
            models = detectedModels,
            onSelect = { onModelNameChange(it) },
            onDismiss = { showModelPicker = false }
        )
    }
}

private fun getQuickModelSuggestions(providerId: String, isSTT: Boolean): List<String> {
    return if (isSTT) {
        when (providerId) {
            "groq" -> listOf("whisper-large-v3-turbo", "whisper-large-v3", "distil-whisper-large-v3-en")
            "gemini" -> listOf("gemini-2.0-flash", "gemini-1.5-flash")
            "openai" -> listOf("whisper-1")
            "ollama" -> listOf("whisper", "whisper:large")
            else -> listOf("whisper-large-v3-turbo", "whisper-1")
        }
    } else {
        when (providerId) {
            "groq" -> listOf("llama-3.3-70b-versatile", "llama-3.1-8b-instant", "mixtral-8x7b-32768")
            "gemini" -> listOf("gemini-2.0-flash", "gemini-1.5-flash", "gemini-2.0-pro-exp-02-05")
            "openai" -> listOf("gpt-4o-mini", "gpt-4o", "gpt-3.5-turbo")
            "deepseek" -> listOf("deepseek-chat", "deepseek-reasoner")
            "openrouter" -> listOf("meta-llama/llama-3.3-70b-instruct", "deepseek/deepseek-chat", "google/gemini-2.0-flash-001")
            "ollama" -> listOf("llama3.2", "llama3.1", "mistral", "qwen2.5")
            else -> listOf("llama-3.3-70b-versatile", "gpt-4o-mini")
        }
    }
}

internal fun hostOf(url: String): String {
    return try {
        val uri = URI(url.trim())
        val host = uri.host ?: ""
        val port = if (uri.port != -1 && uri.port != 80 && uri.port != 443) ":${uri.port}" else ""
        host + port
    } catch (_: Exception) {
        ""
    }
}

internal fun isLocalEndpoint(url: String): Boolean {
    val u = url.lowercase()
    return u.contains("localhost") || u.contains("10.0.2.2") || u.contains("127.0.0.1")
}
