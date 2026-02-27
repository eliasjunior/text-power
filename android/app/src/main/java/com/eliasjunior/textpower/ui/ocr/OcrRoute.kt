package com.eliasjunior.textpower.ui.ocr

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.eliasjunior.textpower.ocr.BitmapLoader
import com.eliasjunior.textpower.ocr.OcrImageProcessor
import com.eliasjunior.textpower.ocr.OcrProcessor
import com.eliasjunior.textpower.ocr.OcrTextFormatter
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.google.mlkit.vision.text.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val scope = rememberCoroutineScope()

    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var loadedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var extractedText by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    var preprocessEnabled by remember { mutableStateOf(true) }
    var filterByBlocks by remember { mutableStateOf(true) }
    var multiPageScanEnabled by remember { mutableStateOf(false) }

    val scannerOptions = remember(multiPageScanEnabled) {
        GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(if (multiPageScanEnabled) 10 else 1)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
    }
    val scanner = remember(scannerOptions) { GmsDocumentScanning.getClient(scannerOptions) }

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        imageUri = uri
        extractedText = ""
    }

    val scannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult

        val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
        val scannedUris = scanResult?.pages?.map { it.imageUri }?.filterNotNull().orEmpty()
        imageUri = scannedUris.firstOrNull()
        extractedText = ""

        if (scannedUris.isNotEmpty()) {
            scope.launch {
                isProcessing = true
                val outputPages = mutableListOf<String>()
                try {
                    for ((index, uri) in scannedUris.withIndex()) {
                        val bitmap = withContext(Dispatchers.IO) { bitmapLoader.load(uri) }
                        val prepared = withContext(Dispatchers.Default) {
                            ocrImageProcessor.preprocessIfRequested(bitmap, preprocessEnabled)
                        }
                        val resultText = ocrProcessor.process(prepared).awaitResult()
                        val pageText = ocrTextFormatter.format(resultText, filterByBlocks)
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
                } catch (err: Exception) {
                    extractedText = ""
                    Toast.makeText(
                        context,
                        "OCR failed: ${err.localizedMessage ?: "unknown error"}",
                        Toast.LENGTH_LONG
                    ).show()
                } finally {
                    isProcessing = false
                }
            }
        }
    }

    LaunchedEffect(imageUri) {
        loadedBitmap = if (imageUri == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching { bitmapLoader.load(imageUri!!) }.getOrNull()
            }
        }
    }

    OcrScreen(
        state = OcrUiState(
            previewBitmap = loadedBitmap?.asImageBitmap(),
            extractedText = extractedText,
            isProcessing = isProcessing,
            preprocessEnabled = preprocessEnabled,
            filterByBlocks = filterByBlocks,
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

                scope.launch {
                    try {
                        val prepared = withContext(Dispatchers.Default) {
                            ocrImageProcessor.preprocessIfRequested(sourceBitmap, preprocessEnabled)
                        }
                        val resultText = ocrProcessor.process(prepared).awaitResult()
                        extractedText = ocrTextFormatter.format(resultText, filterByBlocks)
                    } catch (err: Exception) {
                        extractedText = ""
                        Toast.makeText(
                            context,
                            "OCR failed: ${err.localizedMessage ?: "unknown error"}",
                            Toast.LENGTH_LONG
                        ).show()
                    } finally {
                        isProcessing = false
                    }
                }
            }
        },
        onSetPreprocessEnabled = { preprocessEnabled = it },
        onSetFilterByBlocks = { filterByBlocks = it },
        onSetMultiPageScanEnabled = { multiPageScanEnabled = it }
    )
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
