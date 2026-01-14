package com.commonknowledge.scribbletablet.viewmodel

import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.commonknowledge.scribbletablet.data.model.*
import com.commonknowledge.scribbletablet.data.service.GenerationService
import kotlinx.coroutines.launch
import java.util.UUID

class CanvasViewModel : ViewModel() {
    private val generationService = GenerationService()

    // Drawing state
    val permanentPaths = mutableStateListOf<DrawingPath>()
    val magicPaths = mutableStateListOf<DrawingPath>()
    var currentPath = mutableStateOf<DrawingPath?>(null)

    // Cards
    val cards = mutableStateListOf<CanvasCard>()

    // UI state
    var activeMode = mutableStateOf(ToolMode.PERMANENT_INK)
    var isGenerating = mutableStateOf(false)
    var errorMessage = mutableStateOf<String?>(null)

    // Canvas viewport
    var viewportRect = mutableStateOf(RectF(0f, 0f, 1000f, 1000f))

    // Snapshot callback - set by the canvas composable
    var snapshotCallback: (() -> Bitmap?)? = null

    fun selectPermanentInk() {
        activeMode.value = ToolMode.PERMANENT_INK
    }

    fun selectMagicInk() {
        activeMode.value = ToolMode.MAGIC_INK
    }

    fun selectEraser() {
        activeMode.value = ToolMode.ERASER
    }

    fun selectMoveMode() {
        activeMode.value = ToolMode.MOVE
    }

    fun startPath(x: Float, y: Float, pressure: Float = 1f) {
        val isMagic = activeMode.value == ToolMode.MAGIC_INK
        val color = if (isMagic) 0xFF4CAF50.toInt() else android.graphics.Color.BLACK

        currentPath.value = DrawingPath(
            points = mutableListOf(PathPoint(x, y, pressure)),
            isMagicInk = isMagic,
            color = color
        )
    }

    fun addToPath(x: Float, y: Float, pressure: Float = 1f) {
        currentPath.value?.points?.add(PathPoint(x, y, pressure))
    }

    fun endPath() {
        currentPath.value?.let { path ->
            if (path.points.size > 1) {
                if (path.isMagicInk) {
                    magicPaths.add(path)
                } else {
                    permanentPaths.add(path)
                }
            }
        }
        currentPath.value = null
    }

    fun erasePath(x: Float, y: Float, radius: Float = 30f) {
        // Check magic paths first
        val magicToRemove = magicPaths.filter { path ->
            path.points.any { point ->
                val dx = point.x - x
                val dy = point.y - y
                dx * dx + dy * dy < radius * radius
            }
        }
        magicPaths.removeAll(magicToRemove)

        // Then permanent paths
        val permanentToRemove = permanentPaths.filter { path ->
            path.points.any { point ->
                val dx = point.x - x
                val dy = point.y - y
                dx * dx + dy * dy < radius * radius
            }
        }
        permanentPaths.removeAll(permanentToRemove)
    }

    fun clearMagicInk() {
        magicPaths.clear()
    }

    fun clearAllCards() {
        cards.clear()
    }

    fun play() {
        if (isGenerating.value) return

        if (magicPaths.isEmpty()) {
            errorMessage.value = "Draw something with magic ink first"
            return
        }

        isGenerating.value = true

        viewModelScope.launch {
            try {
                val snapshot = snapshotCallback?.invoke()
                    ?: throw Exception("Could not capture canvas")

                val viewportSize = ViewportSize(
                    widthPx = viewportRect.value.width().toInt(),
                    heightPx = viewportRect.value.height().toInt()
                )

                val assets = collectVisibleAssets()

                val response = generationService.generate(
                    snapshot = snapshot,
                    viewportSize = viewportSize,
                    assets = assets,
                    editMode = false
                )

                processActions(response.actions, response.generationId)
                clearMagicInk()

            } catch (e: Exception) {
                Log.e("CanvasViewModel", "Generation error: ${e.message}", e)
                errorMessage.value = e.message ?: "An error occurred"
            } finally {
                isGenerating.value = false
            }
        }
    }

    private fun collectVisibleAssets(): List<AssetInput> {
        return cards.mapNotNull { card ->
            if (card.rect.intersects(
                    viewportRect.value.left,
                    viewportRect.value.top,
                    viewportRect.value.right,
                    viewportRect.value.bottom
                )
            ) {
                card.toAssetInput(viewportRect.value)
            } else null
        }
    }

