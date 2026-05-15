import socket
import struct
import subprocess
import re
import pyvjoy
import vgamepad as vg
import truck_telemetry

UDP_PORT = 45432
TELEMETRY_PORT = 45433

# vJoy axis range: 0x1 – 0x8000 (1 – 32768)
# Center = 0x4000 (16384)
VJOY_AXIS_MIN = 0x1
VJOY_AXIS_MAX = 0x8000
VJOY_AXIS_CENTER = 0x4000

def steering_to_vjoy(normalized_steer):
    normalized = max(min(normalized_steer, 1.0), -1.0)
    vjoy_val = int((normalized + 1.0) / 2.0 * (VJOY_AXIS_MAX - VJOY_AXIS_MIN) + VJOY_AXIS_MIN)
    return vjoy_val

def get_android_gateway_ip():
    try:
        result = subprocess.run(['route', 'print', '-4'], stdout=subprocess.PIPE).stdout.decode('utf-8', errors='ignore')
        matches = re.findall(r"0\.0\.0\.0\s+0\.0\.0\.0\s+([\d\.]+)", result)
        for ip in matches:
            if ip.startswith("192.") or ip.startswith("10.") or ip.startswith("172."):
                return ip
    except Exception:
        pass
    return None

def get_dynamic_bind_ip():
    try:
        # Membedah tabel rute Windows untuk mendeteksi IP lokal yang aktif berkomunikasi
        result = subprocess.run(['route', 'print', '-4'], stdout=subprocess.PIPE).stdout.decode('utf-8', errors='ignore')
        # Regex menangkap Kolom 3 (Gateway) dan Kolom 4 (Interface IP PC)
        match = re.search(r"0\.0\.0\.0\s+0\.0\.0\.0\s+([\d\.]+)\s+([\d\.]+)", result)
        if match:
            local_ip = match.group(2)
            # Validasi subnet privat
            if local_ip.startswith(("192.", "10.", "172.")):
                return local_ip
        
        # Failsafe: Paksa soket membaca adapter utama yang memiliki rute terluar
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        local_ip = s.getsockname()[0]
        s.close()
        return local_ip
    except Exception:
        # Fallback terakhir ke resolusi hostname bawaan
        return socket.gethostbyname(socket.gethostname())
    
