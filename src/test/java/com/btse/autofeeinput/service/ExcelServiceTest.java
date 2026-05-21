package com.btse.autofeeinput.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExcelServiceTest {

    @Test
    void detectCharset_returnsUtf8ForUtf8File(@TempDir Path dir) throws Exception {
        File f = dir.resolve("utf8.csv").toFile();
        Files.write(f.toPath(), "header,name\n1,abc\n".getBytes(StandardCharsets.UTF_8));
        assertEquals(StandardCharsets.UTF_8, ExcelService.detectCharset(f));
    }

    @Test
    void detectCharset_returnsUtf8ForBomFile(@TempDir Path dir) throws Exception {
        File f = dir.resolve("bom.csv").toFile();
        byte[] bom = { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };
        byte[] body = "header\n1\n".getBytes(StandardCharsets.UTF_8);
        byte[] all = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, all, 0, bom.length);
        System.arraycopy(body, 0, all, bom.length, body.length);
        Files.write(f.toPath(), all);
        assertEquals(StandardCharsets.UTF_8, ExcelService.detectCharset(f));
    }

    @Test
    void detectCharset_fallsBackToMs950ForBig5File(@TempDir Path dir) throws Exception {
        Charset ms950 = Charset.forName("MS950");
        File f = dir.resolve("big5.csv").toFile();
        Files.write(f.toPath(), "姓名,學費\n王小明,40000\n".getBytes(ms950));
        assertEquals(ms950, ExcelService.detectCharset(f));
    }
}