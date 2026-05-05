package com.cera.ets2controller

data class TruckControlState(
    // Analog
    var gas: Float = 0f,
    var brake: Float = 0f,
    var steer: Float = 0f, // -1.0 to 1.0

    // Pulse/Tap Mechanism
    var hazard_pulse: Boolean = false,
    var wiper_pulse: Boolean = false,
    var turnLeft_pulse: Boolean = false,
    var turnRight_pulse: Boolean = false,
    var parkingBrake_pulse: Boolean = false,
    var cruiseControl_pulse: Boolean = false,
    var laneAssist_pulse: Boolean = false,
    var horn_pulse: Boolean = false,         // TAMBAHKAN INI
    var engine_pulse: Boolean = false,       // TAMBAHKAN INI
    var cruiseSpeedUp: Boolean = false,
    var cruiseSpeedDown: Boolean = false,
    var shiftUp: Boolean = false,
    var shiftDown: Boolean = false
)