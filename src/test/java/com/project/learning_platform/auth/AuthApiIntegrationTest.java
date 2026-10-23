package com.project.learning_platform.auth;

import com.jayway.jsonpath.JsonPath;
import com.project.learning_platform.support.IntegrationTest;
import com.project.learning_platform.user.User;
import com.project.learning_platform.user.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class AuthApiIntegrationTest {

    static final String COOKIE = "lp_refresh_token";
    static final String PASSWORD = "correct-horse-battery";

    @Autowired
    MockMvc mvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    // ---- registration ------------------------------------------------------

    @Test
    void registerCreatesAccountAndStartsSession() throws Exception {
        String email = uniqueEmail();
        String username = uniqueUsername();

        MvcResult result = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(email.toUpperCase(), username, PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.id").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.username").value(username))
                .andExpect(jsonPath("$.user.role").value("USER"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.user.password").doesNotExist())
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andReturn();

        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie)
                .startsWith(COOKIE + "=")
                .contains("HttpOnly", "Secure", "SameSite=Lax", "Path=/api/auth")
                .doesNotContain("Max-Age"); // browser-session cookie without "remember me"

        User stored = userRepository.findByEmail(email).orElseThrow();
        assertThat(stored.getPasswordHash()).isNotEqualTo(PASSWORD).startsWith("{bcrypt}");
        assertThat(passwordEncoder.matches(PASSWORD, stored.getPasswordHash())).isTrue();
        assertThat(result.getResponse().getContentAsString()).doesNotContain(stored.getPasswordHash(), PASSWORD);
    }

    @Test
    void registerRejectsDuplicateEmailCaseInsensitively() throws Exception {
        String email = uniqueEmail();
        register(email, uniqueUsername());

        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(email.toUpperCase(), uniqueUsername(), PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_USED"))
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void registerRejectsDuplicateUsernameCaseInsensitively() throws Exception {
        String username = uniqueUsername();
        register(uniqueEmail(), username);

        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(uniqueEmail(), username.toUpperCase(), PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_ALREADY_USED"));
    }

    @Test
    void registerReportsEveryInvalidField() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("not-an-email", "a", "short")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("email", "username", "password")));
    }

    @Test
    void registerRejectsMissingFields() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void registerRejectsPasswordLongerThan72Bytes() throws Exception {
        String multiBytePassword = "ż".repeat(40); // 40 characters, 80 bytes

        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(uniqueEmail(), uniqueUsername(), multiBytePassword)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("password"));
    }

    @Test
    void malformedJsonIsABadRequest() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    // ---- login -------------------------------------------------------------

    @Test
    void loginReturnsAccessTokenThatAuthenticatesMe() throws Exception {
        String email = uniqueEmail();
        String username = uniqueUsername();
        register(email, username);

        MvcResult login = login(email.toUpperCase(), PASSWORD, false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(email))
                .andReturn();

        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void loginWithWrongPasswordOrUnknownEmailFailsIdentically() throws Exception {
        String email = uniqueEmail();
        register(email, uniqueUsername());

        String wrongPassword = login(email, "wrong-password", false)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andReturn().getResponse().getContentAsString();

        String unknownEmail = login(uniqueEmail(), "wrong-password", false)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<String>read(wrongPassword, "$.detail"))
                .isEqualTo(JsonPath.<String>read(unknownEmail, "$.detail"));
    }

    @Test
    void loginRequiresEmailAndPassword() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("email", "password")));
    }

    @Test
    void repeatedFailuresLockLoginEvenForTheCorrectPassword() throws Exception {
        String email = uniqueEmail();
        register(email, uniqueUsername());

        for (int i = 0; i < 5; i++) {
            login(email, "wrong-password", false).andExpect(status().isUnauthorized());
        }

        login(email, PASSWORD, false)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
    }

    @Test
    void successfulLoginResetsTheFailureCounter() throws Exception {
        String email = uniqueEmail();
        register(email, uniqueUsername());

        for (int i = 0; i < 4; i++) {
            login(email, "wrong-password", false).andExpect(status().isUnauthorized());
        }
        login(email, PASSWORD, false).andExpect(status().isOk());

        assertThat(userRepository.findByEmail(email).orElseThrow().getFailedLoginAttempts()).isZero();
    }

    @Test
    void rememberMeSetsAPersistentCookie() throws Exception {
        String email = uniqueEmail();
        register(email, uniqueUsername());

        String setCookie = login(email, PASSWORD, true)
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE);

        Matcher maxAge = Pattern.compile("Max-Age=(\\d+)").matcher(setCookie);
        assertThat(maxAge.find()).as("Max-Age present").isTrue();
        long thirtyDays = 30L * 24 * 3600;
        assertThat(Long.parseLong(maxAge.group(1))).isBetween(thirtyDays - 60, thirtyDays);
    }

    // ---- refresh / logout --------------------------------------------------

    @Test
    void refreshRotatesTheCookieAndIssuesANewAccessToken() throws Exception {
        String email = uniqueEmail();
        register(email, uniqueUsername());
        String original = refreshCookie(login(email, PASSWORD, false).andReturn());

        MvcResult refreshed = mvc.perform(post("/api/auth/refresh").cookie(new Cookie(COOKIE, original)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value(email))
                .andReturn();

        String rotated = refreshCookie(refreshed);
        assertThat(rotated).isNotBlank().isNotEqualTo(original);

        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(refreshed)))
                .andExpect(status().isOk());
    }

    @Test
    void reusingARotatedRefreshTokenRevokesEverySession() throws Exception {
        String email = uniqueEmail();
        register(email, uniqueUsername());
        String original = refreshCookie(login(email, PASSWORD, false).andReturn());
        String rotated = refreshCookie(mvc.perform(post("/api/auth/refresh").cookie(new Cookie(COOKIE, original)))
                .andExpect(status().isOk())
                .andReturn());

        // Replay of the old token (e.g. stolen) ...
        mvc.perform(post("/api/auth/refresh").cookie(new Cookie(COOKIE, original)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

        // ... also kills the legitimate, newer session.
        mvc.perform(post("/api/auth/refresh").cookie(new Cookie(COOKIE, rotated)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshWithoutOrWithUnknownCookieIsUnauthorized() throws Exception {
        mvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

        mvc.perform(post("/api/auth/refresh").cookie(new Cookie(COOKIE, "made-up-token")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void logoutRevokesTheSessionAndClearsTheCookie() throws Exception {
        String email = uniqueEmail();
        register(email, uniqueUsername());
        String refresh = refreshCookie(login(email, PASSWORD, false).andReturn());

        mvc.perform(post("/api/auth/logout").cookie(new Cookie(COOKIE, refresh)))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

        mvc.perform(post("/api/auth/refresh").cookie(new Cookie(COOKIE, refresh)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutWithoutCookieIsANoOp() throws Exception {
        mvc.perform(post("/api/auth/logout"))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, not(containsString(COOKIE + "=x"))));
    }

    // ---- helpers -----------------------------------------------------------

    MvcResult register(String email, String username) throws Exception {
        return mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(email, username, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn();
    }

    ResultActions login(String email, String password, boolean rememberMe)
            throws Exception {
        return mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s", "rememberMe": %s}
                        """.formatted(email, password, rememberMe)));
    }

    static String registerJson(String email, String username, String password) {
        return """
                {"email": "%s", "username": "%s", "password": "%s"}
                """.formatted(email, username, password);
    }

    static String accessToken(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    static String refreshCookie(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie(COOKIE);
        assertThat(cookie).as("refresh cookie").isNotNull();
        return cookie.getValue();
    }

    static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    static String uniqueUsername() {
        return "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    }
}
