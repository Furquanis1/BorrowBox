package com.borrowbox.controller;

import com.borrowbox.dto.WaitlistEntryResponse;
import com.borrowbox.dto.WaitlistJoinRequest;
import com.borrowbox.entity.User;
import com.borrowbox.entity.WaitlistStatus;
import com.borrowbox.exception.BusinessRuleViolationException;
import com.borrowbox.exception.ResourceNotFoundException;
import com.borrowbox.exception.UnauthorizedException;
import com.borrowbox.repository.UserRepository;
import com.borrowbox.service.JwtService;
import com.borrowbox.service.UserService;
import com.borrowbox.service.WaitlistService;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WaitlistController.class)
@AutoConfigureMockMvc(addFilters = false)
public class WaitlistControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private WaitlistService waitlistService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    private User currentUser;

    private WaitlistEntryResponse waitingResponse;

    @BeforeEach
    void setUp() {
        currentUser = new User("Ahmed", "ahmed@example.com");
        currentUser.setId(100L);
        when(userService.findByEmail("ahmed@example.com")).thenReturn(currentUser);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("ahmed@example.com", null,
                        Collections.singletonList(() -> "ROLE_USER")));

        waitingResponse = new WaitlistEntryResponse(
                1L, 900L, "Football", 701L, 500L, "CSE Department",
                100L, "Football match practice", 3,
                WaitlistStatus.WAITING, 1,
                LocalDateTime.of(2026, 1, 2, 10, 0), null);
    }

    @Test
    void joinReturns201WithEntry() throws Exception {
        when(waitlistService.join(eq(701L), any(WaitlistJoinRequest.class), eq(currentUser)))
                .thenReturn(waitingResponse);

        mockMvc.perform(post("/api/listings/701/waitlist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new WaitlistJoinRequest("Football match practice", 3))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.assetId").value(900))
                .andExpect(jsonPath("$.assetTitle").value("Football"))
                .andExpect(jsonPath("$.position").value(1))
                .andExpect(jsonPath("$.status").value("WAITING"));
    }

    @Test
    void joinWithBlankPurposeIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/listings/701/waitlist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"   \",\"requestedDurationDays\":3}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void joinWithoutPurposeIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/listings/701/waitlist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestedDurationDays\":3}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void joinWithInvalidDurationIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/listings/701/waitlist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"Practice\",\"requestedDurationDays\":31}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void joinWhenUnitIsAvailableIsBadRequest() throws Exception {
        when(waitlistService.join(eq(701L), any(WaitlistJoinRequest.class), eq(currentUser)))
                .thenThrow(new BusinessRuleViolationException(
                        "An asset unit is currently available — submit a normal request instead"));

        mockMvc.perform(post("/api/listings/701/waitlist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new WaitlistJoinRequest("Practice", 2))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void joinToUnknownListingIsNotFound() throws Exception {
        when(waitlistService.join(eq(999L), any(WaitlistJoinRequest.class), eq(currentUser)))
                .thenThrow(new ResourceNotFoundException("Listing not found with id: 999"));

        mockMvc.perform(post("/api/listings/999/waitlist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new WaitlistJoinRequest("Practice", 2))))
                .andExpect(status().isNotFound());
    }

    @Test
    void joinForNonMemberIsUnauthorized() throws Exception {
        when(waitlistService.join(eq(701L), any(WaitlistJoinRequest.class), eq(currentUser)))
                .thenThrow(new UnauthorizedException(
                        "Only active members of this community can use its transactions"));

        mockMvc.perform(post("/api/listings/701/waitlist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new WaitlistJoinRequest("Practice", 2))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getMyWaitlistReturnsEntries() throws Exception {
        when(waitlistService.listForBorrower(currentUser)).thenReturn(List.of(waitingResponse));

        mockMvc.perform(get("/api/me/waitlist"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].status").value("WAITING"))
                .andExpect(jsonPath("$[0].position").value(1));
    }

    @Test
    void leaveDelegatesAndReturnsEntry() throws Exception {
        when(waitlistService.leave(eq(1L), eq(currentUser))).thenReturn(waitingResponse);

        mockMvc.perform(delete("/api/me/waitlist/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.assetTitle").value("Football"));
    }

    @Test
    void leaveUnknownEntryIsNotFound() throws Exception {
        when(waitlistService.leave(eq(5L), eq(currentUser)))
                .thenThrow(new ResourceNotFoundException("Waitlist entry not found with id: 5"));

        mockMvc.perform(delete("/api/me/waitlist/5"))
                .andExpect(status().isNotFound());
    }
}
