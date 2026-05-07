# ETS2 Mobile Controller

A high-performance, low-latency Android-based virtual controller designed specifically for Euro Truck Simulator 2 (ETS2) and other PC driving simulators. This project turns your Android smartphone into a fully functional steering wheel, pedal set, and dashboard button box.

## Architecture

This project utilizes a **Monorepo** structure, divided into two main components communicating via a high-speed UDP network bridge.

1. **Android Client (`android_client/`)**: 
   - Built with modern **Jetpack Compose** and **Kotlin**.
   - Zero-blocking UI with `ConstraintLayout` for responsive scaling.
   - Transmits controller state (steering angle, pedals, buttons) as a lightweight UDP payload at a ~60Hz tick rate.
2. **Python Bridge (`python_bridge/`)**: 
   - A lightweight UDP receiver script running on the host PC.
   - Utilizes `vgamepad` (ViGEmBus wrapper) to emulate a physical Xbox 360 Controller at the OS level.
   - Parses the incoming Android payload and translates it into XInput API calls with sub-millisecond latency.

## Key Features

- **360° Continuous Steering**: Advanced `atan2` delta accumulation logic to prevent wrap-around jitter, allowing continuous multi-rotation steering.
- **Analog Pedals**: Slide-to-fill UI mechanics for precise throttle and brake control (Left/Right Trigger emulation).
- **Auto-Canceling Turn Signals**: Smart state tracking that automatically disables blinkers after completing a turn (>90° recovery).
- **Extensive Dashboard Controls**:
  - Engine Start/Stop, Parking Brake, Horn
  - Cycle Light Modes & High Beam
  - Wipers, Lane Assist Toggle
  - Sequential Shifter (Up/Down)
  - Cruise Control (Set, Up, Down)
- **Real-Time Telemetry Indicator**: Visual ping indicator to ensure the UDP connection to the PC is active and stable.

## Installation & Setup

### Prerequisites
- **PC**: Windows OS, Python 3.x installed.
- **Android**: Android device with Developer Options enabled (for debugging/building).
- **Network**: USB Data Cable (Recommended: USB Tethering) or Mobile Wi-Fi Hotspot for local LAN connection.

### 1. PC Setup (Python Bridge)
1. Install the required Virtual Gamepad emulation library:
   ```bash
   pip install vgamepad
   ```
2. Find your PC's IPv4 Address assigned by your phone (via USB Tethering or Hotspot) using `ipconfig` in the Command Prompt.

### 2. Android setup (kotlin client)
1. open the `android_client` project in Android Studio
2. Navigate to `MainActivity.kt`
3. Locate the `UDP TRANSMITTER LOOP` and update the `serverAddress` with your PC's IPv4 address
    ```Kotlin 
    val serverAddress = InetAddress.getByName("YOUR_PC_IP_HERE")
    ```
4. Build and install the APK on your Android device.

### 3. Execution
1. Run the Python bridge on your PC
```bash
cd python_bridge
python bridge_ets2.py
```
2. Open the app on your Android device. The top-right indicator should turn Green
3. Open ETS2 (or any simulator), go to Controls, select Keyboard + XInput Gamepad 1, and bind your steering, pedals, and buttons as desired.

## NOTES ON LATENCY
For the ultimate zero-latency experience without packet loss, USB Tethering is strictly recommended over standard Wi-Fi setups.
Although Wi-Fi setups already have low latency.

## License
This project is Open-Source and available for modification