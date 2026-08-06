package com.borrowbox.service;

import com.borrowbox.dto.BorrowRequestCreateRequest;
import com.borrowbox.entity.BorrowRecord;
import com.borrowbox.entity.BorrowRequest;
import com.borrowbox.entity.BorrowRequestStatus;
import com.borrowbox.entity.Item;
import com.borrowbox.entity.ItemStatus;
import com.borrowbox.entity.User;
import com.borrowbox.exception.BusinessRuleViolationException;
import com.borrowbox.exception.ResourceNotFoundException;
import com.borrowbox.repository.BorrowRecordRepository;
import com.borrowbox.repository.BorrowRequestRepository;
import com.borrowbox.repository.ItemRepository;
import com.borrowbox.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@Transactional
public class BorrowRequestService {

    private final BorrowRequestRepository borrowRequestRepository;
    private final ItemRepository itemRepository;
    private final UserRepository userRepository;
    private final BorrowRecordRepository borrowRecordRepository;

    public BorrowRequestService(BorrowRequestRepository borrowRequestRepository,
                                ItemRepository itemRepository,
                                UserRepository userRepository,
                                BorrowRecordRepository borrowRecordRepository) {
        this.borrowRequestRepository = borrowRequestRepository;
        this.itemRepository = itemRepository;
        this.userRepository = userRepository;
        this.borrowRecordRepository = borrowRecordRepository;
    }

    public List<BorrowRequest> getAllBorrowRequests() {
        return borrowRequestRepository.findAll();
    }

    /**
     * Creates a borrow request using the authenticated user from the JWT cookie.
     * The requestedByUserId field in the request body is ignored if a JWT user is
     * present, preventing clients from impersonating another user.
     */
    public BorrowRequest createBorrowRequest(BorrowRequestCreateRequest request) {
        Item item = itemRepository.findById(Objects.requireNonNull(request.itemId()))
                .orElseThrow(() -> new ResourceNotFoundException("Item not found with id: " + request.itemId()));

        // Prefer the authenticated user over whatever the client sent in the body
        User user = resolveCurrentUser();
        if (user == null) {
            // Fall back to body-provided id (allows tests / Swagger to work without a cookie)
            user = userRepository.findById(Objects.requireNonNull(request.requestedByUserId()))
                    .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + request.requestedByUserId()));
        }

        if (item.isArchived()) {
            throw new BusinessRuleViolationException("Cannot request an archived item: " + item.getId());
        }

        if (item.getStatus() != ItemStatus.AVAILABLE) {
            throw new BusinessRuleViolationException("Item is not available for request: " + item.getId());
        }

        // Prevent self-borrowing
        if (item.getOwner() != null && Objects.equals(item.getOwner().getId(), user.getId())) {
            throw new BusinessRuleViolationException("Cannot request your own item: " + item.getId());
        }

        // Mark item as REQUESTED so no duplicate requests can be submitted
        item.setStatus(ItemStatus.REQUESTED);
        itemRepository.save(item);

        BorrowRequest borrowRequest = new BorrowRequest(item, user, request.message());
        return borrowRequestRepository.save(borrowRequest);
    }

    public BorrowRequest getBorrowRequestById(Long id) {
        return borrowRequestRepository.findById(Objects.requireNonNull(id))
                .orElseThrow(() -> new ResourceNotFoundException("Borrow request not found with id: " + id));
    }

    public BorrowRequest updateBorrowRequest(Long id, BorrowRequestCreateRequest request) {
        BorrowRequest existingRequest = getBorrowRequestById(id);
        Item item = itemRepository.findById(Objects.requireNonNull(request.itemId()))
                .orElseThrow(() -> new ResourceNotFoundException("Item not found with id: " + request.itemId()));
        User user = userRepository.findById(Objects.requireNonNull(request.requestedByUserId()))
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + request.requestedByUserId()));

        existingRequest.setItem(item);
        existingRequest.setRequestedBy(user);
        existingRequest.setMessage(request.message());
        return borrowRequestRepository.save(existingRequest);
    }

    public void deleteBorrowRequest(Long id) {
        BorrowRequest existingRequest = getBorrowRequestById(id);
        borrowRequestRepository.delete(Objects.requireNonNull(existingRequest));
    }

    /**
     * Approves a pending borrow request. Item status is set to APPROVED so that
     * a BorrowRecord can be created against it.
     */
    public BorrowRequest approveBorrowRequest(Long id) {
        BorrowRequest request = getBorrowRequestById(id);
        if (request.getStatus() != BorrowRequestStatus.PENDING) {
            throw new BusinessRuleViolationException("Only pending requests can be approved: " + request.getId());
        }

        if (request.getItem().isArchived()) {
            throw new BusinessRuleViolationException("Cannot approve a request for an archived item: " + request.getItem().getId());
        }

        if (request.getItem().getStatus() != ItemStatus.REQUESTED) {
            throw new BusinessRuleViolationException("Item is not in a requestable state: " + request.getItem().getId());
        }

        request.setStatus(BorrowRequestStatus.APPROVED);
        Item item = request.getItem();
        item.setStatus(ItemStatus.APPROVED);
        itemRepository.save(item);
        return borrowRequestRepository.save(request);
    }

    /**
     * Rejects a pending borrow request. Item status is restored to AVAILABLE so
     * others can submit new requests.
     */
    public BorrowRequest rejectBorrowRequest(Long id) {
        BorrowRequest request = getBorrowRequestById(id);
        if (request.getStatus() != BorrowRequestStatus.PENDING) {
            throw new BusinessRuleViolationException("Only pending requests can be rejected: " + request.getId());
        }

        request.setStatus(BorrowRequestStatus.REJECTED);

        // Restore item availability so new requests can come in
        Item item = request.getItem();
        item.setStatus(ItemStatus.AVAILABLE);
        itemRepository.save(item);
        return borrowRequestRepository.save(request);
    }

    /**
     * Convenience endpoint: approves a PENDING request and immediately creates
     * a BorrowRecord in one atomic transaction. The caller provides a dueAt date.
     */
    public BorrowRecord confirmBorrowRequest(Long id, LocalDateTime dueAt) {
        BorrowRequest request = getBorrowRequestById(id);
        if (request.getStatus() != BorrowRequestStatus.PENDING) {
            throw new BusinessRuleViolationException("Only pending requests can be confirmed: " + request.getId());
        }

        // Approve first
        request.setStatus(BorrowRequestStatus.APPROVED);
        Item item = request.getItem();
        item.setStatus(ItemStatus.APPROVED);
        itemRepository.save(item);
        borrowRequestRepository.save(request);

        // Create the BorrowRecord immediately
        LocalDateTime now = LocalDateTime.now();
        BorrowRecord record = new BorrowRecord(request, item, request.getRequestedBy(), now, dueAt);
        request.setStatus(BorrowRequestStatus.COMPLETED);
        item.setStatus(ItemStatus.BORROWED);
        itemRepository.save(item);
        borrowRequestRepository.save(request);
        return borrowRecordRepository.save(record);
    }

    /** Resolves the authenticated user from the Spring SecurityContext. */
    private User resolveCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        String email = auth.getName();
        return userRepository.findByEmail(email).orElse(null);
    }
}