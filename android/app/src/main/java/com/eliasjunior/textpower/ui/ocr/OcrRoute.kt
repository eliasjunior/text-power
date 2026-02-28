package com.eliasjunior.textpower.ui.ocr

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.eliasjunior.textpower.history.OcrSession
import com.eliasjunior.textpower.history.OcrSessionStore
import com.eliasjunior.textpower.ocr.BitmapLoader
import com.eliasjunior.textpower.ocr.OcrImageProcessor
import com.eliasjunior.textpower.ocr.OcrProcessor
import com.eliasjunior.textpower.ocr.OcrTextFormatter.CleaningLevel
import com.eliasjunior.textpower.ocr.OcrTextFormatter
import com.eliasjunior.textpower.tts.AndroidTextSpeaker
import com.eliasjunior.textpower.tts.TextSpeaker
import com.eliasjunior.textpower.tts.TtsPreferences
import com.eliasjunior.textpower.tts.TtsPreferencesStore
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.google.mlkit.vision.text.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

@Composable
fun OcrRoute() {
    val context = LocalContext.current
    val activity = context.findActivity()

    val ocrProcessor = remember { OcrProcessor() }
    val ocrImageProcessor = remember { OcrImageProcessor() }
    val ocrTextFormatter = remember { OcrTextFormatter() }
    val bitmapLoader = remember(context) { BitmapLoader(context) }
    val sessionStore = remember(context) { OcrSessionStore(context) }
    val textSpeaker = remember(context) { AndroidTextSpeaker(context) }
    val ttsPreferencesStore = remember(context) { TtsPreferencesStore(context) }
    val scope = rememberCoroutineScope()

    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var loadedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var extractedText by remember { mutableStateOf("") }
    var sessions by remember { mutableStateOf<List<OcrSession>>(emptyList()) }
    var isProcessing by remember { mutableStateOf(false) }
    var processingStatus by remember { mutableStateOf<String?>(null) }
    var processingProgress by remember { mutableStateOf<Float?>(null) }
    var playbackStatus by remember { mutableStateOf<String?>(null) }
    var speechRate by remember { mutableStateOf(1.0f) }
    var pitch by remember { mutableStateOf(1.0f) }
    var selectedVoiceName by remember { mutableStateOf<String?>(null) }
    var voiceOptions by remember { mutableStateOf<List<SelectionOption>>(emptyList()) }
    var highlightedRange by remember { mutableStateOf<IntRange?>(null) }
    var resumeCharOffset by remember { mutableStateOf(0) }
    var resumeTextHash by remember { mutableStateOf("") }
    var resumePending by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var preprocessEnabled by remember { mutableStateOf(true) }
    var filterByBlocks by remember { mutableStateOf(true) }
    var cleaningLevel by remember { mutableStateOf(CleaningLevel.NORMAL) }
    var multiPageScanEnabled by remember { mutableStateOf(true) }

    val scannerOptions = remember(multiPageScanEnabled) {
        GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(if (multiPageScanEnabled) 10 else 1)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
    }
    val scanner = remember(scannerOptions) { GmsDocumentScanning.getClient(scannerOptions) }

    DisposableEffect(textSpeaker) {
        textSpeaker.setProgressListener(object : TextSpeaker.ProgressListener {
            override fun onRangeSpoken(start: Int, endExclusive: Int) {
                if (endExclusive <= start) return
                highlightedRange = start until endExclusive
                resumeCharOffset = endExclusive
            }
        })
        onDispose {
            val shouldResume = resumePending || isPaused || isPlaying
            ttsPreferencesStore.save(
                TtsPreferences(
                    speechRate = speechRate,
                    pitch = pitch,
                    languageTag = "",
                    voiceName = selectedVoiceName,
                    resumeCharOffset = resumeCharOffset,
                    resumeTextHash = if (shouldResume) resumeTextHash else "",
                    resumePending = shouldResume && resumeTextHash.isNotBlank()
                )
            )
            textSpeaker.setProgressListener(null)
            textSpeaker.shutdown()
        }
    }

    val persistTtsPreferences = {
        val shouldResume = resumePending || isPaused || isPlaying
        ttsPreferencesStore.save(
            TtsPreferences(
                speechRate = speechRate,
                pitch = pitch,
                languageTag = "",
                voiceName = selectedVoiceName,
                multiPageScanEnabled = multiPageScanEnabled,
                resumeCharOffset = resumeCharOffset,
                resumeTextHash = if (shouldResume) resumeTextHash else "",
                resumePending = shouldResume && resumeTextHash.isNotBlank()
            )
        )
    }

    val clearResumeState = {
        highlightedRange = null
        resumeCharOffset = 0
        resumeTextHash = ""
        resumePending = false
        isPaused = false
        isPlaying = false
    }

    val maybeRestoreResumeForText: (String) -> Unit = { text ->
        val normalized = text.trim()
        if (normalized.isBlank()) {
            clearResumeState()
            persistTtsPreferences()
        } else {
            val hash = textHash(normalized)
            if (resumePending && resumeTextHash.isNotBlank() && resumeTextHash == hash) {
                resumeCharOffset = resumeCharOffset.coerceIn(0, normalized.length)
                isPaused = true
                isPlaying = false
                playbackStatus = "Resume available from last position."
                persistTtsPreferences()
            } else {
                clearResumeState()
                persistTtsPreferences()
            }
        }
    }

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        imageUri = uri
        extractedText = ""
        clearResumeState()
        processingStatus = null
        processingProgress = null
        playbackStatus = null
        persistTtsPreferences()
    }

    val scannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult

        val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
        val scannedUris = scanResult?.pages?.map { it.imageUri }?.filterNotNull().orEmpty()
        imageUri = scannedUris.firstOrNull()
        extractedText = ""
        clearResumeState()
        playbackStatus = null
        persistTtsPreferences()

        if (scannedUris.isNotEmpty()) {
            scope.launch {
                isProcessing = true
                processingStatus = "Preparing OCR…"
                processingProgress = 0f
                val outputPages = mutableListOf<String>()
                try {
                    for ((index, uri) in scannedUris.withIndex()) {
                        val current = index + 1
                        val total = scannedUris.size
                        processingStatus = "Processing page $current/$total…"
                        processingProgress = index.toFloat() / total.toFloat()

                        val bitmap = withContext(Dispatchers.IO) { bitmapLoader.load(uri) }
                        val prepared = withContext(Dispatchers.Default) {
                            ocrImageProcessor.preprocessIfRequested(bitmap, preprocessEnabled)
                        }
                        val resultText = ocrProcessor.process(prepared).awaitResult()
                        val pageText = ocrTextFormatter.format(resultText, filterByBlocks, cleaningLevel)
                        processingProgress = current.toFloat() / total.toFloat()
                        if (pageText.isNotBlank()) {
                            val header = if (multiPageScanEnabled || scannedUris.size > 1) {
                                "Page ${index + 1}\n\n"
                                } else {
                                ""
                            }
                            outputPages.add(header + pageText)
                        }
                    }
                    extractedText = outputPages.joinToString("\n\n")
                    maybeRestoreResumeForText(extractedText)
                } catch (err: Exception) {
                    extractedText = ""
                    clearResumeState()
                    persistTtsPreferences()
                    Toast.makeText(
                        context,
                        "OCR failed: ${err.localizedMessage ?: "unknown error"}",
                        Toast.LENGTH_LONG
                    ).show()
                } finally {
                    processingStatus = null
                    processingProgress = null
                    isProcessing = false
                }
            }
        }
    }

    LaunchedEffect(imageUri) {
        val uri = imageUri ?: return@LaunchedEffect
        loadedBitmap = withContext(Dispatchers.IO) {
            runCatching { bitmapLoader.load(uri) }.getOrNull()
        }
    }

    LaunchedEffect(Unit) {
        sessions = withContext(Dispatchers.IO) {
            sessionStore.loadSessions()
        }

        val prefs = withContext(Dispatchers.IO) { ttsPreferencesStore.load() }
        speechRate = prefs.speechRate
        pitch = prefs.pitch
        selectedVoiceName = prefs.voiceName
        multiPageScanEnabled = prefs.multiPageScanEnabled
        resumeCharOffset = prefs.resumeCharOffset.coerceAtLeast(0)
        resumeTextHash = prefs.resumeTextHash
        resumePending = prefs.resumePending

        textSpeaker.setSpeechRate(speechRate)
        textSpeaker.setPitch(pitch)
        if (!selectedVoiceName.isNullOrBlank()) {
            textSpeaker.setVoice(selectedVoiceName)
        }

        voiceOptions = buildPreferredVoiceOptions(textSpeaker.getAvailableVoices())
    }

    OcrScreen(
        state = OcrUiState(
            previewBitmap = loadedBitmap?.asImageBitmap(),
            extractedText = extractedText,
            sessions = sessions,
            isProcessing = isProcessing,
            processingStatus = processingStatus,
            processingProgress = processingProgress,
            playbackStatus = playbackStatus,
            speechRate = speechRate,
            pitch = pitch,
            selectedVoiceName = selectedVoiceName,
            voiceOptions = voiceOptions,
            highlightedRange = highlightedRange,
            preprocessEnabled = preprocessEnabled,
            filterByBlocks = filterByBlocks,
            cleaningLevel = cleaningLevel,
            multiPageScanEnabled = multiPageScanEnabled
        ),
        onPickImage = {
            pickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        onScanDocument = {
            val host = activity
            if (host == null) {
                Toast.makeText(context, "Cannot start scanner from this context.", Toast.LENGTH_LONG).show()
                return@OcrScreen
            }

            scanner.getStartScanIntent(host)
                .addOnSuccessListener { intentSender ->
                    scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                }
                .addOnFailureListener { err ->
                    Toast.makeText(
                        context,
                        "Scanner unavailable: ${err.localizedMessage ?: "unknown error"}",
                        Toast.LENGTH_LONG
                    ).show()
                }
        },
        onRecognize = {
            val sourceBitmap = loadedBitmap
            if (sourceBitmap != null) {
                isProcessing = true
                processingStatus = "Processing page 1/1…"
                processingProgress = 0f

                scope.launch {
                    try {
                        val prepared = withContext(Dispatchers.Default) {
                            ocrImageProcessor.preprocessIfRequested(sourceBitmap, preprocessEnabled)
                        }
                        val resultText = ocrProcessor.process(prepared).awaitResult()
                        extractedText = ocrTextFormatter.format(resultText, filterByBlocks, cleaningLevel)
                        maybeRestoreResumeForText(extractedText)
                        playbackStatus = null
                    } catch (err: Exception) {
                        extractedText = ""
                        clearResumeState()
                        persistTtsPreferences()
                        Toast.makeText(
                            context,
                            "OCR failed: ${err.localizedMessage ?: "unknown error"}",
                            Toast.LENGTH_LONG
                        ).show()
                    } finally {
                        processingStatus = null
                        processingProgress = null
                        isProcessing = false
                    }
                }
            }
        },
        onPlayText = {
            val safeStartOffset = if (isPaused || resumePending) {
                resumeCharOffset.coerceIn(0, extractedText.length)
            } else {
                0
            }
            highlightedRange = null
            textSpeaker.setSpeechRate(speechRate)
            textSpeaker.setPitch(pitch)
            textSpeaker.setVoice(selectedVoiceName)
            val result = textSpeaker.start(extractedText, safeStartOffset)
            if (result.success) {
                val normalized = extractedText.trim()
                resumeTextHash = if (normalized.isBlank()) "" else textHash(normalized)
                resumePending = resumeTextHash.isNotBlank()
                isPaused = false
                isPlaying = true
                persistTtsPreferences()
            } else {
                isPlaying = false
            }
            playbackStatus = result.message ?: when (result.state) {
                TextSpeaker.SpeakerState.SPEAKING -> "Speaking…"
                TextSpeaker.SpeakerState.UNINITIALIZED -> "TTS is initializing."
                TextSpeaker.SpeakerState.ERROR -> "TTS failed to start."
                else -> "Playback not started."
            }
        },
        onPauseText = {
            val result = textSpeaker.pause()
            if (result.success) {
                val normalized = extractedText.trim()
                resumeTextHash = if (normalized.isBlank()) "" else textHash(normalized)
                resumePending = resumeTextHash.isNotBlank()
                isPaused = true
                isPlaying = false
                persistTtsPreferences()
            } else {
                isPlaying = false
            }
            playbackStatus = result.message ?: when (result.state) {
                TextSpeaker.SpeakerState.PAUSED -> "Paused."
                TextSpeaker.SpeakerState.ERROR -> "Pause failed."
                else -> "Paused."
            }
        },
        onStopText = {
            val result = textSpeaker.stop()
            clearResumeState()
            persistTtsPreferences()
            playbackStatus = result.message ?: when (result.state) {
                TextSpeaker.SpeakerState.STOPPED -> "Stopped."
                TextSpeaker.SpeakerState.ERROR -> "Stop failed."
                else -> "Stopped."
            }
        },
        onCopyText = {
            val text = extractedText.trim()
            if (text.isBlank()) return@OcrScreen
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("OCR Text", text))
            Toast.makeText(context, "Text copied.", Toast.LENGTH_SHORT).show()
        },
        onShareText = {
            val text = extractedText.trim()
            if (text.isBlank()) return@OcrScreen
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            val chooser = Intent.createChooser(shareIntent, "Share OCR text")
            context.startActivity(chooser)
        },
        onSaveSession = {
            val bitmap = loadedBitmap
            val text = extractedText.trim()
            if (bitmap == null || text.isBlank()) {
                Toast.makeText(context, "Nothing to save yet.", Toast.LENGTH_SHORT).show()
                return@OcrScreen
            }

            scope.launch(Dispatchers.IO) {
                val updated = sessionStore.saveSession(bitmap, text)
                withContext(Dispatchers.Main) {
                    sessions = updated
                    Toast.makeText(context, "Session saved.", Toast.LENGTH_SHORT).show()
                }
            }
        },
        onOpenSession = { session ->
            scope.launch(Dispatchers.IO) {
                val bitmap = sessionStore.loadSessionBitmap(session)
                withContext(Dispatchers.Main) {
                    imageUri = null
                    loadedBitmap = bitmap
                    extractedText = session.extractedText
                    maybeRestoreResumeForText(extractedText)
                }
            }
        },
        onDeleteSession = { session ->
            scope.launch(Dispatchers.IO) {
                val updated = sessionStore.deleteSession(session.id)
                withContext(Dispatchers.Main) {
                    sessions = updated
                    Toast.makeText(context, "Session deleted.", Toast.LENGTH_SHORT).show()
                }
            }
        },
        onClearHistory = {
            scope.launch(Dispatchers.IO) {
                val updated = sessionStore.clearAllSessions()
                withContext(Dispatchers.Main) {
                    sessions = updated
                    Toast.makeText(context, "History cleared.", Toast.LENGTH_SHORT).show()
                }
            }
        },
        onSetSpeechRate = { value ->
            speechRate = value
            textSpeaker.setSpeechRate(value)
            persistTtsPreferences()
        },
        onSetPitch = { value ->
            pitch = value
            textSpeaker.setPitch(value)
            persistTtsPreferences()
        },
        onSetVoiceName = { name ->
            selectedVoiceName = name
            val result = textSpeaker.setVoice(name)
            if (!result.success) {
                playbackStatus = result.message ?: "Voice change failed."
            }
            persistTtsPreferences()
        },
        onSetPreprocessEnabled = { preprocessEnabled = it },
        onSetFilterByBlocks = { filterByBlocks = it },
        onSetCleaningLevel = { cleaningLevel = it },
        onSetMultiPageScanEnabled = {
            multiPageScanEnabled = it
            persistTtsPreferences()
        }
    )
}

