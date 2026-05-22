package com.btse.autofeeinput.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Persists captcha image + verified code pairs so they can later be used to train a private OCR
 * model. Lives next to {@link OcrConfig#userConfigPath()} under {@code training_data/}.
 *
 * <p>Layout:
 *
 * <pre>
 *   training_data/
 *     all_data            - append-only manifest, one line per image: "images/<file> <code>"
 *     images/
 *       <code>.png        - captcha image bytes as returned by the server
 *       <code>_1.png      - duplicate code → numeric suffix
 * </pre>
 *
 * <p>All operations are best-effort: failures are logged and swallowed so they never block the
 * fee-extraction flow.
 */
public final class TrainingDataStore {

    private static final Logger log = LoggerFactory.getLogger(TrainingDataStore.class);
    private static final String DIR_NAME = "training_data";
    private static final String IMAGES_SUBDIR = "images";
    private static final String MANIFEST_NAME = "all_data";

    private TrainingDataStore() {}

    /**
     * Save one (image, code) pair. Returns the on-disk filename written (e.g. {@code "asdfg.png"}),
     * or empty string when nothing was written (bad inputs, IO error).
     */
    public static synchronized String save(byte[] image, String code) {
        if (image == null || image.length == 0) return "";
        String safe = sanitizeCode(code);
        if (safe.isEmpty()) return "";
        try {
            Path baseDir = directory();
            Path imagesDir = baseDir.resolve(IMAGES_SUBDIR);
            Files.createDirectories(imagesDir);
            String ext = detectExtension(image);
            Path target = uniquePath(imagesDir, safe, ext);
            Files.write(target, image, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            // Manifest path is relative to training_data/ so it stays portable.
            String relPath = IMAGES_SUBDIR + "/" + target.getFileName().toString();
            appendManifest(baseDir, relPath, safe);
            log.info("Saved training sample {} (code={})", relPath, safe);
            return relPath;
        } catch (Exception e) {
            log.warn("Failed to save training sample for code={}: {}", safe, e.toString());
            return "";
        }
    }

    /** Resolves the training-data directory; same parent as {@link OcrConfig#userConfigPath()}. */
    public static Path directory() {
        String home = System.getProperty("user.home", ".");
        String os = System.getProperty("os.name", "").toLowerCase();
        Path base;
        if (os.contains("mac")) {
            base = Paths.get(home, "Library", "Application Support", "AutoFeeInput");
        } else if (os.contains("win")) {
            String appdata = System.getenv("APPDATA");
            base =
                    appdata != null && !appdata.isEmpty()
                            ? Paths.get(appdata, "AutoFeeInput")
                            : Paths.get(home, ".auto-fee-input");
        } else {
            String xdg = System.getenv("XDG_CONFIG_HOME");
            base =
                    xdg != null && !xdg.isEmpty()
                            ? Paths.get(xdg, "auto-fee-input")
                            : Paths.get(home, ".config", "auto-fee-input");
        }
        return base.resolve(DIR_NAME);
    }

    static Path uniquePath(Path dir, String stem, String ext) {
        Path candidate = dir.resolve(stem + "." + ext);
        if (!Files.exists(candidate)) return candidate;
        for (int i = 1; i < 100_000; i++) {
            candidate = dir.resolve(stem + "_" + i + "." + ext);
            if (!Files.exists(candidate)) return candidate;
        }
        // Pathological: fall back to a timestamp-suffixed name rather than overwriting.
        return dir.resolve(stem + "_" + System.currentTimeMillis() + "." + ext);
    }

    private static void appendManifest(Path dir, String filename, String code) throws IOException {
        Path manifest = dir.resolve(MANIFEST_NAME);
        String line = filename + " " + code + System.lineSeparator();
        OpenOption[] opts = {
            StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND
        };
        Files.write(manifest, line.getBytes(StandardCharsets.UTF_8), opts);
    }

    /** PNG / JPEG / GIF / BMP magic-byte detection; defaults to {@code png}. */
    static String detectExtension(byte[] data) {
        if (data == null || data.length < 4) return "png";
        if (data[0] == (byte) 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G')
            return "png";
        if (data[0] == (byte) 0xFF && data[1] == (byte) 0xD8) return "jpg";
        if (data[0] == 'G' && data[1] == 'I' && data[2] == 'F') return "gif";
        if (data[0] == 'B' && data[1] == 'M') return "bmp";
        return "png";
    }

    /**
     * Keep [A-Za-z0-9_-] only so the code is a safe filename on every OS. Empty after sanitising →
     * empty return (skip save).
     */
    static String sanitizeCode(String code) {
        if (code == null) return "";
        StringBuilder b = new StringBuilder(code.length());
        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '_' || c == '-') {
                b.append(c);
            }
        }
        return b.toString();
    }
}