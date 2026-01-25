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
import com.commonknowledge.scribbletablet.ui.canvas.GenerationRippleView
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.AbsoluteSizeSpan
import android.text.style.LineHeightSpan
import android.text.style.StyleSpan
import android.graphics.Paint
import android.graphics.Typeface
import kotlin.math.roundToInt
import kotlin.random.Random
import android.app.Activity
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.MarkwonSpansFactory
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.SoftBreakAddsNewLinePlugin
import org.commonmark.node.Heading

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
    onExpand: () -> Unit = {},
    onPositionChanged: (Float, Float) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    // Card rect is in canvas coordinates - track canvas position for dragging
    // Key on card.id AND card.rect to update when the card's position changes externally
    var canvasX by remember(card.id, card.rect.left) { mutableStateOf(card.rect.left) }
    var canvasY by remember(card.id, card.rect.top) { mutableStateOf(card.rect.top) }

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
                if (isSelected) {
                    Modifier.pointerInput(canvasScale) {
                        detectDragGestures(
                            onDragEnd = {
                                // Persist the new position when drag ends
                                onPositionChanged(canvasX, canvasY)
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                // Convert screen drag to canvas coordinates
                                canvasX += dragAmount.x / canvasScale
                                canvasY += dragAmount.y / canvasScale
                            }
                        )
                    }
                } else Modifier
            )
    ) {
        // Card content
        when (card.type) {
            CardType.TEXT -> TextCardContent(card, interactionEnabled = !isInMoveMode, expanded = false)
            CardType.IMAGE -> ImageCardContent(card, interactionEnabled = !isInMoveMode, expanded = false)
            CardType.WEB -> WebCardContent(card, interactionEnabled = !isInMoveMode, expanded = false)
            CardType.VIDEO -> VideoCardContent(card, interactionEnabled = !isInMoveMode, expanded = false)
            CardType.AUDIO -> AudioCardContent(card, interactionEnabled = !isInMoveMode, expanded = false)
        }

        // Touch-intercepting overlay when in move mode
        // This sits on top of the content and captures all taps for selection,
        // preventing AndroidView components (TextView, WebView) from intercepting
        if (isInMoveMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null, // No ripple effect
                        onClick = { onSelect() }
                    )
            )
        }
    }

    // Action buttons (shown when selected) - rendered separately with their own offset
    if (isSelected) {
        val context = LocalContext.current
        val buttonSpacingPx = with(density) { 12.dp.toPx() }
        val buttonOffsetYPx = with(density) { 72.dp.toPx() }

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
                onClick = onExpand,
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

            // Download button (for media cards)
            if (card.type in listOf(CardType.IMAGE, CardType.VIDEO, CardType.AUDIO)) {
                IconButton(
                    onClick = {
                        // Download the media file
                        val url = when (card.type) {
                            CardType.IMAGE -> card.imageUrl
                            CardType.VIDEO -> card.videoUrl
                            CardType.AUDIO -> card.audioUrl
                            else -> null
                        }
                        url?.let {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                data = android.net.Uri.parse(it)
                            }
                            context.startActivity(intent)
                        }
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .shadow(4.dp, CircleShape)
                        .background(Color.White, CircleShape)
                        .border(1.dp, Color.LightGray, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Download,
                        contentDescription = "Download",
                        tint = Color.DarkGray,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Edit button (for text cards)
            if (card.type == CardType.TEXT) {
                IconButton(
                    onClick = onExpand, // Open expanded view for editing
                    modifier = Modifier
                        .size(40.dp)
                        .shadow(4.dp, CircleShape)
                        .background(Color.White, CircleShape)
                        .border(1.dp, Color.LightGray, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Edit",
                        tint = Color.DarkGray,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * Expanded card overlay - renders as a regular composable (not a Dialog)
 * so it can be layered below the toolbar.
 */
@kotlin.OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ExpandedCardOverlay(
    card: CanvasCard,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    isGenerating: Boolean = false,
    magicPaths: List<DrawingPath>,
    onAddMagicPath: (DrawingPath) -> Unit,
    modifier: Modifier = Modifier
) {
    // Current path being drawn (local state, added to magicPaths on completion)
    var currentPath by remember { mutableStateOf<DrawingPath?>(null) }

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

    // Background overlay - only dismisses when clicking the background area
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { if (!isGenerating) onDismiss() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Content area - stops click propagation to background
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.pointerInput(Unit) {
                // Consume taps on the content to prevent dismissing
                detectTapGestures { }
            }
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
                ) {
                    // Card content
                    when (card.type) {
                        CardType.TEXT -> TextCardContent(card, interactionEnabled = false, expanded = true)
                        CardType.IMAGE -> ImageCardContent(card, interactionEnabled = false, expanded = true)
                        CardType.WEB -> WebCardContent(card, interactionEnabled = false, expanded = true)
                        CardType.VIDEO -> VideoCardContent(card, interactionEnabled = false, expanded = true)
                        CardType.AUDIO -> AudioCardContent(card, interactionEnabled = false, expanded = true)
                    }

                    // Magic ink drawing overlay (hidden during generation for snapshot)
                    // Only show for non-media cards to allow video/audio player interaction
                    val isMediaCard = card.type == CardType.VIDEO || card.type == CardType.AUDIO
                    if (!isGenerating && !isMediaCard) {
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
                                                    onAddMagicPath(path)
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

                    // Generation ripple animation overlay
                    GenerationRippleView(
                        isVisible = isGenerating,
                        modifier = Modifier.fillMaxSize()
                    )
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

            // Bottom spacer for toolbar clearance
            Spacer(modifier = Modifier.height(100.dp))
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
    val baseTextSize = if (expanded) 18f else 14f

    // Use BoxWithConstraints to get actual size and pass to AndroidView
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(if (expanded) 24.dp else 12.dp)
    ) {
        val widthPx = with(LocalDensity.current) { maxWidth.toPx().toInt() }
        val heightPx = with(LocalDensity.current) { maxHeight.toPx().toInt() }
        val density = LocalDensity.current.density

        // Use AndroidView with Markwon for markdown rendering
        // Key on size to force recreation when dimensions change
        key(widthPx, heightPx) {
            AndroidView(
                factory = { ctx ->
                    android.widget.TextView(ctx).apply {
                        val markwon = createMarkwon(ctx, baseTextSize, density)
                        markwon.setMarkdown(this, markdownText)
                        textSize = baseTextSize
                        setTextColor(android.graphics.Color.BLACK)
                        // Set default line spacing for body text (1.4em)
                        setLineSpacing(baseTextSize * density * 0.4f, 1f)
                        // Disable touch handling so card selection works
                        isClickable = false
                        isFocusable = false
                        isLongClickable = false
                    }
                },
                update = { textView ->
                    val markwon = createMarkwon(context, baseTextSize, density)
                    markwon.setMarkdown(textView, markdownText)
                    textView.textSize = baseTextSize
                    textView.setLineSpacing(baseTextSize * density * 0.4f, 1f)
                    // Always disable touch so card selection/dragging works
                    textView.isClickable = false
                    textView.isFocusable = false
                    textView.isLongClickable = false
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Creates a Markwon instance with proper header styling and line spacing.
 */
private fun createMarkwon(context: android.content.Context, baseTextSize: Float, density: Float): io.noties.markwon.Markwon {
    return io.noties.markwon.Markwon.builder(context)
        // Make single newlines render as line breaks (not just spaces)
        .usePlugin(SoftBreakAddsNewLinePlugin.create())
        .usePlugin(object : AbstractMarkwonPlugin() {
            override fun configureTheme(builder: MarkwonTheme.Builder) {
                // Configure heading sizes relative to base text size
                builder
                    .headingTextSizeMultipliers(floatArrayOf(
                        2.0f,   // H1 - 2x base size
                        1.75f,  // H2 - 1.75x base size
                        1.5f,   // H3 - 1.5x base size
                        1.25f,  // H4 - 1.25x base size
                        1.1f,   // H5 - 1.1x base size
                        1.0f    // H6 - same as base
                    ))
                    .headingTypeface(Typeface.DEFAULT_BOLD)
            }

            override fun afterSetText(textView: android.widget.TextView) {
                // Apply custom line spacing to headers (1.2em) vs body (1.4em)
                val text = textView.text
                if (text is Spannable) {
                    applyHeaderLineSpacing(text, baseTextSize, density)
                }
            }
        })
        .build()
}

/**
 * Applies different line spacing to headers (1.2em) vs body text (1.4em).
 */
private fun applyHeaderLineSpacing(spannable: Spannable, baseTextSize: Float, density: Float) {
    // Get all AbsoluteSizeSpan to identify headers (they have larger text sizes)
    val sizeSpans = spannable.getSpans(0, spannable.length, AbsoluteSizeSpan::class.java)

    for (span in sizeSpans) {
        val start = spannable.getSpanStart(span)
        val end = spannable.getSpanEnd(span)

        // Headers have larger sizes, apply 1.2em line height
        val headerLineHeight = (span.size * 1.2f).toInt()

        if (spannable is SpannableStringBuilder) {
            spannable.setSpan(
                object : LineHeightSpan {
                    override fun chooseHeight(
                        text: CharSequence?,
                        start: Int,
                        end: Int,
                        spanstartv: Int,
                        lineHeight: Int,
                        fm: Paint.FontMetricsInt?
                    ) {
                        fm?.let {
                            val currentHeight = it.descent - it.ascent
                            if (currentHeight < headerLineHeight) {
                                val diff = headerLineHeight - currentHeight
                                it.descent += diff / 2
                                it.ascent -= diff / 2
                            }
                        }
                    }
                },
                start,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
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
                // Only allow touch events when expanded - block otherwise so card selection works
                setOnTouchListener { _, _ -> !expanded }
            }
        },
        update = { webView ->
            // Only allow interaction when expanded
            webView.isEnabled = expanded
            // Block touches when not expanded so card selection/dragging works
            webView.setOnTouchListener { _, _ -> !expanded }
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
        val exoPlayer = remember(videoUrl) {
            ExoPlayer.Builder(context).build().apply {
                val mediaItem = MediaItem.fromUri(Uri.parse(videoUrl))
                setMediaItem(mediaItem)
                prepare()
                playWhenReady = false // Don't auto-play, let user control
            }
        }

        // Cleanup player when composable is disposed
        DisposableEffect(exoPlayer) {
            onDispose {
                exoPlayer.release()
            }
        }

        // ExoPlayer view with full controls
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    controllerShowTimeoutMs = 5000
                    controllerHideOnTouch = false // Keep controls visible
                    setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { })
                }
            },
            update = { playerView ->
                playerView.player = exoPlayer
            },
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        )
    } else {
        // Show thumbnail with play button for non-expanded view
        var thumbnail by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

        // Generate thumbnail from video
        LaunchedEffect(videoUrl) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val retriever = android.media.MediaMetadataRetriever()
                    if (videoUrl.startsWith("http://") || videoUrl.startsWith("https://")) {
                        retriever.setDataSource(videoUrl, HashMap())
                    } else {
                        retriever.setDataSource(context, Uri.parse(videoUrl))
                    }
                    thumbnail = retriever.getFrameAtTime(0, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    retriever.release()
                } catch (e: Exception) {
                    // Ignore thumbnail errors
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            // Show thumbnail if available
            thumbnail?.let { bmp ->
                AsyncImage(
                    model = bmp,
                    contentDescription = "Video thumbnail",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            // Play button overlay
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Play Video",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun AudioCardContent(card: CanvasCard, interactionEnabled: Boolean = true, expanded: Boolean = false) {
    val context = LocalContext.current
    val audioUrl = card.audioUrl

    if (audioUrl == null) {
        // Show placeholder if no audio URL
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1E1E1E)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = "Audio",
                    tint = Color.White,
                    modifier = Modifier.size(if (expanded) 64.dp else 40.dp)
                )
                if (expanded) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No audio",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp
                    )
                }
            }
        }
        return
    }

    // Show audio player when expanded, otherwise show compact view
    if (expanded) {
        // Create and remember ExoPlayer instance for audio
        val exoPlayer = remember(audioUrl) {
            ExoPlayer.Builder(context).build().apply {
                val mediaItem = MediaItem.fromUri(Uri.parse(audioUrl))
                setMediaItem(mediaItem)
                prepare()
                playWhenReady = false
            }
        }

        var isPlaying by remember { mutableStateOf(false) }
        var currentPosition by remember { mutableStateOf(0L) }
        var duration by remember { mutableStateOf(card.audioDuration ?: 0L) }

        // Listen for player state changes
        DisposableEffect(exoPlayer) {
            val listener = object : androidx.media3.common.Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == androidx.media3.common.Player.STATE_ENDED) {
                        isPlaying = false
                        exoPlayer.seekTo(0)
                    }
                    if (playbackState == androidx.media3.common.Player.STATE_READY && exoPlayer.duration > 0) {
                        duration = exoPlayer.duration
                    }
                }
            }
            exoPlayer.addListener(listener)

            onDispose {
                exoPlayer.removeListener(listener)
                exoPlayer.release()
            }
        }

        // Update position periodically while playing
        LaunchedEffect(isPlaying) {
            while (isPlaying) {
                currentPosition = exoPlayer.currentPosition
                kotlinx.coroutines.delay(50)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1E1E1E))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Waveform visualization with seek support
                AudioWaveformVisualization(
                    progress = if (duration > 0) currentPosition.toFloat() / duration else 0f,
                    onSeek = { progress ->
                        val seekPosition = (progress * duration).toLong()
                        exoPlayer.seekTo(seekPosition)
                        currentPosition = seekPosition
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Time display
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatDuration(currentPosition),
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                    Text(
                        text = formatDuration(duration),
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Playback controls
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Rewind 10 seconds
                    IconButton(
                        onClick = {
                            val newPos = maxOf(0, exoPlayer.currentPosition - 10000)
                            exoPlayer.seekTo(newPos)
                            currentPosition = newPos
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color.White.copy(alpha = 0.1f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Replay10,
                            contentDescription = "Rewind 10s",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Play/Pause button
                    IconButton(
                        onClick = {
                            if (isPlaying) {
                                exoPlayer.pause()
                            } else {
                                exoPlayer.play()
                            }
                        },
                        modifier = Modifier
                            .size(72.dp)
                            .background(Color.White, CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.Black,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // Forward 10 seconds
                    IconButton(
                        onClick = {
                            val newPos = minOf(duration, exoPlayer.currentPosition + 10000)
                            exoPlayer.seekTo(newPos)
                            currentPosition = newPos
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color.White.copy(alpha = 0.1f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Forward10,
                            contentDescription = "Forward 10s",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    } else {
        // Compact view with audio icon, waveform preview, and duration
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1E1E1E))
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Play button circle
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Play Audio",
                        tint = Color.Black,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Mini waveform and duration
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    // Mini waveform
                    AudioWaveformVisualization(
                        progress = 0f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                    )

                    card.audioDuration?.let { duration ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = formatDuration(duration),
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioWaveformVisualization(
    progress: Float,
    modifier: Modifier = Modifier,
    onSeek: ((Float) -> Unit)? = null
) {
    val barCount = 40
    val random = remember { java.util.Random(42) } // Fixed seed for consistent waveform
    val barHeights = remember {
        (0 until barCount).map { 0.2f + random.nextFloat() * 0.8f }
    }

    val seekModifier = if (onSeek != null) {
        modifier.pointerInput(Unit) {
            detectTapGestures { offset ->
                val seekProgress = (offset.x / size.width).coerceIn(0f, 1f)
                onSeek(seekProgress)
            }
        }
    } else {
        modifier
    }

    Canvas(modifier = seekModifier) {
        val barWidth = size.width / (barCount * 2f)
        val maxBarHeight = size.height * 0.8f

        for (i in 0 until barCount) {
            val barHeight = barHeights[i] * maxBarHeight
            val x = i * (barWidth * 2) + barWidth / 2
            val y = (size.height - barHeight) / 2

            val isPlayed = (i.toFloat() / barCount) < progress
            val color = if (isPlayed) Color(0xFF4CAF50) else Color.White.copy(alpha = 0.3f)

            drawRoundRect(
                color = color,
                topLeft = androidx.compose.ui.geometry.Offset(x, y),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
            )
        }
    }
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
