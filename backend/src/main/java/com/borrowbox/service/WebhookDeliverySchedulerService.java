package com.borrowbox.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class WebhookDeliverySchedulerService {

    private final WebhookDeliveryService webhookDeliveryService;

    public WebhookDeliverySchedulerService(WebhookDeliveryService webhookDeliveryService) {
        this.webhookDeliveryService = webhookDeliveryService;
    }

    @Scheduled(
            fixedDelayString = "${borrowbox.webhook.retry-interval-ms:60000}",
            initialDelayString = "${borrowbox.webhook.retry-initial-delay-ms:30000}")
    public void retryPendingDeliveries() {
        webhookDeliveryService.retryPendingDeliveries();
    }
}
