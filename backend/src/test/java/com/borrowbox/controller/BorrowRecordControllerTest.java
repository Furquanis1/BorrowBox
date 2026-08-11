package com.borrowbox.controller;

import com.borrowbox.dto.BorrowRecordCreateRequest;
import com.borrowbox.entity.BorrowRecord;
import com.borrowbox.entity.BorrowRequest;
import com.borrowbox.entity.BorrowRequestStatus;
import com.borrowbox.entity.Item;
import com.borrowbox.entity.ItemStatus;
import com.borrowbox.entity.User;
import com.borrowbox.exception.ResourceNotFoundException;
import com.borrowbox.repository.UserRepository;
import com.borrowbox.service.BorrowRecordService;
import com.borrowbox.service.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
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

@WebMvcTest(BorrowRecordController.class)
@AutoConfigureMockMvc(addFilters = false)
@SuppressWarnings({"removal", "null"})
class BorrowRecordControllerTest {

    @Autowired
    private MockMvc mockMvc;

        @MockitoBean
    private BorrowRecordService borrowRecordService;

        @MockitoBean
        private JwtService jwtService;

        @MockitoBean
        private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void getAllBorrowRecordsReturnsList() throws Exception {
        Mockito.when(borrowRecordService.getAllBorrowRecords()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/borrow-records"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getAllBorrowRecordsWithPaginationReturnsPage() throws Exception {
        Item item = new Item("Book", "desc");
        item.setId(1L);
        User user = testUser("User", "user@test.com");
        user.setId(2L);
        BorrowRequest borrowRequest = new BorrowRequest(item, user, "please");
        borrowRequest.setId(3L);
        BorrowRecord record = new BorrowRecord(borrowRequest, item, user, LocalDateTime.now(), LocalDateTime.now().plusDays(7));
        record.setId(30L);
        PageImpl<BorrowRecord> page = new PageImpl<>(List.of(record));

        Mockito.when(borrowRecordService.getAllBorrowRecords(any())).thenReturn(page);

        mockMvc.perform(get("/api/borrow-records").param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].id").value(30));
    }

    @Test
    void createBorrowRecordReturnsCreated() throws Exception {
        LocalDateTime borrowedAt = LocalDateTime.now();
        LocalDateTime dueAt = borrowedAt.plusDays(7);
        BorrowRecordCreateRequest request = new BorrowRecordCreateRequest(3L, 1L, 2L, borrowedAt, dueAt);
        Item item = new Item("Book", "desc");
        item.setId(1L);
        item.setStatus(ItemStatus.BORROWED);
        User user = testUser("User", "user@test.com");
        user.setId(2L);
        BorrowRequest borrowRequest = new BorrowRequest(item, user, "please");
        borrowRequest.setId(3L);
        borrowRequest.setStatus(BorrowRequestStatus.COMPLETED);
        BorrowRecord saved = new BorrowRecord(borrowRequest, item, user, borrowedAt, dueAt);
        saved.setId(4L);

        Mockito.when(borrowRecordService.createBorrowRecord(any(BorrowRecordCreateRequest.class))).thenReturn(saved);

        mockMvc.perform(post("/api/borrow-records")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(4))
                .andExpect(jsonPath("$.returned").value(false));
    }

    @Test
    void getBorrowRecordByIdReturnsRecord() throws Exception {
        Item item = new Item("Book", "desc");
        item.setId(1L);
        User user = testUser("User", "user@test.com");
        user.setId(2L);
        BorrowRequest borrowRequest = new BorrowRequest(item, user, "please");
        borrowRequest.setId(3L);
        BorrowRecord record = new BorrowRecord(borrowRequest, item, user, LocalDateTime.now(), LocalDateTime.now().plusDays(7));
        record.setId(5L);

        Mockito.when(borrowRecordService.getBorrowRecordById(eq(5L))).thenReturn(record);

        mockMvc.perform(get("/api/borrow-records/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    void updateBorrowRecordReturnsOk() throws Exception {
        LocalDateTime borrowedAt = LocalDateTime.now();
        LocalDateTime dueAt = borrowedAt.plusDays(7);
        BorrowRecordCreateRequest request = new BorrowRecordCreateRequest(3L, 1L, 2L, borrowedAt, dueAt);
        Item item = new Item("Book", "desc");
        item.setId(1L);
        User user = testUser("User", "user@test.com");
        user.setId(2L);
        BorrowRequest borrowRequest = new BorrowRequest(item, user, "updated");
        borrowRequest.setId(3L);
        BorrowRecord updated = new BorrowRecord(borrowRequest, item, user, borrowedAt, dueAt);
        updated.setId(6L);

        Mockito.when(borrowRecordService.updateBorrowRecord(eq(6L), any(BorrowRecordCreateRequest.class))).thenReturn(updated);

        mockMvc.perform(put("/api/borrow-records/6")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(6));
    }

    @Test
    void deleteBorrowRecordReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/borrow-records/7"))
                .andExpect(status().isNoContent());
    }

    @Test
    void returnBorrowedItemReturnsOk() throws Exception {
        Item item = new Item("Book", "desc");
        item.setId(1L);
        item.setStatus(ItemStatus.RETURNED);
        User user = testUser("User", "user@test.com");
        user.setId(2L);
        BorrowRequest borrowRequest = new BorrowRequest(item, user, "please");
        borrowRequest.setId(3L);
        BorrowRecord returned = new BorrowRecord(borrowRequest, item, user, LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(6));
        returned.setId(8L);
        returned.setReturned(true);

        Mockito.when(borrowRecordService.returnBorrowedItem(eq(8L))).thenReturn(returned);

        mockMvc.perform(post("/api/borrow-records/8/return"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.returned").value(true));
    }

    @Test
    void getBorrowRecordByIdNotFoundReturns404() throws Exception {
        Mockito.when(borrowRecordService.getBorrowRecordById(eq(99L)))
                .thenThrow(new ResourceNotFoundException("Borrow record not found with id: 99"));

        mockMvc.perform(get("/api/borrow-records/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Borrow record not found with id: 99"));
    }

    @Test
    void searchBorrowRecordsReturnsPage() throws Exception {
        Item item = new Item("Book", "desc");
        item.setId(1L);
        User user = testUser("User", "user@test.com");
        user.setId(2L);
        BorrowRequest borrowRequest = new BorrowRequest(item, user, "please");
        borrowRequest.setId(3L);
        BorrowRecord record = new BorrowRecord(borrowRequest, item, user, LocalDateTime.now(), LocalDateTime.now().plusDays(7));
        record.setId(10L);
        PageImpl<BorrowRecord> page = new PageImpl<>(List.of(record));

        Mockito.when(borrowRecordService.searchBorrowRecords(
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(page);

        mockMvc.perform(get("/api/borrow-records/search").param("active", "true").param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].id").value(10));
    }

    @Test
    void searchBorrowRecordsByOverdueReturnsOverdueOnly() throws Exception {
        Item item = new Item("Book", "desc");
        item.setId(1L);
        User user = testUser("User", "user@test.com");
        user.setId(2L);
        BorrowRequest borrowRequest = new BorrowRequest(item, user, "please");
        borrowRequest.setId(3L);
        BorrowRecord record = new BorrowRecord(borrowRequest, item, user, LocalDateTime.now().minusDays(10), LocalDateTime.now().minusDays(1));
        record.setId(11L);
        PageImpl<BorrowRecord> page = new PageImpl<>(List.of(record));

        Mockito.when(borrowRecordService.searchBorrowRecords(
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.eq(true), Mockito.any()))
                .thenReturn(page);

        mockMvc.perform(get("/api/borrow-records/search").param("overdue", "true").param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(11));
    }

        private User testUser(String fullName, String email) {
                User user = new User(fullName, email);
                user.setPasswordHash("test-password");
                return user;
        }
}