package com.btse.autofeeinput.controller;

import com.btse.autofeeinput.service.CaptchaUi;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.application.Platform;
import javafx.stage.Stage;

/**
 * One open captcha popup. The worker thread calls {@link #awaitCode()} per attempt; the FX-thread
 * CaptchaController feeds submissions/cancels in via {@link #onSend(String)} / {@link #onCancel()}.
 */
public class CaptchaSession implements CaptchaUi.Session {

    private static final String CANCEL_TOKEN = "\0CANCEL\0";

    private final Stage stage;
    private final CaptchaController controller;
    private final LinkedBlockingQueue<String> submissions = new LinkedBlockingQueue<>();
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public CaptchaSession(Stage stage, CaptchaController controller) {
        this.stage = stage;
        this.controller = controller;
        controller.bindSession(this);
    }

    void onSend(String code) {
        submissions.offer(code);
    }

    void onCancel() {
        submissions.offer(CANCEL_TOKEN);
    }

    @Override
    public String awaitCode() throws InterruptedException {
        String s = submissions.take();
        if (CANCEL_TOKEN.equals(s)) return null;
        return s;
    }

    @Override
    public void showError(String message, byte[] freshImage) {
        Platform.runLater(() -> controller.showError(message, freshImage));
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            Platform.runLater(
                    () -> {
                        if (stage.isShowing()) stage.close();
                    });
        }
    }

    /** Used by Stop button to cancel from outside the popup. */
    public void cancelExternally() {
        submissions.offer(CANCEL_TOKEN);
        close();
    }
}
