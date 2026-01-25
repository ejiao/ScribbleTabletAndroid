package com.commonknowledge.scribbletablet.data.service

import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Service for uploading media files to Supabase storage.
 * Matches iOS StorageService behavior.
 */
class StorageService private constructor() {

    companion object {
        private const val TAG = "StorageService"

        // Supabase configuration - should be moved to BuildConfig or secrets
        // TODO: Replace with actual Supabase credentials
        private const val SUPABASE_URL = "https://your-project.supabase.co"
        private const val SUPABASE_KEY = "your-supabase-anon-key"
        private const val BUCKET_NAME = "videos"

        @Volatile
        private var instance: StorageService? = null

        fun getInstance(): StorageService {
            return instance ?: synchronized(this) {
                instance ?: StorageService().also { instance = it }
            }
        }
    }

    /**
     * Upload a video file to Supabase storage.
     * @param file Local video file
     * @param fileName Optional custom file name
     * @return Public URL of the uploaded video
     */
    suspend fun uploadVideo(file: File, fileName: String? = null): String = withContext(Dispatchers.IO) {
        val ext = file.extension.lowercase().ifEmpty { "mp4" }
        val uniqueName = fileName ?: "${UUID.randomUUID()}.$ext"
        val storagePath = "videos/$uniqueName"

        val contentType = when (ext) {
            "mp4" -> "video/mp4"
            "mov" -> "video/quicktime"
            "m4v" -> "video/x-m4v"
            "webm" -> "video/webm"
            else -> "video/mp4"
        }

        val videoData = file.readBytes()
        Log.d(TAG, "Uploading video to $storagePath (${videoData.size} bytes)")

        return@withContext uploadFile(videoData, storagePath, contentType)
    }

    /**
     * Upload an audio file to Supabase storage.
     * @param file Local audio file
     * @param fileName Optional custom file name
     * @return Public URL of the uploaded audio
     */
    suspend fun uploadAudio(file: File, fileName: String? = null): String = withContext(Dispatchers.IO) {
        val ext = file.extension.lowercase().ifEmpty { "m4a" }
        val uniqueName = fileName ?: "${UUID.randomUUID()}.$ext"
        val storagePath = "audio/$uniqueName"

        val contentType = when (ext) {
            "m4a" -> "audio/mp4"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "aac" -> "audio/aac"
            "ogg" -> "audio/ogg"
            else -> "audio/mp4"
        }

        val audioData = file.readBytes()
        Log.d(TAG, "Uploading audio to $storagePath (${audioData.size} bytes)")

        return@withContext uploadFile(audioData, storagePath, contentType)
    }

    /**
     * Upload an image to Supabase storage.
     * @param bitmap Image to upload
     * @param fileName Optional custom file name
     * @param quality JPEG quality (0-100)
     * @return Public URL of the uploaded image
     */
    suspend fun uploadImage(bitmap: Bitmap, fileName: String? = null, quality: Int = 80): String = withContext(Dispatchers.IO) {
        val uniqueName = fileName ?: "${UUID.randomUUID()}.jpg"
        val storagePath = "images/$uniqueName"

        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        val imageData = outputStream.toByteArray()

        Log.d(TAG, "Uploading image to $storagePath (${imageData.size} bytes)")

        return@withContext uploadFile(imageData, storagePath, "image/jpeg")
    }

    private fun uploadFile(data: ByteArray, storagePath: String, contentType: String): String {
        val uploadUrl = URL("$SUPABASE_URL/storage/v1/object/$BUCKET_NAME/$storagePath")

        val connection = uploadUrl.openConnection() as HttpURLConnection
        connection.apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 120_000
            readTimeout = 300_000

            setRequestProperty("Authorization", "Bearer $SUPABASE_KEY")
            setRequestProperty("apikey", SUPABASE_KEY)
            setRequestProperty("Content-Type", contentType)
            setRequestProperty("x-upsert", "true")
        }

        try {
            connection.outputStream.use { output ->
                output.write(data)
            }

            val responseCode = connection.responseCode
            val responseMessage = if (responseCode in 200..299) {
                connection.inputStream.bufferedReader().readText()
            } else {
                connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
            }

            Log.d(TAG, "Response $responseCode: ${responseMessage.take(200)}")

            if (responseCode !in 200..299) {
                throw StorageException.UploadFailed(responseCode, responseMessage)
            }

            val publicUrl = "$SUPABASE_URL/storage/v1/object/public/$BUCKET_NAME/$storagePath"
            Log.d(TAG, "File uploaded successfully: $publicUrl")
            return publicUrl

        } finally {
            connection.disconnect()
        }
    }
}

sealed class StorageException(message: String) : Exception(message) {
    object EncodingFailed : StorageException("Failed to encode file for upload")
    object InvalidResponse : StorageException("Invalid response from storage server")
    class UploadFailed(code: Int, message: String) : StorageException("Upload failed ($code): ${message.take(100)}")
}
