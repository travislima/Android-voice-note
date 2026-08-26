package com.travislima.voicenote

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Continuous dictation on top of Android's on-device SpeechRecognizer.
 * No account sign-in is required. The recogniser stops itself on silence,
 * so this engine restarts it automatically until [stop] is called.
 */
class SpeechEngine(
    private val context: Context,
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onStatus: (String) -> Unit,
) {
    private var recognizer: SpeechRecognizer? = null
    private var listening = false

    val isListening: Boolean get() = listening

    fun start() {
        if (listening) return
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onStatus(context.getString(R.string.err_no_recognizer))
            return
        }
        listening = true
        startCycle()
    }

    fun stop() {
        listening = false
        recognizer?.destroy()
        recognizer = null
        onStatus(context.getString(R.string.status_stopped))
    }

    private fun startCycle() {
        if (!listening) return
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).also { r ->
            r.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    onStatus(context.getString(R.string.status_listening))
                }

                override fun onResults(results: Bundle?) {
                    val best = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                    if (!best.isNullOrBlank()) onFinal(best)
                    startCycle()
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val best = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                    if (!best.isNullOrBlank()) onPartial(best)
                }

                override fun onError(error: Int) {
                    // Silence timeouts and no-match are normal between sentences.
                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                        SpeechRecognizer.ERROR_CLIENT -> startCycle()
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                            listening = false
                            onStatus(context.getString(R.string.err_mic_permission))
                        }
                        else -> startCycle()
                    }
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            r.startListening(recognitionIntent())
        }
    }

    private fun recognitionIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-ZA")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
    }
}
