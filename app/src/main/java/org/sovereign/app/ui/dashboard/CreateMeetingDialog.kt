package org.sovereign.app.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
fun CreateMeetingDialog(
    groups: List<TranscriptGroupDto> = emptyList(),
    initialGroupId: String? = null,
    onDismiss: () -> Unit,
    onCreate: (title: String, language: String, targetLanguage: String, groupId: String?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var selectedLang by remember { mutableStateOf("id") }
    var selectedTarget by remember { mutableStateOf("") }
    var selectedGroupId by remember(initialGroupId) { mutableStateOf(initialGroupId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        containerColor = DarkSlate,
        shape = RoundedCornerShape(8.dp),
        title = {
            Text("START RECORDING SESSION", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Session Title", color = TextMuted, fontSize = 13.sp) },
                    placeholder = { Text("e.g. Planning Discussion", color = TextMuted.copy(alpha = 0.5f), fontSize = 13.sp) },
                    trailingIcon = {
                        if (title.isNotEmpty()) {
                            IconButton(onClick = { title = "" }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextMuted, modifier = Modifier.size(16.dp))
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

                if (groups.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Folder:", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item {
                            FilterChip(
                                selected = selectedGroupId == null,
                                onClick = { selectedGroupId = null },
                                label = { Text("No Folder", fontSize = 11.sp) }
                            )
                        }
                        items(groups) { grp ->
                            FilterChip(
                                selected = selectedGroupId == grp.id,
                                onClick = { selectedGroupId = grp.id },
                                label = { Text(grp.name, fontSize = 11.sp) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text("Source Audio Language:", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listOf("id" to "ID", "en" to "EN", "ja" to "JA", "auto" to "AUTO")) { (code, label) ->
                        FilterChip(
                            selected = selectedLang == code,
                            onClick = { selectedLang = code },
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text("Target Translation (Optional):", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listOf("" to "None", "id" to "To ID", "en" to "To EN")) { (code, label) ->
                        FilterChip(
                            selected = selectedTarget == code,
                            onClick = { selectedTarget = code },
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalTitle = if (title.isBlank()) "Session ${System.currentTimeMillis() % 10000}" else title
                    onCreate(finalTitle, selectedLang, selectedTarget, selectedGroupId)
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text("START RECORDING", color = OnyxBlack, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
