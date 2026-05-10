[Setup]
AppName=ETS2 Mobile Controller Bridge
AppVersion=1.0.0
DefaultDirName={autopf}\ETS2 Mobile Controller
DefaultGroupName=ETS2 Mobile Controller
OutputDir=.\Output
OutputBaseFilename=ETS2_Controller_PC_Setup
Compression=lzma
SolidCompression=yes
ArchitecturesInstallIn64BitMode=x64
SetupIconFile=icon.ico

[Files]
Source: "dist\ETS2 Controller Server.exe"; DestDir: "{app}"; Flags: ignoreversion; DestName: "ETS2 Controller Server.exe"

Source: "python_bridge\scs-telemetry.dll"; DestDir: "{code:GetETS2Path}\bin\win_x64\plugins"; Flags: ignoreversion

[Icons]
Name: "{group}\ETS2 Controller Server"; Filename: "{app}\ETS2 Controller Server.exe"
Name: "{commondesktop}\ETS2 Controller Server"; Filename: "{app}\ETS2 Controller Server.exe"; Tasks: desktopicon

[Tasks]
Name: "desktopicon"; Description: "Create a shortcut on the Desktop"; GroupDescription: "Additional shortcuts:"

[Run]
Filename: "netsh"; Parameters: "advfirewall firewall add rule name=""ETS2 Controller Bridge"" dir=in action=allow protocol=UDP localport=65432,65433 program=""{app}\ETS2 Controller Server.exe"""; Flags: runhidden runascurrentuser

[UninstallRun]
Filename: "netsh"; Parameters: "advfirewall firewall delete rule name=""ETS2 Controller Bridge"""; Flags: runhidden runascurrentuser

[Code]
var
  ETS2DirPage: TInputDirWizardPage;

procedure InitializeWizard;
begin
  ETS2DirPage := CreateInputDirPage(wpSelectDir,
    'Euro Truck Simulator 2 Installation Locations', 'Where is the ETS2 game installed?',
    'Select your Euro Truck Simulator 2 root directory (the folder containing bin\win_x64\eurotrucks2.exe). If you have Steam, it is usually located in C:\Program Files (x86)\Steam\steamapps\common\Euro Truck Simulator 2. If you have a different installation method, please select the correct folder.',
    False, 'New Directory');
  ETS2DirPage.Add('');
  ETS2DirPage.Values[0] := 'C:\Program Files (x86)\Steam\steamapps\common\Euro Truck Simulator 2';
end;

function GetETS2Path(Param: String): String;
begin
  Result := ETS2DirPage.Values[0];
end;

function NextButtonClick(CurPageID: Integer): Boolean;
var
  SelectedPath: String;
begin
  Result := True; 
  if CurPageID = ETS2DirPage.ID then
  begin
    SelectedPath := ETS2DirPage.Values[0];

    if not FileExists(SelectedPath + '\bin\win_x64\eurotrucks2.exe') then
    begin
      MsgBox('Invalid directory. Could not find ETS2 executable (bin\win_x64\eurotrucks2.exe).' #13#13 'Please ensure you selected the correct Euro Truck Simulator 2 root installation folder.', mbError, MB_OK);
      Result := False; 
    end;
  end;
end;