package com.borrowbox.controller;

import com.borrowbox.dto.AssetCreateRequest;
import com.borrowbox.dto.AssetResponse;
import com.borrowbox.entity.AssetStatus;
import com.borrowbox.entity.User;
import com.borrowbox.exception.ResourceNotFoundException;
import com.borrowbox.repository.UserRepository;
import com.borrowbox.service.AssetService;
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

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AssetController.class)
@AutoConfigureMockMvc(addFilters = false)
public class AssetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AssetService assetService;

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
    void createAssetReturnsCreated() throws Exception {
        AssetCreateRequest req = new AssetCreateRequest("Football", "desc", null, 2);
        AssetResponse resp = new AssetResponse(
                1L, "Football", "desc", null, null,
                AssetStatus.ACTIVE, 2L, 2L, 0L);
        Mockito.when(assetService.createAsset(any(), eq(currentUser))).thenReturn(resp);

        mockMvc.perform(post("/api/assets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("Football"))
                .andExpect(jsonPath("$.totalUnits").value(2))
                .andExpect(jsonPath("$.availableUnits").value(2));
    }

    @Test
    void getMyAssetsReturnsList() throws Exception {
        AssetResponse resp = new AssetResponse(
                1L, "Football", "desc", null, null,
                AssetStatus.ACTIVE, 2L, 2L, 0L);
        Mockito.when(assetService.listAssetsForUser(currentUser)).thenReturn(List.of(resp));

        mockMvc.perform(get("/api/assets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Football"));
    }

    @Test
    void getAssetReturnsOwnedAsset() throws Exception {
        AssetResponse resp = new AssetResponse(
                1L, "Football", "desc", null, null,
                AssetStatus.ACTIVE, 2L, 1L, 1L);
        Mockito.when(assetService.getAssetById(eq(1L), eq(currentUser))).thenReturn(resp);

        mockMvc.perform(get("/api/assets/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.borrowedUnits").value(1));
    }

    @Test
    void nonOwnerGetAssetReturnsNotFound() throws Exception {
        Mockito.when(assetService.getAssetById(eq(1L), eq(currentUser)))
                .thenThrow(new ResourceNotFoundException("Asset not found with id: 1"));

        mockMvc.perform(get("/api/assets/1"))
                .andExpect(status().isNotFound());
    }

    @Test
    void archiveAssetReturnsArchived() throws Exception {
        AssetResponse resp = new AssetResponse(
                1L, "Football", "desc", null, null,
                AssetStatus.ARCHIVED, 0L, 0L, 0L);
        Mockito.when(assetService.archiveAsset(eq(1L), eq(currentUser))).thenReturn(resp);

        mockMvc.perform(patch("/api/assets/1/archive"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
    }
}
