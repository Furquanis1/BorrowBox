package com.borrowbox.integration;

import com.borrowbox.entity.User;
import com.borrowbox.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.jwt.cookie-secure=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class JwtCookieLocalIntegrationTest {

    private static final String PASSWORD = "Gate8Password!123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserService userService;

    @Value("${app.jwt.expiration-ms}")
    private long expirationMs;

    @Test
    void registrationCookieUsesLocalAttributes() throws Exception {
        String setCookie = register(uniqueEmail());

        assertCookie(setCookie, expirationMs / 1000, false);
    }

    @Test
    void loginCookieUsesLocalAttributes() throws Exception {
        User user = createUser();

        assertCookie(login(user.getEmail()), expirationMs / 1000, false);
    }

    @Test
    void logoutClearsCookieWithLocalAttributes() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.SET_COOKIE))
                .andReturn();

        assertCookie(result.getResponse().getHeader(HttpHeaders.SET_COOKIE), 0, false);
    }

    private String register(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RegisterBody("Gate Eight Local", email, PASSWORD))))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.SET_COOKIE))
                .andReturn();

        return result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
    }

    private String login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginBody(email, PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.SET_COOKIE))
                .andReturn();

        return result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
    }

    private User createUser() {
        return userService.createUser("Gate Eight Local", uniqueEmail(), PASSWORD);
    }

    private void assertCookie(String setCookie, long expectedMaxAge, boolean secure) {
        assertThat(setCookie).isNotNull().startsWith("jwt=");
        assertThat(setCookie)
                .contains("; HttpOnly")
                .contains("; Path=/")
                .contains("; SameSite=Lax")
                .contains("; Max-Age=" + expectedMaxAge);

        if (secure) {
            assertThat(setCookie).contains("; Secure");
        } else {
            assertThat(setCookie).doesNotContain("; Secure");
        }
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private String uniqueEmail() {
        return "gate8-local-" + UUID.randomUUID() + "@example.com";
    }

    private record RegisterBody(String fullName, String email, String password) {
    }

    private record LoginBody(String email, String password) {
    }
}
