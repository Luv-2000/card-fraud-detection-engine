package com.luv.fraud_service.rules;

import com.luv.fraud_service.entity.Alert;
import com.luv.fraud_service.entity.Transaction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;

@Component
public class HighAmountRule implements FraudRule {

    private final BigDecimal threshold;

    public HighAmountRule(@Value("${fraud.rules.high-amount.threshold}") BigDecimal threshold) {
        this.threshold = threshold;
    }

    @Override
    public Alert evaluate(Transaction transaction) {
        if (transaction.getAmount().compareTo(threshold) > 0) {
            Alert alert = new Alert();
            alert.setTransactionId(transaction.getId());
            alert.setRuleName("HIGH_AMOUNT");
            alert.setReason("Amount " + transaction.getAmount() + " is above the threshold " + threshold);
            alert.setCreatedAt(Instant.now());
            return alert;
        }
        return null;
    }
}