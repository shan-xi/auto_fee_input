package com.btse.autofeeinput.controller;

import com.btse.autofeeinput.service.OcrService;
import java.io.ByteArrayInputStream;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CaptchaController {

    private static final Logger log = LoggerFactory.getLogger(CaptchaController.class);
    private static final long OCR_TIMEOUT_MS = 20_000L;
    private static final int MAX_EMPTY_OCR_RETRIES = 5;

    @FXML private Label keywordLabel;
    @FXML private Label errorLabel;
    @FXML private ImageView captchaImage;
    @FXML private TextField captchaField;
    @FXML private Button sendBtn;

    private CaptchaSession session;
    private Supplier<byte[]> refresher;
    private OcrService ocr;

    /** Monotonic id; result callbacks ignore stale OCR runs (image refreshed since). */
    private int ocrRequestSeq = 0;

    private boolean autoSubmit = true;
    private int autoAttempt = 0;
    private int emptyOcrRetries = 0;

    /** Last image bytes pushed to the popup; consumed by the worker after a successful submit. */
    private volatile byte[] lastImageBytes;

    public void init(
            String keyword, byte[] initialImage, Supplier<byte[]> refresher, OcrService ocr) {
        this.refresher = refresher;
        this.ocr = ocr;
        keywordLabel.setText(keyword);
        setImage(initialImage);
        errorLabel.setText("");
        if (ocr == null) {
            autoSubmit = false;
        }
        triggerOcr(initialImage);
    }

    void bindSession(CaptchaSession session) {
        this.session = session;
    }

    @FXML
    private void onSend() {
        String value = captchaField.getText() == null ? "" : captchaField.getText().trim();
        if (value.isEmpty()) return;
        captchaField.setDisable(true);
        sendBtn.setDisable(true);
        errorLabel.setStyle("-fx-text-fill: #555;");
        errorLabel.setText("Submitting...");
        if (session != null) session.onSend(value);
    }

    @FXML
    private void onRefresh() {
        if (refresher == null) return;
        new Thread(
                        () -> {
                            byte[] fresh;
                            try {
                                fresh = refresher.get();
                            } catch (Exception e) {
                                return;
                            }
                            Platform.runLater(
                                    () -> {
                                        setImage(fresh);
                                        triggerOcr(fresh);
                                    });
                        },
                        "captcha-refresh")
                .start();
    }

    @FXML
    private void onCancel() {
        if (session != null) session.onCancel();
    }

    /** Called on FX thread by CaptchaSession when worker reports a wrong code. */
    void showError(String message, byte[] freshImage) {
        errorLabel.setStyle("-fx-text-fill: #e53935;");
        errorLabel.setText(message);
        if (freshImage != null && freshImage.length > 0) {
            setImage(freshImage);
            triggerOcr(freshImage);
        } else {
            captchaField.setDisable(false);
            sendBtn.setDisable(false);
            captchaField.clear();
            captchaField.requestFocus();
        }
    }

    /**
     * Fire-and-forget OCR call. Pre-fills field on success, leaves blank on error / timeout. Stale
     * results (image refreshed in between) are dropped.
     */
    private void triggerOcr(byte[] image) {
        if (ocr == null || image == null || image.length == 0) {
            captchaField.setDisable(false);
            sendBtn.setDisable(false);
            captchaField.requestFocus();
            return;
        }
        int reqId = ++ocrRequestSeq;
        captchaField.setDisable(true);
        sendBtn.setDisable(true);
        captchaField.clear();
        errorLabel.setStyle("-fx-text-fill: #2b6cb0;");
        errorLabel.setText("辨識中... Recognizing...");

        ocr.recognize(image, OCR_TIMEOUT_MS)
                .whenComplete(
                        (text, ex) -> Platform.runLater(() -> applyOcrResult(reqId, text, ex)));
    }

    private void applyOcrResult(int reqId, String text, Throwable ex) {
        if (reqId != ocrRequestSeq) return; // stale — newer image since
        captchaField.setDisable(false);
        sendBtn.setDisable(false);
        if (ex != null) {
            log.info("OCR failed: {}", ex.toString());
            errorLabel.setStyle("-fx-text-fill: #6b7280;");
            errorLabel.setText("OCR unavailable (" + shortReason(ex) + ") — type manually");
            captchaField.requestFocus();
            return;
        }
        String guess = text == null ? "" : text.replaceAll("[^A-Za-z0-9]", "");
        if (guess.isEmpty()) {
            if (autoSubmit && emptyOcrRetries < MAX_EMPTY_OCR_RETRIES) {
                emptyOcrRetries++;
                errorLabel.setStyle("-fx-text-fill: #2b6cb0;");
                errorLabel.setText(
                        "OCR empty — refreshing ("
                                + emptyOcrRetries
                                + "/"
                                + MAX_EMPTY_OCR_RETRIES
                                + ")");
                onRefresh();
                return;
            }
            errorLabel.setStyle("-fx-text-fill: #6b7280;");
            errorLabel.setText("OCR returned empty — type manually");
            captchaField.requestFocus();
            return;
        }
        emptyOcrRetries = 0;
        captchaField.setText(guess);
        captchaField.selectAll();
        captchaField.requestFocus();
        if (autoSubmit) {
            autoAttempt++;
            errorLabel.setStyle("-fx-text-fill: #2b6cb0;");
            errorLabel.setText("Auto-sending attempt " + autoAttempt + " (\"" + guess + "\")");
            onSend();
        } else {
            errorLabel.setText("");
        }
    }

    private static String shortReason(Throwable ex) {
        String n = ex.getClass().getSimpleName();
        if (n.contains("Timeout")) return "timeout";
        String m = ex.getMessage();
        return m == null || m.isEmpty() ? n : m;
    }

    private void setImage(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return;
        lastImageBytes = bytes;
        captchaImage.setImage(new Image(new ByteArrayInputStream(bytes)));
    }

    byte[] lastImage() {
        return lastImageBytes;
    }
}