private fun buildPreferredVoiceOptions(
    voices: List<TextSpeaker.SpeakerVoice>
): List<SelectionOption> {
    if (voices.isEmpty()) return emptyList()

    val preferred = listOf(
        "en-US" to "English (US)",
        "en-GB" to "English (UK)",
        "en-IE" to "English (Ireland)",
        "pt-BR" to "Portuguese (Brazil)",
        "es-ES" to "Spanish (Spain)",
        "es-US" to "Spanish (US)"
    )

    val usedVoiceNames = mutableSetOf<String>()
    val options = mutableListOf<SelectionOption>()
    for ((tag, label) in preferred) {
        val voice = selectBestVoiceForTag(voices, tag, usedVoiceNames) ?: continue
        usedVoiceNames.add(voice.name)
        options.add(SelectionOption(id = voice.name, label = label))
    }
    return options
}

private fun selectBestVoiceForTag(
    voices: List<TextSpeaker.SpeakerVoice>,
    targetTag: String,
    usedVoiceNames: Set<String>
): TextSpeaker.SpeakerVoice? {
    val target = normalizeTag(targetTag)
    val targetLocale = Locale.forLanguageTag(target)
    val targetLang = targetLocale.language
    val targetCountry = targetLocale.country

    fun available(): List<TextSpeaker.SpeakerVoice> = voices.filterNot { usedVoiceNames.contains(it.name) }

    // 1) Exact canonical match.
    available().firstOrNull { normalizeTag(it.languageTag) == target }?.let { return it }

    // 2) Same language + country.
    available().firstOrNull { voice ->
        val locale = Locale.forLanguageTag(normalizeTag(voice.languageTag))
        locale.language.equals(targetLang, ignoreCase = true) &&
            locale.country.equals(targetCountry, ignoreCase = true)
    }?.let { return it }

    // 3) Same base language.
    available().firstOrNull { voice ->
        val locale = Locale.forLanguageTag(normalizeTag(voice.languageTag))
        locale.language.equals(targetLang, ignoreCase = true)
    }?.let { return it }

    return null
}

private fun normalizeTag(tag: String): String {
    return tag.trim().replace('_', '-').lowercase(Locale.ROOT)
}

private fun textHash(text: String): String {
    return text.hashCode().toUInt().toString(16)
}

private suspend fun com.google.android.gms.tasks.Task<Text>.awaitResult(): Text =
    suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result ->
            if (continuation.isActive) continuation.resume(result)
        }
        addOnFailureListener { err ->
            if (continuation.isActive) continuation.resumeWithException(err)
        }
        addOnCanceledListener {
            if (continuation.isActive) continuation.cancel()
        }
    }

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
