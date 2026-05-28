package com.example

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background
                ) { innerPadding ->
                    SleepTimerScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun SleepTimerScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val isRunning by TimerState.isRunning.collectAsState()
    val remainingMillis by TimerState.remainingMillis.collectAsState()
    val totalMillis by TimerState.totalMillis.collectAsState()
    val isOverlayActive by TimerState.isOverlayActive.collectAsState()

    var selectedMinutes by remember { mutableFloatStateOf(30f) } // Default 30 min
    var fadeVolume by remember { mutableStateOf(true) }
    var dimScreen by remember { mutableStateOf(false) }

    // Start/Stop action
    val toggleTimer: () -> Unit = {
        if (isRunning) {
            val intent = Intent(context, SleepTimerService::class.java).apply {
                action = SleepTimerService.ACTION_STOP
            }
            context.startService(intent)
        } else {
            if (dimScreen && !Settings.canDrawOverlays(context)) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                context.startActivity(intent)
            } else {
                val intent = Intent(context, SleepTimerService::class.java).apply {
                    action = SleepTimerService.ACTION_START
                    putExtra(SleepTimerService.EXTRA_DURATION_MS, (selectedMinutes * 60 * 1000).toLong())
                    putExtra(SleepTimerService.EXTRA_FADE_VOLUME, fadeVolume)
                    putExtra(SleepTimerService.EXTRA_DIM_SCREEN, dimScreen)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Starry Night Animated Background (Fake it with circles)
        StarryBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                text = "Звездный Сон",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(48.dp))

            // Time Selector (Circular Slider)
            Box(contentAlignment = Alignment.Center) {
                if (isRunning) {
                    RunningTimerDisplay(
                        remainingMillis = remainingMillis, 
                        totalMillis = totalMillis,
                        onAddExtraTime = {
                            val intent = Intent(context, SleepTimerService::class.java).apply {
                                action = SleepTimerService.ACTION_ADD_TIME
                            }
                            context.startService(intent)
                        }
                    )
                } else {
                    CircularTimePicker(
                        minutes = selectedMinutes,
                        onMinutesChanged = { selectedMinutes = it }
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Presets row
            if (!isRunning) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf(5f, 15f, 30f, 45f, 60f).forEach { min ->
                        FilterChip(
                            selected = selectedMinutes == min,
                            onClick = { selectedMinutes = min },
                            label = { Text("${min.toInt()} м") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.tertiary,
                                selectedLabelColor = MaterialTheme.colorScheme.onTertiary
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Settings
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Плавное затухание звука", color = MaterialTheme.colorScheme.onSurface)
                        Switch(
                            checked = fadeVolume,
                            onCheckedChange = { fadeVolume = it },
                            enabled = !isRunning
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Отключить экран (AMOLED)", color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                "Экономит заряд. Двойной тап для разблокировки.",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha=0.6f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Switch(
                            checked = dimScreen,
                            onCheckedChange = { dimScreen = it },
                            enabled = !isRunning
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Start/Stop Button
            FloatingActionButton(
                onClick = toggleTimer,
                containerColor = if (isRunning) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                shape = CircleShape,
                modifier = Modifier.size(72.dp)
            ) {
                Icon(
                    imageVector = if (isRunning) Icons.Filled.Close else Icons.Filled.PlayArrow,
                    contentDescription = if (isRunning) "Остановить" else "Запустить",
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun RunningTimerDisplay(remainingMillis: Long, totalMillis: Long, onAddExtraTime: () -> Unit) {
    val progress = if (totalMillis > 0) {
        (remainingMillis.toFloat() / totalMillis.toFloat()).coerceIn(0f, 1f)
    } else 0f
    
    val mins = (remainingMillis / 1000) / 60
    val secs = (remainingMillis / 1000) % 60
    
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(240.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxSize(),
                strokeWidth = 14.dp,
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surface.copy(alpha=0.5f),
                strokeCap = StrokeCap.Round
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = String.format("%02d:%02d", mins, secs),
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Осталось",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha=0.7f)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = onAddExtraTime,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            Text("+ 10 минут")
        }
    }
}

@Composable
fun CircularTimePicker(
    minutes: Float,
    onMinutesChanged: (Float) -> Unit
) {
    // 0 to 120 minutes (2 hours)
    val maxMinutes = 120f
    val progress = minutes / maxMinutes
    val angle = (progress * 360f - 90f) // start from top

    Box(
        modifier = Modifier
            .size(240.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val pos = change.position
                    var newAngle = atan2(pos.y - center.y, pos.x - center.x) * (180f / Math.PI.toFloat())
                    if (newAngle < 0) newAngle += 360f
                    var adjustedAngle = newAngle + 90f
                    if (adjustedAngle >= 360f) adjustedAngle -= 360f
                    
                    val newProgress = adjustedAngle / 360f
                    var newMinutes = newProgress * maxMinutes
                    
                    // Snap to 5 minute intervals
                    newMinutes = Math.round(newMinutes / 5f) * 5f
                    if (newMinutes < 5f) newMinutes = 5f
                    
                    onMinutesChanged(newMinutes)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val strokeColor = MaterialTheme.colorScheme.surface
        val activeColor = MaterialTheme.colorScheme.primary
        val accentColor = MaterialTheme.colorScheme.secondary

        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.width / 2 - 16.dp.toPx()

            // Draw track
            drawCircle(
                color = strokeColor,
                radius = radius,
                style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
            )

            // Draw arc
            drawArc(
                color = activeColor,
                startAngle = -90f,
                sweepAngle = progress * 360f,
                useCenter = false,
                style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
            )

            // Draw thumb
            val thumbX = center.x + radius * cos(angle * (Math.PI / 180f)).toFloat()
            val thumbY = center.y + radius * sin(angle * (Math.PI / 180f)).toFloat()

            // Outer glowing thumb
            drawCircle(
                color = accentColor.copy(alpha = 0.5f),
                radius = 24.dp.toPx(),
                center = Offset(thumbX, thumbY)
            )
            drawCircle(
                color = accentColor,
                radius = 16.dp.toPx(),
                center = Offset(thumbX, thumbY)
            )
            drawCircle(
                color = Color.White,
                radius = 8.dp.toPx(),
                center = Offset(thumbX, thumbY)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${minutes.toInt()}",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "минут",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

@Composable
fun StarryBackground() {
    // Generate static stars to mimic Van Gogh's Starry Night feel
    val stars = remember {
        List(40) {
            Offset(
                (Math.random()).toFloat(),
                (Math.random()).toFloat()
            ) to (Math.random() * 4 + 2).toFloat()
        }
    }
    
    // Animate glowing stars
    val infiniteTransition = rememberInfiniteTransition()
    val glowPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "StarGlow"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        stars.forEachIndexed { index, (posRatio, starSize) ->
            val pos = Offset(posRatio.x * this.size.width, posRatio.y * this.size.height)
            val glow = sin(glowPhase + index) * 0.5f + 0.5f // 0 to 1
            drawCircle(
                color = VanGoghStarGold.copy(alpha = (glow * 0.8f + 0.2f).toFloat()),
                radius = starSize * (glow * 0.5f + 0.8f),
                center = pos
            )
        }
        
        // Swirls - multiple thick overlapping curves
        val path1 = Path().apply {
            moveTo(0f, size.height * 0.25f)
            cubicTo(
                size.width * 0.3f, size.height * 0.4f,
                size.width * 0.6f, -size.height * 0.1f,
                size.width, size.height * 0.4f
            )
        }
        val path2 = Path().apply {
            moveTo(0f, size.height * 0.35f)
            cubicTo(
                size.width * 0.4f, size.height * 0.5f,
                size.width * 0.8f, size.height * 0.1f,
                size.width, size.height * 0.6f
            )
        }
        drawPath(
            path = path1,
            color = VanGoghSwirlBlue.copy(alpha = 0.12f),
            style = Stroke(width = 60.dp.toPx(), cap = StrokeCap.Round)
        )
        drawPath(
            path = path2,
            color = VanGoghSwirlBlue.copy(alpha = 0.08f),
            style = Stroke(width = 80.dp.toPx(), cap = StrokeCap.Round)
        )
        
        // Draw Crescent Moon
        drawCircle(
            color = VanGoghStarGold,
            radius = 40.dp.toPx(),
            center = Offset(size.width * 0.8f, size.height * 0.15f)
        )
        // Subtract circle to make a crescent
        drawCircle(
            color = VanGoghDarkSpace, // Match background
            radius = 35.dp.toPx(),
            center = Offset(size.width * 0.82f, size.height * 0.13f)
        )
    }
}

