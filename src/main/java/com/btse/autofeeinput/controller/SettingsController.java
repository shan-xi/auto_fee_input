package com.btse.autofeeinput.controller;

import com.btse.autofeeinput.service.OcrConfig;
import javafx.beans.binding.BooleanBinding;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;

public class SettingsController {

    @FXML private CheckBox ocrEnabledCheck;
    @FXML private PasswordField apiKey1;
    @FXML private PasswordField apiKey2;
    @FXML private PasswordField apiKey3;
    @FXML private PasswordField apiKey4;
    @FXML private PasswordField apiKey5;
    @FXML private PasswordField apiKey6;
    @FXML private PasswordField apiKey7;
    @FXML private PasswordField apiKey8;
    @FXML private PasswordField apiKey9;
    @FXML private PasswordField apiKey10;
    @FXML private Label locationLabel;
    @FXML private Label statusLabel;

    private Stage stage;
    private Runnable onSaved;

    public void init(Stage stage, Runnable onSaved) {
        this.stage = stage;
        this.onSaved = onSaved;
        OcrConfig current = OcrConfig.load();

        ocrEnabledCheck.setSelected(current.ocrEnabled());
        BooleanBinding disabled = ocrEnabledCheck.selectedProperty().not();
        for (PasswordField f : keyFields()) f.disableProperty().bind(disabled);

        List<String> keys = current.apiKeys();
        List<PasswordField> fields = keyFields();
        boolean usingDemoOnly = keys.size() == 1 && OcrConfig.DEFAULT_API_KEY.equals(keys.get(0));
        for (int i = 0; i < fields.size(); i++) {
            String v = !usingDemoOnly && i < keys.size() ? keys.get(i) : "";
            fields.get(i).setText(v);
        }
        locationLabel.setText("Saved to: " + OcrConfig.userConfigPath()
                + "\nActive source: " + current.apiKeySource());
        statusLabel.setText("");
    }

    @FXML
    private void onSave() {
        List<String> keys = new ArrayList<>();
        for (PasswordField f : keyFields()) {
            String v = f.getText() == null ? "" : f.getText().trim();
            if (!v.isEmpty()) keys.add(v);
        }
        boolean enabled = ocrEnabledCheck.isSelected();
        try {
            OcrConfig.save(keys, enabled);
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

    private List<PasswordField> keyFields() {
        List<PasswordField> list = new ArrayList<>(OcrConfig.MAX_API_KEYS);
        list.add(apiKey1); list.add(apiKey2); list.add(apiKey3); list.add(apiKey4); list.add(apiKey5);
        list.add(apiKey6); list.add(apiKey7); list.add(apiKey8); list.add(apiKey9); list.add(apiKey10);
        return list;
    }
}