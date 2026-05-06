package com.cera.ets2controller

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import kotlinx.coroutines.delay
import kotlin.math.atan2
import androidx.compose.ui.graphics.drawscope.clipRect
import kotlinx.coroutines.launch


// Enum untuk status kontrol
enum class LightMode { OFF, PARKING, LOW_BEAM, HIGH_BEAM }
enum class SignalMode { OFF, LEFT, RIGHT, HAZARD }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFADACAC)) {
                ControllerScreen()
            }
        }
    }
}

@Composable
fun ControllerScreen() {
    // State Management Dasar
    var steeringAngle by remember { mutableFloatStateOf(0f) }
    var gasValue by remember { mutableFloatStateOf(0f) }
    var brakeValue by remember { mutableFloatStateOf(0f) }
    var lightMode by remember { mutableStateOf(LightMode.OFF) }
    var signalMode by remember { mutableStateOf(SignalMode.OFF) }

    // State Management Toggle
    var isLaneAssistOn by remember { mutableStateOf(false) }
    var isCruiseOn by remember { mutableStateOf(false) }
    var isParkingBrakeOn by remember { mutableStateOf(false) }

    // Blinking Logic
    var isBlinkOn by remember { mutableStateOf(false) }
    LaunchedEffect(signalMode) {
        if (signalMode != SignalMode.OFF) {
            while (true) {
                isBlinkOn = !isBlinkOn
                delay(500)
            }
        } else {
            isBlinkOn = false
        }
    }

    ConstraintLayout(
        modifier = Modifier.fillMaxSize()
    ) {
        val (
            topBar, steering, signals, engine,
            gasPedalRef, brakePedalRef, parkingBrake,
            shifter, leftGroup, cruiseGroup
        ) = createRefs()

        // 1. HEADER / TOP BAR
        Row(
            modifier = Modifier
                .constrainAs(topBar) {
                    top.linkTo(parent.top)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                }
                .fillMaxWidth()
                .background(Color(0xFFE0E0E0))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            androidx.compose.material3.Icon(
                painter = painterResource(id = android.R.drawable.ic_menu_sort_by_size),
                contentDescription = "Menu"
            )
            androidx.compose.material3.Text(
                text = "EURO TRUCK SIMULATOR 2 CONTROLLER",
                color = Color.Black
            )
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(Color.Red)
            )
        }

        // 2. LEFT CONTROL GROUP (Lampu, Wiper, Lane Assist, Horn)
        Row(modifier = Modifier.constrainAs(leftGroup) {
            top.linkTo(topBar.bottom, margin = 16.dp)
            start.linkTo(parent.start, margin = 16.dp)
        }) {
            // Main Light Switch
            ToggleIconButton(
                icon = when(lightMode) {
                    LightMode.OFF, LightMode.PARKING -> R.drawable.parking_light
                    LightMode.LOW_BEAM -> R.drawable.low_beam
                    LightMode.HIGH_BEAM -> R.drawable.high_beam
                },
                isActive = lightMode != LightMode.OFF,
                activeColor = when(lightMode) {
                    LightMode.OFF -> Color.Black
                    LightMode.PARKING -> Color.Cyan
                    LightMode.LOW_BEAM -> Color(0xFFFFA500)
                    LightMode.HIGH_BEAM -> Color.Blue
                }
            ) {
                lightMode = when(lightMode) {
                    LightMode.OFF -> LightMode.PARKING
                    LightMode.PARKING -> LightMode.LOW_BEAM
                    LightMode.LOW_BEAM -> LightMode.HIGH_BEAM
                    LightMode.HIGH_BEAM -> LightMode.OFF
                }
            }
            Spacer(modifier = Modifier.width(16.dp))

            // Wipers
            ToggleIconButton(R.drawable.wiper, isActive = false) { }
            Spacer(modifier = Modifier.width(16.dp))

            // Lane Assist
            ToggleIconButton(
                icon = R.drawable.lane_assist,
                isActive = isLaneAssistOn,
                activeColor = Color.Green
            ) { isLaneAssistOn = !isLaneAssistOn }
            Spacer(modifier = Modifier.width(16.dp))
            MomentaryIconButton(
                icon = R.drawable.horn,
                activeColor = Color.DarkGray
            ) { }
        }

        // 4. STEERING WHEEL
        SteeringWheel(
            modifier = Modifier
                .size(170.dp) // Ukuran modifikasi Anda
                .constrainAs(steering) {
                    start.linkTo(parent.start, margin = 32.dp)
                    bottom.linkTo(parent.bottom, margin = 16.dp)
                },
            angle = steeringAngle,
            onAngleChanged = { steeringAngle = it }
        )

        // 5. TURN SIGNALS & HAZARD
        Row(
            modifier = Modifier.constrainAs(signals) {
                bottom.linkTo(steering.top, margin = 12.dp)
                start.linkTo(steering.start)
                end.linkTo(steering.end)
            },
            verticalAlignment = Alignment.CenterVertically
        ) {
            SignalButton(R.drawable.left_turn_signal, signalMode == SignalMode.LEFT && isBlinkOn, Color.Green) {
                signalMode = if (signalMode == SignalMode.LEFT) SignalMode.OFF else SignalMode.LEFT
            }
            Spacer(modifier = Modifier.width(16.dp))
            SignalButton(R.drawable.hazard_signal, signalMode == SignalMode.HAZARD && isBlinkOn, Color.Red) {
                signalMode = if (signalMode == SignalMode.HAZARD) SignalMode.OFF else SignalMode.HAZARD
            }
            Spacer(modifier = Modifier.width(16.dp))
            SignalButton(R.drawable.right_turn_signal, signalMode == SignalMode.RIGHT && isBlinkOn, Color.Green) {
                signalMode = if (signalMode == SignalMode.RIGHT) SignalMode.OFF else SignalMode.RIGHT
            }
        }

        // 6. ENGINE START/STOP
        ToggleIconButton(
            icon = R.drawable.engine_start_stop,
            isActive = false,
            modifier = Modifier.constrainAs(engine) {
                bottom.linkTo(parent.bottom, margin = 16.dp)
                start.linkTo(steering.end)
                end.linkTo(brakePedalRef.start) // Ditengah setir dan pedal
            }
        ) { }

        // 7. CRUISE CONTROL GROUP (Rasio 2:1 diterapkan via buttonSize)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.constrainAs(cruiseGroup) {
                top.linkTo(topBar.bottom, margin = 32.dp)
                end.linkTo(parent.end, margin = 32.dp)
            }
        ) {
            // Arrow = 18.dp
            MomentaryIconButton(R.drawable.cruise_arrow, buttonSize = 25.dp) { }
            // Toggle = 36.dp
            ToggleIconButton(
                icon = R.drawable.cruise_control_toggle,
                isActive = isCruiseOn,
                activeColor = Color.Green,
                buttonSize = 50.dp
            ) { isCruiseOn = !isCruiseOn }
            // Arrow Down = 18.dp
            MomentaryIconButton(
                icon = R.drawable.cruise_arrow,
                modifier = Modifier.graphicsLayer { rotationZ = 180f },
                buttonSize = 25.dp
            ) { }
        }

        // 8. SHIFTER GROUP (Size disamakan dengan CC Toggle = 36.dp)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.constrainAs(shifter) {
                bottom.linkTo(parent.bottom, margin = 24.dp)
                end.linkTo(parent.end, margin = 24.dp)
            }
        ) {
            MomentaryIconButton(
                icon = R.drawable.shifter_arrow,
                activeColor = Color.DarkGray,
                modifier = Modifier.graphicsLayer { rotationZ = -90f },
                buttonSize = 50.dp
            ) { }
            MomentaryIconButton(
                icon = R.drawable.shifter_arrow,
                activeColor = Color.DarkGray,
                modifier = Modifier.graphicsLayer { rotationZ = 90f },
                buttonSize = 50.dp
            ) { }
        }

        // 9. GAS PEDAL (Digeser jauh dari Shifter agar tidak clipping)
        Box(modifier = Modifier.constrainAs(gasPedalRef) {
            bottom.linkTo(parent.bottom, margin = 16.dp)
            end.linkTo(shifter.start, margin = 32.dp) // Jarak aman dari shifter
        }) {
            Pedal(R.drawable.gas_pedal, gasValue) { gasValue = it }
        }

        // 10. BRAKE PEDAL (Di sebelah kiri Gas)
        Box(modifier = Modifier.constrainAs(brakePedalRef) {
            bottom.linkTo(parent.bottom, margin = 16.dp)
            end.linkTo(gasPedalRef.start, margin = 10.dp)
        }) {
            Pedal(R.drawable.brake_pedal, brakeValue) { brakeValue = it }
        }

        // 11. PARKING BRAKE (Akurat tepat di atas Brake Pedal)
        ToggleIconButton(
            icon = R.drawable.parking_brake,
            isActive = isParkingBrakeOn,
            activeColor = Color.Red,
            buttonSize = 36.dp,
            modifier = Modifier.constrainAs(parkingBrake) {
                bottom.linkTo(brakePedalRef.top, margin = 16.dp) // Patokan bawahnya adalah atas rem
                start.linkTo(brakePedalRef.start)                // Rata Kiri rem
                end.linkTo(brakePedalRef.end)                  // Rata Kanan rem
            }
        ) { isParkingBrakeOn = !isParkingBrakeOn }
    }
}

