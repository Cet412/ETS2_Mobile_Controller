package com.cera.ets2controller

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

enum class LightMode { OFF, PARKING, LOW_BEAM }

class ControllerViewModel : ViewModel() {
    // ==========================================
    // INPUT STATES (Disentuh oleh Pengguna)
    // ==========================================
    var steeringAngle by mutableFloatStateOf(0f)
    var gasValue by mutableFloatStateOf(0f)
    var brakeValue by mutableFloatStateOf(0f)

    var inEngine by mutableStateOf(false)
    var inParkingBrake by mutableStateOf(false)
    var inLight by mutableStateOf(false)
    var inHighBeam by mutableStateOf(false)
    var inWiper by mutableStateOf(false)
    var inHorn by mutableStateOf(false)
    var inSigLeft by mutableStateOf(false)
    var inSigRight by mutableStateOf(false)
    var inHazard by mutableStateOf(false)
    var inShifterUp by mutableStateOf(false)
    var inShifterDown by mutableStateOf(false)
    var inCruiseToggle by mutableStateOf(false)
    var inCruiseUp by mutableStateOf(false)
    var inCruiseDown by mutableStateOf(false)
    var inLaneAssist by mutableStateOf(false)

    // ==========================================
    // TELEMETRY STATES (Dikendalikan oleh ETS2)
    // ==========================================
    var telemEngine by mutableStateOf(false)
    var telemParkingBrake by mutableStateOf(false)
    var telemLightMode by mutableStateOf(LightMode.OFF)
    var telemHighBeam by mutableStateOf(false)
    var telemWiper by mutableStateOf(false)
    var telemSigLeft by mutableStateOf(false)
    var telemSigRight by mutableStateOf(false)
    var telemCruise by mutableStateOf(false)

    var isConnected by mutableStateOf(false)

    private var socket: DatagramSocket? = null
    private var receiverSocket: DatagramSocket? = null

    init {
        startUdpTransmitter()
        startTelemetryReceiver()
    }

    private fun startTelemetryReceiver() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                receiverSocket = DatagramSocket(65433)
                val receiveData = ByteArray(16)
                val receivePacket = DatagramPacket(receiveData, receiveData.size)
                val buffer = ByteBuffer.wrap(receiveData).order(ByteOrder.LITTLE_ENDIAN)

                while (true) {
                    receiverSocket?.receive(receivePacket)
                    if (receivePacket.length == 16) {
                        buffer.clear()
                        val telemMask = buffer.int

                        telemEngine = (telemMask shr 0 and 1) == 1
                        telemParkingBrake = (telemMask shr 1 and 1) == 1
                        val isLowBeam = (telemMask shr 2 and 1) == 1
                        telemHighBeam = (telemMask shr 3 and 1) == 1
                        telemWiper = (telemMask shr 4 and 1) == 1
                        telemSigLeft = (telemMask shr 5 and 1) == 1
                        telemSigRight = (telemMask shr 6 and 1) == 1
                        telemCruise = (telemMask shr 7 and 1) == 1
                        val isParking = (telemMask shr 8 and 1) == 1

                        // INJEKSI LOGIKA REKONSTRUKSI ENUM
                        telemLightMode = when {
                            isLowBeam -> LightMode.LOW_BEAM
                            isParking -> LightMode.PARKING
                            else -> LightMode.OFF
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun startUdpTransmitter() {
        viewModelScope.launch(Dispatchers.IO) {
            var serverAddress: InetAddress? = null
            val port = 65432

            try {
                socket = DatagramSocket(port)
                socket?.soTimeout = 0

                val receiveData = ByteArray(1024)
                val receivePacket = DatagramPacket(receiveData, receiveData.size)

                while (serverAddress == null) {
                    socket?.receive(receivePacket)
                    val response = String(receivePacket.data, 0, receivePacket.length)
                    if (response.trim() == "ETS2_PC_HERE") {
                        serverAddress = receivePacket.address
                        isConnected = true
                    }
                }

                val buffer = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)

                while (true) {
                    var inputMask = 0
                    if (inParkingBrake) inputMask = inputMask or (1 shl 0)
                    if (inLight) inputMask = inputMask or (1 shl 1)
                    if (inHighBeam) inputMask = inputMask or (1 shl 2)
                    if (inWiper) inputMask = inputMask or (1 shl 3)
                    if (inHorn) inputMask = inputMask or (1 shl 4)
                    if (inSigLeft) inputMask = inputMask or (1 shl 5)
                    if (inSigRight) inputMask = inputMask or (1 shl 6)
                    if (inHazard) inputMask = inputMask or (1 shl 7)
                    if (inShifterUp) inputMask = inputMask or (1 shl 8)
                    if (inShifterDown) inputMask = inputMask or (1 shl 9)
                    if (inCruiseToggle) inputMask = inputMask or (1 shl 10)
                    if (inCruiseUp) inputMask = inputMask or (1 shl 11)
                    if (inCruiseDown) inputMask = inputMask or (1 shl 12)
                    if (inLaneAssist) inputMask = inputMask or (1 shl 13)
                    if (inEngine) inputMask = inputMask or (1 shl 14)

                    buffer.clear()
                    buffer.putFloat(steeringAngle)
                    buffer.putFloat(gasValue)
                    buffer.putFloat(brakeValue)
                    buffer.putInt(inputMask)

                    val packet = DatagramPacket(buffer.array(), 16, serverAddress, port)

                    try {
                        socket?.send(packet)
                    } catch (e: Exception) {
                        isConnected = false
                    }

                    delay(16)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                isConnected = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        socket?.close()
        receiverSocket?.close()
    }
}