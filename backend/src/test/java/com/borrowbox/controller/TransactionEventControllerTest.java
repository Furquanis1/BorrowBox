package com.borrowbox.controller;

import com.borrowbox.dto.TransactionEventResponse;
import com.borrowbox.entity.TransactionEventType;
import com.borrowbox.entity.User;
import com.borrowbox.exception.ResourceNotFoundException;
import com.borrowbox.exception.UnauthorizedException;
import com.borrowbox.repository.UserRepository;
import com.borrowbox.service.JwtService;
import com.borrowbox.service.TransactionEventService;
import com.borrowbox.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransactionEventController.class)
@AutoConfigureMockMvc(addFilters = false)
public class TransactionEventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransactionEventService eventService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    private User currentUser;

    @BeforeEach
    void setUp() {
        currentUser = new User("Karim", "karim@example.com");
        currentUser.setId(100L);
    }

    private void authenticateAs(User user) {
        when(userService.findByEmail(user.getEmail())).thenReturn(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user.getEmail(), null,
                        Collections.singletonList(() -> "ROLE_USER")));
    }

    @Test
    void timelineIsReturnedForParticipant() throws Exception {
        authenticateAs(currentUser);
        when(eventService.getTimeline(eq(500L), eq(currentUser))).thenReturn(List.of(
                new TransactionEventResponse(1L, 500L, TransactionEventType.REQUEST_APPROVED,
                        100L, "Ahmed", null, LocalDateTime.of(2026, 7, 20, 10, 0)),
                new TransactionEventResponse(2L, 500L, TransactionEventType.LOAN_COMPLETED,
                        100L, "Ahmed", null, LocalDateTime.of(2026, 7, 22, 12, 30))));

        mockMvc.perform(get("/api/transactions/500/timeline"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].eventType").value("REQUEST_APPROVED"))
                .andExpect(jsonPath("$[1].eventType").value("LOAN_COMPLETED"))
                .andExpect(jsonPath("$[1].transactionId").value(500));
    }

    @Test
    void nonParticipantTimelineIsUnauthorized() throws Exception {
        authenticateAs(currentUser);
        when(eventService.getTimeline(eq(500L), eq(currentUser)))
                .thenThrow(new UnauthorizedException("Only participants can view the transaction timeline"));

        mockMvc.perform(get("/api/transactions/500/timeline"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Only participants can view the transaction timeline"));
    }

    @Test
    void unknownTransactionTimelineIsNotFound() throws Exception {
        authenticateAs(currentUser);
        when(eventService.getTimeline(eq(404L), eq(currentUser)))
                .thenThrow(new ResourceNotFoundException("Transaction not found: 404"));

        mockMvc.perform(get("/api/transactions/404/timeline"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Transaction not found: 404"));
    }
}
