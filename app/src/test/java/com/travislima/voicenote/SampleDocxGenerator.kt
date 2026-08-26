package com.travislima.voicenote

import org.junit.Test
import java.io.File

/**
 * Not an assertion test: writes a realistic sample document to
 * build/sample-output/sample.docx so it can be opened and eyeballed
 * in OnlyOffice / Word.
 */
class SampleDocxGenerator {

    @Test
    fun generateSample() {
        val p = CommandParser()
        p.feed("heading heads of argument")
        p.feed("paragraph may it please the court full stop the appellant submits that the court below erred in three material respects")
        p.feed("paragraph first comma the learned judge misdirected himself on the onus of proof footnote see the record volume two page one hundred and twelve end footnote as appears from the judgment")
        p.feed("paragraph the authorities are clear quote the person who alleges must prove end quote this principle is well established in our law")
        val doc = p.finalizeDocument()

        val outDir = File("build/sample-output").apply { mkdirs() }
        File(outDir, "sample.docx").outputStream().use { DocxWriter.write(doc, it) }
    }
}
