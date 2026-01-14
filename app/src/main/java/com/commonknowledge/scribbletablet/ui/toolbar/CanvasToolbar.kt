package com.commonknowledge.scribbletablet.ui.toolbar

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.commonknowledge.scribbletablet.data.model.ToolMode
import com.commonknowledge.scribbletablet.viewmodel.CanvasViewModel

@Composable
fun CanvasToolbar(
    viewModel: CanvasViewModel,
    modifier: Modifier = Modifier
) {
    val activeMode = viewModel.activeMode.value
    val isGenerating = viewModel.isGenerating.value

    Surface(
        modifier = modifier
            .padding(horizontal = 24.dp, vertical = 24.dp)
            .shadow(24.dp, RoundedCornerShape(32.dp)),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Tool buttons
            ToolButton(
                icon = Icons.Outlined.Edit,
                label = "Ink",
                isSelected = activeMode == ToolMode.PERMANENT_INK,
                onClick = { viewModel.selectPermanentInk() }
            )

            ToolButton(
                icon = Icons.Outlined.AutoAwesome,
                label = "Magic",
                isSelected = activeMode == ToolMode.MAGIC_INK,
                onClick = { viewModel.selectMagicInk() }
            )

            ToolButton(
                icon = Icons.Outlined.PanTool,
                label = "Select",
                isSelected = activeMode == ToolMode.MOVE,
                onClick = { viewModel.selectMoveMode() }
            )

            Spacer(modifier = Modifier.weight(1f))

            // Eraser button
            IconButton(
                onClick = { viewModel.selectEraser() },
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (activeMode == ToolMode.ERASER) Color.Black else Color.Transparent
                    )
            ) {
                Icon(
                    imageVector = Icons.Outlined.Backspace,
                    contentDescription = "Eraser",
                    tint = if (activeMode == ToolMode.ERASER) Color.White else Color.Gray
                )
            }

            // Clear button
            IconButton(
                onClick = { viewModel.clearAllCards() },
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "Clear",
                    tint = Color.Gray
                )
            }

            // Play button
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
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) Color.Black else Color.Transparent
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
                tint = if (isSelected) Color.White else Color.Gray,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                fontSize = 10.sp,
                color = if (isSelected) Color.White else Color.Gray
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
