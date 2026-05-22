package com.btse.autofeeinput.service;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Polls the GitHub Releases API for the latest published release and compares its tag with the
 * running app's version. Stateless and dependency-free beyond the project's existing httpclient5
 * dep.
 */
public final class UpdateChecker {

    private static final Logger log = LoggerFactory.getLogger(UpdateChecker.class);

    private static final String DEFAULT_API_URL =
            "https://api.github.com/repos/shan-xi/auto_fee_input/releases/latest";
    public static final String RELEASES_PAGE_URL =
            "https://github.com/shan-xi/auto_fee_input/releases";

    private static final Pattern TAG_NAME =
            Pattern.compile("\"tag_name\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern HTML_URL =
            Pattern.compile("\"html_url\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    private final String apiUrl;

    public UpdateChecker() {
        this(DEFAULT_API_URL);
    }

    public UpdateChecker(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    public static final class Release {
        public final String tagName; // e.g. "v1.3.1"
        public final String pageUrl; // GitHub release page URL

        public Release(String tagName, String pageUrl) {
            this.tagName = tagName;
            this.pageUrl = pageUrl;
        }
    }

    /** Hits the GitHub API. Returns empty on any failure — never throws. */
    public Optional<Release> fetchLatest() {
        RequestConfig cfg =
                RequestConfig.custom()
                        .setConnectTimeout(Timeout.ofSeconds(5))
                        .setResponseTimeout(Timeout.ofSeconds(10))
                        .build();
        try (CloseableHttpClient http =
                HttpClients.custom()
                        .setDefaultRequestConfig(cfg)
                        .setUserAgent("AutoFeeInput-UpdateChecker")
                        .build()) {
            HttpGet req = new HttpGet(apiUrl);
            req.addHeader("Accept", "application/vnd.github+json");
            try (CloseableHttpResponse resp = http.execute(req)) {
                int code = resp.getCode();
                if (code != 200) {
                    log.info("Update check: HTTP {}", code);
                    return Optional.empty();
                }
                String body = EntityUtils.toString(resp.getEntity(), StandardCharsets.UTF_8);
                return parse(body);
            }
        } catch (Exception e) {
            log.info("Update check failed: {}", e.toString());
            return Optional.empty();
        }
    }

    static Optional<Release> parse(String json) {
        Matcher mTag = TAG_NAME.matcher(json);
        if (!mTag.find()) return Optional.empty();
        String tag = mTag.group(1);
        // First html_url in the response is the release page (assets follow).
        Matcher mUrl = HTML_URL.matcher(json);
        String url = mUrl.find() ? mUrl.group(1) : RELEASES_PAGE_URL;
        if (tag.isEmpty()) return Optional.empty();
        return Optional.of(new Release(tag, url));
    }

    /**
     * Returns true when {@code latestTag} is strictly newer than {@code current}. Strips a leading
     * "v"; compares each dot-separated segment numerically when possible, falling back to lexical
     * compare. Non-numeric tail (e.g. "-rc1") is treated as older than the same prefix without a
     * tail.
     */
    public static boolean isNewer(String latestTag, String current) {
        if (latestTag == null || latestTag.isEmpty()) return false;
        if (current == null || current.isEmpty() || "dev".equals(current)) return false;
        int[] l = normalize(latestTag);
        int[] c = normalize(current);
        int n = Math.max(l.length, c.length);
        for (int i = 0; i < n; i++) {
            int a = i < l.length ? l[i] : 0;
            int b = i < c.length ? c[i] : 0;
            if (a != b) return a > b;
        }
        return false;
    }

    private static int[] normalize(String raw) {
        String v = raw.trim();
        if (v.startsWith("v") || v.startsWith("V")) v = v.substring(1);
        // Cut at first non-version char (e.g. -rc1 or +build) so "1.3.0-rc1"
        // compares as 1.3.0 — we lose pre-release ordering, fine for our use.
        int end = v.length();
        for (int i = 0; i < v.length(); i++) {
            char ch = v.charAt(i);
            if (ch != '.' && !Character.isDigit(ch)) {
                end = i;
                break;
            }
        }
        v = v.substring(0, end);
        if (v.isEmpty()) return new int[0];
        String[] parts = v.split("\\.");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                out[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                out[i] = 0;
            }
        }
        return out;
    }
}
