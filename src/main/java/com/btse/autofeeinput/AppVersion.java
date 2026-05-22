package com.btse.autofeeinput;

import java.io.InputStream;
import java.util.Properties;

/** Resolves the build-time version stamped into META-INF/auto-fee-input-version.properties. */
public final class AppVersion {

    private static final String RESOURCE = "/META-INF/auto-fee-input-version.properties";
    private static final String VALUE = load();

    private AppVersion() {}

    /** "1.3.0" in a packaged build, "dev" when running from sources without resource filtering. */
    public static String value() {
        return VALUE;
    }

    private static String load() {
        try (InputStream in = AppVersion.class.getResourceAsStream(RESOURCE)) {
            if (in == null) return "dev";
            Properties p = new Properties();
            p.load(in);
            String v = p.getProperty("version", "").trim();
            // Unfiltered placeholder slipped through — treat as dev.
            if (v.isEmpty() || v.startsWith("${")) return "dev";
            return v;
        } catch (Exception e) {
            return "dev";
        }
    }
}
