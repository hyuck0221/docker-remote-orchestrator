; DRO (Docker Remote Orchestrator) Inno Setup installer script
; Produces a Windows .exe installer with:
;   - Desktop shortcut checkbox on the "Additional Tasks" page
;   - "Launch DRO" checkbox on the final "Finished" page
;
; Expected environment variables (set by CI):
;   APP_VERSION     - app version (e.g. "1.2.3")
;   APP_IMAGE_DIR   - absolute path to jpackage createDistributable output
;                     (folder containing DRO.exe and runtime)
;   OUTPUT_DIR      - absolute path where the generated installer .exe will be placed

#define MyAppName "DRO"
#define MyAppPublisher "DRO"
#define MyAppExeName "DRO.exe"

#define MyAppVersion GetEnv("APP_VERSION")
#if MyAppVersion == ""
  #define MyAppVersion "1.0.0"
#endif

#define AppImageDir GetEnv("APP_IMAGE_DIR")
#define OutputDirPath GetEnv("OUTPUT_DIR")

[Setup]
; AppId is fixed so Inno Setup can detect and upgrade prior installs across versions.
AppId={{E4A5F6C7-8B9D-4E2A-B1C3-D5E6F7A8B9C0}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppVerName={#MyAppName} {#MyAppVersion}
AppPublisher={#MyAppPublisher}
DefaultDirName={autopf}\{#MyAppName}
DisableProgramGroupPage=yes
OutputDir={#OutputDirPath}
OutputBaseFilename=DRO-{#MyAppVersion}-windows-x64
Compression=lzma
SolidCompression=yes
WizardStyle=modern
PrivilegesRequired=lowest
PrivilegesRequiredOverridesAllowed=dialog
ArchitecturesInstallIn64BitMode=x64compatible
UninstallDisplayIcon={app}\{#MyAppExeName}
CloseApplications=yes
RestartApplications=yes

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"
Name: "korean"; MessagesFile: "compiler:Languages\Korean.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"

[Files]
Source: "{#AppImageDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{autoprograms}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; Tasks: desktopicon

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "{cm:LaunchProgram,{#StringChange(MyAppName, '&', '&&')}}"; Flags: nowait postinstall
