package org.sovereign.app.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import org.sovereign.app.auth.EncryptedTokenStorage
import org.sovereign.app.data.MeetingRepository
import org.sovereign.app.network.AppVersionDto
import org.sovereign.app.network.MeetingDto
import org.sovereign.app.network.SubscriptionPlanDto
import org.sovereign.app.network.TranscriptGroupDto
import org.sovereign.app.network.UserQuotaDto
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val OnyxBlack = Color(0xFF0A0D12)
private val DarkSlate = Color(0xFF141A22)
private val SteelBorder = Color(0xFF283342)
private val TextPrimary = Color(0xFFF0F4F8)
private val TextSecondary = Color(0xFF94A3B8)
private val TextMuted = Color(0xFF64748B)
private val AccentPrimary = Color(0xFF38BDF8)
private val EmeraldSuccess = Color(0xFF10B981)
private val CrimsonAlert = Color(0xFFE11D48)
private val AmberWarning = Color(0xFFF59E0B)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DashboardScreen(
    meetingRepository: MeetingRepository,
    onNavigateToMeeting: (String) -> Unit,
    onNavigateToLive: (String, String) -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val tokenStorage = remember { EncryptedTokenStorage(context) }
    var currentAIProvider by remember { mutableStateOf(tokenStorage.getAIProvider()) }
    var showAIEngineDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showGuideDialog by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val (currentVersionCode, currentVersionName) = remember { getInstalledVersionInfo(context) }
    val scope = rememberCoroutineScope()
    var allMeetings by remember { mutableStateOf<List<MeetingDto>>(emptyList()) }
    var displayedMeetings by remember { mutableStateOf<List<MeetingDto>>(emptyList()) }
    var activeMeeting by remember { mutableStateOf<MeetingDto?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showSubscriptionDialog by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var availableUpdate by remember { mutableStateOf<AppVersionDto?>(null) }
    var userQuota by remember { mutableStateOf<UserQuotaDto?>(null) }
    var plans by remember { mutableStateOf<List<SubscriptionPlanDto>>(emptyList()) }

    // Grouping & Hierarchy State
    var selectedMainTab by remember { mutableStateOf(0) } // 0 = SEMUA SESI, 1 = GRUP / FOLDER
    var groups by remember { mutableStateOf<List<TranscriptGroupDto>>(emptyList()) }
    var selectedGroupDetail by remember { mutableStateOf<TranscriptGroupDto?>(null) }
    var groupMeetings by remember { mutableStateOf<List<MeetingDto>>(emptyList()) }
    var isGroupLoading by remember { mutableStateOf(false) }
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var meetingToRename by remember { mutableStateOf<MeetingDto?>(null) }
    var groupToRename by remember { mutableStateOf<TranscriptGroupDto?>(null) }
    var isRenaming by remember { mutableStateOf(false) }
    var preselectedGroupIdForMeeting by remember { mutableStateOf<String?>(null) }

    // Multi-Selection State for Meetings and Groups
    var selectedMeetingIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val isMeetingSelectionMode = selectedMeetingIds.isNotEmpty()

    var selectedGroupIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val isGroupSelectionMode = selectedGroupIds.isNotEmpty()

    var showBatchAssignGroupDialog by remember { mutableStateOf(false) }
    var showBatchDeleteMeetingsDialog by remember { mutableStateOf(false) }
    var showBatchDeleteGroupsDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = isMeetingSelectionMode || isGroupSelectionMode || selectedGroupDetail != null) {
        if (isMeetingSelectionMode) {
            selectedMeetingIds = emptySet()
        } else if (isGroupSelectionMode) {
            selectedGroupIds = emptySet()
        } else if (selectedGroupDetail != null) {
            selectedMeetingIds = emptySet()
            selectedGroupDetail = null
        }
    }

    fun refreshData() {
        scope.launch {
            isLoading = true
            val activeRes = meetingRepository.getActiveMeeting()
            activeMeeting = activeRes.getOrNull()

            val quotaRes = meetingRepository.getUserQuota()
            userQuota = quotaRes.getOrNull()

            val plansRes = meetingRepository.getPlans()
            plans = plansRes.getOrDefault(emptyList())

            val versionRes = meetingRepository.checkAppVersion()
            versionRes.onSuccess { ver ->
                if (ver.latestVersionCode > currentVersionCode) {
                    availableUpdate = ver
                    if (ver.isCritical || currentVersionCode < ver.minSupportedVersionCode) {
                        showUpdateDialog = true
                    }
                }
            }

            val listRes = meetingRepository.listMeetings(limit = 50)
            val list = listRes.getOrDefault(emptyList())
            allMeetings = list
            if (searchQuery.isBlank()) {
                displayedMeetings = list
            } else {
                val q = searchQuery.trim().lowercase()
                displayedMeetings = list.filter { m ->
                    m.title.lowercase().contains(q) ||
                    (m.summary?.lowercase()?.contains(q) == true) ||
                    (m.groupName?.lowercase()?.contains(q) == true) ||
                    m.language.lowercase().contains(q)
                }
            }

            val groupsRes = meetingRepository.listGroups()
            groups = groupsRes.getOrDefault(emptyList())

            if (selectedGroupDetail != null) {
                val grpRes = meetingRepository.getGroup(selectedGroupDetail!!.id)
                grpRes.onSuccess {
                    selectedGroupDetail = it.group
                    groupMeetings = it.meetings
                }
            }

            isLoading = false
        }
    }

    fun loadGroupDetail(groupId: String) {
        scope.launch {
            isGroupLoading = true
            val res = meetingRepository.getGroup(groupId)
            res.onSuccess {
                selectedGroupDetail = it.group
                groupMeetings = it.meetings
                isGroupLoading = false
            }.onFailure {
                Toast.makeText(context, "Gagal memuat isi grup", Toast.LENGTH_SHORT).show()
                isGroupLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshData()
    }

    // App Update Dialog
    availableUpdate?.let { update ->
        if (showUpdateDialog) {
            AppUpdateDialog(
                update = update,
                currentVersionCode = currentVersionCode,
                currentVersionName = currentVersionName,
                onDismiss = { showUpdateDialog = false },
                onDownload = {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(update.downloadUrl)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) {}
                }
            )
        }
    }

    // Subscription & Voucher Dialog (Voucher-Only Mode)
    if (showSubscriptionDialog) {
        SubscriptionDialog(
            currentQuota = userQuota,
            plans = plans,
            onDismiss = { showSubscriptionDialog = false },
            onRedeem = { code, onResult ->
                scope.launch {
                    val res = meetingRepository.redeemVoucher(code)
                    res.onSuccess { msg ->
                        onResult(null, msg)
                        refreshData()
                    }.onFailure { err ->
                        onResult(err.message ?: "Gagal menukarkan voucher", null)
                    }
                }
            }
        )
    }

    // New Meeting Dialog
    if (showCreateDialog) {
        val customSTTActive = (tokenStorage.getSTTProvider() != "DEFAULT" && !tokenStorage.getProviderApiKey(tokenStorage.getSTTProvider()).isNullOrBlank())
        CreateMeetingDialog(
            userQuota = userQuota,
            isCustomSTT = customSTTActive,
            groups = groups,
            initialGroupId = preselectedGroupIdForMeeting,
            onDismiss = {
                showCreateDialog = false
                preselectedGroupIdForMeeting = null
            },
            onUpgradeClicked = {
                showCreateDialog = false
                showSubscriptionDialog = true
            },
            onCreate = { title, lang, targetLang, groupId ->
                showCreateDialog = false
                preselectedGroupIdForMeeting = null
                scope.launch {
                    val res = meetingRepository.createMeeting(title, lang, targetLang, groupId)
                    res.onSuccess { newMeeting ->
                        onNavigateToLive(newMeeting.id, newMeeting.title)
                    }.onFailure {
                        refreshData()
                    }
                }
            }
        )
    }

    // Create Group Dialog
    if (showCreateGroupDialog) {
        CreateGroupDialog(
            onDismiss = { showCreateGroupDialog = false },
            onCreate = { name, desc, color ->
                showCreateGroupDialog = false
                scope.launch {
                    val res = meetingRepository.createGroup(name, desc, color)
                    res.onSuccess {
                        Toast.makeText(context, "Grup \"$name\" berhasil dibuat", Toast.LENGTH_SHORT).show()
                        refreshData()
                    }.onFailure {
                        Toast.makeText(context, "Gagal membuat grup: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    // Batch Assign Group Dialog
    if (showBatchAssignGroupDialog && selectedMeetingIds.isNotEmpty()) {
        BatchAssignGroupDialog(
            selectedCount = selectedMeetingIds.size,
            groups = groups,
            onDismiss = { showBatchAssignGroupDialog = false },
            onAssign = { targetGroupId ->
                showBatchAssignGroupDialog = false
                val ids = selectedMeetingIds.toList()
                selectedMeetingIds = emptySet()
                scope.launch {
                    val res = meetingRepository.batchAssignMeetingGroup(ids, targetGroupId)
                    res.onSuccess {
                        Toast.makeText(context, "${ids.size} sesi berhasil dipindahkan", Toast.LENGTH_SHORT).show()
                        if (selectedGroupDetail != null) {
                            loadGroupDetail(selectedGroupDetail!!.id)
                        }
                        refreshData()
                    }.onFailure {
                        Toast.makeText(context, "Gagal memindahkan sesi: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    // Batch Delete Meetings Dialog
    if (showBatchDeleteMeetingsDialog && selectedMeetingIds.isNotEmpty()) {
        BatchDeleteMeetingsDialog(
            count = selectedMeetingIds.size,
            onDismiss = { showBatchDeleteMeetingsDialog = false },
            onConfirm = {
                showBatchDeleteMeetingsDialog = false
                val ids = selectedMeetingIds.toList()
                selectedMeetingIds = emptySet()
                scope.launch {
                    val res = meetingRepository.batchDeleteMeetings(ids)
                    res.onSuccess {
                        Toast.makeText(context, "${ids.size} sesi berhasil dihapus", Toast.LENGTH_SHORT).show()
                        if (selectedGroupDetail != null) {
                            loadGroupDetail(selectedGroupDetail!!.id)
                        }
                        refreshData()
                    }.onFailure {
                        Toast.makeText(context, "Gagal menghapus sesi: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    // Batch Delete Groups Dialog
    if (showBatchDeleteGroupsDialog && selectedGroupIds.isNotEmpty()) {
        BatchDeleteGroupsDialog(
            count = selectedGroupIds.size,
            onDismiss = { showBatchDeleteGroupsDialog = false },
            onConfirm = { deleteMeetingsAlso ->
                showBatchDeleteGroupsDialog = false
                val ids = selectedGroupIds.toList()
                selectedGroupIds = emptySet()
                scope.launch {
                    val res = meetingRepository.batchDeleteGroups(ids, deleteMeetingsAlso)
                    res.onSuccess {
                        Toast.makeText(context, "${ids.size} grup berhasil dihapus", Toast.LENGTH_SHORT).show()
                        if (selectedGroupDetail != null && selectedGroupDetail!!.id in ids) {
                            selectedGroupDetail = null
                        }
                        refreshData()
                    }.onFailure {
                        Toast.makeText(context, "Gagal menghapus grup: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    // Rename Single Meeting Dialog
    if (meetingToRename != null) {
        val meeting = meetingToRename!!
        var newTitle by remember(meeting.id) { mutableStateOf(meeting.title) }

        AlertDialog(
            onDismissRequest = { if (!isRenaming) meetingToRename = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            containerColor = DarkSlate,
            shape = RoundedCornerShape(8.dp),
            title = {
                Text(
                    text = "GANTI NAMA TRANSKRIP",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Ubah judul sesi pertemuan / transkripsi:",
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
                            isRenaming = true
                            scope.launch {
                                val res = meetingRepository.updateMeetingTitle(meeting.id, trimmed)
                                res.onSuccess {
                                    Toast.makeText(context, "Nama transkrip berhasil diubah", Toast.LENGTH_SHORT).show()
                                    meetingToRename = null
                                    selectedMeetingIds = emptySet()
                                    refreshData()
                                    if (selectedGroupDetail != null) {
                                        loadGroupDetail(selectedGroupDetail!!.id)
                                    }
                                }.onFailure {
                                    Toast.makeText(context, "Gagal mengubah nama: ${it.message}", Toast.LENGTH_SHORT).show()
                                }
                                isRenaming = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                    shape = RoundedCornerShape(4.dp),
                    enabled = !isRenaming && newTitle.isNotBlank()
                ) {
                    Text(if (isRenaming) "MENYIMPAN..." else "SIMPAN", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { meetingToRename = null },
                    border = BorderStroke(1.dp, SteelBorder),
                    shape = RoundedCornerShape(4.dp),
                    enabled = !isRenaming
                ) {
                    Text("BATAL", color = TextPrimary, fontSize = 12.sp)
                }
            }
        )
    }

    // Rename Single Group Dialog
    if (groupToRename != null) {
        val group = groupToRename!!
        var newName by remember(group.id) { mutableStateOf(group.name) }

        AlertDialog(
            onDismissRequest = { if (!isRenaming) groupToRename = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            containerColor = DarkSlate,
            shape = RoundedCornerShape(8.dp),
            title = {
                Text(
                    text = "GANTI NAMA GRUP",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Ubah nama folder grup transkripsi:",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
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
                            isRenaming = true
                            scope.launch {
                                val res = meetingRepository.updateGroup(group.id, trimmed, group.description, group.color)
                                res.onSuccess {
                                    Toast.makeText(context, "Nama grup berhasil diubah", Toast.LENGTH_SHORT).show()
                                    groupToRename = null
                                    selectedGroupIds = emptySet()
                                    refreshData()
                                    if (selectedGroupDetail != null && selectedGroupDetail!!.id == group.id) {
                                        selectedGroupDetail = selectedGroupDetail!!.copy(name = trimmed)
                                    }
                                }.onFailure {
                                    Toast.makeText(context, "Gagal mengubah nama grup: ${it.message}", Toast.LENGTH_SHORT).show()
                                }
                                isRenaming = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                    shape = RoundedCornerShape(4.dp),
                    enabled = !isRenaming && newName.isNotBlank()
                ) {
                    Text(if (isRenaming) "MENYIMPAN..." else "SIMPAN", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { groupToRename = null },
                    border = BorderStroke(1.dp, SteelBorder),
                    shape = RoundedCornerShape(4.dp),
                    enabled = !isRenaming
                ) {
                    Text("BATAL", color = TextPrimary, fontSize = 12.sp)
                }
            }
        )
    }

    // AI Engine Configuration Dialog
    if (showAIEngineDialog) {
        AIEngineConfigDialog(
            tokenStorage = tokenStorage,
            meetingRepository = meetingRepository,
            onDismiss = { showAIEngineDialog = false },
            onSaved = { provider ->
                currentAIProvider = provider
            }
        )
    }

    // System Info Dialog
    if (showInfoDialog) {
        SystemInfoDialog(
            currentVersionName = currentVersionName,
            onDismiss = { showInfoDialog = false }
        )
    }

    // User Guide Dialog
    if (showGuideDialog) {
        GuideDialog(
            onDismiss = { showGuideDialog = false }
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
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
                                text = "TRANSCRIBE CORE",
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
                        IconButton(onClick = { scope.launch { drawerState.close() } }) {
                            Icon(Icons.Default.Close, contentDescription = "Tutup Menu", tint = TextSecondary)
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
                                    text = "STATUS ENGINE",
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
                                        text = "AKTIF",
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
                                    text = "Penyedia: $currentAIProvider",
                                    color = TextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Text(
                                text = "Penyimpanan: SQLite Lokal",
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
                        text = "NAVIGASI & PENGATURAN",
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Menu Item 1: Konfigurasi AI Engine
                    DrawerNavRow(
                        icon = Icons.Default.Settings,
                        title = "Pengaturan Model AI",
                        subtitle = "Endpoint, model, dan API key",
                        badge = currentAIProvider,
                        onClick = {
                            scope.launch { drawerState.close() }
                            showAIEngineDialog = true
                        }
                    )

                    // Menu Item 2: Informasi Aplikasi
                    DrawerNavRow(
                        icon = Icons.Default.Info,
                        title = "Informasi Aplikasi",
                        subtitle = "Versi dan detail aplikasi",
                        onClick = {
                            scope.launch { drawerState.close() }
                            showInfoDialog = true
                        }
                    )

                    // Menu Item 3: Panduan Penggunaan
                    DrawerNavRow(
                        icon = Icons.AutoMirrored.Filled.HelpOutline,
                        title = "Panduan & Bantuan",
                        subtitle = "Panduan API Key & tips rekam",
                        onClick = {
                            scope.launch { drawerState.close() }
                            showGuideDialog = true
                        }
                    )

                    // Menu Item 5: Periksa Pembaruan
                    DrawerNavRow(
                        icon = Icons.Default.Refresh,
                        title = "Periksa Pembaruan",
                        subtitle = "Versi saat ini v$currentVersionName",
                        onClick = {
                            scope.launch {
                                drawerState.close()
                                val versionRes = meetingRepository.checkAppVersion()
                                versionRes.onSuccess { ver ->
                                    if (ver.latestVersionCode > currentVersionCode) {
                                        availableUpdate = ver
                                        showUpdateDialog = true
                                    } else {
                                        Toast.makeText(context, "Aplikasi sudah versi terbaru (v$currentVersionName)", Toast.LENGTH_SHORT).show()
                                    }
                                }.onFailure {
                                    Toast.makeText(context, "Gagal memeriksa pembaruan", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )

                    Spacer(modifier = Modifier.weight(1f))
                    HorizontalDivider(color = SteelBorder, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(10.dp))

                    // Logout Action
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                scope.launch { drawerState.close() }
                                onLogout()
                            }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Keluar",
                            tint = CrimsonAlert,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Keluar Akun",
                            color = CrimsonAlert,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    ) {
        Scaffold(
            containerColor = OnyxBlack,
            topBar = {
                if (isMeetingSelectionMode && (selectedMainTab == 0 || selectedGroupDetail != null)) {
                    // Contextual Action Bar for Meetings
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
                            IconButton(onClick = { selectedMeetingIds = emptySet() }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Batal", tint = TextPrimary)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${selectedMeetingIds.size} Sesi Terpilih",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val allVisibleIds = if (selectedGroupDetail != null) {
                                groupMeetings.map { it.id }.toSet()
                            } else {
                                displayedMeetings.map { it.id }.toSet()
                            }
                            val isAllSelected = selectedMeetingIds.containsAll(allVisibleIds) && allVisibleIds.isNotEmpty()

                            if (selectedMeetingIds.size == 1) {
                                IconButton(
                                    onClick = {
                                        val targetId = selectedMeetingIds.first()
                                        meetingToRename = displayedMeetings.find { it.id == targetId }
                                            ?: groupMeetings.find { it.id == targetId }
                                            ?: allMeetings.find { it.id == targetId }
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Ganti Nama",
                                        tint = AccentPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    selectedMeetingIds = if (isAllSelected) emptySet() else (selectedMeetingIds + allVisibleIds)
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SelectAll,
                                    contentDescription = "Pilih Semua",
                                    tint = if (isAllSelected) AccentPrimary else TextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(
                                onClick = { showBatchAssignGroupDialog = true },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.DriveFileMove,
                                    contentDescription = "Pindahkan ke Grup",
                                    tint = AccentPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(
                                onClick = { showBatchDeleteMeetingsDialog = true },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Hapus Terpilih",
                                    tint = CrimsonAlert,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                } else if (isGroupSelectionMode && selectedMainTab == 1 && selectedGroupDetail == null) {
                    // Contextual Action Bar for Groups
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
                            IconButton(onClick = { selectedGroupIds = emptySet() }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Batal", tint = TextPrimary)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${selectedGroupIds.size} Grup Terpilih",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val allGIds = groups.map { it.id }.toSet()
                            val isAllSelected = selectedGroupIds.containsAll(allGIds) && allGIds.isNotEmpty()

                            if (selectedGroupIds.size == 1) {
                                IconButton(
                                    onClick = {
                                        val targetId = selectedGroupIds.first()
                                        groupToRename = groups.find { it.id == targetId }
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Ganti Nama",
                                        tint = AccentPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    selectedGroupIds = if (isAllSelected) emptySet() else (selectedGroupIds + allGIds)
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SelectAll,
                                    contentDescription = "Pilih Semua",
                                    tint = if (isAllSelected) AccentPrimary else TextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(
                                onClick = { showBatchDeleteGroupsDialog = true },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Hapus Grup",
                                    tint = CrimsonAlert,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                } else {
                    // Sleek, minimal Top Bar with ample statusBarsPadding
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
                        Row(
                            modifier = Modifier.weight(1f, fill = false),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { scope.launch { drawerState.open() } },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Buka Menu",
                                    tint = TextPrimary
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "TRANSCRIBE CORE",
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
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

                        // Clickable AI Engine badge
                        val isBYOKActive = (currentAIProvider != "DEFAULT")
                        Box(
                            modifier = Modifier
                                .background(
                                    if (isBYOKActive) Color(0xFF0E2A3B) else Color(0xFF1E2632),
                                    RoundedCornerShape(4.dp)
                                )
                                .border(
                                    1.dp,
                                    if (isBYOKActive) AccentPrimary else SteelBorder,
                                    RoundedCornerShape(4.dp)
                                )
                                .clickable { showAIEngineDialog = true }
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
                                    text = if (isBYOKActive) "AI: $currentAIProvider" else "AI: CLOUD",
                                    color = if (isBYOKActive) AccentPrimary else TextPrimary,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showCreateDialog = true },
                    containerColor = AccentPrimary,
                    contentColor = OnyxBlack,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Mulai Rapat")
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(modifier = Modifier.height(10.dp))

                // 1. App Update Available Banner (Separated)
                availableUpdate?.let { upd ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F1E2E), RoundedCornerShape(6.dp))
                            .border(1.dp, AccentPrimary, RoundedCornerShape(6.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(AccentPrimary, RoundedCornerShape(3.dp))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Pembaruan v${upd.latestVersionName} Tersedia",
                                color = AccentPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                        TextButton(
                            onClick = { showUpdateDialog = true },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 1.dp)
                        ) {
                            Text("VIEW", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // 2. Active Session Recovery Banner (Separated)
                activeMeeting?.let { ongoing ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF261908), RoundedCornerShape(6.dp))
                            .border(1.dp, AmberWarning, RoundedCornerShape(6.dp))
                            .padding(14.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("ACTIVE RECORDING SESSION", color = AmberWarning, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("• ${ongoing.language.uppercase()}", color = TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = ongoing.title,
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { onNavigateToLive(ongoing.id, ongoing.title) },
                                    colors = ButtonDefaults.buttonColors(containerColor = AmberWarning),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("RESUME", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            meetingRepository.cancelMeeting(ongoing.id)
                                            refreshData()
                                        }
                                    },
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.weight(1f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, CrimsonAlert),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("DISCARD", color = CrimsonAlert, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // 3. SEPARATE SEARCH BAR
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { query ->
                        searchQuery = query
                        searchJob?.cancel()
                        if (query.isNotBlank()) {
                            val q = query.trim().lowercase()
                            // 1. Instant local filter across title, summary, group, language
                            val localFiltered = allMeetings.filter { m ->
                                m.title.lowercase().contains(q) ||
                                (m.summary?.lowercase()?.contains(q) == true) ||
                                (m.groupName?.lowercase()?.contains(q) == true) ||
                                m.language.lowercase().contains(q) ||
                                (m.targetLanguage?.lowercase()?.contains(q) == true)
                            }
                            displayedMeetings = localFiltered

                            // 2. Background server deep full-text search across all transcripts & database
                            isSearching = true
                            searchJob = scope.launch {
                                delay(250)
                                val searchRes = meetingRepository.searchFull(query.trim())
                                searchRes.onSuccess { resp ->
                                    val serverMeetings = resp.meetings
                                    val matchedChunkIds = resp.results.map { it.meetingId }.toSet()
                                    val chunkMatches = allMeetings.filter { it.id in matchedChunkIds }
                                    val merged = (localFiltered + serverMeetings + chunkMatches).distinctBy { it.id }
                                    displayedMeetings = merged
                                    isSearching = false
                                }.onFailure {
                                    isSearching = false
                                }
                            }
                        } else {
                            isSearching = false
                            displayedMeetings = allMeetings
                        }
                    },
                    placeholder = { Text("Search sessions, transcripts, or summaries...", color = TextMuted, fontSize = 13.sp) },
                    leadingIcon = {
                        if (isSearching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = AccentPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Search, contentDescription = null, tint = if (searchQuery.isNotEmpty()) AccentPrimary else TextMuted)
                        }
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = {
                                searchQuery = ""
                                searchJob?.cancel()
                                isSearching = false
                                displayedMeetings = allMeetings
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Hapus Pencarian", tint = TextMuted)
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = AccentPrimary,
                        unfocusedBorderColor = SteelBorder,
                        focusedContainerColor = DarkSlate,
                        unfocusedContainerColor = Color(0xFF0F151E)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 5. HIERARCHICAL VIEW OR ROOT TAB SELECTOR
                if (selectedGroupDetail != null) {
                    val currentGrp = selectedGroupDetail!!
                    // Level 2 Hierarchy: Inside a Group
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, SteelBorder, RoundedCornerShape(8.dp)),
                        colors = CardDefaults.cardColors(containerColor = DarkSlate),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        selectedMeetingIds = emptySet()
                                        selectedGroupDetail = null
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Kembali ke Grup",
                                        tint = TextPrimary
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = currentGrp.name,
                                        color = TextPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val grpTotalMin = (currentGrp.totalDurationSec / 60).toInt()
                                    Text(
                                        text = "${groupMeetings.size} Sesi • Total ${grpTotalMin} Menit",
                                        color = TextSecondary,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Button(
                                onClick = {
                                    preselectedGroupIdForMeeting = currentGrp.id
                                    showCreateDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(36.dp)
                            ) {
                                Text(
                                    text = "+ REKAM DI GRUP INI",
                                    color = OnyxBlack,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (isGroupLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = AccentPrimary)
                        }
                    } else if (groupMeetings.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "Belum ada rekaman di grup ini.\nKetuk [+ REKAM DI GRUP INI] untuk memulai.",
                                color = TextMuted,
                                fontSize = 13.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                lineHeight = 18.sp
                            )
                        }
                    } else {
                        val displayedGroupMeetings = if (searchQuery.isNotBlank()) {
                            val q = searchQuery.trim().lowercase()
                            groupMeetings.filter { m ->
                                m.title.lowercase().contains(q) ||
                                (m.summary?.lowercase()?.contains(q) == true)
                            }
                        } else {
                            groupMeetings
                        }

                        if (displayedGroupMeetings.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(20.dp)) {
                                    Text(
                                        text = "Tidak ditemukan rekaman di grup ini untuk \"$searchQuery\".",
                                        color = TextMuted,
                                        fontSize = 13.sp,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    OutlinedButton(
                                        onClick = {
                                            searchQuery = ""
                                            isSearching = false
                                        },
                                        shape = RoundedCornerShape(4.dp),
                                        border = BorderStroke(1.dp, SteelBorder)
                                    ) {
                                        Text("BERSIHKAN PENCARIAN", color = AccentPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(bottom = 88.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(displayedGroupMeetings, key = { it.id }) { meeting ->
                                    val isSelected = meeting.id in selectedMeetingIds
                                    MeetingCard(
                                        meeting = meeting,
                                        isSelected = isSelected,
                                        isSelectionMode = isMeetingSelectionMode,
                                        onClick = {
                                            if (isMeetingSelectionMode) {
                                                selectedMeetingIds = if (isSelected) selectedMeetingIds - meeting.id else selectedMeetingIds + meeting.id
                                            } else {
                                                onNavigateToMeeting(meeting.id)
                                            }
                                        },
                                        onLongClick = {
                                            selectedMeetingIds = if (isSelected) selectedMeetingIds - meeting.id else selectedMeetingIds + meeting.id
                                        }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Level 1 Hierarchy: Tab Selector (Semua Sesi vs Folder Grup)
                    TabRow(
                        selectedTabIndex = selectedMainTab,
                        containerColor = DarkSlate,
                        contentColor = AccentPrimary,
                        divider = { HorizontalDivider(color = SteelBorder) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Tab(
                            selected = selectedMainTab == 0,
                            onClick = {
                                selectedMainTab = 0
                                selectedGroupIds = emptySet()
                            },
                            text = {
                                Text(
                                    text = "SEMUA SESI (${allMeetings.size})",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1
                                )
                            }
                        )
                        Tab(
                            selected = selectedMainTab == 1,
                            onClick = {
                                selectedMainTab = 1
                                selectedMeetingIds = emptySet()
                            },
                            text = {
                                Text(
                                    text = "GRUP / FOLDER (${groups.size})",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1
                                )
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (selectedMainTab == 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (searchQuery.isNotBlank()) "HASIL PENCARIAN" else "RIWAYAT PERTEMUAN",
                                color = if (searchQuery.isNotBlank()) AccentPrimary else TextMuted,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (searchQuery.isNotBlank()) "${displayedMeetings.size} Hasil" else "${displayedMeetings.size} Sesi",
                                color = if (searchQuery.isNotBlank()) AccentPrimary else TextSecondary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (isLoading) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = AccentPrimary)
                            }
                        } else if (displayedMeetings.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                                    Text(
                                        text = if (searchQuery.isNotBlank()) "Tidak ditemukan sesi untuk \"$searchQuery\"" else "Belum ada rekaman tersimpan.",
                                        color = TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                    if (searchQuery.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "Periksa kata kunci judul rapat, topik grup, ringkasan, atau isi audio.",
                                            color = TextMuted,
                                            fontSize = 12.sp,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(14.dp))
                                        OutlinedButton(
                                            onClick = {
                                                searchQuery = ""
                                                searchJob?.cancel()
                                                isSearching = false
                                                displayedMeetings = allMeetings
                                            },
                                            shape = RoundedCornerShape(4.dp),
                                            border = BorderStroke(1.dp, SteelBorder)
                                        ) {
                                            Text("BERSIHKAN PENCARIAN", color = AccentPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(bottom = 88.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(displayedMeetings, key = { it.id }) { meeting ->
                                    val isSelected = meeting.id in selectedMeetingIds
                                    MeetingCard(
                                        meeting = meeting,
                                        isSelected = isSelected,
                                        isSelectionMode = isMeetingSelectionMode,
                                        onClick = {
                                            if (isMeetingSelectionMode) {
                                                selectedMeetingIds = if (isSelected) selectedMeetingIds - meeting.id else selectedMeetingIds + meeting.id
                                            } else {
                                                onNavigateToMeeting(meeting.id)
                                            }
                                        },
                                        onLongClick = {
                                            selectedMeetingIds = if (isSelected) selectedMeetingIds - meeting.id else selectedMeetingIds + meeting.id
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        // Tab 1: Manajemen Grup
                        val displayedGroups = if (searchQuery.isNotBlank()) {
                            val q = searchQuery.trim().lowercase()
                            groups.filter { g ->
                                g.name.lowercase().contains(q) ||
                                g.description.lowercase().contains(q)
                            }
                        } else {
                            groups
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (searchQuery.isNotBlank()) "HASIL GRUP (${displayedGroups.size})" else "FOLDER & GRUP",
                                color = if (searchQuery.isNotBlank()) AccentPrimary else TextMuted,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )

                            Button(
                                onClick = { showCreateGroupDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                                shape = RoundedCornerShape(4.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text(
                                    text = "+ BUAT GRUP",
                                    color = OnyxBlack,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (displayedGroups.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                                    Text(
                                        text = if (searchQuery.isNotBlank()) "Tidak ditemukan grup untuk \"$searchQuery\"" else "Belum ada grup atau folder.",
                                        color = TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    if (searchQuery.isNotBlank()) {
                                        OutlinedButton(
                                            onClick = {
                                                searchQuery = ""
                                                searchJob?.cancel()
                                                isSearching = false
                                            },
                                            shape = RoundedCornerShape(4.dp),
                                            border = BorderStroke(1.dp, SteelBorder)
                                        ) {
                                            Text("BERSIHKAN PENCARIAN", color = AccentPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    } else {
                                        Button(
                                            onClick = { showCreateGroupDialog = true },
                                            colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text("+ BUAT GRUP PERDANA", color = OnyxBlack, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(bottom = 88.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(displayedGroups, key = { it.id }) { grp ->
                                    val isSelected = grp.id in selectedGroupIds
                                    GroupCard(
                                        group = grp,
                                        isSelected = isSelected,
                                        isSelectionMode = isGroupSelectionMode,
                                        onOpenGroup = {
                                            if (isGroupSelectionMode) {
                                                selectedGroupIds = if (isSelected) selectedGroupIds - grp.id else selectedGroupIds + grp.id
                                            } else {
                                                loadGroupDetail(grp.id)
                                            }
                                        },
                                        onLongClick = {
                                            selectedGroupIds = if (isSelected) selectedGroupIds - grp.id else selectedGroupIds + grp.id
                                        },
                                        onRecordInGroup = {
                                            if (isGroupSelectionMode) {
                                                selectedGroupIds = if (isSelected) selectedGroupIds - grp.id else selectedGroupIds + grp.id
                                            } else {
                                                preselectedGroupIdForMeeting = grp.id
                                                showCreateDialog = true
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerNavRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MeetingCard(
    meeting: MeetingDto,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .border(
                1.dp,
                if (isSelected) AccentPrimary else SteelBorder,
                RoundedCornerShape(6.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF0E2236) else DarkSlate
        ),
        shape = RoundedCornerShape(6.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isSelectionMode) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onClick() },
                            colors = CheckboxDefaults.colors(
                                checkedColor = AccentPrimary,
                                checkmarkColor = OnyxBlack,
                                uncheckedColor = TextMuted
                            ),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    if (!meeting.groupName.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF0E2A3B), RoundedCornerShape(3.dp))
                                .border(1.dp, AccentPrimary.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = meeting.groupName,
                                color = AccentPrimary,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 110.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = meeting.title,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF1E2632), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        val langText = if (!meeting.targetLanguage.isNullOrBlank()) {
                            "${meeting.language.uppercase()} → ${meeting.targetLanguage.uppercase()}"
                        } else {
                            meeting.language.uppercase()
                        }
                        Text(
                            text = langText,
                            color = AccentPrimary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (!meeting.summary.isNullOrBlank()) {
                val cleanPreview = remember(meeting.summary) {
                    cleanExecutiveSummaryText(meeting.summary)
                }
                Text(
                    text = cleanPreview,
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = meeting.createdAt.take(10),
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    softWrap = false
                )
                Spacer(modifier = Modifier.width(8.dp))
                val durSec = meeting.durationSec.toInt()
                val durFormatted = if (durSec >= 60) "${durSec / 60}m ${durSec % 60}s" else "${durSec}s"
                Text(
                    text = "${meeting.status} • $durFormatted",
                    color = if (meeting.status == "COMPLETED") Color(0xFF10B981) else TextMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun parseSafeColor(hex: String?, fallback: Color = AccentPrimary): Color {
    if (hex.isNullOrBlank()) return fallback
    return try {
        val formatted = if (hex.startsWith("#")) hex else "#$hex"
        Color(android.graphics.Color.parseColor(formatted))
    } catch (_: Exception) {
        fallback
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GroupCard(
    group: TranscriptGroupDto,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onOpenGroup: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onRecordInGroup: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onOpenGroup,
                onLongClick = onLongClick
            )
            .border(
                1.dp,
                if (isSelected) AccentPrimary else SteelBorder,
                RoundedCornerShape(8.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF0E2236) else DarkSlate
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (isSelectionMode) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onOpenGroup() },
                            colors = CheckboxDefaults.colors(
                                checkedColor = AccentPrimary,
                                checkmarkColor = OnyxBlack,
                                uncheckedColor = TextMuted
                            ),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                parseSafeColor(group.color),
                                RoundedCornerShape(5.dp)
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = group.name,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (group.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = group.description,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val totalMin = (group.totalDurationSec / 60).toInt()
                Text(
                    text = "${group.meetingCount} Sesi • $totalMin Menit",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                Spacer(modifier = Modifier.width(6.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onRecordInGroup,
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, AccentPrimary),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("+ REKAM", color = AccentPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }

                    Button(
                        onClick = onOpenGroup,
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("BUKA", color = OnyxBlack, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}

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
            Text("BUAT GRUP / FOLDER BARU", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nama Grup / Topik", color = TextMuted, fontSize = 13.sp) },
                    placeholder = { Text("Misal: Kuliah Kriptografi", color = TextMuted.copy(alpha = 0.5f), fontSize = 13.sp) },
                    trailingIcon = {
                        if (name.isNotEmpty()) {
                            IconButton(onClick = { name = "" }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Hapus Nama", tint = TextMuted, modifier = Modifier.size(16.dp))
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
                    label = { Text("Deskripsi Singkat (Opsional)", color = TextMuted, fontSize = 13.sp) },
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

                Text("Label Warna:", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    colors.forEach { hex ->
                        val isSelected = selectedColor == hex
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(Color(android.graphics.Color.parseColor(hex)), RoundedCornerShape(14.dp))
                                .border(
                                    width = if (isSelected) 2.dp else 0.dp,
                                    color = if (isSelected) TextPrimary else Color.Transparent,
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable { selectedColor = hex }
                        )
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
                Text("SIMPAN GRUP", color = OnyxBlack, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                border = BorderStroke(1.dp, SteelBorder),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text("BATAL", color = TextSecondary, fontSize = 12.sp)
            }
        }
    )
}

@Composable
fun CreateMeetingDialog(
    userQuota: UserQuotaDto?,
    isCustomSTT: Boolean = false,
    groups: List<TranscriptGroupDto> = emptyList(),
    initialGroupId: String? = null,
    onDismiss: () -> Unit,
    onUpgradeClicked: () -> Unit,
    onCreate: (String, String, String, String?) -> Unit
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

@Composable
fun SubscriptionDialog(
    currentQuota: UserQuotaDto?,
    plans: List<SubscriptionPlanDto>,
    onDismiss: () -> Unit,
    onRedeem: (code: String, onResult: (error: String?, successMsg: String?) -> Unit) -> Unit
) {
    val idLocale = remember { Locale("id", "ID") }
    val numberFormat = remember { NumberFormat.getNumberInstance(idLocale) }
    var voucherInput by remember { mutableStateOf("") }
    var isRedeeming by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        containerColor = DarkSlate,
        shape = RoundedCornerShape(12.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Aktivasi Voucher Kuota",
                    color = TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Tutup",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // 1. Quota Metric Card
                currentQuota?.let { q ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, SteelBorder, RoundedCornerShape(8.dp)),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F141C)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "STATUS AKUN",
                                    color = TextMuted,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .background(AccentPrimary.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                        .border(1.dp, AccentPrimary, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = q.tier.uppercase(),
                                        color = AccentPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "SISA KUOTA",
                                    color = TextMuted,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${numberFormat.format(q.remainingMinutes)} Menit",
                                    color = TextPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // 2. Voucher Input Section
                Text(
                    text = "KODE VOUCHER",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = voucherInput,
                        onValueChange = { 
                            voucherInput = it
                            errorMessage = null
                            successMessage = null
                        },
                        placeholder = { Text("Contoh: PRO60M", color = TextMuted, fontSize = 13.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = AccentPrimary,
                            unfocusedBorderColor = SteelBorder,
                            focusedContainerColor = Color(0xFF0F141C),
                            unfocusedContainerColor = Color(0xFF0F141C)
                        ),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (voucherInput.isNotBlank()) {
                                isRedeeming = true
                                errorMessage = null
                                successMessage = null
                                onRedeem(voucherInput.trim()) { err, success ->
                                    isRedeeming = false
                                    if (err != null) {
                                        errorMessage = err
                                    } else {
                                        successMessage = success
                                        voucherInput = ""
                                    }
                                }
                            }
                        },
                        enabled = voucherInput.isNotBlank() && !isRedeeming,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentPrimary,
                            disabledContainerColor = SteelBorder,
                            contentColor = OnyxBlack,
                            disabledContentColor = TextMuted
                        ),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        if (isRedeeming) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = OnyxBlack, strokeWidth = 2.dp)
                        } else {
                            Text(
                                text = "KLAIM",
                                color = if (voucherInput.isNotBlank()) OnyxBlack else TextMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }

                // Error / Success Feedback Banner
                errorMessage?.let { err ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF2A0F12), RoundedCornerShape(4.dp))
                            .border(1.dp, CrimsonAlert, RoundedCornerShape(4.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(text = err, color = Color(0xFFFCA5A5), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                }
                successMessage?.let { msg ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0B2416), RoundedCornerShape(4.dp))
                            .border(1.dp, Color(0xFF10B981), RoundedCornerShape(4.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(text = msg, color = Color(0xFF6EE7B7), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
                HorizontalDivider(color = SteelBorder)
                Spacer(modifier = Modifier.height(14.dp))

                // 3. Available Packages (Clean Informational List)
                Text(
                    text = "PAKET KUOTA TERSEDIA",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Hubungi admin untuk memperoleh kode voucher resmi:",
                    color = TextMuted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                plans.forEach { plan ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .border(1.dp, SteelBorder, RoundedCornerShape(6.dp)),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F141C)),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = plan.name,
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                plan.badge?.let { badge ->
                                    Box(
                                        modifier = Modifier
                                            .background(AccentPrimary.copy(alpha = 0.15f), RoundedCornerShape(3.dp))
                                            .border(1.dp, AccentPrimary, RoundedCornerShape(3.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = badge,
                                            color = AccentPrimary,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            softWrap = false,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "Rp ${numberFormat.format(plan.priceIdr)} • +${numberFormat.format(plan.durationMin)} Menit",
                                color = AccentPrimary,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(3.dp))

                            Text(
                                text = plan.description,
                                color = TextMuted,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(6.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, SteelBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                Text(
                    text = "SELESAI",
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
        }
    )
}

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
            Text("PEMBARUAN APLIKASI TERSEDIA", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Versi Terpasang", color = TextMuted, fontSize = 10.sp)
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
                            text = "Terbaru: v${update.latestVersionName}",
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

                Text("Catatan Pembaruan:", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                Text("UNDUH PEMBARUAN", color = OnyxBlack, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            if (!isMandatory) {
                OutlinedButton(
                    onClick = onDismiss,
                    border = BorderStroke(1.dp, SteelBorder),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("NANTI", color = TextSecondary, fontSize = 12.sp)
                }
            }
        }
    )
}

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
                text = "PINDAHKAN KE GRUP ($selectedCount SESI)",
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Pilih grup tujuan untuk memindahkan $selectedCount sesi rapat terpilih:",
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
                        text = "Keluarkan dari Grup (Tanpa Grup)",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                HorizontalDivider(color = SteelBorder, modifier = Modifier.padding(vertical = 8.dp))

                if (groups.isEmpty()) {
                    Text(
                        text = "Belum ada grup yang tersedia. Buat grup terlebih dahulu.",
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
                Text("TERAPKAN", color = OnyxBlack, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                border = BorderStroke(1.dp, SteelBorder),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text("BATAL", color = TextSecondary, fontSize = 12.sp)
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
            val titleText = if (count == 1) "HAPUS 1 SESI TRANSKRIP?" else "HAPUS $count REKAMAN TERPILIH?"
            Text(titleText, color = CrimsonAlert, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            val desc = if (count == 1) {
                "Apakah Anda yakin ingin menghapus sesi transkripsi ini secara permanen? Seluruh rekaman, transkrip, dan ringkasan akan dihapus."
            } else {
                "Apakah Anda yakin ingin menghapus $count rekaman terpilih secara permanen? Seluruh transkrip, potongan audio, dan ringkasan akan dihapus."
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
                Text(if (count == 1) "HAPUS" else "HAPUS SEMUA", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                border = BorderStroke(1.dp, SteelBorder),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text("BATAL", color = TextSecondary, fontSize = 12.sp)
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
            val titleText = if (count == 1) "HAPUS 1 GRUP / FOLDER?" else "HAPUS $count GRUP / FOLDER?"
            Text(titleText, color = CrimsonAlert, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                val headerDesc = if (count == 1) {
                    "Folder grup ini akan dihapus permanen."
                } else {
                    "$count grup terpilih akan dihapus permanen."
                }
                Text(
                    text = headerDesc,
                    color = TextPrimary,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = deleteMeetingsAlso,
                        onCheckedChange = { deleteMeetingsAlso = it },
                        colors = CheckboxDefaults.colors(checkedColor = CrimsonAlert)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (count == 1) "Hapus juga semua isi rekaman di dalam grup ini" else "Hapus juga semua isi rekaman di dalam grup terpilih",
                        color = if (deleteMeetingsAlso) CrimsonAlert else TextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (!deleteMeetingsAlso) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Jika tidak dicentang, seluruh rekaman tetap disimpan di daftar semua sesi.",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(deleteMeetingsAlso) },
                colors = ButtonDefaults.buttonColors(containerColor = CrimsonAlert),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(if (count == 1) "HAPUS GRUP" else "HAPUS $count GRUP", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                border = BorderStroke(1.dp, SteelBorder),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text("BATAL", color = TextSecondary, fontSize = 12.sp)
            }
        }
    )
}

private fun getInstalledVersionInfo(context: Context): Pair<Long, String> {
    return try {
        val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            pInfo.versionCode.toLong()
        }
        val name = pInfo.versionName ?: "1.1.0"
        Pair(code, name)
    } catch (_: Exception) {
        Pair(2L, "1.1.0")
    }
}
