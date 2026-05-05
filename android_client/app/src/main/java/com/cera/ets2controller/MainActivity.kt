package com.cera.ets2controller

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    // State Management
    var steeringAngle by remember { mutableFloatStateOf(0f) }
    var gasValue by remember { mutableFloatStateOf(0f) }
    var brakeValue by remember { mutableFloatStateOf(0f) }
    var lightMode by remember { mutableStateOf(LightMode.OFF) }
    var signalMode by remember { mutableStateOf(SignalMode.OFF) }

    // Blinking Logic (500ms)
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
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        val (
            steering, signals, engine,
            pedals, parkingBrake, shifter,
            leftGroup, cruiseGroup
        ) = createRefs()

        // 1. STEERING WHEEL (Kiri Bawah)
        SteeringWheel(
            modifier = Modifier
                .size(220.dp)
                .constrainAs(steering) {
                    start.linkTo(parent.start)
                    bottom.linkTo(parent.bottom)
                },
            angle = steeringAngle,
            onAngleChanged = { steeringAngle = it }
        )

        // 2. TURN SIGNALS & HAZARD (Terpusat di atas setir)
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

        // 3. LEFT CONTROL GROUP (Lampu, Wiper, Horn)
        Column(modifier = Modifier.constrainAs(leftGroup) {
            top.linkTo(parent.top)
            start.linkTo(parent.start)
        }) {
            Row {
                ControlIconButton(
                    icon = when(lightMode) {
                        LightMode.OFF, LightMode.PARKING -> R.drawable.parking_light
                        LightMode.LOW_BEAM -> R.drawable.low_beam
                        LightMode.HIGH_BEAM -> R.drawable.high_beam
                    },
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
                Spacer(modifier = Modifier.width(12.dp))
                ControlIconButton(R.drawable.wiper) { /* Logika Wiper */ }
            }
            Spacer(modifier = Modifier.height(12.dp))
            ControlIconButton(R.drawable.horn) { /* Logika Klakson */ }
        }

        // 4. ENGINE START/STOP (Tengah Bawah)
        ControlIconButton(
            icon = R.drawable.engine_start_stop,
            modifier = Modifier.constrainAs(engine) {
                bottom.linkTo(parent.bottom)
                start.linkTo(parent.start)
                end.linkTo(parent.end)
            }
        ) { /* Logika Engine */ }

        // 5. PEDAL GROUP (Gas & Rem - Kanan Bawah)
        Row(modifier = Modifier.constrainAs(pedals) {
            bottom.linkTo(parent.bottom)
            end.linkTo(parent.end)
        }) {
            Pedal(R.drawable.brake_pedal, brakeValue) { brakeValue = it }
            Spacer(modifier = Modifier.width(24.dp))
            Pedal(R.drawable.gas_pedal, gasValue) { gasValue = it }
        }

        // 6. PARKING BRAKE (Di atas Pedal)
        ControlIconButton(
            icon = R.drawable.parking_brake,
            modifier = Modifier.constrainAs(parkingBrake) {
                bottom.linkTo(pedals.top, margin = 20.dp)
                end.linkTo(pedals.start, margin = 20.dp)
            }
        ) { /* Logika Parking Brake */ }

        // 7. CRUISE CONTROL GROUP (Kanan Atas)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.constrainAs(cruiseGroup) {
                top.linkTo(parent.top)
                end.linkTo(parent.end)
            }
        ) {
            // Asumsi nama file: R.drawable.cruise_arrow & R.drawable.cruise_control_toggle
            ControlIconButton(R.drawable.cruise_arrow) { /* Logika Cruise Up */ }
            ControlIconButton(R.drawable.cruise_control_toggle) { /* Logika Cruise Toggle */ }

            // Putar aset 180 derajat untuk tombol Down
            ControlIconButton(
                icon = R.drawable.cruise_arrow,
                modifier = Modifier.graphicsLayer { rotationZ = 180f }
            ) { /* Logika Cruise Down */ }
        }

        // 8. SHIFTER GROUP (Di bawah Cruise Control)
        // Pemanggilan 'shifter' di sini akan menghilangkan error Unused Variable
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.constrainAs(shifter) {
                top.linkTo(cruiseGroup.bottom, margin = 24.dp)
                end.linkTo(parent.end)
            }
        ) {
            // Asumsi nama file: R.drawable.shifter_arrow
            ControlIconButton(R.drawable.shifter_arrow) { /* Logika Shifter Up */ }

            // Putar aset 180 derajat untuk tombol Down
            ControlIconButton(
                icon = R.drawable.shifter_arrow,
                modifier = Modifier.graphicsLayer { rotationZ = 180f }
            ) { /* Logika Shifter Down */ }
        }
    }
}

@Composable
fun SteeringWheel(modifier: Modifier, angle: Float, onAngleChanged: (Float) -> Unit) {
    var isDragging by remember { mutableStateOf(false) }
    val animatedAngle by animateFloatAsState(
        targetValue = if (isDragging) angle else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow)
    )

    Image(
        painter = painterResource(id = R.drawable.steering_wheel),
        contentDescription = null,
        modifier = modifier
            .graphicsLayer { rotationZ = animatedAngle }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { isDragging = true },
                    onDragEnd = { isDragging = false },
                    onDrag = { change, _ ->
                        val centerX = size.width / 2f
                        val centerY = size.height / 2f
                        val x = change.position.x - centerX
                        val y = change.position.y - centerY
                        val deg = Math.toDegrees(atan2(y, x).toDouble()).toFloat() + 90f
                        onAngleChanged(deg.coerceIn(-180f, 180f))
                    }
                )
            }
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
fun ControlIconButton(icon: Int, modifier: Modifier = Modifier, activeColor: Color = Color.Black, onClick: () -> Unit) {
    androidx.compose.material3.IconButton(onClick = onClick, modifier = modifier) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(activeColor),
            modifier = Modifier.size(48.dp)
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
            modifier = Modifier.size(40.dp)
        )
    }
}