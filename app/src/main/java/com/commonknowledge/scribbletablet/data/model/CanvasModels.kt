package com.commonknowledge.scribbletablet.data.model

import android.graphics.Bitmap
import android.graphics.RectF
import java.util.UUID

enum class CardType {
    TEXT, IMAGE, WEB, VIDEO
}

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
