package com.btse.autofeeinput.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * OCR config. Optional {@code ocr.properties} next to the jar overrides the
 * built-in defaults. Recognized keys:
 *   endpointUrl=<url>   - OCR.space-compatible REST endpoint
 *   apiKey=<key>        - leave unset to use the public demo key 'helloworld'
 */
public final class OcrConfig {

    private static final Logger log = LoggerFactory.getLogger(OcrConfig.class);

    public static final String DEFAULT_ENDPOINT_URL = "https://api.ocr.space/parse/image";
    /** OCR.space's published demo key — free, no signup, rate-limited. */
    public static final String DEFAULT_API_KEY = "helloworld";

    private final String endpointUrl;
    private final String apiKey;

    private OcrConfig(String endpointUrl, String apiKey) {
        this.endpointUrl = endpointUrl;
        this.apiKey = apiKey;
    }

    public String endpointUrl() { return endpointUrl; }
    public String apiKey() { return apiKey; }

    public static OcrConfig load() {
        Properties p = readProps();
        String url = p.getProperty("endpointUrl", DEFAULT_ENDPOINT_URL).trim();
        String key = p.getProperty("apiKey", DEFAULT_API_KEY).trim();
        return new OcrConfig(url, key);
    }

    private static Properties readProps() {
        Properties p = new Properties();
        File f = new File("ocr.properties");
        if (f.isFile()) {
            try (InputStream in = new FileInputStream(f)) {
                p.load(in);
                log.info("Loaded ocr.properties from {}", f.getAbsolutePath());
            } catch (IOException e) {
                log.warn("Failed to read {}: {}", f.getAbsolutePath(), e.getMessage());
            }
        }
        return p;
    }
}
