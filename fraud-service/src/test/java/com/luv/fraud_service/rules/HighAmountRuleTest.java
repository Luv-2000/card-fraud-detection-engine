package com.luv.fraud_service.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.luv.fraud_service.entity.Alert;
import com.luv.fraud_service.entity.Transaction;

class HighAmountRuleTest {

    private final HighAmountRule rule = new HighAmountRule(new BigDecimal("50000"));

    private Transaction transactionWithAmount(String amount) {
        Transaction t = new Transaction();
        t.setId(UUID.randomUUID());
        t.setCardId("CARD-10001");
        t.setAmount(new BigDecimal(amount));
        t.setCurrency("INR");
        t.setMerchantCategory("GROCERY");
        t.setCountry("IN");
        t.setTimestamp(Instant.now());
        return t;
    }

    @Test
    void amountOverThreshold_returnsAlert() {
        Transaction t = transactionWithAmount("50000.01");

        Alert alert = rule.evaluate(t);

        assertNotNull(alert);
        assertEquals("HIGH_AMOUNT", alert.getRuleName());
        assertEquals(t.getId(), alert.getTransactionId());
    }

    @Test
    void amountExactlyAtThreshold_returnsNull() {
        Alert alert = rule.evaluate(transactionWithAmount("50000.00"));

        assertNull(alert);
    }

    @Test
    void amountBelowThreshold_returnsNull() {
        Alert alert = rule.evaluate(transactionWithAmount("1200.50"));

        assertNull(alert);
    }
}