package com.btse.autofeeinput.service;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
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
import org.apache.hc.core5.http.ssl.TLS;
import org.apache.hc.core5.net.URLEncodedUtils;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.ssl.TrustStrategy;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Per-row session client for ap.ece.moe.edu.tw fee lookup flow.
 *
 * <p>Each new ApiClient owns its own CookieStore so ASP.NET_SessionId / TS01c66436 stay isolated
 * between rows. Reuse one instance across all six steps of a row.
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
        RequestConfig cfg =
                RequestConfig.custom()
                        .setConnectTimeout(org.apache.hc.core5.util.Timeout.ofSeconds(20))
                        .setResponseTimeout(org.apache.hc.core5.util.Timeout.ofSeconds(30))
                        .setRedirectsEnabled(true)
                        .build();
        PoolingHttpClientConnectionManager cm =
                PoolingHttpClientConnectionManagerBuilder.create()
                        .setSSLSocketFactory(trustAllSslSocketFactory())
                        .build();
        this.http =
                HttpClients.custom()
                        .setConnectionManager(cm)
                        .setDefaultCookieStore(cookieStore)
                        .setDefaultRequestConfig(cfg)
                        .setUserAgent(USER_AGENT)
                        .build();
        this.context.setCookieStore(cookieStore);
    }

    /**
     * ap.ece.moe.edu.tw ships an incomplete cert chain that the default JVM truststore can't
     * validate. Single-host scraper — accept any cert.
     */
    private static SSLConnectionSocketFactory trustAllSslSocketFactory() {
        log.warn(
                "ApiClient: cert validation and hostname verification are disabled "
                        + "for {} — connections are not MITM-protected",
                BASE);
        try {
            TrustStrategy trustAll = (chain, authType) -> true;
            javax.net.ssl.SSLContext ctx =
                    SSLContextBuilder.create().loadTrustMaterial(null, trustAll).build();
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
            this.viewStateGenerator =
                    viewStateGenerator == null ? VIEW_STATE_GENERATOR : viewStateGenerator;
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
     * Step 6: submit captcha. Returns the raw HTML of the result page — caller passes it to {@link
     * #parseTuitionFee(String)}.
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

    private static final List<String> FEE_ROW_LABELS = List.of("上學期計6個月", "全學期總收費", "總計");

    /**
     * Extracts the 學費 amount from the 收費明細 page. Scans every table for the first row labelled 上學期計
     * 6 個月, 全學期總收費, or 總計, then reads its 半日班 / 全日班 values. Output formats: both classes present →
     * "學費 半日班X/全日班Y" only 全日班 → "學費 全日班Y" only 半日班 → "學費 半日班X"
     */
    public static String parseTuitionFee(String html) {
        Document doc = Jsoup.parse(html);
        for (Element table : doc.select("table")) {
            // Only process leaf tables — layout tables that wrap others would
            // pollute findClassColumnRanges / label matching with nested text.
            if (hasNestedTable(table)) continue;
            String result = extractFromTable(table);
            if (!result.isEmpty()) return result;
        }
        if (log.isInfoEnabled()) {
            log.info("parseTuitionFee: no match. Row labels seen: {}", collectRowLabels(doc));
        }
        return "";
    }

    private static boolean hasNestedTable(Element table) {
        for (Element desc : table.getAllElements()) {
            if (desc != table && "table".equals(desc.tagName())) return true;
        }
        return false;
    }

    private static List<String> collectRowLabels(Document doc) {
        List<String> labels = new ArrayList<>();
        for (Element row : doc.select("tr")) {
            Elements cells = row.select("td, th");
            if (cells.isEmpty()) continue;
            String s = squish(cells.first().text());
            if (!s.isEmpty()) labels.add(s);
            if (labels.size() >= 40) break;
        }
        return labels;
    }

    private static String extractFromTable(Element table) {
        Elements rows = table.select("tr");
        if (rows.isEmpty()) return "";

        int[] halfRange = {-1, -1}; // [start, endExclusive]
        int[] fullRange = {-1, -1};
        findClassColumnRanges(rows, halfRange, fullRange);
        // Only fee tables expose 半日班 / 全日班 headers — skip layout tables.
        if (halfRange[0] < 0 && fullRange[0] < 0) return "";

        for (Element row : rows) {
            Elements cells = row.select("td, th");
            if (cells.isEmpty()) continue;
            String label = squish(cells.first().text());
            if (!matchesFeeLabel(label)) continue;

            String half = firstAmountInRange(cells, halfRange);
            String full = firstAmountInRange(cells, fullRange);
            boolean hasHalf = hasAmount(half);
            boolean hasFull = hasAmount(full);

            if (log.isInfoEnabled()) {
                log.info(
                        "parseTuitionFee: matched row '{}' half='{}' full='{}'", label, half, full);
            }
            if (hasHalf && hasFull) return "學費 半日班" + half + "/全日班" + full;
            if (hasFull) return "學費 全日班" + full;
            if (hasHalf) return "學費 半日班" + half;
        }
        return "";
    }

    private static boolean matchesFeeLabel(String squished) {
        for (String t : FEE_ROW_LABELS) {
            if (squished.contains(t)) return true;
        }
        return false;
    }

    /**
     * Walks header rows (top-down) and resolves the column ranges occupied by 半日班 and 全日班 headers.
     * Honours colspan so multi-column class headers still produce the right [start, end) range.
     */
    private static void findClassColumnRanges(Elements rows, int[] halfOut, int[] fullOut) {
        for (Element row : rows) {
            Elements cells = row.select("td, th");
            int col = 0;
            for (Element cell : cells) {
                int span = parseSpan(cell.attr("colspan"));
                String text = squish(cell.text());
                if (halfOut[0] < 0 && text.contains("半日班")) {
                    halfOut[0] = col;
                    halfOut[1] = col + span;
                }
                if (fullOut[0] < 0 && text.contains("全日班")) {
                    fullOut[0] = col;
                    fullOut[1] = col + span;
                }
                col += span;
            }
            if (halfOut[0] >= 0 && fullOut[0] >= 0) return;
        }
    }

    private static int parseSpan(String raw) {
        if (raw == null || raw.isEmpty()) return 1;
        try {
            int n = Integer.parseInt(raw.trim());
            return n < 1 ? 1 : n;
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    /** First numeric cell whose column index falls inside [start, end). */
    private static String firstAmountInRange(Elements cells, int[] range) {
        if (range[0] < 0) return "";
        int col = 0;
        for (Element cell : cells) {
            int span = parseSpan(cell.attr("colspan"));
            int next = col + span;
            if (col >= range[0] && col < range[1]) {
                String v = squish(cell.text());
                if (hasAmount(v)) return v;
            }
            col = next;
            if (col >= range[1]) break;
        }
        return "";
    }

    /** True when the cell contains at least one digit. */
    private static boolean hasAmount(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isDigit(s.charAt(i))) return true;
        }
        return false;
    }

    /** Collapse all whitespace (incl. NBSP) and trim. */
    private static String squish(String s) {
        if (s == null) return "";
        return s.replace(' ', ' ').replaceAll("\\s+", "").trim();
    }

    private FormState postForm(String url, List<NameValuePair> form, String tag)
            throws IOException {
        HttpPost req = new HttpPost(url);
        req.setEntity(formEntity(form));
        req.addHeader("Referer", url);
        return execAndParse(req, tag);
    }

    private StringEntity formEntity(List<NameValuePair> form) {
        String body = URLEncodedUtils.format(form, StandardCharsets.UTF_8);
        return new StringEntity(
                body, ContentType.APPLICATION_FORM_URLENCODED.withCharset(StandardCharsets.UTF_8));
    }

    private FormState execAndParse(org.apache.hc.core5.http.ClassicHttpRequest req, String tag)
            throws IOException {
        try (CloseableHttpResponse resp =
                http.execute(
                        (org.apache.hc.client5.http.classic.methods.HttpUriRequestBase) req,
                        context)) {
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
     * True when the response page is still asking for a captcha — i.e. the submitted code was wrong
     * and the user should try again.
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
