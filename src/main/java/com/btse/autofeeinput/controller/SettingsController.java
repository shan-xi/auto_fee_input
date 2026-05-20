package com.btse.autofeeinput.controller;

import com.btse.autofeeinput.service.OcrConfig;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.stage.Stage;

public class SettingsController {

    @FXML private CheckBox ocrEnabledCheck;
    @FXML private PasswordField apiKeyField;
    @FXML private Label locationLabel;
    @FXML private Label statusLabel;

    private Stage stage;
    private Runnable onSaved;

    public void init(Stage stage, Runnable onSaved) {
        this.stage = stage;
        this.onSaved = onSaved;
        OcrConfig current = OcrConfig.load();

        ocrEnabledCheck.setSelected(current.ocrEnabled());
        apiKeyField.disableProperty().bind(ocrEnabledCheck.selectedProperty().not());

        if (!OcrConfig.DEFAULT_API_KEY.equals(current.apiKey())) {
            apiKeyField.setText(current.apiKey());
        }
        locationLabel.setText("Saved to: " + OcrConfig.userConfigPath()
                + "\nActive source: " + current.apiKeySource());
        statusLabel.setText("");
    }

    @FXML
    private void onSave() {
        String key = apiKeyField.getText() == null ? "" : apiKeyField.getText().trim();
        boolean enabled = ocrEnabledCheck.isSelected();
        try {
            OcrConfig.save(key, enabled);
            if (onSaved != null) onSaved.run();
            stage.close();
        } catch (Exception e) {
            statusLabel.setStyle("-fx-text-fill: #e53935;");
            statusLabel.setText("Save failed: " + e.getMessage());
        }
    }

    @FXML
    private void onCancel() {
        stage.close();
    }
}
