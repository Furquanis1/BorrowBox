package com.borrowbox.controller;

import com.borrowbox.dto.WebhookEndpointResponse;
import com.borrowbox.dto.WebhookEndpointRequest;
import com.borrowbox.dto.WebhookDeliveryResponse;
import com.borrowbox.entity.User;
import com.borrowbox.entity.WebhookEndpoint;
import com.borrowbox.entity.WebhookEndpointStatus;
import com.borrowbox.entity.WebhookDelivery;
import com.borrowbox.entity.WebhookDeliveryStatus;
import com.borrowbox.repository.WebhookDeliveryRepository;
import com.borrowbox.repository.WebhookEndpointRepository;
import com.borrowbox.service.UserService;
import com.borrowbox.service.WebhookDeliveryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

/**
 * REST controller for managing webhook endpoints (V2.6).
 *
 * Users can register, list, update, and delete their own webhook endpoints.
 * Secret tokens are never returned in responses.
 */
@RestController
@RequestMapping("/api")
public class WebhookEndpointController {

    private final WebhookEndpointRepository endpointRepository;
    private final WebhookDeliveryRepository deliveryRepository;
    private final WebhookDeliveryService deliveryService;
    private final UserService userService;

    public WebhookEndpointController(WebhookEndpointRepository endpointRepository,
                                     WebhookDeliveryRepository deliveryRepository,
                                     WebhookDeliveryService deliveryService,
                                     UserService userService) {
        this.endpointRepository = endpointRepository;
        this.deliveryRepository = deliveryRepository;
        this.deliveryService = deliveryService;
        this.userService = userService;
    }

    @GetMapping("/webhook-endpoints")
    public ResponseEntity<List<WebhookEndpointResponse>> listEndpoints() {
        User user = currentUser();
        List<WebhookEndpoint> endpoints = endpointRepository
                .findByUserIdAndStatus(user.getId(), WebhookEndpointStatus.ACTIVE);
        return ResponseEntity.ok(endpoints.stream()
                .map(this::toResponse)
                .collect(Collectors.toList()));
    }

    @PostMapping("/webhook-endpoints")
    public ResponseEntity<WebhookEndpointResponse> createEndpoint(
            @RequestBody WebhookEndpointRequest request) {
        User user = currentUser();

        if (!WebhookDeliveryService.isValidUrl(request.url())) {
            throw new IllegalArgumentException("Invalid URL: " + request.url());
        }

        WebhookEndpoint endpoint = new WebhookEndpoint();
        endpoint.setUser(user);
        endpoint.setName(request.name());
        endpoint.setUrl(request.url());
        endpoint.setSecretToken(generateSecretToken());
        endpoint.setStatus(WebhookEndpointStatus.ACTIVE);

        WebhookEndpoint saved = endpointRepository.save(endpoint);
        return ResponseEntity.ok(toResponse(saved));
    }

    @PatchMapping("/webhook-endpoints/{id}")
    public ResponseEntity<WebhookEndpointResponse> updateEndpoint(
            @PathVariable Long id,
            @RequestBody WebhookEndpointRequest request) {
        User user = currentUser();

        WebhookEndpoint endpoint = endpointRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Webhook endpoint not found: " + id));

        if (request.url() != null && !WebhookDeliveryService.isValidUrl(request.url())) {
            throw new IllegalArgumentException("Invalid URL: " + request.url());
        }

        if (request.name() != null) {
            endpoint.setName(request.name());
        }
        if (request.url() != null) {
            endpoint.setUrl(request.url());
        }

        WebhookEndpoint saved = endpointRepository.save(endpoint);
        return ResponseEntity.ok(toResponse(saved));
    }

    @DeleteMapping("/webhook-endpoints/{id}")
    public ResponseEntity<Void> deleteEndpoint(@PathVariable Long id) {
        User user = currentUser();

        WebhookEndpoint endpoint = endpointRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Webhook endpoint not found: " + id));

        endpointRepository.delete(endpoint);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/webhook-endpoints/{id}/deliveries")
    public ResponseEntity<List<WebhookDeliveryResponse>> listDeliveries(@PathVariable Long id) {
        User user = currentUser();

        WebhookEndpoint endpoint = endpointRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Webhook endpoint not found: " + id));

        List<WebhookDelivery> deliveries = deliveryRepository
                .findByEndpointIdOrderByCreatedAtDesc(id);

        return ResponseEntity.ok(deliveries.stream()
                .map(this::toResponse)
                .collect(Collectors.toList()));
    }

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email;
        Object principal = auth.getPrincipal();
        if (principal instanceof UserDetails ud) {
            email = ud.getUsername();
        } else {
            email = principal.toString();
        }
        return userService.findByEmail(email);
    }

    private WebhookEndpointResponse toResponse(WebhookEndpoint endpoint) {
        return new WebhookEndpointResponse(
                endpoint.getId(),
                endpoint.getName(),
                endpoint.getUrl(),
                endpoint.getStatus(),
                endpoint.getCreatedAt(),
                endpoint.getUpdatedAt()
        );
    }

    private WebhookDeliveryResponse toResponse(WebhookDelivery delivery) {
        return new WebhookDeliveryResponse(
                delivery.getId(),
                delivery.getEndpoint().getId(),
                delivery.getEventId(),
                delivery.getEventType(),
                delivery.getTransactionId(),
                delivery.getStatus(),
                delivery.getHttpStatusCode(),
                delivery.getResponseBody(),
                delivery.getErrorMessage(),
                delivery.getAttemptNumber(),
                delivery.getNextRetryAt(),
                delivery.getCreatedAt(),
                delivery.getUpdatedAt()
        );
    }

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    String generateSecretToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
