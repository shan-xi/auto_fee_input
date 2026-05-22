# Auto Fee Input

Download the latest build from the
[**Releases page**](https://github.com/shan-xi/auto_fee_input/releases/latest).

JavaFX desktop app that drives the `ap.ece.moe.edu.tw` 收費明細 lookup for
every row of an uploaded CSV / XLS / XLSX file and writes the 學費 amount
(formatted per 半日班 / 全日班 columns) into a column of your choosing.

Captchas are solved automatically via OCR.space when an API key is
configured; otherwise the popup lets you type the code by hand.

---

## Download

Grab the latest release from the
[**Releases page**](../../releases/latest).

| Platform | File | Notes |
|---|---|---|
| Windows 10/11 | `AutoFeeInput-*-windows-portable.zip` | **Recommended.** Extract, run `AutoFeeInput.exe`. No installer prompt. |
| Windows 10/11 | `AutoFeeInput-*.exe` | Installer with Start Menu + Desktop shortcut. SmartScreen will warn — see below. |
| macOS (Apple Silicon) | `AutoFeeInput-*.dmg` | Drag to Applications. Ad-hoc signed; Gatekeeper still asks once — see below. |

Every artifact ships with a matching `.sha256` checksum file. Verify
with `shasum -a 256 -c …sha256` (macOS / Linux) or `Get-FileHash` (Windows).

---

## Install — Windows

The binaries are **not signed with a commercial code-signing certificate**
(those cost ~$300/year and I'm releasing this for free). Windows
SmartScreen will warn about the unsigned installer.

### Option A — Portable zip (no warnings, recommended)

1. Download the `*-windows-portable.zip` from the latest release.
2. Right-click the zip → **Properties** → tick **Unblock** → OK.
   (This clears the "Mark of the Web" flag the browser added.)
3. Extract anywhere — e.g. `C:\Tools\AutoFeeInput`.
4. Double-click `AutoFeeInput.exe`.

### Option B — `.exe` installer

1. Download the `.exe` installer from the latest release.
2. Double-click. Windows shows **"Windows protected your PC"**.
3. Click **More info**, then **Run anyway**.
4. Follow the installer — default install path is
   `%LOCALAPPDATA%\AutoFeeInput` (per-user, no admin needed). It creates a
   Start Menu entry and a Desktop shortcut.

The installer never writes outside its install folder.
Your Desktop / Documents / personal files are untouched.

---

## Install — macOS

The `.dmg` is **ad-hoc codesigned** (free, no Apple Developer ID). This
stops the "AutoFeeInput is damaged and can't be opened" error you'd
otherwise hit on Apple Silicon, but Gatekeeper still gates the first
launch because the app isn't notarized.

1. Open the `.dmg` and drag **AutoFeeInput** into **Applications**.
2. First launch must bypass Gatekeeper. Pick one:

   - **Easiest (per launch):** Right-click `AutoFeeInput` in
     `/Applications` → **Open** → click **Open** in the dialog.
     macOS remembers the choice for that copy of the app.

   - **One-shot (kills the quarantine flag for good):**
     ```bash
     sudo xattr -dr com.apple.quarantine /Applications/AutoFeeInput.app
     ```

3. After the first allow, normal double-click works.

If you're on **Intel Mac**: the published `.dmg` is Apple Silicon only
right now. Build from source (see below) with an x86_64 JDK 17.

---

## Usage

1. **Upload File** — pick a `.csv` / `.xls` / `.xlsx`. The sheet appears
   in section 2 with a display-only `#` row-number column.
2. In section 1, pick:
   - **Query column** — values sent as `txtKeyNameS`.
   - **Fee output column** — where the result string is written.
3. **Start**. The app runs the 6-step `ap.ece.moe.edu.tw` flow for each
   row. When a captcha is required, a popup opens:
   - OCR fills the box and auto-submits.
   - If OCR returns empty, the popup auto-refreshes the captcha image
     (up to 5 retries) before falling back to manual entry.
   - **Refresh** loads a new captcha. **Cancel Row** skips the row.
4. **Stop / Resume** pauses and resumes the row sequence.
5. **Download** is available at any time — even mid-run. The exported
   file reflects whatever fees have been filled in so far.

### Fee output format

The parser scans the first 收費明細 table for the row labelled
`上學期計 6 個月`, `全學期總收費`, or `總計`, then reads the 半日班 /
全日班 columns (colspan-aware). Output is one of:

| Half-day | Full-day | Output |
|---|---|---|
| `94,626` | `94,626` | `學費 半日班94,626/全日班94,626` |
| (empty)  | `94,626` | `學費 全日班94,626` |
| `50,000` | (empty)  | `學費 半日班50,000` |

Failed rows are tagged `ERR:<reason>` so they stand out in the column.

### Copy from the sheet

Click cells in section 2 and press **Ctrl+C** (Windows) / **Cmd+C**
(macOS), or right-click → **Copy**. Selection is copied as TSV — paste
into Excel / Google Sheets and the columns line up.

### Update notifications

A few seconds after launch (and every 6 hours while the app stays open)
the app checks `api.github.com` for the latest release. When the tag is
newer than your running version a dialog offers:

- **Open Release Page** — launches your browser at the GitHub releases
  page so you can grab the new installer.
- **Remind Me Later** — dismiss; you'll see it again on the next check.
- **Skip This Version** — saved to `app.properties` in the per-user
  config dir; no more prompts for that exact tag.

The running version is shown in the window title bar and in the
Settings dialog header.

---

## Settings (OCR)

Click **Settings** to open the dialog:

- **Enable OCR auto-fill** — uncheck to type every captcha by hand.
- **API Keys #1 – #10** — up to ten OCR.space keys.
  - Key #1 is the active key.
  - On any rate-limit error the service rotates to the next key and
    logs the event, cycling back to #1 once all are exhausted.
  - Leave blank to fall back to the public demo key (~20 req/hr).

Keys are stored at:

| OS | Path |
|---|---|
| macOS   | `~/Library/Application Support/AutoFeeInput/ocr.properties` |
| Windows | `%APPDATA%\AutoFeeInput\ocr.properties` |
| Linux   | `$XDG_CONFIG_HOME/auto-fee-input/ocr.properties` |

You can also set `OCR_API_KEY` / `OCR_ENDPOINT_URL` / `OCR_ENABLED`
environment variables — these override the settings file.

Get a free OCR.space key at <https://ocr.space/ocrapi/freekey>
(25,000 requests/month).

---

## Build from source

### Requirements
- JDK 11+ to run (JDK 17 needed to *build* the installer via jpackage)
- Maven 3.6+
- Network access to `https://ap.ece.moe.edu.tw`

### Run in dev
```bash
mvn clean javafx:run
```

### Fat jar
```bash
mvn clean package
java -jar target/auto-fee-input-*.jar
```

### Native installers
```bash
./package-macos.sh dmg        # macOS: dist/AutoFeeInput.app + .dmg (ad-hoc signed)
.\package-windows.ps1         # Windows: dist\*.exe + portable zip
```

Both scripts write SHA-256 checksums alongside each artifact.

A push of a `v*` tag triggers `.github/workflows/package-*.yml`, which
builds release artifacts and attaches them (plus checksums) to a GitHub
Release automatically.

---

## Project layout

```
pom.xml
src/main/java/com/btse/autofeeinput/
  Main.java                   - JavaFX entry; sets window title with version
  Launcher.java               - fat-jar trampoline
  AppVersion.java             - reads META-INF/auto-fee-input-version.properties
  model/SheetData.java        - observable headers + rows
  service/
    ExcelService.java         - csv/xls/xlsx read, xlsx write
    ApiClient.java            - 6-step API flow + 半日班/全日班 fee parser
    CaptchaImageProcessor.java - PNG preprocess for OCR
    OcrConfig.java            - 1–10 API keys + persistence
    OcrService.java           - OCR.space client + key rotation
    ProcessingService.java    - per-row driver, stop/pause/resume
    CaptchaUi.java            - popup contract used by ProcessingService
    UpdateChecker.java        - polls GitHub releases for newer tag
    UpdatePrefs.java          - persists "skip this version" choice
  controller/
    MainController.java       - main window + update-check scheduler
    CaptchaController.java    - captcha popup (OCR + auto-refresh)
    CaptchaSession.java       - FX-thread bridge for popup ↔ worker
    SettingsController.java   - settings dialog
src/main/resources/
  META-INF/auto-fee-input-version.properties  - filtered by Maven at build
  fxml/{main,captcha,settings}.fxml
  styles/app.css
  logback.xml
```

---

## Notes

- A fresh `ApiClient` is created per row, so each row owns its own
  `ASP.NET_SessionId` / `TS01c66436` cookies.
- The Windows `.exe` installer uses jpackage + Inno Setup. It installs
  to `%LOCALAPPDATA%\AutoFeeInput` by default and uninstalls cleanly
  from "Apps & features".
- The macOS `.app` ships its own JDK runtime under
  `Contents/runtime/`, so end users don't need Java installed.