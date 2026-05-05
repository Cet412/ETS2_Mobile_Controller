import socket
import vgamepad as vg
import time

# 1. Setup Virtual Xbox Controller (Dari Fase 1)
gamepad = vg.VX360Gamepad()

# 2. Setup Server Socket untuk menerima data dari HP via ADB
HOST = '127.0.0.1'
PORT = 65432

print("Menunggu koneksi dari HP...")
with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
    s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)  
    s.bind((HOST, PORT))
    s.listen()
    conn, addr = s.accept() # Program akan berhenti di sini sampai HP terhubung
    print(f"HP TERHUBUNG dari {addr}!")
    
    with conn:
        try:
            while True:
                # ANTI-LAG: Buang semua data lama yang mengantre di memori
                conn.setblocking(False) 
                while True:
                    try:
                        junk = conn.recv(1024) # Buang data lama
                    except BlockingIOError:
                        break # Berhenti membuang kalau sudah kosong
                    except Exception:
                        break
                
                # Kembalikan ke blocking mode untuk baca data terbaru
                conn.setblocking(True) 
                data = conn.recv(1024)
                
                if not data:
                    break
                    
                # Ubah bytes jadi string, lalu pisahkan pakai koma (CSV)
                values = data.decode('utf-8').split(',')
                
                # Pastikan data yang masuk lengkap (11 parameter)
                if len(values) == 13:
                    # 0:Gas, 1:Brake, 2:Steer (Float)
                    gas = float(values[0])
                    brake = float(values[1])
                    steer = float(values[2])
                    
                    # 3 s/d 10: Tombol Boolean (0 atau 1)
                    hazard = bool(int(values[3]))
                    wiper = bool(int(values[4]))
                    turnL = bool(int(values[5]))
                    turnR = bool(int(values[6]))
                    p_brake = bool(int(values[7]))
                    cruise = bool(int(values[8]))
                    lane = bool(int(values[9]))
                    pause = bool(int(values[10]))
                    cruise_up = bool(int(values[11]))
                    cruise_down = bool(int(values[12]))

                    # --- KIRIM KE VIRTUAL GAMEPAD ---
                    gamepad.left_joystick_float(x_value_float=steer, y_value_float=0.0)
                    gamepad.right_trigger_float(value_float=gas)
                    gamepad.left_trigger_float(value_float=brake)
                    
                    # Handle Tombol (Mapping sementara, nanti bisa disesuaikan)
                    if hazard: gamepad.press_button(vg.XUSB_BUTTON.XUSB_GAMEPAD_Y)
                    else: gamepad.release_button(vg.XUSB_BUTTON.XUSB_GAMEPAD_Y)
                    
                    if wiper: gamepad.press_button(vg.XUSB_BUTTON.XUSB_GAMEPAD_X)
                    else: gamepad.release_button(vg.XUSB_BUTTON.XUSB_GAMEPAD_X)
                    
                    if turnL: gamepad.press_button(vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_LEFT)
                    else: gamepad.release_button(vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_LEFT)
                    
                    if turnR: gamepad.press_button(vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_RIGHT)
                    else: gamepad.release_button(vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_RIGHT)
                    
                    if p_brake: gamepad.press_button(vg.XUSB_BUTTON.XUSB_GAMEPAD_A)
                    else: gamepad.release_button(vg.XUSB_BUTTON.XUSB_GAMEPAD_A)
                    
                    if cruise: gamepad.press_button(vg.XUSB_BUTTON.XUSB_GAMEPAD_B)
                    else: gamepad.release_button(vg.XUSB_BUTTON.XUSB_GAMEPAD_B)

                    if lane: gamepad.press_button(vg.XUSB_BUTTON.XUSB_GAMEPAD_LEFT_SHOULDER)
                    else: gamepad.release_button(vg.XUSB_BUTTON.XUSB_GAMEPAD_LEFT_SHOULDER)

                    if pause: gamepad.press_button(vg.XUSB_BUTTON.XUSB_GAMEPAD_START)
                    else: gamepad.release_button(vg.XUSB_BUTTON.XUSB_GAMEPAD_START)

                    if cruise_up: gamepad.press_button(vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_UP)
                    else: gamepad.release_button(vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_UP)

                    if cruise_down: gamepad.press_button(vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_DOWN)
                    else: gamepad.release_button(vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_DOWN)

                    # Update state gamepad
                    gamepad.update()

        except Exception as e:
            print(f"Error: {e}")
        finally:
            gamepad.reset()
            gamepad.update()
            print("Koneksi terputus.")