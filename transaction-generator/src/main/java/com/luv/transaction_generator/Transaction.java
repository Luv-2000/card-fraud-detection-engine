package com.luv.transaction_generator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class Transaction {

    private final UUID id;
    private final String cardId;
    private final BigDecimal amount;
    private final String currency;
    private final String merchantCategory;
    private final String country;
    private final Instant timestamp;

    public Transaction(UUID id, String cardId, BigDecimal amount, String currency,
                       String merchantCategory, String country, Instant timestamp) {
        this.id = id;
        this.cardId = cardId;
        this.amount = amount;
        this.currency = currency;
        this.merchantCategory = merchantCategory;
        this.country = country;
        this.timestamp = timestamp;
    }

    public UUID getId() { return id; }
    public String getCardId() { return cardId; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getMerchantCategory() { return merchantCategory; }
    public String getCountry() { return country; }
    public Instant getTimestamp() { return timestamp; }
}