package com.btse.autofeeinput.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.io.ByteArrayInputStream;
import java.util.function.Supplier;

public class CaptchaController {

    @FXML private Label keywordLabel;
    @FXML private Label errorLabel;
    @FXML private ImageView captchaImage;
    @FXML private TextField captchaField;
    @FXML private Button sendBtn;

    private CaptchaSession session;
    private Supplier<byte[]> refresher;

    public void init(String keyword, byte[] initialImage, Supplier<byte[]> refresher) {
        this.refresher = refresher;
        keywordLabel.setText(keyword);
        setImage(initialImage);
        errorLabel.setText("");
    }

    void bindSession(CaptchaSession session) {
        this.session = session;
    }

    @FXML
    private void onSend() {
        String value = captchaField.getText() == null ? "" : captchaField.getText().trim();
        if (value.isEmpty()) return;
        // Disable input while server validates; CaptchaSession.showError() or close() re-enables/closes.
        captchaField.setDisable(true);
        sendBtn.setDisable(true);
        errorLabel.setText("Submitting...");
        errorLabel.setStyle("-fx-text-fill: #555;");
        if (session != null) session.onSend(value);
    }

    @FXML
    private void onRefresh() {
        if (refresher == null) return;
        new Thread(() -> {
            byte[] fresh;
            try {
                fresh = refresher.get();
            } catch (Exception e) {
                return;
            }
            Platform.runLater(() -> setImage(fresh));
        }, "captcha-refresh").start();
    }

    @FXML
    private void onCancel() {
        if (session != null) session.onCancel();
    }

    /** Called on FX thread by CaptchaSession when worker reports a wrong code. */
    void showError(String message, byte[] freshImage) {
        errorLabel.setStyle("-fx-text-fill: #e53935;");
        errorLabel.setText(message);
        if (freshImage != null && freshImage.length > 0) setImage(freshImage);
        captchaField.setDisable(false);
        sendBtn.setDisable(false);
        captchaField.clear();
        captchaField.requestFocus();
    }

    private void setImage(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return;
        captchaImage.setImage(new Image(new ByteArrayInputStream(bytes)));
    }
}
