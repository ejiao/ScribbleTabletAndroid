package com.commonknowledge.scribbletablet.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.commonknowledge.scribbletablet.ui.canvas.DrawingCanvas
import com.commonknowledge.scribbletablet.ui.cards.CardView
import com.commonknowledge.scribbletablet.ui.toolbar.CanvasToolbar
import com.commonknowledge.scribbletablet.viewmodel.CanvasViewModel

@Composable
fun CanvasScreen(
    viewModel: CanvasViewModel = viewModel()
) {
    val errorMessage = viewModel.errorMessage.value

    Box(modifier = Modifier.fillMaxSize()) {
        // Drawing canvas
        DrawingCanvas(
            viewModel = viewModel,
            modifier = Modifier.fillMaxSize()
        )

        // Cards overlay
        viewModel.cards.forEach { card ->
            CardView(
                card = card,
                onDelete = { viewModel.removeCard(card.id) }
            )
        }

        // Toolbar at bottom
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
        ) {
            CanvasToolbar(viewModel = viewModel)
        }

        // Error snackbar
        if (errorMessage != null) {
            Snackbar(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 48.dp),
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
