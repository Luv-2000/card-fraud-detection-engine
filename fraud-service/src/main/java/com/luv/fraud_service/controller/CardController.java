package com.luv.fraud_service.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.luv.fraud_service.llm.AlertSummaryResponse;
import com.luv.fraud_service.llm.AlertSummaryService;

@RestController
public class CardController {

    private final AlertSummaryService alertSummaryService;

    public CardController(AlertSummaryService alertSummaryService) {
        this.alertSummaryService = alertSummaryService;
    }

    @GetMapping("/cards/{cardId}/alert-summary")
    public AlertSummaryResponse getAlertSummary(@PathVariable String cardId) {
        return alertSummaryService.buildSummary(cardId);
    }
}