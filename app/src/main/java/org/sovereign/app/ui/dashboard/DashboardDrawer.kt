package org.sovereign.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val DarkSlate = Color(0xFF141A22)
private val SteelBorder = Color(0xFF283342)
private val TextPrimary = Color(0xFFF0F4F8)
private val TextSecondary = Color(0xFF94A3B8)
private val TextMuted = Color(0xFF64748B)
private val AccentPrimary = Color(0xFF38BDF8)
private val EmeraldSuccess = Color(0xFF10B981)
private val CrimsonAlert = Color(0xFFEF4444)

@Composable
fun DrawerNavRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    badge: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = AccentPrimary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                color = TextSecondary,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        badge?.let { b ->
            Box(
                modifier = Modifier
                    .background(Color(0xFF1E2632), RoundedCornerShape(3.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = b,
                    color = AccentPrimary,
                    fontSize = 9.sp,
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
fun DashboardDrawerContent(
    currentVersionName: String,
    currentAIProvider: String,
    onCloseDrawer: () -> Unit,
    onOpenAIEngine: () -> Unit,
    onOpenSTT: () -> Unit,
    onOpenLLM: () -> Unit,
    onOpenBackupRestore: () -> Unit,
    onOpenInfo: () -> Unit,
    onOpenGuide: () -> Unit,
    onCheckUpdate: () -> Unit,
    onSignOut: () -> Unit = {}
) {
    ModalDrawerSheet(
        drawerContainerColor = DarkSlate,
        drawerContentColor = TextPrimary,
        modifier = Modifier
            .fillMaxWidth(0.82f)
            .widthIn(max = 320.dp)
            .fillMaxHeight()
            .border(1.dp, SteelBorder, RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp))
            .statusBarsPadding()
            .padding(top = 8.dp)
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Drawer Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SOVEREIGN",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Speech Intelligence v$currentVersionName",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onCloseDrawer, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close menu", tint = TextSecondary, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Engine Status Summary Card in Drawer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F151E), RoundedCornerShape(8.dp))
                    .border(1.dp, SteelBorder, RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ENGINE STATUS",
                            color = TextMuted,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF1E2632), RoundedCornerShape(3.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "ACTIVE",
                                color = EmeraldSuccess,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(EmeraldSuccess, shape = RoundedCornerShape(3.dp))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Provider: $currentAIProvider",
                            color = TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Text(
                        text = "Storage: Local SQLite",
                        color = TextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = SteelBorder, thickness = 1.dp)
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "NAVIGATION & SETTINGS",
                color = TextMuted,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Menu Item 1: AI Engine Pipeline & Combine
            DrawerNavRow(
                icon = Icons.Default.Tune,
                title = "AI Engine Pipeline",
                subtitle = "Combine STT + LLM & presets",
                badge = formatCompactAIProvider(currentAIProvider),
                onClick = onOpenAIEngine
            )

            // Menu Item 2: Voice (STT) Endpoints Setup
            DrawerNavRow(
                icon = Icons.Default.Mic,
                title = "Voice (STT) Endpoints",
                subtitle = "Setup speech-to-text providers",
                onClick = onOpenSTT
            )

            // Menu Item 3: Reasoning (LLM) Endpoints Setup
            DrawerNavRow(
                icon = Icons.Default.Psychology,
                title = "Reasoning (LLM) Endpoints",
                subtitle = "Setup intelligence & summary models",
                onClick = onOpenLLM
            )

            // Menu Item 4: Backup & Restore Data
            DrawerNavRow(
                icon = Icons.Default.Backup,
                title = "Backup & Restore",
                subtitle = "Export or import meetings & keys",
                onClick = onOpenBackupRestore
            )

            // Menu Item 5: About This App
            DrawerNavRow(
                icon = Icons.Default.Info,
                title = "About This App",
                subtitle = "Version and app details",
                onClick = onOpenInfo
            )

            // Menu Item 6: User Guide & Help
            DrawerNavRow(
                icon = Icons.AutoMirrored.Filled.HelpOutline,
                title = "User Guide & Help",
                subtitle = "API key guide & recording tips",
                onClick = onOpenGuide
            )

            // Menu Item 7: Check for Updates
            DrawerNavRow(
                icon = Icons.Default.Refresh,
                title = "Check for Updates",
                subtitle = "Current version v$currentVersionName",
                onClick = onCheckUpdate
            )

            Spacer(modifier = Modifier.weight(1f))
            HorizontalDivider(color = SteelBorder, thickness = 1.dp)
            Spacer(modifier = Modifier.height(10.dp))

            // Zero-Knowledge Architecture Privacy Badge
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F151E), RoundedCornerShape(6.dp))
                    .border(1.dp, SteelBorder, RoundedCornerShape(6.dp))
                    .padding(vertical = 10.dp, horizontal = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(EmeraldSuccess, shape = RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "ZERO-KNOWLEDGE CORE",
                            color = TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "100% on-device • No external tracking",
                            color = TextSecondary,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}
