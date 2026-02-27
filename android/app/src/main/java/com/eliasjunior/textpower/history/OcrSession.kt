package com.eliasjunior.textpower.history

data class OcrSession(
    val id: String,
    val timestampMs: Long,
    val imageFileName: String,
    val extractedText: String
)
