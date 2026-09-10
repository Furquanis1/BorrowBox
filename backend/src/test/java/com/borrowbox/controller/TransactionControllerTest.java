package com.borrowbox.controller;

import com.borrowbox.dto.CounterOfferRequest;
import com.borrowbox.dto.TransactionCreateRequest;
import com.borrowbox.dto.TransactionDecisionRequest;
import com.borrowbox.dto.TransactionResponse;
import com.borrowbox.entity.TransactionStatus;
import com.borrowbox.entity.User;
import com.borrowbox.repository.UserRepository;
import com.borrowbox.service.JwtService;
import com.borrowbox.service.TransactionService;
import com.borrowbox.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransactionController.class)
@AutoConfigureMockMvc(addFilters = false)
public class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TransactionService transactionService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    private User currentUser;

    private TransactionResponse pendingResponse;

    @BeforeEach
    void setUp() {
        currentUser = new User("Ahmed", "ahmed@example.com");
        currentUser.setId(100L);
        when(userService.findByEmail("ahmed@example.com")).thenReturn(currentUser);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("ahmed@example.com", null,
                        Collections.singletonList(() -> "ROLE_USER")));

        pendingResponse = new TransactionResponse(
                1L, 900L, "CSE Department", 701L, 500L, "Football",
                100L, "Ahmed", 101L, "Salah",
                TransactionStatus.PENDING, "Football match practice", 3,
                null, null, null, null,
                null, null, null,
                null, true,
                LocalDateTime.of(2026, 1, 2, 10, 0),
                LocalDateTime.of(2026, 1, 2, 10, 0));
    }

    @Test
    void createTransactionReturns201() throws Exception {
        when(transactionService.create(any(TransactionCreateRequest.class), eq(currentUser)))
                .thenReturn(pendingResponse);

        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new TransactionCreateRequest(701L, "Football match practice", 3))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.state").value("PENDING"))
                .andExpect(jsonPath("$.lenderId").value(100))
                .andExpect(jsonPath("$.borrowerId").value(101))
                .andExpect(jsonPath("$.title").value("Football"))
                .andExpect(jsonPath("$.purpose").value("Football match practice"))
                .andExpect(jsonPath("$.requestedDurationDays").value(3))
                .andExpect(jsonPath("$.reservationHeld").value(true));
    }

    @Test
    void createTransactionWithBlankPurposeIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"listingId\":701,\"purpose\":\"   \",\"requestedDurationDays\":3}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createTransactionWithInvalidDurationIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"listingId\":701,\"purpose\":\"Match\",\"requestedDurationDays\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createTransactionWithoutListingIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"Match\",\"requestedDurationDays\":3}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getMyRequestsReturnsBorrowerList() throws Exception {
        when(transactionService.listForBorrower(currentUser)).thenReturn(List.of(pendingResponse));

        mockMvc.perform(get("/api/me/requests"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].state").value("PENDING"));
    }

    @Test
    void getMyLendRequestsReturnsLenderList() throws Exception {
        when(transactionService.listForLender(currentUser)).thenReturn(List.of(pendingResponse));

        mockMvc.perform(get("/api/me/lend-requests"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    void getTransactionReturnsSingleView() throws Exception {
        when(transactionService.view(1L, currentUser)).thenReturn(pendingResponse);

        mockMvc.perform(get("/api/transactions/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationHeld").value(true))
                .andExpect(jsonPath("$.communityName").value("CSE Department"));
    }

    @Test
    void approveWithNoteDelegatesDecision() throws Exception {
        when(transactionService.approve(eq(1L), any(TransactionDecisionRequest.class), eq(currentUser)))
                .thenReturn(pendingResponse);

        mockMvc.perform(post("/api/transactions/1/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TransactionDecisionRequest("Looks good"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("PENDING"));
    }

    @Test
    void approveWithoutBodyDelegatesNullDecision() throws Exception {
        when(transactionService.approve(eq(1L), isNull(), eq(currentUser)))
                .thenReturn(pendingResponse);

        mockMvc.perform(post("/api/transactions/1/approve"))
                .andExpect(status().isOk());
    }

    @Test
    void rejectDelegatesDecision() throws Exception {
        when(transactionService.reject(eq(1L), any(TransactionDecisionRequest.class), eq(currentUser)))
                .thenReturn(pendingResponse);

        mockMvc.perform(post("/api/transactions/1/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TransactionDecisionRequest("No"))))
                .andExpect(status().isOk());
    }

    @Test
    void counterOfferDelegatesAndReturnsResponse() throws Exception {
        when(transactionService.counterOffer(eq(1L), any(CounterOfferRequest.class), eq(currentUser)))
                .thenReturn(pendingResponse);

        mockMvc.perform(post("/api/transactions/1/counter-offer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CounterOfferRequest("Football match practice", 5, "Saturday"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationHeld").value(true));
    }

    @Test
    void counterOfferWithInvalidDurationIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/transactions/1/counter-offer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestedDurationDays\":31}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void acceptCounterDelegates() throws Exception {
        when(transactionService.acceptCounter(1L, currentUser)).thenReturn(pendingResponse);

        mockMvc.perform(post("/api/transactions/1/accept-counter"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("PENDING"));
    }

    @Test
    void cancelDelegates() throws Exception {
        when(transactionService.cancel(1L, currentUser)).thenReturn(pendingResponse);

        mockMvc.perform(post("/api/transactions/1/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("PENDING"));
    }

    @Test
    void responseDoesNotLeakUnitIds() throws Exception {
        when(transactionService.view(1L, currentUser)).thenReturn(pendingResponse);
        when(transactionService.create(any(TransactionCreateRequest.class), eq(currentUser)))
                .thenReturn(pendingResponse);

        String body = mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new TransactionCreateRequest(701L, "Match", 3))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(body)
                .doesNotContain("reservedUnit")
                .doesNotContain("assetUnitId");
    }
}