import socket
import time
import vgamepad as vg

UDP_IP = "0.0.0.0" 
UDP_PORT = 65432

def main():
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.bind((UDP_IP, UDP_PORT))
    
    print("Membangun Virtual Xbox 360 Controller...")
    gamepad = vg.VX360Gamepad()
    
    # Menambahkan Tracker untuk E (Engine), W (Wiper), HB (High Beam)
    prev_states = {
        'PB': 0, 'L': 0, 'HB': 0, 'S': 0, 'CT': 0, 'LA': 0, 'E': 0, 'W': 0
    }
    
    active_pulses = {}
    active_axis_pulses = {} # Tracker khusus untuk Axis (Analog) Pulses
    PULSE_DURATION = 0.1 

    print(f"Server Aktif. Mendengarkan paket UDP di Port {UDP_PORT}...")

    def trigger_pulse(button):
        gamepad.press_button(button=button)
        active_pulses[button] = time.time() + PULSE_DURATION
        
    def trigger_axis_pulse_up():
        """Menggunakan Joystick Kanan didorong ke atas sebagai tombol pulsa"""
        gamepad.right_joystick_float(x_value_float=0.0, y_value_float=1.0)
        active_axis_pulses['wiper'] = time.time() + PULSE_DURATION

    try:
        while True:
            current_time = time.time()
            
            # Reset tombol digital
            for btn, exp_time in list(active_pulses.items()):
                if current_time >= exp_time:
                    gamepad.release_button(button=btn)
                    del active_pulses[btn]
                    
            # Reset analog virtual
            for axis, exp_time in list(active_axis_pulses.items()):
                if current_time >= exp_time:
                    if axis == 'wiper':
                        gamepad.right_joystick_float(x_value_float=0.0, y_value_float=0.0)
                    del active_axis_pulses[axis]

            data, addr = sock.recvfrom(1024)
            payload = data.decode('utf-8')

            try:
                state = {}
                for part in payload.split('|'):
                    if ':' in part:
                        k, v = part.split(':')
                        state[k] = float(v) if '.' in v else int(v)
            except ValueError:
                continue 

            # ST (Setir): Skala diperbarui ke 360.0 mengikuti revisi Kotlin!
            st_val = state.get('ST', 0.0) / 360.0
            st_val = max(min(st_val, 1.0), -1.0) 
            gamepad.left_joystick_float(x_value_float=st_val, y_value_float=0.0)

            gamepad.right_trigger_float(value_float=state.get('G', 0.0))
            gamepad.left_trigger_float(value_float=state.get('B', 0.0))

            # Momentary Buttons
            if state.get('H', 0) == 1: gamepad.press_button(vg.XUSB_BUTTON.XUSB_GAMEPAD_LEFT_THUMB)
            else: gamepad.release_button(vg.XUSB_BUTTON.XUSB_GAMEPAD_LEFT_THUMB)

            if state.get('SU', 0) == 1: gamepad.press_button(vg.XUSB_BUTTON.XUSB_GAMEPAD_RIGHT_SHOULDER)
            else: gamepad.release_button(vg.XUSB_BUTTON.XUSB_GAMEPAD_RIGHT_SHOULDER)

            if state.get('SD', 0) == 1: gamepad.press_button(vg.XUSB_BUTTON.XUSB_GAMEPAD_LEFT_SHOULDER)
            else: gamepad.release_button(vg.XUSB_BUTTON.XUSB_GAMEPAD_LEFT_SHOULDER)

            if state.get('CU', 0) == 1: gamepad.press_button(vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_UP)
            else: gamepad.release_button(vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_UP)

            if state.get('CD', 0) == 1: gamepad.press_button(vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_DOWN)
            else: gamepad.release_button(vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_DOWN)

            # Toggle Buttons (Edge Detection)
            if state.get('PB', 0) != prev_states['PB']:
                trigger_pulse(vg.XUSB_BUTTON.XUSB_GAMEPAD_B)
                prev_states['PB'] = state.get('PB', 0)

            if state.get('CT', 0) != prev_states['CT']:
                trigger_pulse(vg.XUSB_BUTTON.XUSB_GAMEPAD_Y)
                prev_states['CT'] = state.get('CT', 0)

            if state.get('LA', 0) != prev_states['LA']:
                trigger_pulse(vg.XUSB_BUTTON.XUSB_GAMEPAD_A)
                prev_states['LA'] = state.get('LA', 0)

            if state.get('L', 0) != prev_states['L']:
                trigger_pulse(vg.XUSB_BUTTON.XUSB_GAMEPAD_X)
                prev_states['L'] = state.get('L', 0)

            # HIGH BEAM -> Right Thumb Click (RSB)
            if state.get('HB', 0) != prev_states['HB']:
                trigger_pulse(vg.XUSB_BUTTON.XUSB_GAMEPAD_RIGHT_THUMB)
                prev_states['HB'] = state.get('HB', 0)
                
            # ENGINE START/STOP -> Start Button
            if state.get('E', 0) != prev_states['E']:
                trigger_pulse(vg.XUSB_BUTTON.XUSB_GAMEPAD_START)
                prev_states['E'] = state.get('E', 0)
                
            # WIPER -> Right Joystick Push UP
            if state.get('W', 0) != prev_states['W']:
                trigger_axis_pulse_up()
                prev_states['W'] = state.get('W', 0)

            # Signal Logic
            curr_s = state.get('S', 0)
            if curr_s != prev_states['S']:
                if curr_s == 1: trigger_pulse(vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_LEFT)
                elif curr_s == 2: trigger_pulse(vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_RIGHT)
                elif curr_s == 3: trigger_pulse(vg.XUSB_BUTTON.XUSB_GAMEPAD_BACK)
                
                elif curr_s == 0:
                    if prev_states['S'] == 1: trigger_pulse(vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_LEFT)
                    elif prev_states['S'] == 2: trigger_pulse(vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_RIGHT)
                    elif prev_states['S'] == 3: trigger_pulse(vg.XUSB_BUTTON.XUSB_GAMEPAD_BACK)
                
                prev_states['S'] = curr_s

            gamepad.update()

    except KeyboardInterrupt:
        print("\nSinyal terminasi diterima. Menutup server...")
    finally:
        sock.close()

if __name__ == "__main__":
    main()