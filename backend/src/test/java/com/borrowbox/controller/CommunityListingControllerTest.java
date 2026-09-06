package com.borrowbox.controller;

import com.borrowbox.dto.ListingCreateRequest;
import com.borrowbox.dto.ListingResponse;
import com.borrowbox.entity.ListingStatus;
import com.borrowbox.entity.User;
import com.borrowbox.repository.UserRepository;
import com.borrowbox.service.CommunityListingService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CommunityListingController.class)
@AutoConfigureMockMvc(addFilters = false)
public class CommunityListingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CommunityListingService listingService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    private User currentUser;

    private ListingResponse listedResponse;

    @BeforeEach
    void setUp() {
        currentUser = new User("Ahmed", "ahmed@example.com");
        currentUser.setId(100L);
        when(userService.findByEmail("ahmed@example.com")).thenReturn(currentUser);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("ahmed@example.com", null,
                        Collections.singletonList(() -> "ROLE_USER")));

        listedResponse = new ListingResponse(
                701L, 500L, 900L, "CSE Department", ListingStatus.LISTED,
                LocalDateTime.of(2026, 1, 1, 9, 0),
                "Football", "Match-size football", null, null,
                2L, 1L, 1L);
    }

    @Test
    void postListingReturns201WhenCreated() throws Exception {
        when(listingService.createOrReactivate(eq(500L), any(ListingCreateRequest.class), eq(currentUser)))
                .thenReturn(new CommunityListingService.ListingResult(listedResponse, true));

        mockMvc.perform(post("/api/assets/500/listings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ListingCreateRequest(900L))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.assetId").value(500))
                .andExpect(jsonPath("$.communityId").value(900))
                .andExpect(jsonPath("$.communityName").value("CSE Department"))
                .andExpect(jsonPath("$.listingStatus").value("LISTED"))
                .andExpect(jsonPath("$.title").value("Football"))
                .andExpect(jsonPath("$.totalUnits").value(2))
                .andExpect(jsonPath("$.availableUnits").value(1))
                .andExpect(jsonPath("$.borrowedUnits").value(1));
    }

    @Test
    void postListingReturns200WhenReactivated() throws Exception {
        when(listingService.createOrReactivate(eq(500L), any(ListingCreateRequest.class), eq(currentUser)))
                .thenReturn(new CommunityListingService.ListingResult(listedResponse, false));

        mockMvc.perform(post("/api/assets/500/listings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ListingCreateRequest(900L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.listingStatus").value("LISTED"));
    }

    @Test
    void postListingWithoutCommunityIdIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/assets/500/listings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteListingReturnsUnlistedResponse() throws Exception {
        ListingResponse unlisted = new ListingResponse(
                701L, 500L, 900L, "CSE Department", ListingStatus.UNLISTED,
                LocalDateTime.of(2026, 1, 1, 9, 0),
                "Football", "Match-size football", null, null,
                2L, 1L, 1L);
        when(listingService.unlist(500L, 900L, currentUser)).thenReturn(unlisted);

        mockMvc.perform(delete("/api/assets/500/listings/900"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.listingStatus").value("UNLISTED"));
    }

    @Test
    void getAssetListingsReturnsOwnerView() throws Exception {
        when(listingService.listForAsset(500L, currentUser)).thenReturn(List.of(listedResponse));

        mockMvc.perform(get("/api/assets/500/listings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(701))
                .andExpect(jsonPath("$[0].listingStatus").value("LISTED"));
    }

    @Test
    void getCommunityListingsReturnsMemberView() throws Exception {
        when(listingService.listForCommunity(900L, currentUser)).thenReturn(List.of(listedResponse));

        mockMvc.perform(get("/api/communities/900/listings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].assetId").value(500))
                .andExpect(jsonPath("$[0].title").value("Football"));
    }
}