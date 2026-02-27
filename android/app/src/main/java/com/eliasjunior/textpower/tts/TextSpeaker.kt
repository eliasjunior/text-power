package com.eliasjunior.textpower.tts

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import java.util.Locale

interface TextSpeaker {
    fun setProgressListener(listener: ProgressListener?)
    fun start(text: String, startOffset: Int = 0): SpeakerResult
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

    interface ProgressListener {
        fun onRangeSpoken(start: Int, endExclusive: Int)
    }
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
    private val mainHandler = Handler(Looper.getMainLooper())
    private var progressListener: TextSpeaker.ProgressListener? = null
    private val utteranceChunks = mutableMapOf<String, SpeechChunk>()

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
                    if (!utteranceId.isNullOrBlank()) {
                        utteranceChunks.remove(utteranceId)
                    }
                    if (utteranceChunks.isEmpty()) {
                        state = TextSpeaker.SpeakerState.STOPPED
                        pendingText = null
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    state = TextSpeaker.SpeakerState.ERROR
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    state = TextSpeaker.SpeakerState.ERROR
                }

                override fun onRangeStart(
                    utteranceId: String?,
                    start: Int,
                    end: Int,
                    frame: Int
                ) {
                    val id = utteranceId ?: return
                    val chunk = utteranceChunks[id] ?: return
                    val safeStart = start.coerceIn(0, chunk.text.length)
                    val safeEnd = end.coerceIn(safeStart, chunk.text.length)
                    if (safeEnd <= safeStart) return
                    val absoluteStart = chunk.start + safeStart
                    val absoluteEnd = chunk.start + safeEnd
                    mainHandler.post {
                        progressListener?.onRangeSpoken(absoluteStart, absoluteEnd)
                    }
                }
            })

            applySpeakerSettings(tts)
            state = TextSpeaker.SpeakerState.READY
        }
    }

    override fun start(text: String, startOffset: Int): TextSpeaker.SpeakerResult {
        if (text.isBlank()) {
            return TextSpeaker.SpeakerResult(
                success = false,
                state = state,
                message = "No text to speak."
            )
        }
        val safeStartOffset = startOffset.coerceIn(0, text.length)
        if (safeStartOffset >= text.length) {
            return TextSpeaker.SpeakerResult(
                success = false,
                state = state,
                message = "Already at end of text."
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

        val chunks = chunkText(text, safeStartOffset)
        if (chunks.isEmpty()) {
            return TextSpeaker.SpeakerResult(false, state, "No text to speak.")
        }

        utteranceChunks.clear()
        var code = tts.speak("", TextToSpeech.QUEUE_FLUSH, null, "ocr_flush_${System.currentTimeMillis()}")
        if (code != TextToSpeech.SUCCESS) {
            state = TextSpeaker.SpeakerState.ERROR
            return TextSpeaker.SpeakerResult(false, state, "Failed to start speech.")
        }

        val playId = System.currentTimeMillis()
        for ((index, chunk) in chunks.withIndex()) {
            val utteranceId = "ocr_${playId}_$index"
            utteranceChunks[utteranceId] = chunk
            code = tts.speak(chunk.text, TextToSpeech.QUEUE_ADD, null, utteranceId)
            if (code != TextToSpeech.SUCCESS) {
                state = TextSpeaker.SpeakerState.ERROR
                return TextSpeaker.SpeakerResult(false, state, "Failed while queueing speech.")
            }
        }

        pendingText = text
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
        utteranceChunks.clear()
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
        utteranceChunks.clear()
        pendingText = null
        state = TextSpeaker.SpeakerState.STOPPED
        return TextSpeaker.SpeakerResult(true, state)
    }

    override fun setProgressListener(listener: TextSpeaker.ProgressListener?) {
        progressListener = listener
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
        utteranceChunks.clear()
        progressListener = null
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

    private fun chunkText(text: String, startOffset: Int): List<SpeechChunk> {
        if (startOffset >= text.length) return emptyList()
        if (text.length - startOffset <= MAX_CHUNK_CHARS) {
            val chunkStart = firstNonWhitespaceIndex(text, startOffset, text.length) ?: return emptyList()
            val chunkEnd = lastNonWhitespaceExclusiveIndex(text, chunkStart, text.length)
            if (chunkEnd <= chunkStart) return emptyList()
            return listOf(
                SpeechChunk(
                    text = text.substring(chunkStart, chunkEnd),
                    start = chunkStart,
                    endExclusive = chunkEnd
                )
            )
        }

        val chunks = mutableListOf<SpeechChunk>()
        var cursor = startOffset
        while (cursor < text.length) {
            var end = (cursor + MAX_CHUNK_CHARS).coerceAtMost(text.length)
            if (end < text.length) {
                val breakAt = text.lastIndexOfAny(charArrayOf(' ', '\n', '\t'), end - 1)
                if (breakAt >= cursor + 200) {
                    end = breakAt + 1
                }
            }

            val absStart = firstNonWhitespaceIndex(text, cursor, end)
            if (absStart != null) {
                val absEnd = lastNonWhitespaceExclusiveIndex(text, absStart, end)
                if (absEnd > absStart) {
                    chunks.add(SpeechChunk(text.substring(absStart, absEnd), absStart, absEnd))
                }
            }

            cursor = end
        }

        return chunks
    }

    private fun firstNonWhitespaceIndex(text: String, start: Int, endExclusive: Int): Int? {
        for (index in start until endExclusive) {
            if (!text[index].isWhitespace()) return index
        }
        return null
    }

    private fun lastNonWhitespaceExclusiveIndex(text: String, start: Int, endExclusive: Int): Int {
        var last = endExclusive - 1
        while (last >= start && text[last].isWhitespace()) {
            last--
        }
        return last + 1
    }
}

private data class SpeechChunk(
    val text: String,
    val start: Int,
    val endExclusive: Int
)
