package org.sovereign.app.ui.engine

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import org.sovereign.app.auth.TokenStorage
import org.sovereign.app.network.DirectAIClient
import org.sovereign.app.network.EndpointConfigStore
import org.sovereign.app.network.ProviderPreset
import org.sovereign.app.network.ProviderPresetManager
import org.sovereign.app.network.STTEndpointConfig
import java.util.UUID

@Composable
fun STTEndpointsDialog(
    tokenStorage: TokenStorage,
    onDismiss: () -> Unit,
    onActiveChanged: (STTEndpointConfig) -> Unit
) {
    val context = LocalContext.current
    var configs by remember { mutableStateOf(EndpointConfigStore.loadSTTConfigs(tokenStorage)) }
    var activeId by remember { mutableStateOf(tokenStorage.getActiveSTTConfigId().ifBlank { configs.firstOrNull()?.id ?: "" }) }
    var editingConfig by remember { mutableStateOf<STTEndpointConfig?>(null) }
    var endpointToDelete by remember { mutableStateOf<STTEndpointConfig?>(null) }
    var isCreating by remember { mutableStateOf(false) }

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
                .fillMaxHeight(0.90f)
                .padding(horizontal = 16.dp, vertical = 20.dp)
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(EngineColors.AccentPrimary.copy(alpha = 0.15f), RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = null, tint = EngineColors.AccentPrimary, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "SPEECH-TO-TEXT (STT) ENDPOINTS",
                                color = EngineColors.TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "Default: Groq Whisper Turbo (Free Tier)",
                                color = EngineColors.EmeraldSuccess,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = EngineColors.TextSecondary, modifier = Modifier.size(18.dp))
                    }
                }

                HorizontalDivider(color = EngineColors.SteelBorder.copy(alpha = 0.6f))

                // Action Bar: Add new STT button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "CONFIGURED ENDPOINTS (${configs.size})",
                        color = EngineColors.TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )

                    Button(
                        onClick = { isCreating = true },
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EngineColors.AccentPrimary),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = EngineColors.OnyxBlack, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add STT", color = EngineColors.OnyxBlack, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // List of STT endpoints
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(configs) { item ->
                        val isActive = item.id == activeId
                        STTEndpointCard(
                            config = item,
                            isActive = isActive,
                            onActivate = {
                                activeId = item.id
                                EndpointConfigStore.applySTT(tokenStorage, item)
                                onActiveChanged(item)
                                Toast.makeText(context, "Active STT: ${item.name}", Toast.LENGTH_SHORT).show()
                            },
                            onEdit = { editingConfig = item },
                            onDelete = { endpointToDelete = item }
                        )
                    }
                }

                HorizontalDivider(color = EngineColors.SteelBorder.copy(alpha = 0.6f))

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
                        colors = ButtonDefaults.buttonColors(containerColor = EngineColors.AccentPrimary),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Text("DONE", color = EngineColors.OnyxBlack, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }

    // Modal Edit STT
    if (editingConfig != null) {
        STTEditDialog(
            initial = editingConfig!!,
            onDismiss = { editingConfig = null },
            onSave = { updated ->
                val list = configs.map { if (it.id == updated.id) updated else it }
                configs = list
                EndpointConfigStore.saveSTTConfigs(tokenStorage, list)
                if (activeId == updated.id) {
                    EndpointConfigStore.applySTT(tokenStorage, updated)
                    onActiveChanged(updated)
                }
                editingConfig = null
                Toast.makeText(context, "STT endpoint updated", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Modal Add new STT
    if (isCreating) {
        STTEditDialog(
            initial = STTEndpointConfig(
                id = UUID.randomUUID().toString(),
                name = "",
                providerId = "custom",
                endpoint = "",
                model = "",
                apiKey = "",
                adaptiveStreaming = true,
                isDeletable = true
            ),
            isNew = true,
            onDismiss = { isCreating = false },
            onSave = { newConfig ->
                val list = configs + newConfig
                configs = list
                EndpointConfigStore.saveSTTConfigs(tokenStorage, list)
                isCreating = false
                Toast.makeText(context, "Added new STT endpoint", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (endpointToDelete != null) {
        val target = endpointToDelete!!
        AlertDialog(
            onDismissRequest = { endpointToDelete = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            containerColor = EngineColors.DarkSlate,
            shape = RoundedCornerShape(8.dp),
            title = {
                Text(
                    text = "DELETE STT ENDPOINT?",
                    color = EngineColors.CrimsonAlert,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete \"${target.name}\"? This configuration and stored endpoint details will be removed.",
                    color = EngineColors.TextPrimary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val updated = configs.filterNot { it.id == target.id }
                        configs = updated
                        EndpointConfigStore.saveSTTConfigs(tokenStorage, updated)
                        if (activeId == target.id) {
                            val nextActive = updated.firstOrNull()
                            if (nextActive != null) {
                                activeId = nextActive.id
                                EndpointConfigStore.applySTT(tokenStorage, nextActive)
                                onActiveChanged(nextActive)
                            }
                        }
                        endpointToDelete = null
                        Toast.makeText(context, "Endpoint deleted", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EngineColors.CrimsonAlert),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("DELETE", color = androidx.compose.ui.graphics.Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { endpointToDelete = null },
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
private fun STTEndpointCard(
    config: STTEndpointConfig,
    isActive: Boolean,
    onActivate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val host = hostOf(config.endpoint)
    val hasKey = config.apiKey.isNotBlank() || isLocalEndpoint(config.endpoint)

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) EngineColors.AccentPrimary.copy(alpha = 0.12f) else EngineColors.CardBackground
        ),
        border = BorderStroke(1.dp, if (isActive) EngineColors.AccentPrimary else EngineColors.SteelBorder),
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
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = config.name,
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
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text("ACTIVE", color = EngineColors.EmeraldSuccess, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .background(EngineColors.DarkSlate, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = config.providerId.uppercase(),
                        color = EngineColors.AccentPrimary,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                text = "Model: ${config.model}",
                color = EngineColors.TextSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (host.isNotBlank()) host else "Custom URL",
                    color = EngineColors.TextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (hasKey) "Key: Configured" else "Key: Missing",
                    color = if (hasKey) EngineColors.EmeraldSuccess else EngineColors.AmberWarning,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                )
            }

            HorizontalDivider(color = EngineColors.SteelBorder.copy(alpha = 0.4f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onEdit,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, EngineColors.SteelBorder),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = EngineColors.TextSecondary, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Edit", color = EngineColors.TextSecondary, fontSize = 11.sp)
                    }

                    if (config.isDeletable) {
                        IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = EngineColors.CrimsonAlert, modifier = Modifier.size(15.dp))
                        }
                    }
                }

                if (!isActive) {
                    Button(
                        onClick = onActivate,
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EngineColors.AccentPrimary),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("Select STT", color = EngineColors.OnyxBlack, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun STTEditDialog(
    initial: STTEndpointConfig,
    isNew: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (STTEndpointConfig) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initial.name) }
    var endpoint by remember { mutableStateOf(initial.endpoint) }
    var model by remember { mutableStateOf(initial.model) }
    var apiKey by remember { mutableStateOf(initial.apiKey) }
    var showApiKey by remember { mutableStateOf(false) }
    var adaptiveStreaming by remember { mutableStateOf(initial.adaptiveStreaming) }
    var providerId by remember { mutableStateOf(initial.providerId) }

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
                .padding(horizontal = 16.dp, vertical = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isNew) "Add STT Endpoint" else "Edit STT Endpoint",
                        color = EngineColors.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = EngineColors.TextSecondary, modifier = Modifier.size(18.dp))
                    }
                }

                // Name
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("DISPLAY NAME", color = EngineColors.TextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        singleLine = true,
                        placeholder = { Text("e.g. My Custom Whisper / Local STT", color = EngineColors.TextMuted, fontSize = 11.sp) },
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

                // Endpoint URL
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("ENDPOINT URL", color = EngineColors.TextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = endpoint,
                        onValueChange = { endpoint = it },
                        singleLine = true,
                        placeholder = { Text("https://your-domain.com/v1/audio/transcriptions", color = EngineColors.TextMuted, fontSize = 11.sp) },
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

                // Model Name
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("MODEL IDENTIFIER", color = EngineColors.TextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        singleLine = true,
                        placeholder = { Text("e.g. whisper-large-v3, whisper-1", color = EngineColors.TextMuted, fontSize = 11.sp) },
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

                // API Key
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("API KEY (AES-256 ENCRYPTED KEYSTORE VAULT)", color = EngineColors.TextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        singleLine = true,
                        visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showApiKey = !showApiKey }, modifier = Modifier.size(34.dp)) {
                                Icon(
                                    imageVector = if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle API Key visibility",
                                    tint = EngineColors.TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        placeholder = { Text("Enter API key or leave blank for local", color = EngineColors.TextMuted, fontSize = 11.sp) },
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

                // Adaptive Streaming Switch
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Adaptive Streaming (RMS VAD)", color = EngineColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Buffers silence, transmits voice frames only", color = EngineColors.TextMuted, fontSize = 10.sp)
                    }
                    Switch(
                        checked = adaptiveStreaming,
                        onCheckedChange = { adaptiveStreaming = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = EngineColors.OnyxBlack,
                            checkedTrackColor = EngineColors.AccentPrimary,
                            uncheckedThumbColor = EngineColors.TextMuted,
                            uncheckedTrackColor = EngineColors.DarkSlate
                        )
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, EngineColors.SteelBorder),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                    ) {
                        Text("Cancel", color = EngineColors.TextSecondary, fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            val updated = initial.copy(
                                name = name.trim().ifBlank { "STT Endpoint" },
                                endpoint = endpoint.trim(),
                                model = model.trim(),
                                apiKey = apiKey.trim(),
                                adaptiveStreaming = adaptiveStreaming
                            )
                            onSave(updated)
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EngineColors.AccentPrimary),
                        enabled = endpoint.isNotBlank() && model.isNotBlank(),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                    ) {
                        Text("Save STT", color = EngineColors.OnyxBlack, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
