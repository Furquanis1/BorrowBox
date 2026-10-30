package com.borrowbox.config;

import com.borrowbox.security.WebhookUrlValidator;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class WebhookHttpClientConfig {

    static final int CONNECT_TIMEOUT_MS = 5000;
    static final int RESPONSE_TIMEOUT_MS = 10000;

    @Bean(destroyMethod = "close")
    public CloseableHttpClient webhookHttpClient(WebhookUrlValidator urlValidator) {
        DnsResolver validatingResolver = new DnsResolver() {
            @Override
            public InetAddress[] resolve(String host) throws UnknownHostException {
                return urlValidator.resolveForConnection(host);
            }

            @Override
            public String resolveCanonicalHostname(String host) {
                return host;
            }
        };

        PoolingHttpClientConnectionManager connectionManager =
                PoolingHttpClientConnectionManagerBuilder.create()
                        .setDnsResolver(validatingResolver)
                        .setDefaultConnectionConfig(connectionConfig())
                        .setMaxConnTotal(20)
                        .setMaxConnPerRoute(10)
                        .build();

        return HttpClientBuilder.create()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig())
                .disableRedirectHandling()
                .disableAutomaticRetries()
                .disableCookieManagement()
                .disableAuthCaching()
                .build();
    }

    @Bean
    public RestClient webhookRestClient(CloseableHttpClient webhookHttpClient) {
        HttpComponentsClientHttpRequestFactory requestFactory =
                new HttpComponentsClientHttpRequestFactory(webhookHttpClient);
        requestFactory.setConnectTimeout(Duration.ofMillis(CONNECT_TIMEOUT_MS));
        requestFactory.setReadTimeout(Duration.ofMillis(RESPONSE_TIMEOUT_MS));
        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    static ConnectionConfig connectionConfig() {
        return ConnectionConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(CONNECT_TIMEOUT_MS))
                .setSocketTimeout(Timeout.ofMilliseconds(RESPONSE_TIMEOUT_MS))
                .build();
    }

    static RequestConfig requestConfig() {
        return RequestConfig.custom()
                .setRedirectsEnabled(false)
                .setConnectionRequestTimeout(Timeout.ofMilliseconds(CONNECT_TIMEOUT_MS))
                .setResponseTimeout(Timeout.ofMilliseconds(RESPONSE_TIMEOUT_MS))
                .build();
    }
}
