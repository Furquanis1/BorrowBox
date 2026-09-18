package com.borrowbox.controller;

import com.borrowbox.dto.TransactionEventDeliveryResponse;
import com.borrowbox.entity.TransactionEventDeliveryStatus;
import com.borrowbox.entity.User;
import com.borrowbox.service.TransactionEventService;
import com.borrowbox.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class TransactionEventController {

    private final TransactionEventService eventService;
    private final UserService userService;

    public TransactionEventController(TransactionEventService eventService, UserService userService) {
        this.eventService = eventService;
        this.userService = userService;
    }

    @GetMapping("/me/events")
    public ResponseEntity<List<TransactionEventDeliveryResponse>> getMyEvents(
            @RequestParam(required = false) String status) {
        User user = currentUser();
        List<TransactionEventDeliveryStatus> statuses = parseStatuses(status);
        return ResponseEntity.ok(eventService.getMine(user, statuses));
    }

    @GetMapping("/transactions/{id}/events")
    public ResponseEntity<List<TransactionEventDeliveryResponse>> getTransactionEvents(
            @PathVariable Long id) {
        User user = currentUser();
        return ResponseEntity.ok(eventService.getByTransaction(id, user));
    }

    @PostMapping("/me/events/{deliveryId}/read")
    public ResponseEntity<TransactionEventDeliveryResponse> markRead(@PathVariable Long deliveryId) {
        User user = currentUser();
        return ResponseEntity.ok(eventService.markRead(deliveryId, user));
    }

    @PostMapping("/me/events/{deliveryId}/dismiss")
    public ResponseEntity<TransactionEventDeliveryResponse> dismiss(@PathVariable Long deliveryId) {
        User user = currentUser();
        return ResponseEntity.ok(eventService.dismiss(deliveryId, user));
    }

    @PostMapping("/me/events/read-all")
    public ResponseEntity<Void> markAllRead() {
        User user = currentUser();
        eventService.markAllRead(user);
        return ResponseEntity.ok().build();
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

    private List<TransactionEventDeliveryStatus> parseStatuses(String statusParam) {
        if (statusParam == null || statusParam.isBlank()) {
            return Arrays.asList(
                    TransactionEventDeliveryStatus.UNREAD,
                    TransactionEventDeliveryStatus.READ,
                    TransactionEventDeliveryStatus.DISMISSED
            );
        }
        return Arrays.stream(statusParam.split(","))
                .map(String::trim)
                .map(s -> TransactionEventDeliveryStatus.valueOf(s.toUpperCase()))
                .collect(Collectors.toList());
    }
}