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
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import org.sovereign.app.ui.engine.STTEndpointsDialog
import org.sovereign.app.ui.engine.LLMEndpointsDialog
import org.sovereign.app.ui.dashboard.*
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
import org.sovereign.app.network.TranscriptGroupDto
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
    onNavigateToLive: (meetingId: String, title: String, language: String) -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val tokenStorage = remember { EncryptedTokenStorage(context) }
    var currentAIProvider by remember { mutableStateOf(tokenStorage.getAIProvider()) }
    var showAIEngineDialog by remember { mutableStateOf(false) }
    var showSTTDialog by remember { mutableStateOf(false) }
    var showLLMDialog by remember { mutableStateOf(false) }
    var showBackupRestoreDialog by remember { mutableStateOf(false) }
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
    var showUpdateDialog by remember { mutableStateOf(false) }
    var availableUpdate by remember { mutableStateOf<AppVersionDto?>(null) }

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
                Toast.makeText(context, "Failed to load group contents", Toast.LENGTH_SHORT).show()
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

    // New Meeting Dialog
    if (showCreateDialog) {
        CreateMeetingDialog(
            groups = groups,
            initialGroupId = preselectedGroupIdForMeeting,
            onDismiss = {
                showCreateDialog = false
                preselectedGroupIdForMeeting = null
            },
            onCreate = { title, lang, targetLang, groupId ->
                showCreateDialog = false
                preselectedGroupIdForMeeting = null
                scope.launch {
                    val res = meetingRepository.createMeeting(title, lang, targetLang, groupId)
                    res.onSuccess { newMeeting ->
                        onNavigateToLive(newMeeting.id, newMeeting.title, newMeeting.language)
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
                        Toast.makeText(context, "Group \"$name\" created successfully", Toast.LENGTH_SHORT).show()
                        refreshData()
                    }.onFailure {
                        Toast.makeText(context, "Failed to create group: ${it.message}", Toast.LENGTH_SHORT).show()
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
                        Toast.makeText(context, "${ids.size} sessions moved", Toast.LENGTH_SHORT).show()
                        if (selectedGroupDetail != null) {
                            loadGroupDetail(selectedGroupDetail!!.id)
                        }
                        refreshData()
                    }.onFailure {
                        Toast.makeText(context, "Failed to move sessions: ${it.message}", Toast.LENGTH_SHORT).show()
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
                        Toast.makeText(context, "${ids.size} sessions deleted", Toast.LENGTH_SHORT).show()
                        if (selectedGroupDetail != null) {
                            loadGroupDetail(selectedGroupDetail!!.id)
                        }
                        refreshData()
                    }.onFailure {
                        Toast.makeText(context, "Failed to delete sessions: ${it.message}", Toast.LENGTH_SHORT).show()
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
                        Toast.makeText(context, "${ids.size} groups deleted", Toast.LENGTH_SHORT).show()
                        if (selectedGroupDetail != null && selectedGroupDetail!!.id in ids) {
                            selectedGroupDetail = null
                        }
                        refreshData()
                    }.onFailure {
                        Toast.makeText(context, "Failed to delete groups: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    // Rename Single Meeting Dialog
    if (meetingToRename != null) {
        RenameMeetingDialog(
            meeting = meetingToRename!!,
            isRenaming = isRenaming,
            onDismiss = { meetingToRename = null },
            onRename = { trimmed ->
                isRenaming = true
                scope.launch {
                    val res = meetingRepository.updateMeetingTitle(meetingToRename!!.id, trimmed)
                    res.onSuccess {
                        Toast.makeText(context, "Session renamed", Toast.LENGTH_SHORT).show()
                        meetingToRename = null
                        selectedMeetingIds = emptySet()
                        refreshData()
                        if (selectedGroupDetail != null) {
                            loadGroupDetail(selectedGroupDetail!!.id)
                        }
                    }.onFailure {
                        Toast.makeText(context, "Failed to rename: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
                    isRenaming = false
                }
            }
        )
    }

    // Rename Single Group Dialog
    if (groupToRename != null) {
        RenameGroupDialog(
            group = groupToRename!!,
            isRenaming = isRenaming,
            onDismiss = { groupToRename = null },
            onRename = { trimmed ->
                isRenaming = true
                scope.launch {
                    val res = meetingRepository.updateGroup(groupToRename!!.id, trimmed, groupToRename!!.description, groupToRename!!.color)
                    res.onSuccess {
                        Toast.makeText(context, "Group renamed successfully", Toast.LENGTH_SHORT).show()
                        groupToRename = null
                        selectedGroupIds = emptySet()
                        refreshData()
                        if (selectedGroupDetail != null && selectedGroupDetail!!.id == groupToRename!!.id) {
                            selectedGroupDetail = selectedGroupDetail!!.copy(name = trimmed)
                        }
                    }.onFailure {
                        Toast.makeText(context, "Failed to rename group: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
                    isRenaming = false
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

    // Dedicated Voice (STT) Endpoints Setup Dialog
    if (showSTTDialog) {
        STTEndpointsDialog(
            tokenStorage = tokenStorage,
            onDismiss = { showSTTDialog = false },
            onActiveChanged = { newConfig ->
                currentAIProvider = "${newConfig.name} + ${tokenStorage.getLLMProvider()}"
            }
        )
    }

    // Dedicated Reasoning (LLM) Endpoints Setup Dialog
    if (showLLMDialog) {
        LLMEndpointsDialog(
            tokenStorage = tokenStorage,
            onDismiss = { showLLMDialog = false },
            onActiveChanged = { newConfig ->
                currentAIProvider = "${tokenStorage.getSTTProvider()} + ${newConfig.name}"
            }
        )
    }

    // Backup & Restore Dialog
    if (showBackupRestoreDialog) {
        BackupRestoreDialog(
            tokenStorage = tokenStorage,
            onDismiss = { showBackupRestoreDialog = false },
            onDataRestored = {
                refreshData()
                currentAIProvider = tokenStorage.getAIProvider()
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
            DashboardDrawerContent(
                currentVersionName = currentVersionName,
                currentAIProvider = currentAIProvider,
                onCloseDrawer = { scope.launch { drawerState.close() } },
                onOpenAIEngine = {
                    scope.launch { drawerState.close() }
                    showAIEngineDialog = true
                },
                onOpenSTT = {
                    scope.launch { drawerState.close() }
                    showSTTDialog = true
                },
                onOpenLLM = {
                    scope.launch { drawerState.close() }
                    showLLMDialog = true
                },
                onOpenBackupRestore = {
                    scope.launch { drawerState.close() }
                    showBackupRestoreDialog = true
                },
                onOpenInfo = {
                    scope.launch { drawerState.close() }
                    showInfoDialog = true
                },
                onOpenGuide = {
                    scope.launch { drawerState.close() }
                    showGuideDialog = true
                },
                onCheckUpdate = {
                    scope.launch {
                        drawerState.close()
                        val versionRes = meetingRepository.checkAppVersion()
                        versionRes.onSuccess { ver ->
                            if (ver.latestVersionCode > currentVersionCode) {
                                availableUpdate = ver
                                showUpdateDialog = true
                            } else {
                                Toast.makeText(context, "App is up to date (v)", Toast.LENGTH_SHORT).show()
                            }
                        }.onFailure {
                            Toast.makeText(context, "Failed to check for updates", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onSignOut = {
                    scope.launch { drawerState.close() }
                    onLogout()
                }
            )
        }
    ) {
        Scaffold(
            containerColor = OnyxBlack,
            topBar = {
                if (isMeetingSelectionMode && (selectedMainTab == 0 || selectedGroupDetail != null)) {
                    val allVisibleIds = if (selectedGroupDetail != null) {
                        groupMeetings.map { it.id }.toSet()
                    } else {
                        displayedMeetings.map { it.id }.toSet()
                    }
                    val isAllSelected = selectedMeetingIds.containsAll(allVisibleIds) && allVisibleIds.isNotEmpty()
                    val renameTarget = if (selectedMeetingIds.size == 1) {
                        val targetId = selectedMeetingIds.first()
                        displayedMeetings.find { it.id == targetId }
                            ?: groupMeetings.find { it.id == targetId }
                            ?: allMeetings.find { it.id == targetId }
                    } else null

                    MeetingSelectionActionBar(
                        selectedCount = selectedMeetingIds.size,
                        isAllSelected = isAllSelected,
                        onClearSelection = { selectedMeetingIds = emptySet() },
                        onRenameClicked = if (renameTarget != null) { { meetingToRename = renameTarget } } else null,
                        onToggleSelectAll = {
                            selectedMeetingIds = if (isAllSelected) emptySet() else (selectedMeetingIds + allVisibleIds)
                        },
                        onMoveClicked = { showBatchAssignGroupDialog = true },
                        onDeleteClicked = { showBatchDeleteMeetingsDialog = true }
                    )
                } else if (isGroupSelectionMode && selectedMainTab == 1 && selectedGroupDetail == null) {
                    val allGIds = groups.map { it.id }.toSet()
                    val isAllSelected = selectedGroupIds.containsAll(allGIds) && allGIds.isNotEmpty()
                    val renameTarget = if (selectedGroupIds.size == 1) {
                        val targetId = selectedGroupIds.first()
                        groups.find { it.id == targetId }
                    } else null

                    GroupSelectionActionBar(
                        selectedCount = selectedGroupIds.size,
                        isAllSelected = isAllSelected,
                        onClearSelection = { selectedGroupIds = emptySet() },
                        onRenameClicked = if (renameTarget != null) { { groupToRename = renameTarget } } else null,
                        onToggleSelectAll = {
                            selectedGroupIds = if (isAllSelected) emptySet() else (selectedGroupIds + allGIds)
                        },
                        onDeleteClicked = { showBatchDeleteGroupsDialog = true }
                    )
                } else {
                    DashboardTopBar(
                        currentVersionName = currentVersionName,
                        currentAIProvider = currentAIProvider,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onOpenAIEngine = { showAIEngineDialog = true }
                    )
                }
            },
            floatingActionButton = {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .background(
                            androidx.compose.ui.graphics.Brush.horizontalGradient(
                                listOf(AccentPrimary, Color(0xFF0284C7))
                            ),
                            RoundedCornerShape(10.dp)
                        )
                        .border(
                            BorderStroke(
                                1.dp,
                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                    listOf(Color.White.copy(alpha = 0.35f), Color.Transparent)
                                )
                            ),
                            RoundedCornerShape(10.dp)
                        )
                        .clickable { showCreateDialog = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "New Recording",
                        tint = OnyxBlack,
                        modifier = Modifier.size(26.dp)
                    )
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
                                text = "Update v${upd.latestVersionName} Available",
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
                                    onClick = { onNavigateToLive(ongoing.id, ongoing.title, ongoing.language) },
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
                                Icon(Icons.Default.Close, contentDescription = "Clear Search", tint = TextMuted)
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
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back to Groups",
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
                                        text = "${groupMeetings.size} Sessions • Total ${grpTotalMin} Min",
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
                                    text = "+ RECORD IN THIS GROUP",
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
                                text = "No recordings in this group yet.\nTap [+ RECORD IN THIS GROUP] to start.",
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
                                        text = "No recordings in this group for \"$searchQuery\".",
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
                                        Text("CLEAR SEARCH", color = AccentPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                    // Level 1 Hierarchy: Tab Selector (All Sessions vs Folder Grup)
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
                                    text = "ALL SESSIONS (${allMeetings.size})",
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
                                    text = "GROUPS (${groups.size})",
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
                                text = if (searchQuery.isNotBlank()) "SEARCH RESULTS" else "MEETING SESSIONS",
                                color = if (searchQuery.isNotBlank()) AccentPrimary else TextMuted,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (searchQuery.isNotBlank()) "${displayedMeetings.size} Results" else "${displayedMeetings.size} Sessions",
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
                                        text = if (searchQuery.isNotBlank()) "No sessions found for \"$searchQuery\"" else "No recordings saved yet.",
                                        color = TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                    if (searchQuery.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "Check session titles, group topics, summaries, or audio contents.",
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
                                            Text("CLEAR SEARCH", color = AccentPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                                text = if (searchQuery.isNotBlank()) "GROUP RESULTS (${displayedGroups.size})" else "FOLDERS & GROUPS",
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
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text(
                                    text = "+ NEW GROUP",
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
                                        text = if (searchQuery.isNotBlank()) "No groups found for \"$searchQuery\"" else "No groups or folders yet.",
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
                                            Text("CLEAR SEARCH", color = AccentPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    } else {
                                        Button(
                                            onClick = { showCreateGroupDialog = true },
                                            colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text("+ CREATE FIRST GROUP", color = OnyxBlack, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
