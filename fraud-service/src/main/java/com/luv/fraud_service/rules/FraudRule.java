package com.luv.fraud_service.rules;

import com.luv.fraud_service.entity.Alert;
import com.luv.fraud_service.entity.Transaction;

public interface FraudRule {

    // Returns an Alert if the rule is triggered, or null if not.
    Alert evaluate(Transaction transaction);
}