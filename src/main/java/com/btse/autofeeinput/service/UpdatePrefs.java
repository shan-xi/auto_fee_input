package com.btse.autofeeinput.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Persists update-related user choices (currently: which version the user asked to skip). Lives
 * next to the OCR config under the same per-user dir.
 */
public final class UpdatePrefs {

    private static final Logger log = LoggerFactory.getLogger(UpdatePrefs.class);
    private static final String FILE_NAME = "app.properties";
    private static final String KEY_SKIPPED = "skippedVersion";

    private UpdatePrefs() {}

    /** Tag name (e.g. "v1.3.0") the user chose to skip, or empty string. */
    public static String skippedVersion() {
        Properties p = readProps(file());
        return p.getProperty(KEY_SKIPPED, "").trim();
    }

    public static void setSkippedVersion(String tag) throws IOException {
        Path f = file();
        Files.createDirectories(f.getParent());
        Properties p = readProps(f);
        if (tag == null || tag.trim().isEmpty()) p.remove(KEY_SKIPPED);
        else p.setProperty(KEY_SKIPPED, tag.trim());
        try (OutputStream out = Files.newOutputStream(f)) {
            p.store(out, "Auto Fee Input — update prefs (managed by app)");
        }
    }

    private static Path file() {
        // Mirror OcrConfig.userConfigPath()'s parent dir for consistency.
        String home = System.getProperty("user.home", ".");
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("mac")) {
            return Paths.get(home, "Library", "Application Support", "AutoFeeInput", FILE_NAME);
        }
        if (os.contains("win")) {
            String appdata = System.getenv("APPDATA");
            Path base =
                    appdata != null && !appdata.isEmpty()
                            ? Paths.get(appdata, "AutoFeeInput")
                            : Paths.get(home, ".auto-fee-input");
            return base.resolve(FILE_NAME);
        }
        String xdg = System.getenv("XDG_CONFIG_HOME");
        Path base =
                xdg != null && !xdg.isEmpty()
                        ? Paths.get(xdg, "auto-fee-input")
                        : Paths.get(home, ".config", "auto-fee-input");
        return base.resolve(FILE_NAME);
    }

    private static Properties readProps(Path path) {
        Properties p = new Properties();
        if (path != null && Files.isRegularFile(path)) {
            try (InputStream in = Files.newInputStream(path)) {
                p.load(in);
            } catch (IOException e) {
                log.warn("Failed to read {}: {}", path, e.getMessage());
            }
        }
        return p;
    }
}
