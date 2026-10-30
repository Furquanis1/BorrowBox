package com.borrowbox.service;

import com.borrowbox.entity.TransactionEvent;
import com.borrowbox.entity.User;
import com.borrowbox.entity.WebhookDelivery;
import com.borrowbox.entity.WebhookDeliveryStatus;
import com.borrowbox.entity.WebhookEndpoint;
import com.borrowbox.entity.WebhookEndpointStatus;
import com.borrowbox.repository.WebhookDeliveryRepository;
import com.borrowbox.repository.WebhookEndpointRepository;
import com.borrowbox.security.WebhookSignatureService;
import com.borrowbox.security.WebhookUrlValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * V2.6: Outbound webhook delivery service.
 *
 * Sends webhook notifications asynchronously, outside the transaction that
 * creates the original event. Deliveries are retried with exponential
 * backoff up to a maximum number of attempts, then marked as terminally
 * failed.
 *
 * Concurrency: each delivery is atomically transitioned from PENDING to
 * CLAIMED with a lease before HTTP starts. The claim transaction commits
 * before the remote call, so multiple instances cannot send the same row
 * simultaneously and no row lock is held during HTTP. Expired claims are
 * returned to PENDING for recovery after a crash or restart.
 *
 * Security properties:
 *   <ul>
 *     <li>URLs are validated at registration: localhost, private IP ranges,
 *     link-local addresses, multicast addresses, unspecified addresses, and
 *     cloud metadata endpoints are rejected.</li>
 *     <li>Resolved addresses are validated before every delivery and the
 *     HTTP client resolves through the same validator, so the checked address
 *     is the address used for the connection.</li>
 *     <li>Redirects are disabled and redirect responses are recorded as
 *     failures.</li>
 *     <li>Outbound requests are signed with HMAC-SHA256 over the event ID,
 *     timestamp, and exact transmitted body bytes.</li>
 *     <li>No secret values are logged or returned in API responses.</li>
 *     <li>Connection and response timeouts are enforced.</li>
 *   </ul>
 */
