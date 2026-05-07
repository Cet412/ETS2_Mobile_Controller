import socket
import time
import vgamepad as vg
import struct

UDP_IP = "0.0.0.0" 
UDP_PORT = 65432

def main():
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.bind((UDP_IP, UDP_PORT))
    
    print("Membangun Virtual Xbox 360 Controller...")
    gamepad = vg.VX360Gamepad()
    
    # Menambahkan Tracker untuk E (Engine), W (Wiper), HB (High Beam)
    prev_states = {
        'PB': 0, 'L': 0, 'HB': 0, 'S': 0, 'CT': 0, 'LA': 0, 'E': 0
    }
    
    active_pulses = {}
    PULSE_DURATION = 0.1 

    print(f"Server Aktif. Mendengarkan paket UDP di Port {UDP_PORT}...")

    def trigger_pulse(button):
        gamepad.press_button(button=button)
        active_pulses[button] = time.time() + PULSE_DURATION

    try:
        while True:
            current_time = time.time()
            
            # Reset tombol digital
            for btn, exp_time in list(active_pulses.items()):
                if current_time >= exp_time:
                    gamepad.release_button(button=btn)
                    del active_pulses[btn]

            data, addr = sock.recvfrom(16)
            if len(data) != 16:
                continue

            # Unpack struktur biner Little-Endian: 3 Float, 1 Unsigned Int (<fffI)
            try:
                st_raw, gas_raw, brake_raw, btn_mask = struct.unpack('<fffI', data)
            except struct.error:
                continue

            # Ekstraksi Bitmask
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

            # ==========================================
            # A. PEMETAAN AXIS ANALOG
            # ==========================================
            st_val = max(min(st_raw / 360.0, 1.0), -1.0) 
            gamepad.left_joystick_float(x_value_float=st_val, y_value_float=0.0)
            gamepad.right_trigger_float(value_float=gas_raw)
            gamepad.left_trigger_float(value_float=brake_raw)

            # ==========================================
            # B. PEMETAAN TOMBOL MOMENTARY (Hold)
            # ==========================================
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

            # ==========================================
            # C. PEMETAAN TOMBOL TOGGLE (Edge Detection)
            # ==========================================
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

    except KeyboardInterrupt:
        print("\nSinyal terminasi diterima. Menutup server...")
    finally:
        sock.close()

if __name__ == "__main__":
    main()