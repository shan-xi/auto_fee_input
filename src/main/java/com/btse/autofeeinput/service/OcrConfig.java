package com.btse.autofeeinput.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OCR config with the following resolution order (highest first):
 *
 * <p>1. OCR_API_KEY / OCR_ENDPOINT_URL environment variables (single key only) 2. ocr.properties in
 * the current working directory (dev / launch dir) 3. ocr.properties in the per-user config dir
 * (managed by Settings dialog) 4. built-in default (the public demo key)
 *
 * <p>Up to {@link #MAX_API_KEYS} keys may be configured (apiKey1..apiKey10). Key #1 is the default
 * active key; OcrService rotates to the next when one hits rate limit. Legacy single-key files
 * using {@code apiKey} are migrated into slot #1.
 *
 * <p>Per-user file location: macOS - ~/Library/Application Support/AutoFeeInput/ocr.properties
 * Windows - %APPDATA%\AutoFeeInput\ocr.properties Linux -
 * $XDG_CONFIG_HOME/auto-fee-input/ocr.properties (or ~/.config/...)
 */
public final class OcrConfig {

    private static final Logger log = LoggerFactory.getLogger(OcrConfig.class);
    private static final String FILE_NAME = "ocr.properties";

    public static final int MAX_API_KEYS = 10;
    public static final String DEFAULT_ENDPOINT_URL = "https://api.ocr.space/parse/image";

    /** Public demo key — rate-limited to ~20 req/hr. */
    public static final String DEFAULT_API_KEY = "helloworld";

    public static final boolean DEFAULT_OCR_ENABLED = true;

    private final String endpointUrl;
    private final List<String> apiKeys;
    private final String apiKeySource;
    private final boolean ocrEnabled;

    private OcrConfig(
            String endpointUrl, List<String> apiKeys, String apiKeySource, boolean ocrEnabled) {
        this.endpointUrl = endpointUrl;
        this.apiKeys = Collections.unmodifiableList(new ArrayList<>(apiKeys));
        this.apiKeySource = apiKeySource;
        this.ocrEnabled = ocrEnabled;
    }

    public String endpointUrl() {
        return endpointUrl;
    }

    /** Returns the active (first) API key, or empty string. */
    public String apiKey() {
        return apiKeys.isEmpty() ? "" : apiKeys.get(0);
    }

    /** Non-blank API keys in slot order (key #1 first). Never null. */
    public List<String> apiKeys() {
        return apiKeys;
    }

    /** Where the active apiKey came from (for diagnostics). */
    public String apiKeySource() {
        return apiKeySource;
    }

    public boolean ocrEnabled() {
        return ocrEnabled;
    }

    public static OcrConfig load() {
        Properties cwd = readProps(Paths.get(FILE_NAME));
        Properties home = readProps(userConfigPath());

        String url =
                pick(
                        System.getenv("OCR_ENDPOINT_URL"),
                        cwd.getProperty("endpointUrl"),
                        home.getProperty("endpointUrl"),
                        DEFAULT_ENDPOINT_URL);

        String envKey = System.getenv("OCR_API_KEY");
        List<String> cwdKeys = readKeyList(cwd);
        List<String> homeKeys = readKeyList(home);

        List<String> keys;
        String source;
        if (nonBlank(envKey)) {
            keys = new ArrayList<>();
            keys.add(envKey.trim());
            source = "env OCR_API_KEY";
        } else if (!cwdKeys.isEmpty()) {
            keys = cwdKeys;
            source = "./ocr.properties";
        } else if (!homeKeys.isEmpty()) {
            keys = homeKeys;
            source = userConfigPath().toString();
        } else {
            keys = new ArrayList<>();
            keys.add(DEFAULT_API_KEY);
            source = "built-in demo";
        }

        boolean enabled =
                pickBool(
                        System.getenv("OCR_ENABLED"),
                        cwd.getProperty("ocrEnabled"),
                        home.getProperty("ocrEnabled"),
                        DEFAULT_OCR_ENABLED);

        return new OcrConfig(url.trim(), keys, source, enabled);
    }

    /** Persist OCR settings to the per-user config file. Null args leave that group untouched. */
    public static void save(List<String> apiKeys, Boolean ocrEnabled) throws IOException {
        Path file = userConfigPath();
        Files.createDirectories(file.getParent());
        Properties p = readProps(file);
        if (apiKeys != null) {
            // Remove legacy single-key field — we always write numbered slots now.
            p.remove("apiKey");
            for (int i = 0; i < MAX_API_KEYS; i++) {
                String slot = "apiKey" + (i + 1);
                String val =
                        i < apiKeys.size() && apiKeys.get(i) != null ? apiKeys.get(i).trim() : "";
                if (val.isEmpty()) p.remove(slot);
                else p.setProperty(slot, val);
            }
        }
        if (ocrEnabled != null) {
            p.setProperty("ocrEnabled", Boolean.toString(ocrEnabled));
        }
        try (OutputStream out = Files.newOutputStream(file)) {
            p.store(out, "Auto Fee Input — OCR settings (managed by app)");
        }
        log.info("Saved OCR settings to {}", file);
    }

    /** Backwards-compatible single-key save. */
    public static void save(String apiKey, Boolean ocrEnabled) throws IOException {
        List<String> list = null;
        if (apiKey != null) {
            list = new ArrayList<>();
            if (!apiKey.trim().isEmpty()) list.add(apiKey.trim());
        }
        save(list, ocrEnabled);
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

    /**
     * Read apiKey1..apiKeyN from the props, falling back to legacy apiKey when no slots are set.
     */
    private static List<String> readKeyList(Properties p) {
        List<String> out = new ArrayList<>();
        for (int i = 1; i <= MAX_API_KEYS; i++) {
            String v = p.getProperty("apiKey" + i);
            if (nonBlank(v)) out.add(v.trim());
        }
        if (out.isEmpty()) {
            String legacy = p.getProperty("apiKey");
            if (nonBlank(legacy)) out.add(legacy.trim());
        }
        return out;
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

    private static boolean pickBool(
            String envValue, String cwdValue, String homeValue, boolean fallback) {
        for (String v : new String[] {envValue, cwdValue, homeValue}) {
            if (nonBlank(v)) {
                String t = v.trim().toLowerCase();
                if (t.equals("true") || t.equals("1") || t.equals("yes") || t.equals("on"))
                    return true;
                if (t.equals("false") || t.equals("0") || t.equals("no") || t.equals("off"))
                    return false;
            }
        }
        return fallback;
    }
}
