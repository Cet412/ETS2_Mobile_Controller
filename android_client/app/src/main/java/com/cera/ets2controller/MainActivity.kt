package com.cera.ets2controller

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.OutputStream
import java.net.Socket
import kotlin.math.PI
import kotlin.math.atan2

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        val controlState = TruckControlState()
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFFADACAC)
                ) {
                    ControllerScreen(controlState)
                }
            }
        }
    }
}

@Composable
fun ControllerScreen(state: TruckControlState) {
    var steerAngle by remember { mutableFloatStateOf(0f) }
    val steerMaxAngle = 180f
    val coroutineScope = rememberCoroutineScope()
    val steerAnimatable = remember { Animatable(0f) }

    var lightMode by remember { mutableIntStateOf(0) }
    var isHazardOn by remember { mutableStateOf(false) }
    var isLeftSignalOn by remember { mutableStateOf(false) }
    var isRightSignalOn by remember { mutableStateOf(false) }
    var isWiperOn by remember { mutableStateOf(false) }
    var isPBrakeOn by remember { mutableStateOf(false) }
    var isCruiseOn by remember { mutableStateOf(false) }
    var isLaneOn by remember { mutableStateOf(false) }

    var blinkState by remember { mutableStateOf(true) }
    var hasTurnLeftCrossed by remember { mutableStateOf(false) }
    var hasTurnRightCrossed by remember { mutableStateOf(false) }
    var isConnected by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (isActive) {
            delay(500)
            blinkState = !blinkState
        }
    }

    LaunchedEffect(steerAngle) {
        val cancelThreshold = 45f
        if (isRightSignalOn) {
            if (steerAngle > cancelThreshold) hasTurnRightCrossed = true
            if (steerAngle < cancelThreshold && hasTurnRightCrossed) { isRightSignalOn = false; hasTurnRightCrossed = false }
        }
        if (isLeftSignalOn) {
            if (steerAngle < -cancelThreshold) hasTurnLeftCrossed = true
            if (steerAngle > -cancelThreshold && hasTurnLeftCrossed) { isLeftSignalOn = false; hasTurnLeftCrossed = false }
        }
    }

    val autoCenterSteering = {
        coroutineScope.launch {
            if (steerAnimatable.isRunning) steerAnimatable.stop()
            steerAnimatable.snapTo(steerAngle)
            steerAnimatable.animateTo(0f, animationSpec = tween(300, easing = FastOutSlowInEasing)) {
                steerAngle = value
                state.steer = (value / steerMaxAngle)
            }
        }
    }

    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val socket = Socket("127.0.0.1", 65432)
                    val out: OutputStream = socket.getOutputStream()
                    launch(Dispatchers.Main) { isConnected = true }

                    while (isActive && socket.isConnected) {
                        val dataString = "${state.gas},${state.brake},${state.steer}," +
                                "${if(state.hazard_pulse) 1 else 0}," +
                                "${if(state.wiper_pulse) 1 else 0}," +
                                "${if(state.turnLeft_pulse) 1 else 0}," +
                                "${if(state.turnRight_pulse) 1 else 0}," +
                                "${if(state.parkingBrake_pulse) 1 else 0}," +
                                "${if(state.cruiseControl_pulse) 1 else 0}," +
                                "${if(state.laneAssist_pulse) 1 else 0}," +
                                "${if(state.horn_pulse) 1 else 0}," +
                                "${if(state.engine_pulse) 1 else 0}," +
                                "${if(state.cruiseSpeedUp) 1 else 0}," +
                                "${if(state.cruiseSpeedDown) 1 else 0}," +
                                "${if(state.shiftUp) 1 else 0}," +
                                "${if(state.shiftDown) 1 else 0}\n"

                        out.write(dataString.toByteArray())
                        out.flush()

                        state.hazard_pulse = false; state.wiper_pulse = false
                        state.turnLeft_pulse = false; state.turnRight_pulse = false
                        state.parkingBrake_pulse = false; state.cruiseControl_pulse = false
                        state.laneAssist_pulse = false; state.cruiseSpeedUp = false
                        state.cruiseSpeedDown = false; state.shiftUp = false
                        state.shiftDown = false; state.horn_pulse = false
                        state.engine_pulse = false

                        delay(16)
                    }
                    socket.close()
                } catch (_: Exception) {
                    launch(Dispatchers.Main) { isConnected = false }
                    delay(3000)
                }
            }
        }
    }

    // MAIN LAYOUT
    Column(modifier = Modifier.fillMaxSize()) {

        // TOP BAR
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(Color(0xFF3C3C3C))
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = {}) {
                Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color.White)
            }
            Text("EURO TRUCK SIMULATOR 2 CONTROLLER", color = Color.White, fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(if (isConnected) Color(0xFF4CAF50) else Color(0xFFF44336), shape = androidx.compose.foundation.shape.CircleShape)
            )
        }

        // CONTENT AREA
        Row(modifier = Modifier.fillMaxSize().padding(8.dp)) {

            // LEFT COLUMN: Icons + Steering
            Column(
                modifier = Modifier.weight(0.6f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top icons row
                Row(
                    modifier = Modifier.padding(vertical = 0.dp),
                    horizontalArrangement = Arrangement.spacedBy(30.dp)
                ) {
                    LightModeButton(mode = lightMode, onClick = { lightMode = (lightMode + 1) % 4 })
                    SignalButton("W", isWiperOn, false, Color.Gray, R.drawable.wiper, size = 40.dp) {
                        isWiperOn = !isWiperOn; state.wiper_pulse = true
                    }
                    SignalButton("L", isLaneOn, false, Color(0xFF2196F3), R.drawable.lane_assist, size = 40.dp) {
                        isLaneOn = !isLaneOn; state.laneAssist_pulse = true
                    }
                }

                // Horn
                SignalButton("H", false, false, Color.White, R.drawable.horn, size = 30.dp) {
                    state.horn_pulse = true
                }

                Spacer(Modifier.height(5.dp))

                // Turn signals + Hazard
                Row(
                    horizontalArrangement = Arrangement.spacedBy(50.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SignalButton("L", isLeftSignalOn, blinkState, Color(0xFF4CAF50), R.drawable.left_turn_signal, rotation = 0f, size = 30.dp) {
                        isLeftSignalOn = !isLeftSignalOn; hasTurnLeftCrossed = false; state.turnLeft_pulse = true
                    }
                    SignalButton("H", isHazardOn, blinkState, Color(0xFFF44336), R.drawable.hazard_signal, size = 30.dp) {
                        isHazardOn = !isHazardOn; state.hazard_pulse = true
                    }
                    SignalButton("R", isRightSignalOn, blinkState, Color(0xFF4CAF50), R.drawable.right_turn_signal, rotation = 0f, size = 30.dp) {
                        isRightSignalOn = !isRightSignalOn; hasTurnRightCrossed = false; state.turnRight_pulse = true
                    }
                }

                // Steering wheel
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    SteeringWheelControl(
                        angle = steerAngle,
                        onAngleChange = { newAngle ->
                            if (steerAnimatable.isRunning) coroutineScope.launch { steerAnimatable.stop() }
                            steerAngle = newAngle
                            state.steer = (newAngle / steerMaxAngle)
                        },
                        onRelease = { autoCenterSteering() }
                    )
                }
            }

            // RIGHT COLUMN: Cruise, Shifter, Parking Brake, Pedals
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top section: Cruise + Shifter + Parking Brake
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.weight(1f)
                ) {
                    // Cruise control
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ShifterButton(onPress = { state.cruiseSpeedUp = true }, iconResId = R.drawable.arrow, isUp = true, size = 36.dp)
                        SignalButton("CC", isCruiseOn, false, Color(0xFF00BCD4), R.drawable.cruise_control_toggle, size = 52.dp) {
                            isCruiseOn = !isCruiseOn; state.cruiseControl_pulse = true
                        }
                        ShifterButton(onPress = { state.cruiseSpeedDown = true }, iconResId = R.drawable.arrow, isUp = false, size = 36.dp)
                    }

                    Spacer(Modifier.height(20.dp))

                    // Shifter
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ShifterButton(onPress = { state.shiftUp = true }, iconResId = R.drawable.arrow, isUp = true, size = 44.dp)
                        Spacer(Modifier.height(12.dp))
                        ShifterButton(onPress = { state.shiftDown = true }, iconResId = R.drawable.arrow, isUp = false, size = 44.dp)
                    }

                    Spacer(Modifier.height(20.dp))

                    // Parking brake
                    SignalButton("PB", isPBrakeOn, false, Color(0xFFF44336), R.drawable.parking_brake, size = 52.dp) {
                        isPBrakeOn = !isPBrakeOn; state.parkingBrake_pulse = true
                    }
                }

                // Bottom section: Engine + Pedals
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Engine button
                    SignalButton("E", false, false, Color.White, R.drawable.engine_start_stop, size = 40.dp) {
                        state.engine_pulse = true
                    }

                    Spacer(Modifier.height(12.dp))

                    // Pedals
                    Row(
                        modifier = Modifier.height(220.dp),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        PedalControl(
                            value = state.brake,
                            onValueChange = { state.brake = it },
                            onRelease = { state.brake = 0f },
                            iconResId = R.drawable.brake_pedal,
                            modifier = Modifier.width(90.dp)
                        )
                        PedalControl(
                            value = state.gas,
                            onValueChange = { state.gas = it },
                            onRelease = { state.gas = 0f },
                            iconResId = R.drawable.gas_pedal,
                            modifier = Modifier.width(90.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SteeringWheelControl(angle: Float, onAngleChange: (Float) -> Unit, onRelease: () -> Unit) {
    Box(
        modifier = Modifier.size(220.dp).pointerInput(Unit) {
            detectDragGestures(
                onDragEnd = { onRelease() },
                onDragCancel = { onRelease() },
                onDrag = { change, _ ->
                    val center = Offset(size.width / 2f, size.height / 2f)
                    var theta = (atan2(change.position.y - center.y, change.position.x - center.x) * 180f / PI.toFloat()) - 90f
                    theta = theta.coerceIn(-180f, 180f)
                    onAngleChange(theta)
                    change.consume()
                }
            )
        },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.steering_wheel),
            contentDescription = "Steering",
            modifier = Modifier.fillMaxSize().rotate(angle)
        )
    }
}

@Composable
fun PedalControl(value: Float, onValueChange: (Float) -> Unit, onRelease: () -> Unit, iconResId: Int, modifier: Modifier = Modifier) {
    Box(modifier = modifier.height(250.dp), contentAlignment = Alignment.BottomCenter) {
        Image(
            painter = painterResource(id = iconResId),
            contentDescription = "Pedal",
            alpha = 0.5f + (value * 0.5f),
            modifier = Modifier.fillMaxWidth().offset(y = -((value * 100).dp))
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onRelease,
            valueRange = 0f..1f,
            modifier = Modifier.fillMaxSize().background(Color.Transparent),
            colors = SliderDefaults.colors(
                thumbColor = Color.Transparent,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent
            )
        )
    }
}

@Composable
fun ShifterButton(onPress: () -> Unit, iconResId: Int, isUp: Boolean, size: androidx.compose.ui.unit.Dp = 40.dp) {
    IconButton(onClick = onPress) {
        Image(
            painter = painterResource(id = iconResId),
            contentDescription = "Shift",
            modifier = Modifier.size(size).rotate(if (isUp) 0f else 180f),
            colorFilter = ColorFilter.tint(Color.White)
        )
    }
}

@Composable
fun LightModeButton(mode: Int, onClick: () -> Unit) {
    val iconRes = when(mode) {
        1 -> R.drawable.parking_light
        2 -> R.drawable.low_beam
        3 -> R.drawable.high_beam
        else -> R.drawable.parking_light
    }
    val tint = when(mode) {
        1 -> Color(0xFF00BCD4)
        2 -> Color(0xFFFFEB3B)
        3 -> Color.White
        else -> Color.Gray
    }
    IconButton(onClick = onClick) {
        Image(
            painter = painterResource(id = iconRes),
            contentDescription = "Light",
            colorFilter = ColorFilter.tint(tint),
            modifier = Modifier.size(40.dp)
        )
    }
}

@Composable
fun SignalButton(
    label: String,
    isActive: Boolean,
    blinkState: Boolean,
    activeColor: Color,
    iconResId: Int,
    rotation: Float = 0f,
    size: androidx.compose.ui.unit.Dp = 48.dp,
    onClick: () -> Unit
) {
    val currentColor = if (isActive) {
        if (blinkState) activeColor else Color.Gray
    } else {
        Color.Gray
    }
    IconButton(onClick = onClick) {
        Image(
            painter = painterResource(id = iconResId),
            contentDescription = label,
            colorFilter = ColorFilter.tint(currentColor),
            modifier = Modifier.size(size).rotate(rotation)
        )
    }
}