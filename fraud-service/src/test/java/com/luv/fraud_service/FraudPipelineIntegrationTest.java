package com.luv.fraud_service;

import java.time.Instant;
import java.util.TimeZone;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.luv.fraud_service.entity.Alert;
import com.luv.fraud_service.repository.AlertRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import com.luv.fraud_service.repository.TransactionRepository;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class FraudPipelineIntegrationTest {

    // ---------- TEMPLATE (set up once, rarely touched) ----------

    // Your main() sets the time zone; tests skip main(), so set it here.
    // Same Postgres time zone workaround as in Stage 2.
    static {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    // Three real containers, started before the test, deleted after.
    // @ServiceConnection points Spring Boot at each container automatically.
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7").withExposedPorts(6379);

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:4.3.1");

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private AlertRepository alertRepository;

    // ---------- THE ACTUAL TEST ----------

    @Test
    void highAmountTransactionOnKafka_createsHighAmountAlert() throws Exception {
        UUID transactionId = UUID.randomUUID();
        String json = """
                {"id":"%s","cardId":"CARD-10001","amount":100000.00,"currency":"INR",
                 "merchantCategory":"GROCERY","country":"IN","timestamp":"%s"}
                """.formatted(transactionId, Instant.now());

        // 1. Send a Rs 1,00,000 transaction into the topic.
        kafkaTemplate.send("card-transactions", "CARD-10001", json).get();

        // 2. Kafka delivers in the background, so check once per second, up to 30 seconds.
        Alert found = null;
        for (int attempt = 0; attempt < 30 && found == null; attempt++) {
            for (Alert alert : alertRepository.findAll()) {
                if (transactionId.equals(alert.getTransactionId())
                        && "HIGH_AMOUNT".equals(alert.getRuleName())) {
                    found = alert;
                }
            }
            if (found == null) {
                Thread.sleep(1000);
            }
        }

        // 3. The alert row must exist.
        assertNotNull(found);
    }

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Value("${local.server.port}")
    private int port;

    // ---------- HELPERS ----------

    private String transactionJson(UUID id, String amount) {
        return """
                {"id":"%s","cardId":"CARD-10001","amount":%s,"currency":"INR",
                 "merchantCategory":"GROCERY","country":"IN","timestamp":"%s"}
                """.formatted(id, amount, Instant.now());
    }

    private int get(String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest
                .newBuilder(URI.create("http://localhost:" + port + path)).GET();
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return HttpClient.newHttpClient()
                .send(builder.build(), HttpResponse.BodyHandlers.ofString())
                .statusCode();
    }

    // Same token the login endpoint would give, made directly with the same signing key.
    private String makeToken() {
        Instant now = Instant.now();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject("test").issuedAt(now).expiresAt(now.plusSeconds(300)).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    // ---------- VALIDATION TEST ----------

    @Test
    void invalidMessage_isSkipped_andConsumerKeepsWorking() throws Exception {
        UUID badId = UUID.randomUUID();
        UUID goodId = UUID.randomUUID();

        // Bad message first (amount -50 breaks @Positive), then a good one on the same card,
        // so both go through the same Kafka partition in this order.
        kafkaTemplate.send("card-transactions", "CARD-10001", transactionJson(badId, "-50.00")).get();
        kafkaTemplate.send("card-transactions", "CARD-10001", transactionJson(goodId, "100000.00")).get();

        // If the good one produces an alert, the consumer got past the bad one.
        boolean goodAlertSeen = false;
        for (int attempt = 0; attempt < 30 && !goodAlertSeen; attempt++) {
            for (Alert alert : alertRepository.findAll()) {
                if (goodId.equals(alert.getTransactionId())) {
                    goodAlertSeen = true;
                }
            }
            if (!goodAlertSeen) {
                Thread.sleep(1000);
            }
        }

        assertTrue(goodAlertSeen);
        assertFalse(transactionRepository.existsById(badId));
    }

    // ---------- SECURITY TESTS ----------

    @Test
    void alertsWithoutToken_returns401() throws Exception {
        assertEquals(401, get("/alerts", null));
    }

    @Test
    void alertsWithValidToken_returns200() throws Exception {
        assertEquals(200, get("/alerts", makeToken()));
    }

    @Test
    void alertsWithTamperedToken_returns401() throws Exception {
        assertEquals(401, get("/alerts", makeToken() + "x"));
    }
}