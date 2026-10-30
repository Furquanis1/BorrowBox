package com.borrowbox.security;

import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookSignatureServiceTest {

    private static final String SECRET = "test-webhook-secret-0123456789abcdef";
    private static final String EVENT_ID = UUID.randomUUID().toString();
    private static final String TIMESTAMP = "1730000000";
    private static final byte[] BODY = "{\"transactionId\":7}".getBytes(StandardCharsets.UTF_8);

    private final WebhookSignatureService service = new WebhookSignatureService();

    @Test
    void validSignatureIsReproducedIndependently() throws Exception {
        String signature = service.sign(EVENT_ID, TIMESTAMP, BODY, SECRET);

        assertThat(signature).startsWith(WebhookSignatureService.SIGNATURE_PREFIX);
        String expected = WebhookSignatureService.SIGNATURE_PREFIX + HexFormat.of().formatHex(
                computeHmac(EVENT_ID, TIMESTAMP, BODY, SECRET));
        assertThat(signature).isEqualTo(expected);
        assertThat(service.verify(signature, EVENT_ID, TIMESTAMP, BODY, SECRET)).isTrue();
    }

    @Test
    void modifiedPayloadFailsVerification() {
        String signature = service.sign(EVENT_ID, TIMESTAMP, BODY, SECRET);
        byte[] tampered = "{\"transactionId\":8}".getBytes(StandardCharsets.UTF_8);

        assertThat(service.verify(signature, EVENT_ID, TIMESTAMP, tampered, SECRET)).isFalse();
    }

    @Test
    void modifiedTimestampFailsVerification() {
        String signature = service.sign(EVENT_ID, TIMESTAMP, BODY, SECRET);

        assertThat(service.verify(signature, EVENT_ID, "1730000001", BODY, SECRET)).isFalse();
    }

    @Test
    void modifiedEventIdFailsVerification() {
        String signature = service.sign(EVENT_ID, TIMESTAMP, BODY, SECRET);

        assertThat(service.verify(signature, UUID.randomUUID().toString(), TIMESTAMP, BODY, SECRET)).isFalse();
    }

    @Test
    void malformedSignatureFailsVerification() {
        assertThat(service.verify("not-a-signature", EVENT_ID, TIMESTAMP, BODY, SECRET)).isFalse();
        assertThat(service.verify("sha256=zzzz", EVENT_ID, TIMESTAMP, BODY, SECRET)).isFalse();
        assertThat(service.verify(null, EVENT_ID, TIMESTAMP, BODY, SECRET)).isFalse();
    }

    @Test
    void headersAreDocumentedAndNonBlank() {
        assertThat(WebhookSignatureService.SIGNATURE_HEADER).isNotBlank();
        assertThat(WebhookSignatureService.TIMESTAMP_HEADER).isNotBlank();
        assertThat(WebhookSignatureService.EVENT_ID_HEADER).isNotBlank();
    }

    private static byte[] computeHmac(String eventId, String timestamp, byte[] body, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        mac.update(eventId.getBytes(StandardCharsets.UTF_8));
        mac.update((byte) '\n');
        mac.update(timestamp.getBytes(StandardCharsets.UTF_8));
        mac.update((byte) '\n');
        mac.update(body);
        return mac.doFinal();
    }
}
