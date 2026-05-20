package com.btse.autofeeinput.service;

import com.btse.autofeeinput.model.SheetData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Drives the per-row API flow. Designed to be run on a background thread,
 * with Stop / Resume controlled by AtomicBoolean flags from the UI.
 *
 * Callbacks are invoked on the caller's thread; the main controller is
 * responsible for marshalling UI updates onto the FX thread.
 */
public class ProcessingService {

    private static final Logger log = LoggerFactory.getLogger(ProcessingService.class);

    public interface Listener {
        void onRowStart(int rowIndex, String keyword);
        void onRowDone(int rowIndex, String fee);
        void onRowError(int rowIndex, String keyword, String message);
        void log(String message);
    }

    private final SheetData data;
    private final int queryCol;
    private final int feeCol;
    private final CaptchaUi captcha;
    private final Listener listener;

    private final AtomicBoolean stopFlag = new AtomicBoolean(false);
    private final AtomicBoolean pauseFlag = new AtomicBoolean(false);
    private final AtomicBoolean completed = new AtomicBoolean(false);
    private final Object pauseLock = new Object();
    private volatile int currentRow = 0;

    public ProcessingService(SheetData data, int queryCol, int feeCol, CaptchaUi captcha, Listener listener) {
        this.data = data;
        this.queryCol = queryCol;
        this.feeCol = feeCol;
        this.captcha = captcha;
        this.listener = listener;
    }

    public void stop() {
        stopFlag.set(true);
        resume(); // wake up if paused
    }

    public void pause() {
        pauseFlag.set(true);
    }

    public void resume() {
        pauseFlag.set(false);
        synchronized (pauseLock) {
            pauseLock.notifyAll();
        }
    }

    public int getCurrentRow() {
        return currentRow;
    }

    public boolean isCompleted() {
        return completed.get();
    }

    public boolean isStopped() {
        return stopFlag.get();
    }

    /** Runs the full pipeline. Blocks until done, stopped, or interrupted. */
    public void run() {
        run(0);
    }

    public void run(int startRow) {
        for (int r = startRow; r < data.getRows().size(); r++) {
            currentRow = r;
            if (stopFlag.get() || Thread.currentThread().isInterrupted()) {
                listener.log("Processing stopped at row " + (r + 1));
                return;
            }
            waitIfPaused();

            String keyword = data.getCell(r, queryCol);
            if (keyword == null || keyword.trim().isEmpty()) {
                listener.log("Row " + (r + 1) + ": empty keyword, skipped");
                continue;
            }
            keyword = keyword.trim();

            listener.onRowStart(r, keyword);
            try {
                String fee = processRow(keyword);
                if (fee == null) {
                    listener.onRowError(r, keyword, "cancelled");
                } else {
                    listener.onRowDone(r, fee);
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                listener.log("Interrupted at row " + (r + 1));
                return;
            } catch (Exception e) {
                log.error("Row {} failed", r + 1, e);
                listener.onRowError(r, keyword, e.getMessage());
            }
        }
        completed.set(true);
        listener.log("All rows processed.");
    }

    private String processRow(String keyword) throws Exception {
        try (ApiClient client = new ApiClient()) {
            listener.log("[" + keyword + "] step1 home");
            ApiClient.FormState s1 = client.step1Home();
            checkStop();

            listener.log("[" + keyword + "] step2 search");
            ApiClient.FormState s2 = client.step2Search(keyword, s1);
            checkStop();

            listener.log("[" + keyword + "] step3 show more");
            ApiClient.FormState s3 = client.step3ShowMore(keyword, s2);
            checkStop();

            listener.log("[" + keyword + "] step4 captcha page");
            ApiClient.FormState state = client.step4CaptchaPage(keyword, s3);
            checkStop();

            listener.log("[" + keyword + "] step5 download captcha");
            byte[] image = client.step5DownloadCaptcha();
            checkStop();

            CaptchaUi.Session session = captcha.open(keyword, image, () -> {
                try {
                    return client.step5DownloadCaptcha();
                } catch (Exception e) {
                    return new byte[0];
                }
            });
            try {
                int attempt = 1;
                while (true) {
                    listener.log("[" + keyword + "] awaiting captcha (attempt " + attempt + ")");
                    String code = session.awaitCode();
                    // Global Stop must throw before the per-row null short-circuit,
                    // so the outer loop doesn't write ERR and currentRow stays here.
                    checkStop();
                    if (code == null) return null;

                    listener.log("[" + keyword + "] step6 submit captcha");
                    String html = client.step6Submit(keyword, code, state);
                    if (ApiClient.isCaptchaStillRequired(html)) {
                        listener.log("[" + keyword + "] wrong captcha, refreshing popup");
                        state = ApiClient.extractFormState(html);
                        byte[] fresh;
                        try {
                            fresh = client.step5DownloadCaptcha();
                        } catch (Exception e) {
                            fresh = null;
                        }
                        session.showError("驗證碼錯誤，請重新輸入", fresh);
                        attempt++;
                        continue;
                    }
                    String fee = ApiClient.parseTuitionFee(html);
                    listener.log("[" + keyword + "] fee=" + (fee.isEmpty() ? "<not found>" : fee));
                    return fee;
                }
            } finally {
                session.close();
            }
        }
    }

    private void checkStop() throws InterruptedException {
        if (stopFlag.get() || Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("stopped");
        }
        waitIfPaused();
    }

    private void waitIfPaused() {
        while (pauseFlag.get() && !stopFlag.get()) {
            synchronized (pauseLock) {
                try {
                    pauseLock.wait(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }
}
