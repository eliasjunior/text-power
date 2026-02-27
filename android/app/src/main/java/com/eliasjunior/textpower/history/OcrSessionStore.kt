package com.eliasjunior.textpower.history

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class OcrSessionStore(private val context: Context) {
    private val rootDir = File(context.filesDir, "ocr_history").apply { mkdirs() }
    private val imagesDir = File(rootDir, "images").apply { mkdirs() }
    private val sessionsFile = File(rootDir, "sessions.json")

    fun loadSessions(): List<OcrSession> {
        if (!sessionsFile.exists()) return emptyList()
        val json = runCatching { sessionsFile.readText() }.getOrDefault("[]")
        val array = runCatching { JSONArray(json) }.getOrElse { JSONArray() }

        val sessions = mutableListOf<OcrSession>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val id = obj.optString("id", "")
            val timestamp = obj.optLong("timestampMs", 0L)
            val imageFileName = obj.optString("imageFileName", "")
            val extractedText = obj.optString("extractedText", "")
            if (id.isBlank() || imageFileName.isBlank() || extractedText.isBlank()) continue

            sessions.add(
                OcrSession(
                    id = id,
                    timestampMs = timestamp,
                    imageFileName = imageFileName,
                    extractedText = extractedText
                )
            )
        }

        return sessions.sortedByDescending { it.timestampMs }
    }

    fun saveSession(bitmap: Bitmap, extractedText: String): List<OcrSession> {
        val text = extractedText.trim()
        if (text.isBlank()) return loadSessions()

        val id = UUID.randomUUID().toString()
        val imageFileName = "$id.jpg"
        val imageFile = File(imagesDir, imageFileName)

        FileOutputStream(imageFile).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, stream)
        }

        val session = OcrSession(
            id = id,
            timestampMs = System.currentTimeMillis(),
            imageFileName = imageFileName,
            extractedText = text
        )

        val updated = (listOf(session) + loadSessions()).distinctBy { it.id }
        persistSessions(updated)
        return updated
    }

    fun loadSessionBitmap(session: OcrSession): Bitmap? {
        val imageFile = File(imagesDir, session.imageFileName)
        if (!imageFile.exists()) return null
        return BitmapFactory.decodeFile(imageFile.absolutePath)
    }

    fun deleteSession(sessionId: String): List<OcrSession> {
        val current = loadSessions()
        val target = current.find { it.id == sessionId }
        if (target != null) {
            File(imagesDir, target.imageFileName).delete()
        }
        val updated = current.filterNot { it.id == sessionId }
        persistSessions(updated)
        return updated
    }

    fun clearAllSessions(): List<OcrSession> {
        val current = loadSessions()
        for (session in current) {
            File(imagesDir, session.imageFileName).delete()
        }
        persistSessions(emptyList())
        return emptyList()
    }

    private fun persistSessions(sessions: List<OcrSession>) {
        val array = JSONArray()
        for (session in sessions) {
            array.put(
                JSONObject()
                    .put("id", session.id)
                    .put("timestampMs", session.timestampMs)
                    .put("imageFileName", session.imageFileName)
                    .put("extractedText", session.extractedText)
            )
        }
        sessionsFile.writeText(array.toString())
    }
}
