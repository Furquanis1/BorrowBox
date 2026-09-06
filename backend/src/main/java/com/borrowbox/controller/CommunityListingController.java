package com.borrowbox.controller;

import com.borrowbox.dto.ListingCreateRequest;
import com.borrowbox.dto.ListingResponse;
import com.borrowbox.entity.User;
import com.borrowbox.service.CommunityListingService;
import com.borrowbox.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class CommunityListingController {

    private final CommunityListingService listingService;
    private final UserService userService;

    public CommunityListingController(CommunityListingService listingService, UserService userService) {
        this.listingService = listingService;
        this.userService = userService;
    }

    @PostMapping("/assets/{assetId}/listings")
    public ResponseEntity<ListingResponse> createListing(
            @PathVariable Long assetId,
            @Valid @RequestBody ListingCreateRequest request) {
        CommunityListingService.ListingResult result =
                listingService.createOrReactivate(assetId, request, currentUser());
        return ResponseEntity
                .status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(result.response());
    }

    @DeleteMapping("/assets/{assetId}/listings/{communityId}")
    public ResponseEntity<ListingResponse> unlist(
            @PathVariable Long assetId,
            @PathVariable Long communityId) {
        return ResponseEntity.ok(listingService.unlist(assetId, communityId, currentUser()));
    }

    @GetMapping("/assets/{assetId}/listings")
    public ResponseEntity<List<ListingResponse>> getAssetListings(@PathVariable Long assetId) {
        return ResponseEntity.ok(listingService.listForAsset(assetId, currentUser()));
    }

    @GetMapping("/communities/{communityId}/listings")
    public ResponseEntity<List<ListingResponse>> getCommunityListings(@PathVariable Long communityId) {
        return ResponseEntity.ok(listingService.listForCommunity(communityId, currentUser()));
    }

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email;
        Object principal = auth.getPrincipal();
        if (principal instanceof UserDetails ud) {
            email = ud.getUsername();
        } else {
            email = principal.toString();
        }
        return userService.findByEmail(email);
    }
}