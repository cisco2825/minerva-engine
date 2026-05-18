package com.jrules.ruleengine.v2.auth.service;

import com.jrules.ruleengine.v2.auth.dto.AuthResponse;
import com.jrules.ruleengine.v2.auth.dto.ForgotPasswordRequest;
import com.jrules.ruleengine.v2.auth.dto.LoginRequest;
import com.jrules.ruleengine.v2.auth.dto.ResetPasswordRequest;
import com.jrules.ruleengine.v2.auth.dto.SignupRequest;
import com.jrules.ruleengine.v2.auth.entity.PasswordResetTokenEntity;
import com.jrules.ruleengine.v2.auth.entity.UserEntity;
import com.jrules.ruleengine.v2.auth.repository.PasswordResetTokenRepository;
import com.jrules.ruleengine.v2.auth.repository.UserRepository;
import com.jrules.ruleengine.v2.auth.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository              userRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final JwtTokenProvider            jwtTokenProvider;
    private final PasswordEncoder             passwordEncoder;
    private final EmailService                emailService;

    public AuthResponse signup(SignupRequest req) {
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "An account with this email already exists");
        }

        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID().toString());
        user.setEmail(req.getEmail().toLowerCase().trim());
        user.setName(req.getName().trim());
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        userRepository.save(user);

        String token = jwtTokenProvider.generateToken(user.getId(), user.getEmail(), user.getName());
        return new AuthResponse(token, user.getId(), user.getEmail(), user.getName());
    }

    public AuthResponse login(LoginRequest req) {
        UserEntity user = userRepository.findByEmail(req.getEmail().toLowerCase().trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Invalid email or password"));

        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Invalid email or password");
        }

        String token = jwtTokenProvider.generateToken(user.getId(), user.getEmail(), user.getName());
        return new AuthResponse(token, user.getId(), user.getEmail(), user.getName());
    }

    public AuthResponse me(String email) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        // Re-issue a fresh token so the client can refresh expiry
        String token = jwtTokenProvider.generateToken(user.getId(), user.getEmail(), user.getName());
        return new AuthResponse(token, user.getId(), user.getEmail(), user.getName());
    }

    // ── Forgot / reset password ───────────────────────────────────────────────

    /**
     * Issues a reset token and sends an email.
     * Always returns 200 even when the email is not found — prevents user enumeration.
     */
    @Transactional
    public void forgotPassword(ForgotPasswordRequest req) {
        userRepository.findByEmail(req.getEmail().toLowerCase().trim()).ifPresent(user -> {
            // Invalidate any previous unused tokens
            resetTokenRepository.invalidateAllForUser(user.getId());

            PasswordResetTokenEntity prt = new PasswordResetTokenEntity();
            prt.setId(UUID.randomUUID().toString());
            prt.setUserId(user.getId());
            prt.setToken(UUID.randomUUID().toString());
            prt.setExpiresAt(LocalDateTime.now().plusMinutes(15));
            resetTokenRepository.save(prt);

            emailService.sendPasswordResetEmail(user.getEmail(), user.getName(), prt.getToken());
        });
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest req) {
        PasswordResetTokenEntity prt = resetTokenRepository.findByToken(req.getToken())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Invalid or expired reset link"));

        if (prt.isUsed()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This reset link has already been used");
        }
        if (prt.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This reset link has expired");
        }

        UserEntity user = userRepository.findById(prt.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "User not found"));

        user.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        userRepository.save(user);

        prt.setUsed(true);
        resetTokenRepository.save(prt);
    }
}
