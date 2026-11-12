package com.borrowbox.service;

import com.borrowbox.exception.BusinessRuleViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;

@Service
public class EvidenceImageValidator {

    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    private static final String PNG_CONTENT_TYPE = "image/png";
    private static final String JPEG_CONTENT_TYPE = "image/jpeg";
    private static final String INVALID_IMAGE_MESSAGE = "Evidence must be a valid PNG or JPEG image";
    private static final String DIMENSION_MESSAGE = "Evidence image dimensions are too large";

    private final long maxPixels;
    private final int maxDimension;

    public EvidenceImageValidator(
            @Value("${borrowbox.evidence.max-pixels:50000000}") long maxPixels,
            @Value("${borrowbox.evidence.max-dimension:10000}") int maxDimension) {
        this.maxPixels = maxPixels;
        this.maxDimension = maxDimension;
    }

    public String validate(byte[] content) {
        String expectedContentType = detectContentType(content);
        if (expectedContentType == null) {
            throw new BusinessRuleViolationException(INVALID_IMAGE_MESSAGE);
        }

        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            if (input == null) {
                throw new BusinessRuleViolationException(INVALID_IMAGE_MESSAGE);
            }

            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new BusinessRuleViolationException(INVALID_IMAGE_MESSAGE);
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String actualContentType = contentTypeForFormat(reader.getFormatName());
                if (!expectedContentType.equals(actualContentType)) {
                    throw new BusinessRuleViolationException(INVALID_IMAGE_MESSAGE);
                }

                long width = reader.getWidth(0);
                long height = reader.getHeight(0);
                if (width <= 0 || height <= 0
                        || width > maxDimension || height > maxDimension
                        || width > maxPixels / height) {
                    throw new BusinessRuleViolationException(DIMENSION_MESSAGE);
                }

                BufferedImage image = reader.read(0);
                if (image == null) {
                    throw new BusinessRuleViolationException(INVALID_IMAGE_MESSAGE);
                }
                return expectedContentType;
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException ex) {
            if (ex instanceof BusinessRuleViolationException businessRuleViolationException) {
                throw businessRuleViolationException;
            }
            throw new BusinessRuleViolationException(INVALID_IMAGE_MESSAGE);
        }
    }

    private String detectContentType(byte[] content) {
        if (content == null || content.length < 8) {
            return null;
        }
        if (startsWithPngSignature(content)) {
            return PNG_CONTENT_TYPE;
        }
        if ((content[0] & 0xFF) == 0xFF
                && (content[1] & 0xFF) == 0xD8
                && (content[2] & 0xFF) == 0xFF) {
            return JPEG_CONTENT_TYPE;
        }
        return null;
    }

    private boolean startsWithPngSignature(byte[] content) {
        for (int i = 0; i < PNG_SIGNATURE.length; i++) {
            if (content[i] != PNG_SIGNATURE[i]) {
                return false;
            }
        }
        return true;
    }

    private String contentTypeForFormat(String formatName) {
        if ("png".equalsIgnoreCase(formatName)) {
            return PNG_CONTENT_TYPE;
        }
        if ("jpeg".equalsIgnoreCase(formatName) || "jpg".equalsIgnoreCase(formatName)) {
            return JPEG_CONTENT_TYPE;
        }
        return null;
    }
}
