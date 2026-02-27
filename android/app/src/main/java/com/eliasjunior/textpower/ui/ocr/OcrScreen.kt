package com.eliasjunior.textpower.ui.ocr

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp

data class OcrUiState(
    val previewBitmap: ImageBitmap? = null,
    val extractedText: String = "",
    val isProcessing: Boolean = false,
    val processingStatus: String? = null,
    val processingProgress: Float? = null,
    val preprocessEnabled: Boolean = true,
    val filterByBlocks: Boolean = true,
    val multiPageScanEnabled: Boolean = false
)

@Composable
fun OcrScreen(
    state: OcrUiState,
    onPickImage: () -> Unit,
    onScanDocument: () -> Unit,
    onRecognize: () -> Unit,
    onCopyText: () -> Unit,
    onShareText: () -> Unit,
    onSetPreprocessEnabled: (Boolean) -> Unit,
    onSetFilterByBlocks: (Boolean) -> Unit,
    onSetMultiPageScanEnabled: (Boolean) -> Unit
) {
    var readerFontSize by remember { mutableStateOf(22f) }
    val readerScroll = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Text Power (ML Kit)",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )

        Text(
            text = "Pick an image or scan document, then run text recognition.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onPickImage) { Text("Pick Image") }
            Button(onClick = onScanDocument, enabled = !state.isProcessing) { Text("Scan Document") }
            Button(
                onClick = onRecognize,
                enabled = state.previewBitmap != null && !state.isProcessing
            ) { Text("Recognize Text") }
        }

        OcrSwitchRow(
            label = "Preprocess (grayscale + adaptive threshold)",
            checked = state.preprocessEnabled,
            onCheckedChange = onSetPreprocessEnabled
        )

        OcrSwitchRow(
            label = "Filter using ML Kit blocks/lines",
            checked = state.filterByBlocks,
            onCheckedChange = onSetFilterByBlocks
        )

        OcrSwitchRow(
            label = "Multi-page scan mode",
            checked = state.multiPageScanEnabled,
            onCheckedChange = onSetMultiPageScanEnabled
        )

        state.previewBitmap?.let { bitmap ->
            OcrImagePreview(bitmap = bitmap)
        }

        if (state.isProcessing) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(state.processingStatus ?: "Running ML Kit OCR…")
            }
            state.processingProgress?.let { progress ->
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Text(text = "Readable Mode", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onCopyText,
                enabled = state.extractedText.isNotBlank()
            ) { Text("Copy") }
            Button(
                onClick = onShareText,
                enabled = state.extractedText.isNotBlank()
            ) { Text("Share") }
        }
        Text(
            text = "Font size",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = readerFontSize,
            onValueChange = { readerFontSize = it },
            valueRange = 16f..30f
        )
        Text(
            text = if (state.extractedText.isBlank()) "No text recognized yet." else state.extractedText,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFF3ECD8))
                .heightIn(min = 220.dp, max = 420.dp)
                .verticalScroll(readerScroll)
                .padding(16.dp),
            style = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = FontFamily.Serif,
                fontSize = readerFontSize.sp,
                lineHeight = (readerFontSize * 1.6f).sp
            ),
            color = Color(0xFF2B2B2B)
        )

        Spacer(modifier = Modifier.height(6.dp))
    }
}

@Composable
private fun OcrSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = checked, onCheckedChange = onCheckedChange)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun OcrImagePreview(bitmap: ImageBitmap) {
    Image(
        bitmap = bitmap,
        contentDescription = "OCR preview",
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x11000000)),
        contentScale = ContentScale.Fit
    )
}
