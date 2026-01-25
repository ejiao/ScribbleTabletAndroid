package com.commonknowledge.scribbletablet.ui.toolbar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Text formatting command types matching iOS implementation.
 */
sealed class TextFormattingCommand {
    object Bold : TextFormattingCommand()
    object Italic : TextFormattingCommand()
    data class Heading(val level: Int) : TextFormattingCommand() // 0=Normal, 1=H1, 2=H2, 3=H3
    object AttachImage : TextFormattingCommand()
}

/**
 * Toolbar shown when editing text cards in expanded mode.
 * Matches iOS TextEditingToolbar behavior.
 */
@Composable
fun TextEditingToolbar(
    onFormatCommand: (TextFormattingCommand) -> Unit,
    onAttachImage: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedHeadingLevel by remember { mutableStateOf(0) }
    var showHeadingMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(percent = 50),
                ambientColor = Color.Black.copy(alpha = 0.08f),
                spotColor = Color.Black.copy(alpha = 0.12f)
            ),
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Heading selector dropdown
            Box {
                Surface(
                    onClick = { showHeadingMenu = true },
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.border(
                        width = 1.dp,
                        color = Color.Gray.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when (selectedHeadingLevel) {
                                1 -> "H1"
                                2 -> "H2"
                                3 -> "H3"
                                else -> "Normal"
                            },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.Black
                        )
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color.Black
                        )
                    }
                }

                DropdownMenu(
                    expanded = showHeadingMenu,
                    onDismissRequest = { showHeadingMenu = false }
                ) {
                    HeadingMenuItem(
                        label = "Normal",
                        fontSize = 16,
                        fontWeight = FontWeight.Normal,
                        isSelected = selectedHeadingLevel == 0,
                        onClick = {
                            selectedHeadingLevel = 0
                            showHeadingMenu = false
                            onFormatCommand(TextFormattingCommand.Heading(0))
                        }
                    )
                    HeadingMenuItem(
                        label = "Heading 1",
                        fontSize = 24,
                        fontWeight = FontWeight.Bold,
                        isSelected = selectedHeadingLevel == 1,
                        onClick = {
                            selectedHeadingLevel = 1
                            showHeadingMenu = false
                            onFormatCommand(TextFormattingCommand.Heading(1))
                        }
                    )
                    HeadingMenuItem(
                        label = "Heading 2",
                        fontSize = 20,
                        fontWeight = FontWeight.Bold,
                        isSelected = selectedHeadingLevel == 2,
                        onClick = {
                            selectedHeadingLevel = 2
                            showHeadingMenu = false
                            onFormatCommand(TextFormattingCommand.Heading(2))
                        }
                    )
                    HeadingMenuItem(
                        label = "Heading 3",
                        fontSize = 18,
                        fontWeight = FontWeight.SemiBold,
                        isSelected = selectedHeadingLevel == 3,
                        onClick = {
                            selectedHeadingLevel = 3
                            showHeadingMenu = false
                            onFormatCommand(TextFormattingCommand.Heading(3))
                        }
                    )
                }
            }

            // Divider
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(24.dp)
                    .background(Color.Gray.copy(alpha = 0.3f))
            )

            // Bold button
            FormatButton(
                label = "B",
                isBold = true,
                onClick = { onFormatCommand(TextFormattingCommand.Bold) }
            )

            // Italic button
            FormatButton(
                label = "I",
                isItalic = true,
                onClick = { onFormatCommand(TextFormattingCommand.Italic) }
            )

            Spacer(modifier = Modifier.weight(1f))

            // Attach image button
            IconButton(
                onClick = {
                    onFormatCommand(TextFormattingCommand.AttachImage)
                    onAttachImage()
                },
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.8f))
                    .border(1.dp, Color.Gray.copy(alpha = 0.1f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Photo,
                    contentDescription = "Attach Image",
                    tint = Color.Black,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun HeadingMenuItem(
    label: String,
    fontSize: Int,
    fontWeight: FontWeight,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    fontSize = fontSize.sp,
                    fontWeight = fontWeight
                )
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        },
        onClick = onClick
    )
}

@Composable
private fun FormatButton(
    label: String,
    isBold: Boolean = false,
    isItalic: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.8f),
        modifier = Modifier
            .size(36.dp)
            .border(1.dp, Color.Gray.copy(alpha = 0.1f), CircleShape)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = label,
                fontSize = 16.sp,
                fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (isItalic) FontStyle.Italic else FontStyle.Normal,
                color = Color.Black
            )
        }
    }
}
