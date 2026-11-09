package com.luv.fraud_service.controller;

import com.luv.fraud_service.entity.Alert;
import com.luv.fraud_service.repository.AlertRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import java.util.Optional;
import com.luv.fraud_service.exception.ResourceNotFoundException;

import java.util.List;

@RestController
public class AlertController {

    private final AlertRepository alertRepository;

    public AlertController(AlertRepository alertRepository) {
        this.alertRepository = alertRepository;
    }

    @GetMapping("/alerts")
    public List<Alert> listAlerts() {
        return alertRepository.findAll();
    }

    @GetMapping("/alerts/{id}")
    public Alert getAlert(@PathVariable Long id) {
        Optional<Alert> alert = alertRepository.findById(id);
        if (alert.isPresent()) {
            return alert.get();
        }
        throw new ResourceNotFoundException("Alert not found with id " + id);
    }
}