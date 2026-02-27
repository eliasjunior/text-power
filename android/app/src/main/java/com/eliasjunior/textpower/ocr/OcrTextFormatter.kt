package com.eliasjunior.textpower.ocr

import com.google.mlkit.vision.text.Text

class OcrTextFormatter {
    fun format(result: Text, filterByBlocks: Boolean): OcrFormattedResult {
        if (!filterByBlocks) {
            val raw = normalizeSpaces(result.text)
            val fallback = if (raw.isBlank()) emptyList() else listOf(toScoredLine(raw))
            return OcrFormattedResult(
                plainText = raw,
                scoredLines = fallback
            )
        }

        val scoredLines = result.textBlocks
            .flatMap { block -> block.lines }
            .map { line -> normalizeSpaces(line.text) }
            .filter { line -> line.isNotBlank() }
            .map { line -> toScoredLine(line) }
            .filter { line -> line.qualityScore >= 40 }

        return OcrFormattedResult(
            plainText = scoredLines.joinToString("\n") { it.text }.trim(),
            scoredLines = scoredLines
        )
    }

    private fun normalizeSpaces(text: String): String {
        return text.replace(Regex("\\s+"), " ").trim()
    }

    private fun toScoredLine(line: String): OcrScoredLine {
        if (line.length < 2) return OcrScoredLine(line, 20, "Low")

        val lettersOrDigits = line.count { it.isLetterOrDigit() }
        val alphaNumericRatio = lettersOrDigits.toFloat() / line.length.toFloat()
        val allowedSymbols = ".,;:!?()[]{}'\"-_/+&%$#@"
        val noisyChars = line.count {
            !it.isLetterOrDigit() && !it.isWhitespace() && !allowedSymbols.contains(it)
        }
        val noiseRatio = noisyChars.toFloat() / line.length.toFloat()
        val hasWordLikePattern = Regex("[A-Za-z]{2,}").containsMatchIn(line)

        var score = 0
        score += (alphaNumericRatio * 45f).toInt()
        score += ((1f - noiseRatio) * 45f).toInt()
        score += if (hasWordLikePattern) 10 else 0
        score = score.coerceIn(0, 100)

        val label = when {
            score >= 80 -> "High"
            score >= 60 -> "Medium"
            else -> "Low"
        }

        return OcrScoredLine(
            text = line,
            qualityScore = score,
            qualityLabel = label
        )
    }
}

data class OcrFormattedResult(
    val plainText: String,
    val scoredLines: List<OcrScoredLine>
)

data class OcrScoredLine(
    val text: String,
    val qualityScore: Int,
    val qualityLabel: String
)
