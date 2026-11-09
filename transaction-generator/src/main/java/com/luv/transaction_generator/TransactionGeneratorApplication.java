package com.luv.transaction_generator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class TransactionGeneratorApplication {

	public static void main(String[] args) {
		SpringApplication.run(TransactionGeneratorApplication.class, args);
	}

	@Bean
	CommandLineRunner sendOne(TransactionProducer producer) {
		return args -> producer.send(new Transaction(
				UUID.randomUUID(), "CARD-10482", new BigDecimal("2499.00"),
				"INR", "GROCERY", "IN", Instant.now()));
	}
}