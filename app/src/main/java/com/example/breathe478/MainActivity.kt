package com.example.breathe478

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.breathe478.ui.theme.Breathe478Theme
import kotlinx.coroutines.delay

enum class BreathPhase {
    Ready,
    Inhale,
    Hold,
    Exhale,
    Complete
}

enum class AppScreen {
    ChooseRounds,
    SessionStatus
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            Breathe478Theme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    BreathApp(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun BreathApp(modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext

    val vibrator = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager =
                context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    var screen by remember { mutableStateOf(AppScreen.ChooseRounds) }

    var selectedRounds by remember { mutableIntStateOf(4) }
    var currentRound by remember { mutableIntStateOf(0) }

    var isRunning by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    var isEyesClosedMode by remember { mutableStateOf(false) }

    var currentPhase by remember { mutableStateOf(BreathPhase.Ready) }
    var timeLeft by remember { mutableIntStateOf(0) }
    var remainingSeconds by remember { mutableIntStateOf(0) }

    var phaseProgress by remember { mutableFloatStateOf(0f) }
    var runId by remember { mutableIntStateOf(0) }

    val oneRoundSeconds = 4 + 7 + 8
    val totalSeconds = selectedRounds * oneRoundSeconds
    val ticksPerSecond = 20
    val tickDelayMillis = 1000L / ticksPerSecond

    fun forceVibrate(milliseconds: Long = 500L) {
        if (!vibrator.hasVibrator()) return

        val effect = VibrationEffect.createOneShot(
            milliseconds,
            88
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val attributes = VibrationAttributes.Builder()
                .setUsage(VibrationAttributes.USAGE_ALARM)
                .build()

            vibrator.vibrate(effect, attributes)
        } else {
            vibrator.vibrate(effect)
        }
    }

    fun vibrateIfEyesClosed(milliseconds: Long = 350L) {
        if (isEyesClosedMode) {
            forceVibrate(milliseconds)
        }
    }

    fun resetSession() {
        isRunning = false
        isPaused = false
        currentRound = 0
        currentPhase = BreathPhase.Ready
        timeLeft = 0
        remainingSeconds = totalSeconds
        phaseProgress = 0f
    }

    fun startSession() {
        vibrateIfEyesClosed(150)

        currentRound = 1
        currentPhase = BreathPhase.Inhale
        timeLeft = 4
        remainingSeconds = totalSeconds
        phaseProgress = 0f
        isPaused = false
        isRunning = true
        screen = AppScreen.SessionStatus
        runId += 1
    }
    fun backToChooseRounds() {
        resetSession()
        screen = AppScreen.ChooseRounds
    }
    LaunchedEffect(runId) {
        if (isRunning) {
            val roundsToRun = selectedRounds
            remainingSeconds = roundsToRun * oneRoundSeconds

            suspend fun waitWhilePaused(): Boolean {
                while (isPaused && isRunning) {
                    delay(tickDelayMillis)
                }

                return isRunning
            }

            suspend fun runPhase(phase: BreathPhase, duration: Int): Boolean {
                currentPhase = phase
                phaseProgress = 0f
                vibrateIfEyesClosed(150)

                val totalTicks = duration * ticksPerSecond

                for (tick in 0 until totalTicks) {
                    if (!waitWhilePaused()) {
                        return false
                    }

                    if (!isRunning) {
                        return false
                    }

                    val denominator = (totalTicks - 1).coerceAtLeast(1)
                    phaseProgress = tick.toFloat() / denominator.toFloat()

                    val ticksLeft = totalTicks - tick
                    timeLeft = ((ticksLeft + ticksPerSecond - 1) / ticksPerSecond)
                        .coerceIn(1, duration)

                    delay(tickDelayMillis)

                    if ((tick + 1) % ticksPerSecond == 0) {
                        remainingSeconds = (remainingSeconds - 1).coerceAtLeast(0)
                    }
                }

                return true
            }

            for (round in 1..roundsToRun) {
                currentRound = round

                if (!runPhase(BreathPhase.Inhale, 4)) return@LaunchedEffect
                if (!runPhase(BreathPhase.Hold, 7)) return@LaunchedEffect
                if (!runPhase(BreathPhase.Exhale, 8)) return@LaunchedEffect
            }

            currentPhase = BreathPhase.Complete
            timeLeft = 0
            remainingSeconds = 0
            phaseProgress = 0f
            isRunning = false
            isPaused = false

            vibrateIfEyesClosed(100)
        }
    }

    when (screen) {
        AppScreen.ChooseRounds -> {
            ChooseRoundsScreen(
                modifier = modifier,
                selectedRounds = selectedRounds,
                totalSeconds = totalSeconds,
                isEyesClosedMode = isEyesClosedMode,
                onEyesClosedModeChange = {
                    isEyesClosedMode = it
                },
                onDecrease = {
                    if (selectedRounds > 1) {
                        selectedRounds -= 1
                    }
                },
                onIncrease = {
                    if (selectedRounds < 20) {
                        selectedRounds += 1
                    }
                },
                onStartSession = {
                    startSession()
                }
            )
        }

        AppScreen.SessionStatus -> {
            SessionStatusScreen(
                modifier = modifier,
                selectedRounds = selectedRounds,
                currentRound = currentRound,
                currentPhase = currentPhase,
                timeLeft = timeLeft,
                remainingSeconds = remainingSeconds,
                phaseProgress = phaseProgress,
                isRunning = isRunning,
                isPaused = isPaused,
                isEyesClosedMode = isEyesClosedMode,
                onPauseResume = {
                    vibrateIfEyesClosed(150)
                    isPaused = !isPaused
                },
                onStop = {
                    vibrateIfEyesClosed(100)
                    backToChooseRounds()
                },
                onBackToChooseRounds = {
                    backToChooseRounds()
                },
                onRestart = {
                    startSession()
                }
            )
        }
    }
}

@Composable
fun ChooseRoundsScreen(
    modifier: Modifier = Modifier,
    selectedRounds: Int,
    totalSeconds: Int,
    isEyesClosedMode: Boolean,
    onEyesClosedModeChange: (Boolean) -> Unit,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    onStartSession: () -> Unit
) {
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFEAF4F4),
            Color(0xFFF7FBFB),
            Color(0xFFE8F0FF)
        )
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Breathe 478",
                fontSize = 38.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1F3A3D)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "A calm breathing session for focus and relaxation",
                fontSize = 15.sp,
                color = Color(0xFF5F7477),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(42.dp))

            Text(
                text = "Choose Rounds",
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF263F44)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                OutlinedButton(
                    enabled = selectedRounds > 1,
                    onClick = onDecrease,
                    shape = CircleShape,
                    border = BorderStroke(1.dp, Color(0xFF9CBCC0)),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(56.dp)
                ) {
                    Text(
                        text = "-",
                        fontSize = 24.sp,
                        color = Color(0xFF355C60)
                    )
                }

                Column(
                    modifier = Modifier.padding(horizontal = 34.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "$selectedRounds",
                        fontSize = 64.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF183A3E)
                    )

                    Text(
                        text = "rounds",
                        fontSize = 15.sp,
                        color = Color(0xFF789092)
                    )
                }

                OutlinedButton(
                    enabled = selectedRounds < 20,
                    onClick = onIncrease,
                    shape = CircleShape,
                    border = BorderStroke(1.dp, Color(0xFF9CBCC0)),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(56.dp)
                ) {
                    Text(
                        text = "+",
                        fontSize = 24.sp,
                        color = Color(0xFF355C60)
                    )
                }
            }

            Spacer(modifier = Modifier.height(34.dp))

            Text(
                text = "Estimated Time",
                fontSize = 14.sp,
                color = Color(0xFF6E8588)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = formatTime(totalSeconds),
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF204B50)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "1 round = inhale 4s + hold 7s + exhale 8s",
                fontSize = 13.sp,
                color = Color(0xFF7E9295),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(30.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Eyes Closed Mode",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF263F44)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = if (isEyesClosedMode) {
                            "Vibration cues are enabled."
                        } else {
                            "Vibration cues are off."
                        },
                        fontSize = 13.sp,
                        color = Color(0xFF6E8588)
                    )
                }

                Switch(
                    checked = isEyesClosedMode,
                    onCheckedChange = onEyesClosedModeChange
                )
            }

            Spacer(modifier = Modifier.height(38.dp))

            Button(
                onClick = onStartSession,
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF315E63),
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(
                    text = "Start Session",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun SessionStatusScreen(
    modifier: Modifier = Modifier,
    selectedRounds: Int,
    currentRound: Int,
    currentPhase: BreathPhase,
    timeLeft: Int,
    remainingSeconds: Int,
    phaseProgress: Float,
    isRunning: Boolean,
    isPaused: Boolean,
    isEyesClosedMode: Boolean,
    onPauseResume: () -> Unit,
    onStop: () -> Unit,
    onBackToChooseRounds: () -> Unit,
    onRestart: () -> Unit
) {
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFE8F4F2),
            Color(0xFFF8FBFD),
            Color(0xFFE9EDFF)
        )
    )

    val basePhaseText = when (currentPhase) {
        BreathPhase.Ready -> "Ready"
        BreathPhase.Inhale -> "Inhale"
        BreathPhase.Hold -> "Hold"
        BreathPhase.Exhale -> "Exhale"
        BreathPhase.Complete -> "Complete"
    }

    val phaseText = if (isPaused) {
        "$basePhaseText Paused"
    } else {
        basePhaseText
    }

    val timeText = when (currentPhase) {
        BreathPhase.Complete -> "Done"
        else -> timeLeft.toString()
    }

    val statusText = when {
        currentPhase == BreathPhase.Complete -> "Complete"
        isPaused -> "Paused"
        isRunning -> "Running"
        else -> "Stopped"
    }

    val roundText = when (currentPhase) {
        BreathPhase.Complete -> "Round $selectedRounds / $selectedRounds"
        else -> "Round $currentRound / $selectedRounds"
    }

    val modeText = if (isEyesClosedMode) {
        "Eyes Closed Mode: On"
    } else {
        "Eyes Closed Mode: Off"
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Session Status",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF20383C)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "$statusText · $modeText",
                fontSize = 14.sp,
                color = Color(0xFF6C8184),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Remaining Time",
                    fontSize = 14.sp,
                    color = Color(0xFF6C8184)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = formatTime(remainingSeconds),
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1F3A3D)
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = roundText,
                    fontSize = 16.sp,
                    color = Color(0xFF607D80)
                )
            }

            Spacer(modifier = Modifier.height(34.dp))

            BreathingCircle(
                currentPhase = currentPhase,
                phaseProgress = phaseProgress,
                phaseText = phaseText,
                timeText = timeText
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = when (currentPhase) {
                    BreathPhase.Inhale -> "The blue circle is expanding."
                    BreathPhase.Hold -> "Hold steady. The circle stays full."
                    BreathPhase.Exhale -> "The blue circle is shrinking."
                    BreathPhase.Complete -> "Session complete."
                    else -> "Get ready."
                },
                fontSize = 14.sp,
                color = Color(0xFF6C8184),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            if (currentPhase == BreathPhase.Complete) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onRestart,
                        shape = RoundedCornerShape(22.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF315E63),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.height(52.dp)
                    ) {
                        Text(text = "Restart")
                    }

                    OutlinedButton(
                        onClick = onBackToChooseRounds,
                        shape = RoundedCornerShape(22.dp),
                        border = BorderStroke(1.dp, Color(0xFF9CBCC0)),
                        modifier = Modifier.height(52.dp)
                    ) {
                        Text(
                            text = "Choose Rounds",
                            color = Color(0xFF315E63)
                        )
                    }
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        enabled = isRunning,
                        onClick = onPauseResume,
                        shape = RoundedCornerShape(22.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF315E63),
                            contentColor = Color.White,
                            disabledContainerColor = Color(0xFFB8C9CC),
                            disabledContentColor = Color.White
                        ),
                        modifier = Modifier.height(52.dp)
                    ) {
                        Text(
                            text = if (isPaused) "Resume" else "Pause"
                        )
                    }

                    OutlinedButton(
                        onClick = onStop,
                        shape = RoundedCornerShape(22.dp),
                        border = BorderStroke(1.dp, Color(0xFF9CBCC0)),
                        modifier = Modifier.height(52.dp)
                    ) {
                        Text(
                            text = "Stop",
                            color = Color(0xFF315E63)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BreathingCircle(
    currentPhase: BreathPhase,
    phaseProgress: Float,
    phaseText: String,
    timeText: String
) {
    val blueCircleColor = Color(0xFF3F7FCD)
    val greenCircleColor = Color(0xFF49A36D)
    val neutralCircleColor = Color(0xFF8AA0A3)

    val activeCircleColor = when (currentPhase) {
        BreathPhase.Inhale -> blueCircleColor
        BreathPhase.Hold -> greenCircleColor
        BreathPhase.Exhale -> blueCircleColor
        BreathPhase.Complete -> greenCircleColor
        BreathPhase.Ready -> neutralCircleColor
    }

    val minScale = 0.18f

    val innerScale = when (currentPhase) {
        BreathPhase.Inhale -> {
            minScale + phaseProgress.coerceIn(0f, 1f) * (1f - minScale)
        }

        BreathPhase.Hold -> {
            1f
        }

        BreathPhase.Exhale -> {
            1f - phaseProgress.coerceIn(0f, 1f) * (1f - minScale)
        }

        BreathPhase.Complete -> {
            minScale
        }

        BreathPhase.Ready -> {
            minScale
        }
    }

    Box(
        modifier = Modifier.size(260.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val outerRadius = size.minDimension / 2f
            val borderWidth = 5.dp.toPx()
            val maxInnerRadius = outerRadius - borderWidth
            val innerRadius = maxInnerRadius * innerScale

            drawCircle(
                color = activeCircleColor.copy(alpha = 0.10f),
                radius = outerRadius,
                center = center
            )

            drawCircle(
                color = activeCircleColor.copy(alpha = 0.55f),
                radius = outerRadius - borderWidth / 2f,
                center = center,
                style = Stroke(width = borderWidth)
            )

            drawCircle(
                color = activeCircleColor.copy(alpha = 0.88f),
                radius = innerRadius,
                center = center
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = phaseText,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = timeText,
                fontSize = 58.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

fun formatTime(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}