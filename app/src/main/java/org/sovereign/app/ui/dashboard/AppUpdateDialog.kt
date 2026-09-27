package org.sovereign.app.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import org.sovereign.app.network.AppVersionDto

private val OnyxBlack = Color(0xFF0A0D12)
private val DarkSlate = Color(0xFF141A22)
private val SteelBorder = Color(0xFF283342)
private val TextPrimary = Color(0xFFF0F4F8)
private val TextSecondary = Color(0xFF94A3B8)
private val TextMuted = Color(0xFF64748B)
private val AccentPrimary = Color(0xFF38BDF8)

@Composable
fun AppUpdateDialog(
    update: AppVersionDto,
    currentVersionCode: Long,
    currentVersionName: String,
    onDismiss: () -> Unit,
    onDownload: () -> Unit
) {
    val isMandatory = update.isCritical || currentVersionCode < update.minSupportedVersionCode

    AlertDialog(
        onDismissRequest = { if (!isMandatory) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        containerColor = DarkSlate,
        shape = RoundedCornerShape(8.dp),
        title = {
            Text("APPLICATION UPDATE AVAILABLE", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Installed Version", color = TextMuted, fontSize = 10.sp)
                        Text(
                            text = "v$currentVersionName (b$currentVersionCode)",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Box(
                        modifier = Modifier
                            .background(AccentPrimary.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .border(1.dp, AccentPrimary, RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Latest: v${update.latestVersionName}",
                            color = AccentPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text("Release Notes:", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F141C), RoundedCornerShape(4.dp))
                        .border(1.dp, SteelBorder, RoundedCornerShape(4.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = update.releaseNotes,
                        color = TextPrimary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDownload,
                colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text("DOWNLOAD UPDATE", color = OnyxBlack, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            if (!isMandatory) {
                OutlinedButton(
                    onClick = onDismiss,
                    border = BorderStroke(1.dp, SteelBorder),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("LATER", color = TextSecondary, fontSize = 12.sp)
                }
            }
        }
    )
}
