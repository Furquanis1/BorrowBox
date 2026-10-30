package com.borrowbox.service;

import com.borrowbox.entity.Transaction;
import com.borrowbox.entity.TransactionEvent;
import com.borrowbox.entity.TransactionEventType;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClient;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebhookDeliveryServiceTest {

    @Mock
    private WebhookDeliveryRepository deliveryRepository;

    @Mock
    private WebhookEndpointRepository endpointRepository;

    @Mock
    private RestClient restClient;

    @Mock
    private TaskExecutor taskExecutor;

    private WebhookDeliveryService service;

    private WebhookUrlValidator urlValidator;

    private WebhookSignatureService signatureService;

    @BeforeEach
    void setUp() throws UnknownHostException {
        urlValidator = new WebhookUrlValidator(
                host -> new InetAddress[]{InetAddress.getByName("93.184.216.34")});
        signatureService = new WebhookSignatureService();
        service = new WebhookDeliveryService(
                deliveryRepository, endpointRepository, new ObjectMapper(), restClient, taskExecutor,
                urlValidator, signatureService);
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void createDeliveriesPersistsOneRecordPerActiveEndpointAndDefersHttpDispatch() {
        Transaction transaction = new Transaction();
        transaction.setId(7L);

        TransactionEvent event = new TransactionEvent();
        event.setId(42L);
        event.setTransaction(transaction);
        event.setEventType(TransactionEventType.LOAN_STARTED);
        event.setPayload("{\"transactionId\":7}");

        User borrower = user(1L, "Borrower");
        User lender = user(2L, "Lender");
        WebhookEndpoint borrowerEndpoint = endpoint(10L);
        WebhookEndpoint lenderEndpoint = endpoint(20L);

        when(endpointRepository.findByUserIdAndStatus(1L, WebhookEndpointStatus.ACTIVE))
                .thenReturn(List.of(borrowerEndpoint));
        when(endpointRepository.findByUserIdAndStatus(2L, WebhookEndpointStatus.ACTIVE))
                .thenReturn(List.of(lenderEndpoint));
        when(deliveryRepository.existsByEventIdAndEndpointId(anyString(), anyLong()))
                .thenReturn(false);
        AtomicLong sequence = new AtomicLong(100L);
        when(deliveryRepository.save(any(WebhookDelivery.class))).thenAnswer(invocation -> {
            WebhookDelivery delivery = invocation.getArgument(0);
            delivery.setId(sequence.getAndIncrement());
            return delivery;
        });

        TransactionSynchronizationManager.initSynchronization();

        service.createDeliveriesForEvent(event, List.of(borrower, lender));

        ArgumentCaptor<WebhookDelivery> captor = ArgumentCaptor.forClass(WebhookDelivery.class);
        verify(deliveryRepository, times(2)).save(captor.capture());
        List<WebhookDelivery> saved = captor.getAllValues();

        assertThat(saved).extracting(d -> d.getEndpoint().getId())
                .containsExactlyInAnyOrder(10L, 20L);
        assertThat(saved).extracting(WebhookDelivery::getStatus)
                .containsOnly(WebhookDeliveryStatus.PENDING);
        assertThat(saved).extracting(WebhookDelivery::getEventId)
                .containsOnly(saved.get(0).getEventId());
        assertThat(saved).extracting(WebhookDelivery::getTransactionId)
                .containsOnly(7L);
        verifyNoInteractions(restClient, taskExecutor);

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);

        verify(taskExecutor, times(1)).execute(any(Runnable.class));
        verifyNoInteractions(restClient);
    }

    @Test
    void createDeliveriesSkipsEndpointThatAlreadyHasDeliveryForEvent() {
        Transaction transaction = new Transaction();
        transaction.setId(7L);

        TransactionEvent event = new TransactionEvent();
        event.setId(42L);
        event.setTransaction(transaction);
        event.setEventType(TransactionEventType.REQUEST_APPROVED);

        User borrower = user(1L, "Borrower");
        WebhookEndpoint endpoint = endpoint(10L);

        when(endpointRepository.findByUserIdAndStatus(1L, WebhookEndpointStatus.ACTIVE))
                .thenReturn(List.of(endpoint));
        when(deliveryRepository.existsByEventIdAndEndpointId(anyString(), anyLong()))
                .thenReturn(true);

        service.createDeliveriesForEvent(event, List.of(borrower));

        verify(deliveryRepository, never()).save(any(WebhookDelivery.class));
        verifyNoInteractions(taskExecutor, restClient);
    }

    @Test
    void afterCommitDispatchClaimsRowsBeforeFetchingThem() {
        TransactionEvent event = transactionEvent();
        WebhookEndpoint endpoint = endpoint(10L);

        when(endpointRepository.findByUserIdAndStatus(1L, WebhookEndpointStatus.ACTIVE))
                .thenReturn(List.of(endpoint));
        when(deliveryRepository.existsByEventIdAndEndpointId(anyString(), anyLong()))
                .thenReturn(false);
        when(deliveryRepository.save(any(WebhookDelivery.class))).thenAnswer(invocation -> {
            WebhookDelivery delivery = invocation.getArgument(0);
            delivery.setId(100L);
            return delivery;
        });
        doAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return null;
        }).when(taskExecutor).execute(any(Runnable.class));
        when(deliveryRepository.claimPendingDeliveriesByIdIn(
                eq(List.of(100L)),
                eq(WebhookDeliveryStatus.PENDING),
                eq(WebhookDeliveryStatus.CLAIMED),
                anyString(),
                any(LocalDateTime.class),
                any(LocalDateTime.class)))
                .thenReturn(1);
        when(deliveryRepository.findAllByClaimTokenAndStatusOrderByIdAsc(
                anyString(), eq(WebhookDeliveryStatus.CLAIMED)))
                .thenReturn(List.of());

        TransactionSynchronizationManager.initSynchronization();
        service.createDeliveriesForEvent(event, List.of(user(1L, "Borrower")));
        completeAfterCommitCallbacks();

        ArgumentCaptor<String> claimToken = ArgumentCaptor.forClass(String.class);
        verify(deliveryRepository).claimPendingDeliveriesByIdIn(
                eq(List.of(100L)),
                eq(WebhookDeliveryStatus.PENDING),
                eq(WebhookDeliveryStatus.CLAIMED),
                claimToken.capture(),
                any(LocalDateTime.class),
                any(LocalDateTime.class));
        verify(deliveryRepository).findAllByClaimTokenAndStatusOrderByIdAsc(
                eq(claimToken.getValue()), eq(WebhookDeliveryStatus.CLAIMED));
        verifyNoInteractions(restClient);
    }

    @Test
    void concurrentDispatchAttemptsClaimEachRowOnlyOnce() {
        TransactionEvent event = transactionEvent();
        WebhookEndpoint endpoint = endpoint(10L);

        when(endpointRepository.findByUserIdAndStatus(1L, WebhookEndpointStatus.ACTIVE))
                .thenReturn(List.of(endpoint));
        when(deliveryRepository.existsByEventIdAndEndpointId(anyString(), anyLong()))
                .thenReturn(false);
        when(deliveryRepository.save(any(WebhookDelivery.class))).thenAnswer(invocation -> {
            WebhookDelivery delivery = invocation.getArgument(0);
            delivery.setId(100L);
            return delivery;
        });
        doAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return null;
        }).when(taskExecutor).execute(any(Runnable.class));
        when(deliveryRepository.claimPendingDeliveriesByIdIn(
                eq(List.of(100L)),
                eq(WebhookDeliveryStatus.PENDING),
                eq(WebhookDeliveryStatus.CLAIMED),
                anyString(),
                any(LocalDateTime.class),
                any(LocalDateTime.class)))
                .thenReturn(1, 0);
        when(deliveryRepository.findAllByClaimTokenAndStatusOrderByIdAsc(
                anyString(), eq(WebhookDeliveryStatus.CLAIMED)))
                .thenReturn(List.of());

        TransactionSynchronizationManager.initSynchronization();
        service.createDeliveriesForEvent(event, List.of(user(1L, "Borrower")));
        completeAfterCommitCallbacks();
        completeAfterCommitCallbacks();

        verify(deliveryRepository, times(2)).claimPendingDeliveriesByIdIn(
                eq(List.of(100L)),
                eq(WebhookDeliveryStatus.PENDING),
                eq(WebhookDeliveryStatus.CLAIMED),
                anyString(),
                any(LocalDateTime.class),
                any(LocalDateTime.class));
        verify(deliveryRepository, times(1)).findAllByClaimTokenAndStatusOrderByIdAsc(
                anyString(), eq(WebhookDeliveryStatus.CLAIMED));
        verifyNoInteractions(restClient);
    }

    @Test
    void blockedDestinationIsRecordedAsFailureWithoutHttpCall() throws Exception {
        WebhookEndpoint endpoint = endpoint(10L);
        WebhookDelivery delivery = claimedDelivery(endpoint, "blocked-token", "evt-1", "type", "{}");

        stubRetrySweepClaimsOne(delivery);
        when(deliveryRepository.save(any(WebhookDelivery.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        WebhookUrlValidator blockingValidator = new WebhookUrlValidator(
                host -> {
                    throw new IllegalArgumentException("blocked");
                });
        service = new WebhookDeliveryService(
                deliveryRepository, endpointRepository, new ObjectMapper(), restClient, taskExecutor,
                blockingValidator, signatureService);

        service.retryPendingDeliveries();

        verify(deliveryRepository).save(delivery);
        assertThat(delivery.getStatus()).isEqualTo(WebhookDeliveryStatus.PENDING);
        assertThat(delivery.getAttemptNumber()).isEqualTo(2);
        assertThat(delivery.getNextRetryAt()).isNotNull();
        verifyNoInteractions(restClient);
    }

    @Test
    void redirectResponseIsRecordedAsFailureAndNotFollowed() throws Exception {
        WebhookEndpoint endpoint = endpoint(10L);
        WebhookDelivery delivery = claimedDelivery(endpoint, "token", "evt-1", "type", "{}");

        stubRetrySweepClaimsOne(delivery);
        when(deliveryRepository.save(any(WebhookDelivery.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RestClient.RequestBodyUriSpec requestSpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);
        when(restClient.post()).thenReturn(requestSpec);
        when(requestSpec.uri(any(URI.class))).thenReturn(requestSpec);
        when(requestSpec.contentType(any(MediaType.class))).thenReturn(requestSpec);
        doAnswer(invocation -> {
            Consumer<HttpHeaders> consumer = invocation.getArgument(0);
            HttpHeaders captured = new HttpHeaders();
            consumer.accept(captured);
            return requestSpec;
        }).when(requestSpec).headers(any(Consumer.class));
        AtomicReference<byte[]> body = new AtomicReference<>();
        when(requestSpec.body(any(byte[].class))).thenAnswer(invocation -> {
            body.set(invocation.getArgument(0));
            return requestSpec;
        });
        when(requestSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
        when(responseSpec.toEntity(String.class))
                .thenReturn(ResponseEntity.status(HttpStatus.FOUND).body(""));

        service.retryPendingDeliveries();

        verify(deliveryRepository).save(delivery);
        assertThat(delivery.getStatus()).isEqualTo(WebhookDeliveryStatus.PENDING);
        assertThat(delivery.getAttemptNumber()).isEqualTo(2);
        assertThat(delivery.getHttpStatusCode()).isEqualTo(HttpStatus.FOUND.value());
        assertThat(body.get()).isEqualTo("{}".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void deliverySendsExactBodyAndDocumentedSignatureHeaders() throws Exception {
        WebhookEndpoint endpoint = endpoint(10L);
        WebhookDelivery delivery = claimedDelivery(endpoint, "token", "evt-1", "type", "{}");

        stubRetrySweepClaimsOne(delivery);
        when(deliveryRepository.save(any(WebhookDelivery.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RestClient.RequestBodyUriSpec requestSpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);
        when(restClient.post()).thenReturn(requestSpec);
        when(requestSpec.uri(any(URI.class))).thenReturn(requestSpec);
        when(requestSpec.contentType(any(MediaType.class))).thenReturn(requestSpec);
        AtomicReference<HttpHeaders> headers = new AtomicReference<>();
        AtomicReference<byte[]> body = new AtomicReference<>();
        doAnswer(invocation -> {
            Consumer<HttpHeaders> consumer = invocation.getArgument(0);
            HttpHeaders captured = new HttpHeaders();
            consumer.accept(captured);
            headers.set(captured);
            return requestSpec;
        }).when(requestSpec).headers(any(Consumer.class));
        when(requestSpec.body(any(byte[].class))).thenAnswer(invocation -> {
            body.set(invocation.getArgument(0));
            return requestSpec;
        });
        when(requestSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
        when(responseSpec.toEntity(String.class))
                .thenReturn(ResponseEntity.ok("ok"));

        service.retryPendingDeliveries();

        verify(deliveryRepository).save(delivery);
        assertThat(delivery.getStatus()).isEqualTo(WebhookDeliveryStatus.SENT);
        assertThat(delivery.getAttemptNumber()).isEqualTo(2);
        assertThat(delivery.getNextRetryAt()).isNull();
        assertThat(delivery.getHttpStatusCode()).isEqualTo(200);

        String timestamp = headers.get().getFirst(WebhookSignatureService.TIMESTAMP_HEADER);
        assertThat(timestamp).isNotBlank();
        String signature = headers.get().getFirst(WebhookSignatureService.SIGNATURE_HEADER);
        assertThat(signature).startsWith("sha256=");
        assertThat(signatureService.verify(
                signature, delivery.getEventId(), timestamp, body.get(), "secret")).isTrue();
        assertThat(headers.get().getFirst(WebhookSignatureService.EVENT_ID_HEADER))
                .isEqualTo(delivery.getEventId());
        assertThat(headers.get().getFirst("X-Webhook-Event-Type")).isEqualTo(delivery.getEventType());
        assertThat(body.get()).isEqualTo("{}".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void retrySweepRecoversExpiredClaimsBeforeClaimingDueDeliveries() {
        when(deliveryRepository.releaseExpiredClaims(
                eq(WebhookDeliveryStatus.CLAIMED),
                eq(WebhookDeliveryStatus.PENDING),
                any(LocalDateTime.class)))
                .thenReturn(1);
        when(deliveryRepository.claimPendingDeliveries(
                anyString(),
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                anyInt()))
                .thenReturn(0);

        service.retryPendingDeliveries();

        ArgumentCaptor<LocalDateTime> claimedAt = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> leaseExpiresAt = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<Integer> batchSize = ArgumentCaptor.forClass(Integer.class);

        InOrder order = inOrder(deliveryRepository);
        order.verify(deliveryRepository).releaseExpiredClaims(
                eq(WebhookDeliveryStatus.CLAIMED),
                eq(WebhookDeliveryStatus.PENDING),
                claimedAt.capture());
        order.verify(deliveryRepository).claimPendingDeliveries(
                anyString(),
                eq(claimedAt.getValue()),
                leaseExpiresAt.capture(),
                batchSize.capture());

        assertThat(leaseExpiresAt.getValue()).isAfter(claimedAt.getValue());
        assertThat(batchSize.getValue()).isPositive();
        verify(deliveryRepository, never()).findAllByClaimTokenAndStatusOrderByIdAsc(
                anyString(), any(WebhookDeliveryStatus.class));
        verifyNoInteractions(restClient);
    }

    private static void completeAfterCommitCallbacks() {
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
    }

    private void stubRetrySweepClaimsOne(WebhookDelivery delivery) {
        when(deliveryRepository.releaseExpiredClaims(
                eq(WebhookDeliveryStatus.CLAIMED),
                eq(WebhookDeliveryStatus.PENDING),
                any(LocalDateTime.class)))
                .thenReturn(0);
        when(deliveryRepository.claimPendingDeliveries(
                anyString(),
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                anyInt()))
                .thenReturn(1);
        when(deliveryRepository.findAllByClaimTokenAndStatusOrderByIdAsc(
                anyString(), eq(WebhookDeliveryStatus.CLAIMED)))
                .thenReturn(List.of(delivery));
    }

    private static WebhookDelivery claimedDelivery(WebhookEndpoint endpoint,
                                                   String claimToken,
                                                   String eventId,
                                                   String eventType,
                                                   String payload) {
        WebhookDelivery delivery = new WebhookDelivery();
        delivery.setId(100L);
        delivery.setEndpoint(endpoint);
        delivery.setEventId(eventId);
        delivery.setEventType(eventType);
        delivery.setPayload(payload);
        delivery.setStatus(WebhookDeliveryStatus.CLAIMED);
        delivery.setAttemptNumber(1);
        delivery.setClaimToken(claimToken);
        delivery.setClaimedAt(LocalDateTime.now());
        delivery.setClaimExpiresAt(LocalDateTime.now().plusMinutes(5));
        return delivery;
    }

    private static TransactionEvent transactionEvent() {
        Transaction transaction = new Transaction();
        transaction.setId(7L);

        TransactionEvent event = new TransactionEvent();
        event.setId(42L);
        event.setTransaction(transaction);
        event.setEventType(TransactionEventType.LOAN_STARTED);
        event.setPayload("{\"transactionId\":7}");
        return event;
    }

    private static User user(Long id, String name) {
        User user = new User(name, name.toLowerCase() + "@example.com");
        user.setId(id);
        return user;
    }

    private static WebhookEndpoint endpoint(Long id) {
        WebhookEndpoint endpoint = new WebhookEndpoint();
        endpoint.setId(id);
        endpoint.setName("endpoint-" + id);
        endpoint.setUrl("https://example.com/hook/" + id);
        endpoint.setSecretToken("secret");
        endpoint.setStatus(WebhookEndpointStatus.ACTIVE);
        return endpoint;
    }
}
