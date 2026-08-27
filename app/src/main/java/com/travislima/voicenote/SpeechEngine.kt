package com.travislima.voicenote

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

/**
 * Continuous dictation on top of Android's SpeechRecognizer. No account
 * sign-in is required.
 *
 * The recogniser stops itself on silence, so this engine restarts it until
 * [stop] is called. Not every device has an offline model for every language,
 * and a missing model fails with errors rather than results — so the engine
 * walks a ladder of configurations (en-ZA offline, en-GB offline, device
 * default offline, then the same three online) and locks onto the first one
 * that actually produces text. Every error is surfaced through [onStatus].
 */
class SpeechEngine(
    private val context: Context,
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onStatus: (String) -> Unit,
) {
    private data class Config(val language: String?, val preferOffline: Boolean) {
        fun label(): String =
            (language ?: "default language") + if (preferOffline) " · offline" else " · online"
    }

    private val configs = listOf(
        Config("en-ZA", true),
        Config("en-GB", true),
        Config(null, true),
        Config("en-ZA", false),
        Config("en-GB", false),
        Config(null, false),
    )

    private var configIndex = 0
    private var configLocked = false
    private var failuresOnConfig = 0

    private var recognizer: SpeechRecognizer? = null
    private var listening = false
    private val handler = Handler(Looper.getMainLooper())
    private val restartRunnable = Runnable { startCycle() }

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
        handler.removeCallbacks(restartRunnable)
        recognizer?.destroy()
        recognizer = null
        onStatus(context.getString(R.string.status_stopped))
    }

    private fun scheduleRestart(delayMs: Long) {
        if (!listening) return
        handler.removeCallbacks(restartRunnable)
        handler.postDelayed(restartRunnable, delayMs)
    }

    private fun advanceConfig(reason: String) {
        if (configLocked) return
        if (configIndex < configs.size - 1) {
            configIndex += 1
            failuresOnConfig = 0
            val cfg = configs[configIndex]
            Log.i(TAG, "advancing config ($reason) -> $cfg")
            onStatus(context.getString(R.string.status_trying, cfg.label()))
        } else {
            onStatus(context.getString(R.string.err_all_configs, reason))
        }
    }

    private fun startCycle() {
        if (!listening) return
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).also { r ->
            r.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    val cfg = configs[configIndex]
                    onStatus(
                        if (configLocked) context.getString(R.string.status_listening)
                        else context.getString(R.string.status_listening_cfg, cfg.label())
                    )
                }

                override fun onResults(results: Bundle?) {
                    val best = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                    if (!best.isNullOrBlank()) {
                        // This configuration works — stay on it.
                        configLocked = true
                        failuresOnConfig = 0
                        onFinal(best)
                    }
                    scheduleRestart(100)
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val best = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                    if (!best.isNullOrBlank()) {
                        configLocked = true
                        onPartial(best)
                    }
                }

                override fun onError(error: Int) {
                    Log.w(TAG, "recognizer error $error (${errorName(error)}) on ${configs[configIndex]}")
                    when (error) {
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                            listening = false
                            onStatus(context.getString(R.string.err_mic_permission))
                            return
                        }
                        // Normal silence between sentences — quick restart.
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                            failuresOnConfig += 1
                            // Many consecutive "no match" without ever hearing text
                            // usually means the language/model doesn't work at all.
                            if (!configLocked && failuresOnConfig >= 4) advanceConfig(errorName(error))
                            scheduleRestart(150)
                        }
                        ERROR_LANGUAGE_NOT_SUPPORTED, ERROR_LANGUAGE_UNAVAILABLE -> {
                            advanceConfig(errorName(error))
                            scheduleRestart(200)
                        }
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> scheduleRestart(1000)
                        else -> {
                            failuresOnConfig += 1
                            onStatus(context.getString(R.string.status_error, errorName(error)))
                            if (!configLocked && failuresOnConfig >= 3) advanceConfig(errorName(error))
                            scheduleRestart(500)
                        }
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

    private fun recognitionIntent(): Intent {
        val cfg = configs[configIndex]
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            cfg.language?.let { putExtra(RecognizerIntent.EXTRA_LANGUAGE, it) }
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            if (cfg.preferOffline) putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
        }
    }

    private fun errorName(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "network timeout"
        SpeechRecognizer.ERROR_NETWORK -> "network error"
        SpeechRecognizer.ERROR_AUDIO -> "audio recording error"
        SpeechRecognizer.ERROR_SERVER -> "server error"
        SpeechRecognizer.ERROR_CLIENT -> "client error"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "no speech heard"
        SpeechRecognizer.ERROR_NO_MATCH -> "no match"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "recognizer busy"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "microphone permission missing"
        ERROR_TOO_MANY_REQUESTS -> "too many requests"
        ERROR_SERVER_DISCONNECTED -> "server disconnected"
        ERROR_LANGUAGE_NOT_SUPPORTED -> "language not supported"
        ERROR_LANGUAGE_UNAVAILABLE -> "language model unavailable"
        ERROR_CANNOT_CHECK_SUPPORT -> "cannot check support"
        else -> "error $code"
    }

    companion object {
        private const val TAG = "SpeechEngine"

        // SpeechRecognizer constants added in API 31/33; inlined so minSdk 26 compiles.
        private const val ERROR_TOO_MANY_REQUESTS = 10
        private const val ERROR_SERVER_DISCONNECTED = 11
        private const val ERROR_LANGUAGE_NOT_SUPPORTED = 12
        private const val ERROR_LANGUAGE_UNAVAILABLE = 13
        private const val ERROR_CANNOT_CHECK_SUPPORT = 14
    }
}
