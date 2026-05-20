package com.btse.autofeeinput;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.security.Security;

public class Main extends Application {

    static {
        // ap.ece.moe.edu.tw still negotiates legacy TLS / weak ciphers. JDK 17
        // disables TLSv1, TLSv1.1, and small DH/EC keys by default — relax
        // the policy at the earliest possible moment so the first HTTPS call
        // succeeds. Must run before any SSL context is created.
        Security.setProperty("jdk.tls.disabledAlgorithms",
                "SSLv3, RC4, MD5withRSA, DH keySize < 768, EC keySize < 224");
    }

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main.fxml"));
        Parent root = loader.load();
        Scene scene = new Scene(root, 1200, 800);
        scene.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        stage.setTitle("Auto Fee Input");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
