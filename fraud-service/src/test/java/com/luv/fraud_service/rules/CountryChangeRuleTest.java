package com.luv.fraud_service.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
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

class CountryChangeRuleTest {

    private ValueOperations<String, String> valueOps;
    private CountryChangeRule rule;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        rule = new CountryChangeRule(redisTemplate, 30);
    }

    private Transaction transactionInCountry(String country) {
        Transaction t = new Transaction();
        t.setId(UUID.randomUUID());
        t.setCardId("CARD-10001");
        t.setAmount(new BigDecimal("500.00"));
        t.setCurrency("INR");
        t.setMerchantCategory("GROCERY");
        t.setCountry(country);
        t.setTimestamp(Instant.now());
        return t;
    }

    @Test
    void differentCountryFromPrevious_returnsAlert() {
        when(valueOps.get("lastcountry:CARD-10001")).thenReturn("IN");

        Alert alert = rule.evaluate(transactionInCountry("US"));

        assertNotNull(alert);
        assertEquals("COUNTRY_CHANGE", alert.getRuleName());
    }

    @Test
    void sameCountryAsPrevious_returnsNull() {
        when(valueOps.get("lastcountry:CARD-10001")).thenReturn("IN");

        assertNull(rule.evaluate(transactionInCountry("IN")));
    }

    @Test
    void noPreviousCountry_returnsNull() {
        // We do not tell the fake what to answer, so get(...) returns null.
        assertNull(rule.evaluate(transactionInCountry("US")));
    }

    @Test
    void alwaysStoresTheNewCountry() {
        when(valueOps.get("lastcountry:CARD-10001")).thenReturn("IN");

        rule.evaluate(transactionInCountry("US"));

        verify(valueOps).set("lastcountry:CARD-10001", "US", Duration.ofMinutes(30));
    }
}