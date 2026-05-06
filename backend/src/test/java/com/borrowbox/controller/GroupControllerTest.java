package com.borrowbox.controller;

import com.borrowbox.dto.GroupCreateRequest;
import com.borrowbox.entity.Group;
import com.borrowbox.exception.ResourceNotFoundException;
import com.borrowbox.repository.UserRepository;
import com.borrowbox.service.GroupService;
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

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GroupController.class)
@AutoConfigureMockMvc(addFilters = false)
public class GroupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GroupService groupService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void getAllGroupsReturnsList() throws Exception {
        Mockito.when(groupService.getAllGroups()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/groups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void createGroupReturnsCreated() throws Exception {
        GroupCreateRequest request = new GroupCreateRequest("Family", "Family group", Collections.emptySet());
        Group saved = new Group("Family", "Family group");
        saved.setId(1L);

        Mockito.when(groupService.createGroup(any())).thenReturn(saved);

        mockMvc.perform(post("/api/groups")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Family"));
    }

    @Test
    void getGroupByIdReturnsGroup() throws Exception {
        Group group = new Group("Friends", "Friend circle");
        group.setId(2L);
        Mockito.when(groupService.getGroupById(eq(2L))).thenReturn(group);

        mockMvc.perform(get("/api/groups/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2));
    }

    @Test
    void updateGroupReturnsOk() throws Exception {
        GroupCreateRequest request = new GroupCreateRequest("Updated Group", "Updated desc", Collections.emptySet());
        Group updated = new Group("Updated Group", "Updated desc");
        updated.setId(3L);

        Mockito.when(groupService.updateGroup(eq(3L), any())).thenReturn(updated);

        mockMvc.perform(put("/api/groups/3")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.name").value("Updated Group"));
    }

    @Test
    void deleteGroupReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/groups/4"))
                .andExpect(status().isNoContent());
    }

    @Test
    void addUserToGroupReturnsOk() throws Exception {
        Group group = new Group("Team", "Team group");
        group.setId(5L);
        Mockito.when(groupService.addUserToGroup(eq(5L), eq(10L))).thenReturn(group);

        mockMvc.perform(post("/api/groups/5/users/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    void removeUserFromGroupReturnsOk() throws Exception {
        Group group = new Group("Team", "Team group");
        group.setId(5L);
        Mockito.when(groupService.removeUserFromGroup(eq(5L), eq(10L))).thenReturn(group);

        mockMvc.perform(delete("/api/groups/5/users/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    void getGroupByIdNotFoundReturns404() throws Exception {
        Mockito.when(groupService.getGroupById(eq(99L)))
                .thenThrow(new ResourceNotFoundException("Group not found with id: 99"));

        mockMvc.perform(get("/api/groups/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Group not found with id: 99"));
    }

    @Test
    void createGroupWithBlankNameReturns400() throws Exception {
        GroupCreateRequest invalidRequest = new GroupCreateRequest("", "desc", Collections.emptySet());

        mockMvc.perform(post("/api/groups")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.name").exists());
    }
}
