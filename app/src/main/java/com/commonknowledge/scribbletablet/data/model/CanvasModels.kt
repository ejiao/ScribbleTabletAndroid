package com.commonknowledge.scribbletablet.data.model

import android.graphics.Bitmap
import android.graphics.RectF
import kotlinx.serialization.Serializable
import java.util.UUID

enum class CardType {
    TEXT, IMAGE, WEB, VIDEO
}

// Canvas Document for persistence
data class CanvasDocument(
    val id: UUID = UUID.randomUUID(),
    var title: String = "Untitled Canvas",
    var createdAt: Long = System.currentTimeMillis(),
    var modifiedAt: Long = System.currentTimeMillis(),
    var zoomScale: Float = 1f,
    var contentOffsetX: Float = 0f,
    var contentOffsetY: Float = 0f,
    var permanentPaths: List<DrawingPath> = emptyList(),
    var magicPaths: List<DrawingPath> = emptyList(),
    var cards: List<CanvasCard> = emptyList(),
    var thumbnail: Bitmap? = null
) {
    fun markModified() {
        modifiedAt = System.currentTimeMillis()
    }
}

// Serializable data for JSON persistence
@Serializable
data class CanvasDocumentData(
    val id: String,
    val title: String,
    val createdAt: Long,
    val modifiedAt: Long,
    val zoomScale: Float,
    val contentOffsetX: Float,
    val contentOffsetY: Float,
    val permanentPaths: List<SerializablePath>,
    val magicPaths: List<SerializablePath>,
    val cards: List<SerializableCard>
)

@Serializable
data class SerializablePath(
    val points: List<SerializablePoint>,
    val isMagicInk: Boolean,
    val strokeWidth: Float,
    val color: Int
)

@Serializable
data class SerializablePoint(
    val x: Float,
    val y: Float,
    val pressure: Float
)

@Serializable
data class SerializableCard(
    val id: String,
    val type: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val text: String? = null,
    val imageUrl: String? = null,
    val videoUrl: String? = null,
    val htmlContent: String? = null,
    val generationId: String? = null,
    val assetId: String? = null
)

// Lightweight metadata for menu listing
@Serializable
data class CanvasMetadata(
    val id: String,
    val title: String,
    val createdAt: Long,
    val modifiedAt: Long
)

// Index file for all canvases
@Serializable
data class CanvasIndex(
    val canvases: List<CanvasMetadata>
)

// Extension functions to convert between models
fun DrawingPath.toSerializable() = SerializablePath(
    points = points.map { SerializablePoint(it.x, it.y, it.pressure) },
    isMagicInk = isMagicInk,
    strokeWidth = strokeWidth,
    color = color
)

fun SerializablePath.toDrawingPath() = DrawingPath(
    points = points.map { PathPoint(it.x, it.y, it.pressure) }.toMutableList(),
    isMagicInk = isMagicInk,
    strokeWidth = strokeWidth,
    color = color
)

fun CanvasCard.toSerializable() = SerializableCard(
    id = id.toString(),
    type = type.name,
    left = rect.left,
    top = rect.top,
    right = rect.right,
    bottom = rect.bottom,
    text = text,
    imageUrl = imageUrl,
    videoUrl = videoUrl,
    htmlContent = htmlContent,
    generationId = generationId,
    assetId = assetId
)

fun SerializableCard.toCanvasCard() = CanvasCard(
    id = UUID.fromString(id),
    type = CardType.valueOf(type),
    rect = RectF(left, top, right, bottom),
    text = text,
    imageUrl = imageUrl,
    videoUrl = videoUrl,
    htmlContent = htmlContent,
    generationId = generationId,
    assetId = assetId ?: "asset_${id.take(8)}"
)

fun CanvasDocument.toSerializableData() = CanvasDocumentData(
    id = id.toString(),
    title = title,
    createdAt = createdAt,
    modifiedAt = modifiedAt,
    zoomScale = zoomScale,
    contentOffsetX = contentOffsetX,
    contentOffsetY = contentOffsetY,
    permanentPaths = permanentPaths.map { it.toSerializable() },
    magicPaths = magicPaths.map { it.toSerializable() },
    cards = cards.map { it.toSerializable() }
)

fun CanvasDocumentData.toDocument() = CanvasDocument(
    id = UUID.fromString(id),
    title = title,
    createdAt = createdAt,
    modifiedAt = modifiedAt,
    zoomScale = zoomScale,
    contentOffsetX = contentOffsetX,
    contentOffsetY = contentOffsetY,
    permanentPaths = permanentPaths.map { it.toDrawingPath() },
    magicPaths = magicPaths.map { it.toDrawingPath() },
    cards = cards.map { it.toCanvasCard() }
)

data class CanvasCard(
    val id: UUID = UUID.randomUUID(),
    var type: CardType,
    var rect: RectF,
    var text: String? = null,
    var imageUrl: String? = null,
    var videoUrl: String? = null,
    var htmlContent: String? = null,
    var localBitmap: Bitmap? = null,
    var generationId: String? = null,
    var assetId: String = "asset_${id.toString().take(8)}",
    var sourceAssetIds: List<String> = emptyList(),
    var transformType: TransformType? = null
) {
    fun toAssetInput(visibleRect: RectF, imageData: String? = null): AssetInput? {
        val normalizedBox = NormalizedBox(
            x = ((rect.left - visibleRect.left) / visibleRect.width()).toDouble(),
            y = ((rect.top - visibleRect.top) / visibleRect.height()).toDouble(),
            w = (rect.width() / visibleRect.width()).toDouble(),
            h = (rect.height() / visibleRect.height()).toDouble()
        )

        return when (type) {
            CardType.IMAGE -> AssetInput(
                id = assetId,
                type = AssetType.IMAGE,
                content = imageData,
                url = imageUrl,
                box = normalizedBox
            )
            CardType.TEXT -> {
                val textContent = text
                if (textContent.isNullOrEmpty()) null
                else AssetInput(
                    id = assetId,
                    type = AssetType.TEXT,
                    content = textContent,
                    box = normalizedBox
                )
            }
            CardType.WEB -> {
                val html = htmlContent
                if (html.isNullOrEmpty()) null
                else AssetInput(
                    id = assetId,
                    type = AssetType.HTML,
                    content = html,
                    box = normalizedBox
                )
            }
            CardType.VIDEO -> AssetInput(
                id = assetId,
                type = AssetType.VIDEO,
                url = videoUrl,
                box = normalizedBox
            )
        }
    }
}

enum class ToolMode {
    PERMANENT_INK,
    MAGIC_INK,
    ERASER,
    MOVE
}

data class DrawingPath(
    val points: MutableList<PathPoint> = mutableListOf(),
    val isMagicInk: Boolean = false,
    val strokeWidth: Float = 5f,
    val color: Int = android.graphics.Color.BLACK
)

data class PathPoint(
    val x: Float,
    val y: Float,
    val pressure: Float = 1f
)
