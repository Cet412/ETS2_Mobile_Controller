# ETS2 Mobile Controller

A high-performance, low-latency Android controller system for **Euro Truck Simulator 2** built on a **Hardware-in-the-Loop (HIL)** architecture. This project transforms an Android smartphone into a fully-synchronized steering wheel, pedal set, and dashboard control panel — with bidirectional telemetry extracted directly from ETS2's game memory via the SCS SDK.

> **Language Stack:** Kotlin (Android Client) · Python (PC Bridge & Telemetry Server)

---

## Table of Contents

- [How It Works](#how-it-works)
- [Architecture Overview](#architecture-overview)
- [Core Technical Specifications](#core-technical-specifications)
- [Key Features](#key-features)
- [Repository Structure](#repository-structure)
- [Installation](#installation)
- [ETS2 Controls Configuration](#ets2-controls-configuration)
- [Network Topology](#network-topology)
- [License](#license)

---

## How It Works

This system operates across two simultaneously running applications:

**PC Application (Server):** An invisible bridge program that receives control signals from the Android device, translates them into a virtual Xbox 360 controller input via the XInput API, and reads live vehicle state data from ETS2's game memory.

**Android Application (Client):** A touch interface displaying a steering wheel, pedals, and dashboard controls. It transmits input to the PC at ~60Hz and receives telemetry back from the game — so when the engine or lights change state inside ETS2, the corresponding buttons on your phone update automatically to reflect the actual in-game state.

---

## Architecture Overview

The system uses a **Full-Duplex UDP** communication model with a strict **Single Source of Truth** principle. The Android client acts as a pure **dumb terminal** — it only renders visual state and transmits raw input. All operational state transitions (Engine, Lights, Wipers, Turn Signals, Parking Brake) are governed exclusively by ETS2's internal memory via the SCS SDK, eliminating any risk of desynchronization between the controller and the simulator.

```
┌─────────────────────────────────────────────────────────────┐
│                      Android Client                         │
│         (Jetpack Compose · Kotlin · Dumb Terminal)          │
│                                                             │
│   Controller Input ──────────────────► UDP TX Port 65432    │
│   Visual State     ◄────────────────── UDP RX Port 65433    │
└─────────────────────────────────────────────────────────────┘
                             │ UDP Full-Duplex
                             │ 16-Byte Bitwise Payload
                             ▼
┌─────────────────────────────────────────────────────────────┐
│                  PC Bridge (Python → .exe)                  │
│                   (Host PC · Windows)                       │
│                                                             │
│   UDP RX ──► Bitwise Deserialize ──► vgamepad (XInput API)  │
│   mmap   ──► SCS Telemetry SDK   ──► UDP TX (State Mirror)  │
└─────────────────────────────────────────────────────────────┘
                             │ Memory Mapped File (mmap)
                             ▼
                    ┌────────────────────┐
                    │    ETS2 Engine     │
                    │  (Source of Truth) │
                    └────────────────────┘
```

---

## Core Technical Specifications

### Single Source of Truth Architecture
The Android client is isolated as a pure visual projector. All state transitions — Engine, Lights, Wipers, Turn Signals, Parking Brake — are read exclusively from ETS2's internal memory via the SCS SDK. This guarantees zero desynchronization between the controller UI and the simulator at all times.

### Reverse-Beacon Handshake Protocol
A zero-configuration auto-discovery mechanism. On startup, the PC bridge inspects the system routing table in real-time to identify the Android device's gateway IP, then broadcasts a beacon (`ETS2_PC_HERE`) until the Android client responds. This bypasses Windows Firewall restrictions and AP Isolation without requiring any static IP configuration on either device.

### 16-Byte Bitwise Payload Compression
Both the input (Android → PC) and telemetry (PC → Android) channels use a fixed 16-byte UDP payload. Boolean states (button presses, game states) are packed into a single `Int32` bitmask via bit-shift operations, replacing expensive string parsing and eliminating Garbage Collection thrashing at 60Hz tick rates.

**Input payload structure** `<fffI` (Android → PC, Port 65432):
| Bytes | Field |
|---|---|
| 0–3 | Steering angle (Float, ±450°) |
| 4–7 | Gas value (Float, 0.0–1.0) |
| 8–11 | Brake value (Float, 0.0–1.0) |
| 12–15 | Button bitmask (Int32) |

**Telemetry payload structure** `<Ifff` (PC → Android, Port 65433):
| Bytes | Field |
|---|---|
| 0–3 | Game state bitmask (Int32) |
| 4–15 | Reserved (3× Float) |

### 900-Degree Absolute Steering Resolution
Raw touch input vectors are processed via `atan2` delta accumulation, preventing wrap-around jitter during continuous multi-rotation gestures. The accumulated angle is normalized to an absolute ±450° range, creating a precise 1:1 linear mapping to ETS2's steering physics engine.

### SCS Telemetry SDK Integration (mmap)
Vehicle telemetry — Engine state, Parking Brake, Low Beam, High Beam, Wipers, Turn Signals, Cruise Control, Parking Lights — is extracted via **Memory Mapped File** (`mmap`), a direct OS-level interface provided by the official SCS SDK plugin (`scs-telemetry.dll`). This delivers cache-speed data access without memory hooking or unstable injection techniques.

---

## Key Features

**Steering & Pedals**
- 900° absolute steering with `atan2` delta accumulation (anti wrap-around jitter)
- Analog throttle and brake via slide-to-fill UI (Right/Left Trigger XInput emulation)
- Steering returns to center with spring animation on release

**Transmission**
- Sequential shifter (Up / Down)

**Lighting & Signals**
- Light mode cycling (Off → Parking → Low Beam) — synced from ETS2 via telemetry
- High Beam toggle — synced from ETS2 via telemetry
- Left / Right Turn Signals — synced and auto-cancelled by ETS2 (deactivates automatically when the in-game steering wheel returns past the turn threshold)
- Hazard lights

**Utilities**
- Engine Start / Stop — synced from ETS2 via telemetry
- Parking Brake — synced from ETS2 via telemetry
- Horn
- Wipers — synced from ETS2 via telemetry
- Lane Assist toggle *(local state — SCS SDK does not expose lane assist active state)*
- Cruise Control (Set / Speed Up / Speed Down) — active state synced from ETS2 via telemetry

**Connectivity**
- Zero-configuration Reverse-Beacon auto-discovery (no manual IP setup required)
- Real-time connection indicator: 🔴 Red = searching, 🟢 Green = connected
- On-app connection protocol guide (scrollable, state-persistent through screen rotation)

---

## Repository Structure

```
ETS2_Mobile_Controller/
├── android_client/                  # Kotlin/Jetpack Compose Android application
│   └── app/src/main/java/
│       └── com/cera/ets2controller/
│           ├── MainActivity.kt      # UI composables, steering wheel, pedals, buttons
│           └── ControllerViewModel.kt # UDP TX/RX logic, state management, bitmasking
├── python_bridge/
│   ├── bridge_ets2.py               # UDP server, vgamepad emulation, SCS mmap telemetry
│   └── scs-telemetry.dll            # SCS SDK plugin for ETS2 game memory access
├── build_installer.iss              # Inno Setup script — builds ETS2_Controller_PC_Setup.exe
├── icon.ico                         # Application icon for the PC server binary
└── readme.md
```

---

## Installation

### PC Setup

**1. Run the Installer**

Execute `ETS2_Controller_PC_Setup.exe`. The installation wizard will:
- Install `ETS2 Controller Server.exe` and create a Desktop shortcut
- Ask you to select your ETS2 installation directory (validates `bin\win_x64\eurotrucks2.exe` before proceeding)
- Automatically inject `scs-telemetry.dll` into the game's plugin folder (`bin\win_x64\plugins\`)
- Automatically register Windows Firewall rules for UDP ports 65432 and 65433

> **Default ETS2 path (Steam):** `C:\Program Files (x86)\Steam\steamapps\common\Euro Truck Simulator 2`

**2. Install ViGEmBus Driver** *(one-time, if not already installed)*

The PC server requires the [ViGEmBus driver](https://github.com/nefarius/ViGEmBus/releases) to emulate a virtual Xbox 360 controller at the OS level.

---

### Android Setup

Install `app-release.apk` on your Android device like any standard APK sideload (enable *Install from unknown sources* if prompted).

---

### Running the System

**Step 1 — Configure Network**

Connect your Android device to your PC via one of these methods:
- **USB Tethering** *(recommended)* — Android Settings → Hotspot & Tethering → USB Tethering
- **Mobile Hotspot** — Enable on Android, connect PC Wi-Fi to the phone's hotspot

**Step 2 — Start the PC Server**

Double-click the **"ETS2 Controller Server"** shortcut on your Desktop. A console window will open — leave it running in the background.

**Step 3 — Open the Android App**

Open the ETS2 Controller app. The indicator in the top-right corner will turn **Green** once the connection is established automatically.

**Step 4 — Launch ETS2**

Open Euro Truck Simulator 2, then configure controls as described below.

---

## ETS2 Controls Configuration

In ETS2, go to **Options → Controls** and configure the following:

**1. Controller Profile**

Set the control scheme to **Keyboard + Xbox 360 Controller** (or equivalent XInput gamepad).

**2. Axes** (Options → Controls → Axes)

| Axis | Binding | Mode |
|---|---|---|
| Steering | Left Stick X | Centered |
| Acceleration | Right Trigger | Inverted + Centered |
| Braking | Left Trigger | Normal |

**3. Buttons** (Options → Keys and Buttons)

Bind each function in the **secondary** slot. 

| Function | XInput Button |
|---|---|
| Engine Start/Stop | Start |
| Parking Brake | B |
| Light Modes | X |
| High Beam | Right Stick Click |
| Horn | Left Stick Click |
| Left Turn Signal | D-Pad Left |
| Right Turn Signal | D-Pad Right |
| Hazard Lights | Back |
| Shift Up | Right Bumper (RB) |
| Shift Down | Left Bumper (LB) |
| Cruise Control Toggle | Y |
| Cruise Speed Up | D-Pad Up |
| Cruise Speed Down | D-Pad Down |
| Lane Assist | A |
| Wipers | Right Stick Y-Axis (up) |

---

## Network Topology

| Mode | Latency | Stability | Notes |
|---|---|---|---|
| **USB Tethering** | ~1–5ms | ✅ Excellent | Recommended — zero packet loss, no interference |
| **Mobile Hotspot** | ~5–20ms | ✅ Good | Reliable alternative without a cable |
| **Local Wi-Fi Router** | ~5–30ms | ⚠️ Variable | May be blocked by AP Isolation — not recommended |

> Routers with AP Isolation enabled will block the Reverse-Beacon discovery protocol. USB Tethering or Mobile Hotspot are the only fully supported network topologies.

---

## License

This project is open-source and available for personal use and modification. Attribution appreciated but not required.