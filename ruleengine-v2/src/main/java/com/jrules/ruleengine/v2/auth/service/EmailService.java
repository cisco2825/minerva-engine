package com.jrules.ruleengine.v2.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String fromAddress;

    @Value("${ruleengine.app.base-url}")
    private String appBaseUrl;

    /**
     * Sends a password reset email.
     * If the mail server is not configured (empty username), the reset link is
     * logged at WARN level so development still works without SMTP.
     */
    public void sendPasswordResetEmail(String toEmail, String recipientName, String resetToken) {
        String resetLink = appBaseUrl + "/reset-password?token=" + resetToken;

        // Dev-mode fallback — no SMTP configured
        if (fromAddress == null || fromAddress.isBlank()) {
            log.warn("⚠️  Mail not configured — password reset link for {}: {}", toEmail, resetLink);
            return;
        }

        try {
            var message = mailSender.createMimeMessage();
            var helper  = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromAddress, "Axiom Rule Engine");
            helper.setTo(toEmail);
            helper.setSubject("Reset your Axiom password");
            helper.setText(buildHtml(recipientName, resetLink), true);

            mailSender.send(message);
            log.info("Password reset email sent to {}", toEmail);
        } catch (Exception e) {
            // Log but do NOT surface the error to the caller — prevents user enumeration
            // via email send failures, and also handles misconfigured SMTP gracefully.
            log.error("Failed to send password reset email to {}: {}", toEmail, e.getMessage());
            log.warn("Password reset link (fallback log) for {}: {}", toEmail, resetLink);
        }
    }

    private String buildHtml(String name, String resetLink) {
        return """
                <!DOCTYPE html>
                <html>
                <body style="margin:0;padding:0;background:#f1f5f9;font-family:'Inter',sans-serif;">
                  <div style="max-width:480px;margin:40px auto;background:#fff;border-radius:12px;overflow:hidden;border:1px solid #e2e8f0;">
                    <!-- Header -->
                    <div style="background:linear-gradient(135deg,#6366f1 0%%,#8b5cf6 100%%);padding:28px 32px;text-align:center;">
                      <span style="font-size:28px;">⚡</span>
                      <h1 style="margin:8px 0 0;color:#fff;font-size:20px;font-weight:700;letter-spacing:-0.3px;">Axiom Rule Engine</h1>
                    </div>
                    <!-- Body -->
                    <div style="padding:32px;">
                      <h2 style="margin:0 0 8px;color:#0f172a;font-size:18px;font-weight:700;">Reset your password</h2>
                      <p style="color:#64748b;font-size:14px;line-height:1.6;margin:0 0 24px;">
                        Hi %s, we received a request to reset your password. Click the button below — this link expires in <strong>15 minutes</strong>.
                      </p>
                      <a href="%s" style="display:inline-block;padding:13px 28px;background:linear-gradient(135deg,#6366f1 0%%,#8b5cf6 100%%);color:#fff;text-decoration:none;border-radius:8px;font-weight:600;font-size:14px;">
                        Reset Password
                      </a>
                      <p style="margin:24px 0 0;color:#94a3b8;font-size:12px;line-height:1.6;">
                        If you didn't request this, you can safely ignore this email. Your password won't change.
                      </p>
                    </div>
                  </div>
                </body>
                </html>
                """.formatted(name, resetLink);
    }
}