@Service
public class WebhookDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(WebhookDeliveryService.class);

    private static final int MAX_ATTEMPTS = 5;
    private static final int MAX_RESPONSE_BODY_BYTES = 64 * 1024;
    private static final int CLAIM_BATCH_SIZE = 50;
    private static final Duration CLAIM_LEASE_DURATION = Duration.ofMinutes(5);

    private final WebhookDeliveryRepository deliveryRepository;
    private final WebhookEndpointRepository endpointRepository;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final TaskExecutor taskExecutor;
    private final WebhookUrlValidator urlValidator;
    private final WebhookSignatureService signatureService;

    public WebhookDeliveryService(WebhookDeliveryRepository deliveryRepository,
                                  WebhookEndpointRepository endpointRepository,
                                  ObjectMapper objectMapper,
                                  RestClient restClient,
                                  TaskExecutor taskExecutor) {
        this(deliveryRepository, endpointRepository, objectMapper, restClient, taskExecutor,
                new WebhookUrlValidator(), new WebhookSignatureService());
    }

    @Autowired
    public WebhookDeliveryService(WebhookDeliveryRepository deliveryRepository,
                                  WebhookEndpointRepository endpointRepository,
                                  ObjectMapper objectMapper,
                                  RestClient restClient,
                                  TaskExecutor taskExecutor,
                                  WebhookUrlValidator urlValidator,
                                  WebhookSignatureService signatureService) {
        this.deliveryRepository = deliveryRepository;
        this.endpointRepository = endpointRepository;
        this.objectMapper = objectMapper;
        this.restClient = restClient;
        this.taskExecutor = taskExecutor;
        this.urlValidator = urlValidator;
        this.signatureService = signatureService;
    }

    /**
     * Create durable delivery records for a transaction event, one per active
     * endpoint owned by an event recipient. Must run in the same transaction
     * that created the event; HTTP delivery is scheduled only after commit.
     *
     * @param event      the persisted transaction event
     * @param recipients the users who should receive the event
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void createDeliveriesForEvent(TransactionEvent event, List<User> recipients) {
        if (event == null || event.getId() == null || recipients == null || recipients.isEmpty()) {
            return;
        }

        String eventId = eventIdFor(event);
        Set<Long> seenEndpointIds = new HashSet<>();
        List<WebhookEndpoint> endpoints = new ArrayList<>();
        for (User recipient : recipients) {
            if (recipient == null || recipient.getId() == null) {
                continue;
            }
            endpoints.addAll(endpointRepository
                    .findByUserIdAndStatus(recipient.getId(), WebhookEndpointStatus.ACTIVE));
        }

        List<Long> deliveryIds = persistDeliveries(
                eventId,
                event.getTransaction().getId(),
                event.getEventType().name(),
                event.getPayload(),
                endpoints,
                seenEndpointIds);
        scheduleDispatchAfterCommit(deliveryIds);
    }

    /**
     * Create delivery records for all active endpoints of one user.
     * Kept for callers that dispatch a standalone webhook event.
     *
     * @param userId          the user whose endpoints should receive the event
     * @param transactionId   the transaction that triggered the event
     * @param eventType       the type of event
     * @param payload         optional JSON payload
     */
    @Transactional
    public void deliverEvent(Long userId, Long transactionId, String eventType, String payload) {
        String eventId = UUID.randomUUID().toString();
        List<WebhookEndpoint> endpoints = endpointRepository
                .findByUserIdAndStatus(userId, WebhookEndpointStatus.ACTIVE);
        List<Long> deliveryIds = persistDeliveries(
                eventId, transactionId, eventType, payload, endpoints, new HashSet<>());
        scheduleDispatchAfterCommit(deliveryIds);
    }

    private List<Long> persistDeliveries(String eventId,
                                         Long transactionId,
                                         String eventType,
                                         String payload,
                                         List<WebhookEndpoint> endpoints,
                                         Set<Long> seenEndpointIds) {
        List<Long> deliveryIds = new ArrayList<>();
        for (WebhookEndpoint endpoint : endpoints) {
            if (endpoint == null || endpoint.getId() == null
                    || !seenEndpointIds.add(endpoint.getId())) {
                continue;
            }
            if (deliveryRepository.existsByEventIdAndEndpointId(eventId, endpoint.getId())) {
                continue;
            }

            WebhookDelivery delivery = new WebhookDelivery();
            delivery.setEndpoint(endpoint);
            delivery.setEventId(eventId);
            delivery.setEventType(eventType);
            delivery.setTransactionId(transactionId);
            delivery.setPayload(payload);
            delivery.setStatus(WebhookDeliveryStatus.PENDING);
            delivery.setAttemptNumber(1);
            delivery.setNextRetryAt(LocalDateTime.now());
            deliveryIds.add(deliveryRepository.save(delivery).getId());
        }
        return deliveryIds;
    }

    private String eventIdFor(TransactionEvent event) {
        return UUID.nameUUIDFromBytes(
                ("transaction-event:" + event.getId()).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private void scheduleDispatchAfterCommit(List<Long> deliveryIds) {
        if (deliveryIds.isEmpty()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            dispatchAsync(deliveryIds);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                dispatchAsync(deliveryIds);
            }
        });
    }

    private void dispatchAsync(List<Long> deliveryIds) {
        if (deliveryIds.isEmpty()) {
            return;
        }
        try {
            taskExecutor.execute(() -> dispatch(deliveryIds));
        } catch (RuntimeException ex) {
            log.warn("Unable to schedule webhook delivery for {} record(s)", deliveryIds.size(), ex);
        }
    }

    private void dispatch(List<Long> deliveryIds) {
        if (deliveryIds == null || deliveryIds.isEmpty()) {
            return;
        }
        try {
            String claimToken = UUID.randomUUID().toString();
            LocalDateTime now = LocalDateTime.now();
            int claimed = deliveryRepository.claimPendingDeliveriesByIdIn(
                    deliveryIds,
                    WebhookDeliveryStatus.PENDING,
                    WebhookDeliveryStatus.CLAIMED,
                    claimToken,
                    now,
                    now.plus(CLAIM_LEASE_DURATION));
            if (claimed == 0) {
                return;
            }
            deliverClaimed(claimToken);
        } catch (Exception ex) {
            log.warn("Webhook dispatch failed for {} record(s)", deliveryIds.size(), ex);
        }
    }

    private void deliverClaimed(String claimToken) {
        List<WebhookDelivery> deliveries = deliveryRepository
                .findAllByClaimTokenAndStatusOrderByIdAsc(
                        claimToken, WebhookDeliveryStatus.CLAIMED);
        for (WebhookDelivery delivery : deliveries) {
            deliverNow(delivery, delivery.getPayload());
        }
    }

    /**
     * Deliver a single webhook payload to the given endpoint.
     */
    private void deliverNow(WebhookDelivery delivery, String payload) {
        WebhookEndpoint endpoint = delivery.getEndpoint();
        String requestBody = payload != null ? payload : "{}";
        byte[] requestBodyBytes = requestBody.getBytes(StandardCharsets.UTF_8);
        String timestamp = String.valueOf(Instant.now().getEpochSecond());

        try {
            URI destination = urlValidator.validate(endpoint.getUrl());
            String signature = signatureService.sign(
                    delivery.getEventId(), timestamp, requestBodyBytes, endpoint.getSecretToken());

            var responseEntity = restClient.post()
                    .uri(destination)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> {
                        headers.set(WebhookSignatureService.SIGNATURE_HEADER, signature);
                        headers.set(WebhookSignatureService.TIMESTAMP_HEADER, timestamp);
                        headers.set(WebhookSignatureService.EVENT_ID_HEADER, delivery.getEventId());
                        headers.set("X-Webhook-Event-Type", delivery.getEventType());
                    })
                    .body(requestBodyBytes)
                    .retrieve()
                    .onStatus(status -> status.isError(),
                            (req, res) -> {
                                throw new WebhookHttpStatusException(
                                        res.getStatusCode().value());
                            })
                    .toEntity(String.class);

            int httpStatusCode = responseEntity.getStatusCode().value();
            if (responseEntity.getStatusCode().is3xxRedirection()) {
                recordFailure(delivery, "Redirect responses are not allowed", httpStatusCode);
                return;
            }
            recordSuccess(delivery, httpStatusCode, responseEntity.getBody());
        } catch (WebhookHttpStatusException ex) {
            recordFailure(delivery, ex.getMessage(), ex.statusCode);
        } catch (RestClientException ex) {
            recordFailure(delivery, ex.getMessage(), null);
        } catch (Exception ex) {
            recordFailure(delivery, ex.getMessage(), null);
        }
    }

    private void recordSuccess(WebhookDelivery delivery, int httpStatusCode, String body) {
        clearClaim(delivery);
        delivery.setStatus(WebhookDeliveryStatus.SENT);
        delivery.setHttpStatusCode(httpStatusCode);
        delivery.setResponseBody(truncate(body));
        delivery.setAttemptNumber(delivery.getAttemptNumber() + 1);
        delivery.setNextRetryAt(null);
        deliveryRepository.save(delivery);
    }

    private void recordFailure(WebhookDelivery delivery, String errorMessage, Integer httpStatus) {
        clearClaim(delivery);
        delivery.setAttemptNumber(delivery.getAttemptNumber() + 1);

        if (delivery.getAttemptNumber() >= MAX_ATTEMPTS) {
            delivery.setStatus(WebhookDeliveryStatus.FAILED);
            delivery.setErrorMessage(errorMessage);
            delivery.setHttpStatusCode(httpStatus);
            delivery.setResponseBody(truncate(errorMessage));
            delivery.setNextRetryAt(null);
        } else {
            delivery.setStatus(WebhookDeliveryStatus.PENDING);
            delivery.setErrorMessage(errorMessage);
            delivery.setHttpStatusCode(httpStatus);
            delivery.setResponseBody(truncate(errorMessage));
            LocalDateTime nextRetry = LocalDateTime.now()
                    .plusSeconds((long) Math.pow(2, delivery.getAttemptNumber()) * 5);
            delivery.setNextRetryAt(nextRetry);
        }
        deliveryRepository.save(delivery);
    }

    private void clearClaim(WebhookDelivery delivery) {
        delivery.setClaimToken(null);
        delivery.setClaimedAt(null);
        delivery.setClaimExpiresAt(null);
    }

    private String truncate(String value) {
        if (value == null) return null;
        if (value.length() <= MAX_RESPONSE_BODY_BYTES) return value;
        return value.substring(0, MAX_RESPONSE_BODY_BYTES) + "...[truncated]";
    }

    /**
     * Recover expired claims, then atomically claim and deliver due pending
     * rows. Claiming commits before HTTP starts, so no row locks are held
     * while a remote endpoint is called.
     *
     * Called periodically by the scheduler.
     */
    public void retryPendingDeliveries() {
        try {
            LocalDateTime now = LocalDateTime.now();
            deliveryRepository.releaseExpiredClaims(
                    WebhookDeliveryStatus.CLAIMED,
                    WebhookDeliveryStatus.PENDING,
                    now);

            String claimToken = UUID.randomUUID().toString();
            int claimed = deliveryRepository.claimPendingDeliveries(
                    claimToken,
                    now,
                    now.plus(CLAIM_LEASE_DURATION),
                    CLAIM_BATCH_SIZE);
            if (claimed == 0) {
                return;
            }
            deliverClaimed(claimToken);
        } catch (Exception ex) {
            log.warn("Webhook retry sweep failed", ex);
        }
    }

    public static boolean isValidUrl(String url) {
        return WebhookUrlValidator.isValidUrl(url);
    }

    private static final class WebhookHttpStatusException extends RuntimeException {

        private final int statusCode;

        private WebhookHttpStatusException(int statusCode) {
            super("HTTP " + statusCode);
            this.statusCode = statusCode;
        }
    }
}
