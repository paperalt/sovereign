package org.sovereign.app.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import org.sovereign.app.MainActivity
import org.sovereign.app.audio.AudioChunk
import org.sovereign.app.audio.AudioStreamChunker
import org.sovereign.app.audio.WavEncoder
import org.sovereign.app.auth.TokenStorage
import org.sovereign.app.data.LocalMeetingRepository
import org.sovereign.app.network.DirectAIClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

sealed interface StreamEvent {
    data class Status(val status: String, val message: String?) : StreamEvent
    data class Chunk(val index: Int, val startTimeSec: Double, val endTimeSec: Double, val text: String, val remainingSeconds: Int? = null) : StreamEvent
    data class Summary(val summary: String) : StreamEvent
    data class Error(val message: String) : StreamEvent
    data class Quota(val remainingSeconds: Int) : StreamEvent
}

class TranscriptionService : Service() {

    private var serviceJob = SupervisorJob()
    private var serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var audioRecord: AudioRecord? = null

    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private lateinit var localRepo: LocalMeetingRepository
    private lateinit var tokenStorage: TokenStorage
    private val directAIClient = DirectAIClient()

    private var meetingId: String = ""
    private var language: String = "id"
    private var isAdaptive: Boolean = true

    private var recordStartTime = 0L
    private var pauseStartTime = 0L
    private var totalPausedDuration = 0L
    private var isStopping = false
    private var isCancelled = false
    private var isManuallyPaused = false
    private var isSystemFocusPaused = false

    private val chunkIndexCounter = AtomicInteger(0)
    private val activeTranscribeJobs = java.util.concurrent.CopyOnWriteArrayList<Job>()

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (_isRecording.value && !_isPaused.value) {
                    isSystemFocusPaused = true
                    _isPaused.value = true
                    _vadState.value = "PAUSED"
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (isSystemFocusPaused && !isManuallyPaused) {
                    isSystemFocusPaused = false
                    _isPaused.value = false
                    _vadState.value = "ACTIVE"
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        localRepo = LocalMeetingRepository(applicationContext)
        tokenStorage = org.sovereign.app.auth.EncryptedTokenStorage(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY

        when (action) {
            ACTION_START -> {
                meetingId = intent.getStringExtra(EXTRA_MEETING_ID) ?: ""
                val pipelineMode = intent.getStringExtra(EXTRA_PIPELINE_MODE) ?: "adaptive"
                isAdaptive = pipelineMode == "adaptive"
                _isAdaptiveBeta.value = isAdaptive

                val requestedLang = intent.getStringExtra(EXTRA_LANGUAGE) ?: "id"
                language = requestedLang

                startForegroundServiceSafely()
                startLocalRecordingPipeline()
            }
            ACTION_PAUSE -> pauseRecording()
            ACTION_RESUME -> resumeRecording()
            ACTION_STOP -> stopRecording(cancel = false)
            ACTION_CANCEL -> stopRecording(cancel = true)
        }

        return START_NOT_STICKY
    }

    private fun startForegroundServiceSafely() {
        if (serviceJob.isCancelled) {
            serviceJob = SupervisorJob()
            serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
        }

        val notification = createNotification("Recording and transcribing...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        acquireWakeLock()
        requestAudioFocus()
        enableBluetoothAudioRouting()
    }

    private fun startLocalRecordingPipeline() {
        _isRecording.value = true
        _isPaused.value = false
        isStopping = false
        isCancelled = false
        isManuallyPaused = false
        recordStartTime = System.currentTimeMillis()
        totalPausedDuration = 0L
        chunkIndexCounter.set(0)
        activeTranscribeJobs.clear()

        startTimerJob()

        serviceScope.launch {
            _events.emit(StreamEvent.Status("RECORDING", "Local recording active."))
            runAudioLoop()
        }
    }

    private suspend fun runAudioLoop() = withContext(Dispatchers.IO) {
        val sampleRate = 16000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT

        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        // 128ms frame = 2048 samples = 4096 bytes
        val frameBytes = 4096
        val bufferSize = maxOf(minBufferSize, frameBytes * 2)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )
        } catch (_: SecurityException) {
            _events.emit(StreamEvent.Error("Microphone permission not granted."))
            stopSelf()
            return@withContext
        } catch (e: Exception) {
            _events.emit(StreamEvent.Error("Audio initialization failed: ${e.message}"))
            stopSelf()
            return@withContext
        }

        val record = audioRecord
        if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
            _events.emit(StreamEvent.Error("Audio hardware is not ready."))
            stopSelf()
            return@withContext
        }

        try {
            record.startRecording()
        } catch (e: Exception) {
            _events.emit(StreamEvent.Error("Failed to start the audio recorder: ${e.message}"))
            stopSelf()
            return@withContext
        }

        val chunker = AudioStreamChunker(isAdaptive = isAdaptive, sampleRate = sampleRate)
        val audioFrame = ByteArray(frameBytes)

        while (isActive && !isStopping) {
            if (isManuallyPaused) {
                delay(100)
                continue
            }

            val read = record.read(audioFrame, 0, frameBytes)
            if (read < 0) {
                delay(50)
                continue
            }

            if (read > 0) {
                val pcmData = if (read == frameBytes) audioFrame else audioFrame.copyOf(read)
                val (chunk, rms) = chunker.processFrame(pcmData)

                // Update UI live waveforms
                updateAmplitudes(rms)

                // Update VAD energy indicator
                _vadState.value = if (rms >= 280.0) "ACTIVE" else "SAVING"

                if (chunk != null) {
                    dispatchChunkToAI(chunk)
                }
            }
        }

        if (isCancelled) return@withContext

        // Recording loop ended. Flush any remaining audio
        val finalChunk = chunker.flush()
        if (finalChunk != null && !isCancelled) {
            dispatchChunkToAI(finalChunk)
        }

        if (isCancelled) return@withContext

        if (isStopping) {
            _events.emit(StreamEvent.Status("FINALIZING", "Processing final audio & generating executive summary..."))
        }

        // Wait for active transcription calls to finish
        activeTranscribeJobs.forEach { it.join() }

        if (isCancelled) return@withContext

        // Finalize meeting in local database
        if (!isStopping) return@withContext

        finalizeMeetingLocally()
    }

