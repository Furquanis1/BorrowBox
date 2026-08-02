package com.borrowbox.controller;

import com.borrowbox.dto.ItemCreateRequest;
import com.borrowbox.entity.Item;
import com.borrowbox.exception.ResourceNotFoundException;
import com.borrowbox.repository.UserRepository;
import com.borrowbox.service.ItemService;
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

@WebMvcTest(ItemController.class)
@AutoConfigureMockMvc(addFilters = false)
public class ItemControllerTest {

    @Autowired
    private MockMvc mockMvc;

        @MockitoBean
    private ItemService itemService;

        @MockitoBean
        private JwtService jwtService;

        @MockitoBean
        private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void getAllItemsReturnsList() throws Exception {
        Mockito.when(itemService.getAllItems()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void createItemReturnsCreated() throws Exception {
        ItemCreateRequest req = new ItemCreateRequest("Book", "A good book", null);
        Item saved = new Item("Book", "A good book");
        saved.setId(1L);

        Mockito.when(itemService.createItem(any())).thenReturn(saved);

        mockMvc.perform(post("/api/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void getItemByIdReturnsItem() throws Exception {
        Item item = new Item("Lamp", "Desk lamp");
        item.setId(2L);
        Mockito.when(itemService.getItemById(eq(2L))).thenReturn(item);

        mockMvc.perform(get("/api/items/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2));
    }

    @Test
    void updateItemReturnsOk() throws Exception {
        ItemCreateRequest req = new ItemCreateRequest("Updated Book", "Updated description", null);
        Item updated = new Item("Updated Book", "Updated description");
        updated.setId(3L);

        Mockito.when(itemService.updateItem(eq(3L), any())).thenReturn(updated);

        mockMvc.perform(put("/api/items/3")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.title").value("Updated Book"));
    }

    @Test
    void deleteItemReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/items/4"))
                .andExpect(status().isNoContent());
    }

    @Test
    void archiveItemReturnsArchivedItem() throws Exception {
        Item archived = new Item("Archived Book", "No longer active");
        archived.setId(5L);
        archived.setArchived(true);
        Mockito.when(itemService.archiveItem(eq(5L))).thenReturn(archived);

        mockMvc.perform(post("/api/items/5/archive"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.archived").value(true));
    }

    @Test
    void getItemByIdNotFoundReturns404() throws Exception {
        Mockito.when(itemService.getItemById(eq(99L)))
                .thenThrow(new ResourceNotFoundException("Item not found with id: 99"));

        mockMvc.perform(get("/api/items/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Item not found with id: 99"));
    }

    @Test
    void createItemWithBlankTitleReturns400() throws Exception {
        ItemCreateRequest invalidReq = new ItemCreateRequest("", "desc", null);

        mockMvc.perform(post("/api/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").exists());
    }

    @Test
    void searchItemsReturnsPage() throws Exception {
        Item item = new Item("Search Book", "Desc");
        item.setId(10L);
        PageImpl<Item> page = new PageImpl<>(List.of(item));

        Mockito.when(itemService.searchItems(
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(page);

        mockMvc.perform(get("/api/items/search").param("q", "search").param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].id").value(10));
    }

    @Test
    void searchItemsByCategoryReturnsFiltered() throws Exception {
        Item item = new Item("Book", "Desc");
        item.setId(11L);
        PageImpl<Item> page = new PageImpl<>(List.of(item));

        Mockito.when(itemService.searchItems(
                Mockito.any(), Mockito.any(), Mockito.eq(5L), Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(page);

        mockMvc.perform(get("/api/items/search").param("categoryId", "5").param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(11));
    }
}
