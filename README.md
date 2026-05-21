# Auto Fee Input

JavaFX desktop app that ingests a CSV/XLS/XLSX file, runs the
`ap.ece.moe.edu.tw` 收費明細 lookup for each row, and writes the first 學費
amount into a chosen column of a processed copy of the file.

## Requirements

- JDK 11+ (project targets Java 11)
- Maven 3.6+
- Network access to `https://ap.ece.moe.edu.tw`

Your shell currently has Maven defaulting to Oracle JDK 1.8.
Before building, point `JAVA_HOME` at a JDK 11 install — for example:

```bash
export JAVA_HOME=/Users/spin.liao/Library/Java/JavaVirtualMachines/ms-11.0.28/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
mvn -version   # confirm Java 11
```

## Run (development)

```bash
mvn clean javafx:run
```

## Build a runnable jar

```bash
mvn clean package
java -jar target/auto-fee-input-*.jar
```

## Usage

1. Click **Upload File** and pick a `.csv` / `.xls` / `.xlsx`.
2. The sheet content appears in section 2.
3. In section 1, pick:
   - **Query column** — the column whose values are sent as `txtKeyNameS`.
   - **Fee output column** — where the extracted 學費 amount will be written.
4. Click **Start**. For every row the app runs steps 1–6 of the API flow.
5. When step 5 downloads the captcha, a popup shows the image — type the
   code, click **Send**. The row continues automatically.
6. After all rows finish, a `*_processed.xlsx` file is written next to the
   source file. Click **Download** to copy it elsewhere.

### Stop / Resume

- **Stop** interrupts processing immediately. If a captcha popup is open it
  is force-cancelled. The current row's fee cell is marked `ERR:cancelled`.
- **Resume** restarts from the row that was in flight when stopped.

## Project layout

```
pom.xml
src/main/java/com/btse/autofeeinput/
  Main.java                 - JavaFX entry
  Launcher.java             - fat-jar trampoline
  model/SheetData.java      - in-memory headers + observable rows
  service/
    ExcelService.java       - csv/xls/xlsx read, xlsx write
    ApiClient.java          - 6-step ap.ece.moe.edu.tw flow + fee parser
    ProcessingService.java  - row driver with stop/pause/resume
  controller/
    MainController.java     - UI wiring
    CaptchaController.java  - captcha popup
src/main/resources/
  fxml/main.fxml
  fxml/captcha.fxml
  styles/app.css
  logback.xml
```

## Notes

- A fresh `ApiClient` is created per row, so each row owns its own
  `ASP.NET_SessionId` / `TS01c66436` cookies.
- 學費 extraction looks for a table row whose first cell starts with `學費`
  and returns the next numeric cell. If the page layout differs, adjust
  `ApiClient.parseTuitionFee`.
- Cells that fail are tagged `ERR:<reason>` so you can spot them in the
  output file.
