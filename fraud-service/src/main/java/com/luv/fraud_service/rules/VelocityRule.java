package com.luv.fraud_service.rules;

import com.luv.fraud_service.entity.Alert;
import com.luv.fraud_service.entity.Transaction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class VelocityRule implements FraudRule {

    private final StringRedisTemplate redisTemplate;
    private final int maxTransactions;
    private final long windowSeconds;

    public VelocityRule(
            StringRedisTemplate redisTemplate,
            @Value("${fraud.rules.velocity.max-transactions}") int maxTransactions,
            @Value("${fraud.rules.velocity.window-seconds}") long windowSeconds) {
        this.redisTemplate = redisTemplate;
        this.maxTransactions = maxTransactions;
        this.windowSeconds = windowSeconds;
    }

    @Override
    public Alert evaluate(Transaction transaction) {
        String key = "velocity:" + transaction.getCardId();

        Long count = redisTemplate.opsForValue().increment(key);

        // First transaction of a window: start the countdown.
        if (count != null && count == 1) {
            redisTemplate.expire(key, Duration.ofSeconds(windowSeconds));
        }

        if (count != null && count > maxTransactions) {
            Alert alert = new Alert();
            alert.setTransactionId(transaction.getId());
            alert.setRuleName("VELOCITY");
            alert.setReason("Card " + transaction.getCardId() + " made " + count
                    + " transactions within " + windowSeconds
                    + " seconds (limit " + maxTransactions + ")");
            alert.setCreatedAt(Instant.now());
            return alert;
        }
        return null;
    }
}