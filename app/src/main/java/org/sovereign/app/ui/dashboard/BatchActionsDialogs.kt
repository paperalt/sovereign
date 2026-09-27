package org.sovereign.app.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
private val CrimsonAlert = Color(0xFFEF4444)

@Composable
fun BatchAssignGroupDialog(
    selectedCount: Int,
    groups: List<TranscriptGroupDto>,
    onDismiss: () -> Unit,
    onAssign: (groupId: String?) -> Unit
) {
    var chosenGroupId by remember { mutableStateOf<String?>(null) }
    var isRemoveGroup by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        containerColor = DarkSlate,
        shape = RoundedCornerShape(8.dp),
        title = {
            Text(
                text = "MOVE TO GROUP ($selectedCount SESSIONS)",
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Select a destination group to move $selectedCount selected sessions:",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            isRemoveGroup = true
                            chosenGroupId = null
                        }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = isRemoveGroup,
                        onClick = {
                            isRemoveGroup = true
                            chosenGroupId = null
                        },
                        colors = RadioButtonDefaults.colors(selectedColor = AccentPrimary)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Remove from Group (No Group)",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                HorizontalDivider(color = SteelBorder, modifier = Modifier.padding(vertical = 8.dp))

                if (groups.isEmpty()) {
                    Text(
                        text = "No groups available. Create a group first.",
                        color = TextMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
                        items(groups) { grp ->
                            val isPicked = !isRemoveGroup && chosenGroupId == grp.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        isRemoveGroup = false
                                        chosenGroupId = grp.id
                                    }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isPicked,
                                    onClick = {
                                        isRemoveGroup = false
                                        chosenGroupId = grp.id
                                    },
                                    colors = RadioButtonDefaults.colors(selectedColor = AccentPrimary)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(parseSafeColor(grp.color), RoundedCornerShape(5.dp))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = grp.name,
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isRemoveGroup) {
                        onAssign(null)
                    } else if (chosenGroupId != null) {
                        onAssign(chosenGroupId)
                    }
                },
                enabled = isRemoveGroup || chosenGroupId != null,
                colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text("APPLY", color = OnyxBlack, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
fun BatchDeleteMeetingsDialog(
    count: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        containerColor = DarkSlate,
        shape = RoundedCornerShape(8.dp),
        title = {
            val titleText = if (count == 1) "DELETE 1 TRANSCRIPT SESSION?" else "DELETE $count SELECTED SESSIONS?"
            Text(titleText, color = CrimsonAlert, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            val desc = if (count == 1) {
                "Delete this transcription session permanently? All audio, transcripts, and summaries will be removed."
            } else {
                "Are you sure you want to permanently delete $count selected recordings? All transcripts, audio segments, and summaries will be removed."
            }
            Text(
                text = desc,
                color = TextPrimary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = CrimsonAlert),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(if (count == 1) "DELETE" else "DELETE ALL", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
fun BatchDeleteGroupsDialog(
    count: Int,
    onDismiss: () -> Unit,
    onConfirm: (deleteMeetings: Boolean) -> Unit
) {
    var deleteMeetingsAlso by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        containerColor = DarkSlate,
        shape = RoundedCornerShape(8.dp),
        title = {
            val titleText = if (count == 1) "DELETE 1 GROUP / FOLDER?" else "DELETE $count GROUPS / FOLDERS?"
            Text(titleText, color = CrimsonAlert, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                val headerDesc = if (count == 1) {
                    "This group folder will be permanently deleted."
                } else {
                    "$count selected groups will be permanently deleted."
                }
                Text(
                    text = headerDesc,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { deleteMeetingsAlso = !deleteMeetingsAlso }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = deleteMeetingsAlso,
                        onCheckedChange = { deleteMeetingsAlso = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = CrimsonAlert,
                            checkmarkColor = TextPrimary,
                            uncheckedColor = TextMuted
                        ),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (count == 1) "Also delete all recordings inside this group"
                        else "Also delete all recordings inside the selected groups",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "If unchecked, recordings stay in the all-sessions list.",
                    color = TextMuted,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(deleteMeetingsAlso) },
                colors = ButtonDefaults.buttonColors(containerColor = CrimsonAlert),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(if (count == 1) "DELETE GROUP" else "DELETE $count GROUPS", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
fun RenameMeetingDialog(
    meeting: org.sovereign.app.network.MeetingDto,
    isRenaming: Boolean,
    onDismiss: () -> Unit,
    onRename: (newTitle: String) -> Unit
) {
    var newTitle by remember(meeting.id) { mutableStateOf(meeting.title) }

    AlertDialog(
        onDismissRequest = { if (!isRenaming) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        containerColor = DarkSlate,
        shape = RoundedCornerShape(8.dp),
        title = {
            Text(
                text = "RENAME TRANSCRIPT",
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = "Edit session title:",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
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
                    val trimmed = newTitle.trim()
                    if (trimmed.isNotBlank() && !isRenaming) {
                        onRename(trimmed)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                shape = RoundedCornerShape(4.dp),
                enabled = !isRenaming && newTitle.isNotBlank()
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
                shape = RoundedCornerShape(4.dp),
                enabled = !isRenaming
            ) {
                Text("CANCEL", color = TextSecondary, fontSize = 12.sp)
            }
        }
    )
}
