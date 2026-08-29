package com.llm.nexusai_gateway.Governance;

import com.llm.nexusai_gateway.Security.PiiRedactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class GovernanceAndPiiTest {

    @Autowired
    private BudgetService budgetService;

    @Autowired
    private BudgetRepository budgetRepository;

    @Autowired
    private PiiRedactionService piiRedactionService;

    @BeforeEach
    void setUp() {
        Budget b = new Budget("PROJECT", "proj-alpha-123", 10.0, 100.0);
        budgetRepository.save(b);
    }

    @Test
    @DisplayName("BudgetService should allow requests under daily cap and block when daily cap is breached")
    void testBudgetCapEnforcement() {
        // Initial check: spend = $0, cap = $10
        var check1 = budgetService.checkBudgetAllowed("PROJECT", "proj-alpha-123");
        assertTrue(check1.allowed());

        // Spend $5 -> spend = $5 (50% of cap)
        budgetService.recordSpend("PROJECT", "proj-alpha-123", 5.0);
        var check2 = budgetService.checkBudgetAllowed("PROJECT", "proj-alpha-123");
        assertTrue(check2.allowed());

        // Spend $4 -> spend = $9 (90% of cap -> 80% warning triggered)
        budgetService.recordSpend("PROJECT", "proj-alpha-123", 4.0);
        var check3 = budgetService.checkBudgetAllowed("PROJECT", "proj-alpha-123");
        assertTrue(check3.allowed());
        assertTrue(check3.is80PercentWarningTriggered());

        // Spend $2 -> spend = $11 (> $10 cap -> blocked)
        budgetService.recordSpend("PROJECT", "proj-alpha-123", 2.0);
        var check4 = budgetService.checkBudgetAllowed("PROJECT", "proj-alpha-123");
        assertFalse(check4.allowed());
        assertTrue(check4.message().contains("budget cap exceeded"));
    }

    @Test
    @DisplayName("PiiRedactionService should detect and redact email, SSN, and secret keys from prompts")
    void testPiiRedaction() {
        String sensitivePrompt = "Please contact me at user@example.com or SSN 123-45-6789. Use API key sk-proj-123456789012345678901234.";
        PiiRedactionService.RedactionResult result = piiRedactionService.redact(sensitivePrompt);

        assertTrue(result.piiDetected());
        assertTrue(result.redactionCount() >= 3);
        assertFalse(result.redactedText().contains("user@example.com"));
        assertFalse(result.redactedText().contains("123-45-6789"));
        assertFalse(result.redactedText().contains("sk-proj-123456789012345678901234"));
        assertTrue(result.redactedText().contains("[EMAIL_REDACTED]"));
        assertTrue(result.redactedText().contains("[SSN_REDACTED]"));
        assertTrue(result.redactedText().contains("[API_KEY_REDACTED]"));
    }
}
