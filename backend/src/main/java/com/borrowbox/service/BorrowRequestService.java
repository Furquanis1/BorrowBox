package com.borrowbox.service;

import com.borrowbox.dto.BorrowRequestCreateRequest;
import com.borrowbox.entity.BorrowRequest;
import com.borrowbox.entity.Item;
import com.borrowbox.entity.ItemStatus;
import com.borrowbox.entity.User;
import com.borrowbox.exception.BusinessRuleViolationException;
import com.borrowbox.exception.ResourceNotFoundException;
import com.borrowbox.repository.BorrowRequestRepository;
import com.borrowbox.repository.ItemRepository;
import com.borrowbox.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@Transactional
public class BorrowRequestService {

    private final BorrowRequestRepository borrowRequestRepository;
    private final ItemRepository itemRepository;
    private final UserRepository userRepository;

    public BorrowRequestService(BorrowRequestRepository borrowRequestRepository, ItemRepository itemRepository, UserRepository userRepository) {
        this.borrowRequestRepository = borrowRequestRepository;
        this.itemRepository = itemRepository;
        this.userRepository = userRepository;
    }

    public List<BorrowRequest> getAllBorrowRequests() {
        return borrowRequestRepository.findAll();
    }

    public BorrowRequest createBorrowRequest(BorrowRequestCreateRequest request) {
        Item item = itemRepository.findById(Objects.requireNonNull(request.itemId()))
                .orElseThrow(() -> new ResourceNotFoundException("Item not found with id: " + request.itemId()));
        User user = userRepository.findById(Objects.requireNonNull(request.requestedByUserId()))
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + request.requestedByUserId()));

        if (item.isArchived()) {
            throw new BusinessRuleViolationException("Cannot request an archived item: " + item.getId());
        }

        if (item.getStatus() != ItemStatus.AVAILABLE) {
            throw new BusinessRuleViolationException("Item is not available for request: " + item.getId());
        }

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

    public BorrowRequest approveBorrowRequest(Long id) {
        BorrowRequest request = getBorrowRequestById(id);
        if (request.getStatus() != com.borrowbox.entity.BorrowRequestStatus.PENDING) {
            throw new BusinessRuleViolationException("Only pending requests can be approved: " + request.getId());
        }

        if (request.getItem().isArchived()) {
            throw new BusinessRuleViolationException("Cannot approve a request for an archived item: " + request.getItem().getId());
        }

        if (request.getItem().getStatus() != ItemStatus.AVAILABLE) {
            throw new BusinessRuleViolationException("Item is not available for approval: " + request.getItem().getId());
        }

        request.setStatus(com.borrowbox.entity.BorrowRequestStatus.APPROVED);
        Item item = request.getItem();
        item.setStatus(ItemStatus.APPROVED);
        itemRepository.save(item);
        return borrowRequestRepository.save(request);
    }
}