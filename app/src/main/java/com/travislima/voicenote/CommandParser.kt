package com.travislima.voicenote

/**
 * Turns a stream of recognised utterances into a structured [Document].
 *
 * Spoken commands (case-insensitive):
 *  - "paragraph" / "new paragraph"  -> close the current block; skip a line
 *  - "heading"                       -> next text is a heading (CAPS + bold);
 *                                       ends with "paragraph" or "end heading"
 *  - "quote" / "open quote"          -> start an indented, italicised quotation
 *                                       block; ends with "end quote" / "unquote"
 *                                       / "close quote"
 *  - "footnote"                      -> start a footnote anchored at the current
 *                                       position; ends with "end footnote" /
 *                                       "close footnote"
 *  - spoken punctuation: "full stop", "period", "comma", "question mark",
 *    "exclamation mark", "colon", "semicolon", "open bracket", "close bracket",
 *    "new line"
 *  - an utterance that is exactly "end" -> the document is finished
 *    (the caller receives [Event.FINISH] and should export + email it)
 */
class CommandParser {

    enum class Event { NONE, CHANGED, FINISH }

    private enum class Mode { BODY, HEADING, QUOTE }

    val document = Document()

    private var mode = Mode.BODY
    private var inFootnote = false
    private var current: Block? = null
    private var currentFootnote: Footnote? = null

    /** Feed one final utterance from the speech recogniser. */
    fun feed(utterance: String): Event {
        val trimmed = utterance.trim()
        if (trimmed.isEmpty()) return Event.NONE

        if (trimmed.lowercase().replace(Regex("[.!?,]"), "").trim() == "end" && !inFootnote) {
            closeBlock()
            return Event.FINISH
        }

        val words = trimmed.split(Regex("\\s+"))
        var i = 0
        while (i < words.size) {
            val w = clean(words[i])
            val next = if (i + 1 < words.size) clean(words[i + 1]) else null

            when {
                // ---- two-word commands ----
                w == "new" && next == "paragraph" -> { endParagraph(); i += 2 }
                w == "new" && next == "line" -> { appendText("\n"); i += 2 }
                w == "end" && next == "heading" -> { endParagraph(); i += 2 }
                (w == "end" || w == "close") && next == "quote" -> { endQuote(); i += 2 }
                w == "open" && next == "quote" -> { startQuote(); i += 2 }
                (w == "end" || w == "close") && next == "footnote" -> { endFootnote(); i += 2 }
                w == "full" && next == "stop" -> { appendPunct("."); i += 2 }
                w == "question" && next == "mark" -> { appendPunct("?"); i += 2 }
                w == "exclamation" && (next == "mark" || next == "point") -> { appendPunct("!"); i += 2 }
                w == "open" && next == "bracket" -> { appendText(" ("); i += 2 }
                w == "close" && next == "bracket" -> { appendPunct(")"); i += 2 }
                // ---- one-word commands ----
                w == "paragraph" -> { endParagraph(); i += 1 }
                w == "heading" -> { startHeading(); i += 1 }
                w == "quote" -> { startQuote(); i += 1 }
                w == "unquote" -> { endQuote(); i += 1 }
                w == "footnote" -> { startFootnote(); i += 1 }
                w == "period" -> { appendPunct("."); i += 1 }
                w == "comma" -> { appendPunct(","); i += 1 }
                w == "colon" -> { appendPunct(":"); i += 1 }
                w == "semicolon" -> { appendPunct(";"); i += 1 }
                else -> { appendWord(words[i]); i += 1 }
            }
        }
        return Event.CHANGED
    }

    private fun clean(word: String) = word.lowercase().trim('.', ',', '!', '?', ';', ':')

    // ---------------- block handling ----------------

    private fun target(): StringBuilder {
        currentFootnote?.let { return it.text }
        val block = current ?: newBlock()
        return block.text
    }

    private fun newBlock(): Block {
        val block = when (mode) {
            Mode.HEADING -> Block.Heading()
            Mode.QUOTE -> Block.Quote()
            Mode.BODY -> Block.Paragraph()
        }
        document.blocks.add(block)
        current = block
        return block
    }

    private fun closeBlock() {
        endFootnote()
        current = null
        mode = Mode.BODY
    }

    private fun endParagraph() = closeBlock()

    private fun startHeading() {
        closeBlock()
        mode = Mode.HEADING
        newBlock()
    }

    private fun startQuote() {
        if (mode == Mode.QUOTE) return
        endFootnote()
        current = null
        mode = Mode.QUOTE
        newBlock()
    }

    private fun endQuote() {
        if (mode != Mode.QUOTE) return
        closeBlock()
    }

    private fun startFootnote() {
        if (inFootnote) return
        val block = current ?: newBlock()
        val note = Footnote(anchor = block.text.length)
        block.footnotes.add(note)
        currentFootnote = note
        inFootnote = true
    }

    private fun endFootnote() {
        if (!inFootnote) return
        currentFootnote?.let { finishSentence(it.text) }
        currentFootnote = null
        inFootnote = false
    }

    // ---------------- text handling ----------------

    private fun appendWord(raw: String) {
        val sb = target()
        val word = BritishSpelling.normaliseWord(raw)
        val cased = when {
            mode == Mode.HEADING && !inFootnote -> word.uppercase()
            needsCapital(sb) -> word.replaceFirstChar { it.uppercase() }
            else -> word
        }
        if (sb.isNotEmpty() && !sb.endsWith("\n") && !sb.endsWith("(")) sb.append(' ')
        sb.append(cased)
    }

    private fun appendPunct(p: String) {
        val sb = target()
        while (sb.isNotEmpty() && sb.last() == ' ') sb.deleteCharAt(sb.length - 1)
        sb.append(p)
    }

    private fun appendText(t: String) = target().append(t)

    private fun needsCapital(sb: StringBuilder): Boolean {
        val s = sb.toString().trimEnd()
        if (s.isEmpty()) return true
        return s.endsWith(".") || s.endsWith("!") || s.endsWith("?")
    }

    private fun finishSentence(sb: StringBuilder) {
        val s = sb.toString().trimEnd()
        if (s.isNotEmpty() && !s.endsWith(".") && !s.endsWith("!") && !s.endsWith("?")) {
            sb.setLength(0)
            sb.append(s).append('.')
        }
    }

    /** Close any open structures (e.g. when exporting mid-dictation). */
    fun finalizeDocument(): Document {
        closeBlock()
        return document
    }
}
