package com.luv.transaction_generator;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Value;

@SpringBootApplication
public class TransactionGeneratorApplication {

	public static void main(String[] args) {
		SpringApplication.run(TransactionGeneratorApplication.class, args);
	}

	@Bean
	CommandLineRunner sendForever(TransactionProducer producer,
								  @Value("${generator.delay-ms}") long delayMs) {
		return args -> {
			TransactionFactory factory = new TransactionFactory();
			while (true) {
				producer.send(factory.create());
				if (delayMs > 0) {
					Thread.sleep(delayMs);
				}
			}
		};
	}
}