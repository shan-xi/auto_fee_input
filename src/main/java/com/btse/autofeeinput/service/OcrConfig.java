package com.btse.autofeeinput.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * OCR config with the following resolution order (highest first):
 *
 *   1. OCR_API_KEY / OCR_ENDPOINT_URL environment variables
 *   2. ocr.properties in the current working directory (dev / launch dir)
 *   3. ocr.properties in the per-user config dir (managed by Settings dialog)
 *   4. built-in default (the public demo key)
 *
 * Recognized property keys: endpointUrl, apiKey.
 *
 * Per-user file location:
 *   macOS   - ~/Library/Application Support/AutoFeeInput/ocr.properties
 *   Windows - %APPDATA%\AutoFeeInput\ocr.properties
 *   Linux   - $XDG_CONFIG_HOME/auto-fee-input/ocr.properties (or ~/.config/...)
 */
public final class OcrConfig {

    private static final Logger log = LoggerFactory.getLogger(OcrConfig.class);
    private static final String FILE_NAME = "ocr.properties";

    public static final String DEFAULT_ENDPOINT_URL = "https://api.ocr.space/parse/image";
    /** Public demo key — rate-limited to ~20 req/hr. */
    public static final String DEFAULT_API_KEY = "helloworld";
    public static final boolean DEFAULT_OCR_ENABLED = true;

    private final String endpointUrl;
    private final String apiKey;
    private final String apiKeySource;
    private final boolean ocrEnabled;

    private OcrConfig(String endpointUrl, String apiKey, String apiKeySource, boolean ocrEnabled) {
        this.endpointUrl = endpointUrl;
        this.apiKey = apiKey;
        this.apiKeySource = apiKeySource;
        this.ocrEnabled = ocrEnabled;
    }

    public String endpointUrl() { return endpointUrl; }
    public String apiKey() { return apiKey; }
    /** Where the active apiKey came from (for diagnostics). */
    public String apiKeySource() { return apiKeySource; }
    public boolean ocrEnabled() { return ocrEnabled; }

    public static OcrConfig load() {
        Properties cwd = readProps(Paths.get(FILE_NAME));
        Properties home = readProps(userConfigPath());

        String url = pick(
                System.getenv("OCR_ENDPOINT_URL"),
                cwd.getProperty("endpointUrl"),
                home.getProperty("endpointUrl"),
                DEFAULT_ENDPOINT_URL);

        String envKey = System.getenv("OCR_API_KEY");
        String cwdKey = cwd.getProperty("apiKey");
        String homeKey = home.getProperty("apiKey");

        String key;
        String source;
        if (nonBlank(envKey))      { key = envKey;  source = "env OCR_API_KEY"; }
        else if (nonBlank(cwdKey)) { key = cwdKey;  source = "./ocr.properties"; }
        else if (nonBlank(homeKey)){ key = homeKey; source = userConfigPath().toString(); }
        else                       { key = DEFAULT_API_KEY; source = "built-in demo"; }

        boolean enabled = pickBool(
                System.getenv("OCR_ENABLED"),
                cwd.getProperty("ocrEnabled"),
                home.getProperty("ocrEnabled"),
                DEFAULT_OCR_ENABLED);

        return new OcrConfig(url.trim(), key.trim(), source, enabled);
    }

    /** Persist OCR settings to the per-user config file. Null args leave that key untouched. */
    public static void save(String apiKey, Boolean ocrEnabled) throws IOException {
        Path file = userConfigPath();
        Files.createDirectories(file.getParent());
        Properties p = readProps(file);
        if (apiKey != null) {
            if (apiKey.trim().isEmpty()) p.remove("apiKey");
            else p.setProperty("apiKey", apiKey.trim());
        }
        if (ocrEnabled != null) {
            p.setProperty("ocrEnabled", Boolean.toString(ocrEnabled));
        }
        try (OutputStream out = Files.newOutputStream(file)) {
            p.store(out, "Auto Fee Input — OCR settings (managed by app)");
        }
        log.info("Saved OCR settings to {}", file);
    }

    /** Backwards-compatible shortcut. */
    public static void saveApiKey(String key) throws IOException {
        save(key, null);
    }

    public static Path userConfigPath() {
        String home = System.getProperty("user.home", ".");
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("mac")) {
            return Paths.get(home, "Library", "Application Support", "AutoFeeInput", FILE_NAME);
        }
        if (os.contains("win")) {
            String appdata = System.getenv("APPDATA");
            Path base = appdata != null && !appdata.isEmpty()
                    ? Paths.get(appdata, "AutoFeeInput")
                    : Paths.get(home, ".auto-fee-input");
            return base.resolve(FILE_NAME);
        }
        String xdg = System.getenv("XDG_CONFIG_HOME");
        Path base = xdg != null && !xdg.isEmpty()
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

    private static String pick(String... values) {
        for (String v : values) if (nonBlank(v)) return v;
        return "";
    }

    private static boolean nonBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private static boolean pickBool(String envValue, String cwdValue, String homeValue, boolean fallback) {
        for (String v : new String[] { envValue, cwdValue, homeValue }) {
            if (nonBlank(v)) {
                String t = v.trim().toLowerCase();
                if (t.equals("true")  || t.equals("1") || t.equals("yes") || t.equals("on"))  return true;
                if (t.equals("false") || t.equals("0") || t.equals("no")  || t.equals("off")) return false;
            }
        }
        return fallback;
    }
}
