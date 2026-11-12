package com.borrowbox.controller;

import com.borrowbox.dto.UserCreateRequest;
import com.borrowbox.dto.UserUpdateRequest;
import com.borrowbox.entity.User;
import com.borrowbox.exception.ResourceConflictException;
import com.borrowbox.exception.ResourceNotFoundException;
import com.borrowbox.repository.UserRepository;
import com.borrowbox.security.AuthorizationService;
import com.borrowbox.service.UserService;
import com.borrowbox.service.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
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

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
public class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private AuthorizationService authorizationService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void getAllUsersReturnsList() throws Exception {
        Mockito.when(userService.getAllUsers()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void createUserReturnsCreated() throws Exception {
        UserCreateRequest request = new UserCreateRequest("Alice Smith", "alice@example.com", "secret123");
        User saved = testUser("Alice Smith", "alice@example.com");
        saved.setId(1L);

        Mockito.when(userService.createUser(any())).thenReturn(saved);

        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("alice@example.com"));
    }

    @Test
    void getUserByIdReturnsUser() throws Exception {
        User user = testUser("Bob Brown", "bob@example.com");
        user.setId(2L);
        Mockito.when(userService.getUserById(eq(2L))).thenReturn(user);

        mockMvc.perform(get("/api/users/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2));
    }

    @Test
    void updateUserReturnsOk() throws Exception {
        UserCreateRequest request = new UserCreateRequest("Bob B.", "bob.b@example.com", "secret123");
        User updated = testUser("Bob B.", "bob.b@example.com");
        updated.setId(3L);

        Mockito.when(userService.updateUser(eq(3L), any())).thenReturn(updated);

        mockMvc.perform(put("/api/users/3")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.fullName").value("Bob B."));
    }

    @Test
    void deleteUserReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/users/4"))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteUserReturnsConflictWhenReferenced() throws Exception {
        Mockito.doThrow(new ResourceConflictException(
                        "Cannot delete because related records still reference this resource."))
                .when(userService).deleteUser(4L);

        mockMvc.perform(delete("/api/users/4"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error")
                        .value("Cannot delete because related records still reference this resource."));
    }

    @Test
    void deleteUserReturnsConflictWhenTargetIsLastActiveAdmin() throws Exception {
        Mockito.doThrow(new ResourceConflictException("Cannot delete the last active administrator."))
                .when(userService).deleteUser(4L);

        mockMvc.perform(delete("/api/users/4"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Cannot delete the last active administrator."));
    }

    @Test
    void getUserByIdNotFoundReturns404() throws Exception {
        Mockito.when(userService.getUserById(eq(99L)))
                .thenThrow(new ResourceNotFoundException("User not found with id: 99"));

        mockMvc.perform(get("/api/users/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("User not found with id: 99"));
    }

    @Test
    void createUserWithBlankEmailReturns400() throws Exception {
        UserCreateRequest invalidRequest = new UserCreateRequest("Alice", "", "secret123");

        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.email").exists());
    }

    @Test
    void getAllUsersReturnsForbiddenWhenNotAdmin() throws Exception {
        Mockito.doThrow(new AccessDeniedException("denied")).when(authorizationService).requireAdmin();

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    void createUserReturnsForbiddenWhenNotAdmin() throws Exception {
        Mockito.doThrow(new AccessDeniedException("denied")).when(authorizationService).requireAdmin();
        UserCreateRequest request = new UserCreateRequest("Alice Smith", "alice@example.com", "secret123");

        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getUserByIdReturnsForbiddenForOtherUsersRecord() throws Exception {
        Mockito.doThrow(new AccessDeniedException("denied")).when(authorizationService).requireSelfOrAdmin(2L);

        mockMvc.perform(get("/api/users/2"))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateUserReturnsForbiddenForOtherUsersRecord() throws Exception {
        Mockito.doThrow(new AccessDeniedException("denied")).when(authorizationService).requireSelfOrAdmin(3L);
        UserUpdateRequest request = new UserUpdateRequest("Bob B.", "bob.b@example.com");

        mockMvc.perform(put("/api/users/3")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteUserReturnsForbiddenWhenNotAdmin() throws Exception {
        Mockito.doThrow(new AccessDeniedException("denied")).when(authorizationService).requireAdmin();

        mockMvc.perform(delete("/api/users/4"))
                .andExpect(status().isForbidden());
    }

    private User testUser(String fullName, String email) {
        User user = new User(fullName, email);
        user.setPasswordHash("test-password");
        return user;
    }
}
