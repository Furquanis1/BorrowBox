package com.borrowbox.security;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Validates outbound webhook destinations and supplies the same validated
 * addresses to the HTTP client that will open the connection.
 *
 * <p>The HTTP client uses {@link #resolveForConnection(String)} as its DNS
 * resolver, so the addresses that were checked are the addresses used for the
 * connection. This closes the usual validation-to-connection DNS rebinding
 * gap.</p>
 */
@Component
public class WebhookUrlValidator {

    @FunctionalInterface
    public interface HostResolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    private final HostResolver hostResolver;

    public WebhookUrlValidator() {
        this(InetAddress::getAllByName);
    }

    public WebhookUrlValidator(HostResolver hostResolver) {
        this.hostResolver = hostResolver;
    }

    public URI validate(String url) {
        URI uri = parse(url);
        String host = requireHost(uri);
        InetAddress[] addresses = resolve(host);
        requireAllowed(host, addresses);
        return uri;
    }

    public InetAddress[] resolveForConnection(String host) throws UnknownHostException {
        String normalizedHost = normalizeHost(host);
        if (normalizedHost.isBlank()) {
            throw new UnknownHostException("Webhook host is blank");
        }
        try {
            InetAddress[] addresses = resolve(normalizedHost);
            requireAllowed(normalizedHost, addresses);
            return addresses.clone();
        } catch (IllegalArgumentException ex) {
            UnknownHostException blocked = new UnknownHostException(
                    "Webhook destination is not allowed");
            blocked.initCause(ex);
            throw blocked;
        }
    }

    public static boolean isValidUrl(String url) {
        try {
            new WebhookUrlValidator().validate(url);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    public static boolean isBlockedAddress(InetAddress address) {
        if (address == null) {
            return true;
        }
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }

        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address || bytes.length == 4) {
            return isBlockedIpv4(bytes);
        }
        if (address instanceof Inet6Address || bytes.length == 16) {
            return isBlockedIpv6(bytes);
        }
        return true;
    }

    private static URI parse(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Webhook URL is required");
        }
        final URI uri;
        try {
            uri = new URI(url.trim());
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("Webhook URL is malformed", ex);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("Webhook URL must use HTTPS");
        }
        if (uri.getUserInfo() != null) {
            throw new IllegalArgumentException("Webhook URL must not contain user information");
        }
        if (uri.getFragment() != null) {
            throw new IllegalArgumentException("Webhook URL must not contain a fragment");
        }
        return uri;
    }

    private static String requireHost(URI uri) {
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Webhook URL must contain a host");
        }
        String normalized = normalizeHost(host);
        if (normalized.isBlank() || normalized.contains("%")) {
            throw new IllegalArgumentException("Webhook host is not allowed");
        }
        return normalized;
    }

    private InetAddress[] resolve(String host) {
        final InetAddress[] addresses;
        try {
            addresses = hostResolver.resolve(host);
        } catch (UnknownHostException ex) {
            throw new IllegalArgumentException("Webhook host could not be resolved", ex);
        }
        if (addresses == null || addresses.length == 0) {
            throw new IllegalArgumentException("Webhook host could not be resolved");
        }
        return addresses;
    }

    private static void requireAllowed(String host, InetAddress[] addresses) {
        for (InetAddress address : addresses) {
            if (isBlockedAddress(address)) {
                throw new IllegalArgumentException(
                        "Webhook destination is not allowed: " + host);
            }
        }
    }

    private static String normalizeHost(String host) {
        String normalized = host.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        while (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static boolean isBlockedIpv4(byte[] bytes) {
        int first = bytes[0] & 0xff;
        int second = bytes[1] & 0xff;
        int third = bytes[2] & 0xff;

        if (first == 0 || first == 10 || first == 127) {
            return true;
        }
        if (first == 100 && second >= 64 && second <= 127) {
            return true;
        }
        if (first == 169 && second == 254) {
            return true;
        }
        if (first == 172 && second >= 16 && second <= 31) {
            return true;
        }
        if (first == 192 && second == 0 && (third == 0 || third == 2)) {
            return true;
        }
        if (first == 192 && second == 88 && third == 99) {
            return true;
        }
        if (first == 192 && second == 168) {
            return true;
        }
        if (first == 198 && (second == 18 || second == 19)) {
            return true;
        }
        if (first == 198 && second == 51 && third == 100) {
            return true;
        }
        if (first == 203 && second == 0 && third == 113) {
            return true;
        }
        return first >= 224;
    }

    private static boolean isBlockedIpv6(byte[] bytes) {
        int first = bytes[0] & 0xff;
        if (first == 0xff) {
            return true;
        }
        if ((first & 0xfe) == 0xfc) {
            return true;
        }

        if (isIpv4Mapped(bytes) || isIpv4Compatible(bytes)) {
            return isBlockedIpv4(lastFour(bytes));
        }
        if (isNat64LocalUse(bytes)) {
            return true;
        }
        if (isNat64WellKnown(bytes)) {
            return isBlockedIpv4(lastFour(bytes));
        }
        if (first == 0x20 && (bytes[1] & 0xff) == 0x02) {
            return true;
        }
        if (first == 0x20 && (bytes[1] & 0xff) == 0x01
                && bytes[2] == 0 && bytes[3] == 0) {
            return true;
        }
        if (first == 0x20 && (bytes[1] & 0xff) == 0x01
                && (bytes[2] & 0xff) == 0x0d && (bytes[3] & 0xff) == 0xb8) {
            return true;
        }
        return first == 0x3f && (bytes[1] & 0xf0) == 0xf0;
    }

    private static boolean isIpv4Mapped(byte[] bytes) {
        for (int i = 0; i < 10; i++) {
            if (bytes[i] != 0) {
                return false;
            }
        }
        return bytes[10] == (byte) 0xff && bytes[11] == (byte) 0xff;
    }

    private static boolean isIpv4Compatible(byte[] bytes) {
        for (int i = 0; i < 12; i++) {
            if (bytes[i] != 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean isNat64LocalUse(byte[] bytes) {
        return bytes[0] == 0 && (bytes[1] & 0xff) == 0x64
                && (bytes[2] & 0xff) == 0xff && (bytes[3] & 0xff) == 0x9b
                && bytes[4] == 0 && bytes[5] == 1;
    }

    private static boolean isNat64WellKnown(byte[] bytes) {
        if (bytes[0] != 0 || (bytes[1] & 0xff) != 0x64
                || (bytes[2] & 0xff) != 0xff || (bytes[3] & 0xff) != 0x9b) {
            return false;
        }
        for (int i = 4; i < 12; i++) {
            if (bytes[i] != 0) {
                return false;
            }
        }
        return true;
    }

    private static byte[] lastFour(byte[] bytes) {
        byte[] ipv4 = new byte[4];
        System.arraycopy(bytes, 12, ipv4, 0, 4);
        return ipv4;
    }
}
