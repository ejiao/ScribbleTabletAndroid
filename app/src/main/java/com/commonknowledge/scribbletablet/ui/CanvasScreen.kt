package com.commonknowledge.scribbletablet.ui

import android.graphics.Bitmap
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
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.commonknowledge.scribbletablet.data.model.CanvasCard
import com.commonknowledge.scribbletablet.data.model.DrawingPath
import com.commonknowledge.scribbletablet.data.model.ToolMode
import com.commonknowledge.scribbletablet.ui.canvas.DrawingCanvas
import com.commonknowledge.scribbletablet.ui.cards.CardView
import com.commonknowledge.scribbletablet.ui.toolbar.CanvasToolbar
import com.commonknowledge.scribbletablet.ui.workspace.WorkspaceMenuView
import com.commonknowledge.scribbletablet.viewmodel.CanvasViewModel

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

    Box(modifier = Modifier.fillMaxSize()) {
        // Drawing canvas
        DrawingCanvas(
            viewModel = viewModel,
            modifier = Modifier.fillMaxSize()
        )

        // Cards overlay - render non-selected cards first, then selected card on top
        viewModel.cards.filter { it.id != selectedCardId }.forEach { card ->
            CardView(
                card = card,
                isInMoveMode = isInMoveMode,
                onDelete = { viewModel.removeCard(card.id) },
                onSelect = { viewModel.selectCard(card.id) },
                isSelected = false,
                canvasScale = canvasScale,
                canvasOffsetX = canvasOffsetX,
                canvasOffsetY = canvasOffsetY,
                onPlayExpanded = { expandedCard, snapshot, magicPaths ->
                    viewModel.playExpandedCard(expandedCard, snapshot, magicPaths)
                }
            )
        }

        // Selected card rendered last (on top) with higher z-index
        selectedCardId?.let { selectedId ->
            viewModel.cards.find { it.id == selectedId }?.let { card ->
                CardView(
                    card = card,
                    isInMoveMode = isInMoveMode,
                    onDelete = { viewModel.removeCard(card.id) },
                    onSelect = { viewModel.selectCard(card.id) },
                    isSelected = true,
                    canvasScale = canvasScale,
                    canvasOffsetX = canvasOffsetX,
                    canvasOffsetY = canvasOffsetY,
                    onPlayExpanded = { expandedCard, snapshot, magicPaths ->
                        viewModel.playExpandedCard(expandedCard, snapshot, magicPaths)
                    },
                    modifier = Modifier.zIndex(100f)
                )
            }
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
            // Left: Workspace menu button with canvas title
            WorkspaceMenuButton(
                title = currentCanvasTitle,
                onClick = { viewModel.toggleWorkspaceMenu() }
            )

            // Right: New canvas button and fuel pill
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // New canvas button
                IconButton(
                    onClick = { viewModel.createNewCanvas() },
                    modifier = Modifier
                        .size(44.dp)
                        .shadow(
                            elevation = 12.dp,
                            shape = CircleShape,
                            ambientColor = Color.Black.copy(alpha = 0.08f),
                            spotColor = Color.Black.copy(alpha = 0.12f)
                        )
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                        .border(1.dp, Color.Black.copy(alpha = 0.1f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.NoteAdd,
                        contentDescription = "New Canvas",
                        tint = Color.DarkGray,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Fuel pill (placeholder for now)
                FuelPill(fuelValue = "57.1")
            }
        }

        // Toolbar at bottom
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .zIndex(200f)
        ) {
            CanvasToolbar(viewModel = viewModel)
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
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
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
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
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
