package com.luv.transaction_generator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Random;
import java.util.UUID;

public class TransactionFactory {

    private static final String[] NORMAL_CATEGORIES = {"GROCERY", "FUEL", "RESTAURANT", "ONLINE_RETAIL"};
    private static final String[] RISKY_CATEGORIES = {"GAMBLING", "CRYPTO_EXCHANGE"};
    private static final String[] FOREIGN_COUNTRIES = {"US", "SG", "AE"};

    private final Random random = new Random();
    // Burst state: when burstRemaining > 0, the next transactions reuse burstCardId.
    private String burstCardId = null;
    private int burstRemaining = 0;
    private final int cardCount;

    public TransactionFactory(int cardCount) {
        this.cardCount = cardCount;
    }

    public Transaction create() {
        // 20 cards: CARD-10001 to CARD-10020
        String cardId;
        if (burstRemaining > 0) {
            // inside a burst: same card again
            cardId = burstCardId;
            burstRemaining--;
        } else if (random.nextInt(300) == 0) {
            // about 1 in 300: start a burst of 12 transactions on one card
            burstCardId = "CARD-" + (10001 + random.nextInt(cardCount));
            burstRemaining = 11;
            cardId = burstCardId;
        } else {
            cardId = "CARD-" + (10001 + random.nextInt(cardCount));
        }
        // amount in paise (1 rupee = 100 paise), so we never use floating point
        long paise;
        if (random.nextInt(50) == 0) {
            // about 1 in 50: large amount, Rs 1,00,000 to Rs 5,00,000
            paise = (100000 + random.nextInt(400001)) * 100L + random.nextInt(100);
        } else {
            // normal amount, Rs 100 to Rs 5,000
            paise = (100 + random.nextInt(4901)) * 100L + random.nextInt(100);
        }

        // about 1 in 20: foreign country
        String country = "IN";
        if (random.nextInt(20) == 0) {
            country = FOREIGN_COUNTRIES[random.nextInt(FOREIGN_COUNTRIES.length)];
        }

        // about 1 in 25: risky category
        String category;
        if (random.nextInt(25) == 0) {
            category = RISKY_CATEGORIES[random.nextInt(RISKY_CATEGORIES.length)];
        } else {
            category = NORMAL_CATEGORIES[random.nextInt(NORMAL_CATEGORIES.length)];
        }

        return new Transaction(UUID.randomUUID(), cardId, BigDecimal.valueOf(paise, 2),
                "INR", category, country, Instant.now());
    }
}