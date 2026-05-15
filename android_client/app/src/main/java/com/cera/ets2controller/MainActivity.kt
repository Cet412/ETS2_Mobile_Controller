package com.cera.ets2controller

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import kotlinx.coroutines.launch
import kotlin.math.atan2

class MainActivity : ComponentActivity() {
    private val viewModel: ControllerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFADACAC)) {
                ControllerScreen(viewModel)
            }
        }
    }
}

@Composable
fun ControllerScreen(vm: ControllerViewModel) {
    var screenState by rememberSaveable { mutableStateOf(ScreenState.MAIN) }

    Box(modifier = Modifier.fillMaxSize()) {
        // ==========================================
        // LAYER 1: MAIN GAME CONTROLLER (Z-Index 0)
        // ==========================================
    ConstraintLayout(modifier = Modifier.fillMaxSize()) {
        val (
            topBar, steering, signals, engine,
            gasPedalRef, brakePedalRef, parkingBrake,
            shifter, leftGroup, cruiseGroup
        ) = createRefs()

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
            IconButton(onClick = { screenState = ScreenState.MENU_OPEN }) {
                Icon(
                    painter = painterResource(id = android.R.drawable.ic_menu_sort_by_size),
                    contentDescription = "Menu"
                )
            }

            Text("EURO TRUCK SIMULATOR 2 CONTROLLER", color = Color.Black)

            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(if (vm.isConnected) Color.Green else Color.Red)
            )
        }

        // LEFT GROUP
        Row(modifier = Modifier.constrainAs(leftGroup) {
            top.linkTo(topBar.bottom, margin = 16.dp)
            start.linkTo(parent.start, margin = 16.dp)
        }) {
            SmartLightButton(
                lightMode = vm.telemLightMode,
                onInputStateChange = { vm.inLight = it }
            )
            Spacer(modifier = Modifier.width(16.dp))
            SmartTelemetryButton(
                icon = R.drawable.high_beam,
                isActiveTelemetry = vm.telemHighBeam,
                activeColor = Color.Blue,
                onInputStateChange = { vm.inHighBeam = it }
            )
            Spacer(modifier = Modifier.width(16.dp))
            SmartTelemetryButton(
                icon = R.drawable.wiper,
                isActiveTelemetry = vm.telemWiper,
                activeColor = Color.Cyan,
                onInputStateChange = { vm.inWiper = it }
            )
            Spacer(modifier = Modifier.width(16.dp))
            SmartTelemetryButton(
                icon = R.drawable.lane_assist,
                isActiveTelemetry = vm.inLaneAssist,
                activeColor = Color.Green,
                onInputStateChange = { vm.inLaneAssist = it }
            )
            Spacer(modifier = Modifier.width(16.dp))
            SmartTelemetryButton(
                icon = R.drawable.horn,
                isActiveTelemetry = vm.inHorn,
                activeColor = Color.DarkGray,
                onInputStateChange = { vm.inHorn = it }
            )
        }

        // STEERING WHEEL
        SteeringWheel(
            modifier = Modifier
                .size(170.dp)
                .constrainAs(steering) {
                    start.linkTo(parent.start, margin = 32.dp)
                    bottom.linkTo(parent.bottom, margin = 16.dp)
                },
            angle = vm.steeringAngle,
            maxAngleLimit = vm.maxUiSteeringAngle,
            onAngleChanged = { vm.steeringAngle = it },
            onDraggingChanged = { vm.isDragging = it }
        )

        // SIGNALS & HAZARD
        Row(
            modifier = Modifier.constrainAs(signals) {
                bottom.linkTo(steering.top, margin = 12.dp)
                start.linkTo(steering.start)
                end.linkTo(steering.end)
            },
            verticalAlignment = Alignment.CenterVertically
        ) {
            SmartTelemetryButton(
                icon = R.drawable.left_turn_signal,
                isActiveTelemetry = vm.telemSigLeft,
                isBlinking = true,
                activeColor = Color.Green,
                buttonSize = 30.dp,
                onInputStateChange = { vm.inSigLeft = it }
            )
            Spacer(modifier = Modifier.width(16.dp))
            SmartTelemetryButton(
                icon = R.drawable.hazard_signal,
                isActiveTelemetry = vm.telemSigLeft && vm.telemSigRight,
                isBlinking = true,
                activeColor = Color.Red,
                buttonSize = 30.dp,
                onInputStateChange = { vm.inHazard = it }
            )
            Spacer(modifier = Modifier.width(16.dp))
            SmartTelemetryButton(
                icon = R.drawable.right_turn_signal,
                isActiveTelemetry = vm.telemSigRight,
                isBlinking = true,
                activeColor = Color.Green,
                buttonSize = 30.dp,
                onInputStateChange = { vm.inSigRight = it }
            )
        }

        // ENGINE
        SmartTelemetryButton(
            icon = R.drawable.engine_start_stop,
            isActiveTelemetry = vm.telemEngine,
            activeColor = Color(0xFFFFA500), // Warna Oranye
            modifier = Modifier.constrainAs(engine) {
                bottom.linkTo(parent.bottom, margin = 16.dp)
                start.linkTo(steering.end)
                end.linkTo(brakePedalRef.start)
            },
            onInputStateChange = { vm.inEngine = it }
        )

        // CRUISE
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.constrainAs(cruiseGroup) {
                top.linkTo(topBar.bottom, margin = 32.dp)
                end.linkTo(parent.end, margin = 32.dp)
            }
        ) {
            // Momentary: isActiveTelemetry di-set false, UI membaca tekanan isPressed di dalam komponen
            SmartTelemetryButton(
                icon = R.drawable.cruise_arrow,
                isActiveTelemetry = false,
                activeColor = Color.Green,
                buttonSize = 25.dp,
                onInputStateChange = { vm.inCruiseUp = it }
            )
            // Toggle: Baca dari Telemetri ETS2
            SmartTelemetryButton(
                icon = R.drawable.cruise_control_toggle,
                isActiveTelemetry = vm.telemCruise,
                activeColor = Color.Green,
                buttonSize = 50.dp,
                onInputStateChange = { vm.inCruiseToggle = it }
            )
            // Momentary
            SmartTelemetryButton(
                icon = R.drawable.cruise_arrow,
                isActiveTelemetry = false,
                activeColor = Color.Green,
                modifier = Modifier.graphicsLayer { rotationZ = 180f },
                buttonSize = 25.dp,
                onInputStateChange = { vm.inCruiseDown = it }
            )
        }

        // SHIFTER
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.constrainAs(shifter) {
                bottom.linkTo(parent.bottom, margin = 24.dp)
                end.linkTo(parent.end, margin = 24.dp)
            }
        ) {
            SmartTelemetryButton(
                icon = R.drawable.shifter_arrow,
                isActiveTelemetry = vm.inShifterUp,
                activeColor = Color.DarkGray,
                modifier = Modifier.graphicsLayer { rotationZ = -90f },
                buttonSize = 50.dp,
                onInputStateChange = { vm.inShifterUp = it }
            )
            SmartTelemetryButton(
                icon = R.drawable.shifter_arrow,
                isActiveTelemetry = vm.inShifterDown,
                activeColor = Color.DarkGray,
                modifier = Modifier.graphicsLayer { rotationZ = 90f },
                buttonSize = 50.dp,
                onInputStateChange = { vm.inShifterDown = it }
            )
        }

        // GAS
        Box(modifier = Modifier.constrainAs(gasPedalRef) {
            bottom.linkTo(parent.bottom, margin = 16.dp)
            end.linkTo(shifter.start, margin = 32.dp)
        }) {
            Pedal(R.drawable.gas_pedal, vm.gasValue) { vm.gasValue = it }
        }

        // BRAKE
        Box(modifier = Modifier.constrainAs(brakePedalRef) {
            bottom.linkTo(parent.bottom, margin = 16.dp)
            end.linkTo(gasPedalRef.start, margin = 10.dp)
        }) {
            Pedal(R.drawable.brake_pedal, vm.brakeValue) { vm.brakeValue = it }
        }

        // PARKING BRAKE
        SmartTelemetryButton(
            icon = R.drawable.parking_brake,
            isActiveTelemetry = vm.telemParkingBrake,
            activeColor = Color.Red,
            buttonSize = 36.dp,
            modifier = Modifier.constrainAs(parkingBrake) {
                bottom.linkTo(brakePedalRef.top, margin = 16.dp)
                start.linkTo(brakePedalRef.start)
                end.linkTo(brakePedalRef.end)
            },
            onInputStateChange = { vm.inParkingBrake = it }
        )
    }
        // ==========================================
        // LAYER 2: OVERLAYS (Z-Index 1 - Berada di luar ConstraintLayout)
        // ==========================================
        // STATE: SIDE MENU
        if (screenState == ScreenState.MENU_OPEN) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable { screenState = ScreenState.MAIN }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(300.dp)
                        .background(Color(0xFFE8E8E8))
                        .clickable(enabled = false) {} // Blok klik tembus
                        .padding(top = 64.dp)
                ) {
                    TextButton(
                        onClick = { screenState = ScreenState.SENSITIVITY },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    ) {
                        Text("Sensitivity Settings", color = Color.Black)
                    }
                    HorizontalDivider(color = Color.Gray)
                    TextButton(
                        onClick = { screenState = ScreenState.HELP },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    ) {
                        Text("Help", color = Color.Black)
                    }
                }
            }
        }

        // STATE: SENSITIVITY SETTINGS
        if (screenState == ScreenState.SENSITIVITY) {
            var tempAngle by remember { mutableFloatStateOf(vm.maxUiSteeringAngle) }

            FullPageMenu(title = "Sensitivity Settings", onBack = { screenState = ScreenState.MAIN }) {
                Column(modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp)) {
                    Text("Lock-to-Lock Range: ${(tempAngle * 2).toInt()}°", color = Color.Black)
                    Slider(
                        value = tempAngle,
                        onValueChange = { tempAngle = it },
                        valueRange = 90f..900f,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                    Button(
                        onClick = {
                            vm.maxUiSteeringAngle = tempAngle
                            screenState = ScreenState.MAIN
                        }
                    ) {
                        Text("Apply Configuration")
                    }
                }
            }
        }

        // STATE: HELP MENU
        if (screenState == ScreenState.HELP) {
            FullPageMenu(title = "System Connection Protocol", onBack = { screenState = ScreenState.MAIN }) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val instructions = listOf(
                        "1. Activate Mobile Hotspot OR USB Tethering on your Android device.",
                        "2. Connect your PC to that specific Hotspot/USB network.",
                        "3. Run the ETS2 Controller Server (exe) on your PC.",
                        "4. Wait for the top-right indicator to turn Green, then open ETS2.",
                        "5. Once ETS2 is open, go to the settings menu.",
                        "6. change driving controls to keyboard + vJoy Device",
                        "7. In the \"+ none\" section, select + Xinput Gamepad 1",
                        "8. Bind each button according to your needs or follow the icon on the button in the \"keys and buttons\" menu",
                        "9. Bind the steering wheel to the steering axis, the gas to the acceleration axis, and the brake to the brake axis in the \"Controls\" menu",
                        "10. Enjoy!"
                    )
                    instructions.forEach {
                        Text(it, color = Color.Black)
                    }
                }
            }
        }
    }
}

