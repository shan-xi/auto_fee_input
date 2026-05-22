package com.btse.autofeeinput.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class UpdateCheckerTest {

    @Test
    void isNewer_basicSemver() {
        assertTrue(UpdateChecker.isNewer("v1.3.1", "1.3.0"));
        assertTrue(UpdateChecker.isNewer("v2.0.0", "1.9.9"));
        assertTrue(UpdateChecker.isNewer("1.3.0", "v1.2.9"));
        assertFalse(UpdateChecker.isNewer("v1.3.0", "1.3.0"));
        assertFalse(UpdateChecker.isNewer("v1.2.9", "1.3.0"));
    }

    @Test
    void isNewer_differentSegmentLengths() {
        assertTrue(UpdateChecker.isNewer("v1.3", "1.2.9"));
        assertTrue(UpdateChecker.isNewer("v1.3.0.1", "1.3.0"));
        assertFalse(UpdateChecker.isNewer("v1.3", "1.3.0"));
    }

    @Test
    void isNewer_handlesPrereleaseTailAsBaseVersion() {
        // "1.3.0-rc1" normalises to 1.3.0; equal to v1.3.0, not newer.
        assertFalse(UpdateChecker.isNewer("v1.3.0-rc1", "1.3.0"));
    }

    @Test
    void isNewer_falseForDevOrEmpty() {
        assertFalse(UpdateChecker.isNewer("v1.3.0", "dev"));
        assertFalse(UpdateChecker.isNewer("v1.3.0", ""));
        assertFalse(UpdateChecker.isNewer("", "1.3.0"));
        assertFalse(UpdateChecker.isNewer(null, "1.3.0"));
    }

    @Test
    void parse_extractsTagAndFirstHtmlUrl() {
        String json =
                "{"
                        + "\"tag_name\":\"v1.3.1\","
                        + "\"html_url\":\"https://github.com/foo/bar/releases/tag/v1.3.1\","
                        + "\"assets\":[{\"html_url\":\"https://example/asset.exe\"}]"
                        + "}";
        Optional<UpdateChecker.Release> rel = UpdateChecker.parse(json);
        assertTrue(rel.isPresent());
        assertEquals("v1.3.1", rel.get().tagName);
        assertEquals("https://github.com/foo/bar/releases/tag/v1.3.1", rel.get().pageUrl);
    }

    @Test
    void parse_missingTagReturnsEmpty() {
        assertFalse(UpdateChecker.parse("{\"name\":\"no tag here\"}").isPresent());
    }
}
