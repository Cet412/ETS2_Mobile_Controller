import socket
import time
import struct
import subprocess
import re
import vgamepad as vg
import truck_telemetry

UDP_IP = "0.0.0.0" 
UDP_PORT = 65432
TELEMETRY_PORT = 65433 # Port Receiver di sisi Android

PULSE_DURATION = 0.1 
telemetry_active = False # INTERVENSI: State tracking untuk memory hook

def get_android_gateway_ip():
    try:
        result = subprocess.run(['ipconfig'], stdout=subprocess.PIPE).stdout.decode('utf-8', errors='ignore')
        matches = re.findall(r"Default Gateway.*: ([\d\.]+)", result)
        for ip in matches:
            if ip.startswith("192.") or ip.startswith("10.") or ip.startswith("172."):
                return ip
    except Exception:
        pass
    return None

def main():

    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.bind((UDP_IP, UDP_PORT))
    
    print("Membangun Virtual Xbox 360 Controller...")
    gamepad = vg.VX360Gamepad()
    
    prev_states = {'PB': 0, 'L': 0, 'HB': 0, 'S': 0, 'CT': 0, 'LA': 0, 'E': 0}
    active_pulses = {}
    PULSE_DURATION = 0.1 

    gateway_ip = get_android_gateway_ip()
    if gateway_ip:
        print(f"Mendeteksi Android Hotspot di IP Gateway: {gateway_ip}")
    else:
        print("Peringatan: Gateway tidak ditemukan.")

    def trigger_pulse(button):
        gamepad.press_button(button=button)
        active_pulses[button] = time.time() + PULSE_DURATION

    # INTERVENSI: Master Loop untuk Auto-Recovery
    try:
        while True: 
            sock.settimeout(2.0)
            handshake_done = False
            print(f"Menunggu koneksi telemetri di Port {UDP_PORT}...")

            # Fase 1: Reverse-Beacon Handshake Loop
            while not handshake_done:
                if gateway_ip:
                    sock.sendto(b"ETS2_PC_HERE", (gateway_ip, UDP_PORT))
                
                try:
                    data, addr = sock.recvfrom(1024)
                    if len(data) == 16:
                        print(f"Telemetri terkunci dari Klien: {addr[0]}")
                        handshake_done = True
                        # INTERVENSI: Jangan gunakan None, gunakan timeout 1 detik
                        sock.settimeout(1.0) 
                        break
                except socket.timeout:
                    continue

            # Fase 2: Telemetry Loop
            while True:
                current_time = time.time()
                
                for btn, exp_time in list(active_pulses.items()):
                    if current_time >= exp_time:
                        gamepad.release_button(button=btn)
                        del active_pulses[btn]

                try:
                    st_raw, gas_raw, brake_raw, btn_mask = struct.unpack('<fffI', data)
                except struct.error:
                    try:
                        data, addr = sock.recvfrom(16)
                    except socket.timeout:
                        print("Koneksi Android terputus. Mengulangi Handshake...")
                        break # Memutus Telemetry Loop, memicu Handshake ulang
                    continue

                pb_state = (btn_mask >> 0) & 1
                light_state = (btn_mask >> 1) & 3
                hb_state = (btn_mask >> 3) & 1
                signal_state = (btn_mask >> 4) & 3
                horn_state = (btn_mask >> 6) & 1
                su_state = (btn_mask >> 7) & 1
                sd_state = (btn_mask >> 8) & 1
                cu_state = (btn_mask >> 9) & 1
                ct_state = (btn_mask >> 10) & 1
                cd_state = (btn_mask >> 11) & 1
                la_state = (btn_mask >> 12) & 1
                e_state = (btn_mask >> 13) & 1
                w_state = (btn_mask >> 14) & 1

                st_val = max(min(st_raw / 360.0, 1.0), -1.0) 
                gamepad.left_joystick_float(x_value_float=st_val, y_value_float=0.0)
                gamepad.right_trigger_float(value_float=gas_raw)
                gamepad.left_trigger_float(value_float=brake_raw)

                if horn_state: gamepad.press_button(vg.XUSB_BUTTON.XUSB_GAMEPAD_LEFT_THUMB)
                else: gamepad.release_button(vg.XUSB_BUTTON.XUSB_GAMEPAD_LEFT_THUMB)

                if su_state: gamepad.press_button(vg.XUSB_BUTTON.XUSB_GAMEPAD_RIGHT_SHOULDER)
                else: gamepad.release_button(vg.XUSB_BUTTON.XUSB_GAMEPAD_RIGHT_SHOULDER)

                if sd_state: gamepad.press_button(vg.XUSB_BUTTON.XUSB_GAMEPAD_LEFT_SHOULDER)
                else: gamepad.release_button(vg.XUSB_BUTTON.XUSB_GAMEPAD_LEFT_SHOULDER)

                if cu_state: gamepad.press_button(vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_UP)
                else: gamepad.release_button(vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_UP)

                if cd_state: gamepad.press_button(vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_DOWN)
                else: gamepad.release_button(vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_DOWN)

                if w_state: gamepad.right_joystick_float(x_value_float=0.0, y_value_float=1.0)
                else: gamepad.right_joystick_float(x_value_float=0.0, y_value_float=0.0)

                if pb_state != prev_states['PB']:
                    trigger_pulse(vg.XUSB_BUTTON.XUSB_GAMEPAD_B)
                    prev_states['PB'] = pb_state

                if ct_state != prev_states['CT']:
                    trigger_pulse(vg.XUSB_BUTTON.XUSB_GAMEPAD_Y)
                    prev_states['CT'] = ct_state

                if la_state != prev_states['LA']:
                    trigger_pulse(vg.XUSB_BUTTON.XUSB_GAMEPAD_A)
                    prev_states['LA'] = la_state

                if light_state != prev_states['L']:
                    trigger_pulse(vg.XUSB_BUTTON.XUSB_GAMEPAD_X)
                    prev_states['L'] = light_state

                if hb_state != prev_states['HB']:
                    trigger_pulse(vg.XUSB_BUTTON.XUSB_GAMEPAD_RIGHT_THUMB)
                    prev_states['HB'] = hb_state
                    
                if e_state != prev_states['E']:
                    trigger_pulse(vg.XUSB_BUTTON.XUSB_GAMEPAD_START)
                    prev_states['E'] = e_state

                if signal_state != prev_states['S']:
                    if signal_state == 1: trigger_pulse(vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_LEFT)
                    elif signal_state == 2: trigger_pulse(vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_RIGHT)
                    elif signal_state == 3: trigger_pulse(vg.XUSB_BUTTON.XUSB_GAMEPAD_BACK)
                    elif signal_state == 0:
                        if prev_states['S'] == 1: trigger_pulse(vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_LEFT)
                        elif prev_states['S'] == 2: trigger_pulse(vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_RIGHT)
                        elif prev_states['S'] == 3: trigger_pulse(vg.XUSB_BUTTON.XUSB_GAMEPAD_BACK)
                    prev_states['S'] = signal_state

                gamepad.update()

                # ==========================================
                # FASE 4: EXTRACT & TRANSMIT REVERSE TELEMETRY
                # ==========================================
                global telemetry_active
                if not telemetry_active:
                    try:
                        truck_telemetry.init()
                        telemetry_active = True
                        print("\n[SYSTEM] Shared Memory ETS2 terdeteksi. Transmisi Telemetri Aktif.")
                    except Exception:
                        pass # Abaikan eksekusi jika game belum berjalan

                if telemetry_active:
                    try:
                        game_data = truck_telemetry.get_data()
                        if game_data:
                            speed_ms = float(game_data.get('speed', 0.0))
                            speed_kmh = speed_ms * 3.6
                            rpm = float(game_data.get('engineRpm', 0.0))
                            gear = int(game_data.get('gear', 0))
                            fuel = float(game_data.get('fuel', 0.0))
                            
                            out_payload = struct.pack('<ffif', speed_kmh, rpm, gear, fuel)
                            sock.sendto(out_payload, (gateway_ip, TELEMETRY_PORT))
                    except Exception:
                        telemetry_active = False # Putus sirkuit jika game ditutup mendadak
                # INTERVENSI: Deteksi Timeout
                try:
                    data, addr = sock.recvfrom(16)
                except socket.timeout:
                    print("Koneksi Android terputus. Mengulangi Handshake...")
                    break # Memutus Telemetry Loop, memicu Handshake ulang

    except KeyboardInterrupt:
        print("\nSinyal terminasi diterima. Menutup server...")
    finally:
        sock.close()

if __name__ == "__main__":
    main()