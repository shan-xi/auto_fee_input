# Build a Windows .exe installer for Auto Fee Input.
#
# Run on a Windows machine with:
#   - JDK 17+ on PATH (e.g. Microsoft OpenJDK or Eclipse Temurin)
#   - Maven 3.6+ on PATH
#   - Inno Setup 6+ installed (only needed for --type exe)
#       https://jrsoftware.org/isinfo.php
#
# Open PowerShell in this folder and run:
#   .\package-windows.ps1
#
# Output: dist\AutoFeeInput-1.0.0.exe (the installer)
#
# If you don't want to install Inno Setup, change $PackageType to "app-image"
# below — that produces a dist\AutoFeeInput\ folder you can zip and send.

$ErrorActionPreference = 'Stop'

$AppName     = 'AutoFeeInput'
$AppVersion  = '1.0.0'
$JarName     = "auto-fee-input-$AppVersion.jar"
$MainClass   = 'com.btse.autofeeinput.Launcher'
$OutDir      = 'dist'
$PackageType = 'exe'   # 'exe' (needs Inno Setup), 'msi' (needs WiX), or 'app-image'

Write-Host '==> Java:'
& java -version

Write-Host '==> jpackage:'
& jpackage --version

Write-Host '==> Building shaded jar'
& mvn -q clean package
if ($LASTEXITCODE -ne 0) { throw 'Maven build failed' }

if (-not (Test-Path "target\$JarName")) {
    throw "Expected target\$JarName but it isn't there. Check 'mvn package' output."
}

# Stage only the artifacts jpackage needs: the fat jar + JavaFX module jars.
$StageDir = 'target\jpackage-input'
if (Test-Path $StageDir) { Remove-Item -Recurse -Force $StageDir }
New-Item -ItemType Directory -Path "$StageDir\jfx-modules" | Out-Null
Copy-Item "target\$JarName" $StageDir

Write-Host '==> Collecting JavaFX module jars'
& mvn -q dependency:copy-dependencies `
    '-DincludeGroupIds=org.openjfx' `
    "-DoutputDirectory=$StageDir\jfx-modules"

# Strip empty platform-neutral javafx jars; keep only the *-win classifier.
Get-ChildItem "$StageDir\jfx-modules" -Filter 'javafx-*.jar' |
    Where-Object { $_.Name -notmatch '-win\.jar$' -and $_.Length -lt 1024 } |
    Remove-Item

Write-Host "==> Running jpackage (type=$PackageType)"
if (Test-Path $OutDir) { Remove-Item -Recurse -Force $OutDir }
New-Item -ItemType Directory -Path $OutDir | Out-Null

$jpackageArgs = @(
    '--type',        $PackageType,
    '--name',        $AppName,
    '--app-version', $AppVersion,
    '--input',       $StageDir,
    '--main-jar',    $JarName,
    '--main-class',  $MainClass,
    '--module-path', "$StageDir\jfx-modules",
    '--add-modules', 'javafx.controls,javafx.fxml,javafx.graphics,jdk.crypto.ec,jdk.crypto.cryptoki,jdk.localedata',
    '--dest',        $OutDir,
    '--java-options','-Xmx512m'
)

if ($PackageType -eq 'exe' -or $PackageType -eq 'msi') {
    # Add Start Menu entry + Desktop shortcut for the installed app.
    $jpackageArgs += @('--win-shortcut', '--win-menu', '--win-dir-chooser')
}

& jpackage @jpackageArgs

Write-Host ''
Write-Host "==> Built artifacts in $OutDir\"
Get-ChildItem $OutDir | Format-Table Name, Length, LastWriteTime
