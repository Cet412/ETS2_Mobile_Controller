@echo off
if "%1"=="hide" goto begin
start mshta vbscript:createobject("wscript.shell").run("""%~0"" hide",0)(window.close)&&exit
:begin

set base_dir=%~dp0
pushd "%base_dir%"

echo Installing ViGEmBus Driver...
if exist "ViGEmBus_1.22.0_x64_x86_arm64.exe" (
    start /wait "" "ViGEmBus_1.22.0_x64_x86_arm64.exe" /quiet /norestart
    if %ERRORLEVEL% NEQ 0 echo ERROR: ViGEmBus installation failed.
) else echo File ViGEmBus not found.

echo Installing vJoy Driver...
if exist "vJoySetup.exe" (
    start /wait "" "vJoySetup.exe" /S
    if %ERRORLEVEL% NEQ 0 echo ERROR: vJoy installation failed.
) else echo File vJoySetup.exe not found.

popd
exit /b 0