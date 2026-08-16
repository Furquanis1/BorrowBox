package com.borrowbox.controller;

import com.borrowbox.dto.BorrowRequestCreateRequest;
import com.borrowbox.dto.BorrowRequestConfirmRequest;
import com.borrowbox.entity.BorrowRecord;
import com.borrowbox.entity.BorrowRequest;
import com.borrowbox.entity.BorrowRequestStatus;
import com.borrowbox.entity.Item;
import com.borrowbox.entity.ItemStatus;
import com.borrowbox.entity.User;
import com.borrowbox.exception.ResourceNotFoundException;

import java.time.LocalDateTime;
import com.borrowbox.repository.UserRepository;
import com.borrowbox.service.BorrowRequestService;
import com.borrowbox.service.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import org.springframework.data.domain.PageImpl;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BorrowRequestController.class)
@AutoConfigureMockMvc(addFilters = false)
@SuppressWarnings({"removal", "null"})
class BorrowRequestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BorrowRequestService borrowRequestService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void getAllBorrowRequestsReturnsList() throws Exception {
        Mockito.when(borrowRequestService.getAllBorrowRequests()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/borrow-requests"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getAllBorrowRequestsWithPaginationReturnsPage() throws Exception {
        Item item = new Item("Book", "desc");
        item.setId(1L);
        User user = testUser("User", "user@test.com");
        user.setId(2L);
        BorrowRequest request = new BorrowRequest(item, user, "please");
        request.setId(40L);
        PageImpl<BorrowRequest> page = new PageImpl<>(List.of(request));

        Mockito.when(borrowRequestService.getAllBorrowRequests(any())).thenReturn(page);

        mockMvc.perform(get("/api/borrow-requests").param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].id").value(40));
    }

    @Test
    void createBorrowRequestReturnsCreated() throws Exception {
        BorrowRequestCreateRequest request = new BorrowRequestCreateRequest(1L, 2L, "please");
        Item item = new Item("Book", "desc");
        item.setId(1L);
        User user = testUser("User", "user@test.com");
        user.setId(2L);
        BorrowRequest saved = new BorrowRequest(item, user, "please");
        saved.setId(3L);
        saved.setStatus(BorrowRequestStatus.PENDING);

        Mockito.when(borrowRequestService.createBorrowRequest(any(BorrowRequestCreateRequest.class))).thenReturn(saved);

        mockMvc.perform(post("/api/borrow-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void getBorrowRequestByIdReturnsRequest() throws Exception {
        Item item = new Item("Book", "desc");
        item.setId(1L);
        User user = testUser("User", "user@test.com");
        user.setId(2L);
        BorrowRequest request = new BorrowRequest(item, user, "please");
        request.setId(4L);

        Mockito.when(borrowRequestService.getBorrowRequestById(eq(4L))).thenReturn(request);

        mockMvc.perform(get("/api/borrow-requests/4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(4));
    }

    @Test
    void updateBorrowRequestReturnsOk() throws Exception {
        BorrowRequestCreateRequest request = new BorrowRequestCreateRequest(1L, 2L, "updated");
        Item item = new Item("Book", "desc");
        item.setId(1L);
        User user = testUser("User", "user@test.com");
        user.setId(2L);
        BorrowRequest updated = new BorrowRequest(item, user, "updated");
        updated.setId(5L);

        Mockito.when(borrowRequestService.updateBorrowRequest(eq(5L), any(BorrowRequestCreateRequest.class))).thenReturn(updated);

        mockMvc.perform(put("/api/borrow-requests/5")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    void deleteBorrowRequestReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/borrow-requests/6"))
                .andExpect(status().isNoContent());
    }

    @Test
    void approveBorrowRequestReturnsOk() throws Exception {
        Item item = new Item("Book", "desc");
        item.setId(1L);
        User user = testUser("User", "user@test.com");
        user.setId(2L);
        BorrowRequest approved = new BorrowRequest(item, user, "please");
        approved.setId(7L);
        approved.setStatus(BorrowRequestStatus.APPROVED);

        Mockito.when(borrowRequestService.approveBorrowRequest(eq(7L))).thenReturn(approved);

        mockMvc.perform(post("/api/borrow-requests/7/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void rejectBorrowRequestReturnsOk() throws Exception {
        Item item = new Item("Book", "desc");
        item.setId(1L);
        User user = testUser("User", "user@test.com");
        user.setId(2L);
        BorrowRequest rejected = new BorrowRequest(item, user, "please");
        rejected.setId(8L);
        rejected.setStatus(BorrowRequestStatus.REJECTED);

        Mockito.when(borrowRequestService.rejectBorrowRequest(eq(8L))).thenReturn(rejected);

        mockMvc.perform(post("/api/borrow-requests/8/reject"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void confirmBorrowRequestReturnsCreated() throws Exception {
        Item item = new Item("Book", "desc");
        item.setId(1L);
        item.setStatus(ItemStatus.BORROWED);
        User user = testUser("User", "user@test.com");
        user.setId(2L);
        BorrowRequest request = new BorrowRequest(item, user, "please");
        request.setId(9L);
        request.setStatus(BorrowRequestStatus.COMPLETED);
        LocalDateTime dueAt = LocalDateTime.now().plusDays(14);
        BorrowRecord record = new BorrowRecord(request, item, user, LocalDateTime.now(), dueAt);
        record.setId(10L);

        Mockito.when(borrowRequestService.confirmBorrowRequest(eq(9L), any(LocalDateTime.class))).thenReturn(record);

        BorrowRequestConfirmRequest body = new BorrowRequestConfirmRequest(dueAt);
        mockMvc.perform(post("/api/borrow-requests/9/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10));
    }

    @Test
    void getBorrowRequestByIdNotFoundReturns404() throws Exception {
        Mockito.when(borrowRequestService.getBorrowRequestById(eq(99L)))
                .thenThrow(new ResourceNotFoundException("Borrow request not found with id: 99"));

        mockMvc.perform(get("/api/borrow-requests/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Borrow request not found with id: 99"));
    }

    private User testUser(String fullName, String email) {
        User user = new User(fullName, email);
        user.setPasswordHash("test-password");
        return user;
    }
}