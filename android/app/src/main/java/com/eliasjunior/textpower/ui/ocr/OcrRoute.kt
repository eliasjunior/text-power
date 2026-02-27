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
import com.eliasjunior.textpower.ocr.OcrScoredLine
import com.eliasjunior.textpower.ocr.OcrTextFormatter
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun OcrRoute() {
    val context = LocalContext.current
    val activity = context.findActivity()

    val ocrProcessor = remember { OcrProcessor() }
    val ocrImageProcessor = remember { OcrImageProcessor() }
    val ocrTextFormatter = remember { OcrTextFormatter() }
    val bitmapLoader = remember(context) { BitmapLoader(context) }
    val scope = rememberCoroutineScope()

    val scannerOptions = remember {
        GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(1)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
    }
    val scanner = remember { GmsDocumentScanning.getClient(scannerOptions) }

    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var loadedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var extractedText by remember { mutableStateOf("") }
    var scoredLines by remember { mutableStateOf<List<OcrScoredLine>>(emptyList()) }
    var isProcessing by remember { mutableStateOf(false) }
    var preprocessEnabled by remember { mutableStateOf(true) }
    var filterByBlocks by remember { mutableStateOf(true) }

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        imageUri = uri
        extractedText = ""
        scoredLines = emptyList()
    }

    val scannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult

        val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
        val scannedUri = scanResult?.pages?.firstOrNull()?.imageUri
        imageUri = scannedUri
        extractedText = ""
        scoredLines = emptyList()
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
            scoredLines = scoredLines,
            isProcessing = isProcessing,
            preprocessEnabled = preprocessEnabled,
            filterByBlocks = filterByBlocks
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
                    val prepared = withContext(Dispatchers.Default) {
                        ocrImageProcessor.preprocessIfRequested(sourceBitmap, preprocessEnabled)
                    }

                    ocrProcessor.process(prepared)
                        .addOnSuccessListener { result ->
                            val formatted = ocrTextFormatter.format(result, filterByBlocks)
                            extractedText = formatted.plainText
                            scoredLines = formatted.scoredLines
                            isProcessing = false
                        }
                        .addOnFailureListener { err ->
                            extractedText = ""
                            scoredLines = emptyList()
                            isProcessing = false
                            Toast.makeText(
                                context,
                                "OCR failed: ${err.localizedMessage ?: "unknown error"}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                }
            }
        },
        onSetPreprocessEnabled = { preprocessEnabled = it },
        onSetFilterByBlocks = { filterByBlocks = it }
    )
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
