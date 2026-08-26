package com.travislima.voicenote

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Writes a [Document] as a .docx file (OOXML), with no external dependencies.
 * Output is readable by OnlyOffice, Word, LibreOffice and Google Docs.
 *
 * Formatting per the brief:
 *  - headings: bold, upper case
 *  - quotations: italic, wrapped in quotation marks, indented 2 cm from the margin
 *  - real Word footnotes
 *  - automatic page numbers in the footer
 *  - A4 page, document language en-ZA (South African English spellcheck)
 */
object DocxWriter {

    private const val W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
    private const val R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private const val TWIPS_2CM = 1134 // 2 cm in twentieths of a point

    fun write(doc: Document, out: OutputStream) {
        ZipOutputStream(out).use { zip ->
            zip.put("[Content_Types].xml", contentTypes())
            zip.put("_rels/.rels", rootRels())
            zip.put("word/_rels/document.xml.rels", documentRels())
            zip.put("word/styles.xml", styles())
            zip.put("word/footer1.xml", footer())
            val (documentXml, footnotesXml) = buildBody(doc)
            zip.put("word/document.xml", documentXml)
            zip.put("word/footnotes.xml", footnotesXml)
        }
    }

    private fun ZipOutputStream.put(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun esc(s: String) = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&apos;")

    // ------------------------------------------------------------------ body

    private fun buildBody(doc: Document): Pair<String, String> {
        val body = StringBuilder()
        val notes = StringBuilder()
        var noteId = 0

        for (block in doc.blocks) {
            if (block.text.isBlank() && block.footnotes.isEmpty()) continue
            val runs = StringBuilder()
            val text = block.text.toString().trim()
            val display = when (block) {
                is Block.Quote -> "“$text”"
                else -> text
            }
            val quoteShift = if (block is Block.Quote) 1 else 0 // opening quote char offset

            // Emit text runs split at footnote anchors so references sit in place.
            val anchors = block.footnotes.sortedBy { it.anchor }
            var pos = 0
            for (note in anchors) {
                noteId += 1
                val cut = (note.anchor + quoteShift).coerceIn(0, display.length)
                if (cut > pos) runs.append(run(display.substring(pos, cut), block))
                runs.append(footnoteRef(noteId))
                notes.append(footnoteBody(noteId, note.text.toString().trim()))
                pos = cut
            }
            if (pos < display.length) runs.append(run(display.substring(pos), block))

            body.append(paragraph(block, runs.toString()))
        }

        if (body.isEmpty()) body.append("<w:p/>")

        val documentXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="$W" xmlns:r="$R">
<w:body>
$body<w:sectPr>
<w:footerReference w:type="default" r:id="rId3"/>
<w:pgSz w:w="11906" w:h="16838"/>
<w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440" w:header="708" w:footer="708" w:gutter="0"/>
</w:sectPr>
</w:body>
</w:document>"""

        val footnotesXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:footnotes xmlns:w="$W" xmlns:r="$R">
<w:footnote w:type="separator" w:id="-1"><w:p><w:r><w:separator/></w:r></w:p></w:footnote>
<w:footnote w:type="continuationSeparator" w:id="0"><w:p><w:r><w:continuationSeparator/></w:r></w:p></w:footnote>
$notes</w:footnotes>"""

        return documentXml to footnotesXml
    }

    private fun paragraph(block: Block, runs: String): String {
        val pPr = when (block) {
            is Block.Heading ->
                "<w:pPr><w:pStyle w:val=\"Heading1\"/><w:spacing w:after=\"240\"/></w:pPr>"
            is Block.Quote ->
                "<w:pPr><w:ind w:left=\"$TWIPS_2CM\" w:right=\"$TWIPS_2CM\"/><w:spacing w:after=\"240\"/></w:pPr>"
            is Block.Paragraph ->
                "<w:pPr><w:spacing w:after=\"240\"/></w:pPr>"
        }
        return "<w:p>$pPr$runs</w:p>\n"
    }

    private fun run(text: String, block: Block): String {
        if (text.isEmpty()) return ""
        val rPr = when (block) {
            is Block.Heading -> "<w:rPr><w:b/></w:rPr>"
            is Block.Quote -> "<w:rPr><w:i/></w:rPr>"
            is Block.Paragraph -> ""
        }
        // Preserve manual "new line" breaks inside a paragraph.
        val parts = text.split("\n")
        val sb = StringBuilder()
        for ((idx, part) in parts.withIndex()) {
            if (idx > 0) sb.append("<w:r>$rPr<w:br/></w:r>")
            if (part.isNotEmpty()) {
                sb.append("<w:r>$rPr<w:t xml:space=\"preserve\">${esc(part)}</w:t></w:r>")
            }
        }
        return sb.toString()
    }

    private fun footnoteRef(id: Int) =
        "<w:r><w:rPr><w:rStyle w:val=\"FootnoteReference\"/></w:rPr><w:footnoteReference w:id=\"$id\"/></w:r>"

    private fun footnoteBody(id: Int, text: String) = """
<w:footnote w:id="$id"><w:p><w:pPr><w:pStyle w:val="FootnoteText"/></w:pPr><w:r><w:rPr><w:rStyle w:val="FootnoteReference"/></w:rPr><w:footnoteRef/></w:r><w:r><w:t xml:space="preserve"> ${esc(text)}</w:t></w:r></w:p></w:footnote>
""".trimStart()

    // ------------------------------------------------------------- packaging

    private fun contentTypes() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
<Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
<Override PartName="/word/footnotes.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.footnotes+xml"/>
<Override PartName="/word/footer1.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.footer+xml"/>
</Types>"""

    private fun rootRels() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""

    private fun documentRels() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/footnotes" Target="footnotes.xml"/>
<Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/footer" Target="footer1.xml"/>
</Relationships>"""

    private fun styles() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="$W">
<w:docDefaults>
<w:rPrDefault><w:rPr><w:rFonts w:ascii="Arial" w:hAnsi="Arial"/><w:sz w:val="24"/><w:lang w:val="en-ZA"/></w:rPr></w:rPrDefault>
<w:pPrDefault><w:pPr><w:spacing w:line="276" w:lineRule="auto"/></w:pPr></w:pPrDefault>
</w:docDefaults>
<w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/></w:style>
<w:style w:type="paragraph" w:styleId="Heading1">
<w:name w:val="heading 1"/><w:basedOn w:val="Normal"/>
<w:pPr><w:keepNext/><w:spacing w:before="240" w:after="240"/><w:outlineLvl w:val="0"/></w:pPr>
<w:rPr><w:b/><w:sz w:val="28"/></w:rPr>
</w:style>
<w:style w:type="paragraph" w:styleId="FootnoteText">
<w:name w:val="footnote text"/><w:basedOn w:val="Normal"/>
<w:rPr><w:sz w:val="20"/></w:rPr>
</w:style>
<w:style w:type="character" w:styleId="FootnoteReference">
<w:name w:val="footnote reference"/>
<w:rPr><w:vertAlign w:val="superscript"/></w:rPr>
</w:style>
</w:styles>"""

    private fun footer() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:ftr xmlns:w="$W">
<w:p><w:pPr><w:jc w:val="center"/></w:pPr>
<w:r><w:fldChar w:fldCharType="begin"/></w:r>
<w:r><w:instrText xml:space="preserve"> PAGE </w:instrText></w:r>
<w:r><w:fldChar w:fldCharType="separate"/></w:r>
<w:r><w:t>1</w:t></w:r>
<w:r><w:fldChar w:fldCharType="end"/></w:r>
</w:p>
</w:ftr>"""
}
