package com.commonknowledge.scribbletablet.ui.components

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Enhanced video scrubber with frame thumbnail strip.
 * Matches iOS EnhancedVideoScrubber behavior.
 */
@Composable
fun EnhancedVideoScrubber(
    videoUri: String,
    currentPosition: Long,
    duration: Long,
    onSeek: (Long) -> Unit,
    onScrubStart: () -> Unit = {},
    onScrubEnd: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var thumbnails by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubProgress by remember { mutableStateOf(0f) }

    // Calculate progress
    val progress = if (duration > 0 && !isScrubbing) {
        currentPosition.toFloat() / duration
    } else if (isScrubbing) {
        scrubProgress
    } else {
        0f
    }

    // Generate thumbnails when video URI or size changes
    LaunchedEffect(videoUri, containerSize) {
        if (containerSize.width > 0 && videoUri.isNotEmpty()) {
            val thumbnailWidth = 44
            val count = maxOf(8, containerSize.width / thumbnailWidth)

            scope.launch {
                thumbnails = generateThumbnails(context, videoUri, count, duration)
            }
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.6f))
            .onSizeChanged { containerSize = it }
            .pointerInput(duration) {
                detectTapGestures { offset ->
                    val tapProgress = (offset.x / size.width).coerceIn(0f, 1f)
                    val seekTime = (tapProgress * duration).toLong()
                    onSeek(seekTime)
                }
            }
            .pointerInput(duration) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        isScrubbing = true
                        scrubProgress = (offset.x / size.width).coerceIn(0f, 1f)
                        onScrubStart()
                    },
                    onDragEnd = {
                        val seekTime = (scrubProgress * duration).toLong()
                        onSeek(seekTime)
                        isScrubbing = false
                        onScrubEnd()
                    },
                    onDragCancel = {
                        isScrubbing = false
                        onScrubEnd()
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        val delta = dragAmount / size.width
                        scrubProgress = (scrubProgress + delta).coerceIn(0f, 1f)
                        val seekTime = (scrubProgress * duration).toLong()
                        onSeek(seekTime)
                    }
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Time labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatTime(if (isScrubbing) (scrubProgress * duration).toLong() else currentPosition),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = formatTime(duration),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Thumbnail strip with playhead
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                // Thumbnails row
                Row(
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (thumbnails.isEmpty()) {
                        // Placeholders
                        repeat(8) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .background(Color.White.copy(alpha = 0.1f))
                            )
                        }
                    } else {
                        thumbnails.forEach { bitmap ->
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }

                // Playhead indicator
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(3.dp)
                        .offset(x = with(density) {
                            ((containerSize.width - 24.dp.toPx()) * progress - 1.5.dp.toPx()).toDp()
                        })
                        .background(Color.White, RoundedCornerShape(1.5.dp))
                )
            }
        }
    }
}

private suspend fun generateThumbnails(
    context: android.content.Context,
    videoUri: String,
    count: Int,
    duration: Long
): List<Bitmap> = withContext(Dispatchers.IO) {
    val retriever = MediaMetadataRetriever()
    val thumbnails = mutableListOf<Bitmap>()

    try {
        // Try to set data source
        if (videoUri.startsWith("http://") || videoUri.startsWith("https://")) {
            retriever.setDataSource(videoUri, HashMap())
        } else {
            retriever.setDataSource(context, Uri.parse(videoUri))
        }

        val videoDuration = duration.takeIf { it > 0 }
            ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            ?: 0L

        if (videoDuration > 0) {
            for (i in 0 until count) {
                val timeUs = (videoDuration * i / (count - 1).coerceAtLeast(1)) * 1000 // Convert to microseconds
                val frame = retriever.getFrameAtTime(
                    timeUs,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )
                frame?.let {
                    // Scale down for thumbnail
                    val scaled = Bitmap.createScaledBitmap(it, 80, 80, true)
                    thumbnails.add(scaled)
                    if (it != scaled) it.recycle()
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    } finally {
        try {
            retriever.release()
        } catch (e: Exception) {
            // Ignore
        }
    }

    thumbnails
}

private fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
