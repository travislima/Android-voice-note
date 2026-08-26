package com.travislima.voicenote

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar

class MainActivity : AppCompatActivity() {

    private lateinit var engine: SpeechEngine
    private var parser = CommandParser()

    private lateinit var preview: TextView
    private lateinit var scroll: ScrollView
    private lateinit var status: TextView
    private lateinit var recordButton: MaterialButton
    private lateinit var sendButton: MaterialButton
    private lateinit var clearButton: MaterialButton
    private lateinit var emailButton: MaterialButton

    private var pendingPartial: String? = null

    private val micPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startDictation()
            else status.text = getString(R.string.err_mic_permission)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        preview = findViewById(R.id.preview)
        scroll = findViewById(R.id.scroll)
        status = findViewById(R.id.status)
        recordButton = findViewById(R.id.btn_record)
        sendButton = findViewById(R.id.btn_send)
        clearButton = findViewById(R.id.btn_clear)
        emailButton = findViewById(R.id.btn_email)

        engine = SpeechEngine(
            context = this,
            onPartial = { text ->
                pendingPartial = text
                refreshPreview()
            },
            onFinal = { text ->
                pendingPartial = null
                when (parser.feed(text)) {
                    CommandParser.Event.FINISH -> finishAndSend()
                    else -> refreshPreview()
                }
            },
            onStatus = { s -> runOnUiThread { status.text = s } },
        )

        recordButton.setOnClickListener { toggleDictation() }
        sendButton.setOnClickListener { finishAndSend() }
        clearButton.setOnClickListener { confirmClear() }
        emailButton.setOnClickListener { promptEmail() }

        refreshPreview()
        if (prefs().getString(KEY_EMAIL, null).isNullOrBlank()) promptEmail()
        if (BuildConfig.DEBUG) registerDebugReceiver()
    }

    /**
     * Debug builds only: lets automated tests inject utterances into the same
     * path the speech recogniser uses, e.g.
     *   adb shell am broadcast -a com.travislima.voicenote.DEBUG_UTTERANCE \
     *       --es text "heading notice of motion"
     */
    private var debugReceiver: BroadcastReceiver? = null

    private fun registerDebugReceiver() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val text = intent?.getStringExtra("text") ?: return
                pendingPartial = null
                when (parser.feed(text)) {
                    CommandParser.Event.FINISH -> finishAndSend()
                    else -> refreshPreview()
                }
            }
        }
        debugReceiver = receiver
        val filter = IntentFilter("com.travislima.voicenote.DEBUG_UTTERANCE")
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(receiver, filter)
        }
    }

    override fun onDestroy() {
        engine.stop()
        debugReceiver?.let { unregisterReceiver(it) }
        super.onDestroy()
    }

    private fun toggleDictation() {
        if (engine.isListening) {
            engine.stop()
            recordButton.text = getString(R.string.btn_record)
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                micPermission.launch(Manifest.permission.RECORD_AUDIO)
            } else {
                startDictation()
            }
        }
    }

    private fun startDictation() {
        engine.start()
        recordButton.text = getString(R.string.btn_stop)
    }

    private fun finishAndSend() {
        engine.stop()
        recordButton.text = getString(R.string.btn_record)
        val doc = parser.finalizeDocument()
        if (doc.isEmpty()) {
            Snackbar.make(preview, R.string.err_empty, Snackbar.LENGTH_SHORT).show()
            return
        }
        val recipient = prefs().getString(KEY_EMAIL, null)
        EmailSender.exportAndEmail(this, doc, recipient)
        status.text = getString(R.string.status_sent)
    }

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setMessage(R.string.confirm_clear)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                parser = CommandParser()
                pendingPartial = null
                refreshPreview()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun promptEmail() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            setText(prefs().getString(KEY_EMAIL, ""))
            hint = getString(R.string.email_hint)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.email_dialog_title)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                prefs().edit().putString(KEY_EMAIL, input.text.toString().trim()).apply()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun refreshPreview() {
        runOnUiThread {
            val rendered = PreviewRenderer.render(parser.document, pendingPartial)
            preview.text = if (rendered.isBlank()) getString(R.string.preview_hint) else rendered
            scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }

    private fun prefs() = getSharedPreferences("voicenote", MODE_PRIVATE)

    companion object {
        private const val KEY_EMAIL = "email"
    }
}
