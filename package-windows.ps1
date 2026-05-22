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
# Output: dist\AutoFeeInput-<version>.exe (the installer)
#
# If you don't want to install Inno Setup, change $PackageType to "app-image"
# below — that produces a dist\AutoFeeInput\ folder you can zip and send.

$ErrorActionPreference = 'Stop'

$AppName     = 'AutoFeeInput'
# Single source of truth for the version is pom.xml.
$pomMatch    = Select-String -Path 'pom.xml' -Pattern '<version>(.*?)</version>' | Select-Object -First 1
if (-not $pomMatch) { throw 'Failed to read <version> from pom.xml' }
$AppVersion  = $pomMatch.Matches[0].Groups[1].Value
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

if (Test-Path $OutDir) { Remove-Item -Recurse -Force $OutDir }
New-Item -ItemType Directory -Path $OutDir | Out-Null

# ---- Step 1: app-image (used for both the portable zip and the .exe build) ---
Write-Host '==> Running jpackage (app-image)'
$AppImageDir = "$OutDir\app-image"
New-Item -ItemType Directory -Path $AppImageDir | Out-Null
& jpackage `
    --type app-image `
    --name $AppName `
    --app-version $AppVersion `
    --input $StageDir `
    --main-jar $JarName `
    --main-class $MainClass `
    --module-path "$StageDir\jfx-modules" `
    --add-modules 'javafx.controls,javafx.fxml,javafx.graphics,jdk.crypto.ec,jdk.crypto.cryptoki,jdk.localedata' `
    --dest $AppImageDir `
    --java-options '-Xmx512m'

# ---- Step 2: portable zip ----------------------------------------------------
# Extract-and-run avoids SmartScreen's "unrecognized publisher" install prompt
# (which jpackage's unsigned .exe always triggers). Users unzip and run
# AutoFeeInput.exe directly — far less scary than the installer warning.
Write-Host '==> Packaging portable zip'
$ZipPath = "$OutDir\$AppName-$AppVersion-windows-portable.zip"
Compress-Archive -Path "$AppImageDir\$AppName" -DestinationPath $ZipPath -Force

# ---- Step 3: .exe installer (Inno Setup) ------------------------------------
if ($PackageType -eq 'exe' -or $PackageType -eq 'msi') {
    Write-Host "==> Running jpackage (type=$PackageType)"
    & jpackage `
        --type $PackageType `
        --name $AppName `
        --app-version $AppVersion `
        --app-image "$AppImageDir\$AppName" `
        --dest $OutDir `
        --win-shortcut `
        --win-menu `
        --win-dir-chooser
}

# ---- Step 4: SHA-256 checksums ----------------------------------------------
# Lets users verify the download without trusting the (unsigned) binary itself.
Write-Host '==> Writing SHA-256 checksums'
Get-ChildItem $OutDir -File |
    Where-Object { $_.Extension -in '.exe','.msi','.zip' } |
    ForEach-Object {
        $hash = (Get-FileHash -Algorithm SHA256 -Path $_.FullName).Hash.ToLower()
        "$hash  $($_.Name)" | Out-File -Encoding ascii "$($_.FullName).sha256"
    }

# Drop the staging app-image folder so only release artifacts remain.
Remove-Item -Recurse -Force $AppImageDir

Write-Host ''
Write-Host "==> Built artifacts in $OutDir\"
Get-ChildItem $OutDir | Format-Table Name, Length, LastWriteTime
