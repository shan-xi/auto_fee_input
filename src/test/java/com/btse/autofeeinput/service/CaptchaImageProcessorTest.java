package com.btse.autofeeinput.service;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaptchaImageProcessorTest {

    @Test
    void preprocess_producesPngWithOnlyZeroAndTwoFiftyFivePixels() throws Exception {
        BufferedImage src = new BufferedImage(20, 10, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = src.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 20, 10);
        g.setColor(Color.BLACK);
        g.fillRect(2, 2, 6, 4);
        g.dispose();

        ByteArrayOutputStream srcOut = new ByteArrayOutputStream();
        ImageIO.write(src, "png", srcOut);

        byte[] result = CaptchaImageProcessor.preprocess(srcOut.toByteArray());
        BufferedImage out = ImageIO.read(new ByteArrayInputStream(result));
        assertNotNull(out, "preprocess should return a decodable PNG");

        WritableRaster r = out.getRaster();
        for (int y = 0; y < out.getHeight(); y++) {
            for (int x = 0; x < out.getWidth(); x++) {
                int v = r.getSample(x, y, 0);
                assertTrue(v == 0 || v == 255, "pixel out of {0,255} at " + x + "," + y + " = " + v);
            }
        }
    }

    @Test
    void preprocess_upscalesByExpectedFactor() throws Exception {
        BufferedImage src = new BufferedImage(8, 4, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = src.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 8, 4);
        g.dispose();

        ByteArrayOutputStream srcOut = new ByteArrayOutputStream();
        ImageIO.write(src, "png", srcOut);

        byte[] result = CaptchaImageProcessor.preprocess(srcOut.toByteArray());
        BufferedImage out = ImageIO.read(new ByteArrayInputStream(result));

        assertEquals(8 * 3, out.getWidth());
        assertEquals(4 * 3, out.getHeight());
    }
}