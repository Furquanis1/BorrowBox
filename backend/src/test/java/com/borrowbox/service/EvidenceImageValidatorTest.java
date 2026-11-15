package com.borrowbox.service;

import com.borrowbox.exception.BusinessRuleViolationException;
import com.borrowbox.support.TestImages;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvidenceImageValidatorTest {

    private final EvidenceImageValidator validator = new EvidenceImageValidator(50_000_000L, 10_000);

    @Test
    void acceptsPngAndReturnsCanonicalContentType() {
        assertThat(validator.validate(TestImages.png())).isEqualTo("image/png");
    }

    @Test
    void acceptsJpegAndReturnsCanonicalContentType() {
        assertThat(validator.validate(TestImages.jpeg())).isEqualTo("image/jpeg");
    }

    @Test
    void rejectsTextHtmlSvgGifAndWebp() {
        assertThatThrownBy(() -> validator.validate("plain text".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("valid PNG or JPEG");
        assertThatThrownBy(() -> validator.validate("<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("valid PNG or JPEG");
        assertThatThrownBy(() -> validator.validate("<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("valid PNG or JPEG");
        assertThatThrownBy(() -> validator.validate("GIF89a-not-allowed".getBytes(StandardCharsets.US_ASCII)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("valid PNG or JPEG");
        assertThatThrownBy(() -> validator.validate("RIFF0000WEBPVP8 ".getBytes(StandardCharsets.US_ASCII)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("valid PNG or JPEG");
    }

    @Test
    void rejectsTruncatedPngAfterTheSignature() {
        assertThatThrownBy(() -> validator.validate(Arrays.copyOf(TestImages.png(), 8)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("valid PNG or JPEG");
    }

    @Test
    void rejectsTruncatedJpegAfterTheSignature() {
        assertThatThrownBy(() -> validator.validate(Arrays.copyOf(TestImages.jpeg(), 3)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("valid PNG or JPEG");
    }

    @Test
    void rejectsImagesAboveThePixelLimitBeforeDecoding() {
        EvidenceImageValidator tinyLimitValidator = new EvidenceImageValidator(1L, 10_000);

        assertThatThrownBy(() -> tinyLimitValidator.validate(TestImages.png()))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("dimensions are too large");
    }

    @Test
    void rejectsImagesAboveTheDimensionLimitBeforeDecoding() {
        EvidenceImageValidator tinyDimensionValidator = new EvidenceImageValidator(50_000_000L, 1);

        assertThatThrownBy(() -> tinyDimensionValidator.validate(TestImages.png()))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("dimensions are too large");
    }
}
