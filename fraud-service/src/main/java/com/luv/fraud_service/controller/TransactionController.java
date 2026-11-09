package com.luv.fraud_service.controller;

import com.luv.fraud_service.entity.Transaction;
import com.luv.fraud_service.repository.TransactionRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class TransactionController {

    private final TransactionRepository transactionRepository;

    public TransactionController(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @GetMapping("/transactions")
    public List<Transaction> listTransactions() {
        return transactionRepository.findAll();
    }
}