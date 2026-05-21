package com.btse.autofeeinput.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OcrServiceTest {

    @Test
    void unescapeJson_handlesCommonEscapes() {
        assertEquals("a\nb", OcrService.unescapeJson("a\\nb"));
        assertEquals("a\tb", OcrService.unescapeJson("a\\tb"));
        assertEquals("a\"b", OcrService.unescapeJson("a\\\"b"));
        assertEquals("a\\b", OcrService.unescapeJson("a\\\\b"));
    }

    @Test
    void unescapeJson_decodesUnicodeEscape() {
        assertEquals("A", OcrService.unescapeJson("\\u0041"));
        assertEquals("漢", OcrService.unescapeJson("\\u6f22"));
    }

    @Test
    void unescapeJson_leavesUnknownEscapeAsLiteral() {
        assertEquals("z", OcrService.unescapeJson("\\z"));
    }

    @Test
    void unescapeJson_passesUnescapedThrough() {
        assertEquals("plain ABC 123", OcrService.unescapeJson("plain ABC 123"));
    }

    @Test
    void isRateLimit_recognisesCommonPhrasings() {
        org.junit.jupiter.api.Assertions.assertTrue(OcrService.isRateLimit(
                new java.io.IOException("HTTP 403: Rate Limit Exceeded")));
        org.junit.jupiter.api.Assertions.assertTrue(OcrService.isRateLimit(
                new java.io.IOException("API error: You may upload only a limited number of pages, daily limit reached")));
        org.junit.jupiter.api.Assertions.assertTrue(OcrService.isRateLimit(
                new java.io.IOException("HTTP 429: Too Many Requests")));
        org.junit.jupiter.api.Assertions.assertFalse(OcrService.isRateLimit(
                new java.io.IOException("HTTP 500: Server Error")));
        org.junit.jupiter.api.Assertions.assertFalse(OcrService.isRateLimit(null));
    }
}