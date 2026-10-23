package com.project.learning_platform.security;

import com.jayway.jsonpath.JsonPath;
import com.project.learning_platform.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class SecurityIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtEncoder jwtEncoder;

    @Autowired
    JwtProperties jwtProperties;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void protectedEndpointWithoutTokenReturns401ProblemJson() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void unknownRoutesAreProtectedToo() throws Exception {
        mvc.perform(get("/api/does-not-exist"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void garbageTokenIsRejected() throws Exception {
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() throws Exception {
        String userId = registerAndGetUserId();
        JwtEncoder foreign = NimbusJwtEncoder.withSecretKey(new SecretKeySpec(
                "another-secret-another-secret-another-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
                .algorithm(MacAlgorithm.HS256)
                .build();

        String token = encode(foreign, userId, jwtProperties.issuer(), Instant.now(), Instant.now().plusSeconds(600));

        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        String userId = registerAndGetUserId();
        Instant issued = Instant.now().minus(Duration.ofHours(1));
        String token = encode(jwtEncoder, userId, jwtProperties.issuer(), issued, issued.plus(Duration.ofMinutes(15)));

        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenFromAnotherIssuerIsRejected() throws Exception {
        String userId = registerAndGetUserId();
        String token = encode(jwtEncoder, userId, "someone-else", Instant.now(), Instant.now().plusSeconds(600));

        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validTokenForUnknownUserIsRejected() throws Exception {
        String token = encode(jwtEncoder, UUID.randomUUID().toString(), jwtProperties.issuer(),
                Instant.now(), Instant.now().plusSeconds(600));

        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void disablingAnAccountInvalidatesItsAccessTokensAndBlocksLogin() throws Exception {
        String email = "disabled-" + UUID.randomUUID() + "@example.com";
        String body = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String accessToken = JsonPath.read(body, "$.accessToken");

        jdbcTemplate.update("update users set status = 'DISABLED' where email = ?", email);

        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"password\": \"correct-horse-battery\"}".formatted(email)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
    }

    @Test
    void corsPreflightFromTheConfiguredFrontendIsAllowedWithCredentials() throws Exception {
        mvc.perform(options("/api/auth/login")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    void corsPreflightFromAnUnknownOriginIsRejected() throws Exception {
        mvc.perform(options("/api/auth/login")
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    private String registerAndGetUserId() throws Exception {
        String body = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("sec-" + UUID.randomUUID() + "@example.com")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.user.id");
    }

    private static String registerJson(String email) {
        String username = "s" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        return """
                {"email": "%s", "username": "%s", "password": "correct-horse-battery"}
                """.formatted(email, username);
    }

    private static String encode(JwtEncoder encoder, String subject, String issuer, Instant issuedAt, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(subject)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(), claims))
                .getTokenValue();
    }
}
