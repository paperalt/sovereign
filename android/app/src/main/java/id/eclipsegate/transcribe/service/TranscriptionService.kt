package id.eclipsegate.transcribe.service

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
import com.google.gson.Gson
import id.eclipsegate.transcribe.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import okio.ByteString.Companion.toByteString
import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.sqrt

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
    private var webSocket: WebSocket? = null
    private val okHttpClient = OkHttpClient()
    private val gson = Gson()

    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var wakeLock: PowerManager.WakeLock? = null

    // 5 MB Bounded Ring Buffer to prevent OutOfMemory during long network transitions
    private val maxBufferBytes = 5 * 1024 * 1024
    private val memoryRingBuffer = ByteArrayOutputStream()

    // Beta Adaptive Stream batch consolidation buffer & mutex
    private val betaAudioBatchLock = Any()
    private val betaBatchedStream = ByteArrayOutputStream(4096)

    private var recordStartTime = 0L
    private var pauseStartTime = 0L
    private var totalPausedDuration = 0L
    private var isStopping = false
    private var isManuallyPaused = false

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY

        when (action) {
            ACTION_START -> {
                val meetingId = intent.getStringExtra(EXTRA_MEETING_ID) ?: return START_NOT_STICKY
                val token = intent.getStringExtra(EXTRA_TOKEN) ?: return START_NOT_STICKY
                val wsUrl = intent.getStringExtra(EXTRA_WS_URL) ?: return START_NOT_STICKY
                val sttProvider = intent.getStringExtra(EXTRA_STT_PROVIDER) ?: intent.getStringExtra(EXTRA_BYOK_PROVIDER)
                val sttKey = intent.getStringExtra(EXTRA_STT_KEY) ?: intent.getStringExtra(EXTRA_BYOK_KEY)
                val llmProvider = intent.getStringExtra(EXTRA_LLM_PROVIDER)
                val llmKey = intent.getStringExtra(EXTRA_LLM_KEY)
                val pipelineMode = intent.getStringExtra(EXTRA_PIPELINE_MODE) ?: "standard"

                val notification = buildNotification("Merekam dan streaming audio rapat...")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                startStreaming(meetingId, token, wsUrl, sttProvider, sttKey, llmProvider, llmKey, pipelineMode)
            }
            ACTION_PAUSE -> pauseStreaming(isManual = true)
            ACTION_RESUME -> resumeStreaming(isManual = true)
            ACTION_STOP -> stopStreaming()
            ACTION_CANCEL -> cancelStreaming()
        }

        return START_STICKY
    }

    private fun startStreaming(
        meetingId: String,
        token: String,
        rawWsUrl: String,
        sttProvider: String? = null,
        sttKey: String? = null,
        llmProvider: String? = null,
        llmKey: String? = null,
        pipelineMode: String = "standard"
    ) {
        if (_isRecording.value) return

        val isBeta = (pipelineMode == "adaptive_beta")
        _isAdaptiveBeta.value = isBeta
        _vadState.value = "ACTIVE"

        if (!requestAudioFocus()) {
            _events.tryEmit(StreamEvent.Error("Gagal mendapatkan izin audio focus perangkat"))
            stopSelf()
            return
        }

        // Acquire WakeLock to prevent CPU deep sleep while recording in background / screen off
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TranscribeCore:AudioRecordWakeLock")?.apply {
                setReferenceCounted(false)
                acquire(3 * 60 * 60 * 1000L) // 3-hour safety timeout matching stream duration cap
            }
        } catch (_: Exception) {}

        val encToken = URLEncoder.encode(token, "UTF-8")
        val encMeetingId = URLEncoder.encode(meetingId, "UTF-8")
        var url = if (rawWsUrl.contains("?")) {
            "$rawWsUrl&token=$encToken&meeting_id=$encMeetingId"
        } else {
            "$rawWsUrl?token=$encToken&meeting_id=$encMeetingId"
        }
        if (!sttProvider.isNullOrBlank() && !sttKey.isNullOrBlank()) {
            val encSttProv = URLEncoder.encode(sttProvider, "UTF-8")
            val encSttKey = URLEncoder.encode(sttKey, "UTF-8")
            url += "&stt_provider=$encSttProv&stt_key=$encSttKey"
        }
        if (!llmProvider.isNullOrBlank()) {
            val encLlmProv = URLEncoder.encode(llmProvider, "UTF-8")
            url += "&llm_provider=$encLlmProv"
            if (!llmKey.isNullOrBlank()) {
                val encLlmKey = URLEncoder.encode(llmKey, "UTF-8")
                url += "&llm_key=$encLlmKey"
            }
        }
        if (isBeta) {
            url += "&pipeline_mode=adaptive_beta"
        }

        val request = Request.Builder().url(url).build()
        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                // Connected: flush any memory buffered chunks
                synchronized(memoryRingBuffer) {
                    if (memoryRingBuffer.size() > 0) {
                        webSocket.send(memoryRingBuffer.toByteArray().toByteString())
                        memoryRingBuffer.reset()
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleWebSocketMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _events.tryEmit(StreamEvent.Error("Koneksi socket terputus: ${t.message}"))
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _events.tryEmit(StreamEvent.Status("CLOSED", reason))
            }
        })

        // Initialize AudioRecord (16kHz mono 16-bit)
        val sampleRate = 16000
        val minBufSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)

        try {
            val record = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBufSize
            )

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                _events.tryEmit(StreamEvent.Error("Perangkat keras mikrofon tidak siap atau sedang digunakan aplikasi lain"))
                cleanup()
                stopSelf()
                return
            }

            audioRecord = record
            record.startRecording()
            _isRecording.value = true
            recordStartTime = System.currentTimeMillis()
            totalPausedDuration = 0L

            // Timer coroutine
            serviceScope.launch {
                while (_isRecording.value) {
                    if (pauseStartTime == 0L) {
                        val elapsed = System.currentTimeMillis() - recordStartTime - totalPausedDuration
                        val seconds = (elapsed / 1000) % 60
                        val minutes = (elapsed / (1000 * 60)) % 60
                        val hours = elapsed / (1000 * 60 * 60)
                        _elapsedTime.value = String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
                    }
                    delay(500)
                }
            }

            // Audio Record pump loop
            serviceScope.launch {
                val pcmBuffer = ByteArray(2048)
                val preRollQueue = ArrayDeque<ByteArray>()
                var isSpeaking = false
                var silentFrames = 0
                var lastSilenceKeepAlive = System.currentTimeMillis()

                while (_isRecording.value && isActive) {
                    if (pauseStartTime != 0L) {
                        delay(100)
                        continue
                    }

                    val read = audioRecord?.read(pcmBuffer, 0, pcmBuffer.size) ?: -1
                    if (read > 0) {
                        val frame = pcmBuffer.copyOf(read)
                        computeAmplitudeBars(frame)

                        if (!_isAdaptiveBeta.value) {
                            // Standard default workflow (100% UNTOUCHED)
                            dispatchAudioFrame(frame)
                        } else {
                            // Beta Adaptive Mode: Client VAD Gating + Frame Compaction + Fast Latency
                            val rms = calculateFrameRMS(frame)
                            val voiceDetected = rms >= 280.0

                            if (voiceDetected) {
                                silentFrames = 0
                                if (!isSpeaking) {
                                    isSpeaking = true
                                    _vadState.value = "SPEAKING"
                                    // Flush pre-roll buffer to preserve opening phonemes/syllables
                                    synchronized(betaAudioBatchLock) {
                                        while (preRollQueue.isNotEmpty()) {
                                            betaBatchedStream.write(preRollQueue.removeFirst())
                                        }
                                    }
                                }
                                synchronized(betaAudioBatchLock) {
                                    betaBatchedStream.write(frame)
                                    if (betaBatchedStream.size() >= 4096) {
                                        dispatchAudioFrame(betaBatchedStream.toByteArray())
                                        betaBatchedStream.reset()
                                    }
                                }
                            } else {
                                silentFrames++
                                if (preRollQueue.size >= 4) {
                                    preRollQueue.removeFirst()
                                }
                                preRollQueue.add(frame)

                                if (silentFrames > 6) {
                                    // Silence Suppression: Suppress transmission to save ~80% bandwidth
                                    if (isSpeaking) {
                                        isSpeaking = false
                                        _vadState.value = "SUPPRESSED"
                                        synchronized(betaAudioBatchLock) {
                                            if (betaBatchedStream.size() > 0) {
                                                dispatchAudioFrame(betaBatchedStream.toByteArray())
                                                betaBatchedStream.reset()
                                            }
                                        }
                                    }
                                    // Periodic keepalive every 4 seconds of silence
                                    val now = System.currentTimeMillis()
                                    if (now - lastSilenceKeepAlive > 4000L) {
                                        webSocket?.send("""{"action": "VAD_SILENCE"}""")
                                        lastSilenceKeepAlive = now
                                    }
                                } else {
                                    // Short trailing pause before cutoff: continue buffering
                                    synchronized(betaAudioBatchLock) {
                                        betaBatchedStream.write(frame)
                                        if (betaBatchedStream.size() >= 4096) {
                                            dispatchAudioFrame(betaBatchedStream.toByteArray())
                                            betaBatchedStream.reset()
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Prevent 100% CPU thread burn on AudioRecord error code or read glitch
                        delay(50)
                    }
                }
            }
        } catch (e: SecurityException) {
            _events.tryEmit(StreamEvent.Error("Izin mikrofon ditolak"))
            cleanup()
            stopSelf()
        } catch (e: Exception) {
            _events.tryEmit(StreamEvent.Error("Gagal memulai perekaman audio: ${e.message}"))
            cleanup()
            stopSelf()
        }
    }

    private fun dispatchAudioFrame(data: ByteArray) {
        val ws = webSocket
        if (ws != null) {
            ws.send(data.toByteString())
        } else {
            synchronized(memoryRingBuffer) {
                if (memoryRingBuffer.size() + data.size <= maxBufferBytes) {
                    memoryRingBuffer.write(data)
                }
            }
        }
    }

    private fun computeAmplitudeBars(pcm: ByteArray) {
        // Fast RMS calculation for 32 visualization bars
        val samples = pcm.size / 2
        if (samples == 0) return

        var sum = 0.0
        for (i in 0 until pcm.size - 1 step 2) {
            val sample = (pcm[i].toInt() and 0xFF) or (pcm[i + 1].toInt() shl 8)
            val s = sample.toShort()
            sum += s * s
        }
        val rms = sqrt(sum / samples).toFloat()
        val normalized = (rms / 32767f).coerceIn(0.05f, 1.0f)

        val currentBars = _amplitudes.value.copyOf()
        for (i in 0 until currentBars.size - 1) {
            currentBars[i] = currentBars[i + 1]
        }
        currentBars[currentBars.size - 1] = normalized
        _amplitudes.value = currentBars
    }

    private fun calculateFrameRMS(pcm: ByteArray): Double {
        val samples = pcm.size / 2
        if (samples == 0) return 0.0
        var sum = 0.0
        for (i in 0 until pcm.size - 1 step 2) {
            val sample = (pcm[i].toInt() and 0xFF) or (pcm[i + 1].toInt() shl 8)
            val s = sample.toShort()
            sum += s * s
        }
        return sqrt(sum / samples)
    }

    private fun handleWebSocketMessage(jsonStr: String) {
        try {
            val map = gson.fromJson(jsonStr, Map::class.java)
            val event = map["event"] as? String ?: return

            when (event) {
                "CHUNK_TRANSCRIBED" -> {
                    val idx = (map["chunk_index"] as? Number)?.toInt() ?: 0
                    val start = (map["start_time_sec"] as? Number)?.toDouble() ?: 0.0
                    val end = (map["end_time_sec"] as? Number)?.toDouble() ?: 0.0
                    val text = map["text"] as? String ?: ""
                    val rem = (map["remaining_seconds"] as? Number)?.toInt()
                    _events.tryEmit(StreamEvent.Chunk(idx, start, end, text, rem))
                }
                "QUOTA_UPDATED" -> {
                    val rem = (map["remaining_seconds"] as? Number)?.toInt() ?: 0
                    _events.tryEmit(StreamEvent.Quota(rem))
                }
                "SUMMARY_GENERATED" -> {
                    val summary = map["summary"] as? String ?: ""
                    _events.tryEmit(StreamEvent.Summary(summary))
                }
                "STATUS" -> {
                    val status = map["status"] as? String ?: ""
                    val message = map["message"] as? String
                    _events.tryEmit(StreamEvent.Status(status, message))
                    if (status == "COMPLETED" || status == "CANCELLED") {
                        serviceScope.launch {
                            delay(500)
                            cleanup()
                            stopSelf()
                        }
                    }
                }
                "PIPELINE_MODE" -> {
                    val status = map["status"] as? String ?: ""
                    val message = map["message"] as? String
                    _events.tryEmit(StreamEvent.Status(status, message))
                }
                "ERROR" -> {
                    val message = map["message"] as? String ?: "Terjadi kesalahan"
                    _events.tryEmit(StreamEvent.Error(message))
                }
            }
        } catch (_: Exception) {}
    }

    private fun pauseStreaming(isManual: Boolean = false) {
        if (_isRecording.value && !_isPaused.value) {
            if (isManual) {
                isManuallyPaused = true
            }
            _isPaused.value = true
            pauseStartTime = System.currentTimeMillis()
            // 1. Flush any pending beta batched audio
            synchronized(betaAudioBatchLock) {
                if (betaBatchedStream.size() > 0) {
                    dispatchAudioFrame(betaBatchedStream.toByteArray())
                    betaBatchedStream.reset()
                }
            }
            // 2. Tell backend to flush whatever audio was in-flight
            webSocket?.send("""{"action": "PAUSE"}""")
            // 3. Clear amplitude visualizer to baseline
            _amplitudes.value = FloatArray(32)
            // 4. Update notification
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID, buildNotification("Perekaman dijeda"))
            // 5. Emit status event
            _events.tryEmit(StreamEvent.Status("PAUSED", "Perekaman dijeda"))
        }
    }

    private fun resumeStreaming(isManual: Boolean = false) {
        if (_isRecording.value && _isPaused.value) {
            if (isManual) {
                isManuallyPaused = false
            }
            if (pauseStartTime != 0L) {
                totalPausedDuration += System.currentTimeMillis() - pauseStartTime
                pauseStartTime = 0L
            }
            _isPaused.value = false
            // 1. Tell backend we are resuming
            webSocket?.send("""{"action": "RESUME"}""")
            // 2. Update notification
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID, buildNotification("Merekam dan streaming audio rapat..."))
            // 3. Emit status event
            _events.tryEmit(StreamEvent.Status("STREAMING", "Perekaman dilanjutkan"))
        }
    }

    private fun stopStreaming() {
        if (isStopping) return
        isStopping = true

        // 1. Stop audio recording hardware immediately
        _isRecording.value = false
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
        abandonAudioFocus()

        // 2. Flush any pending beta batched audio before issuing STOP command
        synchronized(betaAudioBatchLock) {
            if (betaBatchedStream.size() > 0) {
                dispatchAudioFrame(betaBatchedStream.toByteArray())
                betaBatchedStream.reset()
            }
        }

        // 3. Notify UI that finalizing is in progress
        _events.tryEmit(StreamEvent.Status("FINALIZING", "Memproses audio akhir dan ringkasan AI..."))

        // 4. Send STOP command to backend so backend flushes chunk & runs summarizer
        webSocket?.send("""{"action": "STOP"}""")

        // 4. Fallback timeout: if server doesn't respond within 15 seconds, cleanup & stop
        serviceScope.launch {
            delay(15000)
            if (isStopping) {
                cleanup()
                stopSelf()
            }
        }
    }

    private fun cancelStreaming() {
        // Send CANCEL action frame to discard meeting
        webSocket?.send("""{"action": "CANCEL"}""")
        cleanup()
        stopSelf()
    }

    private fun cleanup() {
        _isRecording.value = false
        _isPaused.value = false
        _isAdaptiveBeta.value = false
        _vadState.value = "ACTIVE"
        synchronized(betaAudioBatchLock) {
            betaBatchedStream.reset()
        }
        pauseStartTime = 0L
        isStopping = false
        isManuallyPaused = false
        _amplitudes.value = FloatArray(32)
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null

        webSocket?.close(1000, "Normal Closure")
        webSocket = null

        abandonAudioFocus()
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
        wakeLock = null

        // Cancel running session coroutines and prepare fresh scope for next potential session
        serviceJob.cancel()
        serviceJob = SupervisorJob()
        serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    }

    private fun requestAudioFocus(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val focusReq = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setOnAudioFocusChangeListener { focusChange ->
                    if (focusChange == AudioManager.AUDIOFOCUS_LOSS || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                        if (!_isPaused.value) {
                            pauseStreaming(isManual = false)
                        }
                    } else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
                        if (!isManuallyPaused) {
                            resumeStreaming(isManual = false)
                        }
                    }
                }
                .build()
            audioFocusRequest = focusReq
            return audioManager?.requestAudioFocus(focusReq) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
        return true
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        }
    }

    private fun buildNotification(text: String): Notification {
        val channelId = "transcribe_channel"
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Transkripsi Langsung", NotificationManager.IMPORTANCE_LOW)
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
            .setContentTitle("Transcribe Core")
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
        const val ACTION_START = "id.eclipsegate.transcribe.START"
        const val ACTION_PAUSE = "id.eclipsegate.transcribe.PAUSE"
        const val ACTION_RESUME = "id.eclipsegate.transcribe.RESUME"
        const val ACTION_STOP = "id.eclipsegate.transcribe.STOP"
        const val ACTION_CANCEL = "id.eclipsegate.transcribe.CANCEL"

        const val EXTRA_MEETING_ID = "extra_meeting_id"
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
