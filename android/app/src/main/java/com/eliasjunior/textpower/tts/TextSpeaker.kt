package com.eliasjunior.textpower.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

interface TextSpeaker {
    fun start(text: String): SpeakerResult
    fun pause(): SpeakerResult
    fun stop(): SpeakerResult
    fun shutdown()

    fun setSpeechRate(rate: Float): SpeakerResult
    fun setPitch(pitch: Float): SpeakerResult
    fun setLanguage(languageTag: String): SpeakerResult
    fun setVoice(voiceName: String?): SpeakerResult
    fun getAvailableVoices(): List<SpeakerVoice>
    fun getAvailableLanguageTags(): List<String>

    enum class SpeakerState {
        UNINITIALIZED,
        READY,
        SPEAKING,
        PAUSED,
        STOPPED,
        ERROR
    }

    data class SpeakerResult(
        val success: Boolean,
        val state: SpeakerState,
        val message: String? = null
    )

    data class SpeakerVoice(
        val name: String,
        val languageTag: String,
        val displayLabel: String
    )
}

class AndroidTextSpeaker(
    context: Context,
    private val defaultLocale: Locale = Locale.getDefault()
) : TextSpeaker {

    companion object {
        private const val MAX_CHUNK_CHARS = 2800
    }

    private var engine: TextToSpeech? = null
    private var state: TextSpeaker.SpeakerState = TextSpeaker.SpeakerState.UNINITIALIZED
    private var pendingText: String? = null

    private var speechRate: Float = 1.0f
    private var pitch: Float = 1.0f
    private var languageTag: String = defaultLocale.toLanguageTag()
    private var selectedVoiceName: String? = null

    init {
        engine = TextToSpeech(context.applicationContext) { status ->
            if (status != TextToSpeech.SUCCESS) {
                state = TextSpeaker.SpeakerState.ERROR
                return@TextToSpeech
            }

            val tts = engine ?: run {
                state = TextSpeaker.SpeakerState.ERROR
                return@TextToSpeech
            }

            tts.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    state = TextSpeaker.SpeakerState.SPEAKING
                }

                override fun onDone(utteranceId: String?) {
                    state = TextSpeaker.SpeakerState.STOPPED
                    pendingText = null
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    state = TextSpeaker.SpeakerState.ERROR
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    state = TextSpeaker.SpeakerState.ERROR
                }
            })

            applySpeakerSettings(tts)
            state = TextSpeaker.SpeakerState.READY
        }
    }

    override fun start(text: String): TextSpeaker.SpeakerResult {
        val clean = text.trim()
        if (clean.isEmpty()) {
            return TextSpeaker.SpeakerResult(
                success = false,
                state = state,
                message = "No text to speak."
            )
        }

        val tts = engine
        if (tts == null) {
            state = TextSpeaker.SpeakerState.ERROR
            return TextSpeaker.SpeakerResult(false, state, "TTS engine is not available.")
        }

        if (state == TextSpeaker.SpeakerState.UNINITIALIZED) {
            return TextSpeaker.SpeakerResult(false, state, "TTS is still initializing.")
        }

        if (state == TextSpeaker.SpeakerState.ERROR) {
            return TextSpeaker.SpeakerResult(false, state, "TTS initialization failed.")
        }

        applySpeakerSettings(tts)

        val chunks = chunkText(clean)
        if (chunks.isEmpty()) {
            return TextSpeaker.SpeakerResult(false, state, "No text to speak.")
        }

        var code = tts.speak("", TextToSpeech.QUEUE_FLUSH, null, "ocr_flush_${System.currentTimeMillis()}")
        if (code != TextToSpeech.SUCCESS) {
            state = TextSpeaker.SpeakerState.ERROR
            return TextSpeaker.SpeakerResult(false, state, "Failed to start speech.")
        }

        for ((index, chunk) in chunks.withIndex()) {
            val utteranceId = "ocr_${System.currentTimeMillis()}_$index"
            code = tts.speak(chunk, TextToSpeech.QUEUE_ADD, null, utteranceId)
            if (code != TextToSpeech.SUCCESS) {
                state = TextSpeaker.SpeakerState.ERROR
                return TextSpeaker.SpeakerResult(false, state, "Failed while queueing speech.")
            }
        }

        pendingText = clean
        state = TextSpeaker.SpeakerState.SPEAKING
        return TextSpeaker.SpeakerResult(true, state)
    }

    override fun pause(): TextSpeaker.SpeakerResult {
        val tts = engine
        if (tts == null) {
            state = TextSpeaker.SpeakerState.ERROR
            return TextSpeaker.SpeakerResult(false, state, "TTS engine is not available.")
        }

        tts.stop()
        state = TextSpeaker.SpeakerState.PAUSED
        return TextSpeaker.SpeakerResult(true, state)
    }

    override fun stop(): TextSpeaker.SpeakerResult {
        val tts = engine
        if (tts == null) {
            state = TextSpeaker.SpeakerState.ERROR
            return TextSpeaker.SpeakerResult(false, state, "TTS engine is not available.")
        }

        tts.stop()
        pendingText = null
        state = TextSpeaker.SpeakerState.STOPPED
        return TextSpeaker.SpeakerResult(true, state)
    }

    override fun setSpeechRate(rate: Float): TextSpeaker.SpeakerResult {
        speechRate = rate.coerceIn(0.5f, 2.0f)
        val tts = engine ?: return TextSpeaker.SpeakerResult(false, state, "TTS engine is not available.")
        val ok = tts.setSpeechRate(speechRate)
        return TextSpeaker.SpeakerResult(ok == TextToSpeech.SUCCESS, state)
    }

    override fun setPitch(pitch: Float): TextSpeaker.SpeakerResult {
        this.pitch = pitch.coerceIn(0.5f, 2.0f)
        val tts = engine ?: return TextSpeaker.SpeakerResult(false, state, "TTS engine is not available.")
        val ok = tts.setPitch(this.pitch)
        return TextSpeaker.SpeakerResult(ok == TextToSpeech.SUCCESS, state)
    }

    override fun setLanguage(languageTag: String): TextSpeaker.SpeakerResult {
        val tag = languageTag.ifBlank { defaultLocale.toLanguageTag() }
        this.languageTag = tag
        val tts = engine ?: return TextSpeaker.SpeakerResult(false, state, "TTS engine is not available.")
        val locale = Locale.forLanguageTag(tag)
        val result = tts.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            return TextSpeaker.SpeakerResult(false, state, "Language not supported on this device.")
        }
        return TextSpeaker.SpeakerResult(true, state)
    }

    override fun setVoice(voiceName: String?): TextSpeaker.SpeakerResult {
        selectedVoiceName = voiceName
        val tts = engine ?: return TextSpeaker.SpeakerResult(false, state, "TTS engine is not available.")
        if (voiceName.isNullOrBlank()) {
            return TextSpeaker.SpeakerResult(true, state)
        }

        val voice = tts.voices?.firstOrNull { it.name == voiceName }
            ?: return TextSpeaker.SpeakerResult(false, state, "Voice not found.")

        tts.voice = voice
        return TextSpeaker.SpeakerResult(true, state)
    }

    override fun getAvailableVoices(): List<TextSpeaker.SpeakerVoice> {
        val tts = engine ?: return emptyList()
        return tts.voices
            ?.map { voice ->
                val tag = voice.locale?.toLanguageTag().orEmpty()
                TextSpeaker.SpeakerVoice(
                    name = voice.name,
                    languageTag = tag,
                    displayLabel = "${voice.name} ($tag)"
                )
            }
            ?.sortedBy { it.displayLabel.lowercase(Locale.getDefault()) }
            .orEmpty()
    }

    override fun getAvailableLanguageTags(): List<String> {
        val tts = engine ?: return listOf(defaultLocale.toLanguageTag())
        val tags = tts.voices
            ?.mapNotNull { it.locale?.toLanguageTag() }
            ?.filter { it.isNotBlank() }
            ?.toMutableSet()
            ?: mutableSetOf()
        tags.add(defaultLocale.toLanguageTag())
        return tags.sorted()
    }

    override fun shutdown() {
        engine?.stop()
        engine?.shutdown()
        engine = null
        state = TextSpeaker.SpeakerState.STOPPED
    }

    private fun applySpeakerSettings(tts: TextToSpeech) {
        tts.setSpeechRate(speechRate)
        tts.setPitch(pitch)
        setLanguage(languageTag)
        if (!selectedVoiceName.isNullOrBlank()) {
            setVoice(selectedVoiceName)
        }
    }

    private fun chunkText(text: String): List<String> {
        if (text.length <= MAX_CHUNK_CHARS) return listOf(text)

        val chunks = mutableListOf<String>()
        val paragraphParts = text.split(Regex("\\n\\s*\\n")).filter { it.isNotBlank() }

        for (part in paragraphParts) {
            val normalized = part.replace(Regex("\\s+"), " ").trim()
            if (normalized.isEmpty()) continue

            if (normalized.length <= MAX_CHUNK_CHARS) {
                chunks.add(normalized)
                continue
            }

            val sentences = normalized.split(Regex("(?<=[.!?])\\s+")).filter { it.isNotBlank() }
            var current = StringBuilder()
            for (sentence in sentences) {
                val extra = if (current.isEmpty()) sentence else " $sentence"
                if (current.length + extra.length <= MAX_CHUNK_CHARS) {
                    current.append(extra)
                } else {
                    if (current.isNotEmpty()) chunks.add(current.toString().trim())
                    current = StringBuilder(sentence)
                }
            }
            if (current.isNotEmpty()) chunks.add(current.toString().trim())
        }

        return chunks.filter { it.isNotBlank() }
    }
}
