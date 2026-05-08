# ETS2 Mobile Controller

A high-performance, low-latency Android controller system for **Euro Truck Simulator 2** built on a **Hardware-in-the-Loop (HIL)** architecture. This project transforms an Android smartphone into a fully-synchronized steering wheel, pedal set, and dashboard control panel — with bidirectional telemetry extracted directly from ETS2's game memory.

> **Language Stack:** Kotlin (Android Client) · Python (PC Bridge & Telemetry Server)

---

## Table of Contents

- [Architecture Overview](#architecture-overview)
- [Core Technical Specifications](#core-technical-specifications)
- [Key Features](#key-features)
- [Repository Structure](#repository-structure)
- [Prerequisites](#prerequisites)
- [Installation & Setup](#installation--setup)
- [Network Topology](#network-topology)
- [Notes on Latency](#notes-on-latency)
- [License](#license)

---

## Architecture Overview

This project operates on a **Full-Duplex UDP** communication model with a strict **Single Source of Truth** principle. The Android client acts as a pure **dumb terminal** — it only renders visual state and transmits raw input. All operational state transitions (Engine, Lights, Wipers, Parking Brake) are validated and governed exclusively by ETS2's internal game memory, eliminating any risk of state desynchronization between the app and the simulator.

```
┌─────────────────────────────────────────────────────────────┐
│                      Android Client                         │
│         (Jetpack Compose · Kotlin · Dumb Terminal)          │
│                                                             │
│   Controller Input ──────────────────► UDP TX (60Hz)        │
│   Visual State    ◄────────────────── UDP RX (Telemetry)    │
└─────────────────────────────────────────────────────────────┘
                             │ UDP Full-Duplex
                             │ 16-Byte Bitwise Payload
                             ▼
┌─────────────────────────────────────────────────────────────┐
│                       Python Bridge                         │
│                   (Host PC · Windows)                       │
│                                                             │
│   UDP RX ──► Bitwise Deserialize ──► vgamepad (XInput API)  │
│   mmap   ──► SCS Telemetry SDK   ──► UDP TX (State Mirror)  │
└─────────────────────────────────────────────────────────────┘
                             │ Memory Mapped File
                             ▼
                    ┌────────────────┐
                    │   ETS2 Engine  │
                    │ (Source of     │
                    │    Truth)      │
                    └────────────────┘
```

---

## Core Technical Specifications

### Single Source of Truth Architecture
The Android client is isolated as a pure visual projector. All operational state transitions — Engine, Lights, Wipers, Parking Brake — are validated and controlled exclusively by ETS2's internal memory. This design guarantees zero state desynchronization between the physical controller and the simulator.

### Reverse-Beacon Handshake Protocol
An adaptive auto-discovery mechanism that inspects the PC's routing table in real-time to identify the Android device's gateway IP. This protocol proactively bypasses Windows Firewall restrictions and AP Isolation without requiring static IP configuration on either device.

### 16-Byte Bitwise Payload Compression
Low-level network transmission optimization using bitwise `Int32` bit-shift operations to pack dozens of boolean variables into a single compact payload. This replaces expensive String parsing, eliminating Garbage Collection thrashing at 60Hz tick rates.

### 900-Degree Absolute Steering Resolution
Raw mobile device input vectors are normalized to an absolute ±450° range, creating a precise 1:1 linear mapping against ETS2's steering physics engine — enabling smooth, full-lock steering without dead zones or jitter.

### SCS Telemetry SDK Integration (mmap)
Vehicle telemetry data (component status, speed, RPM, gear) is extracted via **Memory Mapped File** (`mmap`) — a direct OS-level interface provided by the SCS SDK. This delivers cache-speed data access without illegal memory hooking or unstable injection techniques.

---

## Key Features

**Controller Input**
- 900° absolute steering with `atan2` delta accumulation (prevents wrap-around jitter on continuous multi-rotation)
- Analog throttle and brake via slide-to-fill UI (Left/Right Trigger XInput emulation)
- Sequential shifter (Up/Down)

**Dashboard Controls**
- Engine Start/Stop
- Parking Brake toggle
- Horn
- Light mode cycling & High Beam
- Wipers
- Lane Assist toggle
- Cruise Control (Set, Speed Up, Speed Down)
- Left/Right Turn Signals with **Auto-Cancel** (disengages automatically after >90° steering recovery)

**Telemetry & Connectivity**
- Real-time bidirectional telemetry: Speed, RPM, Gear, component states mirrored from ETS2 memory
- Reverse-Beacon auto-discovery — no manual IP configuration needed
- Live connection status indicator (Green = active, Red = disconnected)

---

## Repository Structure

```
ETS2_Mobile_Controller/
├── android_client/          # Kotlin/Jetpack Compose Android application
│   └── ...                  # UI, UDP transmitter/receiver, input logic
├── python_bridge/           # Python host-side server
│   └── bridge_ets2.py       # UDP receiver, vgamepad emulation, SCS mmap reader
├── .gitignore
└── readme.md
```

---

## Prerequisites

**PC (Host)**
- Windows OS
- Python 3.x
- [ViGEmBus Driver](https://github.com/nefarius/ViGEmBus/releases) — required for virtual gamepad emulation
- SCS Telemetry SDK plugin installed in ETS2 (`scs-telemetry.dll` in the game's `bin/win_x64/plugins/` folder)

**Android Device**
- Android with Developer Options enabled
- USB Debugging enabled (for sideloading the APK)

**Network**
- USB Tethering *(recommended)* or Mobile Wi-Fi Hotspot

---

## Installation & Setup

### 1. Install ViGEmBus (one-time)

Download and install the [ViGEmBus driver](https://github.com/nefarius/ViGEmBus/releases) on your PC. This is required for the Python bridge to emulate an Xbox 360 controller at the OS level.

### 2. Install SCS Telemetry SDK Plugin

Copy the SCS Telemetry SDK plugin DLL into your ETS2 installation:

```
<ETS2_install_dir>/bin/win_x64/plugins/scs-telemetry.dll
```

This enables the Python bridge to read live game telemetry via Memory Mapped File without memory hooking.

### 3. PC Setup — Python Bridge

Install the required Python dependency:

```bash
pip install vgamepad
```

### 4. Android Setup — Kotlin Client

1. Open the `android_client/` project in **Android Studio**
2. Build the project and install the APK on your Android device via USB Debugging

> **Note:** The Reverse-Beacon Handshake Protocol handles IP discovery automatically — no manual IP configuration is required in the source code.

### 5. Running the System

**Start the Python bridge on your PC:**
```bash
cd python_bridge
python bridge_ets2.py
```

**On your Android device:**
1. Connect via USB Tethering or Mobile Hotspot
2. Open the ETS2 Mobile Controller app
3. The connection indicator (top-right) should turn **Green**

**In ETS2:**
1. Go to **Options → Controls**
2. Select **Keyboard + XInput Gamepad 1**
3. Bind steering, pedals, and dashboard buttons as desired

---

## Network Topology

| Mode | Latency | Stability | Setup |
|---|---|---|---|
| **USB Tethering** | ~1–5ms | ✅ Excellent | Plug in cable, enable tethering |
| **Wi-Fi Hotspot** | ~5–20ms | ✅ Good | Connect to phone's hotspot |
| **Local Wi-Fi Router** | ~5–30ms | ⚠️ Variable | May be affected by AP isolation |

USB Tethering is strongly recommended for competitive or precision driving as it eliminates packet loss and interference entirely.

---

## Notes on Latency

The system is engineered for minimum perceptible latency across the entire signal chain:

- **Input → UDP TX:** Sub-frame, non-blocking coroutine on the Android side
- **UDP RX → XInput API:** Sub-millisecond processing in the Python bridge
- **Telemetry mmap read:** Cache-speed access — no disk or network I/O
- **60Hz tick rate** balances responsiveness with network efficiency

End-to-end input latency over USB Tethering is typically **under 10ms**.

---

## License

This project is open-source and available for modification and personal use. Attribution appreciated but not required.