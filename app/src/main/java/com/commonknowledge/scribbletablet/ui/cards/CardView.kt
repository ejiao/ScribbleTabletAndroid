package com.commonknowledge.scribbletablet.ui.cards

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.commonknowledge.scribbletablet.data.model.CanvasCard
import com.commonknowledge.scribbletablet.data.model.CardType
import kotlin.math.roundToInt

@Composable
fun CardView(
    card: CanvasCard,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var offsetX by remember { mutableFloatStateOf(card.rect.left) }
    var offsetY by remember { mutableFloatStateOf(card.rect.top) }

    Box(
        modifier = modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .size(
                width = card.rect.width().dp,
                height = card.rect.height().dp
            )
            .shadow(8.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .border(2.dp, Color.LightGray, RoundedCornerShape(12.dp))
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    offsetX += dragAmount.x
                    offsetY += dragAmount.y
                }
            }
    ) {
        when (card.type) {
            CardType.TEXT -> TextCardContent(card)
            CardType.IMAGE -> ImageCardContent(card)
            CardType.WEB -> WebCardContent(card)
            CardType.VIDEO -> VideoCardContent(card)
        }

        // Delete button
        IconButton(
            onClick = onDelete,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(28.dp)
                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Delete",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun TextCardContent(card: CanvasCard) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        Text(
            text = card.text ?: "",
            fontSize = 14.sp,
            color = Color.Black,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ImageCardContent(card: CanvasCard) {
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
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
private fun WebCardContent(card: CanvasCard) {
    val htmlContent = card.htmlContent ?: return

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                webViewClient = WebViewClient()
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun VideoCardContent(card: CanvasCard) {
    // Placeholder for video content
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Video",
            color = Color.White
        )
    }
}
