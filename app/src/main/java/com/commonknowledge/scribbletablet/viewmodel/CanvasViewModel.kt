package com.commonknowledge.scribbletablet.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.commonknowledge.scribbletablet.data.model.*
import com.commonknowledge.scribbletablet.data.service.GenerationService
import com.commonknowledge.scribbletablet.data.service.WorkspaceManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

// Sealed class for undo/redo actions
sealed class UndoableAction {
    data class AddPermanentPath(val path: DrawingPath) : UndoableAction()
    data class AddMagicPath(val path: DrawingPath) : UndoableAction()
    data class ErasePaths(val permanentPaths: List<DrawingPath>, val magicPaths: List<DrawingPath>) : UndoableAction()
    data class AddCard(val card: CanvasCard) : UndoableAction()
    data class AddCards(val cards: List<CanvasCard>) : UndoableAction()
    data class RemoveCard(val card: CanvasCard) : UndoableAction()
}

class CanvasViewModel : ViewModel() {
    private val generationService = GenerationService()
    private var workspaceManager: WorkspaceManager? = null

    // Current canvas document
    private var currentDocument: CanvasDocument? = null
    val currentCanvasId = mutableStateOf<UUID?>(null)
    val currentCanvasTitle = mutableStateOf("Untitled Canvas")

    // Workspace menu
    val showingWorkspaceMenu = mutableStateOf(false)
    val canvasList = mutableStateListOf<CanvasMetadata>()
    val thumbnails = mutableStateOf<Map<String, Bitmap?>>(emptyMap())

    // Auto-save
    private var needsSave = false
    private var autoSaveJob: Job? = null
    private val autoSaveIntervalMs = 5000L

    // Drawing state
    val permanentPaths = mutableStateListOf<DrawingPath>()
    val magicPaths = mutableStateListOf<DrawingPath>()
    // Use neverEqualPolicy to always trigger recomposition when currentPath is assigned,
    // even if the object reference is the same. This avoids creating new objects for every point.
    var currentPath = mutableStateOf<DrawingPath?>(null, neverEqualPolicy())
    // Version counter to force recomposition without object allocation
    var currentPathVersion = mutableStateOf(0L)

    // Cards
    val cards = mutableStateListOf<CanvasCard>()

    // Undo/Redo stacks
    private val undoStack = mutableStateListOf<UndoableAction>()
    private val redoStack = mutableStateListOf<UndoableAction>()

    // Observable state for UI
    val canUndo = mutableStateOf(false)
    val canRedo = mutableStateOf(false)

    // UI state
    var activeMode = mutableStateOf(ToolMode.PERMANENT_INK)
    var isGenerating = mutableStateOf(false)
    var errorMessage = mutableStateOf<String?>(null)
    var selectedCardId = mutableStateOf<UUID?>(null)
    var isCardExpanded = mutableStateOf(false)
    var expandedCardId = mutableStateOf<UUID?>(null)

    // Magic paths drawn on expanded card (separate from main canvas magic paths)
    val expandedCardMagicPaths = mutableStateListOf<DrawingPath>()

    fun expandCard(cardId: UUID) {
        expandedCardId.value = cardId
        isCardExpanded.value = true
        expandedCardMagicPaths.clear() // Clear any previous expanded card paths
    }

    fun collapseExpandedCard() {
        expandedCardId.value = null
        isCardExpanded.value = false
        expandedCardMagicPaths.clear()
    }

    fun addExpandedCardMagicPath(path: DrawingPath) {
        expandedCardMagicPaths.add(path)
    }

    fun clearExpandedCardMagicPaths() {
        expandedCardMagicPaths.clear()
    }

    // Canvas viewport
    var viewportRect = mutableStateOf(RectF(0f, 0f, 1000f, 1000f))

    // Canvas transform state (shared between canvas and cards)
    var canvasScale = mutableStateOf(1f)
    var canvasOffsetX = mutableStateOf(0f)
    var canvasOffsetY = mutableStateOf(0f)

