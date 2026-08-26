package com.travislima.voicenote

/**
 * A dictated document is an ordered list of blocks. Footnotes are anchored
 * inside body/quote text by index into the block's text (character offset).
 */
sealed class Block {
    abstract val text: StringBuilder
    abstract val footnotes: MutableList<Footnote>

    class Heading(
        override val text: StringBuilder = StringBuilder(),
        override val footnotes: MutableList<Footnote> = mutableListOf(),
    ) : Block()

    class Paragraph(
        override val text: StringBuilder = StringBuilder(),
        override val footnotes: MutableList<Footnote> = mutableListOf(),
    ) : Block()

    class Quote(
        override val text: StringBuilder = StringBuilder(),
        override val footnotes: MutableList<Footnote> = mutableListOf(),
    ) : Block()
}

/** A footnote anchored at [anchor] (character offset in the owning block's text). */
class Footnote(val anchor: Int, val text: StringBuilder = StringBuilder())

class Document {
    val blocks = mutableListOf<Block>()

    fun isEmpty(): Boolean = blocks.all { it.text.isBlank() && it.footnotes.isEmpty() }
}
