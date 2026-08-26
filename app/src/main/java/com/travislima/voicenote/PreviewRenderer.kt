package com.travislima.voicenote

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.LeadingMarginSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.SuperscriptSpan

/** Renders the live document model as styled text for the on-screen preview. */
object PreviewRenderer {

    fun render(doc: Document, pending: String?): CharSequence {
        val out = SpannableStringBuilder()
        var noteNumber = 0
        val noteTexts = mutableListOf<String>()

        for (block in doc.blocks) {
            if (block.text.isBlank() && block.footnotes.isEmpty()) continue
            val start = out.length
            var text = block.text.toString().trim()
            if (block is Block.Heading) text = text.uppercase()
            if (block is Block.Quote) text = "“$text”"
            val quoteShift = if (block is Block.Quote) 1 else 0

            // Insert superscript footnote markers at their anchors.
            var inserted = 0
            val sb = StringBuilder(text)
            val markers = mutableListOf<Pair<Int, Int>>() // position, number
            for (note in block.footnotes.sortedBy { it.anchor }) {
                noteNumber += 1
                val at = (note.anchor + quoteShift + inserted).coerceIn(0, sb.length)
                val marker = noteNumber.toString()
                sb.insert(at, marker)
                markers.add(at to marker.length)
                inserted += marker.length
                noteTexts.add("$noteNumber. ${note.text.toString().trim()}")
            }
            out.append(sb)

            when (block) {
                is Block.Heading -> {
                    out.setSpan(StyleSpan(Typeface.BOLD), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    out.setSpan(RelativeSizeSpan(1.15f), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                is Block.Quote -> {
                    out.setSpan(StyleSpan(Typeface.ITALIC), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    out.setSpan(LeadingMarginSpan.Standard(64), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                is Block.Paragraph -> {}
            }
            for ((pos, len) in markers) {
                out.setSpan(SuperscriptSpan(), start + pos, start + pos + len, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                out.setSpan(RelativeSizeSpan(0.7f), start + pos, start + pos + len, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            out.append("\n\n")
        }

        if (!pending.isNullOrBlank()) {
            val start = out.length
            out.append(pending)
            out.setSpan(StyleSpan(Typeface.ITALIC), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            out.append("\n\n")
        }

        if (noteTexts.isNotEmpty()) {
            val start = out.length
            out.append("—\n")
            out.append(noteTexts.joinToString("\n"))
            out.setSpan(RelativeSizeSpan(0.85f), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return out
    }
}
