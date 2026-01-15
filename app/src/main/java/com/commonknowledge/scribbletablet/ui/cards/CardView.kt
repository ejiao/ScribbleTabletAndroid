package com.commonknowledge.scribbletablet.ui.cards

import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.view.PixelCopy
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.OptIn
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.commonknowledge.scribbletablet.data.model.CanvasCard
import com.commonknowledge.scribbletablet.data.model.CardType
import com.commonknowledge.scribbletablet.data.model.DrawingPath
import com.commonknowledge.scribbletablet.data.model.PathPoint
import kotlin.math.roundToInt
import kotlin.random.Random
import android.app.Activity

@Composable
fun CardView(
    card: CanvasCard,
    isInMoveMode: Boolean,
    onDelete: () -> Unit,
    onSelect: () -> Unit,
    isSelected: Boolean,
    canvasScale: Float,
    canvasOffsetX: Float,
    canvasOffsetY: Float,
    onPlayExpanded: (CanvasCard, Bitmap, List<DrawingPath>) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier
) {
    // Card rect is in canvas coordinates - track canvas position for dragging
    var canvasX by remember { mutableStateOf(card.rect.left) }
    var canvasY by remember { mutableStateOf(card.rect.top) }
    var isExpanded by remember { mutableStateOf(false) }

    // Transform canvas coordinates to screen coordinates (in pixels)
    val screenX = canvasX * canvasScale + canvasOffsetX
    val screenY = canvasY * canvasScale + canvasOffsetY
    val screenWidthPx = card.rect.width() * canvasScale
    val screenHeightPx = card.rect.height() * canvasScale

    // Convert pixels to dp for sizing
    val density = LocalDensity.current
    val screenWidthDp = with(density) { screenWidthPx.toDp() }
    val screenHeightDp = with(density) { screenHeightPx.toDp() }

    // Animated dashed border
    val infiniteTransition = rememberInfiniteTransition(label = "border")
    val dashPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 24f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dashPhase"
    )

    // Show expanded dialog
    if (isExpanded) {
        ExpandedCardDialog(
            card = card,
            onDismiss = { isExpanded = false },
            onDelete = {
                isExpanded = false
                onDelete()
            },
            onPlay = { snapshot, magicPaths ->
                onPlayExpanded(card, snapshot, magicPaths)
            }
        )
    }

    // Card content box
    Box(
        modifier = modifier
            .offset { IntOffset(screenX.roundToInt(), screenY.roundToInt()) }
            .width(screenWidthDp)
            .height(screenHeightDp)
            .then(
                if (isSelected) {
                    Modifier.drawBehind {
                        val strokeWidth = 3.dp.toPx()
                        val dashLength = 8.dp.toPx()
                        val gapLength = 4.dp.toPx()
                        drawRoundRect(
                            color = Color(0xFF2196F3),
                            style = Stroke(
                                width = strokeWidth,
                                pathEffect = PathEffect.dashPathEffect(
                                    floatArrayOf(dashLength, gapLength),
                                    dashPhase
                                )
                            ),
                            cornerRadius = CornerRadius(16.dp.toPx())
                        )
                    }
                } else Modifier
            )
            .shadow(
                elevation = if (isSelected) 20.dp else 16.dp,
                shape = RoundedCornerShape(12.dp),
                ambientColor = Color.Black.copy(alpha = 0.08f),
                spotColor = Color.Black.copy(alpha = 0.12f)
            )
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .border(
                width = if (isSelected) 0.dp else 2.dp,
                color = if (isSelected) Color.Transparent else Color.LightGray,
                shape = RoundedCornerShape(12.dp)
            )
            .then(
                if (isInMoveMode) {
                    Modifier.pointerInput(isSelected, canvasScale) {
                        detectTapGestures(
                            onTap = { onSelect() }
                        )
                    }
                } else Modifier
            )
            .then(
                if (isSelected) {
                    Modifier.pointerInput(canvasScale) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            // Convert screen drag to canvas coordinates
                            canvasX += dragAmount.x / canvasScale
                            canvasY += dragAmount.y / canvasScale
                        }
                    }
                } else Modifier
            )
    ) {
        // Card content - only enable interaction in move mode
        when (card.type) {
            CardType.TEXT -> TextCardContent(card, interactionEnabled = isInMoveMode)
            CardType.IMAGE -> ImageCardContent(card, interactionEnabled = isInMoveMode)
            CardType.WEB -> WebCardContent(card, interactionEnabled = isInMoveMode)
            CardType.VIDEO -> VideoCardContent(card, interactionEnabled = isInMoveMode)
        }
    }

    // Action buttons (shown when selected) - rendered separately with their own offset
    if (isSelected) {
        val buttonSpacingPx = with(density) { 12.dp.toPx() }
        val buttonOffsetYPx = with(density) { 48.dp.toPx() }

        Column(
            modifier = Modifier
                .offset {
                    IntOffset(
                        (screenX + screenWidthPx + buttonSpacingPx).roundToInt(),
                        (screenY + (screenHeightPx / 2) - buttonOffsetYPx).roundToInt()
                    )
                },
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Delete button
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .size(40.dp)
                    .shadow(4.dp, CircleShape)
                    .background(Color(0xFFF44336), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Expand button
            IconButton(
                onClick = { isExpanded = true },
                modifier = Modifier
                    .size(40.dp)
                    .shadow(4.dp, CircleShape)
                    .background(Color.White, CircleShape)
                    .border(1.dp, Color.LightGray, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Filled.OpenInFull,
                    contentDescription = "Expand",
                    tint = Color.DarkGray,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@kotlin.OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ExpandedCardDialog(
    card: CanvasCard,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onPlay: (Bitmap, List<DrawingPath>) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val view = LocalView.current

    // Magic ink drawing state
    val magicPaths = remember { mutableStateListOf<DrawingPath>() }
    var currentPath by remember { mutableStateOf<DrawingPath?>(null) }
    var isGenerating by remember { mutableStateOf(false) }

    // Card content area position for snapshot
    var contentX by remember { mutableStateOf(0) }
    var contentY by remember { mutableStateOf(0) }
    var contentWidth by remember { mutableStateOf(0) }
    var contentHeight by remember { mutableStateOf(0) }

    // Shimmer animation for magic ink
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerTime by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTime"
    )

    // Loading rotation animation
    val loadingRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Snapshot function
    val takeSnapshot: () -> Bitmap? = {
        try {
            val window = activity?.window
            if (contentWidth > 0 && contentHeight > 0 && window != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val bitmap = Bitmap.createBitmap(contentWidth, contentHeight, Bitmap.Config.ARGB_8888)
                val rect = android.graphics.Rect(contentX, contentY, contentX + contentWidth, contentY + contentHeight)

                var copyResult: Int = PixelCopy.ERROR_UNKNOWN
                val latch = java.util.concurrent.CountDownLatch(1)
                val handlerThread = android.os.HandlerThread("PixelCopyThread")
                handlerThread.start()
                val handler = Handler(handlerThread.looper)

                PixelCopy.request(window, rect, bitmap, { result ->
                    copyResult = result
                    latch.countDown()
                }, handler)

                val completed = latch.await(2, java.util.concurrent.TimeUnit.SECONDS)
                handlerThread.quitSafely()

                if (completed && copyResult == PixelCopy.SUCCESS) bitmap else null
            } else null
        } catch (e: Exception) {
            null
        }
    }

    Dialog(
        onDismissRequest = { if (!isGenerating) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = !isGenerating,
            dismissOnClickOutside = !isGenerating
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(enabled = !isGenerating, onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable(enabled = false, onClick = {})
            ) {
                // Main content area with card + drawing overlay
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Card content with drawing overlay
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.75f)
                            .fillMaxHeight(0.65f)
                            .shadow(30.dp, RoundedCornerShape(16.dp))
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White)
                            .onGloballyPositioned { coordinates ->
                                val position = coordinates.positionInWindow()
                                contentX = position.x.toInt()
                                contentY = position.y.toInt()
                                contentWidth = coordinates.size.width
                                contentHeight = coordinates.size.height
                            }
                    ) {
                        // Card content
                        when (card.type) {
                            CardType.TEXT -> TextCardContent(card, interactionEnabled = false, expanded = true)
                            CardType.IMAGE -> ImageCardContent(card, interactionEnabled = false, expanded = true)
                            CardType.WEB -> WebCardContent(card, interactionEnabled = false, expanded = true)
                            CardType.VIDEO -> VideoCardContent(card, interactionEnabled = false, expanded = true)
                        }

                        // Magic ink drawing overlay
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInteropFilter { event ->
                                    when (event.actionMasked) {
                                        android.view.MotionEvent.ACTION_DOWN -> {
                                            currentPath = DrawingPath(
                                                points = mutableListOf(PathPoint(event.x, event.y, event.pressure)),
                                                isMagicInk = true,
                                                color = 0xFF4CAF50.toInt()
                                            )
                                            true
                                        }
                                        android.view.MotionEvent.ACTION_MOVE -> {
                                            currentPath?.let { path ->
                                                path.points.add(PathPoint(event.x, event.y, event.pressure))
                                                currentPath = path.copy()
                                            }
                                            true
                                        }
                                        android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                                            currentPath?.let { path ->
                                                if (path.points.size > 1) {
                                                    magicPaths.add(path)
                                                }
                                            }
                                            currentPath = null
                                            true
                                        }
                                        else -> false
                                    }
                                }
                        ) {
                            // Draw magic paths with shimmer
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                // Draw completed magic paths
                                magicPaths.forEach { path ->
                                    drawMagicPath(path)
                                    drawShimmerEffect(path, shimmerTime)
                                }
                                // Draw current path
                                currentPath?.let { path ->
                                    drawMagicPath(path)
                                    drawShimmerEffect(path, shimmerTime)
                                }
                            }
                        }
                    }

                    // Action buttons to the right
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Delete button
                        IconButton(
                            onClick = onDelete,
                            enabled = !isGenerating,
                            modifier = Modifier
                                .size(48.dp)
                                .shadow(8.dp, CircleShape)
                                .background(Color(0xFFF44336), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "Delete",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Contract button
                        IconButton(
                            onClick = onDismiss,
                            enabled = !isGenerating,
                            modifier = Modifier
                                .size(48.dp)
                                .shadow(8.dp, CircleShape)
                                .background(Color.White, CircleShape)
                                .border(1.dp, Color.LightGray, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CloseFullscreen,
                                contentDescription = "Contract",
                                tint = Color.DarkGray,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Toolbar at the bottom
                Surface(
                    modifier = Modifier
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
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Magic ink indicator
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color.Black
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.AutoAwesome,
                                    contentDescription = "Magic Ink",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Magic",
                                    fontSize = 12.sp,
                                    color = Color.White
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Clear magic ink button
                        if (magicPaths.isNotEmpty()) {
                            IconButton(
                                onClick = { magicPaths.clear() },
                                enabled = !isGenerating,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Backspace,
                                    contentDescription = "Clear",
                                    tint = Color.Gray
                                )
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        // Play button
                        Button(
                            onClick = {
                                if (magicPaths.isNotEmpty()) {
                                    isGenerating = true
                                    val snapshot = takeSnapshot()
                                    if (snapshot != null) {
                                        onPlay(snapshot, magicPaths.toList())
                                    }
                                    isGenerating = false
                                }
                            },
                            enabled = !isGenerating && magicPaths.isNotEmpty(),
                            modifier = Modifier.size(56.dp),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Black,
                                disabledContainerColor = Color.Black.copy(alpha = 0.5f)
                            ),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            if (isGenerating) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .rotate(loadingRotation),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = "Play",
                                    tint = if (magicPaths.isNotEmpty()) Color.White else Color.Gray,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Draw a magic path on the canvas
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMagicPath(path: DrawingPath) {
    if (path.points.size < 2) return

    val androidPath = Path()
    val firstPoint = path.points.first()
    androidPath.moveTo(firstPoint.x, firstPoint.y)

    for (i in 1 until path.points.size) {
        val point = path.points[i]
        val prevPoint = path.points[i - 1]
        val midX = (prevPoint.x + point.x) / 2
        val midY = (prevPoint.y + point.y) / 2
        androidPath.quadraticBezierTo(prevPoint.x, prevPoint.y, midX, midY)
    }

    val lastPoint = path.points.last()
    androidPath.lineTo(lastPoint.x, lastPoint.y)

    drawPath(
        path = androidPath,
        color = Color(path.color),
        style = Stroke(
            width = path.strokeWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
}

// Draw shimmer effect on magic ink
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawShimmerEffect(
    path: DrawingPath,
    time: Float
) {
    if (path.points.size < 2) return

    val random = Random(path.hashCode())
    val numShimmers = 6
    val baseColor = Color(path.color)

    for (shimmerIndex in 0 until numShimmers) {
        val shimmerSeed = random.nextInt(1000)
        val speed = 0.02f + random.nextFloat() * 0.03f
        val shimmerPosition = ((time * speed + shimmerSeed) % 100f) / 100f

        val pointIndex = (shimmerPosition * (path.points.size - 1)).toInt()
            .coerceIn(0, path.points.size - 1)

        val phase = ((time * 0.1f + shimmerSeed) % 50f)
        val intensity = when {
            phase < 15f -> phase / 15f
            phase < 35f -> 1f
            else -> 1f - (phase - 35f) / 15f
        }.coerceIn(0f, 1f)

        val segmentRadius = 3
        val startIdx = maxOf(0, pointIndex - segmentRadius)
        val endIdx = minOf(path.points.size - 1, pointIndex + segmentRadius)

        if (endIdx > startIdx) {
            val shimmerPath = Path()
            shimmerPath.moveTo(path.points[startIdx].x, path.points[startIdx].y)

            for (i in startIdx + 1..endIdx) {
                val point = path.points[i]
                val prevPoint = path.points[i - 1]
                val midX = (prevPoint.x + point.x) / 2
                val midY = (prevPoint.y + point.y) / 2
                shimmerPath.quadraticBezierTo(prevPoint.x, prevPoint.y, midX, midY)
            }
            shimmerPath.lineTo(path.points[endIdx].x, path.points[endIdx].y)

            val highlightColor = Color(
                red = minOf(1f, baseColor.red + 0.3f * intensity),
                green = minOf(1f, baseColor.green + 0.2f * intensity),
                blue = minOf(1f, baseColor.blue + 0.1f * intensity),
                alpha = 0.6f * intensity
            )

            drawPath(
                path = shimmerPath,
                color = highlightColor,
                style = Stroke(
                    width = path.strokeWidth * 0.6f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}

@Composable
private fun TextCardContent(card: CanvasCard, interactionEnabled: Boolean = true, expanded: Boolean = false) {
    val context = LocalContext.current
    val markdownText = card.text ?: ""

    // Use BoxWithConstraints to get actual size and pass to AndroidView
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(if (expanded) 24.dp else 12.dp)
    ) {
        val widthPx = with(LocalDensity.current) { maxWidth.toPx().toInt() }
        val heightPx = with(LocalDensity.current) { maxHeight.toPx().toInt() }

        // Use AndroidView with Markwon for markdown rendering
        // Key on size to force recreation when dimensions change
        key(widthPx, heightPx) {
            AndroidView(
                factory = { ctx ->
                    android.widget.TextView(ctx).apply {
                        val markwon = io.noties.markwon.Markwon.create(ctx)
                        markwon.setMarkdown(this, markdownText)
                        textSize = if (expanded) 18f else 14f
                        setTextColor(android.graphics.Color.BLACK)
                    }
                },
                update = { textView ->
                    val markwon = io.noties.markwon.Markwon.create(context)
                    markwon.setMarkdown(textView, markdownText)
                    textView.textSize = if (expanded) 18f else 14f
                    textView.isClickable = interactionEnabled
                    textView.isFocusable = interactionEnabled
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun ImageCardContent(card: CanvasCard, interactionEnabled: Boolean = true, expanded: Boolean = false) {
    val context = LocalContext.current

    if (card.localBitmap != null) {
        AsyncImage(
            model = card.localBitmap,
            contentDescription = "Image",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    } else if (card.imageUrl != null) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(card.imageUrl)
                .crossfade(true)
                .build(),
            contentDescription = "Image",
            modifier = Modifier.fillMaxSize(),
            contentScale = if (expanded) ContentScale.Fit else ContentScale.Crop
        )
    }
}

@Composable
private fun WebCardContent(card: CanvasCard, interactionEnabled: Boolean = true, expanded: Boolean = false) {
    val htmlContent = card.htmlContent ?: return

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                webViewClient = WebViewClient()
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
                // Disable touch events initially
                setOnTouchListener { _, _ -> !interactionEnabled && !expanded }
            }
        },
        update = { webView ->
            // Only allow interaction when expanded or in move mode
            webView.isEnabled = expanded
            webView.setOnTouchListener { _, _ -> !interactionEnabled && !expanded }
        },
        modifier = Modifier.fillMaxSize()
    )
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoCardContent(card: CanvasCard, interactionEnabled: Boolean = true, expanded: Boolean = false) {
    val context = LocalContext.current
    val videoUrl = card.videoUrl

    if (videoUrl == null) {
        // Show placeholder if no video URL
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.PlayCircle,
                contentDescription = "Video",
                tint = Color.White,
                modifier = Modifier.size(if (expanded) 80.dp else 48.dp)
            )
        }
        return
    }

    // Only show video player when expanded, otherwise show thumbnail with play button
    if (expanded) {
        // Create and remember ExoPlayer instance
        val exoPlayer = remember {
            ExoPlayer.Builder(context).build().apply {
                val mediaItem = MediaItem.fromUri(Uri.parse(videoUrl))
                setMediaItem(mediaItem)
                prepare()
            }
        }

        // Cleanup player when composable is disposed
        DisposableEffect(Unit) {
            onDispose {
                exoPlayer.release()
            }
        }

        // ExoPlayer view
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        )
    } else {
        // Show thumbnail with play button for non-expanded view
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.PlayCircle,
                contentDescription = "Play Video",
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(48.dp)
            )
        }
    }
}
