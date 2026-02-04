package com.raybans.claudeassistant.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import com.raybans.claudeassistant.ClaudeAssistantApp
import com.raybans.claudeassistant.MainActivity
import com.raybans.claudeassistant.R
import com.raybans.claudeassistant.api.ClaudeApiClient
import com.raybans.claudeassistant.audio.BluetoothAudioHandler
import com.raybans.claudeassistant.audio.SpeechRecognitionHandler
import com.raybans.claudeassistant.audio.TextToSpeechHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Foreground service that handles voice assistant functionality
 * Listens for media button events from Meta Ray-Bans for handsfree activation
 */
class VoiceAssistantService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var speechRecognizer: SpeechRecognitionHandler? = null
    private var ttsHandler: TextToSpeechHandler? = null
    private var bluetoothHandler: BluetoothAudioHandler? = null
    private var claudeClient: ClaudeApiClient? = null
    private var mediaSession: MediaSession? = null

    private val _assistantState = MutableStateFlow<AssistantState>(AssistantState.Idle)
    val assistantState: StateFlow<AssistantState> = _assistantState.asStateFlow()

    private val _lastResponse = MutableStateFlow("")
    val lastResponse: StateFlow<String> = _lastResponse.asStateFlow()

    sealed class AssistantState {
        object Idle : AssistantState()
        object Listening : AssistantState()
        object Processing : AssistantState()
        object Speaking : AssistantState()
        data class Error(val message: String) : AssistantState()
    }

    inner class LocalBinder : Binder() {
        fun getService(): VoiceAssistantService = this@VoiceAssistantService
    }

    // Media button receiver for Ray-Bans handsfree activation
    private val mediaButtonReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_MEDIA_BUTTON) {
                val keyEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
                }

                keyEvent?.let { handleMediaButton(it) }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        initializeComponents()
        setupMediaSession()
        registerMediaButtonReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())

        // Handle media button intents
        intent?.let {
            if (it.action == Intent.ACTION_MEDIA_BUTTON) {
                val keyEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    it.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    it.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
                }
                keyEvent?.let { event -> handleMediaButton(event) }
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        cleanup()
    }

    private fun initializeComponents() {
        // Initialize Bluetooth handler
        bluetoothHandler = BluetoothAudioHandler(this).apply {
            initialize()
        }

        // Initialize speech recognition
        speechRecognizer = SpeechRecognitionHandler(this).apply {
            initialize()
        }

        // Initialize TTS
        ttsHandler = TextToSpeechHandler(this).apply {
            initialize { success ->
                if (success) {
                    speak("Claude assistent gereed. Tik op je Ray-Bans of druk op de knop om te praten.")
                }
            }
        }

        // Observe speech recognition state
        serviceScope.launch {
            speechRecognizer?.recognitionState?.collect { state ->
                when (state) {
                    is SpeechRecognitionHandler.RecognitionState.Result -> {
                        handleUserSpeech(state.text)
                    }
                    is SpeechRecognitionHandler.RecognitionState.Error -> {
                        if (state.errorCode != android.speech.SpeechRecognizer.ERROR_NO_MATCH) {
                            _assistantState.value = AssistantState.Error(state.message)
                        } else {
                            _assistantState.value = AssistantState.Idle
                        }
                    }
                    is SpeechRecognitionHandler.RecognitionState.Listening -> {
                        _assistantState.value = AssistantState.Listening
                    }
                    else -> {}
                }
            }
        }
    }

    private fun setupMediaSession() {
        mediaSession = MediaSession(this, "ClaudeAssistant").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                    val keyEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
                    }

                    keyEvent?.let {
                        handleMediaButton(it)
                        return true
                    }
                    return super.onMediaButtonEvent(mediaButtonIntent)
                }

                override fun onPlay() {
                    toggleListening()
                }

                override fun onPause() {
                    stopListening()
                }
            })

            // Set playback state to receive media button events
            setPlaybackState(
                PlaybackState.Builder()
                    .setActions(
                        PlaybackState.ACTION_PLAY or
                        PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_PLAY_PAUSE
                    )
                    .setState(PlaybackState.STATE_PAUSED, 0, 1.0f)
                    .build()
            )

            isActive = true
        }
    }

    private fun registerMediaButtonReceiver() {
        val filter = IntentFilter(Intent.ACTION_MEDIA_BUTTON).apply {
            priority = IntentFilter.SYSTEM_HIGH_PRIORITY
        }
        registerReceiver(mediaButtonReceiver, filter)
    }

    /**
     * Handle media button events from Ray-Bans
     * Single tap: toggle listening
     * Double tap: repeat last response
     * Long press: clear conversation
     */
    private fun handleMediaButton(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false

        when (event.keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_HEADSETHOOK -> {
                // Single tap - toggle listening
                if (event.repeatCount == 0) {
                    toggleListening()
                }
                return true
            }
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                // Skip forward - repeat last response
                repeatLastResponse()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                // Skip back - clear conversation
                clearConversation()
                return true
            }
        }
        return false
    }

    /**
     * Toggle voice listening on/off
     */
    fun toggleListening() {
        when (_assistantState.value) {
            is AssistantState.Idle, is AssistantState.Error -> {
                startListening()
            }
            is AssistantState.Listening -> {
                stopListening()
            }
            is AssistantState.Speaking -> {
                ttsHandler?.stop()
                startListening()
            }
            else -> {}
        }
    }

    /**
     * Start listening for voice input
     */
    fun startListening() {
        ttsHandler?.stop()
        bluetoothHandler?.startBluetoothSco()
        _assistantState.value = AssistantState.Listening

        // Play a short beep/feedback
        serviceScope.launch {
            kotlinx.coroutines.delay(200)
            speechRecognizer?.startListening(
                useBluetooth = bluetoothHandler?.isBluetoothAudioAvailable() == true
            )
        }
    }

    /**
     * Stop listening
     */
    fun stopListening() {
        speechRecognizer?.stopListening()
        bluetoothHandler?.stopBluetoothSco()
        _assistantState.value = AssistantState.Idle
    }

    /**
     * Handle recognized speech and send to Claude
     */
    private fun handleUserSpeech(text: String) {
        _assistantState.value = AssistantState.Processing

        serviceScope.launch {
            val client = claudeClient ?: run {
                _assistantState.value = AssistantState.Error("API key niet geconfigureerd")
                speak("Je moet eerst je API key instellen in de app.")
                return@launch
            }

            val result = client.sendMessage(text)

            result.fold(
                onSuccess = { response ->
                    _lastResponse.value = response
                    speak(response)
                },
                onFailure = { error ->
                    _assistantState.value = AssistantState.Error(error.message ?: "Onbekende fout")
                    speak("Er is een fout opgetreden: ${error.message}")
                }
            )
        }
    }

    /**
     * Speak text using TTS
     */
    fun speak(text: String) {
        _assistantState.value = AssistantState.Speaking
        ttsHandler?.speak(text) {
            _assistantState.value = AssistantState.Idle
        }
    }

    /**
     * Repeat the last Claude response
     */
    fun repeatLastResponse() {
        val lastResponse = _lastResponse.value
        if (lastResponse.isNotBlank()) {
            speak(lastResponse)
        } else {
            speak("Er is nog geen vorige reactie om te herhalen.")
        }
    }

    /**
     * Clear conversation history
     */
    fun clearConversation() {
        claudeClient?.clearHistory()
        _lastResponse.value = ""
        speak("Conversatie gewist. We beginnen opnieuw.")
    }

    /**
     * Set the Claude API key
     */
    fun setApiKey(apiKey: String) {
        claudeClient = ClaudeApiClient(apiKey)
    }

    /**
     * Check if API key is configured
     */
    fun isApiKeyConfigured(): Boolean = claudeClient != null

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, ClaudeAssistantApp.CHANNEL_ID)
            .setContentTitle("Claude Assistant")
            .setContentText("Tik op je Ray-Bans om te praten")
            .setSmallIcon(R.drawable.ic_assistant)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun cleanup() {
        serviceScope.cancel()
        try {
            unregisterReceiver(mediaButtonReceiver)
        } catch (e: Exception) {}
        mediaSession?.release()
        speechRecognizer?.destroy()
        ttsHandler?.shutdown()
        bluetoothHandler?.destroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
    }
}
