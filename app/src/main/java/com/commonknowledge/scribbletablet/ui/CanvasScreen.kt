package com.commonknowledge.scribbletablet.ui

import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.commonknowledge.scribbletablet.data.model.CanvasCard
import com.commonknowledge.scribbletablet.data.model.CardType
import com.commonknowledge.scribbletablet.data.model.DrawingPath
import com.commonknowledge.scribbletablet.data.model.ToolMode
import androidx.compose.ui.viewinterop.AndroidView
import com.commonknowledge.scribbletablet.ui.canvas.DrawingCanvas
import com.commonknowledge.scribbletablet.ui.canvas.DrawingOverlay
import com.commonknowledge.scribbletablet.ui.canvas.GenerationRippleView
import com.commonknowledge.scribbletablet.ui.canvas.OnyxDrawingSurface
import com.commonknowledge.scribbletablet.ui.canvas.RealtimeStrokeView
import com.commonknowledge.scribbletablet.ui.cards.CardView
import com.commonknowledge.scribbletablet.ui.cards.ExpandedCardOverlay
import com.commonknowledge.scribbletablet.ui.components.AudioRecorderView
import com.commonknowledge.scribbletablet.ui.toolbar.CanvasToolbar
import com.commonknowledge.scribbletablet.ui.workspace.WorkspaceMenuView
import com.commonknowledge.scribbletablet.viewmodel.CanvasViewModel
import java.io.File

