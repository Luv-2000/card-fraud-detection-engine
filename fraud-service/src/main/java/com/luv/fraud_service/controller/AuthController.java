package com.luv.fraud_service.controller;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.luv.fraud_service.exception.ErrorResponse;
import com.luv.fraud_service.security.LoginRequest;
import com.luv.fraud_service.security.LoginResponse;

@RestController
public class AuthController {

    private final JwtEncoder jwtEncoder;
    private final PasswordEncoder passwordEncoder;
    private final String configuredUsername;
    private final String configuredPasswordHash;
    private final long expiryMinutes;

    public AuthController(JwtEncoder jwtEncoder,
                          PasswordEncoder passwordEncoder,
                          @Value("${app.auth.username}") String configuredUsername,
                          @Value("${app.auth.password-hash}") String configuredPasswordHash,
                          @Value("${app.jwt.expiry-minutes}") long expiryMinutes) {
        this.jwtEncoder = jwtEncoder;
        this.passwordEncoder = passwordEncoder;
        this.configuredUsername = configuredUsername;
        this.configuredPasswordHash = configuredPasswordHash;
        this.expiryMinutes = expiryMinutes;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        boolean usernameOk = configuredUsername.equals(request.username());
        boolean passwordOk = request.password() != null
                && passwordEncoder.matches(request.password(), configuredPasswordHash);

        // Same message for a wrong username and a wrong password.
        if (!usernameOk || !passwordOk) {
            ErrorResponse body = new ErrorResponse(401, "Unauthorized",
                    "Invalid username or password", Instant.now());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
        }

        Instant now = Instant.now();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(request.username())
                .issuedAt(now)
                .expiresAt(now.plus(expiryMinutes, ChronoUnit.MINUTES))
                .build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        return ResponseEntity.ok(new LoginResponse(token));
    }
}