// --- FUNGSI PEMBANTU (DIPERBARUI DENGAN buttonSize) ---

@Composable
fun ToggleIconButton(
    icon: Int,
    modifier: Modifier = Modifier,
    isActive: Boolean,
    activeColor: Color = Color.Black,
    buttonSize: androidx.compose.ui.unit.Dp = 48.dp, // Parameter Baru
    onClick: () -> Unit
) {
    androidx.compose.material3.IconButton(onClick = onClick, modifier = modifier.size(buttonSize)) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(if (isActive) activeColor else Color.Black),
            modifier = Modifier.fillMaxSize() // Memaksa gambar mengikuti buttonSize
        )
    }
}

@Composable
fun MomentaryIconButton(
    icon: Int,
    modifier: Modifier = Modifier,
    activeColor: Color = Color.Green,
    buttonSize: androidx.compose.ui.unit.Dp = 48.dp, // Parameter Baru
    onClick: () -> Unit
) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    androidx.compose.material3.IconButton(
        onClick = onClick,
        modifier = modifier.size(buttonSize),
        interactionSource = interactionSource
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(if (isPressed) activeColor else Color.Black),
            modifier = Modifier.fillMaxSize() // Memaksa gambar mengikuti buttonSize
        )
    }
}

@Composable
fun SteeringWheel(modifier: Modifier, angle: Float, onAngleChanged: (Float) -> Unit) {
    val scope = rememberCoroutineScope()

    // State UI Absolut: Bebas dari overhead coroutine dispatcher
    var visualAngle by remember { mutableFloatStateOf(angle) }
    // Dedicated Animator hanya untuk efek pegas kembali ke tengah
    val returnAnimator = remember { Animatable(0f) }

    val maxAngle = 720f

    LaunchedEffect(visualAngle) {
        onAngleChanged(visualAngle)
    }

    Image(
        painter = painterResource(id = R.drawable.steering_wheel),
        contentDescription = null,
        modifier = modifier
            // 1. TANGKAP INPUT TERLEBIH DAHULU (Koordinat statis absolut)
            .pointerInput(Unit) {
                var lastTouchAngle = 0f
                var accumulatedAngle = 0f

                detectDragGestures(
                    onDragStart = { offset ->
                        scope.launch { returnAnimator.stop() }
                        accumulatedAngle = visualAngle

                        val centerX = size.width / 2f
                        val centerY = size.height / 2f
                        lastTouchAngle = Math.toDegrees(atan2((offset.y - centerY).toDouble(), (offset.x - centerX).toDouble())).toFloat()
                    },
                    onDragEnd = {
                        scope.launch {
                            returnAnimator.snapTo(accumulatedAngle)
                            returnAnimator.animateTo(
                                targetValue = 0f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow
                                )
                            ) {
                                visualAngle = this.value
                            }
                        }
                    },
                    onDragCancel = {
                        scope.launch {
                            returnAnimator.snapTo(accumulatedAngle)
                            returnAnimator.animateTo(
                                targetValue = 0f,
                                animationSpec = spring(stiffness = Spring.StiffnessVeryLow)
                            ) {
                                visualAngle = this.value
                            }
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()

                        val centerX = size.width / 2f
                        val centerY = size.height / 2f
                        val currentTouchAngle = Math.toDegrees(atan2((change.position.y - centerY).toDouble(), (change.position.x - centerX).toDouble())).toFloat()

                        var delta = currentTouchAngle - lastTouchAngle

                        if (delta > 180f) delta -= 360f
                        else if (delta < -180f) delta += 360f

                        accumulatedAngle = (accumulatedAngle + delta).coerceIn(-maxAngle, maxAngle)
                        visualAngle = accumulatedAngle
                        lastTouchAngle = currentTouchAngle
                    }
                )
            }
            // 2. TERAPKAN ROTASI VISUAL SETELAH INPUT (Tidak mengubah koordinat sentuh)
            .graphicsLayer { rotationZ = visualAngle }
    )
}

@Composable
fun Pedal(iconRes: Int, value: Float, onValueChange: (Float) -> Unit) {
    Box(
        modifier = Modifier
            .size(width = 80.dp, height = 140.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = { onValueChange(0f) },
                    onDrag = { change, _ ->
                        val pos = 1f - (change.position.y / size.height).coerceIn(0f, 1f)
                        onValueChange(pos)
                    }
                )
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        // Background (Abu-abu)
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            colorFilter = ColorFilter.tint(Color.Black.copy(alpha = 0.2f))
        )
        // Fill (Hitam Pekat dengan Clipping dari bawah)
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            colorFilter = ColorFilter.tint(Color.Black),
            modifier = Modifier.drawWithContent {
                val height = size.height
                clipRect(
                    top = height - (height * value),
                    bottom = height
                ) {
                    this@drawWithContent.drawContent()
                }
            }
        )
    }
}

@Composable
fun SignalButton(icon: Int, isActive: Boolean, activeColor: Color, onClick: () -> Unit) {
    androidx.compose.material3.IconButton(onClick = onClick) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(if (isActive) activeColor else Color.Black.copy(alpha = 0.3f)),
            modifier = Modifier.size(30.dp)
        )
    }
}