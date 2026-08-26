package com.travislima.voicenote

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class DocxWriterTest {

    private fun buildDocx(): Map<String, String> {
        val p = CommandParser()
        p.feed("heading heads of argument paragraph the appellant submits the following")
        p.feed("quote the law must be certain end quote")
        p.feed("as noted footnote see the record at page five end footnote above")
        val doc = p.finalizeDocument()

        val out = ByteArrayOutputStream()
        DocxWriter.write(doc, out)

        val entries = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                entries[e.name] = zip.readBytes().toString(Charsets.UTF_8)
                e = zip.nextEntry
            }
        }
        return entries
    }

    @Test
    fun packageHasAllParts() {
        val entries = buildDocx()
        assertTrue("[Content_Types].xml" in entries)
        assertTrue("_rels/.rels" in entries)
        assertTrue("word/document.xml" in entries)
        assertTrue("word/styles.xml" in entries)
        assertTrue("word/footnotes.xml" in entries)
        assertTrue("word/footer1.xml" in entries)
    }

    @Test
    fun headingIsBoldAndUppercase() {
        val docXml = buildDocx().getValue("word/document.xml")
        assertTrue(docXml.contains("HEADS OF ARGUMENT"))
        assertTrue(docXml.contains("Heading1"))
    }

    @Test
    fun quoteIsItalicIndentedAndQuoted() {
        val docXml = buildDocx().getValue("word/document.xml")
        assertTrue(docXml.contains("<w:i/>"))
        assertTrue(docXml.contains("w:left=\"1134\""))
        assertTrue(docXml.contains("“The law must be certain”"))
    }

    @Test
    fun footnoteIsReferencedAndDefined() {
        val entries = buildDocx()
        assertTrue(entries.getValue("word/document.xml").contains("<w:footnoteReference w:id=\"1\"/>"))
        assertTrue(entries.getValue("word/footnotes.xml").contains("See the record at page five."))
    }

    @Test
    fun footerHasPageNumberField() {
        val footer = buildDocx().getValue("word/footer1.xml")
        assertTrue(footer.contains(" PAGE "))
    }
}