    private fun processActions(actions: List<CanvasAction>, generationId: String?) {
        val genId = generationId ?: UUID.randomUUID().toString()
        val visibleRect = viewportRect.value

        Log.d("Cards", "Processing ${actions.size} actions")

        for (action in actions) {
            when (action.type) {
                ActionType.PLACE_TEXT -> {
                    val box = action.box ?: continue
                    val rect = RectF(
                        visibleRect.left + (box.x * visibleRect.width()).toFloat(),
                        visibleRect.top + (box.y * visibleRect.height()).toFloat(),
                        visibleRect.left + ((box.x + box.w) * visibleRect.width()).toFloat(),
                        visibleRect.top + ((box.y + box.h) * visibleRect.height()).toFloat()
                    )

                    Log.d("Cards", "Placing text at $rect")

                    cards.add(
                        CanvasCard(
                            type = CardType.TEXT,
                            rect = rect,
                            text = action.text,
                            generationId = genId,
                            sourceAssetIds = action.sourceAssetIds ?: emptyList(),
                            transformType = action.transformType
                        )
                    )
                }

                ActionType.PLACE_IMAGE -> {
                    val box = action.box ?: continue
                    val rect = RectF(
                        visibleRect.left + (box.x * visibleRect.width()).toFloat(),
                        visibleRect.top + (box.y * visibleRect.height()).toFloat(),
                        visibleRect.left + ((box.x + box.w) * visibleRect.width()).toFloat(),
                        visibleRect.top + ((box.y + box.h) * visibleRect.height()).toFloat()
                    )

                    Log.d("Cards", "Placing image at $rect")

                    if (action.imageUrl != null) {
                        cards.add(
                            CanvasCard(
                                type = CardType.IMAGE,
                                rect = rect,
                                imageUrl = action.imageUrl,
                                generationId = genId,
                                sourceAssetIds = action.sourceAssetIds ?: emptyList(),
                                transformType = action.transformType
                            )
                        )
                    } else if (action.prompt != null) {
                        // Show prompt as text placeholder
                        cards.add(
                            CanvasCard(
                                type = CardType.TEXT,
                                rect = rect,
                                text = "\uD83C\uDFA8 ${action.prompt}",
                                generationId = genId
                            )
                        )
                    }
                }

                ActionType.PLACE_WEB -> {
                    val box = action.box ?: continue
                    val rect = RectF(
                        visibleRect.left + (box.x * visibleRect.width()).toFloat(),
                        visibleRect.top + (box.y * visibleRect.height()).toFloat(),
                        visibleRect.left + ((box.x + box.w) * visibleRect.width()).toFloat(),
                        visibleRect.top + ((box.y + box.h) * visibleRect.height()).toFloat()
                    )

                    Log.d("Cards", "Placing web card at $rect")

                    action.html?.let { html ->
                        cards.add(
                            CanvasCard(
                                type = CardType.WEB,
                                rect = rect,
                                htmlContent = html,
                                generationId = genId,
                                sourceAssetIds = action.sourceAssetIds ?: emptyList(),
                                transformType = action.transformType
                            )
                        )
                    }
                }

                ActionType.PLACE_VIDEO -> {
                    val box = action.box ?: continue
                    val rect = RectF(
                        visibleRect.left + (box.x * visibleRect.width()).toFloat(),
                        visibleRect.top + (box.y * visibleRect.height()).toFloat(),
                        visibleRect.left + ((box.x + box.w) * visibleRect.width()).toFloat(),
                        visibleRect.top + ((box.y + box.h) * visibleRect.height()).toFloat()
                    )

                    action.videoUrl?.let { videoUrl ->
                        cards.add(
                            CanvasCard(
                                type = CardType.VIDEO,
                                rect = rect,
                                videoUrl = videoUrl,
                                generationId = genId
                            )
                        )
                    }
                }

                ActionType.MODIFY_ASSET -> {
                    val targetId = action.targetAssetId ?: continue
                    val cardIndex = cards.indexOfFirst { it.assetId == targetId }
                    if (cardIndex >= 0) {
                        val card = cards[cardIndex]
                        when {
                            action.html != null -> {
                                cards[cardIndex] = card.copy(
                                    type = CardType.WEB,
                                    htmlContent = action.html,
                                    text = null,
                                    imageUrl = null
                                )
                            }
                            action.text != null -> {
                                cards[cardIndex] = card.copy(
                                    type = CardType.TEXT,
                                    text = action.text,
                                    htmlContent = null,
                                    imageUrl = null
                                )
                            }
                            action.imageUrl != null -> {
                                cards[cardIndex] = card.copy(
                                    type = CardType.IMAGE,
                                    imageUrl = action.imageUrl,
                                    text = null,
                                    htmlContent = null
                                )
                            }
                        }
                    }
                }

                ActionType.DELETE_ASSET -> {
                    val targetId = action.targetAssetId ?: continue
                    cards.removeAll { it.assetId == targetId }
                }
            }
        }
    }

    fun dismissError() {
        errorMessage.value = null
    }

    fun removeCard(cardId: UUID) {
        cards.removeAll { it.id == cardId }
    }
}
