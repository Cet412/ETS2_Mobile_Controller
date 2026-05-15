; Script untuk ETS2 Mobile Controller Bridge
[Setup]
AppName=ETS2 Mobile Controller Bridge
AppVersion=1.1.0
DefaultDirName={pf}\ETS2 Mobile Controller
DefaultGroupName=ETS2 Mobile Controller
UninstallDisplayIcon={app}\icon.ico
Compression=lzma2
SolidCompression=yes
OutputDir=installer_output
OutputBaseFilename=ETS2_Controller_Setup
SetupIconFile=icon.ico
PrivilegesRequired=admin

[Files]
; Salin seluruh isi folder Bridge (termasuk Driver, Dependencies, dan exe)
Source: "Bridge\*"; DestDir: "{app}"; Flags: recursesubdirs createallsubdirs; Excludes: "python_bridge"
; Salin icon ke folder app untuk keperluan uninstall
Source: "icon.ico"; DestDir: "{app}"; Flags: ignoreversion

[Run]
; Opsi 1: Instal driver ViGEmBus & vJoy (checkbox di akhir)
Filename: "{app}\Driver\install_driver.bat"; Description: "Install required drivers (ViGEmBus & vJoy)"; Flags: runhidden postinstall skipifsilent

; Opsi 2: Instal plugin telemetri ETS2 (copy scs-telemetry.dll ke folder plugins ETS2)
Filename: "{app}\Driver\install_ets2_plugin.bat"; Description: "Install ETS2 Telemetry Plugin (scs-telemetry.dll)"; Flags: postinstall skipifsilent

[Icons]
Name: "{group}\ETS2 Controller Server"; Filename: "{app}\ETS2_Controller_Server.exe"
Name: "{group}\Uninstall"; Filename: "{uninstallexe}"
Name: "{commondesktop}\ETS2 Controller Server"; Filename: "{app}\ETS2_Controller_ Server.exe"; Tasks: desktopicon

[Tasks]
Name: desktopicon; Description: "Create desktop icon"; GroupDescription: "Additional icons:"

[UninstallDelete]
Type: filesandordirs; Name: "{app}\Driver"