package com.borrowbox.controller;

import com.borrowbox.dto.EvidenceResponse;
import com.borrowbox.entity.EvidenceType;
import com.borrowbox.entity.User;
import com.borrowbox.service.TransactionService;
import com.borrowbox.service.UserService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * V2.2.6 evidence endpoints. The binary payload exists only behind the
 * participant-only content endpoint; evidence is never statically served.
 */
@RestController
@RequestMapping("/api")
public class EvidenceController {

    private final TransactionService transactionService;
    private final UserService userService;

    public EvidenceController(TransactionService transactionService, UserService userService) {
        this.transactionService = transactionService;
        this.userService = userService;
    }

    @PostMapping("/transactions/{id}/evidence")
    public ResponseEntity<EvidenceResponse> uploadEvidence(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @RequestParam("type") EvidenceType type,
            @RequestParam(value = "conditionNote", required = false) String conditionNote,
            @RequestParam(value = "conditionRating", required = false) Integer conditionRating) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(transactionService.uploadEvidence(id, type, file, conditionNote, conditionRating, currentUser()));
    }

    @GetMapping("/transactions/{id}/evidence")
    public ResponseEntity<List<EvidenceResponse>> listEvidence(@PathVariable Long id) {
        return ResponseEntity.ok(transactionService.listEvidence(id, currentUser()));
    }

    @GetMapping("/evidence/{evidenceId}/content")
    public ResponseEntity<byte[]> getContent(@PathVariable Long evidenceId) {
        TransactionService.EvidenceContent content =
                transactionService.getEvidenceContent(evidenceId, currentUser());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .cacheControl(CacheControl.noStore().mustRevalidate())
                .body(content.bytes());
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