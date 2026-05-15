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

**PC Application (Server):** An invisible bridge program that receives control signals from the Android device, translates them into virtual joystick and gamepad input via vJoy and ViGEmBus, and reads live vehicle state data from ETS2's game memory.

**Android Application (Client):** A touch interface displaying a steering wheel, pedals, and dashboard controls. It transmits input to the PC at ~60Hz and receives telemetry back from the game — so when the engine or lights change state inside ETS2, the corresponding buttons on your phone update automatically to reflect the actual in-game state.

---

## Architecture Overview

The system uses a **Full-Duplex UDP** communication model with a strict **Single Source of Truth** principle. The Android client acts as a pure **dumb terminal** — it only renders visual state and transmits raw input. All operational state transitions (Engine, Lights, Wipers, Turn Signals, Parking Brake) are governed exclusively by ETS2's internal memory via the SCS SDK, eliminating any risk of desynchronization between the controller and the simulator.

```
┌─────────────────────────────────────────────────────────────┐
│                      Android Client                         │
│         (Jetpack Compose · Kotlin · Dumb Terminal)          │
│                                                             │
│   Controller Input ──────────────────► UDP TX Port 45432    │
│   Visual State     ◄────────────────── UDP RX Port 45433    │
└─────────────────────────────────────────────────────────────┘
                             │ UDP Full-Duplex
                             │ 16-Byte Bitwise Payload
                             ▼
┌─────────────────────────────────────────────────────────────┐
│              PC Bridge (Python → .exe)                      │
│                   (Host PC · Windows)                       │
│                                                             │
│   UDP RX ──► Bitwise Deserialize ──► vJoy (Steering/Pedals) │
│                                  ──► vgamepad (Buttons)     │
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
A zero-configuration auto-discovery mechanism. On startup, the PC bridge dynamically detects the active network interface and broadcasts a beacon (`ETS2_PC_HERE`) until the Android client responds. This bypasses Windows Firewall restrictions and AP Isolation without requiring any static IP configuration on either device.

### 16-Byte Bitwise Payload Compression
Both the input (Android → PC) and telemetry (PC → Android) channels use a fixed 16-byte UDP payload. Boolean states (button presses, game states) are packed into a single `Int32` bitmask via bit-shift operations, replacing expensive string parsing and eliminating Garbage Collection thrashing at 60Hz tick rates.

**Input payload structure** `<fffI` (Android → PC, Port 45432):
| Bytes | Field |
|---|---|
| 0–3 | Steering (Float, normalized –1.0 to +1.0) |
| 4–7 | Gas value (Float, 0.0–1.0) |
| 8–11 | Brake value (Float, 0.0–1.0) |
| 12–15 | Button bitmask (Int32) |

**Telemetry payload structure** `<Ifff` (PC → Android, Port 45433):
| Bytes | Field |
|---|---|
| 0–3 | Game state bitmask (Int32) |
| 4–7 | Steering position normalized (Float, –1.0 to +1.0) |
| 8–15 | Reserved (2× Float) |

### Adaptive Steering Sensitivity System
The steering range is fully adjustable from within the app (180°–1800° lock-to-lock) without restarting or reconnecting. The `maxUiSteeringAngle` value acts as the scaling factor for both input normalization and telemetry feedback — ensuring the visual steering wheel on the phone always remains 1:1 synchronized with ETS2 regardless of the configured range.

When the user releases the steering wheel, it returns to center. Telemetry then takes over and mirrors the actual in-game steering position — so if the truck is stationary and the in-game wheel is centered, the phone wheel stays centered too.

### Dual Virtual Device Architecture
Steering and pedals are handled by **vJoy** (absolute axis, 32768-step resolution) for precise sim-racing grade input. Dashboard buttons are handled by **ViGEmBus/vgamepad** (XInput) for broad compatibility with ETS2's button binding system. ETS2 is configured to use both devices simultaneously via its multi-controller input profile.

### SCS Telemetry SDK Integration (mmap)
Vehicle telemetry — Engine state, Parking Brake, Low Beam, High Beam, Wipers, Turn Signals, Cruise Control, Parking Lights, and Steering Position — is extracted via **Memory Mapped File** (`mmap`), a direct OS-level interface provided by the official SCS SDK plugin (`scs-telemetry.dll`). This delivers cache-speed data access without memory hooking or unstable injection techniques.

---

## Key Features

**Steering & Pedals**
- Adjustable lock-to-lock range: **180°–1800°** configurable in-app without restart
- Absolute steering position via vJoy axis (32768-step resolution) — true sim-racing grade input
- Real-time 1:1 mirror between phone steering wheel and ETS2
- Analog throttle and brake via slide-to-fill UI (vJoy absolute axis)
- Steering returns to center on release; follows ETS2 telemetry when idle

**Transmission**
- Sequential shifter (Up / Down)

**Lighting & Signals**
- Light mode cycling (Off → Parking → Low Beam) — synced from ETS2 via telemetry
- High Beam toggle — synced from ETS2 via telemetry
- Left / Right Turn Signals — synced and auto-cancelled by ETS2
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
- Dynamic network interface detection — automatically binds to the active hotspot/tethering adapter
- Real-time connection indicator: 🔴 Red = searching, 🟢 Green = connected
- In-app connection protocol guide (scrollable, state-persistent through screen rotation)

---

## Repository Structure

```
ETS2_Mobile_Controller/
├── android_client/                        # Kotlin/Jetpack Compose Android application
│   └── app/src/main/java/
│       └── com/cera/ets2controller/
│           ├── MainActivity.kt            # UI composables, steering wheel, pedals, buttons, menus
│           └── ControllerViewModel.kt     # UDP TX/RX logic, state management, bitmasking
├── Bridge/
│   ├── Driver/
│   │   ├── install_driver.bat             # Silent installer for ViGEmBus & vJoy
│   │   ├── install_ets2_plugin.bat        # Copies scs-telemetry.dll to ETS2 plugins folder
│   │   ├── ViGEmBus_1.22.0_x64_x86_arm64.exe
│   │   └── vJoySetup.exe
│   ├── python_bridge/
│   │   └── ETS2_Controller_Server.py      # UDP server, vJoy/vgamepad emulation, SCS mmap telemetry
│   └── Dependencies/
│       └── scs-telemetry.dll              # SCS SDK plugin for ETS2 game memory access
├── build_installer.iss                    # Inno Setup script — builds ETS2_Controller_Setup.exe
├── icon.ico
└── readme.md
```

---

## Installation

### PC Setup

**Run the Installer**

Execute `ETS2_Controller_Setup.exe`. The wizard will install the server application and create a Desktop shortcut.

At the end of the wizard, two optional post-install steps are presented:

- **Install required drivers (ViGEmBus & vJoy)** — run this on first install. Both drivers are bundled and installed silently. A system restart may be required.
- **Install ETS2 Telemetry Plugin** — copies `scs-telemetry.dll` to your ETS2 plugins folder. You will be prompted to enter your ETS2 installation path if it is not found automatically.

> Both steps can be re-run at any time from `{install_dir}\Driver\`.

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

### 1. Controller Profile

Set the primary input to **Keyboard + vJoy Device**. Then add **+ XInput Gamepad 1** as the secondary input device.

### 2. Steering Animation Range

Go to **Options → Controls** and set **Steering Animation Range** to match your preferred lock-to-lock range. This value must be consistent with the sensitivity configured in the app.

For best results, set ETS2 to the maximum available value (**2520°**) and adjust feel exclusively via the in-app Sensitivity Settings slider.

### 3. Axes (Options → Controls → Axes)

| Axis | Device | Binding |
|---|---|---|
| Steering | vJoy Device | Axis X |
| Acceleration | vJoy Device | Axis Rz |
| Braking | vJoy Device | Axis Z |

### 4. Buttons (Options → Keys and Buttons)

Bind each function to the **secondary** slot using XInput Gamepad 1.

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
