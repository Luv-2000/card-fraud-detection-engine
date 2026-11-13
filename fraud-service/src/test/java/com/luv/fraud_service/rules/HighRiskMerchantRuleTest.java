package com.luv.fraud_service.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.luv.fraud_service.entity.Alert;
import com.luv.fraud_service.entity.Transaction;

class HighRiskMerchantRuleTest {

    private final HighRiskMerchantRule rule =
            new HighRiskMerchantRule(List.of("GAMBLING", "CRYPTO_EXCHANGE"));

    private Transaction transactionWithCategory(String category) {
        Transaction t = new Transaction();
        t.setId(UUID.randomUUID());
        t.setCardId("CARD-10001");
        t.setAmount(new BigDecimal("500.00"));
        t.setCurrency("INR");
        t.setMerchantCategory(category);
        t.setCountry("IN");
        t.setTimestamp(Instant.now());
        return t;
    }

    @Test
    void categoryOnList_returnsAlert() {
        Alert alert = rule.evaluate(transactionWithCategory("CRYPTO_EXCHANGE"));

        assertNotNull(alert);
        assertEquals("HIGH_RISK_MERCHANT", alert.getRuleName());
    }

    @Test
    void categoryNotOnList_returnsNull() {
        assertNull(rule.evaluate(transactionWithCategory("GROCERY")));
    }

    @Test
    void lowerCaseCategory_returnsNull() {
        // The rule uses case-sensitive equals (a Stage 4 decision), so this does not match.
        assertNull(rule.evaluate(transactionWithCategory("gambling")));
    }
}