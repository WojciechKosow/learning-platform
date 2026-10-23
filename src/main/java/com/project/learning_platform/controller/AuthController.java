package com.project.learning_platform.controller;

import com.project.learning_platform.dto.UserDTO;
import com.project.learning_platform.exceptions.ApiException;
import com.project.learning_platform.exceptions.ErrorCode;
import com.project.learning_platform.request.LoginRequest;
import com.project.learning_platform.request.RegisterRequest;
import com.project.learning_platform.response.AuthResponse;
import com.project.learning_platform.security.AuthCookies;
import com.project.learning_platform.security.AuthenticatedUser;
import com.project.learning_platform.service.AuthService;
import com.project.learning_platform.service.AuthSession;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthCookies cookies;

    public AuthController(AuthService authService, AuthCookies cookies) {
        this.authService = authService;
        this.cookies = cookies;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return sessionResponse(HttpStatus.CREATED, authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return sessionResponse(HttpStatus.OK, authService.login(request));
    }

    /** Restores or extends a session from the refresh-token cookie, rotating the cookie. */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(HttpServletRequest request, HttpServletResponse response) {
        try {
            String rawToken = cookies.read(request)
                    .orElseThrow(() -> new ApiException(ErrorCode.INVALID_REFRESH_TOKEN));
            return sessionResponse(HttpStatus.OK, authService.refresh(rawToken));
        } catch (ApiException e) {
            // Drop a dead cookie so the browser stops sending it.
            response.addHeader(HttpHeaders.SET_COOKIE, cookies.clear().toString());
            throw e;
        }
    }

    /** Ends the current session. Idempotent; works without a valid access token. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        cookies.read(request).ifPresent(authService::logout);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookies.clear().toString())
                .build();
    }

    @GetMapping("/me")
    public UserDTO me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return authService.currentUser(principal.id());
    }

    private ResponseEntity<AuthResponse> sessionResponse(HttpStatus status, AuthSession session) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookies.create(session.refreshToken()).toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(AuthResponse.from(session));
    }
}
