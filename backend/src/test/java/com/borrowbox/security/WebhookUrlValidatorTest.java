package com.borrowbox.security;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebhookUrlValidatorTest {

    @Test
    void blocksLoopbackAndPrivateIpv4() throws UnknownHostException {
        assertBlocked("127.0.0.1", "10.0.0.1", "172.16.0.1", "192.168.1.1",
                "169.254.169.254", "0.0.0.0", "100.64.0.1", "192.0.2.1",
                "198.18.0.1", "198.51.100.1", "203.0.113.1", "240.0.0.1",
                "255.255.255.255");
    }

    @Test
    void allowsPublicIpv4() throws UnknownHostException {
        assertAllowed("8.8.8.8", "1.1.1.1");
    }

    @Test
    void blocksIpv6LoopbackUnspecifiedLinkLocalUniqueLocalMulticast() throws UnknownHostException {
        assertBlocked("::1", "::", "fe80::1", "fc00::1", "fd12:3456::1", "ff02::1");
    }

    @Test
    void blocksReservedIpv6() throws UnknownHostException {
        assertBlocked("2001:db8::1", "2002:7f00:1::", "64:ff9b:1::1",
                "::ffff:127.0.0.1", "::ffff:10.0.0.1");
    }

    @Test
    void allowsAllowedIpv6() throws UnknownHostException {
        assertAllowed("2001:4860:4860::8888", "::ffff:8.8.8.8", "64:ff9b::8.8.8.8");
    }

    @Test
    void rejectsNonHttpsAndMissingHost() {
        assertFalse("http://8.8.8.8/hook");
        assertFalse("https:///hook");
        assertFalse("");
        assertFalse("   ");
    }

    @Test
    void rejectsUserInfoAndFragment() {
        assertFalse("https://user:pass@8.8.8.8/hook");
        assertFalse("https://8.8.8.8/hook#section");
    }

    @Test
    void registrationAcceptsThenRebindingToPrivateIsRejected() throws UnknownHostException {
        AtomicReference<WebhookUrlValidator.HostResolver> resolver = new AtomicReference<>(
                host -> new InetAddress[]{InetAddress.getByName("93.184.216.34")});
        WebhookUrlValidator validator = new WebhookUrlValidator(
                host -> resolver.get().resolve(host));

        validator.validate("https://hooks.example.com/hook");

        resolver.set(host -> new InetAddress[]{InetAddress.getByName("127.0.0.1")});
        assertThatThrownBy(() -> validator.resolveForConnection("hooks.example.com"))
                .isInstanceOf(UnknownHostException.class);
        assertThatThrownBy(() -> validator.validate("https://hooks.example.com/hook"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void everyResolvedAddressMustBeAllowed() throws UnknownHostException {
        WebhookUrlValidator validator = new WebhookUrlValidator(
                host -> new InetAddress[]{
                        InetAddress.getByName("93.184.216.34"),
                        InetAddress.getByName("127.0.0.1")});

        assertThatThrownBy(() -> validator.validate("https://hooks.example.com/hook"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static void assertBlocked(String... addresses) throws UnknownHostException {
        for (String address : addresses) {
            assertThat(WebhookUrlValidator.isBlockedAddress(InetAddress.getByName(address)))
                    .as(address)
                    .isTrue();
        }
    }

    private static void assertAllowed(String... addresses) throws UnknownHostException {
        for (String address : addresses) {
            assertThat(WebhookUrlValidator.isBlockedAddress(InetAddress.getByName(address)))
                    .as(address)
                    .isFalse();
        }
    }

    private static void assertFalse(String url) {
        assertThat(WebhookUrlValidator.isValidUrl(url)).isFalse();
    }
}
