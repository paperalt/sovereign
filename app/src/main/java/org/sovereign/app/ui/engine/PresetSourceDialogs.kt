package org.sovereign.app.ui.engine

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import org.sovereign.app.auth.TokenStorage
import org.sovereign.app.network.ProviderPreset
import org.sovereign.app.network.ProviderPresetManager

@Composable
fun PastePresetsDialog(
    onDismiss: () -> Unit,
    onImported: (List<ProviderPreset>) -> Unit
) {
    var rawText by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Import Presets via JSON",
                            color = EngineColors.TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Paste standard provider schema JSON",
                            color = EngineColors.TextMuted,
                            fontSize = 11.sp
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = EngineColors.TextSecondary, modifier = Modifier.size(18.dp))
                    }
                }

                OutlinedTextField(
                    value = rawText,
                    onValueChange = {
                        rawText = it
                        errorMsg = null
                    },
                    placeholder = {
                        Text(
                            "{\n  \"version\": 1,\n  \"providers\": [\n    {\n      \"id\": \"my-engine\",\n      \"name\": \"My Engine\",\n      ...\n    }\n  ]\n}",
                            color = EngineColors.TextMuted,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 280.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = EngineColors.TextPrimary,
                        unfocusedTextColor = EngineColors.TextPrimary,
                        focusedBorderColor = EngineColors.AccentPrimary,
                        unfocusedBorderColor = EngineColors.SteelBorder,
                        focusedContainerColor = EngineColors.OnyxBlack,
                        unfocusedContainerColor = EngineColors.OnyxBlack,
                        cursorColor = EngineColors.AccentPrimary
                    )
                )

                if (errorMsg != null) {
                    Text(
                        text = errorMsg ?: "",
                        color = EngineColors.CrimsonAlert,
                        fontSize = 11.sp
                    )
                }

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
                        onClick = {
                            val parsed = ProviderPresetManager.parsePresetsJson(rawText)
                            if (parsed.isEmpty()) {
                                errorMsg = "Invalid JSON or missing 'providers' array."
                            } else {
                                onImported(parsed)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EngineColors.AccentPrimary),
                        enabled = rawText.isNotBlank()
                    ) {
                        Text("Apply JSON", color = EngineColors.OnyxBlack, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun PresetSourceSheet(
    context: Context,
    tokenStorage: TokenStorage,
    presetCount: Int,
    onPresetsUpdated: (List<ProviderPreset>) -> Unit,
    onLaunchFilePicker: () -> Unit,
    onOpenPasteJson: () -> Unit,
    onDismiss: () -> Unit
) {
    var customUrl by remember { mutableStateOf(tokenStorage.getCustomPresetsUrl()) }
    var isFetching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

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
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Preset Library Sources",
                            color = EngineColors.TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$presetCount provider presets loaded",
                            color = EngineColors.TextMuted,
                            fontSize = 11.sp
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = EngineColors.TextSecondary, modifier = Modifier.size(18.dp))
                    }
                }

                // Remote Fetch URL
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "REMOTE PRESET URL (GITHUB RAW JSON)",
                        color = EngineColors.TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    OutlinedTextField(
                        value = customUrl,
                        onValueChange = { customUrl = it },
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
                        Button(
                            onClick = {
                                scope.launch {
                                    isFetching = true
                                    val res = ProviderPresetManager.fetchFromUrl(customUrl, context)
                                    isFetching = false
                                    if (res.isSuccess) {
                                        val list = res.getOrThrow()
                                        tokenStorage.setCustomPresetsUrl(customUrl)
                                        onPresetsUpdated(list)
                                        Toast.makeText(context, "Loaded ${list.size} presets from URL", Toast.LENGTH_SHORT).show()
                                        onDismiss()
                                    } else {
                                        Toast.makeText(context, res.exceptionOrNull()?.message ?: "Failed to fetch presets", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            enabled = !isFetching && customUrl.isNotBlank(),
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = EngineColors.AccentPrimary),
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                        ) {
                            if (isFetching) {
                                CircularProgressIndicator(color = EngineColors.OnyxBlack, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Download, contentDescription = null, tint = EngineColors.OnyxBlack, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Fetch Remote", color = EngineColors.OnyxBlack, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                val defUrl = ProviderPresetManager.DEFAULT_GITHUB_RAW_URL
                                customUrl = defUrl
                                tokenStorage.setCustomPresetsUrl(defUrl)
                                val list = ProviderPresetManager.resetToDefault(context)
                                onPresetsUpdated(list)
                                Toast.makeText(context, "Presets reset to default", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            },
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, EngineColors.SteelBorder),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = EngineColors.TextSecondary, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reset", color = EngineColors.TextSecondary, fontSize = 11.sp)
                        }
                    }
                }

                HorizontalDivider(color = EngineColors.SteelBorder.copy(alpha = 0.5f))

                // File and Clipboard options
                Text(
                    text = "MANUAL IMPORT OPTIONS",
                    color = EngineColors.TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onLaunchFilePicker()
                        },
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, EngineColors.SteelBorder),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, tint = EngineColors.AccentPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Import JSON File", color = EngineColors.TextPrimary, fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onOpenPasteJson()
                        },
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, EngineColors.SteelBorder),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                    ) {
                        Text("Paste Raw JSON", color = EngineColors.TextPrimary, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
