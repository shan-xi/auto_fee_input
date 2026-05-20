package com.btse.autofeeinput.service;

import java.util.function.Supplier;

/**
 * Adapter so the background worker can drive the captcha popup without
 * knowing about JavaFX. One {@link Session} represents one open popup that
 * may accept several submit attempts before closing.
 */
public interface CaptchaUi {

    /** Open the popup and return a session bound to it. */
    Session open(String keyword, byte[] initialImage, Supplier<byte[]> refresher) throws InterruptedException;

    interface Session {
        /** Block until the user clicks Send. Returns the entered code, or null if cancelled. */
        String awaitCode() throws InterruptedException;

        /** Show an error message and replace the captcha image; popup stays open for retry. */
        void showError(String message, byte[] freshImage);

        /** Close the popup (idempotent). */
        void close();
    }
}
