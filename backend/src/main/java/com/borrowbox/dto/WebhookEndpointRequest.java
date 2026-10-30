package com.borrowbox.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating or updating a webhook endpoint (V2.6).
 *
 * The URL is validated against an allowlist of safe schemes and hosts to
 * prevent SSRF attacks: localhost, private IP ranges, link-local addresses,
 * and cloud metadata endpoints are rejected.
 */
public record WebhookEndpointRequest(
        @NotBlank
        @Size(max = 255)
        String name,

        @NotBlank
        @Size(max = 2048)
        String url
) {
}