    private fun dispatchChunkToAI(chunk: AudioChunk) {
        val index = chunkIndexCounter.getAndIncrement()
        val wavBytes = WavEncoder.encodePcmToWav(chunk.pcmData)

        val job = serviceScope.launch {
            val provider = tokenStorage.getSelectedPreset().ifBlank { "groq" }
            val sttEndpoint = tokenStorage.getSTTEndpoint()
            val sttModel = tokenStorage.getSTTModel()
            val apiKey = tokenStorage.getSTTKey()

            if (apiKey.isBlank() && !sttEndpoint.contains("localhost") && !sttEndpoint.contains("10.0.2.2")) {
                _events.emit(StreamEvent.Error("API key is missing. Add it in AI Engine settings."))
                return@launch
            }

            val res = directAIClient.transcribeAudio(
                wavBytes = wavBytes,
                language = language,
                provider = provider,
                apiKey = apiKey,
                customEndpoint = sttEndpoint.ifBlank { null },
                customModel = sttModel.ifBlank { null }
            )
            if (res.isSuccess) {
                val text = res.getOrThrow()
                if (text.isNotBlank()) {
                    // 1. Insert into local SQLite
                    localRepo.insertTranscriptChunk(
                        meetingId = meetingId,
                        chunkIndex = index,
                        text = text,
                        startTimeSec = chunk.startTimeSec,
                        endTimeSec = chunk.endTimeSec
                    )

                    // 2. Emit to UI stream
                    _events.emit(
                        StreamEvent.Chunk(
                            index = index,
                            startTimeSec = chunk.startTimeSec,
                            endTimeSec = chunk.endTimeSec,
                            text = text,
                            remainingSeconds = null
                        )
                    )
                }
            } else {
                val err = res.exceptionOrNull()?.message ?: "Audio transcription failed"
                _events.emit(StreamEvent.Error(err))
            }
        }
        activeTranscribeJobs.add(job)
    }

