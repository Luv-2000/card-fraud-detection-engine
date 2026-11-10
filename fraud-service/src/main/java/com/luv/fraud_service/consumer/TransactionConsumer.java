package com.luv.fraud_service.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.luv.fraud_service.entity.Transaction;
import com.luv.fraud_service.repository.TransactionRepository;

@Component
public class TransactionConsumer {

    private final TransactionRepository transactionRepository;

    public TransactionConsumer(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @KafkaListener(topics = "card-transactions", groupId = "fraud-service")
    public void onMessage(Transaction transaction) {
        if (transactionRepository.existsById(transaction.getId())) {
            System.out.println("Skipping duplicate transaction " + transaction.getId());
            return;
        }
        transactionRepository.save(transaction);
        System.out.println("Saved transaction " + transaction.getId());
    }
}