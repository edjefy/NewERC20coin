package com.raybans.claudeassistant.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

/**
 * Handles text-to-speech output for Claude responses
 * Routes audio through Bluetooth (Meta Ray-Bans) when available
 */
class TextToSpeechHandler(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    private val _speakingState = MutableStateFlow<SpeakingState>(SpeakingState.Idle)
    val speakingState: StateFlow<SpeakingState> = _speakingState.asStateFlow()

    sealed class SpeakingState {
        object Idle : SpeakingState()
        object Speaking : SpeakingState()
        data class Error(val message: String) : SpeakingState()
    }

    private var onSpeakingComplete: (() -> Unit)? = null

    /**
     * Initialize the TTS engine with Dutch language
     */
    fun initialize(onReady: (Boolean) -> Unit) {
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // Set Dutch as primary language
                val result = tts?.setLanguage(Locale("nl", "NL"))

                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    // Fallback to English
                    tts?.setLanguage(Locale.US)
                }

                // Configure for natural speech
                tts?.setSpeechRate(1.0f)
                tts?.setPitch(1.0f)

                // Set up progress listener
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _speakingState.value = SpeakingState.Speaking
                    }

                    override fun onDone(utteranceId: String?) {
                        releaseAudioFocus()
                        _speakingState.value = SpeakingState.Idle
                        onSpeakingComplete?.invoke()
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        releaseAudioFocus()
                        _speakingState.value = SpeakingState.Error("Spraakfout opgetreden")
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        releaseAudioFocus()
                        _speakingState.value = SpeakingState.Error("Spraakfout: $errorCode")
                    }
                })

                isInitialized = true
                onReady(true)
            } else {
                _speakingState.value = SpeakingState.Error("TTS initialisatie mislukt")
                onReady(false)
            }
        }
    }

    /**
     * Speak the given text
     * Routes through Bluetooth if connected (Ray-Bans)
     */
    fun speak(text: String, onComplete: (() -> Unit)? = null) {
        if (!isInitialized || tts == null) {
            _speakingState.value = SpeakingState.Error("TTS niet geïnitialiseerd")
            return
        }

        onSpeakingComplete = onComplete

        // Request audio focus for proper Bluetooth routing
        requestAudioFocus()

        // Route audio through communication stream for Bluetooth headset
        val params = android.os.Bundle().apply {
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
        }

        val utteranceId = UUID.randomUUID().toString()
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    /**
     * Stop any ongoing speech
     */
    fun stop() {
        tts?.stop()
        releaseAudioFocus()
        _speakingState.value = SpeakingState.Idle
    }

    /**
     * Check if currently speaking
     */
    fun isSpeaking(): Boolean = tts?.isSpeaking == true

    /**
     * Release TTS resources
     */
    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(audioAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener { }
                .build()

            audioManager?.requestAudioFocus(audioFocusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager?.requestAudioFocus(
                null,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
        }
    }

    private fun releaseAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager?.abandonAudioFocus(null)
        }
    }
}