@Composable
fun FullPageMenu(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFE8E8E8))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text("Back", color = Color.Black, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.weight(1f))
            Text("EURO TRUCK SIMULATOR 2 CONTROLLER", color = Color.Black)
            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(64.dp))
        }
        HorizontalDivider(color = Color.Black, thickness = 1.dp)

        Text(
            text = title,
            color = Color.Black,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(16.dp)
        )
        content()
    }
}

@Composable
fun SmartTelemetryButton(
    icon: Int,
    modifier: Modifier = Modifier,
    isActiveTelemetry: Boolean,
    isBlinking: Boolean = false,
    activeColor: Color = Color.Green,
    buttonSize: androidx.compose.ui.unit.Dp = 48.dp,
    onInputStateChange: (Boolean) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    LaunchedEffect(isPressed) {
        onInputStateChange(isPressed)
    }

    // Animasi Blink Berbasis Siklus Waktu (Hardware Accelerated)
    val infiniteTransition = rememberInfiniteTransition(label = "BlinkAnimator")
    val blinkAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.2f, // Tidak hilang total, menjaga presensi UI
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "BlinkAlpha"
    )

    // SSOT: Telemetri memiliki prioritas, isPressed sebagai fallback untuk tombol momentary
    val isVisuallyActive = isActiveTelemetry || isPressed

    val finalTint = if (isVisuallyActive) {
        if (isActiveTelemetry && isBlinking) activeColor.copy(alpha = blinkAlpha) else activeColor
    } else {
        Color.Black
    }

    IconButton(
        onClick = { },
        modifier = modifier.size(buttonSize),
        interactionSource = interactionSource
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(finalTint),
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun SmartLightButton(
    lightMode: LightMode,
    modifier: Modifier = Modifier,
    buttonSize: androidx.compose.ui.unit.Dp = 48.dp,
    onInputStateChange: (Boolean) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    LaunchedEffect(isPressed) {
        onInputStateChange(isPressed)
    }

    // Siklus Ikon sesuai hierarki Figma
    val icon = when(lightMode) {
        LightMode.LOW_BEAM -> R.drawable.low_beam
        else -> R.drawable.parking_light // Off & Parking menggunakan ikon yang sama
    }

    // Siklus Warna Berdasarkan Telemetri
    val finalTint = when(lightMode) {
        LightMode.OFF -> if(isPressed) Color.DarkGray else Color.Black
        LightMode.PARKING -> Color.Cyan
        LightMode.LOW_BEAM -> Color(0xFFFFA500) // Orange
    }

    IconButton(
        onClick = { },
        modifier = modifier.size(buttonSize),
        interactionSource = interactionSource
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(finalTint),
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun SteeringWheel(
    modifier: Modifier,
    angle: Float,
    maxAngleLimit: Float,
    onAngleChanged: (Float) -> Unit,
    onDraggingChanged: (Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()
    val returnAnimator = remember { Animatable(0f) }

    val currentOnAngleChanged by rememberUpdatedState(onAngleChanged)
    val currentOnDraggingChanged by rememberUpdatedState(onDraggingChanged)

    Image(
        painter = painterResource(id = R.drawable.steering_wheel),
        contentDescription = null,
        modifier = modifier
            // KUNCI UTAMA: Gunakan maxAngleLimit sebagai parameter pointerInput.
            // Setiap kali sensitivitas diubah, blok ini akan dihancurkan dan direkonstruksi dengan limit absolut yang baru.
            .pointerInput(maxAngleLimit) {
                var lastTouchAngle = 0f
                var accumulatedAngle = angle

                detectDragGestures(
                    onDragStart = { offset ->
                        scope.launch { returnAnimator.stop() }
                        currentOnDraggingChanged(true)
                        accumulatedAngle = angle
                        val centerX = size.width / 2f
                        val centerY = size.height / 2f
                        lastTouchAngle = Math.toDegrees(atan2((offset.y - centerY).toDouble(), (offset.x - centerX).toDouble())).toFloat()
                    },
                    onDragEnd = {
                        scope.launch {
                            returnAnimator.snapTo(accumulatedAngle)
                            returnAnimator.animateTo(
                                targetValue = 0f, // Kembalikan ke titik tengah absolut (0 derajat) secara mulus
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
                            ) {
                                currentOnAngleChanged(this.value)
                            }
                            // Buka kunci telemetri ETS2 HANYA setelah animasi spring selesai
                            currentOnDraggingChanged(false)
                        }
                    },
                    onDragCancel = {
                        scope.launch {
                            returnAnimator.snapTo(accumulatedAngle)
                            returnAnimator.animateTo(
                                targetValue = 0f, // Kembalikan ke titik tengah
                                animationSpec = spring(stiffness = Spring.StiffnessVeryLow)
                            ) {
                                currentOnAngleChanged(this.value)
                            }
                            currentOnDraggingChanged(false)
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

                        // Eksekusi clamping langsung dari maxAngleLimit yang diinjeksi ulang
                        accumulatedAngle = (accumulatedAngle + delta).coerceIn(-maxAngleLimit, maxAngleLimit)
                        currentOnAngleChanged(accumulatedAngle)
                        lastTouchAngle = currentTouchAngle
                    }
                )
            }
            .graphicsLayer { rotationZ = angle }
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
        Image(painter = painterResource(iconRes), contentDescription = null, colorFilter = ColorFilter.tint(Color.Black.copy(alpha = 0.2f)))
        Image(
            painter = painterResource(iconRes), contentDescription = null, colorFilter = ColorFilter.tint(Color.Black),
            modifier = Modifier.drawWithContent {
                val height = size.height
                clipRect(top = height - (height * value), bottom = height) { this@drawWithContent.drawContent() }
            }
        )
    }
}