@Composable
fun CanvasScreen(
    viewModel: CanvasViewModel = viewModel()
) {
    val context = LocalContext.current

    // Initialize workspace on first composition
    LaunchedEffect(Unit) {
        viewModel.initializeWorkspace(context)
    }

    val errorMessage = viewModel.errorMessage.value
    val activeMode = viewModel.activeMode.value
    val selectedCardId = viewModel.selectedCardId.value
    val isInMoveMode = activeMode == ToolMode.MOVE
    val isGenerating = viewModel.isGenerating.value

    // Canvas transform state
    val canvasScale = viewModel.canvasScale.value
    val canvasOffsetX = viewModel.canvasOffsetX.value
    val canvasOffsetY = viewModel.canvasOffsetY.value

    // Workspace state
    val showingWorkspaceMenu = viewModel.showingWorkspaceMenu.value
    val currentCanvasTitle = viewModel.currentCanvasTitle.value
    val currentCanvasId = viewModel.currentCanvasId.value
    val canvasList = viewModel.canvasList
    val thumbnails = viewModel.thumbnails.value

    // Audio recorder state
    var showAudioRecorder by remember { mutableStateOf(false) }

    // Photo picker launcher
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            // Add image card at center of viewport
            val centerX = viewModel.viewportRect.value.centerX()
            val centerY = viewModel.viewportRect.value.centerY()
            val cardWidth = 300f
            val cardHeight = 200f

            val card = CanvasCard(
                type = CardType.IMAGE,
                rect = RectF(
                    centerX - cardWidth / 2,
                    centerY - cardHeight / 2,
                    centerX + cardWidth / 2,
                    centerY + cardHeight / 2
                ),
                imageUrl = uri.toString()
            )
            viewModel.addCard(card)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Drawing canvas
        DrawingCanvas(
            viewModel = viewModel,
            modifier = Modifier.fillMaxSize()
        )

        // Generation ripple animation overlay (below drawing overlay so magic ink stays visible)
        GenerationRippleView(
            isVisible = isGenerating,
            modifier = Modifier.fillMaxSize().zIndex(1f)
        )

        // Cards overlay - use key() to maintain card identity across recompositions
        viewModel.cards.forEach { card ->
            val isSelected = card.id == selectedCardId
            key(card.id) {
                CardView(
                    card = card,
                    isInMoveMode = isInMoveMode,
                    onDelete = { viewModel.removeCard(card.id) },
                    onSelect = { viewModel.selectCard(card.id) },
                    isSelected = isSelected,
                    canvasScale = canvasScale,
                    canvasOffsetX = canvasOffsetX,
                    canvasOffsetY = canvasOffsetY,
                    onPlayExpanded = { expandedCard, snapshot, magicPaths ->
                        viewModel.playExpandedCard(expandedCard, snapshot, magicPaths)
                    },
                    onExpand = { viewModel.expandCard(card.id) },
                    onPositionChanged = { newX, newY ->
                        viewModel.updateCardPosition(card.id, newX, newY)
                    },
                    modifier = if (isSelected) Modifier.zIndex(100f) else Modifier
                )
            }
        }

        // Drawing overlay - renders completed strokes above cards
        DrawingOverlay(
            viewModel = viewModel,
            modifier = Modifier.fillMaxSize().zIndex(150f)
        )

        // Real-time stroke view - renders current stroke with minimal latency
        // Uses Android Canvas directly, bypassing Compose recomposition
        val scale = viewModel.canvasScale.value
        val offsetX = viewModel.canvasOffsetX.value
        val offsetY = viewModel.canvasOffsetY.value
        val activeMode = viewModel.activeMode.value
        val strokeColor = if (activeMode == ToolMode.MAGIC_INK) 0xFF4CAF50.toInt() else android.graphics.Color.BLACK

        AndroidView(
            modifier = Modifier.fillMaxSize().zIndex(155f),
            factory = { context ->
                RealtimeStrokeView(context).apply {
                    // Register callback with ViewModel
                    viewModel.realtimeStrokeCallback = { points ->
                        updateCurrentStroke(points)
                    }
                }
            },
            update = { view ->
                view.setTransform(scale, offsetX, offsetY)
                view.setStrokeStyle(strokeColor, 3f)
            }
        )

        // Onyx drawing surface - uses hardware-accelerated pen input on Boox devices
        OnyxDrawingSurface(
            viewModel = viewModel,
            modifier = Modifier.fillMaxSize().zIndex(160f),
            isVisible = !showingWorkspaceMenu && !viewModel.isCardExpanded.value
        )

        // Expanded card overlay - rendered below toolbar (zIndex 190 < toolbar 200)
        val expandedCardId = viewModel.expandedCardId.value
        val expandedCard = expandedCardId?.let { id -> viewModel.cards.find { it.id == id } }
        if (expandedCard != null) {
            ExpandedCardOverlay(
                card = expandedCard,
                onDismiss = { viewModel.collapseExpandedCard() },
                onDelete = {
                    viewModel.removeCard(expandedCard.id)
                    viewModel.collapseExpandedCard()
                },
                isGenerating = isGenerating,
                magicPaths = viewModel.expandedCardMagicPaths,
                onAddMagicPath = { viewModel.addExpandedCardMagicPath(it) },
                modifier = Modifier.zIndex(190f)
            )
        }

        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .zIndex(200f),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Workspace menu button and new canvas button
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Workspace menu button (hidden when sidebar is open)
                if (!showingWorkspaceMenu) {
                    WorkspaceMenuButton(
                        title = currentCanvasTitle,
                        onClick = { viewModel.toggleWorkspaceMenu() }
                    )
                }

                // New canvas button
                Surface(
                    onClick = { viewModel.createNewCanvas() },
                    modifier = Modifier
                        .size(44.dp)
                        .shadow(
                            elevation = 12.dp,
                            shape = CircleShape,
                            ambientColor = Color.Black.copy(alpha = 0.08f),
                            spotColor = Color.Black.copy(alpha = 0.12f)
                        )
                        .border(1.dp, Color.Black.copy(alpha = 0.1f), CircleShape),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    tonalElevation = 4.dp
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.NoteAdd,
                            contentDescription = "New Canvas",
                            tint = Color.DarkGray,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // Right: Fuel pill
            FuelPill(fuelValue = "57.1")
        }

        // Toolbar at bottom
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .zIndex(200f)
        ) {
            CanvasToolbar(
                viewModel = viewModel,
                onOpenPhotoPicker = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                    )
                },
                onOpenAudioRecorder = { showAudioRecorder = true }
            )
        }

        // Audio recorder dialog
        if (showAudioRecorder) {
            Dialog(onDismissRequest = { showAudioRecorder = false }) {
                AudioRecorderView(
                    onDismiss = { showAudioRecorder = false },
                    onRecordingComplete = { file, duration ->
                        // Add audio card at center of viewport
                        val centerX = viewModel.viewportRect.value.centerX()
                        val centerY = viewModel.viewportRect.value.centerY()
                        val cardWidth = 200f
                        val cardHeight = 100f

                        val card = CanvasCard(
                            type = CardType.AUDIO,
                            rect = RectF(
                                centerX - cardWidth / 2,
                                centerY - cardHeight / 2,
                                centerX + cardWidth / 2,
                                centerY + cardHeight / 2
                            ),
                            audioUrl = file.toURI().toString(),
                            audioDuration = duration
                        )
                        viewModel.addCard(card)
                        showAudioRecorder = false
                    }
                )
            }
        }

        // Workspace side menu
        WorkspaceMenuView(
            isOpen = showingWorkspaceMenu,
            canvases = canvasList,
            activeCanvasId = currentCanvasId,
            thumbnails = thumbnails,
            onDismiss = { viewModel.closeWorkspaceMenu() },
            onCanvasSelect = { id -> viewModel.switchToCanvas(id) },
            onCanvasDelete = { id -> viewModel.deleteCanvas(id) },
            onCanvasRename = { id, title -> viewModel.renameCanvas(id, title) },
            onNewCanvas = { viewModel.createNewCanvas() },
            modifier = Modifier.zIndex(400f)
        )

        // Error snackbar
        if (errorMessage != null) {
            Snackbar(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp)
                    .zIndex(300f),
                action = {
                    TextButton(onClick = { viewModel.dismissError() }) {
                        Text("Dismiss")
                    }
                }
            ) {
                Text(errorMessage)
            }
        }
    }
}

@Composable
private fun WorkspaceMenuButton(
    title: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .height(44.dp)
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(percent = 50),
                ambientColor = Color.Black.copy(alpha = 0.08f),
                spotColor = Color.Black.copy(alpha = 0.12f)
            )
            .border(1.dp, Color.Black.copy(alpha = 0.1f), RoundedCornerShape(percent = 50)),
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Menu,
                contentDescription = "Menu",
                tint = Color.DarkGray,
                modifier = Modifier.size(20.dp)
            )

            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color.DarkGray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 150.dp)
            )
        }
    }
}

@Composable
private fun FuelPill(
    fuelValue: String
) {
    Surface(
        modifier = Modifier
            .height(44.dp)
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(percent = 50),
                ambientColor = Color.Black.copy(alpha = 0.08f),
                spotColor = Color.Black.copy(alpha = 0.12f)
            )
            .border(1.dp, Color.Black.copy(alpha = 0.1f), RoundedCornerShape(percent = 50)),
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.LocalGasStation,
                contentDescription = "Fuel",
                tint = Color.DarkGray,
                modifier = Modifier.size(18.dp)
            )

            Text(
                text = fuelValue,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color.DarkGray
            )
        }
    }
}
