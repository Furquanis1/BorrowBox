package com.borrowbox.integration;

import com.borrowbox.dto.UserUpdateRequest;
import com.borrowbox.entity.User;
import com.borrowbox.entity.UserStatus;
import com.borrowbox.repository.UserRepository;
import com.borrowbox.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AuthenticationBoundaryIntegrationTest {

    private static final String PASSWORD = "Gate7Password!123";
    private static final String OTHER_SECRET = Base64.getEncoder()
            .encodeToString("abcdefghijklmnopqrstuvwxyz123456".getBytes(StandardCharsets.UTF_8));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Test
    void anonymousProtectedRequestsReturnJson401WithoutWwwAuthenticate() throws Exception {
        assertUnauthorized("/api/users");
        assertUnauthorized("/api/communities");
        assertUnauthorized("/api/memberships");
        assertUnauthorized("/api/auth/me");
    }

    @Test
    void malformedTokenReturnsJson401() throws Exception {
        assertUnauthorized(get("/api/auth/me").cookie(new Cookie("jwt", "not-a-jwt")));
    }

    @Test
    void badlySignedTokenReturnsJson401() throws Exception {
        User user = createUser();
        Instant now = Instant.now();
        String token = tokenFor(user.getId(), user.getEmail(), now, now.plusSeconds(60), OTHER_SECRET);

        assertUnauthorized(get("/api/auth/me").cookie(new Cookie("jwt", token)));
    }

    @Test
    void expiredTokenReturnsJson401() throws Exception {
        User user = createUser();
        Instant now = Instant.now();
        String token = tokenFor(user.getId(), user.getEmail(), now.minusSeconds(120), now.minusSeconds(60), jwtSecret);

        assertUnauthorized(get("/api/auth/me").cookie(new Cookie("jwt", token)));
    }

    @Test
    void tokenWithoutUserIdReturnsJson401() throws Exception {
        User user = createUser();
        Instant now = Instant.now();
        String token = tokenFor(null, user.getEmail(), now, now.plusSeconds(60), jwtSecret);

        assertUnauthorized(get("/api/auth/me").cookie(new Cookie("jwt", token)));
    }

    @Test
    void mismatchedTokenIdentityReturnsJson401() throws Exception {
        User user = createUser();
        User otherUser = createUser();
        Instant now = Instant.now();
        String token = tokenFor(otherUser.getId(), user.getEmail(), now, now.plusSeconds(60), jwtSecret);

        assertUnauthorized(get("/api/auth/me").cookie(new Cookie("jwt", token)));
    }

    @Test
    void suspendedUserCannotLoginAndGetsNoCookie() throws Exception {
        User user = createUser();
        setStatus(user, UserStatus.SUSPENDED);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginBody(user.getEmail(), PASSWORD))))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("Invalid email or password"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void suspendedUserTokenIsRejectedAndReactivationRestoresIt() throws Exception {
        User user = createUser();
        Cookie cookie = login(user.getEmail());
        setStatus(user, UserStatus.SUSPENDED);

        assertUnauthorized(get("/api/auth/me").cookie(cookie));
        assertUnauthorized(get("/api/communities").cookie(cookie));

        setStatus(user, UserStatus.ACTIVE);

        mockMvc.perform(get("/api/auth/me").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(user.getEmail()));
    }

    @Test
    void deletedUserTokenIsRejectedWithoutServerError() throws Exception {
        User user = createUser();
        Cookie cookie = login(user.getEmail());
        userRepository.deleteById(user.getId());
        assertThat(userRepository.findById(user.getId())).isEmpty();

        assertUnauthorized(get("/api/auth/me").cookie(cookie));
    }

    @Test
    void emailChangeInvalidatesOldToken() throws Exception {
        User user = createUser();
        Cookie oldCookie = login(user.getEmail());
        String newEmail = uniqueEmail();
        userService.updateUser(user.getId(), new UserUpdateRequest(user.getFullName(), newEmail));

        assertUnauthorized(get("/api/auth/me").cookie(oldCookie));

        Cookie newCookie = login(newEmail);
        mockMvc.perform(get("/api/auth/me").cookie(newCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(newEmail));
    }

    @Test
    void authenticatedNonAdminGetsJson403FromControllerAuthorization() throws Exception {
        User user = createUser();
        Cookie cookie = login(user.getEmail());

        mockMvc.perform(get("/api/users").cookie(cookie))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("Administrator access is required"))
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE));
    }

    @Test
    void cookieAndBearerTokensAuthenticateActiveUser() throws Exception {
        User user = createUser();
        Cookie cookie = login(user.getEmail());

        mockMvc.perform(get("/api/auth/me").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(user.getEmail()));

        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cookie.getValue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(user.getEmail()));
    }

    @Test
    void healthEndpointRemainsPublic() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk());
    }

    private User createUser() {
        return userService.createUser("Gate Seven", uniqueEmail(), PASSWORD);
    }

    private Cookie login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginBody(email, PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.SET_COOKIE))
                .andReturn();

        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).isNotNull().startsWith("jwt=");
        int separator = setCookie.indexOf(';');
        String value = separator >= 0
                ? setCookie.substring("jwt=".length(), separator)
                : setCookie.substring("jwt=".length());
        return new Cookie("jwt", value);
    }

    private void setStatus(User user, UserStatus status) {
        User persisted = userRepository.findById(user.getId()).orElseThrow();
        persisted.setStatus(status);
        userRepository.saveAndFlush(persisted);
    }

    private void assertUnauthorized(String path) throws Exception {
        assertUnauthorized(get(path));
    }

    private void assertUnauthorized(MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("Authentication required"))
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE));
    }

    private String tokenFor(Long userId, String email, Instant issuedAt, Instant expiration, String secret) {
        var builder = Jwts.builder()
                .setSubject(email)
                .setIssuedAt(Date.from(issuedAt))
                .setExpiration(Date.from(expiration));
        if (userId != null) {
            builder.claim("userId", userId);
        }
        return builder.signWith(key(secret), SignatureAlgorithm.HS256).compact();
    }

    private Key key(String secret) {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private String uniqueEmail() {
        return "gate7-" + UUID.randomUUID() + "@example.com";
    }

    private record LoginBody(String email, String password) {
    }
}
