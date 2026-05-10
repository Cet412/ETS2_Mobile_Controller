package com.cera.ets2controller

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
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
import kotlin.math.atan2
import androidx.compose.ui.graphics.drawscope.clipRect
import kotlinx.coroutines.launch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

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
    // State memori untuk melacak status pop-up (kebal rotasi layar)
    var showTutorial by rememberSaveable { mutableStateOf(true) }

    // Komponen Pop-up Instruksi
    if (showTutorial) {
        AlertDialog(
            onDismissRequest = { showTutorial = false },
            title = { androidx.compose.material3.Text("System Connection Protocol", color = Color.Black) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    androidx.compose.material3.Text("1. Activate Mobile Hotspot OR USB Tethering on your Android device.")
                    androidx.compose.material3.Text("2. Connect your PC to that specific Hotspot/USB network.")
                    androidx.compose.material3.Text("3. Run the ETS2 Controller Server (exe) on your PC.")
                    androidx.compose.material3.Text("4. Wait for the top-right indicator to turn Green, then open ETS2.")
                    androidx.compose.material3.Text("5. Once ETS2 is open, go to the settings menu.")
                    androidx.compose.material3.Text("6. change driving controls to keyboard + xbox 360 controller (or similar)")
                    androidx.compose.material3.Text("7. bind each button in this controller application in 'keys and button' menu according to its features (it is more important to bind to secondary than primary)")
                    androidx.compose.material3.Text("8. For gas, brake, and steering. Bind them in the 'controls' menu to the brake, gas, and steering axes. Steering axis mode uses centered, Acceleration axis mode uses inverted and centered, Brake axis mode uses Normal.")
                    androidx.compose.material3.Text("9. Enjoy the controller!")
                }
            },
            confirmButton = {
                TextButton(onClick = { showTutorial = false }) {
                    androidx.compose.material3.Text("I understand", color = Color.Blue)
                }
            }
        )
    }

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
            // Penggantian ikon menjadi Tanda Tanya (Help)
            androidx.compose.material3.IconButton(onClick = { showTutorial = true }) {
                androidx.compose.material3.Icon(
                    painter = painterResource(id = android.R.drawable.ic_menu_help),
                    contentDescription = "Bantuan Koneksi"
                )
            }

            androidx.compose.material3.Text("EURO TRUCK SIMULATOR 2 CONTROLLER", color = Color.Black)

            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
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

        // STEERING WHEEL (Skala 900 Derajat Lock-to-Lock)
        SteeringWheel(
            modifier = Modifier
                .size(170.dp)
                .constrainAs(steering) {
                    start.linkTo(parent.start, margin = 32.dp)
                    bottom.linkTo(parent.bottom, margin = 16.dp)
                },
            angle = vm.steeringAngle,
            onAngleChanged = { vm.steeringAngle = it }
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
                activeColor = Color.Green,
                buttonSize = 30.dp,
                onInputStateChange = { vm.inSigLeft = it }
            )
            Spacer(modifier = Modifier.width(16.dp))
            SmartTelemetryButton(
                icon = R.drawable.hazard_signal,
                isActiveTelemetry = vm.telemSigLeft && vm.telemSigRight,
                activeColor = Color.Red,
                buttonSize = 30.dp,
                onInputStateChange = { vm.inHazard = it }
            )
            Spacer(modifier = Modifier.width(16.dp))
            SmartTelemetryButton(
                icon = R.drawable.right_turn_signal,
                isActiveTelemetry = vm.telemSigRight,
                activeColor = Color.Green,
                buttonSize = 30.dp,
                onInputStateChange = { vm.inSigRight = it }
            )
        }

        // ENGINE
        SmartTelemetryButton(
            icon = R.drawable.engine_start_stop,
            isActiveTelemetry = vm.telemEngine,
            activeColor = Color.Green,
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
            SmartTelemetryButton(
                icon = R.drawable.cruise_arrow,
                isActiveTelemetry = vm.inCruiseUp,
                buttonSize = 25.dp,
                onInputStateChange = { vm.inCruiseUp = it }
            )
            SmartTelemetryButton(
                icon = R.drawable.cruise_control_toggle,
                isActiveTelemetry = vm.telemCruise,
                activeColor = Color.Green,
                buttonSize = 50.dp,
                onInputStateChange = { vm.inCruiseToggle = it }
            )
            SmartTelemetryButton(
                icon = R.drawable.cruise_arrow,
                isActiveTelemetry = vm.inCruiseDown,
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
}

// ==========================================
// ABSTRAKSI UI COMPONENT BARU
// ==========================================
@Composable
fun SmartTelemetryButton(
    icon: Int,
    modifier: Modifier = Modifier,
    isActiveTelemetry: Boolean,
    activeColor: Color = Color.Green,
    buttonSize: androidx.compose.ui.unit.Dp = 48.dp,
    onInputStateChange: (Boolean) -> Unit
) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    LaunchedEffect(isPressed) {
        onInputStateChange(isPressed)
    }

    androidx.compose.material3.IconButton(
        onClick = { },
        modifier = modifier.size(buttonSize),
        interactionSource = interactionSource
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(if (isActiveTelemetry) activeColor else Color.Black),
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
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    LaunchedEffect(isPressed) {
        onInputStateChange(isPressed)
    }

    // Rekonstruksi visual dinamis berdasarkan enum telemetri absolut
    val icon = when(lightMode) {
        LightMode.OFF, LightMode.PARKING -> R.drawable.parking_light
        LightMode.LOW_BEAM -> R.drawable.low_beam
    }

    val activeColor = when(lightMode) {
        LightMode.OFF -> Color.Black
        LightMode.PARKING -> Color.Cyan
        LightMode.LOW_BEAM -> Color(0xFFFFA500)
    }

    androidx.compose.material3.IconButton(
        onClick = { },
        modifier = modifier.size(buttonSize),
        interactionSource = interactionSource
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(activeColor),
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun SteeringWheel(modifier: Modifier, angle: Float, onAngleChanged: (Float) -> Unit) {
    val scope = rememberCoroutineScope()
    var visualAngle by remember { mutableFloatStateOf(angle) }
    val returnAnimator = remember { Animatable(0f) }

    // INTERVENSI: Skala Absolut 900 Derajat (1:1 dengan ETS2)
    val maxAngle = 450f

    LaunchedEffect(visualAngle) {
        onAngleChanged(visualAngle)
    }

    Image(
        painter = painterResource(id = R.drawable.steering_wheel),
        contentDescription = null,
        modifier = modifier
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
                            returnAnimator.animateTo(0f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) {
                                visualAngle = this.value
                            }
                        }
                    },
                    onDragCancel = {
                        scope.launch {
                            returnAnimator.snapTo(accumulatedAngle)
                            returnAnimator.animateTo(0f, animationSpec = spring(stiffness = Spring.StiffnessVeryLow)) {
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
            .graphicsLayer { rotationZ = visualAngle }
    )
}

// (Fungsi Pedal dibiarkan SAMA PERSIS seperti sebelumnya)
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