package com.borrowbox.controller;

import com.borrowbox.dto.CounterOfferRequest;
import com.borrowbox.dto.TransactionCreateRequest;
import com.borrowbox.dto.TransactionDecisionRequest;
import com.borrowbox.dto.TransactionMessageRequest;
import com.borrowbox.dto.TransactionMessageResponse;
import com.borrowbox.dto.TransactionResponse;
import com.borrowbox.entity.User;
import com.borrowbox.service.TransactionMessageService;
import com.borrowbox.service.TransactionService;
import com.borrowbox.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class TransactionController {

    private final TransactionService transactionService;
    private final TransactionMessageService transactionMessageService;
    private final UserService userService;

    public TransactionController(TransactionService transactionService,
                                 TransactionMessageService transactionMessageService,
                                 UserService userService) {
        this.transactionService = transactionService;
        this.transactionMessageService = transactionMessageService;
        this.userService = userService;
    }

    @PostMapping("/transactions")
    public ResponseEntity<TransactionResponse> create(
            @Valid @RequestBody TransactionCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(transactionService.create(request, currentUser()));
    }

    @GetMapping("/me/requests")
    public ResponseEntity<List<TransactionResponse>> getMyRequests() {
        return ResponseEntity.ok(transactionService.listForBorrower(currentUser()));
    }

    @GetMapping("/me/lend-requests")
    public ResponseEntity<List<TransactionResponse>> getMyLendRequests() {
        return ResponseEntity.ok(transactionService.listForLender(currentUser()));
    }

    @GetMapping("/transactions/{id}")
    public ResponseEntity<TransactionResponse> getTransaction(@PathVariable Long id) {
        return ResponseEntity.ok(transactionService.view(id, currentUser()));
    }

    @PostMapping("/transactions/{id}/approve")
    public ResponseEntity<TransactionResponse> approve(
            @PathVariable Long id,
            @RequestBody(required = false) TransactionDecisionRequest request) {
        return ResponseEntity.ok(transactionService.approve(id, request, currentUser()));
    }

    @PostMapping("/transactions/{id}/reject")
    public ResponseEntity<TransactionResponse> reject(
            @PathVariable Long id,
            @RequestBody(required = false) TransactionDecisionRequest request) {
        return ResponseEntity.ok(transactionService.reject(id, request, currentUser()));
    }

    @PostMapping("/transactions/{id}/counter-offer")
    public ResponseEntity<TransactionResponse> counterOffer(
            @PathVariable Long id,
            @Valid @RequestBody CounterOfferRequest request) {
        return ResponseEntity.ok(transactionService.counterOffer(id, request, currentUser()));
    }

    @PostMapping("/transactions/{id}/accept-counter")
    public ResponseEntity<TransactionResponse> acceptCounter(@PathVariable Long id) {
        return ResponseEntity.ok(transactionService.acceptCounter(id, currentUser()));
    }

    @PostMapping("/transactions/{id}/cancel")
    public ResponseEntity<TransactionResponse> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(transactionService.cancel(id, currentUser()));
    }

    @PostMapping("/transactions/{id}/stage-handover")
    public ResponseEntity<TransactionResponse> stageHandover(@PathVariable Long id) {
        return ResponseEntity.ok(transactionService.stageHandover(id, currentUser()));
    }

    @PostMapping("/transactions/{id}/confirm-handover")
    public ResponseEntity<TransactionResponse> confirmHandover(@PathVariable Long id) {
        return ResponseEntity.ok(transactionService.confirmHandover(id, currentUser()));
    }

    @PostMapping("/transactions/{id}/initiate-return")
    public ResponseEntity<TransactionResponse> initiateReturn(@PathVariable Long id) {
        return ResponseEntity.ok(transactionService.initiateReturn(id, currentUser()));
    }

    @PostMapping("/transactions/{id}/report-handback")
    public ResponseEntity<TransactionResponse> reportHandback(@PathVariable Long id) {
        return ResponseEntity.ok(transactionService.reportHandback(id, currentUser()));
    }

    @PostMapping("/transactions/{id}/confirm-return")
    public ResponseEntity<TransactionResponse> confirmReturn(@PathVariable Long id) {
        return ResponseEntity.ok(transactionService.confirmReturn(id, currentUser()));
    }

    @PostMapping("/transactions/{id}/messages")
    public ResponseEntity<TransactionMessageResponse> sendMessage(
            @PathVariable Long id,
            @Valid @RequestBody TransactionMessageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(transactionMessageService.sendMessage(id, currentUser(), request.body()));
    }

    @GetMapping("/transactions/{id}/messages")
    public ResponseEntity<List<TransactionMessageResponse>> listMessages(@PathVariable Long id) {
        return ResponseEntity.ok(transactionMessageService.listMessages(id, currentUser()));
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