package com.btse.autofeeinput.service;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Cheap captcha-image preprocessing to improve OCR success rate:
 * grayscale -> upscale -> Otsu binarization. Inverts the result if the
 * majority of pixels turn out to be dark (i.e. the original was light-on-dark).
 *
 * Output is a PNG byte stream ready to upload.
 */
public final class CaptchaImageProcessor {

    private static final int UPSCALE_FACTOR = 3;

    private CaptchaImageProcessor() {}

    public static byte[] preprocess(byte[] input) throws IOException {
        BufferedImage src = ImageIO.read(new ByteArrayInputStream(input));
        if (src == null) throw new IOException("Unable to decode captcha image");

        BufferedImage gray = toGrayscale(src);
        BufferedImage upscaled = upscale(gray, UPSCALE_FACTOR);
        BufferedImage binary = otsuBinarize(upscaled);
        binary = ensureDarkTextOnLightBg(binary);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(binary, "png", out);
        return out.toByteArray();
    }

    private static BufferedImage toGrayscale(BufferedImage src) {
        BufferedImage gray = new BufferedImage(src.getWidth(), src.getHeight(),
                BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = gray.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return gray;
    }

    private static BufferedImage upscale(BufferedImage src, int factor) {
        int w = src.getWidth() * factor;
        int h = src.getHeight() * factor;
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = dst.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return dst;
    }

    /**
     * Otsu's method — picks the threshold that maximizes between-class variance.
     * Output is TYPE_BYTE_GRAY with pixels {0, 255} for downstream tools that
     * dislike BYTE_BINARY palettes.
     */
    private static BufferedImage otsuBinarize(BufferedImage gray) {
        int w = gray.getWidth();
        int h = gray.getHeight();
        int total = w * h;
        WritableRaster src = gray.getRaster();

        int[] histogram = new int[256];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                histogram[src.getSample(x, y, 0)]++;
            }
        }

        double sum = 0;
        for (int i = 0; i < 256; i++) sum += (double) i * histogram[i];

        double sumB = 0;
        int wB = 0;
        double maxVar = -1;
        int threshold = 127;
        for (int t = 0; t < 256; t++) {
            wB += histogram[t];
            if (wB == 0) continue;
            int wF = total - wB;
            if (wF == 0) break;
            sumB += (double) t * histogram[t];
            double mB = sumB / wB;
            double mF = (sum - sumB) / wF;
            double between = (double) wB * wF * (mB - mF) * (mB - mF);
            if (between > maxVar) {
                maxVar = between;
                threshold = t;
            }
        }

        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        WritableRaster dst = out.getRaster();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int v = src.getSample(x, y, 0);
                dst.setSample(x, y, 0, v > threshold ? 255 : 0);
            }
        }
        return out;
    }

    /**
     * If more pixels are dark (0) than light (255), invert — OCR engines
     * expect dark text on a light background.
     */
    private static BufferedImage ensureDarkTextOnLightBg(BufferedImage binary) {
        int w = binary.getWidth();
        int h = binary.getHeight();
        WritableRaster r = binary.getRaster();
        long dark = 0;
        long total = (long) w * h;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (r.getSample(x, y, 0) == 0) dark++;
            }
        }
        if (dark <= total / 2) return binary;

        BufferedImage inv = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        WritableRaster d = inv.getRaster();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                d.setSample(x, y, 0, 255 - r.getSample(x, y, 0));
            }
        }
        return inv;
    }
}
