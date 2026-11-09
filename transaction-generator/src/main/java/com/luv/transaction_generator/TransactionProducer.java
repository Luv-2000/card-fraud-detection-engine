package com.luv.transaction_generator;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

@Component
public class TransactionProducer {

    private static final String TOPIC = "card-transactions";

    private final KafkaTemplate<String, Transaction> kafkaTemplate;

    public TransactionProducer(KafkaTemplate<String, Transaction> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void send(Transaction transaction) throws Exception {
        SendResult<String, Transaction> result =
                kafkaTemplate.send(TOPIC, transaction.getCardId(), transaction).get();

        System.out.println("Sent " + transaction.getId()
                + " to partition " + result.getRecordMetadata().partition()
                + ", offset " + result.getRecordMetadata().offset());
    }
}