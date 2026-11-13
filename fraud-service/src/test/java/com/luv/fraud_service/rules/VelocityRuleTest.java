package com.luv.fraud_service.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.luv.fraud_service.entity.Alert;
import com.luv.fraud_service.entity.Transaction;

class VelocityRuleTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private VelocityRule rule;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        rule = new VelocityRule(redisTemplate, 10, 60);
    }

    private Transaction transaction() {
        Transaction t = new Transaction();
        t.setId(UUID.randomUUID());
        t.setCardId("CARD-10001");
        t.setAmount(new BigDecimal("500.00"));
        t.setCurrency("INR");
        t.setMerchantCategory("GROCERY");
        t.setCountry("IN");
        t.setTimestamp(Instant.now());
        return t;
    }

    @Test
    void countAboveLimit_returnsAlert() {
        when(valueOps.increment("velocity:CARD-10001")).thenReturn(11L);

        Alert alert = rule.evaluate(transaction());

        assertNotNull(alert);
        assertEquals("VELOCITY", alert.getRuleName());
    }

    @Test
    void countExactlyAtLimit_returnsNull() {
        when(valueOps.increment("velocity:CARD-10001")).thenReturn(10L);

        Alert alert = rule.evaluate(transaction());

        assertNull(alert);
    }

    @Test
    void firstTransaction_startsTheCountdown() {
        when(valueOps.increment("velocity:CARD-10001")).thenReturn(1L);

        rule.evaluate(transaction());

        verify(redisTemplate).expire("velocity:CARD-10001", Duration.ofSeconds(60));
    }

    @Test
    void laterTransaction_doesNotRestartTheCountdown() {
        when(valueOps.increment("velocity:CARD-10001")).thenReturn(5L);

        rule.evaluate(transaction());

        verify(redisTemplate, never()).expire(anyString(), any(Duration.class));
    }
}