    // Snapshot callback - set by the canvas composable
    var snapshotCallback: (() -> Bitmap?)? = null

    private fun updateUndoRedoState() {
        canUndo.value = undoStack.isNotEmpty()
        canRedo.value = redoStack.isNotEmpty()
    }

    private fun pushUndo(action: UndoableAction) {
        undoStack.add(action)
        redoStack.clear()
        updateUndoRedoState()
    }

    fun undo() {
        if (undoStack.isEmpty()) return

        val action = undoStack.removeLast()
        when (action) {
            is UndoableAction.AddPermanentPath -> {
                permanentPaths.remove(action.path)
                redoStack.add(action)
            }
            is UndoableAction.AddMagicPath -> {
                magicPaths.remove(action.path)
                redoStack.add(action)
            }
            is UndoableAction.ErasePaths -> {
                permanentPaths.addAll(action.permanentPaths)
                magicPaths.addAll(action.magicPaths)
                redoStack.add(action)
            }
            is UndoableAction.AddCard -> {
                cards.remove(action.card)
                redoStack.add(action)
            }
            is UndoableAction.AddCards -> {
                action.cards.forEach { cards.remove(it) }
                redoStack.add(action)
            }
            is UndoableAction.RemoveCard -> {
                cards.add(action.card)
                redoStack.add(action)
            }
        }
        updateUndoRedoState()
    }

    fun redo() {
        if (redoStack.isEmpty()) return

        val action = redoStack.removeLast()
        when (action) {
            is UndoableAction.AddPermanentPath -> {
                permanentPaths.add(action.path)
                undoStack.add(action)
            }
            is UndoableAction.AddMagicPath -> {
                magicPaths.add(action.path)
                undoStack.add(action)
            }
            is UndoableAction.ErasePaths -> {
                permanentPaths.removeAll(action.permanentPaths.toSet())
                magicPaths.removeAll(action.magicPaths.toSet())
                undoStack.add(action)
            }
            is UndoableAction.AddCard -> {
                cards.add(action.card)
                undoStack.add(action)
            }
            is UndoableAction.AddCards -> {
                cards.addAll(action.cards)
                undoStack.add(action)
            }
            is UndoableAction.RemoveCard -> {
                cards.remove(action.card)
                undoStack.add(action)
            }
        }
        updateUndoRedoState()
    }

    fun selectPermanentInk() {
        activeMode.value = ToolMode.PERMANENT_INK
        clearSelection()
    }

    fun selectMagicInk() {
        activeMode.value = ToolMode.MAGIC_INK
        clearSelection()
    }

    fun selectEraser() {
        activeMode.value = ToolMode.ERASER
        clearSelection()
    }

    fun selectMoveMode() {
        activeMode.value = ToolMode.MOVE
    }

    fun selectCard(cardId: UUID?) {
        selectedCardId.value = if (selectedCardId.value == cardId) null else cardId
    }

    fun clearSelection() {
        selectedCardId.value = null
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
        currentPath.value?.let { path ->
            // Light decimation - skip only very close points (< 1px)
            val lastPoint = path.points.lastOrNull()
            if (lastPoint != null) {
                val dx = x - lastPoint.x
                val dy = y - lastPoint.y
                if (dx * dx + dy * dy < 1f) return
            }

            path.points.add(PathPoint(x, y, pressure))
            currentPathVersion.value++
            currentPath.value = path
        }
    }

    fun endPath() {
        currentPath.value?.let { path ->
            if (path.points.size > 1) {
                if (path.isMagicInk) {
                    magicPaths.add(path)
                    pushUndo(UndoableAction.AddMagicPath(path))
                } else {
                    permanentPaths.add(path)
                    pushUndo(UndoableAction.AddPermanentPath(path))
                }
                markNeedsSave()
            }
        }
        currentPath.value = null
    }

    // Track erased paths for undo
    private var currentEraseAction: UndoableAction.ErasePaths? = null

