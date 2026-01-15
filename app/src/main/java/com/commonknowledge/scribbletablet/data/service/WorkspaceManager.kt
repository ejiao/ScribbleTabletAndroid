package com.commonknowledge.scribbletablet.data.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.commonknowledge.scribbletablet.data.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Singleton service managing canvas document persistence.
 * Stores canvases as JSON files in the app's internal storage.
 */
class WorkspaceManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "WorkspaceManager"
        private const val CANVASES_DIR = "canvases"
        private const val THUMBNAILS_DIR = "thumbnails"
        private const val INDEX_FILE = "index.json"
        private const val THUMBNAIL_QUALITY = 80

        @Volatile
        private var instance: WorkspaceManager? = null

        fun getInstance(context: Context): WorkspaceManager {
            return instance ?: synchronized(this) {
                instance ?: WorkspaceManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    private val canvasesDir: File
        get() = File(context.filesDir, CANVASES_DIR).also { it.mkdirs() }

    private val thumbnailsDir: File
        get() = File(context.filesDir, THUMBNAILS_DIR).also { it.mkdirs() }

    private val indexFile: File
        get() = File(canvasesDir, INDEX_FILE)

    /**
     * Get list of all canvas metadata, sorted by most recently modified.
     */
    fun listCanvases(): List<CanvasMetadata> {
        return try {
            if (indexFile.exists()) {
                val index = json.decodeFromString<CanvasIndex>(indexFile.readText())
                index.canvases.sortedByDescending { it.modifiedAt }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load canvas index", e)
            emptyList()
        }
    }

    /**
     * Create a new canvas with a unique title.
     */
    fun createCanvas(): CanvasDocument {
        val existingTitles = listCanvases().map { it.title }.toSet()
        var number = 1
        var title = "Canvas $number"
        while (existingTitles.contains(title)) {
            number++
            title = "Canvas $number"
        }

        val document = CanvasDocument(
            id = UUID.randomUUID(),
            title = title,
            createdAt = System.currentTimeMillis(),
            modifiedAt = System.currentTimeMillis()
        )

        saveCanvas(document)
        return document
    }

    /**
     * Save a canvas document and update the index.
     */
    fun saveCanvas(document: CanvasDocument) {
        try {
            // Save canvas data
            val canvasFile = File(canvasesDir, "${document.id}.json")
            val data = document.toSerializableData()
            canvasFile.writeText(json.encodeToString(data))

            // Update index
            updateIndex(document)

            Log.d(TAG, "Saved canvas: ${document.title} (${document.id})")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save canvas", e)
        }
    }

    /**
     * Load a canvas document by ID.
     */
    fun loadCanvas(id: UUID): CanvasDocument? {
        return try {
            val canvasFile = File(canvasesDir, "$id.json")
            if (canvasFile.exists()) {
                val data = json.decodeFromString<CanvasDocumentData>(canvasFile.readText())
                data.toDocument().also {
                    Log.d(TAG, "Loaded canvas: ${it.title} (${it.id})")
                }
            } else {
                Log.w(TAG, "Canvas file not found: $id")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load canvas: $id", e)
            null
        }
    }

    /**
     * Delete a canvas and its thumbnail.
     */
    fun deleteCanvas(id: UUID): Boolean {
        return try {
            // Don't delete if it's the last canvas
            val canvases = listCanvases()
            if (canvases.size <= 1) {
                Log.w(TAG, "Cannot delete the last canvas")
                return false
            }

            // Delete canvas file
            val canvasFile = File(canvasesDir, "$id.json")
            canvasFile.delete()

            // Delete thumbnail
            val thumbnailFile = File(thumbnailsDir, "$id.jpg")
            thumbnailFile.delete()

            // Update index
            removeFromIndex(id)

            Log.d(TAG, "Deleted canvas: $id")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete canvas: $id", e)
            false
        }
    }

    /**
     * Rename a canvas.
     */
    fun renameCanvas(id: UUID, newTitle: String) {
        val document = loadCanvas(id) ?: return
        document.title = newTitle
        document.markModified()
        saveCanvas(document)
    }

    /**
     * Save a thumbnail for a canvas.
     */
    fun saveThumbnail(id: UUID, bitmap: Bitmap) {
        try {
            val thumbnailFile = File(thumbnailsDir, "$id.jpg")
            FileOutputStream(thumbnailFile).use { out ->
                // Scale down for thumbnail
                val scaledBitmap = scaleBitmap(bitmap, 400)
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, THUMBNAIL_QUALITY, out)
            }
            Log.d(TAG, "Saved thumbnail for canvas: $id")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save thumbnail: $id", e)
        }
    }

    /**
     * Load a thumbnail for a canvas.
     */
    fun loadThumbnail(id: UUID): Bitmap? {
        return try {
            val thumbnailFile = File(thumbnailsDir, "$id.jpg")
            if (thumbnailFile.exists()) {
                BitmapFactory.decodeFile(thumbnailFile.absolutePath)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load thumbnail: $id", e)
            null
        }
    }

    /**
     * Get or create the first canvas (for initial app launch).
     */
    fun getOrCreateFirstCanvas(): CanvasDocument {
        val canvases = listCanvases()
        return if (canvases.isNotEmpty()) {
            loadCanvas(UUID.fromString(canvases.first().id)) ?: createCanvas()
        } else {
            createCanvas()
        }
    }

    private fun updateIndex(document: CanvasDocument) {
        val canvases = listCanvases().toMutableList()
        val existingIndex = canvases.indexOfFirst { it.id == document.id.toString() }

        val metadata = CanvasMetadata(
            id = document.id.toString(),
            title = document.title,
            createdAt = document.createdAt,
            modifiedAt = document.modifiedAt
        )

        if (existingIndex >= 0) {
            canvases[existingIndex] = metadata
        } else {
            canvases.add(0, metadata)
        }

        saveIndex(canvases)
    }

    private fun removeFromIndex(id: UUID) {
        val canvases = listCanvases().filter { it.id != id.toString() }
        saveIndex(canvases)
    }

    private fun saveIndex(canvases: List<CanvasMetadata>) {
        try {
            val index = CanvasIndex(canvases)
            indexFile.writeText(json.encodeToString(index))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save index", e)
        }
    }

    private fun scaleBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        if (width <= maxDimension && height <= maxDimension) {
            return bitmap
        }

        val scale = if (width > height) {
            maxDimension.toFloat() / width
        } else {
            maxDimension.toFloat() / height
        }

        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
}
