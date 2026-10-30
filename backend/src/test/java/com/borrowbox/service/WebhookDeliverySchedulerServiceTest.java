package com.borrowbox.service;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.mockito.Mockito.verify;

class WebhookDeliverySchedulerServiceTest {

    @Test
    void retryPendingDeliveriesInvokesDeliveryService() {
        WebhookDeliveryService service = Mockito.mock(WebhookDeliveryService.class);
        WebhookDeliverySchedulerService scheduler = new WebhookDeliverySchedulerService(service);

        scheduler.retryPendingDeliveries();

        verify(service).retryPendingDeliveries();
    }
}
