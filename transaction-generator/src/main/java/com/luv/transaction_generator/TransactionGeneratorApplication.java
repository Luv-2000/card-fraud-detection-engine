package com.luv.transaction_generator;

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
	CommandLineRunner sendForever(TransactionProducer producer) {
		return args -> {
			TransactionFactory factory = new TransactionFactory();
			while (true) {
				producer.send(factory.create());
				Thread.sleep(500);
			}
		};
	}
}