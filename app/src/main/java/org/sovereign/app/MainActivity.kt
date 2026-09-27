package org.sovereign.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.sovereign.app.auth.AuthIntent
import org.sovereign.app.auth.AuthViewModel
import org.sovereign.app.auth.LoginScreen
import org.sovereign.app.service.StreamEvent
import org.sovereign.app.service.TranscriptionService
import org.sovereign.app.ui.*
import org.sovereign.app.ui.navigation.Screen
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Permissions handled
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestRequiredPermissions()

        val app = SovereignApp.instance

        setContent {
            MaterialTheme {
                val navController = rememberNavController()
                val scope = rememberCoroutineScope()
                val isLoggedIn by app.authRepository.isLoggedIn.collectAsState()

                val startDestination = Screen.Dashboard.route

                Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0D12))) {
                    NavHost(
                        navController = navController,
                        startDestination = startDestination
                    ) {
                        // 1. Login Screen
                        composable(Screen.Login.route) {
                            val authViewModel = viewModel { AuthViewModel(app.authRepository) }
                            LoginScreen(
                                viewModel = authViewModel,
                                onGoogleSignInClicked = {
                                    scope.launch {
                                        val res = app.googleAuthManager.signIn()
                                        res.onSuccess { idToken ->
                                            authViewModel.processIntent(AuthIntent.SignInWithGoogle(idToken))
                                        }.onFailure { err ->
                                            authViewModel.processIntent(
                                                AuthIntent.SetError(err.message ?: "Google authentication failed")
                                            )
                                        }
                                    }
                                },
                                onAuthSuccess = {
                                    navController.navigate(Screen.Dashboard.route) {
                                        popUpTo(Screen.Login.route) { inclusive = true }
                                    }
                                }
                            )
                        }

                        // 2. Dashboard Screen
                        composable(Screen.Dashboard.route) {
                            DashboardScreen(
                                meetingRepository = app.meetingRepository,
                                onNavigateToMeeting = { meetingId ->
                                    navController.navigate(Screen.MeetingDetail.createRoute(meetingId))
                                },
                                onNavigateToLive = { meetingId, title, language ->
                                    // Start Foreground Service
                                    val token = app.tokenStorage.getAccessToken() ?: ""
                                    val currentBase = app.tokenStorage.getServerUrl()
                                    val wsBase = if (currentBase.startsWith("https://")) {
                                        currentBase.replaceFirst("https://", "wss://")
                                    } else {
                                        currentBase.replaceFirst("http://", "ws://")
                                    }
                                    val sttProvider = app.tokenStorage.getSTTProvider()
                                    val sttKey = if (sttProvider != "DEFAULT") app.tokenStorage.getProviderApiKey(sttProvider) else null
                                    val llmProvider = app.tokenStorage.getLLMProvider()
                                    val llmKey = if (llmProvider != "DEFAULT") app.tokenStorage.getProviderApiKey(llmProvider) else null

                                    val intent = Intent(this@MainActivity, TranscriptionService::class.java).apply {
                                        action = TranscriptionService.ACTION_START
                                        putExtra(TranscriptionService.EXTRA_MEETING_ID, meetingId)
                                        putExtra(TranscriptionService.EXTRA_LANGUAGE, language)
                                        putExtra(TranscriptionService.EXTRA_TOKEN, token)
                                        putExtra(TranscriptionService.EXTRA_WS_URL, "$wsBase/ws/transcribe")
                                        if (sttProvider != "DEFAULT" && !sttKey.isNullOrBlank()) {
                                            putExtra(TranscriptionService.EXTRA_STT_PROVIDER, sttProvider)
                                            putExtra(TranscriptionService.EXTRA_STT_KEY, sttKey)
                                            putExtra(TranscriptionService.EXTRA_BYOK_PROVIDER, sttProvider)
                                            putExtra(TranscriptionService.EXTRA_BYOK_KEY, sttKey)
                                        }
                                        if (llmProvider != "DEFAULT" && !llmKey.isNullOrBlank()) {
                                            putExtra(TranscriptionService.EXTRA_LLM_PROVIDER, llmProvider)
                                            putExtra(TranscriptionService.EXTRA_LLM_KEY, llmKey)
                                        }
                                        if (app.tokenStorage.isAdaptiveStreamingBetaEnabled()) {
                                            putExtra(TranscriptionService.EXTRA_PIPELINE_MODE, "adaptive_beta")
                                        }
                                    }
                                    ContextCompat.startForegroundService(this@MainActivity, intent)

                                    navController.navigate(Screen.LiveTranscription.createRoute(meetingId, title))
                                },
                                onLogout = {
                                    Toast.makeText(this@MainActivity, "Running locally on this device", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }

                        // 3. Live Transcription Screen
                        composable(
                            route = Screen.LiveTranscription.route,
                            arguments = listOf(
                                navArgument("meetingId") { type = NavType.StringType },
                                navArgument("title") { type = NavType.StringType }
                            )
                        ) { backStackEntry ->
                            val meetingId = backStackEntry.arguments?.getString("meetingId") ?: ""
                            val meetingTitle = backStackEntry.arguments?.getString("title") ?: "Meeting Session"

                            val isRecording by TranscriptionService.isRecording.collectAsState()
                            val isPaused by TranscriptionService.isPaused.collectAsState()
                            val elapsedTime by TranscriptionService.elapsedTime.collectAsState()
                            val amplitudes by TranscriptionService.amplitudes.collectAsState()
                            var isFinalizing by remember { mutableStateOf(false) }
                            var remainingQuotaSec by remember { mutableStateOf<Int?>(null) }

                            val transcriptItems = remember { mutableStateListOf<TranscriptItem>() }

                            // Preload existing chunks if resuming an active meeting
                            LaunchedEffect(meetingId) {
                                if (meetingId.isNotBlank()) {
                                    val res = app.meetingRepository.getTranscript(meetingId)
                                    res.onSuccess { data ->
                                        if (transcriptItems.isEmpty() && data.chunks.isNotEmpty()) {
                                            val items = data.chunks.map { chunk ->
                                                val minutes = (chunk.startTimeSec / 60).toInt()
                                                val seconds = (chunk.startTimeSec % 60).toInt()
                                                val timeLabel = String.format(Locale.US, "%02d:%02d", minutes, seconds)
                                                TranscriptItem(chunk.chunkIndex, timeLabel, chunk.rawText)
                                            }
                                            transcriptItems.addAll(items)
                                        }
                                    }
                                }
                            }

                            // Collect events from Background Service
                            LaunchedEffect(Unit) {
                                TranscriptionService.events.collect { event ->
                                    when (event) {
                                        is StreamEvent.Chunk -> {
                                            val minutes = (event.startTimeSec / 60).toInt()
                                            val seconds = (event.startTimeSec % 60).toInt()
                                            val timeLabel = String.format(Locale.US, "%02d:%02d", minutes, seconds)
                                            if (transcriptItems.none { it.index == event.index }) {
                                                transcriptItems.add(TranscriptItem(event.index, timeLabel, event.text))
                                            }
                                            event.remainingSeconds?.let { remainingQuotaSec = it }
                                        }
                                        is StreamEvent.Quota -> {
                                            remainingQuotaSec = event.remainingSeconds
                                        }
                                        is StreamEvent.Status -> {
                                            if (event.status == "FINALIZING") {
                                                isFinalizing = true
                                            } else if (event.status == "COMPLETED") {
                                                isFinalizing = false
                                                navController.navigate(Screen.MeetingDetail.createRoute(meetingId)) {
                                                    popUpTo(Screen.Dashboard.route)
                                                }
                                            } else if (event.status == "CANCELLED" || event.status == "DISCARDED") {
                                                isFinalizing = false
                                                navController.navigate(Screen.Dashboard.route) {
                                                    popUpTo(Screen.Dashboard.route) { inclusive = true }
                                                }
                                            }
                                        }
                                        is StreamEvent.Error -> {
                                            isFinalizing = false
                                            Toast.makeText(this@MainActivity, event.message, Toast.LENGTH_LONG).show()
                                        }
                                        else -> {}
                                    }
                                }
                            }

                            LiveTranscriptionScreen(
                                meetingId = meetingId,
                                meetingRepository = app.meetingRepository,
                                meetingTitle = meetingTitle,
                                elapsedTime = elapsedTime,
                                transcriptItems = transcriptItems,
                                isRecording = isRecording,
                                isPaused = isPaused,
                                isFinalizing = isFinalizing,
                                remainingQuotaSeconds = remainingQuotaSec,
                                aiProvider = app.tokenStorage.getAIProvider(),
                                amplitudeSupplier = { amplitudes },
                                onStopClicked = {
                                    val intent = Intent(this@MainActivity, TranscriptionService::class.java).apply {
                                        action = TranscriptionService.ACTION_STOP
                                    }
                                    startService(intent)
                                },
                                onPauseClicked = {
                                    val intent = Intent(this@MainActivity, TranscriptionService::class.java).apply {
                                        action = if (isPaused) TranscriptionService.ACTION_RESUME else TranscriptionService.ACTION_PAUSE
                                    }
                                    startService(intent)
                                },
                                onCancelConfirmed = {
                                    val intent = Intent(this@MainActivity, TranscriptionService::class.java).apply {
                                        action = TranscriptionService.ACTION_CANCEL
                                    }
                                    startService(intent)
                                }
                            )
                        }

                        // 4. Meeting Detail Screen
                        composable(
                            route = Screen.MeetingDetail.route,
                            arguments = listOf(navArgument("meetingId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val meetingId = backStackEntry.arguments?.getString("meetingId") ?: ""
                            MeetingDetailScreen(
                                meetingId = meetingId,
                                meetingRepository = app.meetingRepository,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }
}
