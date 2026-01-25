package com.commonknowledge.scribbletablet.ui.toolbar

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.commonknowledge.scribbletablet.data.model.ToolMode
import com.commonknowledge.scribbletablet.viewmodel.CanvasViewModel

@Composable
fun CanvasToolbar(
    viewModel: CanvasViewModel,
    onOpenPhotoPicker: () -> Unit = {},
    onOpenAudioRecorder: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val activeMode = viewModel.activeMode.value
    val isGenerating = viewModel.isGenerating.value
    val canUndo = viewModel.canUndo.value
    val canRedo = viewModel.canRedo.value
    val isExpanded = viewModel.isCardExpanded.value
    var showAttachmentMenu by remember { mutableStateOf(false) }

    // Disabled opacity for expanded mode
    val disabledAlpha = 0.2f

    Surface(
        modifier = modifier
            .padding(horizontal = 24.dp, vertical = 24.dp)
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Undo button - disabled when expanded
            IconButton(
                onClick = { viewModel.undo() },
                enabled = canUndo && !isExpanded,
                modifier = Modifier
                    .size(44.dp)
                    .alpha(if (isExpanded) disabledAlpha else 1f)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Undo,
                    contentDescription = "Undo",
                    tint = if (canUndo && !isExpanded) Color.Gray else Color.LightGray
                )
            }

            // Redo button - disabled when expanded
            IconButton(
                onClick = { viewModel.redo() },
                enabled = canRedo && !isExpanded,
                modifier = Modifier
                    .size(44.dp)
                    .alpha(if (isExpanded) disabledAlpha else 1f)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Redo,
                    contentDescription = "Redo",
                    tint = if (canRedo && !isExpanded) Color.Gray else Color.LightGray
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Tool buttons
            // Ink button - disabled when expanded
            ToolButton(
                icon = Icons.Outlined.Edit,
                label = "Ink",
                isSelected = activeMode == ToolMode.PERMANENT_INK,
                isDisabled = isExpanded,
                onClick = { viewModel.selectPermanentInk() }
            )

            // Magic button - enabled when expanded
            ToolButton(
                icon = Icons.Outlined.AutoAwesome,
                label = "Magic",
                isSelected = activeMode == ToolMode.MAGIC_INK,
                isDisabled = false,
                onClick = { viewModel.selectMagicInk() }
            )

            // Select button - disabled when expanded
            ToolButton(
                icon = Icons.Outlined.PanTool,
                label = "Select",
                isSelected = activeMode == ToolMode.MOVE,
                isDisabled = isExpanded,
                onClick = { viewModel.selectMoveMode() }
            )

            // Flexible spacer to push plus button to center
            Spacer(modifier = Modifier.weight(1f))

            // Attachment menu button (centered) - disabled when expanded
            Box(
                modifier = Modifier.alpha(if (isExpanded) disabledAlpha else 1f)
            ) {
                IconButton(
                    onClick = { if (!isExpanded) showAttachmentMenu = true },
                    enabled = !isExpanded,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = "Add attachment",
                        tint = Color.Gray
                    )
                }

                DropdownMenu(
                    expanded = showAttachmentMenu,
                    onDismissRequest = { showAttachmentMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("From Camera Roll") },
                        onClick = {
                            showAttachmentMenu = false
                            onOpenPhotoPicker()
                        },
                        leadingIcon = {
                            Icon(Icons.Outlined.Photo, contentDescription = null)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Record Audio") },
                        onClick = {
                            showAttachmentMenu = false
                            onOpenAudioRecorder()
                        },
                        leadingIcon = {
                            Icon(Icons.Outlined.Mic, contentDescription = null)
                        }
                    )
                }
            }

            // Flexible spacer to push right items to the right
            Spacer(modifier = Modifier.weight(1f))

            // Eraser button - enabled when expanded
            IconButton(
                onClick = { viewModel.selectEraser() },
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (activeMode == ToolMode.ERASER) Color.Black else Color.Transparent
                    )
            ) {
                EraserIcon(
                    tint = if (activeMode == ToolMode.ERASER) Color.White else Color.Gray,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Clear button - disabled when expanded
            IconButton(
                onClick = { viewModel.clearAllCards() },
                enabled = !isExpanded,
                modifier = Modifier
                    .size(44.dp)
                    .alpha(if (isExpanded) disabledAlpha else 1f)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "Clear",
                    tint = Color.Gray
                )
            }

            // Play button - enabled when expanded
            PlayButton(
                isLoading = isGenerating,
                onClick = { viewModel.play() }
            )
        }
    }
}

@Composable
private fun ToolButton(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    isDisabled: Boolean = false,
    onClick: () -> Unit
) {
    val disabledAlpha = 0.2f

    Surface(
        onClick = { if (!isDisabled) onClick() },
        enabled = !isDisabled,
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected && !isDisabled) Color.Black else Color.Transparent,
        modifier = Modifier.alpha(if (isDisabled) disabledAlpha else 1f)
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected && !isDisabled) Color.White else Color.Gray,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                fontSize = 10.sp,
                color = if (isSelected && !isDisabled) Color.White else Color.Gray
            )
        }
    }
}

@Composable
private fun PlayButton(
    isLoading: Boolean,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "loading")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Button(
        onClick = onClick,
        enabled = !isLoading,
        modifier = Modifier.size(64.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Black,
            disabledContainerColor = Color.Black.copy(alpha = 0.7f)
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(24.dp)
                    .rotate(rotation),
                color = Color.White,
                strokeWidth = 2.dp
            )
        } else {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "Play",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun EraserIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height

        // Draw eraser body (angled rectangle)
        val path = Path().apply {
            // Start from top-left of eraser body
            moveTo(width * 0.15f, height * 0.35f)
            // Top edge
            lineTo(width * 0.7f, height * 0.1f)
            // Right edge (rounded tip area)
            lineTo(width * 0.95f, height * 0.35f)
            // Bottom-right
            lineTo(width * 0.85f, height * 0.65f)
            // Bottom edge going back
            lineTo(width * 0.3f, height * 0.9f)
            // Left edge
            lineTo(width * 0.05f, height * 0.65f)
            close()
        }

        drawPath(
            path = path,
            color = tint,
            style = Stroke(width = 2.dp.toPx())
        )

        // Draw dividing line (between eraser tip and body)
        drawLine(
            color = tint,
            start = Offset(width * 0.45f, height * 0.22f),
            end = Offset(width * 0.2f, height * 0.72f),
            strokeWidth = 2.dp.toPx()
        )
    }
}
