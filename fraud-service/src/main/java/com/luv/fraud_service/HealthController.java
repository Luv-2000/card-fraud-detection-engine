package com.luv.fraud_service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private final JdbcTemplate jdbcTemplate;

    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/ping")
    public String ping() {
        return "fraud-service is up";
    }

    @GetMapping("/db-ping")
    public String dbPing() {
        Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        return "db ok: " + result;
    }
}