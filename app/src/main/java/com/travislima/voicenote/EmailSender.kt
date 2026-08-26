package com.travislima.voicenote

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Exports the document to a .docx in app storage and opens the email app with it attached. */
object EmailSender {

    fun exportAndEmail(context: Context, doc: Document, recipient: String?): File {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH.mm", Locale.UK).format(Date())
        val dir = File(context.filesDir, "exports").apply { mkdirs() }
        val file = File(dir, "Dictation $stamp.docx")
        file.outputStream().use { DocxWriter.write(doc, it) }

        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.email_subject, stamp))
            putExtra(Intent.EXTRA_TEXT, context.getString(R.string.email_body))
            if (!recipient.isNullOrBlank()) putExtra(Intent.EXTRA_EMAIL, arrayOf(recipient))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(intent, context.getString(R.string.email_chooser))
        )
        return file
    }
}
