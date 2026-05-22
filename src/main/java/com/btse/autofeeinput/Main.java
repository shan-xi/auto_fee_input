package com.btse.autofeeinput;

import java.security.Security;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {

    static {
        // ap.ece.moe.edu.tw requires TLSv1 / TLSv1.1, which JDK 11+ disables
        // by default. jdk.tls.disabledAlgorithms is JVM-global, so re-enable
        // only those two; keep DES, 3DES, anon, NULL, RC4 and small keys
        // disabled. Must run before any SSL context is created.
        Security.setProperty(
                "jdk.tls.disabledAlgorithms",
                "SSLv3, RC4, DES, MD5withRSA, DH keySize < 1024, "
                        + "EC keySize < 224, 3DES_EDE_CBC, anon, NULL, "
                        + "include jdk.disabled.namedCurves");
    }

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main.fxml"));
        Parent root = loader.load();
        Scene scene = new Scene(root, 1200, 800);
        scene.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        stage.setTitle("Auto Fee Input v" + AppVersion.value());
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