    fun startErase() {
        currentEraseAction = UndoableAction.ErasePaths(emptyList(), emptyList())
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

        // Then permanent paths
        val permanentToRemove = permanentPaths.filter { path ->
            path.points.any { point ->
                val dx = point.x - x
                val dy = point.y - y
                dx * dx + dy * dy < radius * radius
            }
        }

        if (magicToRemove.isNotEmpty() || permanentToRemove.isNotEmpty()) {
            magicPaths.removeAll(magicToRemove.toSet())
            permanentPaths.removeAll(permanentToRemove.toSet())

            // Accumulate erased paths
            currentEraseAction?.let { current ->
                currentEraseAction = UndoableAction.ErasePaths(
                    permanentPaths = current.permanentPaths + permanentToRemove,
                    magicPaths = current.magicPaths + magicToRemove
                )
            }
        }
    }

    fun endErase() {
        currentEraseAction?.let { action ->
            if (action.permanentPaths.isNotEmpty() || action.magicPaths.isNotEmpty()) {
                pushUndo(action)
                markNeedsSave()
            }
        }
        currentEraseAction = null
    }

    fun clearMagicInk() {
        magicPaths.clear()
    }

    fun clearAllCards() {
        cards.clear()
    }

    fun play() {
        if (isGenerating.value) return

        // Check for magic paths - either on main canvas or expanded card
        val hasExpandedCardPaths = isCardExpanded.value && expandedCardMagicPaths.isNotEmpty()
        val hasMainCanvasPaths = magicPaths.isNotEmpty()

        if (!hasExpandedCardPaths && !hasMainCanvasPaths) {
            errorMessage.value = "Draw something with magic ink first"
            return
        }

        isGenerating.value = true

        viewModelScope.launch {
            try {
                val callback = snapshotCallback
                if (callback == null) {
                    throw Exception("Snapshot callback not set")
                }
                val snapshot = callback.invoke()
                if (snapshot == null) {
                    throw Exception("Snapshot returned null - check logs for details")
                }

                val viewportSize = ViewportSize(
                    widthPx = viewportRect.value.width().toInt(),
                    heightPx = viewportRect.value.height().toInt()
                )

                val assets = collectVisibleAssets()

                val response = generationService.generate(
                    snapshot = snapshot,
                    viewportSize = viewportSize,
                    assets = assets,
                    editMode = isCardExpanded.value // Use edit mode when card is expanded
                )

                processActions(response.actions, response.generationId)

                // Clear the appropriate magic paths
                if (isCardExpanded.value) {
                    expandedCardMagicPaths.clear()
                } else {
                    clearMagicInk()
                }

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

        // Get current canvas transform to convert screen coords to canvas coords
        val scale = canvasScale.value
        val offsetX = canvasOffsetX.value
        val offsetY = canvasOffsetY.value

        Log.d("Cards", "Processing ${actions.size} actions (scale=$scale, offset=($offsetX, $offsetY))")

        // Track cards added for undo
        val addedCards = mutableListOf<CanvasCard>()

        for (action in actions) {
            when (action.type) {
                ActionType.PLACE_TEXT -> {
                    val box = action.box ?: continue
                    // Calculate screen coordinates first
                    val screenLeft = box.x * visibleRect.width()
                    val screenTop = box.y * visibleRect.height()
                    val screenRight = (box.x + box.w) * visibleRect.width()
                    val screenBottom = (box.y + box.h) * visibleRect.height()

                    // Convert screen coordinates to canvas coordinates
                    val rect = RectF(
                        ((screenLeft - offsetX) / scale).toFloat(),
                        ((screenTop - offsetY) / scale).toFloat(),
                        ((screenRight - offsetX) / scale).toFloat(),
                        ((screenBottom - offsetY) / scale).toFloat()
                    )

                    Log.d("Cards", "Placing text at $rect")

                    val card = CanvasCard(
                        type = CardType.TEXT,
                        rect = rect,
                        text = action.text,
                        generationId = genId,
                        sourceAssetIds = action.sourceAssetIds ?: emptyList(),
                        transformType = action.transformType
                    )
                    cards.add(card)
                    addedCards.add(card)
                }

                ActionType.PLACE_IMAGE -> {
                    val box = action.box ?: continue
                    // Calculate screen coordinates first
                    val screenLeft = box.x * visibleRect.width()
                    val screenTop = box.y * visibleRect.height()
                    val screenRight = (box.x + box.w) * visibleRect.width()
                    val screenBottom = (box.y + box.h) * visibleRect.height()

                    // Convert screen coordinates to canvas coordinates
                    val rect = RectF(
                        ((screenLeft - offsetX) / scale).toFloat(),
                        ((screenTop - offsetY) / scale).toFloat(),
                        ((screenRight - offsetX) / scale).toFloat(),
                        ((screenBottom - offsetY) / scale).toFloat()
                    )

                    Log.d("Cards", "Placing image at $rect")

                    if (action.imageUrl != null) {
                        val card = CanvasCard(
                            type = CardType.IMAGE,
                            rect = rect,
                            imageUrl = action.imageUrl,
                            generationId = genId,
                            sourceAssetIds = action.sourceAssetIds ?: emptyList(),
                            transformType = action.transformType
                        )
                        cards.add(card)
                        addedCards.add(card)
                    } else if (action.prompt != null) {
                        // Show prompt as text placeholder
                        val card = CanvasCard(
                            type = CardType.TEXT,
                            rect = rect,
                            text = "\uD83C\uDFA8 ${action.prompt}",
                            generationId = genId
                        )
                        cards.add(card)
                        addedCards.add(card)
                    }
                }

                ActionType.PLACE_WEB -> {
                    val box = action.box ?: continue
                    // Calculate screen coordinates first
                    val screenLeft = box.x * visibleRect.width()
                    val screenTop = box.y * visibleRect.height()
                    val screenRight = (box.x + box.w) * visibleRect.width()
                    val screenBottom = (box.y + box.h) * visibleRect.height()

                    // Convert screen coordinates to canvas coordinates
                    val rect = RectF(
                        ((screenLeft - offsetX) / scale).toFloat(),
                        ((screenTop - offsetY) / scale).toFloat(),
                        ((screenRight - offsetX) / scale).toFloat(),
                        ((screenBottom - offsetY) / scale).toFloat()
                    )

                    Log.d("Cards", "Placing web card at $rect")

                    action.html?.let { html ->
                        val card = CanvasCard(
                            type = CardType.WEB,
                            rect = rect,
                            htmlContent = html,
                            generationId = genId,
                            sourceAssetIds = action.sourceAssetIds ?: emptyList(),
                            transformType = action.transformType
                        )
                        cards.add(card)
                        addedCards.add(card)
                    }
                }

                ActionType.PLACE_VIDEO -> {
                    val box = action.box ?: continue
                    // Calculate screen coordinates first
                    val screenLeft = box.x * visibleRect.width()
                    val screenTop = box.y * visibleRect.height()
                    val screenRight = (box.x + box.w) * visibleRect.width()
                    val screenBottom = (box.y + box.h) * visibleRect.height()

                    // Convert screen coordinates to canvas coordinates
                    val rect = RectF(
                        ((screenLeft - offsetX) / scale).toFloat(),
                        ((screenTop - offsetY) / scale).toFloat(),
                        ((screenRight - offsetX) / scale).toFloat(),
                        ((screenBottom - offsetY) / scale).toFloat()
                    )

                    action.videoUrl?.let { videoUrl ->
                        val card = CanvasCard(
                            type = CardType.VIDEO,
                            rect = rect,
                            videoUrl = videoUrl,
                            generationId = genId
                        )
                        cards.add(card)
                        addedCards.add(card)
                    }
                }

                ActionType.PLACE_AUDIO -> {
                    val box = action.box ?: continue
                    val screenLeft = box.x * visibleRect.width()
                    val screenTop = box.y * visibleRect.height()
                    val screenRight = (box.x + box.w) * visibleRect.width()
                    val screenBottom = (box.y + box.h) * visibleRect.height()

                    val rect = RectF(
                        ((screenLeft - offsetX) / scale).toFloat(),
                        ((screenTop - offsetY) / scale).toFloat(),
                        ((screenRight - offsetX) / scale).toFloat(),
                        ((screenBottom - offsetY) / scale).toFloat()
                    )

                    action.audioUrl?.let { audioUrl ->
                        val card = CanvasCard(
                            type = CardType.AUDIO,
                            rect = rect,
                            audioUrl = audioUrl,
                            generationId = genId
                        )
                        cards.add(card)
                        addedCards.add(card)
                    }
                }

                ActionType.MODIFY_ASSET -> {
                    val targetId = action.targetAssetId ?: continue
                    val cardIndex = cards.indexOfFirst { it.assetId == targetId }
                    if (cardIndex >= 0) {
                        val card = cards[cardIndex]
                        Log.d("Cards", "Modifying asset $targetId")

                        // Determine new content type and update accordingly
                        // Only ONE of these will be set (mutually exclusive)
                        val updatedCard = when {
                            action.imageUrl != null -> {
                                card.copy(
                                    type = CardType.IMAGE,
                                    imageUrl = action.imageUrl,
                                    text = null,
                                    htmlContent = null,
                                    videoUrl = null,
                                    audioUrl = null,
                                    sourceAssetIds = action.sourceAssetIds ?: card.sourceAssetIds,
                                    transformType = action.transformType ?: card.transformType
                                )
                            }
                            action.videoUrl != null -> {
                                card.copy(
                                    type = CardType.VIDEO,
                                    videoUrl = action.videoUrl,
                                    imageUrl = null,
                                    text = null,
                                    htmlContent = null,
                                    audioUrl = null,
                                    sourceAssetIds = action.sourceAssetIds ?: card.sourceAssetIds,
                                    transformType = action.transformType ?: card.transformType
                                )
                            }
                            action.audioUrl != null -> {
                                card.copy(
                                    type = CardType.AUDIO,
                                    audioUrl = action.audioUrl,
                                    imageUrl = null,
                                    text = null,
                                    htmlContent = null,
                                    videoUrl = null,
                                    sourceAssetIds = action.sourceAssetIds ?: card.sourceAssetIds,
                                    transformType = action.transformType ?: card.transformType
                                )
                            }
                            action.text != null -> {
                                card.copy(
                                    type = CardType.TEXT,
                                    text = action.text,
                                    htmlContent = null,
                                    imageUrl = null,
                                    videoUrl = null,
                                    audioUrl = null,
                                    sourceAssetIds = action.sourceAssetIds ?: card.sourceAssetIds,
                                    transformType = action.transformType ?: card.transformType
                                )
                            }
                            action.html != null -> {
                                card.copy(
                                    type = CardType.WEB,
                                    htmlContent = action.html,
                                    text = null,
                                    imageUrl = null,
                                    videoUrl = null,
                                    audioUrl = null,
                                    sourceAssetIds = action.sourceAssetIds ?: card.sourceAssetIds,
                                    transformType = action.transformType ?: card.transformType
                                )
                            }
                            else -> card // No change if no content field set
                        }

                        cards[cardIndex] = updatedCard
                        markNeedsSave()
                    } else {
                        Log.w("Cards", "modify_asset: target asset $targetId not found")
                    }
                }

                ActionType.DELETE_ASSET -> {
                    val targetId = action.targetAssetId ?: continue
                    cards.removeAll { it.assetId == targetId }
                }
            }
        }

        // Push all added cards as a single undo action
        if (addedCards.isNotEmpty()) {
            pushUndo(UndoableAction.AddCards(addedCards.toList()))
            markNeedsSave()
        }
    }

    fun dismissError() {
        errorMessage.value = null
    }

    fun removeCard(cardId: UUID) {
        val card = cards.find { it.id == cardId }
        if (card != null) {
            cards.remove(card)
            pushUndo(UndoableAction.RemoveCard(card))
            markNeedsSave()
        }
    }

    fun updateCardPosition(cardId: UUID, newX: Float, newY: Float) {
        val cardIndex = cards.indexOfFirst { it.id == cardId }
        if (cardIndex >= 0) {
            val card = cards[cardIndex]
            val width = card.rect.width()
            val height = card.rect.height()
            card.rect = RectF(newX, newY, newX + width, newY + height)
            markNeedsSave()
        }
    }

    fun addCard(card: CanvasCard) {
        cards.add(card)
        pushUndo(UndoableAction.AddCard(card))
        markNeedsSave()
    }

    fun playExpandedCard(card: CanvasCard, snapshot: Bitmap, magicPaths: List<DrawingPath>) {
        if (isGenerating.value) return

        if (magicPaths.isEmpty()) {
            errorMessage.value = "Draw something with magic ink first"
            return
        }

        isGenerating.value = true

        viewModelScope.launch {
            try {
                val viewportSize = ViewportSize(
                    widthPx = snapshot.width,
                    heightPx = snapshot.height
                )

                // The expanded card is the only asset in this context
                val assets = listOf(
                    card.toAssetInput(RectF(0f, 0f, snapshot.width.toFloat(), snapshot.height.toFloat()))
                ).filterNotNull()

                val response = generationService.generate(
                    snapshot = snapshot,
                    viewportSize = viewportSize,
                    assets = assets,
                    editMode = true // Edit mode for modifying existing card
                )

                // Process actions - they will modify or replace the card
                processExpandedCardActions(card, response.actions, response.generationId)

            } catch (e: Exception) {
                Log.e("CanvasViewModel", "Expanded card generation error: ${e.message}", e)
                errorMessage.value = e.message ?: "An error occurred"
            } finally {
                isGenerating.value = false
            }
        }
    }

    private fun processExpandedCardActions(sourceCard: CanvasCard, actions: List<CanvasAction>, generationId: String?) {
        val genId = generationId ?: UUID.randomUUID().toString()

        // Track cards added for undo
        val addedCards = mutableListOf<CanvasCard>()

        for (action in actions) {
            when (action.type) {
                ActionType.MODIFY_ASSET -> {
                    // Modify the source card based on action
                    val cardIndex = cards.indexOfFirst { it.id == sourceCard.id }
                    if (cardIndex >= 0) {
                        when {
                            action.html != null -> {
                                cards[cardIndex] = sourceCard.copy(
                                    type = CardType.WEB,
                                    htmlContent = action.html,
                                    text = null,
                                    imageUrl = null
                                )
                            }
                            action.text != null -> {
                                cards[cardIndex] = sourceCard.copy(
                                    type = CardType.TEXT,
                                    text = action.text,
                                    htmlContent = null,
                                    imageUrl = null
                                )
                            }
                            action.imageUrl != null -> {
                                cards[cardIndex] = sourceCard.copy(
                                    type = CardType.IMAGE,
                                    imageUrl = action.imageUrl,
                                    text = null,
                                    htmlContent = null
                                )
                            }
                        }
                    }
                }
                ActionType.PLACE_TEXT, ActionType.PLACE_IMAGE, ActionType.PLACE_WEB, ActionType.PLACE_VIDEO, ActionType.PLACE_AUDIO -> {
                    // Place new cards relative to the source card position
                    val box = action.box ?: continue
                    val rect = RectF(
                        sourceCard.rect.left + box.x.toFloat() * sourceCard.rect.width(),
                        sourceCard.rect.top + box.y.toFloat() * sourceCard.rect.height(),
                        sourceCard.rect.left + (box.x.toFloat() + box.w.toFloat()) * sourceCard.rect.width(),
                        sourceCard.rect.top + (box.y.toFloat() + box.h.toFloat()) * sourceCard.rect.height()
                    )

                    val newCard = when (action.type) {
                        ActionType.PLACE_TEXT -> CanvasCard(
                            type = CardType.TEXT,
                            rect = rect,
                            text = action.text,
                            generationId = genId
                        )
                        ActionType.PLACE_IMAGE -> {
                            if (action.imageUrl != null) {
                                CanvasCard(
                                    type = CardType.IMAGE,
                                    rect = rect,
                                    imageUrl = action.imageUrl,
                                    generationId = genId
                                )
                            } else null
                        }
                        ActionType.PLACE_WEB -> {
                            action.html?.let { html ->
                                CanvasCard(
                                    type = CardType.WEB,
                                    rect = rect,
                                    htmlContent = html,
                                    generationId = genId
                                )
                            }
                        }
                        ActionType.PLACE_VIDEO -> {
                            action.videoUrl?.let { videoUrl ->
                                CanvasCard(
                                    type = CardType.VIDEO,
                                    rect = rect,
                                    videoUrl = videoUrl,
                                    generationId = genId
                                )
                            }
                        }
                        ActionType.PLACE_AUDIO -> {
                            action.audioUrl?.let { audioUrl ->
                                CanvasCard(
                                    type = CardType.AUDIO,
                                    rect = rect,
                                    audioUrl = audioUrl,
                                    generationId = genId
                                )
                            }
                        }
                        else -> null
                    }

                    newCard?.let {
                        cards.add(it)
                        addedCards.add(it)
                    }
                }
                ActionType.DELETE_ASSET -> {
                    // Remove the source card
                    cards.removeAll { it.id == sourceCard.id }
                }
            }
        }

        // Push all added cards as a single undo action
        if (addedCards.isNotEmpty()) {
            pushUndo(UndoableAction.AddCards(addedCards.toList()))
        }
    }

    // ==================== Workspace Management ====================

    /**
     * Initialize workspace manager with context. Call this once when the activity is created.
     */
    fun initializeWorkspace(context: Context) {
        if (workspaceManager == null) {
            workspaceManager = WorkspaceManager.getInstance(context)
            loadInitialCanvas()
            startAutoSave()
        }
    }

    private fun loadInitialCanvas() {
        val manager = workspaceManager ?: return
        val document = manager.getOrCreateFirstCanvas()
        loadDocument(document)
        refreshCanvasList()
    }

    private fun loadDocument(document: CanvasDocument) {
        currentDocument = document
        currentCanvasId.value = document.id
        currentCanvasTitle.value = document.title

        // Clear current state
        permanentPaths.clear()
        magicPaths.clear()
        cards.clear()
        undoStack.clear()
        redoStack.clear()
        updateUndoRedoState()

        // Load document state
        permanentPaths.addAll(document.permanentPaths)
        magicPaths.addAll(document.magicPaths)
        cards.addAll(document.cards)

        // Restore view state
        canvasScale.value = document.zoomScale
        canvasOffsetX.value = document.contentOffsetX
        canvasOffsetY.value = document.contentOffsetY

        Log.d("CanvasViewModel", "Loaded canvas: ${document.title} (${document.id})")
    }

    fun refreshCanvasList() {
        val manager = workspaceManager ?: return
        canvasList.clear()
        canvasList.addAll(manager.listCanvases())

        // Load thumbnails
        val thumbMap = mutableMapOf<String, Bitmap?>()
        canvasList.forEach { metadata ->
            thumbMap[metadata.id] = manager.loadThumbnail(UUID.fromString(metadata.id))
        }
        thumbnails.value = thumbMap
    }

    /**
     * Toggle workspace menu visibility.
     */
    fun toggleWorkspaceMenu() {
        showingWorkspaceMenu.value = !showingWorkspaceMenu.value
        if (showingWorkspaceMenu.value) {
            refreshCanvasList()
        }
    }

    fun closeWorkspaceMenu() {
        showingWorkspaceMenu.value = false
    }

    /**
     * Create a new canvas and switch to it.
     */
    fun createNewCanvas() {
        val manager = workspaceManager ?: return

        // Save current canvas first
        saveCurrentCanvas()

        // Create new canvas
        val newDocument = manager.createCanvas()
        loadDocument(newDocument)
        refreshCanvasList()
    }

    /**
     * Switch to a different canvas.
     */
    fun switchToCanvas(canvasId: UUID) {
        if (canvasId == currentCanvasId.value) return

        val manager = workspaceManager ?: return

        // Save current canvas first
        saveCurrentCanvas()

        // Load the requested canvas
        val document = manager.loadCanvas(canvasId)
        if (document != null) {
            loadDocument(document)
        } else {
            errorMessage.value = "Failed to load canvas"
        }
    }

    /**
     * Delete a canvas.
     */
    fun deleteCanvas(canvasId: UUID) {
        val manager = workspaceManager ?: return

        // Don't delete the current canvas if it's the last one
        if (canvasList.size <= 1) {
            errorMessage.value = "Cannot delete the last canvas"
            return
        }

        val success = manager.deleteCanvas(canvasId)
        if (success) {
            // If we deleted the current canvas, switch to another one
            if (canvasId == currentCanvasId.value) {
                val remainingCanvases = manager.listCanvases()
                if (remainingCanvases.isNotEmpty()) {
                    val firstCanvas = manager.loadCanvas(UUID.fromString(remainingCanvases.first().id))
                    if (firstCanvas != null) {
                        loadDocument(firstCanvas)
                    }
                }
            }
            refreshCanvasList()
        } else {
            errorMessage.value = "Failed to delete canvas"
        }
    }

    /**
     * Rename a canvas.
     */
    fun renameCanvas(canvasId: UUID, newTitle: String) {
        val manager = workspaceManager ?: return
        manager.renameCanvas(canvasId, newTitle)

        // Update current title if this is the current canvas
        if (canvasId == currentCanvasId.value) {
            currentCanvasTitle.value = newTitle
            currentDocument?.title = newTitle
        }

        refreshCanvasList()
    }

    /**
     * Mark canvas as needing save (call this when content changes).
     */
    fun markNeedsSave() {
        needsSave = true
        currentDocument?.markModified()
    }

    /**
     * Sync current canvas state to document model.
     */
    private fun syncCanvasState() {
        currentDocument?.let { doc ->
            doc.permanentPaths = permanentPaths.toList()
            doc.magicPaths = magicPaths.toList()
            doc.cards = cards.toList()
            doc.zoomScale = canvasScale.value
            doc.contentOffsetX = canvasOffsetX.value
            doc.contentOffsetY = canvasOffsetY.value
        }
    }

    /**
     * Save the current canvas and generate thumbnail.
     */
    fun saveCurrentCanvas() {
        val manager = workspaceManager ?: return
        val document = currentDocument ?: return

        syncCanvasState()
        manager.saveCanvas(document)

        // Generate and save thumbnail
        snapshotCallback?.invoke()?.let { bitmap ->
            manager.saveThumbnail(document.id, bitmap)
        }

        needsSave = false
        Log.d("CanvasViewModel", "Saved canvas: ${document.title}")
    }

    /**
     * Get thumbnail for a canvas.
     */
    fun getThumbnail(canvasId: UUID): Bitmap? {
        return thumbnails.value[canvasId.toString()]
    }

    private fun startAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            while (true) {
                delay(autoSaveIntervalMs)
                if (needsSave) {
                    saveCurrentCanvas()
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        autoSaveJob?.cancel()
        // Final save
        if (needsSave) {
            val manager = workspaceManager ?: return
            val document = currentDocument ?: return
            syncCanvasState()
            manager.saveCanvas(document)
        }
    }
}
