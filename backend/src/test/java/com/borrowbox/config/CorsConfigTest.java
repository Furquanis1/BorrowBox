package com.borrowbox.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;

class CorsConfigTest {

    private final CorsConfig corsConfig = new CorsConfig();

    private CorsConfiguration configFor(String origin, String method) {
        UrlBasedCorsConfigurationSource source =
                (UrlBasedCorsConfigurationSource) corsConfig.corsConfigurationSource();
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/api/communities/1/flags");
        if (origin != null) {
            request.addHeader("Origin", origin);
        }
        return source.getCorsConfiguration(request);
    }

    @Test
    void allowedOriginsIncludeLocalhostDevelopmentOrigins() {
        assertThat(configFor("http://localhost:3000", "GET").getAllowedOrigins())
                .contains("http://localhost:5173", "http://localhost:3000", "http://127.0.0.1:3000");
    }

    @Test
    void allowsPatchAndEveryVerbTheFrontendUses() {
        assertThat(configFor("http://localhost:3000", "PATCH").getAllowedMethods())
                .contains("GET", "POST", "PATCH", "DELETE", "OPTIONS");
    }
}