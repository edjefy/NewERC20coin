package com.raybans.claudeassistant.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Handles speech-to-text recognition for voice input
 * Optimized for use with Bluetooth headsets like Meta Ray-Bans
 */
class SpeechRecognitionHandler(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    private val _recognitionState = MutableStateFlow<RecognitionState>(RecognitionState.Idle)
    val recognitionState: StateFlow<RecognitionState> = _recognitionState.asStateFlow()

    private val _partialResults = MutableStateFlow("")
    val partialResults: StateFlow<String> = _partialResults.asStateFlow()

    sealed class RecognitionState {
        object Idle : RecognitionState()
        object Listening : RecognitionState()
        object Processing : RecognitionState()
        data class Result(val text: String) : RecognitionState()
        data class Error(val message: String, val errorCode: Int) : RecognitionState()
    }

    /**
     * Initialize the speech recognizer
     */
    fun initialize(): Boolean {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _recognitionState.value = RecognitionState.Error(
                "Spraakherkenning niet beschikbaar op dit apparaat",
                -1
            )
            return false
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(createRecognitionListener())
        }

        return true
    }

    /**
     * Start listening for speech input
     * Uses Dutch as primary language with English fallback
     */
    fun startListening(useBluetooth: Boolean = true) {
        if (isListening) {
            stopListening()
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "nl-NL")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "nl-NL")
            putExtra(RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES, arrayOf("nl-NL", "en-US"))
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)

            // Request longer speech timeout for conversational input
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1000L)

            // Prefer Bluetooth audio if available (for Ray-Bans)
            if (useBluetooth) {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
            }
        }

        try {
            _recognitionState.value = RecognitionState.Listening
            _partialResults.value = ""
            isListening = true
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            _recognitionState.value = RecognitionState.Error("Kon spraakherkenning niet starten: ${e.message}", -1)
            isListening = false
        }
    }

    /**
     * Stop listening for speech
     */
    fun stopListening() {
        if (isListening) {
            speechRecognizer?.stopListening()
            isListening = false
        }
    }

    /**
     * Cancel current recognition session
     */
    fun cancel() {
        speechRecognizer?.cancel()
        isListening = false
        _recognitionState.value = RecognitionState.Idle
        _partialResults.value = ""
    }

    /**
     * Release resources
     */
    fun destroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        isListening = false
    }

    private fun createRecognitionListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _recognitionState.value = RecognitionState.Listening
        }

        override fun onBeginningOfSpeech() {
            // User started speaking
        }

        override fun onRmsChanged(rmsdB: Float) {
            // Audio level changed - could be used for visual feedback
        }

        override fun onBufferReceived(buffer: ByteArray?) {
            // Audio buffer received
        }

        override fun onEndOfSpeech() {
            _recognitionState.value = RecognitionState.Processing
            isListening = false
        }

        override fun onError(error: Int) {
            isListening = false
            val errorMessage = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio opname fout"
                SpeechRecognizer.ERROR_CLIENT -> "Client fout"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Geen microfoon permissie"
                SpeechRecognizer.ERROR_NETWORK -> "Netwerkfout"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Netwerk timeout"
                SpeechRecognizer.ERROR_NO_MATCH -> "Geen spraak herkend"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Herkenner is bezig"
                SpeechRecognizer.ERROR_SERVER -> "Server fout"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Geen spraak gedetecteerd"
                else -> "Onbekende fout"
            }
            _recognitionState.value = RecognitionState.Error(errorMessage, error)
        }

        override fun onResults(results: Bundle?) {
            isListening = false
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val recognizedText = matches?.firstOrNull() ?: ""

            if (recognizedText.isNotBlank()) {
                _recognitionState.value = RecognitionState.Result(recognizedText)
            } else {
                _recognitionState.value = RecognitionState.Error("Geen spraak herkend", SpeechRecognizer.ERROR_NO_MATCH)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            _partialResults.value = matches?.firstOrNull() ?: ""
        }

        override fun onEvent(eventType: Int, params: Bundle?) {
            // Additional events
        }
    }
}
