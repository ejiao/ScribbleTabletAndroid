package com.commonknowledge.scribbletablet.ui.workspace

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.commonknowledge.scribbletablet.data.model.CanvasMetadata
import java.util.UUID

// Orange color matching iOS implementation
private val ActiveCanvasColor = Color(0xFFFF9800)

@Composable
fun WorkspaceMenuView(
    isOpen: Boolean,
    canvases: List<CanvasMetadata>,
    activeCanvasId: UUID?,
    thumbnails: Map<String, Bitmap?>,
    onDismiss: () -> Unit,
    onCanvasSelect: (UUID) -> Unit,
    onCanvasDelete: (UUID) -> Unit,
    onCanvasRename: (UUID, String) -> Unit,
    onNewCanvas: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Dimmed overlay when menu is open
    AnimatedVisibility(
        visible = isOpen,
        enter = fadeIn(animationSpec = tween(200)),
        exit = fadeOut(animationSpec = tween(200)),
        modifier = modifier // Apply zIndex to overlay too
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable(onClick = onDismiss)
        )
    }

    // Side menu
    AnimatedVisibility(
        visible = isOpen,
        enter = slideInHorizontally(
            initialOffsetX = { -it },
            animationSpec = tween(250)
        ),
        exit = slideOutHorizontally(
            targetOffsetX = { -it },
            animationSpec = tween(250)
        ),
        modifier = modifier // Apply zIndex to menu
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(280.dp) // Match iOS width
                .shadow(16.dp)
                .background(Color.White)
                .clickable(enabled = false, onClick = {}) // Prevent clicks passing through
        ) {
            // Canvas list with New Canvas button at top (matching iOS)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // New Canvas button at top (matching iOS style)
                item {
                    Surface(
                        onClick = {
                            onNewCanvas()
                            onDismiss()
                        },
                        shape = RoundedCornerShape(percent = 50),
                        color = Color.Black
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "New Canvas",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }
                }

                // Canvas items
                items(canvases, key = { it.id }) { canvas ->
                    CanvasListItem(
                        canvas = canvas,
                        isActive = canvas.id == activeCanvasId?.toString(),
                        thumbnail = thumbnails[canvas.id],
                        canDelete = canvases.size > 1,
                        onClick = {
                            onCanvasSelect(UUID.fromString(canvas.id))
                            onDismiss()
                        },
                        onDelete = {
                            onCanvasDelete(UUID.fromString(canvas.id))
                        },
                        onRename = { newTitle ->
                            onCanvasRename(UUID.fromString(canvas.id), newTitle)
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CanvasListItem(
    canvas: CanvasMetadata,
    isActive: Boolean,
    thumbnail: Bitmap?,
    canDelete: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onRename: (String) -> Unit
) {
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    // Rename dialog
    if (showRenameDialog) {
        RenameDialog(
            currentTitle = canvas.title,
            onDismiss = { showRenameDialog = false },
            onConfirm = { newTitle ->
                onRename(newTitle)
                showRenameDialog = false
            }
        )
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Canvas") },
            text = { Text("Are you sure you want to delete \"${canvas.title}\"? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Card container with optional active highlight (matching iOS orange)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (isActive) ActiveCanvasColor.copy(alpha = 0.3f) else Color.Transparent
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            // Thumbnail
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.LightGray.copy(alpha = 0.1f))
                    .border(1.dp, Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
            ) {
                if (thumbnail != null) {
                    Image(
                        bitmap = thumbnail.asImageBitmap(),
                        contentDescription = canvas.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    // Placeholder matching iOS
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Description,
                            contentDescription = null,
                            tint = Color.Gray.copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }

                // Menu button (top right) - matching iOS ellipsis style
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier
                            .size(28.dp)
                            .shadow(2.dp, CircleShape)
                            .background(Color.White, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "Options",
                            tint = Color.DarkGray,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            onClick = {
                                showMenu = false
                                showRenameDialog = true
                            },
                            leadingIcon = {
                                Icon(Icons.Outlined.Edit, contentDescription = null)
                            }
                        )
                        if (canDelete) {
                            DropdownMenuItem(
                                text = { Text("Delete", color = Color.Red) },
                                onClick = {
                                    showMenu = false
                                    showDeleteDialog = true
                                },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Delete, contentDescription = null, tint = Color.Red)
                                }
                            )
                        }
                    }
                }
            }

            // Title and date underneath (matching iOS layout)
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = canvas.title,
                fontSize = 14.sp,
                fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal,
                color = Color.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            Text(
                text = formatRelativeTime(canvas.modifiedAt),
                fontSize = 12.sp,
                color = Color.Gray,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
private fun RenameDialog(
    currentTitle: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var title by remember { mutableStateOf(currentTitle) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Canvas") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Canvas name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(title) },
                enabled = title.isNotBlank()
            ) {
                Text("Rename")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun formatRelativeTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        seconds < 60 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days < 7 -> "${days}d ago"
        else -> {
            val date = java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault())
            date.format(java.util.Date(timestamp))
        }
    }
}
