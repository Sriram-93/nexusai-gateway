package com.llm.nexusai_gateway.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * NotificationService — sends email notifications for team lifecycle events.
 *
 * If MAIL_HOST is not configured, all sends are logged instead of emailed
 * so the app boots cleanly in environments without SMTP.
 *
 * All sends are @Async so they never block the HTTP request thread.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final JavaMailSender mailSender;

    @Value("${nexusai.mail.from:noreply@nexusai.dev}")
    private String fromAddress;

    @Value("${nexusai.app.url:http://localhost:5173}")
    private String appUrl;

    @Value("${spring.mail.host:}")
    private String mailHost;

    public NotificationService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    // ─── Public notification methods ─────────────────────────────────────────

    /**
     * Sent when an ORG_ADMIN creates a Team Lead account for a new user.
     * Contains: login URL, email, temporary password, and team name.
     */
    @Async
    public void sendTeamLeadWelcome(String toEmail, String teamName, String orgName,
                                    String tempPassword, String teamId) {
        String subject = "You're now a Team Lead on NexusAI — " + teamName;
        String body = buildTeamLeadWelcomeHtml(toEmail, teamName, orgName, tempPassword);
        sendEmail(toEmail, subject, body);
    }

    /**
     * Sent to the Team Lead when an ORG_ADMIN generates a new API key for their team.
     * The raw key is shown ONCE here. After this email, it can never be retrieved again.
     */
    @Async
    public void sendTeamApiKey(String toEmail, String teamName, String rawApiKey) {
        String subject = "Your Team Gateway API Key — " + teamName + " | NexusAI";
        String body = buildApiKeyHtml(toEmail, teamName, rawApiKey);
        sendEmail(toEmail, subject, body);
    }

    /**
     * Sent to Team Lead when their team's key is enabled or disabled by ORG_ADMIN.
     */
    @Async
    public void sendKeyStatusChange(String toEmail, String teamName, boolean enabled) {
        String subject = "Team Gateway Key " + (enabled ? "Enabled" : "Suspended") + " — " + teamName;
        String body = buildKeyStatusHtml(toEmail, teamName, enabled);
        sendEmail(toEmail, subject, body);
    }

    /**
     * Sent to Team Lead when a new member is added to their team by ORG_ADMIN.
     */
    @Async
    public void sendMemberAddedToTeam(String toLeadEmail, String teamName,
                                       String memberEmail, String memberRole) {
        String subject = "New Member Added to " + teamName + " — NexusAI";
        String body = buildMemberAddedHtml(toLeadEmail, teamName, memberEmail, memberRole);
        sendEmail(toLeadEmail, subject, body);
    }

    /**
     * Sent to ORG_ADMIN when a team approaches 80% of their daily budget.
     */
    @Async
    public void sendBudgetWarning(String toEmail, String teamName, double usedUsd, double totalUsd) {
        String subject = "⚠ Budget Warning: " + teamName + " at 80% — NexusAI";
        String body = buildBudgetWarningHtml(toEmail, teamName, usedUsd, totalUsd);
        sendEmail(toEmail, subject, body);
    }

    // ─── Core send method ────────────────────────────────────────────────────

    private void sendEmail(String to, String subject, String htmlBody) {
        if (mailHost == null || mailHost.isBlank()) {
            log.info("[NOTIFY-LOG-ONLY] To: {} | Subject: {}", to, subject);
            return;
        }
        try {
            var msg = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(msg, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(msg);
            log.info("Email sent → {} | {}", to, subject);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
        }
    }

    // ─── HTML Email Templates ────────────────────────────────────────────────

    private String buildTeamLeadWelcomeHtml(String email, String teamName,
                                             String orgName, String tempPassword) {
        return """
            <!DOCTYPE html>
            <html>
            <head><meta charset="UTF-8"></head>
            <body style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
                         background:#0f1117; color:#e2e8f0; margin:0; padding:40px 20px;">
              <div style="max-width:560px; margin:0 auto; background:#1a1d2e;
                          border-radius:16px; border:1px solid #2d3148; padding:40px;">
                <div style="display:flex; align-items:center; gap:12px; margin-bottom:32px;">
                  <div style="width:36px;height:36px;border-radius:10px;
                              background:linear-gradient(135deg,#06b6d4,#818cf8);
                              display:flex;align-items:center;justify-content:center;
                              font-size:18px;">⚡</div>
                  <span style="font-size:18px;font-weight:600;color:#f1f5f9;">NexusAI</span>
                </div>

                <h1 style="font-size:22px;font-weight:600;color:#f1f5f9;margin:0 0 8px;">
                  You're now a Team Lead 🎉
                </h1>
                <p style="color:#94a3b8;font-size:14px;margin:0 0 28px;line-height:1.6;">
                  You've been assigned as Team Lead of <strong style="color:#06b6d4;">%s</strong>
                  in <strong>%s</strong>. Use the credentials below to sign in.
                </p>

                <div style="background:#0f1117;border-radius:12px;padding:20px;margin-bottom:24px;
                            border:1px solid #2d3148;">
                  <p style="margin:0 0 12px;font-size:12px;text-transform:uppercase;
                             letter-spacing:0.1em;color:#64748b;">Your Login Credentials</p>
                  <div style="margin-bottom:8px;">
                    <span style="color:#64748b;font-size:12px;">Email</span><br>
                    <span style="font-family:monospace;color:#06b6d4;font-size:14px;">%s</span>
                  </div>
                  <div>
                    <span style="color:#64748b;font-size:12px;">Temporary Password</span><br>
                    <span style="font-family:monospace;color:#f59e0b;font-size:14px;">%s</span>
                  </div>
                </div>

                <p style="color:#ef4444;font-size:12px;margin:0 0 24px;">
                  ⚠ Change your password immediately after first login.
                </p>

                <a href="%s" style="display:inline-block;background:linear-gradient(135deg,#06b6d4,#818cf8);
                   color:white;text-decoration:none;padding:12px 24px;border-radius:10px;
                   font-size:14px;font-weight:600;">Sign In to NexusAI →</a>

                <p style="color:#475569;font-size:11px;margin-top:32px;">
                  This email was sent by your organization admin. If you have questions, reply directly to your admin.
                </p>
              </div>
            </body>
            </html>
            """.formatted(teamName, orgName, email, tempPassword, appUrl);
    }

    private String buildApiKeyHtml(String email, String teamName, String rawApiKey) {
        return """
            <!DOCTYPE html>
            <html>
            <head><meta charset="UTF-8"></head>
            <body style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
                         background:#0f1117; color:#e2e8f0; margin:0; padding:40px 20px;">
              <div style="max-width:560px; margin:0 auto; background:#1a1d2e;
                          border-radius:16px; border:1px solid #2d3148; padding:40px;">
                <div style="display:flex; align-items:center; gap:12px; margin-bottom:32px;">
                  <div style="width:36px;height:36px;border-radius:10px;
                              background:linear-gradient(135deg,#06b6d4,#818cf8);
                              display:flex;align-items:center;justify-content:center;
                              font-size:18px;">⚡</div>
                  <span style="font-size:18px;font-weight:600;color:#f1f5f9;">NexusAI</span>
                </div>

                <h1 style="font-size:22px;font-weight:600;color:#f1f5f9;margin:0 0 8px;">
                  Your Team Gateway API Key 🔑
                </h1>
                <p style="color:#94a3b8;font-size:14px;margin:0 0 28px;line-height:1.6;">
                  A Gateway API key has been generated for your team
                  <strong style="color:#06b6d4;">%s</strong>.
                  This key is shown <strong>exactly once</strong> — save it securely.
                </p>

                <div style="background:#0f1117;border:1px dashed #06b6d4;border-radius:12px;
                            padding:20px;margin-bottom:24px;">
                  <p style="margin:0 0 8px;font-size:12px;text-transform:uppercase;
                             letter-spacing:0.1em;color:#64748b;">Team Gateway Key</p>
                  <code style="font-family:monospace;color:#06b6d4;font-size:14px;
                               word-break:break-all;">%s</code>
                </div>

                <div style="background:#1e1b10;border:1px solid #78350f;border-radius:10px;
                            padding:16px;margin-bottom:24px;">
                  <p style="margin:0;color:#fbbf24;font-size:13px;">
                    ⚠ <strong>Important:</strong> This key is never shown again after this email.
                    Store it in a secure password manager and share only with team members via secure channels.
                  </p>
                </div>

                <p style="color:#94a3b8;font-size:13px;line-height:1.6;">
                  Use this key in the <code>Authorization: Bearer nx_live_...</code> header
                  when calling the NexusAI gateway endpoint.
                </p>

                <a href="%s/app" style="display:inline-block;background:linear-gradient(135deg,#06b6d4,#818cf8);
                   color:white;text-decoration:none;padding:12px 24px;border-radius:10px;
                   font-size:14px;font-weight:600;margin-top:24px;">Open Dashboard →</a>
              </div>
            </body>
            </html>
            """.formatted(teamName, rawApiKey, appUrl);
    }

    private String buildKeyStatusHtml(String email, String teamName, boolean enabled) {
        String color = enabled ? "#10b981" : "#ef4444";
        String status = enabled ? "Enabled" : "Suspended";
        String message = enabled
            ? "Your team's gateway key is now active. Your team members can start making requests."
            : "Your team's gateway key has been suspended by your administrator. Contact your admin for assistance.";
        return """
            <!DOCTYPE html>
            <html>
            <head><meta charset="UTF-8"></head>
            <body style="font-family:-apple-system,sans-serif;background:#0f1117;color:#e2e8f0;
                         margin:0;padding:40px 20px;">
              <div style="max-width:560px;margin:0 auto;background:#1a1d2e;
                          border-radius:16px;border:1px solid #2d3148;padding:40px;">
                <h1 style="font-size:20px;color:%s;margin:0 0 16px;">Team Key %s — %s</h1>
                <p style="color:#94a3b8;font-size:14px;line-height:1.6;">%s</p>
                <a href="%s/app" style="display:inline-block;background:linear-gradient(135deg,#06b6d4,#818cf8);
                   color:white;text-decoration:none;padding:12px 24px;border-radius:10px;
                   font-size:14px;font-weight:600;margin-top:16px;">Open Dashboard →</a>
              </div>
            </body>
            </html>
            """.formatted(color, status, teamName, message, appUrl);
    }

    private String buildMemberAddedHtml(String leadEmail, String teamName,
                                         String memberEmail, String memberRole) {
        return """
            <!DOCTYPE html>
            <html>
            <head><meta charset="UTF-8"></head>
            <body style="font-family:-apple-system,sans-serif;background:#0f1117;color:#e2e8f0;
                         margin:0;padding:40px 20px;">
              <div style="max-width:560px;margin:0 auto;background:#1a1d2e;
                          border-radius:16px;border:1px solid #2d3148;padding:40px;">
                <h1 style="font-size:20px;color:#f1f5f9;margin:0 0 16px;">New Team Member Added</h1>
                <p style="color:#94a3b8;font-size:14px;line-height:1.6;">
                  <strong style="color:#06b6d4;">%s</strong> has been added to your team
                  <strong>%s</strong> as a <strong>%s</strong>.
                </p>
                <a href="%s/app/members" style="display:inline-block;
                   background:linear-gradient(135deg,#06b6d4,#818cf8);
                   color:white;text-decoration:none;padding:12px 24px;border-radius:10px;
                   font-size:14px;font-weight:600;margin-top:16px;">View Your Team →</a>
              </div>
            </body>
            </html>
            """.formatted(memberEmail, teamName, memberRole, appUrl);
    }

    private String buildBudgetWarningHtml(String email, String teamName,
                                           double usedUsd, double totalUsd) {
        double pct = totalUsd > 0 ? (usedUsd / totalUsd) * 100 : 0;
        return """
            <!DOCTYPE html>
            <html>
            <head><meta charset="UTF-8"></head>
            <body style="font-family:-apple-system,sans-serif;background:#0f1117;color:#e2e8f0;
                         margin:0;padding:40px 20px;">
              <div style="max-width:560px;margin:0 auto;background:#1a1d2e;
                          border-radius:16px;border:1px solid #78350f;padding:40px;">
                <h1 style="font-size:20px;color:#fbbf24;margin:0 0 16px;">⚠ Budget Warning</h1>
                <p style="color:#94a3b8;font-size:14px;line-height:1.6;">
                  Team <strong style="color:#06b6d4;">%s</strong> has used
                  <strong style="color:#fbbf24;">%.0f%%</strong> of its daily budget
                  ($%.4f of $%.2f).
                </p>
                <a href="%s/app" style="display:inline-block;
                   background:linear-gradient(135deg,#f59e0b,#ef4444);
                   color:white;text-decoration:none;padding:12px 24px;border-radius:10px;
                   font-size:14px;font-weight:600;margin-top:16px;">View Analytics →</a>
              </div>
            </body>
            </html>
            """.formatted(teamName, pct, usedUsd, totalUsd, appUrl);
    }
}
