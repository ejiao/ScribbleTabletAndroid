package com.commonknowledge.scribbletablet.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.io.File
import kotlin.random.Random

/**
 * Audio recorder view with waveform visualization.
 * Matches iOS AudioRecorderView behavior.
 */
@Composable
fun AudioRecorderView(
    onDismiss: () -> Unit,
    onRecordingComplete: (File, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Permission state
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
    }

    // Recording state
    var isRecording by remember { mutableStateOf(false) }
    var recordingDuration by remember { mutableStateOf(0L) }
    var audioLevels by remember { mutableStateOf(List(30) { 0.1f }) }
    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var outputFile by remember { mutableStateOf<File?>(null) }

    // Timer for recording duration
    LaunchedEffect(isRecording) {
        if (isRecording) {
            while (isRecording) {
                delay(100)
                recordingDuration += 100

                // Simulate audio levels (in production, use actual audio amplitude)
                audioLevels = audioLevels.drop(1) + listOf(
                    if (isRecording) 0.2f + Random.nextFloat() * 0.6f else 0.1f
                )
            }
        }
    }

    // Request permission on first load
    LaunchedEffect(Unit) {
        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            mediaRecorder?.apply {
                try {
                    stop()
                    release()
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = Color.White,
        shadowElevation = 16.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Record Audio",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (!hasPermission) {
                // Permission denied state
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Filled.MicOff,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Microphone access required",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                    ) {
                        Text("Grant Permission")
                    }
                }
            } else {
                // Waveform visualization
                AudioWaveformBars(
                    levels = audioLevels,
                    isRecording = isRecording,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Duration display
                Text(
                    text = formatRecordingTime(recordingDuration),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Light,
                    color = if (isRecording) Color(0xFFE53935) else Color.Black
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Record button
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isRecording) {
                        // Cancel button
                        IconButton(
                            onClick = {
                                mediaRecorder?.apply {
                                    try {
                                        stop()
                                        release()
                                    } catch (e: Exception) {
                                        // Ignore
                                    }
                                }
                                mediaRecorder = null
                                outputFile?.delete()
                                outputFile = null
                                isRecording = false
                                recordingDuration = 0
                                audioLevels = List(30) { 0.1f }
                            },
                            modifier = Modifier
                                .size(56.dp)
                                .background(Color.LightGray.copy(alpha = 0.3f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Cancel",
                                tint = Color.DarkGray,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    // Main record/stop button
                    IconButton(
                        onClick = {
                            if (isRecording) {
                                // Stop recording
                                mediaRecorder?.apply {
                                    try {
                                        stop()
                                        release()
                                    } catch (e: Exception) {
                                        // Ignore
                                    }
                                }
                                mediaRecorder = null

                                // Return the recorded file
                                outputFile?.let { file ->
                                    if (file.exists() && file.length() > 0) {
                                        onRecordingComplete(file, recordingDuration)
                                    }
                                }

                                isRecording = false
                                onDismiss()
                            } else {
                                // Start recording
                                val file = File(context.cacheDir, "audio_${System.currentTimeMillis()}.m4a")
                                outputFile = file

                                val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    MediaRecorder(context)
                                } else {
                                    @Suppress("DEPRECATION")
                                    MediaRecorder()
                                }

                                recorder.apply {
                                    setAudioSource(MediaRecorder.AudioSource.MIC)
                                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                                    setAudioSamplingRate(44100)
                                    setAudioEncodingBitRate(128000)
                                    setOutputFile(file.absolutePath)

                                    try {
                                        prepare()
                                        start()
                                        mediaRecorder = this
                                        isRecording = true
                                        recordingDuration = 0
                                    } catch (e: Exception) {
                                        release()
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .size(72.dp)
                            .background(
                                if (isRecording) Color(0xFFE53935) else Color(0xFFE53935),
                                CircleShape
                            )
                    ) {
                        if (isRecording) {
                            // Stop icon (square)
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.White)
                            )
                        } else {
                            // Record icon (circle)
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                            )
                        }
                    }

                    if (isRecording) {
                        // Done/confirm button
                        IconButton(
                            onClick = {
                                // Stop and save
                                mediaRecorder?.apply {
                                    try {
                                        stop()
                                        release()
                                    } catch (e: Exception) {
                                        // Ignore
                                    }
                                }
                                mediaRecorder = null

                                outputFile?.let { file ->
                                    if (file.exists() && file.length() > 0) {
                                        onRecordingComplete(file, recordingDuration)
                                    }
                                }

                                isRecording = false
                                onDismiss()
                            },
                            modifier = Modifier
                                .size(56.dp)
                                .background(Color(0xFF4CAF50), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "Done",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioWaveformBars(
    levels: List<Float>,
    isRecording: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val barCount = levels.size
        val barWidth = size.width / (barCount * 2f)
        val maxBarHeight = size.height * 0.8f

        for (i in 0 until barCount) {
            val level = levels[i]
            val barHeight = level * maxBarHeight
            val x = i * (barWidth * 2) + barWidth / 2
            val y = (size.height - barHeight) / 2

            val color = if (isRecording) Color(0xFFE53935) else Color.LightGray

            drawRoundRect(
                color = color,
                topLeft = androidx.compose.ui.geometry.Offset(x, y),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2, barWidth / 2)
            )
        }
    }
}

private fun formatRecordingTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val centiseconds = (millis % 1000) / 10
    return "%02d:%02d.%02d".format(minutes, seconds, centiseconds)
}