def main():
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    dynamic_ip = get_dynamic_bind_ip()
    try:
        sock.bind((dynamic_ip, UDP_PORT))
        print(f"[NETWORK] Server successfully bound to interface: {dynamic_ip}:{UDP_PORT}")
    except PermissionError:
        print(f"[FATAL] Windows blocked the port {UDP_PORT} on IP {dynamic_ip}. Execute terminal as Administrator.")
        return

    # ==========================================
    # INIT vJoy (Steering Axis Absolut)
    # ==========================================
    print("Building a Virtual Joystick (vJoy Device 1)...")
    try:
        vjoy = pyvjoy.VJoyDevice(1)
        vjoy.reset()
        # Set ke center saat init
        vjoy.set_axis(pyvjoy.HID_USAGE_X, VJOY_AXIS_CENTER)
        print("[OK] vJoy Device 1 successfully initialized.")
    except Exception as e:
        print(f"[ERROR] Failed to initialize vJoy: {e}")
        print("Please ensure the vJoy driver is installed and Device 1 is configured.")
        return

    # ==========================================
    # INIT vgamepad (Tombol Dashboard)
    # ==========================================
    print("Building Virtual Xbox 360 Controller (for dashboard buttons)...")
    gamepad = vg.VX360Gamepad()

    gateway_ip = get_android_gateway_ip()
    if gateway_ip:
        print(f"Detecting Android Hotspot at Gateway IP: {gateway_ip}")
    else:
        print("Warning: Gateway not found.")

    telemetry_active = False

    try:
        while True:
            sock.settimeout(2.0)
            handshake_done = False
            print(f"\nWaiting for connection on Port {UDP_PORT}...")

            # Reset posisi setir ke center saat menunggu koneksi
            vjoy.set_axis(pyvjoy.HID_USAGE_X, VJOY_AXIS_CENTER)

            while not handshake_done:
                if gateway_ip:
                    sock.sendto(b"ETS2_PC_HERE", (gateway_ip, UDP_PORT))
                try:
                    data, addr = sock.recvfrom(1024)
                    if len(data) == 16:
                        print(f"Client connected: {addr[0]}")
                        handshake_done = True
                        sock.settimeout(1.0)
                        break
                except socket.timeout:
                    continue

            # ==========================================
            # MASTER LOOP: I/O & TELEMETRY
            # ==========================================
            while True:
                try:
                    st_raw, gas_raw, brake_raw, btn_mask = struct.unpack('<fffI', data)
                except struct.error:
                    try:
                        data, addr = sock.recvfrom(16)
                    except socket.timeout:
                        print("Android connection lost. Repeating Handshake...")
                        break
                    continue

                # ==========================================
                # STEERING — vJoy Absolute Axis (HID_USAGE_X)
                # Resolusi penuh 32768 step, absolute position
                # ==========================================
                vjoy_steer = steering_to_vjoy(st_raw)
                vjoy.set_axis(pyvjoy.HID_USAGE_X, vjoy_steer)

                # ==========================================
                # GAS & BRAKE — vJoy Absolute Axis
                # HID_USAGE_RZ = Gas (throttle)
                # HID_USAGE_Z  = Brake
                # ==========================================
                gas_vjoy = int(gas_raw * VJOY_AXIS_MAX)
                brake_vjoy = int(brake_raw * VJOY_AXIS_MAX)
                vjoy.set_axis(pyvjoy.HID_USAGE_RZ, gas_vjoy)
                vjoy.set_axis(pyvjoy.HID_USAGE_Z, brake_vjoy)

                # ==========================================
                # EKSTRAKSI TOMBOL DASHBOARD
                # ==========================================
                pb_in      = (btn_mask >> 0) & 1
                light_in   = (btn_mask >> 1) & 1
                hb_in      = (btn_mask >> 2) & 1
                wiper_in   = (btn_mask >> 3) & 1
                horn_in    = (btn_mask >> 4) & 1
                sig_l_in   = (btn_mask >> 5) & 1
                sig_r_in   = (btn_mask >> 6) & 1
                haz_in     = (btn_mask >> 7) & 1
                su_in      = (btn_mask >> 8) & 1
                sd_in      = (btn_mask >> 9) & 1
                cc_tog_in  = (btn_mask >> 10) & 1
                cc_up_in   = (btn_mask >> 11) & 1
                cc_dn_in   = (btn_mask >> 12) & 1
                la_in      = (btn_mask >> 13) & 1
                eng_in     = (btn_mask >> 14) & 1

                # ==========================================
                # PEMETAAN TOMBOL — vgamepad (XInput)
                # Tombol dashboard tetap pakai XInput
                # ==========================================
                if eng_in: gamepad.press_button(vg.XUSB_BUTTON.XUSB_GAMEPAD_START)
                else: gamepad.release_button(vg.XUSB_BUTTON.XUSB_GAMEPAD_START)

                if pb_in: gamepad.press_button(vg.XUSB_BUTTON.XUSB_GAMEPAD_B)
                else: gamepad.release_button(vg.XUSB_BUTTON.XUSB_GAMEPAD_B)

                if light_in: gamepad.press_button(vg.XUSB_BUTTON.XUSB_GAMEPAD_X)
                else: gamepad.release_button(vg.XUSB_BUTTON.XUSB_GAMEPAD_X)

                if hb_in: gamepad.press_button(vg.XUSB_BUTTON.XUSB_GAMEPAD_RIGHT_THUMB)
                else: gamepad.release_button(vg.XUSB_BUTTON.XUSB_GAMEPAD_RIGHT_THUMB)

                if sig_l_in: gamepad.press_button(vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_LEFT)
                else: gamepad.release_button(vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_LEFT)

                if sig_r_in: gamepad.press_button(vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_RIGHT)
                else: gamepad.release_button(vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_RIGHT)

                if haz_in: gamepad.press_button(vg.XUSB_BUTTON.XUSB_GAMEPAD_BACK)
                else: gamepad.release_button(vg.XUSB_BUTTON.XUSB_GAMEPAD_BACK)

                if horn_in: gamepad.press_button(vg.XUSB_BUTTON.XUSB_GAMEPAD_LEFT_THUMB)
                else: gamepad.release_button(vg.XUSB_BUTTON.XUSB_GAMEPAD_LEFT_THUMB)

                if su_in: gamepad.press_button(vg.XUSB_BUTTON.XUSB_GAMEPAD_RIGHT_SHOULDER)
                else: gamepad.release_button(vg.XUSB_BUTTON.XUSB_GAMEPAD_RIGHT_SHOULDER)

                if sd_in: gamepad.press_button(vg.XUSB_BUTTON.XUSB_GAMEPAD_LEFT_SHOULDER)
                else: gamepad.release_button(vg.XUSB_BUTTON.XUSB_GAMEPAD_LEFT_SHOULDER)

                if cc_tog_in: gamepad.press_button(vg.XUSB_BUTTON.XUSB_GAMEPAD_Y)
                else: gamepad.release_button(vg.XUSB_BUTTON.XUSB_GAMEPAD_Y)

                if cc_up_in: gamepad.press_button(vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_UP)
                else: gamepad.release_button(vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_UP)

                if cc_dn_in: gamepad.press_button(vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_DOWN)
                else: gamepad.release_button(vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_DOWN)

                if la_in: gamepad.press_button(vg.XUSB_BUTTON.XUSB_GAMEPAD_A)
                else: gamepad.release_button(vg.XUSB_BUTTON.XUSB_GAMEPAD_A)

                if wiper_in: gamepad.right_joystick_float(x_value_float=0.0, y_value_float=1.0)
                else: gamepad.right_joystick_float(x_value_float=0.0, y_value_float=0.0)

                gamepad.update()

                # ==========================================
                # REVERSE TELEMETRY (State Sync ETS2 → Android)
                # ==========================================
                if not telemetry_active:
                    try:
                        truck_telemetry.init()
                        telemetry_active = True
                        print("\n[SYSTEM] Telemetry Active. Synchronizing Game State with Android.")
                    except Exception:
                        pass

                if telemetry_active:
                    try:
                        game_data = truck_telemetry.get_data()
                        if game_data:
                            telem_eng   = 1 if game_data.get('engineEnabled', False) else 0
                            telem_pb    = 1 if game_data.get('parkingBrake', False) else 0
                            telem_lb    = 1 if game_data.get('lightsBeamLow', False) else 0
                            telem_hb    = 1 if game_data.get('lightsBeamHigh', False) else 0
                            telem_wip   = 1 if game_data.get('wipers', False) else 0
                            telem_sig_l = 1 if game_data.get('blinkerLeftActive', False) else 0
                            telem_sig_r = 1 if game_data.get('blinkerRightActive', False) else 0
                            telem_cc    = 1 if game_data.get('cruiseControlSpeed', 0.0) > 0 else 0
                            telem_park  = 1 if game_data.get('lightsParking', False) else 0

                            telem_mask = (
                                (telem_eng   << 0) |
                                (telem_pb    << 1) |
                                (telem_lb    << 2) |
                                (telem_hb    << 3) |
                                (telem_wip   << 4) |
                                (telem_sig_l << 5) |
                                (telem_sig_r << 6) |
                                (telem_cc    << 7) |
                                (telem_park  << 8)
                            )

                            # Hanya inversi arah sesuai konvensi SCS, biarkan tetap dinormalisasi (-1.0 s/d 1.0)
                            steering_game_norm = game_data.get('gameSteer', 0.0)
                            steering_game_norm_inv = float(steering_game_norm) * -1.0

                            out_payload = struct.pack('<Ifff', telem_mask, steering_game_norm_inv, 0.0, 0.0)
                            sock.sendto(out_payload, (addr[0], TELEMETRY_PORT))
                    except Exception:
                        print("[ERROR] Failed to send telemetry data.")
                        telemetry_active = False

                try:
                    data, addr = sock.recvfrom(16)
                except socket.timeout:
                    print("Connection lost. Waiting for Client...")
                    break

    except KeyboardInterrupt:
        print("\nTermination signal received. Closing server...")
    finally:
        vjoy.reset()
        sock.close()

if __name__ == "__main__":
    main()