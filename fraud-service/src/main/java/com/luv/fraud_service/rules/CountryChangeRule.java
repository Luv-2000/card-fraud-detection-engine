package com.luv.fraud_service.rules;

import com.luv.fraud_service.entity.Alert;
import com.luv.fraud_service.entity.Transaction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class CountryChangeRule implements FraudRule {

    private final StringRedisTemplate redisTemplate;
    private final long windowMinutes;

    public CountryChangeRule(
            StringRedisTemplate redisTemplate,
            @Value("${fraud.rules.country-change.window-minutes}") long windowMinutes) {
        this.redisTemplate = redisTemplate;
        this.windowMinutes = windowMinutes;
    }

    @Override
    public Alert evaluate(Transaction transaction) {
        String key = "lastcountry:" + transaction.getCardId();

        // Read the previous country first, then overwrite it with this one.
        String previousCountry = redisTemplate.opsForValue().get(key);
        redisTemplate.opsForValue().set(key, transaction.getCountry(), Duration.ofMinutes(windowMinutes));

        if (previousCountry != null && !previousCountry.equals(transaction.getCountry())) {
            Alert alert = new Alert();
            alert.setTransactionId(transaction.getId());
            alert.setRuleName("COUNTRY_CHANGE");
            alert.setReason("Card " + transaction.getCardId() + " changed country from "
                    + previousCountry + " to " + transaction.getCountry()
                    + " within " + windowMinutes + " minutes");
            alert.setCreatedAt(Instant.now());
            return alert;
        }
        return null;
    }
}