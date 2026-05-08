import socket
import struct
import subprocess
import re
import vgamepad as vg
import truck_telemetry

UDP_IP = "0.0.0.0" 
UDP_PORT = 65432
TELEMETRY_PORT = 65433

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

def main():
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.bind((UDP_IP, UDP_PORT))
    
    print("Membangun Virtual Xbox 360 Controller...")
    gamepad = vg.VX360Gamepad()
    
    gateway_ip = get_android_gateway_ip()
    if gateway_ip:
        print(f"Mendeteksi Android Hotspot di IP Gateway: {gateway_ip}")
    else:
        print("Peringatan: Gateway tidak ditemukan.")

    telemetry_active = False

    try:
        while True: 
            sock.settimeout(2.0)
            handshake_done = False
            print(f"Menunggu koneksi telemetri di Port {UDP_PORT}...")

            while not handshake_done:
                if gateway_ip:
                    sock.sendto(b"ETS2_PC_HERE", (gateway_ip, UDP_PORT))
                
                try:
                    data, addr = sock.recvfrom(1024)
                    if len(data) == 16:
                        print(f"Telemetri terkunci dari Klien: {addr[0]}")
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
                        print("Koneksi Android terputus. Mengulangi Handshake...")
                        break 
                    continue

                # Ekstraksi Input Mentah Android (Momentary State)
                pb_in = (btn_mask >> 0) & 1
                light_in = (btn_mask >> 1) & 1
                hb_in = (btn_mask >> 2) & 1
                wiper_in = (btn_mask >> 3) & 1
                horn_in = (btn_mask >> 4) & 1
                sig_l_in = (btn_mask >> 5) & 1
                sig_r_in = (btn_mask >> 6) & 1
                haz_in = (btn_mask >> 7) & 1
                su_in = (btn_mask >> 8) & 1
                sd_in = (btn_mask >> 9) & 1
                cc_tog_in = (btn_mask >> 10) & 1
                cc_up_in = (btn_mask >> 11) & 1
                cc_dn_in = (btn_mask >> 12) & 1
                la_in = (btn_mask >> 13) & 1
                eng_in = (btn_mask >> 14) & 1

                # Normalisasi Steering (900 Derajat / ±450 Derajat)
                st_val = max(min(st_raw / 450.0, 1.0), -1.0) 
                gamepad.left_joystick_float(x_value_float=st_val, y_value_float=0.0)
                gamepad.right_trigger_float(value_float=gas_raw)
                gamepad.left_trigger_float(value_float=brake_raw)

                # Pemetaan Langsung (Zero Logic Mirroring)
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
                # REVERSE TELEMETRY (State Sync)
                # ==========================================
                if not telemetry_active:
                    try:
                        truck_telemetry.init()
                        telemetry_active = True
                        print("\n[SYSTEM] Telemetri Aktif. Melakukan sinkronisasi Game State ke UI Android.")
                    except Exception:
                        pass 

                if telemetry_active:
                    try:
                        game_data = truck_telemetry.get_data()
                        if game_data:
                            # Mengekstrak status murni dari game
                            telem_eng = 1 if game_data.get('engineEnabled', False) else 0
                            telem_pb = 1 if game_data.get('parkingBrake', False) else 0
                            telem_lb = 1 if game_data.get('lightsBeamLow', False) else 0
                            telem_hb = 1 if game_data.get('lightsBeamHigh', False) else 0
                            telem_wip = 1 if game_data.get('wipers', False) else 0
                            telem_sig_l = 1 if game_data.get('blinkerLeftActive', False) else 0
                            telem_sig_r = 1 if game_data.get('blinkerRightActive', False) else 0
                            telem_cc = 1 if game_data.get('cruiseControlSpeed', 0.0) > 0 else 0
                            telem_park = 1 if game_data.get('lightsParking', False) else 0

                            # Kompresi status menjadi bitmask 32-bit untuk Android
                            telem_mask = (
                                (telem_eng << 0) |
                                (telem_pb << 1) |
                                (telem_lb << 2) |
                                (telem_hb << 3) |
                                (telem_wip << 4) |
                                (telem_sig_l << 5) |
                                (telem_sig_r << 6) |
                                (telem_cc << 7) |
                                (telem_park << 8)
                            )
                            
                            # Transmisi: <Ifff (1 Int, 3 Float cadangan)
                            out_payload = struct.pack('<Ifff', telem_mask, 0.0, 0.0, 0.0)
                            sock.sendto(out_payload, (gateway_ip, TELEMETRY_PORT))
                    except Exception:
                        telemetry_active = False 

                try:
                    data, addr = sock.recvfrom(16)
                except socket.timeout:
                    print("Koneksi terputus. Menunggu Klien...")
                    break 

    except KeyboardInterrupt:
        print("\nSinyal terminasi diterima. Menutup server...")
    finally:
        sock.close()

if __name__ == "__main__":
    main()