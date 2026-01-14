package com.commonknowledge.scribbletablet.data.service

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.commonknowledge.scribbletablet.data.model.AssetInput
import com.commonknowledge.scribbletablet.data.model.GenerateRequest
import com.commonknowledge.scribbletablet.data.model.GenerateResponse
import com.commonknowledge.scribbletablet.data.model.ViewportSize
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

sealed class GenerationError : Exception() {
    data object ImageEncodingFailed : GenerationError()
    data class NetworkError(override val message: String) : GenerationError()
    data class ServerError(val code: Int, val body: String) : GenerationError()
    data class DecodingError(override val message: String) : GenerationError()
}

class GenerationService(
    private val baseUrl: String = "https://scribble-backend-production.up.railway.app"
) {
    private val client = HttpClient(OkHttp) {
        engine {
            config {
                connectTimeout(60, TimeUnit.SECONDS)
                readTimeout(600, TimeUnit.SECONDS) // 10 minutes for long generation
                writeTimeout(60, TimeUnit.SECONDS)
            }
        }
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) {
                    Log.d("API", message)
                }
            }
            level = LogLevel.INFO
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 600_000 // 10 minutes
            connectTimeoutMillis = 60_000
            socketTimeoutMillis = 600_000
        }
    }

    suspend fun generate(
        snapshot: Bitmap,
        viewportSize: ViewportSize,
        assets: List<AssetInput>,
        editMode: Boolean = false
    ): GenerateResponse {
        val endpoint = "$baseUrl/v1/executeMagicInk"

        // Resize image if needed
        val resizedBitmap = compressBitmap(snapshot, 2048)

        // Convert to base64 PNG
        val outputStream = ByteArrayOutputStream()
        resizedBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        val imageBytes = outputStream.toByteArray()
        val base64String = "data:image/png;base64," + Base64.encodeToString(imageBytes, Base64.NO_WRAP)

        val request = GenerateRequest(
            imageBase64 = base64String,
            viewport = viewportSize,
            assets = assets.ifEmpty { null },
            editMode = if (editMode) true else null
        )

        Log.d("API", "POST $endpoint")
        Log.d("API", "Viewport: ${viewportSize.widthPx}x${viewportSize.heightPx}")
        Log.d("API", "Original image: ${snapshot.width}x${snapshot.height}")
        Log.d("API", "Resized image: ${resizedBitmap.width}x${resizedBitmap.height}")
        Log.d("API", "Image data size: ${imageBytes.size} bytes (${imageBytes.size / 1024}KB)")
        Log.d("API", "Assets: ${assets.size}")

        return try {
            val response: GenerateResponse = client.post(endpoint) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body()

            Log.d("API", "Success! Generation ID: ${response.generationId ?: "none"}")
            Log.d("API", "Received ${response.actions.size} actions")

            response.actions.forEachIndexed { index, action ->
                val boxInfo = action.box?.let { "(${it.x}, ${it.y}) size (${it.w}x${it.h})" } ?: ""
                Log.d("API", "  [$index] ${action.type} $boxInfo")
            }

            response
        } catch (e: Exception) {
            Log.e("API", "Error: ${e.message}", e)
            throw when (e) {
                is GenerationError -> e
                else -> GenerationError.NetworkError(e.message ?: "Unknown error")
            }
        }
    }

    private fun compressBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
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
