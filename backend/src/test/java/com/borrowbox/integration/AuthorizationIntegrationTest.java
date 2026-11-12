package com.borrowbox.integration;

import com.borrowbox.dto.CategoryCreateRequest;
import com.borrowbox.dto.LoginRequest;
import com.borrowbox.dto.UserCreateRequest;
import com.borrowbox.dto.UserUpdateRequest;
import com.borrowbox.entity.Asset;
import com.borrowbox.entity.AssetStatus;
import com.borrowbox.entity.Category;
import com.borrowbox.entity.User;
import com.borrowbox.entity.UserRole;
import com.borrowbox.entity.UserStatus;
import com.borrowbox.repository.AssetRepository;
import com.borrowbox.repository.CategoryRepository;
import com.borrowbox.repository.UserRepository;
import com.borrowbox.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end authorization regression for the user and category endpoints.
 *
 * <p>Intentionally NOT {@code @Transactional}: login, the JWT filter and every
 * repository call must keep their production transaction boundaries, and the
 * category foreign-key rejection only happens when the delete is committed.
 * Rows are UUID-isolated, matching the other non-transactional integration
 * tests, so assertions never depend on seed data or execution order.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AuthorizationIntegrationTest {

    private static final String PASSWORD = "Gate6Password!123";
    private static final String CONFLICT_MESSAGE =
            "Cannot delete because related records still reference this resource.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private AssetRepository assetRepository;

    // ------------------------------------------------------------------ users

    @Test
    void anonymousCannotListUsers() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").isNotEmpty())
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE));
    }

    @Test
    void userCannotListUsers() throws Exception {
        User user = createUser("Gate6 List User", uniqueEmail("gate6.list.user"));

        mockMvc.perform(get("/api/users").cookie(login(user.getEmail())))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanListUsersAndSeesCreatedUser() throws Exception {
        User admin = createAdmin("Gate6 List Admin", uniqueEmail("gate6.list.admin"));
        User target = createUser("Gate6 Listed Target", uniqueEmail("gate6.listed.target"));

        mockMvc.perform(get("/api/users").cookie(login(admin.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.email == '" + target.getEmail() + "')]").isNotEmpty());
    }

    @Test
    void userCanReadOwnRecord() throws Exception {
        User user = createUser("Gate6 Own Reader", uniqueEmail("gate6.own.reader"));

        mockMvc.perform(get("/api/users/{id}", user.getId()).cookie(login(user.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId().intValue()))
                .andExpect(jsonPath("$.email").value(user.getEmail()));
    }

    @Test
    void userCannotReadAnotherUserRecord() throws Exception {
        User user = createUser("Gate6 Other Reader", uniqueEmail("gate6.other.reader"));
        User other = createUser("Gate6 Read Target", uniqueEmail("gate6.read.target"));

        mockMvc.perform(get("/api/users/{id}", other.getId()).cookie(login(user.getEmail())))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanReadAnotherUserRecord() throws Exception {
        User admin = createAdmin("Gate6 Read Admin", uniqueEmail("gate6.read.admin"));
        User other = createUser("Gate6 Admin Read Target", uniqueEmail("gate6.admin.read.target"));

        mockMvc.perform(get("/api/users/{id}", other.getId()).cookie(login(admin.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(other.getId().intValue()))
                .andExpect(jsonPath("$.email").value(other.getEmail()));
    }

    @Test
    void adminReadingMissingUserGetsNotFound() throws Exception {
        User admin = createAdmin("Gate6 Missing Admin", uniqueEmail("gate6.missing.admin"));
        User deleted = createUser("Gate6 Deleted Target", uniqueEmail("gate6.deleted.target"));
        Long deletedId = deleted.getId();
        userRepository.deleteById(deletedId);
        assertThat(userRepository.findById(deletedId)).isEmpty();

        mockMvc.perform(get("/api/users/{id}", deletedId).cookie(login(admin.getEmail())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void userCanUpdateOwnPermittedFields() throws Exception {
        User user = createUser("Gate6 Self Old", uniqueEmail("gate6.self.update"));
        Cookie cookie = login(user.getEmail());
        String updatedEmail = uniqueEmail("gate6.self.updated");

        mockMvc.perform(put("/api/users/{id}", user.getId()).cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UserUpdateRequest("Gate6 Self New", updatedEmail))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Gate6 Self New"))
                .andExpect(jsonPath("$.email").value(updatedEmail));

        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertThat(reloaded.getFullName()).isEqualTo("Gate6 Self New");
        assertThat(reloaded.getEmail()).isEqualTo(updatedEmail);
        assertThat(reloaded.getRole()).isEqualTo(UserRole.USER);
        assertThat(reloaded.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void userCannotUpdateAnotherUser() throws Exception {
        User user = createUser("Gate6 Other Updater", uniqueEmail("gate6.other.updater"));
        User target = createUser("Gate6 Untouched Target", uniqueEmail("gate6.untouched.target"));
        Long targetId = target.getId();
        String targetEmail = target.getEmail();

        mockMvc.perform(put("/api/users/{id}", targetId).cookie(login(user.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UserUpdateRequest("Gate6 Hijacked", uniqueEmail("gate6.hijacked")))))
                .andExpect(status().isForbidden());

        User reloaded = userRepository.findById(targetId).orElseThrow();
        assertThat(reloaded.getFullName()).isEqualTo("Gate6 Untouched Target");
        assertThat(reloaded.getEmail()).isEqualTo(targetEmail);
        assertThat(reloaded.getRole()).isEqualTo(UserRole.USER);
        assertThat(reloaded.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void selfUpdateCannotTamperWithRoleOrStatus() throws Exception {
        User user = createUser("Gate6 Tamper Original", uniqueEmail("gate6.tamper.self"));
        Cookie cookie = login(user.getEmail());
        String tamperedPayload = json(Map.of(
                "fullName", "Gate6 Tamper Updated",
                "email", user.getEmail(),
                "role", "ADMIN",
                "status", "SUSPENDED"
        ));

        mockMvc.perform(put("/api/users/{id}", user.getId()).cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tamperedPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").doesNotExist())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertThat(reloaded.getFullName()).isEqualTo("Gate6 Tamper Updated");
        assertThat(reloaded.getRole()).isEqualTo(UserRole.USER);
        assertThat(reloaded.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void adminCanUpdateAnotherUser() throws Exception {
        User admin = createAdmin("Gate6 Update Admin", uniqueEmail("gate6.update.admin"));
        User target = createUser("Gate6 Admin Target Old", uniqueEmail("gate6.admin.target"));
        Long targetId = target.getId();
        String updatedEmail = uniqueEmail("gate6.admin.target.updated");

        mockMvc.perform(put("/api/users/{id}", targetId).cookie(login(admin.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UserUpdateRequest("Gate6 Admin Target New", updatedEmail))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Gate6 Admin Target New"))
                .andExpect(jsonPath("$.email").value(updatedEmail));

        User reloaded = userRepository.findById(targetId).orElseThrow();
        assertThat(reloaded.getFullName()).isEqualTo("Gate6 Admin Target New");
        assertThat(reloaded.getEmail()).isEqualTo(updatedEmail);
        assertThat(reloaded.getRole()).isEqualTo(UserRole.USER);
        assertThat(reloaded.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void userCannotDeleteAnotherUser() throws Exception {
        User user = createUser("Gate6 Delete User", uniqueEmail("gate6.delete.user"));
        User target = createUser("Gate6 Delete Target", uniqueEmail("gate6.delete.target"));

        mockMvc.perform(delete("/api/users/{id}", target.getId()).cookie(login(user.getEmail())))
                .andExpect(status().isForbidden());

        assertThat(userRepository.findById(target.getId())).isPresent();
    }

    @Test
    void adminCanDeleteUserWithoutDependencies() throws Exception {
        User admin = createAdmin("Gate6 Delete Admin", uniqueEmail("gate6.delete.admin"));
        User target = createUser("Gate6 Deletable Target", uniqueEmail("gate6.deletable.target"));

        mockMvc.perform(delete("/api/users/{id}", target.getId()).cookie(login(admin.getEmail())))
                .andExpect(status().isNoContent());

        assertThat(userRepository.findById(target.getId())).isEmpty();
    }

    @Test
    void adminCannotDeleteUserReferencedByAsset() throws Exception {
        User admin = createAdmin("Gate9 User FK Admin", uniqueEmail("gate9.user.fk.admin"));
        User owner = createUser("Gate9 User FK Owner", uniqueEmail("gate9.user.fk.owner"));

        Asset asset = new Asset();
        asset.setOwner(owner);
        asset.setTitle(uniqueName("Gate9 User FK Asset"));
        asset.setStatus(AssetStatus.ACTIVE);
        asset = assetRepository.save(asset);
        assertThat(asset.getId()).isNotNull();

        mockMvc.perform(delete("/api/users/{id}", owner.getId()).cookie(login(admin.getEmail())))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value(CONFLICT_MESSAGE));

        assertThat(userRepository.findById(owner.getId()))
                .as("rejected delete must not remove the referenced user")
                .isPresent();
        assertThat(assetRepository.findById(asset.getId()))
                .as("rejected delete must not remove the dependent asset")
                .isPresent();
    }

    @Test
    void userCannotCreateUser() throws Exception {
        User user = createUser("Gate6 Create User", uniqueEmail("gate6.create.user"));
        String attemptedEmail = uniqueEmail("gate6.denied.create");

        mockMvc.perform(post("/api/users").cookie(login(user.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UserCreateRequest("Gate6 Denied Create", attemptedEmail, PASSWORD))))
                .andExpect(status().isForbidden());

        assertThat(userRepository.findByEmail(attemptedEmail)).isEmpty();
    }

    @Test
    void adminCreateIgnoresSuppliedRole() throws Exception {
        User admin = createAdmin("Gate6 Create Admin", uniqueEmail("gate6.create.admin"));
        String createdEmail = uniqueEmail("gate6.admin.created");

        mockMvc.perform(post("/api/users").cookie(login(admin.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "fullName", "Gate6 Admin Created",
                                "email", createdEmail,
                                "password", PASSWORD,
                                "role", "ADMIN"
                        ))))
                .andExpect(status().isCreated());

        User created = userRepository.findByEmail(createdEmail).orElseThrow();
        assertThat(created.getRole()).isEqualTo(UserRole.USER);
        assertThat(created.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void publicRegistrationIgnoresSuppliedRole() throws Exception {
        String registeredEmail = uniqueEmail("gate6.registered");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "fullName", "Gate6 Registered",
                                "email", registeredEmail,
                                "password", PASSWORD,
                                "role", "ADMIN"
                        ))))
                .andExpect(status().isCreated());

        User registered = userRepository.findByEmail(registeredEmail).orElseThrow();
        assertThat(registered.getRole()).isEqualTo(UserRole.USER);
        assertThat(registered.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void authorizationFollowsCurrentPersistedRole() throws Exception {
        User user = createUser("Gate6 Live Role", uniqueEmail("gate6.live.role"));
        Cookie cookie = login(user.getEmail());

        mockMvc.perform(get("/api/users").cookie(cookie))
                .andExpect(status().isForbidden());

        withRole(user, UserRole.ADMIN);
        mockMvc.perform(get("/api/users").cookie(cookie))
                .andExpect(status().isOk());

        withRole(user, UserRole.USER);
        mockMvc.perform(get("/api/users").cookie(cookie))
                .andExpect(status().isForbidden());

        assertThat(userRepository.findById(user.getId()).orElseThrow().getRole()).isEqualTo(UserRole.USER);
    }

    // -------------------------------------------------------------- categories

    @Test
    void anonymousCannotReadCategories() throws Exception {
        Category category = createCategory(uniqueName("Gate6 Anonymous Category"), "anonymous");

        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").isNotEmpty())
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE));
        mockMvc.perform(get("/api/categories/{id}", category.getId()))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").isNotEmpty())
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE));
    }

    @Test
    void authenticatedUserCanReadCategories() throws Exception {
        User user = createUser("Gate6 Category Reader", uniqueEmail("gate6.category.reader"));
        Category category = createCategory(uniqueName("Gate6 User Read Category"), "readable");
        Cookie cookie = login(user.getEmail());

        mockMvc.perform(get("/api/categories").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == '" + category.getName() + "')]").isNotEmpty());
        mockMvc.perform(get("/api/categories/{id}", category.getId()).cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(category.getId().intValue()))
                .andExpect(jsonPath("$.name").value(category.getName()));
    }

    @Test
    void adminCanReadCategories() throws Exception {
        User admin = createAdmin("Gate6 Category Admin", uniqueEmail("gate6.category.admin"));
        Category category = createCategory(uniqueName("Gate6 Admin Read Category"), "readable");
        Cookie cookie = login(admin.getEmail());

        mockMvc.perform(get("/api/categories").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == '" + category.getName() + "')]").isNotEmpty());
        mockMvc.perform(get("/api/categories/{id}", category.getId()).cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(category.getId().intValue()))
                .andExpect(jsonPath("$.name").value(category.getName()));
    }

    @Test
    void userCannotCreateCategory() throws Exception {
        User user = createUser("Gate6 Category Creator", uniqueEmail("gate6.category.creator"));
        String attemptedName = uniqueName("Gate6 Denied Category");

        mockMvc.perform(post("/api/categories").cookie(login(user.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CategoryCreateRequest(attemptedName, "denied"))))
                .andExpect(status().isForbidden());

        assertThat(categoryExists(attemptedName)).isFalse();
    }

    @Test
    void userCannotUpdateCategory() throws Exception {
        User user = createUser("Gate6 Category Updater", uniqueEmail("gate6.category.updater"));
        Category category = createCategory(uniqueName("Gate6 User Update Category"), "original");

        mockMvc.perform(put("/api/categories/{id}", category.getId()).cookie(login(user.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CategoryCreateRequest("Gate6 Hijacked Category", "hijacked"))))
                .andExpect(status().isForbidden());

        Category reloaded = categoryRepository.findById(category.getId()).orElseThrow();
        assertThat(reloaded.getName()).isEqualTo(category.getName());
        assertThat(reloaded.getDescription()).isEqualTo("original");
    }

    @Test
    void userCannotDeleteCategory() throws Exception {
        User user = createUser("Gate6 Category Deleter", uniqueEmail("gate6.category.deleter"));
        Category category = createCategory(uniqueName("Gate6 User Delete Category"), "protected");

        mockMvc.perform(delete("/api/categories/{id}", category.getId()).cookie(login(user.getEmail())))
                .andExpect(status().isForbidden());

        assertThat(categoryRepository.findById(category.getId())).isPresent();
    }

    @Test
    void adminCanCreateCategory() throws Exception {
        User admin = createAdmin("Gate6 Category Creator Admin", uniqueEmail("gate6.category.create.admin"));
        String name = uniqueName("Gate6 Admin Created Category");

        MvcResult result = mockMvc.perform(post("/api/categories").cookie(login(admin.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CategoryCreateRequest(name, "created by admin"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(name))
                .andReturn();

        long createdId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        Category persisted = categoryRepository.findById(createdId).orElseThrow();
        assertThat(persisted.getName()).isEqualTo(name);
        assertThat(persisted.getDescription()).isEqualTo("created by admin");
    }

    @Test
    void adminCanUpdateCategory() throws Exception {
        User admin = createAdmin("Gate6 Category Update Admin", uniqueEmail("gate6.category.update.admin"));
        Category category = createCategory(uniqueName("Gate6 Admin Update Category"), "original");
        String updatedName = uniqueName("Gate6 Admin Updated Category");

        mockMvc.perform(put("/api/categories/{id}", category.getId()).cookie(login(admin.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CategoryCreateRequest(updatedName, "updated by admin"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(updatedName))
                .andExpect(jsonPath("$.description").value("updated by admin"));

        Category reloaded = categoryRepository.findById(category.getId()).orElseThrow();
        assertThat(reloaded.getName()).isEqualTo(updatedName);
        assertThat(reloaded.getDescription()).isEqualTo("updated by admin");
    }

    @Test
    void adminCanDeleteUnusedCategory() throws Exception {
        User admin = createAdmin("Gate6 Category Delete Admin", uniqueEmail("gate6.category.delete.admin"));
        Category category = createCategory(uniqueName("Gate6 Unused Category"), "unused");

        mockMvc.perform(delete("/api/categories/{id}", category.getId()).cookie(login(admin.getEmail())))
                .andExpect(status().isNoContent());

        assertThat(categoryRepository.findById(category.getId())).isEmpty();
    }

    @Test
    void deletingCategoryReferencedByAssetIsRejectedAndPreservesRows() throws Exception {
        User admin = createAdmin("Gate6 FK Admin", uniqueEmail("gate6.fk.admin"));
        User owner = createUser("Gate6 FK Owner", uniqueEmail("gate6.fk.owner"));
        Category category = createCategory(uniqueName("Gate6 FK Category"), "referenced");

        Asset asset = new Asset();
        asset.setOwner(owner);
        asset.setTitle(uniqueName("Gate6 FK Asset"));
        asset.setCategory(category);
        asset.setStatus(AssetStatus.ACTIVE);
        asset = assetRepository.save(asset);
        assertThat(asset.getId()).isNotNull();

        mockMvc.perform(delete("/api/categories/{id}", category.getId())
                        .cookie(login(admin.getEmail())))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value(CONFLICT_MESSAGE));

        assertThat(categoryRepository.findById(category.getId()))
                .as("rejected delete must not remove the category")
                .isPresent();
        assertThat(assetRepository.findById(asset.getId()))
                .as("rejected delete must not remove the dependent asset")
                .isPresent();
    }

    // ---------------------------------------------------------------- helpers

    private User createUser(String fullName, String email) {
        return userService.createUser(fullName, email, PASSWORD);
    }

    private User createAdmin(String fullName, String email) {
        return withRole(createUser(fullName, email), UserRole.ADMIN);
    }

    private User withRole(User user, UserRole role) {
        User persisted = userRepository.findById(user.getId()).orElseThrow();
        persisted.setRole(role);
        return userRepository.save(persisted);
    }

    private Category createCategory(String name, String description) {
        return categoryRepository.save(new Category(name, description));
    }

    private boolean categoryExists(String name) {
        return categoryRepository.findAll().stream().anyMatch(category -> name.equals(category.getName()));
    }

    private Cookie login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();

        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).as("login must set the jwt cookie").isNotNull();
        assertThat(setCookie).startsWith("jwt=");
        int separator = setCookie.indexOf(';');
        assertThat(separator).as("jwt cookie must have attributes").isGreaterThan("jwt=".length());
        return new Cookie("jwt", setCookie.substring("jwt=".length(), separator));
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private String uniqueEmail(String prefix) {
        return prefix + "." + UUID.randomUUID() + "@example.com";
    }

    private String uniqueName(String prefix) {
        return prefix + " " + UUID.randomUUID();
    }
}
