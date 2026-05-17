package com.jrules.ruleengine.v2.auth.api;

import com.jrules.ruleengine.v2.auth.dto.AuthResponse;
import com.jrules.ruleengine.v2.auth.dto.ForgotPasswordRequest;
import com.jrules.ruleengine.v2.auth.dto.LoginRequest;
import com.jrules.ruleengine.v2.auth.dto.ResetPasswordRequest;
import com.jrules.ruleengine.v2.auth.dto.SignupRequest;
import com.jrules.ruleengine.v2.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v2/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse signup(@RequestBody SignupRequest req) {
        return authService.signup(req);
    }

    @PostMapping("/login")
    public AuthResponse login(@RequestBody LoginRequest req) {
        return authService.login(req);
    }

    /** Returns current user info + a refreshed token. Used on app load to validate stored token. */
    @GetMapping("/me")
    public AuthResponse me(@AuthenticationPrincipal String email) {
        return authService.me(email);
    }

    /**
     * Sends a reset email. Always returns 200 even if the email is unknown
     * to prevent user enumeration.
     */
    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.OK)
    public void forgotPassword(@RequestBody ForgotPasswordRequest req) {
        authService.forgotPassword(req);
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.OK)
    public void resetPassword(@RequestBody ResetPasswordRequest req) {
        authService.resetPassword(req);
    }
}
