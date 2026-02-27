package com.eliasjunior.textpower.ui.ocr

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eliasjunior.textpower.ocr.OcrScoredLine

data class OcrUiState(
    val previewBitmap: ImageBitmap? = null,
    val extractedText: String = "",
    val scoredLines: List<OcrScoredLine> = emptyList(),
    val isProcessing: Boolean = false,
    val preprocessEnabled: Boolean = true,
    val filterByBlocks: Boolean = true
)

@Composable
fun OcrScreen(
    state: OcrUiState,
    onPickImage: () -> Unit,
    onScanDocument: () -> Unit,
    onRecognize: () -> Unit,
    onSetPreprocessEnabled: (Boolean) -> Unit,
    onSetFilterByBlocks: (Boolean) -> Unit
) {
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

        state.previewBitmap?.let { bitmap ->
            OcrImagePreview(bitmap = bitmap)
        }

        if (state.isProcessing) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text("Running ML Kit OCR…")
            }
        }

        Text(text = "Recognized text", style = MaterialTheme.typography.titleMedium)
        if (state.scoredLines.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (line in state.scoredLines) {
                    ScoredLineRow(line = line)
                }
            }
        }
        Text(
            text = if (state.extractedText.isBlank()) "No text recognized yet." else state.extractedText,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(12.dp),
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(6.dp))
    }
}

@Composable
private fun ScoredLineRow(line: OcrScoredLine) {
    val badgeColor = when (line.qualityLabel) {
        "High" -> Color(0xFF2E7D32)
        "Medium" -> Color(0xFFF9A825)
        else -> Color(0xFFC62828)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${line.qualityLabel} ${line.qualityScore}%",
            color = Color.White,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(badgeColor)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = line.text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
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
