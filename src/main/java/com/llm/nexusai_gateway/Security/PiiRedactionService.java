package com.llm.nexusai_gateway.Security;

import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PII and Secret Key Redaction Service.
 * Ensures zero-trust data protection before prompts or responses are stored in telemetry logs.
 */
@Service
public class PiiRedactionService {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6}");

    private static final Pattern CREDIT_CARD_PATTERN =
            Pattern.compile("\\b(?:4[0-9]{12}(?:[0-9]{3})?|5[1-5][0-9]{14}|3[47][0-9]{13}|6(?:011|5[0-9]{2})[0-9]{12})\\b");

    private static final Pattern SSN_PATTERN =
            Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b");

    private static final Pattern API_KEY_PATTERN =
            Pattern.compile("(?:sk-[a-zA-Z0-9_-]{20,64}|nx_live_[a-zA-Z0-9_]{20,64}|AKIA[0-9A-Z]{16})");

    private static final Pattern IPV4_PATTERN =
            Pattern.compile("\\b(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\b");

    public record RedactionResult(String redactedText, boolean piiDetected, int redactionCount) {}

    public RedactionResult redact(String inputText) {
        if (inputText == null || inputText.isBlank()) {
            return new RedactionResult(inputText, false, 0);
        }

        String currentText = inputText;
        int totalCount = 0;

        // 1. Email
        Matcher emailMatcher = EMAIL_PATTERN.matcher(currentText);
        int emailMatches = 0;
        while (emailMatcher.find()) { emailMatches++; }
        if (emailMatches > 0) {
            totalCount += emailMatches;
            currentText = EMAIL_PATTERN.matcher(currentText).replaceAll("[EMAIL_REDACTED]");
        }

        // 2. Credit Card
        Matcher cardMatcher = CREDIT_CARD_PATTERN.matcher(currentText);
        int cardMatches = 0;
        while (cardMatcher.find()) { cardMatches++; }
        if (cardMatches > 0) {
            totalCount += cardMatches;
            currentText = CREDIT_CARD_PATTERN.matcher(currentText).replaceAll("[CREDIT_CARD_REDACTED]");
        }

        // 3. SSN
        Matcher ssnMatcher = SSN_PATTERN.matcher(currentText);
        int ssnMatches = 0;
        while (ssnMatcher.find()) { ssnMatches++; }
        if (ssnMatches > 0) {
            totalCount += ssnMatches;
            currentText = SSN_PATTERN.matcher(currentText).replaceAll("[SSN_REDACTED]");
        }

        // 4. API Keys
        Matcher apiKeyMatcher = API_KEY_PATTERN.matcher(currentText);
        int apiKeyMatches = 0;
        while (apiKeyMatcher.find()) { apiKeyMatches++; }
        if (apiKeyMatches > 0) {
            totalCount += apiKeyMatches;
            currentText = API_KEY_PATTERN.matcher(currentText).replaceAll("[API_KEY_REDACTED]");
        }

        // 5. IPv4
        Matcher ipMatcher = IPV4_PATTERN.matcher(currentText);
        int ipMatches = 0;
        while (ipMatcher.find()) { ipMatches++; }
        if (ipMatches > 0) {
            totalCount += ipMatches;
            currentText = IPV4_PATTERN.matcher(currentText).replaceAll("[IP_ADDRESS_REDACTED]");
        }

        return new RedactionResult(currentText, totalCount > 0, totalCount);
    }
}
