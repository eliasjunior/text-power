package com.eliasjunior.textpower.ocr

import com.google.mlkit.vision.text.Text

class OcrTextFormatter {
    fun format(result: Text, filterByBlocks: Boolean): String {
        if (!filterByBlocks) {
            return normalizeForReading(result.text)
        }

        val paragraphs = result.textBlocks
            .map { block ->
                block.lines.joinToString(" ") { normalizeSpaces(it.text) }
            }
            .map { normalizeForReading(it) }
            .filter { it.isNotBlank() }

        return paragraphs.joinToString("\n\n").trim()
    }

    private fun normalizeSpaces(text: String): String {
        return text.replace(Regex("\\s+"), " ").trim()
    }

    private fun normalizeForReading(text: String): String {
        return text
            .replace(Regex("\\s+"), " ")
            .replace(Regex("([A-Za-z])-\\s+([A-Za-z])"), "$1$2")
            .replace(Regex("\\s+([.,;:!?])"), "$1")
            .trim()
    }
}
