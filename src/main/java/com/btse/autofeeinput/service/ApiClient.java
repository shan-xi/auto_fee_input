package com.btse.autofeeinput.service;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.cookie.CookieStore;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.net.URLEncodedUtils;
import org.apache.hc.core5.http.ssl.TLS;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.ssl.TrustStrategy;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Per-row session client for ap.ece.moe.edu.tw fee lookup flow.
 *
 * Each new ApiClient owns its own CookieStore so ASP.NET_SessionId / TS01c66436
 * stay isolated between rows. Reuse one instance across all six steps of a row.
 */
public class ApiClient implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(ApiClient.class);

    private static final String BASE = "https://ap.ece.moe.edu.tw/webecems";
    private static final String SEARCH_URL = BASE + "/pubSearch.aspx";
    private static final String CAPTCHA_URL_PREFIX = BASE + "/ChgValidateCode.aspx";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124 Safari/537.36";
    private static final String VIEW_STATE_GENERATOR = "1AE246C8";

    private final CookieStore cookieStore = new BasicCookieStore();
    private final HttpClientContext context = HttpClientContext.create();
    private final CloseableHttpClient http;

    public ApiClient() {
        RequestConfig cfg = RequestConfig.custom()
                .setConnectTimeout(org.apache.hc.core5.util.Timeout.ofSeconds(20))
                .setResponseTimeout(org.apache.hc.core5.util.Timeout.ofSeconds(30))
                .setRedirectsEnabled(true)
                .build();
        PoolingHttpClientConnectionManager cm = PoolingHttpClientConnectionManagerBuilder.create()
                .setSSLSocketFactory(trustAllSslSocketFactory())
                .build();
        this.http = HttpClients.custom()
                .setConnectionManager(cm)
                .setDefaultCookieStore(cookieStore)
                .setDefaultRequestConfig(cfg)
                .setUserAgent(USER_AGENT)
                .build();
        this.context.setCookieStore(cookieStore);
    }

    /**
     * ap.ece.moe.edu.tw ships an incomplete cert chain that the default JVM
     * truststore can't validate. Single-host scraper — accept any cert.
     */
    private static SSLConnectionSocketFactory trustAllSslSocketFactory() {
        try {
            TrustStrategy trustAll = (chain, authType) -> true;
            javax.net.ssl.SSLContext ctx = SSLContextBuilder.create()
                    .loadTrustMaterial(null, trustAll)
                    .build();
            return SSLConnectionSocketFactoryBuilder.create()
                    .setSslContext(ctx)
                    .setTlsVersions(TLS.V_1_0, TLS.V_1_1, TLS.V_1_2, TLS.V_1_3)
                    .setHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to init trust-all SSL context", e);
        }
    }

    /** Holds the ASP.NET hidden fields parsed from a response page. */
    public static class FormState {
        public final String viewState;
        public final String eventValidation;
        public final String viewStateGenerator;

        public FormState(String viewState, String eventValidation, String viewStateGenerator) {
            this.viewState = viewState;
            this.eventValidation = eventValidation;
            this.viewStateGenerator = viewStateGenerator == null ? VIEW_STATE_GENERATOR : viewStateGenerator;
        }
    }

    /** Step 1: GET homepage to seed session cookies & first __VIEWSTATE. */
    public FormState step1Home() throws IOException {
        HttpGet req = new HttpGet(SEARCH_URL);
        return execAndParse(req, "step1");
    }

    /** Step 2: POST search with the keyword. */
    public FormState step2Search(String keyword, FormState prev) throws IOException {
        List<NameValuePair> form = new ArrayList<>();
        form.add(new BasicNameValuePair("txtKeyNameS", keyword));
        form.add(new BasicNameValuePair("__VIEWSTATE", prev.viewState));
        form.add(new BasicNameValuePair("__EVENTVALIDATION", prev.eventValidation));
        form.add(new BasicNameValuePair("__VIEWSTATEGENERATOR", prev.viewStateGenerator));
        form.add(new BasicNameValuePair("btnSearch", "查詢"));
        return postForm(SEARCH_URL, form, "step2");
    }

    /** Step 3: POST 顯示更多. */
    public FormState step3ShowMore(String keyword, FormState prev) throws IOException {
        List<NameValuePair> form = new ArrayList<>();
        form.add(new BasicNameValuePair("__VIEWSTATE", prev.viewState));
        form.add(new BasicNameValuePair("__EVENTVALIDATION", prev.eventValidation));
        form.add(new BasicNameValuePair("__VIEWSTATEGENERATOR", prev.viewStateGenerator));
        form.add(new BasicNameValuePair("__EVENTTARGET", "GridView1$ctl02$lbChgList"));
        form.add(new BasicNameValuePair("__EVENTARGUMENT", ""));
        form.add(new BasicNameValuePair("txtKeyNameS", keyword));
        form.add(new BasicNameValuePair("GridView1$ctl02$btnMore", " 顯示更多"));
        return postForm(SEARCH_URL, form, "step3");
    }

    /** Step 4: POST 收費明細驗證碼頁 (returns the captcha-bearing page). */
    public FormState step4CaptchaPage(String keyword, FormState prev) throws IOException {
        List<NameValuePair> form = new ArrayList<>();
        form.add(new BasicNameValuePair("__VIEWSTATE", prev.viewState));
        form.add(new BasicNameValuePair("__EVENTVALIDATION", prev.eventValidation));
        form.add(new BasicNameValuePair("__VIEWSTATEGENERATOR", prev.viewStateGenerator));
        form.add(new BasicNameValuePair("__EVENTTARGET", "GridView1$ctl02$lbChgList"));
        form.add(new BasicNameValuePair("__EVENTARGUMENT", ""));
        form.add(new BasicNameValuePair("txtKeyNameS", keyword));
        return postForm(SEARCH_URL, form, "step4");
    }

    /** Step 5: GET captcha image bytes. Reuses session cookies from step 4. */
    public byte[] step5DownloadCaptcha() throws IOException {
        // refresh value emulates a cache-buster — page uses Math.random-ish int
        String url = CAPTCHA_URL_PREFIX + "?refresh=" + (int) (Math.random() * 1_000_000_000);
        HttpGet req = new HttpGet(url);
        req.addHeader("Referer", SEARCH_URL);
        try (CloseableHttpResponse resp = http.execute(req, context)) {
            int code = resp.getCode();
            if (code != 200) {
                throw new IOException("step5 captcha download failed: HTTP " + code);
            }
            return EntityUtils.toByteArray(resp.getEntity());
        }
    }

    /**
     * Step 6: submit captcha. Returns the raw HTML of the result page —
     * caller passes it to {@link #parseTuitionFee(String)}.
     */
    public String step6Submit(String keyword, String captcha, FormState prev) throws IOException {
        List<NameValuePair> form = new ArrayList<>();
        form.add(new BasicNameValuePair("__VIEWSTATE", prev.viewState));
        form.add(new BasicNameValuePair("__EVENTVALIDATION", prev.eventValidation));
        form.add(new BasicNameValuePair("__VIEWSTATEGENERATOR", prev.viewStateGenerator));
        form.add(new BasicNameValuePair("__EVENTTARGET", ""));
        form.add(new BasicNameValuePair("__EVENTARGUMENT", ""));
        form.add(new BasicNameValuePair("txtKeyNameS", keyword));
        form.add(new BasicNameValuePair("txtVerify", captcha));
        form.add(new BasicNameValuePair("btnNext", "下一步"));
        form.add(new BasicNameValuePair("__VIEWSTATEENCRYPTED", ""));
        form.add(new BasicNameValuePair("__LASTFOCUS", ""));

        HttpPost req = new HttpPost(SEARCH_URL);
        req.setEntity(formEntity(form));
        req.addHeader("Referer", SEARCH_URL);
        try (CloseableHttpResponse resp = http.execute(req, context)) {
            int code = resp.getCode();
            String body = EntityUtils.toString(resp.getEntity(), StandardCharsets.UTF_8);
            if (code != 200) {
                throw new IOException("step6 failed: HTTP " + code);
            }
            return body;
        } catch (org.apache.hc.core5.http.ParseException e) {
            throw new IOException(e);
        }
    }

    /**
     * Extracts the first "學費" amount from the 收費明細 page.
     * Searches all tables for a row whose first cell starts with 學費 and
     * returns the next numeric-looking cell.
     */
    public static String parseTuitionFee(String html) {
        Document doc = Jsoup.parse(html);
        Elements tables = doc.select("table");
        for (Element table : tables) {
            Elements rows = table.select("tr");
            for (Element row : rows) {
                Elements cells = row.select("td, th");
                if (cells.isEmpty()) continue;
                String first = cells.first().text().trim();
                if (first.startsWith("學費") || first.equals("學費")) {
                    for (int i = 1; i < cells.size(); i++) {
                        String v = cells.get(i).text().trim();
                        if (!v.isEmpty() && v.matches("[\\d,]+(\\.[\\d]+)?")) {
                            return v.replace(",", "");
                        }
                    }
                    // Fallback: return whatever the second cell holds
                    if (cells.size() >= 2) return cells.get(1).text().trim();
                }
            }
        }
        return "";
    }

    private FormState postForm(String url, List<NameValuePair> form, String tag) throws IOException {
        HttpPost req = new HttpPost(url);
        req.setEntity(formEntity(form));
        req.addHeader("Referer", url);
        return execAndParse(req, tag);
    }

    private StringEntity formEntity(List<NameValuePair> form) {
        String body = URLEncodedUtils.format(form, StandardCharsets.UTF_8);
        return new StringEntity(body, ContentType.APPLICATION_FORM_URLENCODED.withCharset(StandardCharsets.UTF_8));
    }

    private FormState execAndParse(org.apache.hc.core5.http.ClassicHttpRequest req, String tag) throws IOException {
        try (CloseableHttpResponse resp = http.execute((org.apache.hc.client5.http.classic.methods.HttpUriRequestBase) req, context)) {
            int code = resp.getCode();
            String body = EntityUtils.toString(resp.getEntity(), StandardCharsets.UTF_8);
            if (code != 200) {
                throw new IOException(tag + " failed: HTTP " + code);
            }
            log.debug("{} response length={}", tag, body.length());
            return extractFormState(body);
        } catch (org.apache.hc.core5.http.ParseException e) {
            throw new IOException(e);
        }
    }

    public static FormState extractFormState(String html) {
        Document doc = Jsoup.parse(html);
        String vs = attr(doc, "#__VIEWSTATE");
        String ev = attr(doc, "#__EVENTVALIDATION");
        String vsg = attr(doc, "#__VIEWSTATEGENERATOR");
        return new FormState(vs, ev, vsg);
    }

    /**
     * True when the response page is still asking for a captcha — i.e. the
     * submitted code was wrong and the user should try again.
     */
    public static boolean isCaptchaStillRequired(String html) {
        Document doc = Jsoup.parse(html);
        return doc.selectFirst("#txtVerify, input[name=txtVerify]") != null;
    }

    private static String attr(Document doc, String selector) {
        Element e = doc.selectFirst(selector);
        return e == null ? "" : e.attr("value");
    }

    @Override
    public void close() throws IOException {
        http.close();
    }
}
