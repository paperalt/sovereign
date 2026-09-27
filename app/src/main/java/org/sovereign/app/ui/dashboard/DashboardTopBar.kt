package org.sovereign.app.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val DarkSlate = Color(0xFF141A22)
private val SteelBorder = Color(0xFF283342)
private val TextPrimary = Color(0xFFF0F4F8)
private val TextSecondary = Color(0xFF94A3B8)
private val AccentPrimary = Color(0xFF38BDF8)
private val EmeraldSuccess = Color(0xFF10B981)
private val CrimsonAlert = Color(0xFFEF4444)

fun formatCompactAIProvider(raw: String): String {
    if (raw.isBlank() || raw == "DEFAULT") return "AI: READY"
    val clean = raw.replace("(Free Tier)", "", ignoreCase = true)
        .replace("(Local)", "", ignoreCase = true)
        .replace("(Custom)", "", ignoreCase = true)
        .replace("Platform", "", ignoreCase = true)
        .replace("Cloud", "", ignoreCase = true)
        .replace("Studio", "", ignoreCase = true)
        .replace("Whisper Turbo", "", ignoreCase = true)
        .replace("Whisper-1", "", ignoreCase = true)
        .replace("Whisper", "", ignoreCase = true)
        .trim()

    if (clean.contains("+")) {
        val parts = clean.split("+").map { it.trim() }
        val w1 = parts.getOrNull(0)?.split(" ")?.filter { it.isNotBlank() } ?: emptyList()
        var w2 = parts.getOrNull(1)?.split(" ")?.filter { it.isNotBlank() } ?: emptyList()
        val p1 = w1.firstOrNull()?.uppercase() ?: "STT"
        if (w2.isNotEmpty() && w2[0].equals(p1, ignoreCase = true)) {
            w2 = w2.drop(1)
        }
        val p2 = if (w2.size >= 2) "${w2[0]} ${w2[1]}".uppercase()
                 else (w2.firstOrNull()?.uppercase() ?: p1)
        return if (p1 == p2) "AI: $p1" else "AI: $p1 + $p2"
    }

    val firstTwo = clean.split(" ").filter { it.isNotBlank() }.take(2).joinToString(" ").uppercase()
    return "AI: $firstTwo"
}

@Composable
fun DashboardTopBar(
    currentVersionName: String,
    currentAIProvider: String,
    onOpenDrawer: () -> Unit,
    onOpenAIEngine: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSlate)
            .border(width = 1.dp, color = SteelBorder)
            .statusBarsPadding()
            .padding(top = 6.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Section: Hamburger Menu + Title + Version Tag (Always Visible, Never squashed)
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onOpenDrawer,
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Open Menu",
                    tint = TextPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "SOVEREIGN",
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                maxLines = 1
            )
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .background(Color(0xFF1E2632), RoundedCornerShape(3.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "v$currentVersionName",
                    color = AccentPrimary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }

        // Right Section: Clickable AI Engine badge (Capped width with Ellipsis so it NEVER pushes the menu)
        val isBYOKActive = (currentAIProvider != "DEFAULT" && currentAIProvider.isNotBlank())
        val compactAIProvider = remember(currentAIProvider) {
            formatCompactAIProvider(currentAIProvider)
        }
        Box(
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(start = 8.dp)
                .widthIn(max = 160.dp)
                .background(
                    if (isBYOKActive) androidx.compose.ui.graphics.Brush.horizontalGradient(
                        listOf(Color(0xFF0E2A3B), Color(0xFF081A26))
                    ) else androidx.compose.ui.graphics.Brush.horizontalGradient(
                        listOf(Color(0xFF1E2632), Color(0xFF141A22))
                    ),
                    RoundedCornerShape(4.dp)
                )
                .border(
                    BorderStroke(
                        1.dp,
                        if (isBYOKActive) androidx.compose.ui.graphics.Brush.horizontalGradient(
                            listOf(AccentPrimary, Color(0xFF0284C7))
                        ) else androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.12f), SteelBorder)
                        )
                    ),
                    RoundedCornerShape(4.dp)
                )
                .clickable(onClick = onOpenAIEngine)
                .padding(horizontal = 8.dp, vertical = 5.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(
                            if (isBYOKActive) EmeraldSuccess else TextSecondary,
                            shape = RoundedCornerShape(3.dp)
                        )
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = compactAIProvider,
                    color = if (isBYOKActive) AccentPrimary else TextPrimary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun MeetingSelectionActionBar(
    selectedCount: Int,
    isAllSelected: Boolean,
    onClearSelection: () -> Unit,
    onRenameClicked: (() -> Unit)?,
    onToggleSelectAll: () -> Unit,
    onMoveClicked: () -> Unit,
    onDeleteClicked: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0F1E2E))
            .border(width = 1.dp, color = AccentPrimary)
            .statusBarsPadding()
            .padding(top = 6.dp)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClearSelection, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Cancel", tint = TextPrimary)
            }
            Spacer(modifier = Modifier.width(6.dp))
            val label = if (selectedCount == 1) "1 Session Selected" else "$selectedCount Sessions Selected"
            Text(
                text = label,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onRenameClicked != null) {
                IconButton(onClick = onRenameClicked, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Rename Session",
                        tint = AccentPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            IconButton(onClick = onToggleSelectAll, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.SelectAll,
                    contentDescription = "Select All",
                    tint = if (isAllSelected) AccentPrimary else TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(onClick = onMoveClicked, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.DriveFileMove,
                    contentDescription = "Move to Group",
                    tint = AccentPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(onClick = onDeleteClicked, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete Selected",
                    tint = CrimsonAlert,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun GroupSelectionActionBar(
    selectedCount: Int,
    isAllSelected: Boolean,
    onClearSelection: () -> Unit,
    onRenameClicked: (() -> Unit)?,
    onToggleSelectAll: () -> Unit,
    onDeleteClicked: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF261014))
            .border(width = 1.dp, color = CrimsonAlert)
            .statusBarsPadding()
            .padding(top = 6.dp)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClearSelection, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Cancel", tint = TextPrimary)
            }
            Spacer(modifier = Modifier.width(6.dp))
            val label = if (selectedCount == 1) "1 Group Selected" else "$selectedCount Groups Selected"
            Text(
                text = label,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onRenameClicked != null) {
                IconButton(onClick = onRenameClicked, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Rename Group",
                        tint = AccentPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            IconButton(onClick = onToggleSelectAll, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.SelectAll,
                    contentDescription = "Select All",
                    tint = if (isAllSelected) AccentPrimary else TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(onClick = onDeleteClicked, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete Group",
                    tint = CrimsonAlert,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
