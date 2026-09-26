package com.borrowbox.controller;

import com.borrowbox.dto.EvidenceResponse;
import com.borrowbox.entity.EvidenceType;
import com.borrowbox.entity.User;
import com.borrowbox.repository.UserRepository;
import com.borrowbox.service.JwtService;
import com.borrowbox.service.TransactionService;
import com.borrowbox.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EvidenceController.class)
@AutoConfigureMockMvc(addFilters = false)
public class EvidenceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransactionService transactionService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    private User currentUser;

    private EvidenceResponse evidenceResponse;

    @BeforeEach
    void setUp() {
        currentUser = new User("Salah", "salah@example.com");
        currentUser.setId(101L);
        when(userService.findByEmail("salah@example.com")).thenReturn(currentUser);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("salah@example.com", null,
                        Collections.singletonList(() -> "ROLE_USER")));

        evidenceResponse = new EvidenceResponse(
                7L, 1L, EvidenceType.BORROWER_PRE_RETURN,
                101L, "Salah", "image/png", 3L,
                LocalDateTime.of(2026, 6, 1, 10, 0),
                LocalDateTime.of(2026, 6, 1, 10, 0),
                "/api/evidence/7/content",
                null, null);
    }

    @Test
    void uploadEvidenceReturnsCreatedWithResponse() throws Exception {
        when(transactionService.uploadEvidence(
                eq(1L), eq(EvidenceType.BORROWER_PRE_RETURN), any(org.springframework.web.multipart.MultipartFile.class),
                isNull(), isNull(), eq(currentUser)))
                .thenReturn(evidenceResponse);
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/transactions/1/evidence")
                        .file(file)
                        .param("type", "BORROWER_PRE_RETURN"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.type").value("BORROWER_PRE_RETURN"))
                .andExpect(jsonPath("$.contentUrl").value("/api/evidence/7/content"))
                .andExpect(jsonPath("$.conditionNote").isEmpty())
                .andExpect(jsonPath("$.conditionRating").isEmpty());
    }

    @Test
    void uploadEvidencePassesOptionalConditionMetadata() throws Exception {
        when(transactionService.uploadEvidence(
                eq(1L), eq(EvidenceType.LENDER_PRE_LENDING), any(org.springframework.web.multipart.MultipartFile.class),
                eq("Minor scratches"), eq(4), eq(currentUser)))
                .thenReturn(new EvidenceResponse(
                        8L, 1L, EvidenceType.LENDER_PRE_LENDING,
                        100L, "Ahmed", "image/png", 3L,
                        LocalDateTime.of(2026, 6, 1, 10, 0),
                        LocalDateTime.of(2026, 6, 1, 10, 0),
                        "/api/evidence/8/content",
                        "Minor scratches", 4));
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/transactions/1/evidence")
                        .file(file)
                        .param("type", "LENDER_PRE_LENDING")
                        .param("conditionNote", "Minor scratches")
                        .param("conditionRating", "4"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.conditionNote").value("Minor scratches"))
                .andExpect(jsonPath("$.conditionRating").value(4));
    }

    @Test
    void uploadEvidenceWithUnknownTypeIsBadRequest() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", new byte[]{1});

        mockMvc.perform(multipart("/api/transactions/1/evidence")
                        .file(file)
                        .param("type", "NOT_A_MOMENT"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listEvidenceReturnsEvidenceList() throws Exception {
        when(transactionService.listEvidence(1L, currentUser)).thenReturn(List.of(evidenceResponse));

        mockMvc.perform(get("/api/transactions/1/evidence"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(7))
                .andExpect(jsonPath("$[0].capturerName").value("Salah"));
    }

    @Test
    void getContentReturnsStoredBytesWithContentType() throws Exception {
        when(transactionService.getEvidenceContent(7L, currentUser))
                .thenReturn(new TransactionService.EvidenceContent(new byte[]{1, 2, 3}, "image/png"));

        mockMvc.perform(get("/api/evidence/7/content"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(new byte[]{1, 2, 3}));
    }
}