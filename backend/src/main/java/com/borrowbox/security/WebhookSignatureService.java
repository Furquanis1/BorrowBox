package com.borrowbox.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Signs and verifies webhook request bodies with HMAC-SHA256.
 *
 * <p>The canonical signed bytes are exactly:</p>
 * <pre>
 * UTF-8(eventId) + LF + UTF-8(timestamp) + LF + rawBodyBytes
 * </pre>
 *
 * <p>The signature header value is {@code sha256=} followed by the lowercase
 * hexadecimal HMAC. Receivers must read the event ID from
 * {@value #EVENT_ID_HEADER}, the timestamp from {@value #TIMESTAMP_HEADER},
 * and the exact raw request body, then reproduce the canonical bytes above.
 * The timestamp is Unix epoch seconds.</p>
 */
@Component
public class WebhookSignatureService {

    public static final String SIGNATURE_HEADER = "X-Webhook-Signature";
    public static final String TIMESTAMP_HEADER = "X-Webhook-Timestamp";
    public static final String EVENT_ID_HEADER = "X-Webhook-Event-Id";
    public static final String SIGNATURE_PREFIX = "sha256=";

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    public String sign(String eventId, String timestamp, byte[] payload, String secret) {
        try {
            Mac mac = newMac(secret);
            updateCanonical(mac, eventId, timestamp, payload);
            return SIGNATURE_PREFIX + HexFormat.of().formatHex(mac.doFinal());
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to sign webhook payload", ex);
        }
    }

    public boolean verify(String signature,
                          String eventId,
                          String timestamp,
                          byte[] payload,
                          String secret) {
        if (signature == null || !signature.startsWith(SIGNATURE_PREFIX)) {
            return false;
        }
        try {
            byte[] supplied = HexFormat.of().parseHex(
                    signature.substring(SIGNATURE_PREFIX.length()));
            Mac mac = newMac(secret);
            updateCanonical(mac, eventId, timestamp, payload);
            byte[] expected = mac.doFinal();
            return MessageDigest.isEqual(supplied, expected);
        } catch (Exception ex) {
            return false;
        }
    }

    private static Mac newMac(String secret) throws Exception {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("Webhook signing secret is required");
        }
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
        return mac;
    }

    private static void updateCanonical(Mac mac, String eventId, String timestamp, byte[] payload) {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("Webhook event ID is required");
        }
        if (timestamp == null || timestamp.isBlank()) {
            throw new IllegalArgumentException("Webhook timestamp is required");
        }
        mac.update(eventId.getBytes(StandardCharsets.UTF_8));
        mac.update((byte) '\n');
        mac.update(timestamp.getBytes(StandardCharsets.UTF_8));
        mac.update((byte) '\n');
        if (payload != null) {
            mac.update(payload);
        }
    }
}
