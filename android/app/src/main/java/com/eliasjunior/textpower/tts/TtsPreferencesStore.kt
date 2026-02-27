package com.eliasjunior.textpower.tts

import android.content.Context

data class TtsPreferences(
    val speechRate: Float = 1.0f,
    val pitch: Float = 1.0f,
    val languageTag: String = "",
    val voiceName: String? = null
)

class TtsPreferencesStore(context: Context) {
    private val prefs = context.getSharedPreferences("tts_prefs", Context.MODE_PRIVATE)

    fun load(): TtsPreferences {
        return TtsPreferences(
            speechRate = prefs.getFloat("speech_rate", 1.0f),
            pitch = prefs.getFloat("pitch", 1.0f),
            languageTag = prefs.getString("language_tag", "").orEmpty(),
            voiceName = prefs.getString("voice_name", null)
        )
    }

    fun save(preferences: TtsPreferences) {
        prefs.edit()
            .putFloat("speech_rate", preferences.speechRate)
            .putFloat("pitch", preferences.pitch)
            .putString("language_tag", preferences.languageTag)
            .putString("voice_name", preferences.voiceName)
            .apply()
    }
}
