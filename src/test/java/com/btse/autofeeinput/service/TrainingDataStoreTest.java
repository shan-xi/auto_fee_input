package com.btse.autofeeinput.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TrainingDataStoreTest {

    @Test
    void detectExtension_recognisesCommonFormats() {
        assertEquals("png", TrainingDataStore.detectExtension(new byte[] {
                (byte) 0x89, 'P', 'N', 'G', 0, 0
        }));
        assertEquals("jpg", TrainingDataStore.detectExtension(new byte[] {
                (byte) 0xFF, (byte) 0xD8, 0, 0
        }));
        assertEquals("gif", TrainingDataStore.detectExtension(new byte[] {'G', 'I', 'F', '8'}));
        assertEquals("bmp", TrainingDataStore.detectExtension(new byte[] {'B', 'M', 0, 0}));
        // Unknown / too-short → png fallback.
        assertEquals("png", TrainingDataStore.detectExtension(new byte[] {1, 2, 3, 4}));
        assertEquals("png", TrainingDataStore.detectExtension(new byte[0]));
    }

    @Test
    void sanitizeCode_keepsAlphaNumDashUnderscore() {
        assertEquals("asdfg", TrainingDataStore.sanitizeCode("asdfg"));
        assertEquals("ABC123", TrainingDataStore.sanitizeCode("ABC 123"));
        assertEquals("a_b-c", TrainingDataStore.sanitizeCode("a_b-c"));
        // Path traversal / shell chars stripped.
        assertEquals("abcd", TrainingDataStore.sanitizeCode("../a/b\\c?d"));
        assertEquals("", TrainingDataStore.sanitizeCode(""));
        assertEquals("", TrainingDataStore.sanitizeCode(null));
    }

    @Test
    void uniquePath_addsNumericSuffixOnCollision(@TempDir Path dir) throws Exception {
        // Existing: asdfg.png  → next picks asdfg_1.png.
        Files.write(dir.resolve("asdfg.png"), new byte[] {1});
        Path p1 = TrainingDataStore.uniquePath(dir, "asdfg", "png");
        assertEquals("asdfg_1.png", p1.getFileName().toString());

        Files.write(p1, new byte[] {1});
        Path p2 = TrainingDataStore.uniquePath(dir, "asdfg", "png");
        assertEquals("asdfg_2.png", p2.getFileName().toString());
    }

    @Test
    void uniquePath_returnsBaseWhenNoCollision(@TempDir Path dir) {
        Path p = TrainingDataStore.uniquePath(dir, "fresh", "png");
        assertEquals("fresh.png", p.getFileName().toString());
        assertTrue(p.startsWith(dir));
    }
}