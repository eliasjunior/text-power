package com.eliasjunior.textpower.tts

import android.content.Context

data class TtsPreferences(
    val speechRate: Float = 1.0f,
    val pitch: Float = 1.0f,
    val languageTag: String = "",
    val voiceName: String? = null,
    val multiPageScanEnabled: Boolean = true,
    val resumeCharOffset: Int = 0,
    val resumeTextHash: String = "",
    val resumePending: Boolean = false
)

class TtsPreferencesStore(context: Context) {
    private val prefs = context.getSharedPreferences("tts_prefs", Context.MODE_PRIVATE)

    fun load(): TtsPreferences {
        return TtsPreferences(
            speechRate = prefs.getFloat("speech_rate", 1.0f),
            pitch = prefs.getFloat("pitch", 1.0f),
            languageTag = prefs.getString("language_tag", "").orEmpty(),
            voiceName = prefs.getString("voice_name", null),
            multiPageScanEnabled = prefs.getBoolean("multi_page_scan_enabled", true),
            resumeCharOffset = prefs.getInt("resume_char_offset", 0),
            resumeTextHash = prefs.getString("resume_text_hash", "").orEmpty(),
            resumePending = prefs.getBoolean("resume_pending", false)
        )
    }

    fun save(preferences: TtsPreferences) {
        prefs.edit()
            .putFloat("speech_rate", preferences.speechRate)
            .putFloat("pitch", preferences.pitch)
            .putString("language_tag", preferences.languageTag)
            .putString("voice_name", preferences.voiceName)
            .putBoolean("multi_page_scan_enabled", preferences.multiPageScanEnabled)
            .putInt("resume_char_offset", preferences.resumeCharOffset.coerceAtLeast(0))
            .putString("resume_text_hash", preferences.resumeTextHash)
            .putBoolean("resume_pending", preferences.resumePending)
            .apply()
    }
}
