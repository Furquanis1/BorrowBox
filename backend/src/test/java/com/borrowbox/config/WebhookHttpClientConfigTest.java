package com.borrowbox.config;

import java.time.Duration;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookHttpClientConfigTest {

    @Test
    void redirectsAreDisabledAndTimeoutsAreConfigured() {
        WebhookHttpClientConfig config = new WebhookHttpClientConfig();

        RequestConfig requestConfig = config.requestConfig();
        assertThat(requestConfig.isRedirectsEnabled()).isFalse();
        assertThat(requestConfig.getConnectionRequestTimeout().toDuration())
                .isEqualTo(Duration.ofMillis(WebhookHttpClientConfig.CONNECT_TIMEOUT_MS));
        assertThat(requestConfig.getResponseTimeout().toDuration())
                .isEqualTo(Duration.ofMillis(WebhookHttpClientConfig.RESPONSE_TIMEOUT_MS));

        ConnectionConfig connectionConfig = config.connectionConfig();
        assertThat(connectionConfig.getConnectTimeout().toDuration())
                .isEqualTo(Duration.ofMillis(WebhookHttpClientConfig.CONNECT_TIMEOUT_MS));
        assertThat(connectionConfig.getSocketTimeout().toDuration())
                .isEqualTo(Duration.ofMillis(WebhookHttpClientConfig.RESPONSE_TIMEOUT_MS));
    }
}
