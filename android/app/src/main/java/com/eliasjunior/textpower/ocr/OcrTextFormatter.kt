package com.eliasjunior.textpower.ocr

import com.google.mlkit.vision.text.Text

class OcrTextFormatter {
    enum class CleaningLevel {
        NORMAL,
        AGGRESSIVE
    }

    fun format(result: Text, filterByBlocks: Boolean, cleaningLevel: CleaningLevel): String {
        if (!filterByBlocks) {
            val base = normalizeForReading(result.text)
            return cleanFinalText(base, cleaningLevel)
        }

        val paragraphs = result.textBlocks
            .mapNotNull { block ->
                val lines = block.lines
                    .map { line -> normalizeSpaces(line.text) }
                    .filter { line -> line.isNotBlank() }

                val usefulLines = lines.filter { line -> isUsefulLine(line, cleaningLevel) }
                if (usefulLines.isEmpty()) return@mapNotNull null
                usefulLines.joinToString(" ")
            }
            .map { normalizeForReading(it) }
            .filter { it.isNotBlank() }

        val merged = paragraphs.joinToString("\n\n").trim()
        return cleanFinalText(merged, cleaningLevel)
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

    private fun isUsefulLine(line: String, cleaningLevel: CleaningLevel): Boolean {
        val text = line.trim()
        if (text.isBlank()) return false

        // Drop lines that are only punctuation/symbols.
        if (!text.any { it.isLetterOrDigit() }) return false

        // In aggressive mode, drop isolated single-character OCR noise.
        if (cleaningLevel == CleaningLevel.AGGRESSIVE && text.length == 1) return false

        // Drop mostly-symbol garbage.
        val symbols = text.count { !it.isLetterOrDigit() && !it.isWhitespace() }
        val symbolRatio = symbols.toFloat() / text.length.toFloat()
        val maxRatio = if (cleaningLevel == CleaningLevel.AGGRESSIVE) 0.45f else 0.70f
        if (symbolRatio > maxRatio) return false

        return true
    }

    private fun cleanFinalText(text: String, cleaningLevel: CleaningLevel): String {
        return text
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filter { isUsefulLine(it, cleaningLevel) }
            .joinToString("\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }
}
