package com.luv.fraud_service.consumer;

import java.util.List;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.luv.fraud_service.entity.Alert;
import com.luv.fraud_service.entity.Transaction;
import com.luv.fraud_service.repository.AlertRepository;
import com.luv.fraud_service.repository.TransactionRepository;
import com.luv.fraud_service.rules.FraudRule;

@Component
public class TransactionConsumer {

    private final TransactionRepository transactionRepository;
    private final AlertRepository alertRepository;
    private final List<FraudRule> rules;

    public TransactionConsumer(TransactionRepository transactionRepository,
                               AlertRepository alertRepository,
                               List<FraudRule> rules) {
        this.transactionRepository = transactionRepository;
        this.alertRepository = alertRepository;
        this.rules = rules;
    }

    @KafkaListener(topics = "card-transactions", groupId = "fraud-service")
    public void onMessage(Transaction transaction) {
        if (transactionRepository.existsById(transaction.getId())) {
            System.out.println("Skipping duplicate transaction " + transaction.getId());
            return;
        }
        transactionRepository.save(transaction);
        System.out.println("Saved transaction " + transaction.getId());

        for (FraudRule rule : rules) {
            Alert alert = rule.evaluate(transaction);
            if (alert != null) {
                alertRepository.save(alert);
                System.out.println("Alert " + alert.getRuleName() + " for transaction " + transaction.getId());
            }
        }
    }
}