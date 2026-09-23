package com.borrowbox.controller;

import com.borrowbox.dto.FlagCreateRequest;
import com.borrowbox.dto.FlagUpdateRequest;
import com.borrowbox.entity.Flag;
import com.borrowbox.entity.FlagStatus;
import com.borrowbox.entity.FlagType;
import com.borrowbox.entity.User;
import com.borrowbox.exception.UnauthorizedException;
import com.borrowbox.repository.UserRepository;
import com.borrowbox.service.FlagService;
import com.borrowbox.service.JwtService;
import com.borrowbox.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FlagController.class)
@AutoConfigureMockMvc(addFilters = false)
public class FlagControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private FlagService flagService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    private User currentUser;

    @BeforeEach
    void setUp() {
        currentUser = new User("Ahmed", "ahmed@example.com");
        currentUser.setId(100L);
        Mockito.when(userService.findByEmail("ahmed@example.com")).thenReturn(currentUser);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("ahmed@example.com", null,
                        Collections.singletonList(() -> "ROLE_USER")));
    }

    @Test
    void listFlagsSupportsCombinedFilters() throws Exception {
        Mockito.when(flagService.listFiltered(eq(900L), eq(FlagStatus.OPEN), eq(FlagType.OVERDUE),
                eq(50L), eq(currentUser)))
                .thenReturn(List.of(stubFlag(91L)));

        mockMvc.perform(get("/api/communities/900/flags")
                        .param("status", "OPEN")
                        .param("flagType", "OVERDUE")
                        .param("transactionId", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(91))
                .andExpect(jsonPath("$[0].flagType").value("OVERDUE"));
    }

    @Test
    void listFlagsAsNonManagerIsUnauthorized() throws Exception {
        Mockito.when(flagService.listFiltered(eq(900L), Mockito.any(), Mockito.any(),
                Mockito.any(), eq(currentUser)))
                .thenThrow(new UnauthorizedException("Only an active manager can manage community flags"));

        mockMvc.perform(get("/api/communities/900/flags"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createFlagReturnsCreatedAndReportsActingManager() throws Exception {
        Flag created = new Flag();
        created.setId(91L);
        created.setFlagType(FlagType.MANUAL);
        created.setStatus(FlagStatus.OPEN);
        created.setOccurredAt(LocalDateTime.now());
        created.setCreatedAt(LocalDateTime.now());
        created.setUpdatedAt(LocalDateTime.now());
        created.setCommunity(new com.borrowbox.entity.Community());
        Mockito.when(flagService.createFlag(eq(900L), eq(50L), eq(FlagType.MANUAL), eq("  note  "), eq(currentUser)))
                .thenAnswer(invocation -> {
                    Flag f = created;
                    // simulate note normalization handled by the service
                    return f;
                });

        mockMvc.perform(post("/api/communities/900/flags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new FlagCreateRequest(FlagType.MANUAL, 50L, "  note  "))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(91));

        verify(flagService).createFlag(eq(900L), eq(50L), eq(FlagType.MANUAL), eq("  note  "), eq(currentUser));
    }

    @Test
    void createFlagWithoutFlagTypeIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/communities/900/flags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transactionId\": 50}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getFlagReturnsOwnedFlag() throws Exception {
        Mockito.when(flagService.getFlag(eq(900L), eq(91L), eq(currentUser)))
                .thenReturn(stubFlag(91L));

        mockMvc.perform(get("/api/communities/900/flags/91"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(91))
                .andExpect(jsonPath("$.flagType").value("OVERDUE"));
    }

    @Test
    void updateFlagAppliesPatch() throws Exception {
        Mockito.when(flagService.updateFlag(eq(900L), eq(91L), Mockito.any(FlagUpdateRequest.class), eq(currentUser)))
                .thenReturn(stubFlag(91L));

        mockMvc.perform(patch("/api/communities/900/flags/91")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"REVIEWED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"));
        verify(flagService).updateFlag(eq(900L), eq(91L), Mockito.any(FlagUpdateRequest.class), eq(currentUser));
    }

    private Flag stubFlag(Long id) {
        Flag f = new Flag();
        f.setId(id);
        f.setFlagType(FlagType.OVERDUE);
        f.setStatus(FlagStatus.OPEN);
        f.setOccurredAt(LocalDateTime.now());
        f.setCreatedAt(LocalDateTime.now());
        f.setUpdatedAt(LocalDateTime.now());
        com.borrowbox.entity.Community c = new com.borrowbox.entity.Community();
        c.setId(900L);
        f.setCommunity(c);
        return f;
    }
}