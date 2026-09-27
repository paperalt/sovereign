package org.sovereign.app.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import org.sovereign.app.network.TranscriptGroupDto

private val OnyxBlack = Color(0xFF0A0D12)
private val DarkSlate = Color(0xFF141A22)
private val SteelBorder = Color(0xFF283342)
private val TextPrimary = Color(0xFFF0F4F8)
private val TextSecondary = Color(0xFF94A3B8)
private val TextMuted = Color(0xFF64748B)
private val AccentPrimary = Color(0xFF38BDF8)

@Composable
fun CreateGroupDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, description: String, color: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf("#38BDF8") }

    val colors = listOf("#38BDF8", "#10B981", "#F59E0B", "#EC4899", "#8B5CF6", "#64748B")

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        containerColor = DarkSlate,
        shape = RoundedCornerShape(8.dp),
        title = {
            Text("CREATE NEW GROUP / FOLDER", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Group Name", color = TextMuted, fontSize = 13.sp) },
                    placeholder = { Text("e.g. Engineering Sync", color = TextMuted.copy(alpha = 0.5f), fontSize = 13.sp) },
                    trailingIcon = {
                        if (name.isNotEmpty()) {
                            IconButton(onClick = { name = "" }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Clear Name", tint = TextMuted, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = AccentPrimary,
                        unfocusedBorderColor = SteelBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Short Description (Optional)", color = TextMuted, fontSize = 13.sp) },
                    maxLines = 2,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = AccentPrimary,
                        unfocusedBorderColor = SteelBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text("Color Accent:", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    colors.forEach { hex ->
                        val isSelected = selectedColor == hex
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clickable { selectedColor = hex },
                            contentAlignment = androidx.compose.ui.Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(Color(android.graphics.Color.parseColor(hex)), RoundedCornerShape(14.dp))
                                    .border(
                                        width = if (isSelected) 2.dp else 0.dp,
                                        color = if (isSelected) TextPrimary else Color.Transparent,
                                        shape = RoundedCornerShape(14.dp)
                                    )
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onCreate(name.trim(), description.trim(), selectedColor)
                    }
                },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text("SAVE GROUP", color = OnyxBlack, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                border = BorderStroke(1.dp, SteelBorder),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text("CANCEL", color = TextSecondary, fontSize = 12.sp)
            }
        }
    )
}

@Composable
fun RenameGroupDialog(
    group: TranscriptGroupDto,
    isRenaming: Boolean,
    onDismiss: () -> Unit,
    onRename: (newName: String) -> Unit
) {
    var newName by remember(group.id) { mutableStateOf(group.name) }

    AlertDialog(
        onDismissRequest = { if (!isRenaming) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        containerColor = DarkSlate,
        shape = RoundedCornerShape(8.dp),
        title = {
            Text("RENAME GROUP", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text("Rename group folder:", color = TextSecondary, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = AccentPrimary,
                        unfocusedBorderColor = SteelBorder,
                        cursorColor = AccentPrimary,
                        focusedContainerColor = OnyxBlack,
                        unfocusedContainerColor = OnyxBlack
                    ),
                    shape = RoundedCornerShape(6.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = newName.trim()
                    if (trimmed.isNotBlank() && !isRenaming) {
                        onRename(trimmed)
                    }
                },
                enabled = newName.trim().isNotBlank() && !isRenaming,
                colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                shape = RoundedCornerShape(4.dp)
            ) {
                if (isRenaming) {
                    CircularProgressIndicator(color = OnyxBlack, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("SAVE", color = OnyxBlack, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                border = BorderStroke(1.dp, SteelBorder),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text("CANCEL", color = TextSecondary, fontSize = 12.sp)
            }
        }
    )
}