    private suspend fun finalizeMeetingLocally() = withContext(Dispatchers.IO) {
        // 1. Stop meeting status in local SQLite
        localRepo.stopMeeting(meetingId)

        // 2. Generate executive summary locally before signaling completion
        val summaryRes = localRepo.summarizeMeeting(meetingId)
        if (summaryRes.isSuccess) {
            val summary = summaryRes.getOrThrow()
            _events.emit(StreamEvent.Summary(summary.executiveSummary))
        } else {
            val err = summaryRes.exceptionOrNull()?.message
            if (!err.isNullOrBlank()) {
                _events.emit(StreamEvent.Error(err))
            }
        }

        // 3. Signal completed only after database write finishes
        _events.emit(StreamEvent.Status("COMPLETED", "Recording processed."))

        cleanup()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun pauseRecording() {
        if (!_isPaused.value) {
            _isPaused.value = true
            isManuallyPaused = true
            pauseStartTime = System.currentTimeMillis()
            _vadState.value = "PAUSED"
        }
    }

    private fun resumeRecording() {
        if (_isPaused.value) {
            _isPaused.value = false
            isManuallyPaused = false
            totalPausedDuration += System.currentTimeMillis() - pauseStartTime
            _vadState.value = "ACTIVE"
        }
    }

    private fun stopRecording(cancel: Boolean) {
        if (cancel) {
            isCancelled = true
            isStopping = true
            _isRecording.value = false
            _isPaused.value = false
            activeTranscribeJobs.forEach { it.cancel() }
            activeTranscribeJobs.clear()

            serviceScope.launch {
                localRepo.cancelMeeting(meetingId)
                _events.emit(StreamEvent.Status("DISCARDED", "Session discarded."))
                cleanup()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        } else {
            isStopping = true
            _isRecording.value = false
            _isPaused.value = false
            // runAudioLoop will handle flush & finalizeMeetingLocally()
        }
    }

    private fun startTimerJob() {
        serviceScope.launch {
            while (_isRecording.value) {
                if (!_isPaused.value) {
                    val now = System.currentTimeMillis()
                    val elapsedMs = now - recordStartTime - totalPausedDuration
                    val seconds = (elapsedMs / 1000) % 60
                    val minutes = (elapsedMs / (1000 * 60)) % 60
                    val hours = (elapsedMs / (1000 * 60 * 60))
                    _elapsedTime.value = String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
                }
                delay(1000)
            }
        }
    }

    private fun updateAmplitudes(rms: Double) {
        val norm = (rms / 32767.0).toFloat().coerceIn(0.05f, 1.0f)
        val current = _amplitudes.value.clone()
        for (i in 0 until current.size - 1) {
            current[i] = current[i + 1]
        }
        current[current.size - 1] = norm
        _amplitudes.value = current
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "Sovereign::TranscriptionWakeLock"
            )?.apply {
                acquire(4 * 60 * 60 * 1000L) // 4 hours maximum
            }
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
        wakeLock = null
    }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(playbackAttributes)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()

            audioManager?.requestAudioFocus(audioFocusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager?.requestAudioFocus(audioFocusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        }
    }

    private fun releaseAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager?.abandonAudioFocus(audioFocusChangeListener)
        }
    }

    private fun enableBluetoothAudioRouting() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val devices = audioManager?.availableCommunicationDevices ?: emptyList()
                val btDevice = devices.firstOrNull {
                    it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    it.type == android.media.AudioDeviceInfo.TYPE_BLE_HEADSET ||
                    it.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET
                }
                if (btDevice != null) {
                    audioManager?.setCommunicationDevice(btDevice)
                }
            } else {
                @Suppress("DEPRECATION")
                if (audioManager?.isBluetoothScoAvailableOffCall == true) {
                    audioManager?.startBluetoothSco()
                    audioManager?.isBluetoothScoOn = true
                }
            }
        } catch (_: Exception) {}
    }

    private fun disableBluetoothAudioRouting() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager?.clearCommunicationDevice()
            } else {
                @Suppress("DEPRECATION")
                audioManager?.stopBluetoothSco()
                audioManager?.isBluetoothScoOn = false
            }
        } catch (_: Exception) {}
    }

    private fun cleanup() {
        disableBluetoothAudioRouting()
        try {
            audioRecord?.stop()
        } catch (_: Exception) {}
        try {
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null

        releaseWakeLock()
        releaseAudioFocus()
        _isRecording.value = false
        _isPaused.value = false
    }

    private fun createNotification(text: String): Notification {
        val channelId = "sovereign_transcribe_channel"
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Live Transcription", NotificationManager.IMPORTANCE_LOW)
            manager.createNotificationChannel(channel)
        }

        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Sovereign Speech Core")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        cleanup()
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val NOTIFICATION_ID = 4040
        const val ACTION_START = "org.sovereign.app.START"
        const val ACTION_PAUSE = "org.sovereign.app.PAUSE"
        const val ACTION_RESUME = "org.sovereign.app.RESUME"
        const val ACTION_STOP = "org.sovereign.app.STOP"
        const val ACTION_CANCEL = "org.sovereign.app.CANCEL"

        const val EXTRA_MEETING_ID = "extra_meeting_id"
        const val EXTRA_LANGUAGE = "extra_language"
        const val EXTRA_TOKEN = "extra_token"
        const val EXTRA_WS_URL = "extra_ws_url"
        const val EXTRA_BYOK_PROVIDER = "extra_byok_provider"
        const val EXTRA_BYOK_KEY = "extra_byok_key"
        const val EXTRA_STT_PROVIDER = "extra_stt_provider"
        const val EXTRA_STT_KEY = "extra_stt_key"
        const val EXTRA_LLM_PROVIDER = "extra_llm_provider"
        const val EXTRA_LLM_KEY = "extra_llm_key"
        const val EXTRA_PIPELINE_MODE = "extra_pipeline_mode"

        private val _isRecording = MutableStateFlow(false)
        val isRecording = _isRecording.asStateFlow()

        private val _isPaused = MutableStateFlow(false)
        val isPaused = _isPaused.asStateFlow()

        private val _isAdaptiveBeta = MutableStateFlow(false)
        val isAdaptiveBeta = _isAdaptiveBeta.asStateFlow()

        private val _vadState = MutableStateFlow("ACTIVE")
        val vadState = _vadState.asStateFlow()

        private val _elapsedTime = MutableStateFlow("00:00:00")
        val elapsedTime = _elapsedTime.asStateFlow()

        private val _amplitudes = MutableStateFlow(FloatArray(32))
        val amplitudes = _amplitudes.asStateFlow()

        private val _events = MutableSharedFlow<StreamEvent>(extraBufferCapacity = 64)
        val events = _events.asSharedFlow()
    }
}
