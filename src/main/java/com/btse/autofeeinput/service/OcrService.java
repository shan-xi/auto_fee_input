package com.btse.autofeeinput.service;

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sends captcha images to an OCR.space-compatible REST endpoint and returns
 * the parsed text. Default endpoint + demo API key live in {@link OcrConfig}.
 * Calls run on the common ForkJoinPool — safe to invoke from any thread.
 */
public class OcrService {

    private static final Logger log = LoggerFactory.getLogger(OcrService.class);

    private static final Pattern PARSED_TEXT =
            Pattern.compile("\"ParsedText\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern IS_ERRORED =
            Pattern.compile("\"IsErroredOnProcessing\"\\s*:\\s*(true|false)");
    private static final Pattern ERR_MSG =
            Pattern.compile("\"ErrorMessage\"\\s*:\\s*(?:\\[\\s*\"((?:[^\"\\\\]|\\\\.)*)\"|\"((?:[^\"\\\\]|\\\\.)*)\")");

    private final String endpointUrl;
    private final String apiKey;
    private final Consumer<String> uiLog;
    private final CloseableHttpClient http;
    private final AtomicInteger seq = new AtomicInteger();

    public OcrService(OcrConfig cfg, Consumer<String> uiLog) {
        this.endpointUrl = cfg.endpointUrl();
        this.apiKey = cfg.apiKey();
        this.uiLog = uiLog == null ? s -> {} : uiLog;
        this.http = HttpClients.createDefault();
        announce("OCR ready (provider=ocr.space url=" + endpointUrl + " key=" + maskKey(apiKey) + ")");
    }

    /** Returns the raw recognized text. Caller cleans whitespace/punctuation. */
    public CompletableFuture<String> recognize(byte[] imageBytes, long timeoutMs) {
        if (imageBytes == null || imageBytes.length == 0) {
            CompletableFuture<String> f = new CompletableFuture<>();
            f.completeExceptionally(new IllegalArgumentException("empty image"));
            return f;
        }
        int id = seq.getAndIncrement();
        announce("OCR recognize id=" + id + " bytes=" + imageBytes.length);
        return CompletableFuture.supplyAsync(() -> {
            try {
                String text = call(id, imageBytes, timeoutMs);
                announce("OCR result id=" + id + " text=" + text.replace("\n", "\\n"));
                return text;
            } catch (Exception e) {
                announce("OCR error id=" + id + " : " + e.getMessage());
                throw new RuntimeException(e);
            }
        });
    }

    private String call(int id, byte[] imageBytes, long timeoutMs) throws IOException {
        RequestConfig cfg = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(Math.max(2000, timeoutMs / 4)))
                .setResponseTimeout(Timeout.ofMilliseconds(timeoutMs))
                .build();

        HttpPost post = new HttpPost(endpointUrl);
        post.setConfig(cfg);

        HttpEntity entity = MultipartEntityBuilder.create()
                .addTextBody("apikey", apiKey)
                .addTextBody("language", "eng")
                .addTextBody("isOverlayRequired", "false")
                .addTextBody("OCREngine", "2")
                .addBinaryBody("file", imageBytes, ContentType.IMAGE_PNG, "captcha.png")
                .build();
        post.setEntity(entity);

        try (CloseableHttpResponse resp = http.execute(post)) {
            int code = resp.getCode();
            String body = EntityUtils.toString(resp.getEntity(), StandardCharsets.UTF_8);
            if (code != 200) {
                throw new IOException("HTTP " + code + ": " + truncate(body));
            }
            Matcher mErr = IS_ERRORED.matcher(body);
            if (mErr.find() && "true".equals(mErr.group(1))) {
                Matcher mMsg = ERR_MSG.matcher(body);
                String msg = "(no msg)";
                if (mMsg.find()) {
                    msg = mMsg.group(1) != null ? mMsg.group(1) : mMsg.group(2);
                }
                throw new IOException("API error: " + msg);
            }
            Matcher mText = PARSED_TEXT.matcher(body);
            if (!mText.find()) {
                log.debug("OCR id={} body={}", id, truncate(body));
                return "";
            }
            return unescapeJson(mText.group(1));
        } catch (org.apache.hc.core5.http.ParseException e) {
            throw new IOException(e);
        }
    }

    private static String unescapeJson(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                switch (n) {
                    case 'n': b.append('\n'); break;
                    case 'r': b.append('\r'); break;
                    case 't': b.append('\t'); break;
                    case '"': b.append('"'); break;
                    case '\\': b.append('\\'); break;
                    case '/': b.append('/'); break;
                    case 'u':
                        if (i + 4 < s.length()) {
                            try {
                                b.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16));
                                i += 4;
                            } catch (NumberFormatException ignored) {
                                b.append(n);
                            }
                        } else {
                            b.append(n);
                        }
                        break;
                    default: b.append(n);
                }
            } else {
                b.append(c);
            }
        }
        return b.toString();
    }

    private static String maskKey(String k) {
        if (k == null || k.length() <= 4) return "***";
        return k.substring(0, 2) + "***" + k.substring(k.length() - 2);
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() > 300 ? s.substring(0, 300) + "..." : s;
    }

    private void announce(String msg) {
        log.info(msg);
        uiLog.accept(msg);
    }
}
