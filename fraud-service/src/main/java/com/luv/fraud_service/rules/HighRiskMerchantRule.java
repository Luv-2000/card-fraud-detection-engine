package com.luv.fraud_service.rules;

import com.luv.fraud_service.entity.Alert;
import com.luv.fraud_service.entity.Transaction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class HighRiskMerchantRule implements FraudRule {

    private final List<String> highRiskCategories;

    public HighRiskMerchantRule(
            @Value("${fraud.rules.high-risk-merchant.categories}") List<String> highRiskCategories) {
        this.highRiskCategories = highRiskCategories;
    }

    @Override
    public Alert evaluate(Transaction transaction) {
        for (String category : highRiskCategories) {
            if (category.equals(transaction.getMerchantCategory())) {
                Alert alert = new Alert();
                alert.setTransactionId(transaction.getId());
                alert.setRuleName("HIGH_RISK_MERCHANT");
                alert.setReason("Merchant category " + category + " is on the high-risk list");
                alert.setCreatedAt(Instant.now());
                return alert;
            }
        }
        return null;
    }
}