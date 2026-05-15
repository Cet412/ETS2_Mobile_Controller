@echo off
echo =====================================================
echo ETS2 Telemetry Plugin Installer
echo =====================================================
echo.

:: Cari folder instalasi ETS2 dari registry Steam
set "ETS2_PATH="
for /f "tokens=2*" %%a in ('reg query "HKCU\Software\Valve\Steam" /v "SteamPath" 2^>nul') do set "SteamPath=%%b"
if defined SteamPath (
    for /f "tokens=*" %%i in ('dir /b /s "%SteamPath%\steamapps\common\Euro Truck Simulator 2\bin\win_x64" 2^>nul') do set "ETS2_PATH=%%i"
)

:: Jika tidak ditemukan, minta pengguna untuk memasukkan path secara manual
if not defined ETS2_PATH (
    echo ETS2 installation folder was not found automatically.
    set /p "ETS2_PATH=Enter the full path to the 'Euro Truck Simulator 2' folder (example: D:\Games\Euro Truck Simulator 2): "
    set "ETS2_PATH=%ETS2_PATH%\bin\win_x64"
)

:: Tentukan folder plugins
set "PLUGINS_DIR=%ETS2_PATH%\plugins"

:: Buat folder plugins jika belum ada
if not exist "%PLUGINS_DIR%" (
    echo Create a plugins folder in: %PLUGINS_DIR%
    mkdir "%PLUGINS_DIR%"
)

:: Salin file scs-telemetry.dll
echo Copying scs-telemetry.dll ke %PLUGINS_DIR%...
copy /y "%~dp0..\Dependencies\scs-telemetry.dll" "%PLUGINS_DIR%\" >nul

:: Cek apakah proses copy berhasil
if %errorlevel% equ 0 (
    echo.
    echo =====================================================
    echo SUCCESS! The telemetry plugin has been installed.
    echo Please restart ETS2 if it is running.
    echo =====================================================
) else (
    echo.
    echo =====================================================
    echo FAIL! Make sure the ETS2 folder path is correct.
    echo =====================================================
)
echo.
pause