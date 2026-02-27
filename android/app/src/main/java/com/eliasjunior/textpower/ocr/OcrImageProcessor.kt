package com.eliasjunior.textpower.ocr

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min

class OcrImageProcessor {
    fun preprocessIfRequested(source: Bitmap, enabled: Boolean): Bitmap {
        if (!enabled) return source
        return grayscaleAndAdaptiveThreshold(source)
    }

    private fun grayscaleAndAdaptiveThreshold(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        val gray = IntArray(width * height)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            gray[i] = ((r * 299 + g * 587 + b * 114) / 1000)
        }

        val integral = LongArray((width + 1) * (height + 1))
        for (y in 1..height) {
            var rowSum = 0L
            for (x in 1..width) {
                rowSum += gray[(y - 1) * width + (x - 1)].toLong()
                integral[y * (width + 1) + x] = integral[(y - 1) * (width + 1) + x] + rowSum
            }
        }

        val output = IntArray(width * height)
        val window = max(15, min(width, height) / 12)
        val half = window / 2
        val thresholdBias = 0.85f

        for (y in 0 until height) {
            val y0 = max(0, y - half)
            val y1 = min(height - 1, y + half)
            for (x in 0 until width) {
                val x0 = max(0, x - half)
                val x1 = min(width - 1, x + half)

                val area = (x1 - x0 + 1) * (y1 - y0 + 1)
                val sum = rectSum(integral, width, x0, y0, x1, y1)
                val localMean = (sum / area).toInt()
                val g = gray[y * width + x]
                val bw = if (g < localMean * thresholdBias) 0 else 255
                output[y * width + x] = (0xFF shl 24) or (bw shl 16) or (bw shl 8) or bw
            }
        }

        return Bitmap.createBitmap(output, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun rectSum(integral: LongArray, width: Int, x0: Int, y0: Int, x1: Int, y1: Int): Long {
        val w = width + 1
        val a = integral[y0 * w + x0]
        val b = integral[y0 * w + (x1 + 1)]
        val c = integral[(y1 + 1) * w + x0]
        val d = integral[(y1 + 1) * w + (x1 + 1)]
        return d - b - c + a
    }
}
