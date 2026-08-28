package com.travislima.voicenote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandParserTest {

    @Test
    fun paragraphCommandSplitsBlocks() {
        val p = CommandParser()
        p.feed("this is the first point paragraph and this is the second")
        val doc = p.finalizeDocument()
        assertEquals(2, doc.blocks.size)
        assertEquals("This is the first point", doc.blocks[0].text.toString())
        assertEquals("And this is the second", doc.blocks[1].text.toString())
    }

    @Test
    fun headingIsSeparateBlockInCaps() {
        val p = CommandParser()
        p.feed("heading notice of motion paragraph take notice that the applicant will apply")
        val doc = p.finalizeDocument()
        assertEquals(2, doc.blocks.size)
        assertTrue(doc.blocks[0] is Block.Heading)
        assertEquals("NOTICE OF MOTION", doc.blocks[0].text.toString())
        assertTrue(doc.blocks[1] is Block.Paragraph)
    }

    @Test
    fun quoteBlockCollectsUntilEndQuote() {
        val p = CommandParser()
        p.feed("the court held as follows quote justice must be seen to be done end quote which supports our case")
        val doc = p.finalizeDocument()
        assertEquals(3, doc.blocks.size)
        assertTrue(doc.blocks[1] is Block.Quote)
        assertEquals("Justice must be seen to be done", doc.blocks[1].text.toString())
        assertEquals("Which supports our case", doc.blocks[2].text.toString())
    }

    @Test
    fun footnoteAnchorsInParagraph() {
        val p = CommandParser()
        p.feed("as held in smith footnote smith versus jones nineteen ninety nine end footnote the point stands")
        val doc = p.finalizeDocument()
        assertEquals(1, doc.blocks.size)
        val block = doc.blocks[0]
        assertEquals(1, block.footnotes.size)
        assertEquals("As held in smith", block.text.substring(0, block.footnotes[0].anchor))
        assertTrue(block.footnotes[0].text.toString().startsWith("Smith versus jones"))
        assertTrue(block.footnotes[0].text.toString().endsWith("."))
    }

    @Test
    fun endUtteranceFinishes() {
        val p = CommandParser()
        p.feed("some dictated text")
        assertEquals(CommandParser.Event.FINISH, p.feed("end"))
    }

    @Test
    fun endInsideSentenceIsNotACommand() {
        val p = CommandParser()
        assertEquals(CommandParser.Event.CHANGED, p.feed("at the end of the day"))
        assertEquals("At the end of the day", p.finalizeDocument().blocks[0].text.toString())
    }

    @Test
    fun spokenPunctuationAndCapitalisation() {
        val p = CommandParser()
        p.feed("the first point full stop the second point")
        val doc = p.finalizeDocument()
        assertEquals("The first point. The second point", doc.blocks[0].text.toString())
    }

    // --- Recogniser-styled input: auto-capitals, auto-punctuation, newlines ---

    @Test
    fun autoPunctuatedCommandsAreRecognised() {
        val p = CommandParser()
        p.feed("Heading, heads of argument.")
        p.feed("Paragraph, may it please the court.")
        p.feed("New paragraph. The appellant submits the following.")
        val doc = p.finalizeDocument()
        assertEquals(3, doc.blocks.size)
        assertTrue(doc.blocks[0] is Block.Heading)
        assertEquals("HEADS OF ARGUMENT", doc.blocks[0].text.toString())
        assertEquals("May it please the court.", doc.blocks[1].text.toString())
        assertEquals("The appellant submits the following.", doc.blocks[2].text.toString())
    }

    @Test
    fun recogniserNewlinesBecomeParagraphs() {
        val p = CommandParser()
        p.feed("The first point.\n\nThe second point.")
        val doc = p.finalizeDocument()
        assertEquals(2, doc.blocks.size)
        assertEquals("The first point.", doc.blocks[0].text.toString())
        assertEquals("The second point.", doc.blocks[1].text.toString())
    }

    @Test
    fun singleNewlineBecomesLineBreak() {
        val p = CommandParser()
        p.feed("care of chambers\nfifth floor")
        val doc = p.finalizeDocument()
        assertEquals(1, doc.blocks.size)
        assertTrue(doc.blocks[0].text.toString().contains("\n"))
    }

    @Test
    fun punctuatedQuoteAndFootnoteCommands() {
        val p = CommandParser()
        p.feed("The court held, quote, justice must be done. End quote. Which supports us.")
        p.feed("As noted, footnote, see page five. End footnote. Above.")
        val doc = p.finalizeDocument()
        assertTrue(doc.blocks.any { it is Block.Quote })
        val quote = doc.blocks.first { it is Block.Quote }
        assertEquals("Justice must be done.", quote.text.toString())
        assertEquals(1, doc.blocks.sumOf { it.footnotes.size })
    }

    @Test
    fun capitalisedEndUtteranceFinishes() {
        val p = CommandParser()
        p.feed("Some dictated text.")
        assertEquals(CommandParser.Event.FINISH, p.feed("End."))
    }

    @Test
    fun britishSpellingApplied() {
        val p = CommandParser()
        p.feed("the color of the labor organization")
        val doc = p.finalizeDocument()
        assertEquals("The colour of the labour organisation", doc.blocks[0].text.toString())
    }
}
