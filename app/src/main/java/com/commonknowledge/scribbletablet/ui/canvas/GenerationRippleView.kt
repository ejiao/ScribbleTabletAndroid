package com.commonknowledge.scribbletablet.ui.canvas

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay
import kotlin.math.max

/**
 * Ripple animation view shown during AI generation.
 * Displays expanding radial gradient ripples emanating from the center.
 *
 * Matches iOS GenerationRippleView behavior:
 * - 3 concurrent ripples on staggered timing
 * - Green color with radial gradient
 * - 2.5 seconds duration per ripple
 * - 0.8 seconds between ripples
 * - Alpha fade-out during expansion
 */
@Composable
fun GenerationRippleView(
    isVisible: Boolean,
    modifier: Modifier = Modifier
) {
    if (!isVisible) return

    // Track active ripples with their start times
    var rippleId by remember { mutableStateOf(0) }
    val activeRipples = remember { mutableStateListOf<Int>() }

    // Launch new ripples every 800ms
    LaunchedEffect(isVisible) {
        while (isVisible) {
            if (activeRipples.size < 3) {
                activeRipples.add(rippleId++)
            }
            delay(800)
        }
    }

    // Clean up old ripples
    LaunchedEffect(activeRipples.size) {
        if (activeRipples.size > 3) {
            activeRipples.removeAt(0)
        }
    }

    // Render each ripple
    activeRipples.forEach { id ->
        SingleRipple(
            key = id,
            onComplete = { activeRipples.remove(id) },
            modifier = modifier
        )
    }
}

@Composable
private fun SingleRipple(
    key: Int,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animationProgress = remember { Animatable(0f) }

    LaunchedEffect(key) {
        animationProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 2500,
                easing = FastOutSlowInEasing
            )
        )
        onComplete()
    }

    val progress = animationProgress.value
    val alpha = 1f - progress // Fade out as ripple expands

    Canvas(modifier = modifier.fillMaxSize()) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        val maxDimension = max(size.width, size.height)

        // Ripple expands from 20% to 180% of viewport
        val startRadius = maxDimension * 0.1f
        val endRadius = maxDimension * 0.9f
        val currentRadius = startRadius + (endRadius - startRadius) * progress

        // Darker green color for better visibility
        val rippleColor = Color(0xFF1B5E20) // Dark green

        // Draw radial gradient ripple
        val gradient = Brush.radialGradient(
            colors = listOf(
                rippleColor.copy(alpha = alpha * 0.7f),
                rippleColor.copy(alpha = alpha * 0.4f),
                rippleColor.copy(alpha = 0f)
            ),
            center = Offset(centerX, centerY),
            radius = currentRadius
        )

        drawCircle(
            brush = gradient,
            radius = currentRadius,
            center = Offset(centerX, centerY)
        )
    }
}
