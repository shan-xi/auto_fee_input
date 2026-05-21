package com.btse.autofeeinput.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OcrConfigTest {

    @Test
    void load_returnsBuiltInDefaultsWhenNothingConfigured() {
        OcrConfig cfg = OcrConfig.load();
        assertNotNull(cfg.endpointUrl());
        assertNotNull(cfg.apiKey());
        assertNotNull(cfg.apiKeySource());
    }

    @Test
    void userConfigPath_returnsAbsolutePath() {
        assertTrue(OcrConfig.userConfigPath().isAbsolute(),
                "user config path should be absolute on every platform");
    }
}