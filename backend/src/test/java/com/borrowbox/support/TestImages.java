package com.borrowbox.support;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class TestImages {

    private TestImages() {
    }

    public static byte[] png(byte... marker) {
        return render("png", marker);
    }

    public static byte[] jpeg(byte... marker) {
        return render("jpeg", marker);
    }

    private static byte[] render(String format, byte[] marker) {
        int seed = marker.length == 0 ? 0 : marker[0] & 0xFF;
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, (seed << 16) | 0x2020);
        image.setRGB(1, 0, (seed << 8) | 0x3030);
        image.setRGB(0, 1, seed | 0x404040);
        image.setRGB(1, 1, 0x505050 | seed);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, format, output)) {
                throw new IllegalStateException("No test image writer for " + format);
            }
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Could not create test image", ex);
        }
    }
}
