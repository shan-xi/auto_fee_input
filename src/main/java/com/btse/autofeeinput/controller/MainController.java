package com.btse.autofeeinput.controller;

import com.btse.autofeeinput.AppVersion;
import com.btse.autofeeinput.model.SheetData;
import com.btse.autofeeinput.service.CaptchaUi;
import com.btse.autofeeinput.service.ExcelService;
import com.btse.autofeeinput.service.OcrConfig;
import com.btse.autofeeinput.service.OcrService;
import com.btse.autofeeinput.service.ProcessingService;
import com.btse.autofeeinput.service.UpdateChecker;
import com.btse.autofeeinput.service.UpdatePrefs;
import java.awt.Desktop;
import java.io.File;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainController {

    private static final Logger LOG = LoggerFactory.getLogger(MainController.class);

    @FXML private Button uploadBtn;
    @FXML private Label filePathLabel;
    @FXML private ComboBox<String> queryColumnCombo;
    @FXML private ComboBox<String> feeColumnCombo;
    @FXML private Button startBtn;
    @FXML private Button stopBtn;
    @FXML private Button resumeBtn;
    @FXML private Button downloadBtn;
    @FXML private Label statusLabel;
    @FXML private TableView<ObservableList<String>> sheetTable;
    @FXML private TextArea logArea;

    private final ExcelService excelService = new ExcelService();
    private OcrService ocrService;
    private SheetData sheetData;
    private File sourceFile;
    private Thread workerThread;
    private ProcessingService processor;
    private final AtomicReference<CaptchaSession> activeCaptcha = new AtomicReference<>();
    private final CaptchaUi captchaUi = this::openCaptcha;
    private final UpdateChecker updateChecker = new UpdateChecker();
    private final ScheduledExecutorService updateScheduler =
            Executors.newSingleThreadScheduledExecutor(
                    r -> {
                        Thread t = new Thread(r, "update-checker");
                        t.setDaemon(true);
                        return t;
                    });

    /**
     * Suppress repeated dialogs once one is open or once the user already dismissed today's check.
     */
    private final AtomicBoolean updateDialogActive = new AtomicBoolean();

    private final SimpleDateFormat ts = new SimpleDateFormat("HH:mm:ss");
    private final SimpleDateFormat fileTs = new SimpleDateFormat("yyyyMMddHHmmss");

    @FXML
    public void initialize() {
        reinitOcrService();
        queryColumnCombo.disableProperty().bind(sheetTable.itemsProperty().isNull());
        feeColumnCombo.disableProperty().bind(sheetTable.itemsProperty().isNull());

        Runnable updateStart =
                () ->
                        startBtn.setDisable(
                                sheetData == null
                                        || queryColumnCombo.getValue() == null
                                        || feeColumnCombo.getValue() == null
                                        || workerRunning());
        queryColumnCombo.valueProperty().addListener((o, a, b) -> updateStart.run());
        feeColumnCombo.valueProperty().addListener((o, a, b) -> updateStart.run());

        installSheetCopySupport();
        scheduleUpdateChecks();
    }

    /** Run an immediate background check, then re-poll every 6 hours while the app is open. */
    private void scheduleUpdateChecks() {
        Runnable task =
                () -> {
                    try {
                        Optional<UpdateChecker.Release> latest = updateChecker.fetchLatest();
                        latest.ifPresent(
                                rel -> {
                                    String current = AppVersion.value();
                                    if (!UpdateChecker.isNewer(rel.tagName, current)) return;
                                    if (rel.tagName.equals(UpdatePrefs.skippedVersion())) return;
                                    Platform.runLater(() -> showUpdateDialog(rel, current));
                                });
                    } catch (Exception e) {
                        LOG.info("Update check error: {}", e.toString());
                    }
                };
        updateScheduler.schedule(task, 3, TimeUnit.SECONDS);
        updateScheduler.scheduleAtFixedRate(task, 6, 6, TimeUnit.HOURS);
    }

    private void showUpdateDialog(UpdateChecker.Release rel, String current) {
        if (!updateDialogActive.compareAndSet(false, true)) return;
        try {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Update available");
            alert.setHeaderText("A new version is available: " + rel.tagName);
            alert.setContentText(
                    "You are running v"
                            + current
                            + ".\n"
                            + "Open the GitHub release page to download the latest installer?");
            ButtonType openBtn = new ButtonType("Open Release Page", ButtonBar.ButtonData.YES);
            ButtonType remindBtn = new ButtonType("Remind Me Later", ButtonBar.ButtonData.NO);
            ButtonType skipBtn =
                    new ButtonType("Skip This Version", ButtonBar.ButtonData.CANCEL_CLOSE);
            alert.getButtonTypes().setAll(openBtn, remindBtn, skipBtn);

            alert.showAndWait()
                    .ifPresent(
                            choice -> {
                                if (choice == openBtn) {
                                    openInBrowser(rel.pageUrl);
                                    log("Opened release page for " + rel.tagName);
                                } else if (choice == skipBtn) {
                                    try {
                                        UpdatePrefs.setSkippedVersion(rel.tagName);
                                        log("Skipping update notifications for " + rel.tagName);
                                    } catch (Exception e) {
                                        log("Failed to save skip preference: " + e.getMessage());
                                    }
                                }
                            });
        } finally {
            updateDialogActive.set(false);
        }
    }

    private void openInBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
                return;
            }
        } catch (Exception e) {
            LOG.info("Desktop.browse failed: {}", e.toString());
        }
        // Fallback for headless / unsupported Desktop — paste-friendly logging.
        log("Open this URL manually: " + url);
    }

    /** Enables cell-level selection, Ctrl/Cmd+C copy, and a Copy context menu on the sheet. */
    private void installSheetCopySupport() {
        sheetTable.getSelectionModel().setCellSelectionEnabled(true);
        sheetTable
                .getSelectionModel()
                .setSelectionMode(javafx.scene.control.SelectionMode.MULTIPLE);

        javafx.scene.input.KeyCombination copyShortcut =
                new javafx.scene.input.KeyCodeCombination(
                        javafx.scene.input.KeyCode.C,
                        javafx.scene.input.KeyCombination.SHORTCUT_DOWN);
        sheetTable.addEventFilter(
                javafx.scene.input.KeyEvent.KEY_PRESSED,
                e -> {
                    if (copyShortcut.match(e)) {
                        copySelectionToClipboard();
                        e.consume();
                    }
                });

        MenuItem copyItem = new MenuItem("Copy");
        copyItem.setAccelerator(copyShortcut);
        copyItem.setOnAction(e -> copySelectionToClipboard());
        sheetTable.setContextMenu(new ContextMenu(copyItem));
    }

    /**
     * Copies the current selection to the clipboard as TSV. Cells from the same row are
     * tab-separated; rows are newline-separated. Empty selection no-ops.
     */
    private void copySelectionToClipboard() {
        var selected = sheetTable.getSelectionModel().getSelectedCells();
        if (selected.isEmpty()) return;

        java.util.TreeMap<Integer, java.util.TreeMap<Integer, String>> grid =
                new java.util.TreeMap<>();
        for (var pos : selected) {
            int rowIdx = pos.getRow();
            int colIdx = pos.getColumn();
            if (rowIdx < 0 || colIdx < 0) continue;
            String text = "";
            Object cellValue =
                    pos.getTableColumn() == null
                            ? null
                            : pos.getTableColumn().getCellObservableValue(rowIdx) == null
                                    ? null
                                    : pos.getTableColumn()
                                            .getCellObservableValue(rowIdx)
                                            .getValue();
            if (cellValue != null) text = cellValue.toString();
            grid.computeIfAbsent(rowIdx, k -> new java.util.TreeMap<>()).put(colIdx, text);
        }
        if (grid.isEmpty()) return;

        StringBuilder out = new StringBuilder();
        boolean firstRow = true;
        for (var row : grid.values()) {
            if (!firstRow) out.append('\n');
            firstRow = false;
            boolean firstCell = true;
            int prevCol = -1;
            for (var entry : row.entrySet()) {
                if (!firstCell) {
                    // Pad gaps when the user selected non-contiguous columns.
                    int gap = entry.getKey() - prevCol - 1;
                    for (int i = 0; i < gap; i++) out.append('\t');
                    out.append('\t');
                }
                out.append(entry.getValue());
                prevCol = entry.getKey();
                firstCell = false;
            }
        }
        javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
        content.putString(out.toString());
        javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
    }

    @FXML
    private void onUpload() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Excel/CSV File");
        chooser.getExtensionFilters()
                .add(new FileChooser.ExtensionFilter("Spreadsheets", "*.csv", "*.xls", "*.xlsx"));
        File f = chooser.showOpenDialog(uploadBtn.getScene().getWindow());
        if (f == null) return;
        loadFile(f);
    }

    private void loadFile(File f) {
        try {
            sheetData = excelService.read(f);
            sourceFile = f;
            filePathLabel.setText(f.getAbsolutePath());
            populateTable(sheetData);
            populateComboBoxes(sheetData);
            downloadBtn.setDisable(false);
            statusLabel.setText("Loaded " + sheetData.getRows().size() + " rows.");
            log(
                    "Loaded file: "
                            + f.getAbsolutePath()
                            + " (rows="
                            + sheetData.getRows().size()
                            + ")");
        } catch (Exception e) {
            error("Failed to read file: " + e.getMessage());
        }
    }

    private void populateTable(SheetData data) {
        sheetTable.getColumns().clear();

        // Display-only row number column — not part of SheetData, not saved on download.
        TableColumn<ObservableList<String>, String> rowNumCol = new TableColumn<>("#");
        rowNumCol.setCellValueFactory(
                cd -> {
                    int idx = sheetTable.getItems().indexOf(cd.getValue());
                    return new SimpleStringProperty(idx < 0 ? "" : String.valueOf(idx + 1));
                });
        rowNumCol.setSortable(false);
        rowNumCol.setPrefWidth(56);
        sheetTable.getColumns().add(rowNumCol);

        for (int i = 0; i < data.getHeaders().size(); i++) {
            final int colIdx = i;
            TableColumn<ObservableList<String>, String> col =
                    new TableColumn<>(data.getHeaders().get(i));
            col.setCellValueFactory(
                    cd -> {
                        ObservableList<String> row = cd.getValue();
                        String v = colIdx < row.size() ? row.get(colIdx) : "";
                        return new SimpleStringProperty(v);
                    });
            col.setPrefWidth(140);
            sheetTable.getColumns().add(col);
        }
        sheetTable.setItems(data.getRows());
    }

    /** Refreshes a single column to pick up edits made to backing ObservableList rows. */
    private void refreshFeeColumn() {
        sheetTable.refresh();
    }

    private void populateComboBoxes(SheetData data) {
        ObservableList<String> headers = FXCollections.observableArrayList(data.getHeaders());
        queryColumnCombo.setItems(headers);
        feeColumnCombo.setItems(headers);
        queryColumnCombo.getSelectionModel().clearSelection();
        feeColumnCombo.getSelectionModel().clearSelection();
    }

    @FXML
    private void onStart() {
        if (sheetData == null) return;
        String queryCol = queryColumnCombo.getValue();
        String feeCol = feeColumnCombo.getValue();
        if (queryCol == null || feeCol == null) {
            error("Select both query and fee columns.");
            return;
        }
        int qIdx = sheetData.columnIndex(queryCol);
        int fIdx = sheetData.columnIndex(feeCol);
        if (qIdx < 0 || fIdx < 0) {
            error("Invalid column selection.");
            return;
        }

        statusLabel.setText("Running...");
        log("Start processing. queryCol=" + queryCol + " feeCol=" + feeCol);
        launchWorker(qIdx, fIdx, 0);
    }

    @FXML
    private void onStop() {
        if (processor != null) {
            processor.stop();
            log("Stop requested.");
        }
        if (workerThread != null && workerThread.isAlive()) {
            workerThread.interrupt();
        }
        CaptchaSession cap = activeCaptcha.get();
        if (cap != null) cap.cancelExternally();
        stopBtn.setDisable(true);
        resumeBtn.setDisable(false);
        statusLabel.setText("Stopped.");
    }

    @FXML
    private void onResume() {
        if (sheetData == null) return;
        int resumeFrom = processor == null ? 0 : processor.getCurrentRow();
        log("Resume from row " + (resumeFrom + 1));
        statusLabel.setText("Resuming...");

        int qIdx = sheetData.columnIndex(queryColumnCombo.getValue());
        int fIdx = sheetData.columnIndex(feeColumnCombo.getValue());
        launchWorker(qIdx, fIdx, resumeFrom);
    }

    private void launchWorker(int qIdx, int fIdx, int startRow) {
        startBtn.setDisable(true);
        stopBtn.setDisable(false);
        resumeBtn.setDisable(true);
        uploadBtn.setDisable(true);

        processor =
                new ProcessingService(
                        sheetData,
                        qIdx,
                        fIdx,
                        captchaUi,
                        new ProcessingService.Listener() {
                            @Override
                            public void onRowStart(int rowIndex, String keyword) {
                                Platform.runLater(
                                        () ->
                                                statusLabel.setText(
                                                        "Row " + (rowIndex + 1) + ": " + keyword));
                            }

                            @Override
                            public void onRowDone(int rowIndex, String fee) {
                                Platform.runLater(
                                        () -> {
                                            sheetData.setCell(
                                                    rowIndex, fIdx, fee == null ? "" : fee);
                                            refreshFeeColumn();
                                        });
                            }

                            @Override
                            public void onRowError(int rowIndex, String keyword, String message) {
                                Platform.runLater(
                                        () -> {
                                            sheetData.setCell(
                                                    rowIndex,
                                                    fIdx,
                                                    "ERR:" + (message == null ? "" : message));
                                            refreshFeeColumn();
                                            log("Row " + (rowIndex + 1) + " error: " + message);
                                        });
                            }

                            @Override
                            public void log(String message) {
                                Platform.runLater(() -> MainController.this.log(message));
                            }
                        });

        workerThread =
                new Thread(
                        () -> {
                            try {
                                processor.run(startRow);
                            } catch (Exception e) {
                                Platform.runLater(() -> error("Worker crashed: " + e.getMessage()));
                            } finally {
                                Platform.runLater(this::afterWorkerEnded);
                            }
                        },
                        "fee-worker");
        workerThread.setDaemon(true);
        workerThread.start();
    }

    private void afterWorkerEnded() {
        boolean finished = processor != null && processor.isCompleted();
        stopBtn.setDisable(true);
        uploadBtn.setDisable(false);
        startBtn.setDisable(!finished);
        resumeBtn.setDisable(finished);
        if (finished) {
            statusLabel.setText("Done. Press Download to save.");
            log("Processing complete. Awaiting Download.");
        } else {
            int row = processor == null ? 0 : processor.getCurrentRow();
            statusLabel.setText("Stopped at row " + (row + 1));
        }
    }

    @FXML
    private void onDownload() {
        if (sheetData == null || sourceFile == null) return;

        String srcName = sourceFile.getName();
        int dot = srcName.lastIndexOf('.');
        String base = dot < 0 ? srcName : srcName.substring(0, dot);
        String suggestedName = base + "_" + fileTs.format(new Date()) + ".xlsx";

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Processed File");
        chooser.setInitialFileName(suggestedName);
        if (sourceFile.getParentFile() != null) {
            chooser.setInitialDirectory(sourceFile.getParentFile());
        }
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel", "*.xlsx"));
        File target = chooser.showSaveDialog(downloadBtn.getScene().getWindow());
        if (target == null) return;
        try {
            excelService.write(target.toPath(), sheetData);
            log("Saved to: " + target.getAbsolutePath());
            statusLabel.setText("Saved to " + target.getAbsolutePath());
        } catch (Exception e) {
            error("Save failed: " + e.getMessage());
        }
    }

    @FXML
    private void onClearLog() {
        logArea.clear();
    }

    @FXML
    private void onSettings() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/settings.fxml"));
            Parent root = loader.load();
            SettingsController c = loader.getController();
            Stage stage = new Stage();
            stage.setTitle("Settings");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            c.init(stage, this::reinitOcrService);
            stage.showAndWait();
        } catch (Exception e) {
            error("Failed to open Settings: " + e.getMessage());
        }
    }

    private void reinitOcrService() {
        OcrService old = ocrService;
        OcrConfig cfg = OcrConfig.load();
        if (!cfg.ocrEnabled()) {
            ocrService = null;
            log("OCR disabled (Settings) — captcha popup will use manual entry");
        } else {
            try {
                ocrService = new OcrService(cfg, msg -> Platform.runLater(() -> log(msg)));
                log(
                        "OCR enabled (Settings) — keys="
                                + cfg.apiKeys().size()
                                + " source: "
                                + cfg.apiKeySource());
            } catch (Throwable t) {
                log("OCR init failed (manual entry only): " + t);
                LOG.error("OCR init failed", t);
                ocrService = null;
            }
        }
        if (old != null && old != ocrService) old.close();
    }

    private boolean workerRunning() {
        return workerThread != null && workerThread.isAlive();
    }

    private CaptchaUi.Session openCaptcha(
            String keyword, byte[] initialImage, Supplier<byte[]> refresher)
            throws InterruptedException {
        CompletableFuture<CaptchaSession> future = new CompletableFuture<>();
        Platform.runLater(
                () -> {
                    try {
                        FXMLLoader loader =
                                new FXMLLoader(getClass().getResource("/fxml/captcha.fxml"));
                        Parent root = loader.load();
                        CaptchaController controller = loader.getController();
                        controller.init(keyword, initialImage, refresher, ocrService);

                        Stage stage = new Stage();
                        stage.initModality(Modality.NONE);
                        stage.setTitle("Captcha: " + keyword);
                        stage.setScene(new Scene(root));
                        stage.setAlwaysOnTop(true);

                        CaptchaSession session = new CaptchaSession(stage, controller);
                        stage.setOnCloseRequest(e -> session.cancelExternally());
                        stage.setOnHidden(e -> activeCaptcha.compareAndSet(session, null));
                        activeCaptcha.set(session);
                        stage.show();
                        future.complete(session);
                    } catch (Exception e) {
                        future.completeExceptionally(e);
                    }
                });
        try {
            return future.get();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw ie;
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new RuntimeException("Failed to open captcha popup", cause);
        }
    }

    private void log(String msg) {
        String line = "[" + ts.format(new Date()) + "] " + msg + "\n";
        logArea.appendText(line);
    }

    private void error(String msg) {
        log("ERROR: " + msg);
        Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        a.showAndWait();
    }
}
