package com.cera.ets2controller

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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

// ==========================================
// 1. ENUMS
// ==========================================
enum class LightMode { OFF, PARKING, LOW_BEAM }
enum class SignalMode { OFF, LEFT, RIGHT, HAZARD }

// ==========================================
// 2. VIEWMODEL (Isolasi Logika & Jaringan)
// ==========================================
class ControllerViewModel : ViewModel() {
    // Analog States
    var steeringAngle by mutableFloatStateOf(0f)
    var gasValue by mutableFloatStateOf(0f)
    var brakeValue by mutableFloatStateOf(0f)

    // Digital States (Toggle)
    var lightMode by mutableStateOf(LightMode.OFF)
    var signalMode by mutableStateOf(SignalMode.OFF)
    var isHighBeamOn by mutableStateOf(false)
    var isEngineOn by mutableStateOf(false)
    var isLaneAssistOn by mutableStateOf(false)
    var isCruiseOn by mutableStateOf(false)
    var isParkingBrakeOn by mutableStateOf(false)

    // Digital States (Momentary)
    var isWiperPressed by mutableStateOf(false)
    var isHornPressed by mutableStateOf(false)
    var isShifterUpPressed by mutableStateOf(false)
    var isShifterDownPressed by mutableStateOf(false)
    var isCruiseUpPressed by mutableStateOf(false)
    var isCruiseDownPressed by mutableStateOf(false)

    var speedKmh by mutableFloatStateOf(0f)
    var engineRpm by mutableFloatStateOf(0f)
    var gear by mutableIntStateOf(0)
    var fuelCapacity by mutableFloatStateOf(0f)

    // System States
    var isConnected by mutableStateOf(false)
    private var socket: DatagramSocket? = null
    private var receiverSocket: DatagramSocket? = null // INTERVENSI 1: Deklarasi global

    init {
        startUdpTransmitter()
        startTelemetryReceiver()
    }

    private fun startUdpTransmitter() {
        viewModelScope.launch(Dispatchers.IO) {
            var serverAddress: InetAddress? = null
            val port = 65432

            try {
                // Buka soket di port spesifik
                socket = DatagramSocket(port)

                // ==========================================
                // FASE 1: PASSIVE LISTENER (Reverse-Beacon)
                // ==========================================
                socket?.soTimeout = 0 // Menunggu tanpa batas waktu

                val receiveData = ByteArray(1024)
                val receivePacket = DatagramPacket(receiveData, receiveData.size)

                while (serverAddress == null) {
                    socket?.receive(receivePacket)
                    val response = String(receivePacket.data, 0, receivePacket.length)

                    // Memverifikasi paket ping dari PC
                    if (response.trim() == "ETS2_PC_HERE") {
                        serverAddress = receivePacket.address
                        isConnected = true
                    }
                }

                // ==========================================
                // FASE 2: TELEMETRY TRANSMITTER (16-Byte Bitwise)
                // ==========================================
                val buffer = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)

                while (true) {
                    var buttonsMask = 0
                    if (isParkingBrakeOn) buttonsMask = buttonsMask or (1 shl 0)
                    buttonsMask = buttonsMask or (lightMode.ordinal shl 1)
                    if (isHighBeamOn) buttonsMask = buttonsMask or (1 shl 3)
                    buttonsMask = buttonsMask or (signalMode.ordinal shl 4)
                    if (isHornPressed) buttonsMask = buttonsMask or (1 shl 6)
                    if (isShifterUpPressed) buttonsMask = buttonsMask or (1 shl 7)
                    if (isShifterDownPressed) buttonsMask = buttonsMask or (1 shl 8)
                    if (isCruiseUpPressed) buttonsMask = buttonsMask or (1 shl 9)
                    if (isCruiseOn) buttonsMask = buttonsMask or (1 shl 10)
                    if (isCruiseDownPressed) buttonsMask = buttonsMask or (1 shl 11)
                    if (isLaneAssistOn) buttonsMask = buttonsMask or (1 shl 12)
                    if (isEngineOn) buttonsMask = buttonsMask or (1 shl 13)
                    if (isWiperPressed) buttonsMask = buttonsMask or (1 shl 14)

                    buffer.clear()
                    buffer.putFloat(steeringAngle)
                    buffer.putFloat(gasValue)
                    buffer.putFloat(brakeValue)
                    buffer.putInt(buttonsMask)

                    // Kirim telemetry balik ke alamat IP PC yang didapatkan
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

    private fun startTelemetryReceiver() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                receiverSocket = DatagramSocket(65433) // INTERVENSI 2: Gunakan variabel global
                val receiveData = ByteArray(16)
                val receivePacket = DatagramPacket(receiveData, receiveData.size)
                val buffer = ByteBuffer.wrap(receiveData).order(ByteOrder.LITTLE_ENDIAN)

                while (true) {
                    receiverSocket?.receive(receivePacket)
                    if (receivePacket.length == 16) {
                        buffer.clear()
                        speedKmh = buffer.float
                        engineRpm = buffer.float
                        gear = buffer.int
                        fuelCapacity = buffer.float
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setBrake(value: Float) {
        brakeValue = value
        if (value > 0.05f) isCruiseOn = false
    }

    override fun onCleared() {
        super.onCleared()
        socket?.close()
        receiverSocket?.close() // INTERVENSI 3: Cegah Memory Leak
    }
}
