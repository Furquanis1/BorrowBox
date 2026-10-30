package com.borrowbox.controller;

import com.borrowbox.entity.WebhookEndpoint;
import com.borrowbox.entity.WebhookEndpointStatus;
import com.borrowbox.repository.UserRepository;
import com.borrowbox.repository.WebhookDeliveryRepository;
import com.borrowbox.repository.WebhookEndpointRepository;
import com.borrowbox.service.JwtService;
import com.borrowbox.service.UserService;
import com.borrowbox.service.WebhookDeliveryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Base64;
import java.util.Set;
import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WebhookEndpointController.class)
class WebhookEndpointControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private WebhookEndpointRepository endpointRepository;

    @MockitoBean
    private WebhookDeliveryRepository deliveryRepository;

    @MockitoBean
    private WebhookDeliveryService deliveryService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void generateSecretToken_shouldBeAtLeast32Bytes() {
        WebhookEndpointController controller = new WebhookEndpointController(
            null, null, null, null
        );

        String token = controller.generateSecretToken();
        byte[] decoded = Base64.getUrlDecoder().decode(token);

        assertThat(decoded.length).isGreaterThanOrEqualTo(32);
    }

    @Test
    void generateSecretToken_shouldNotRepeat() {
        WebhookEndpointController controller = new WebhookEndpointController(
            null, null, null, null
        );

        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            tokens.add(controller.generateSecretToken());
        }

        assertThat(tokens).hasSize(100);
    }
}
