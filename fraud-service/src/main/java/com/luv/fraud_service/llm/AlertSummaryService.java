package com.luv.fraud_service.llm;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.luv.fraud_service.entity.Alert;
import com.luv.fraud_service.repository.AlertRepository;

@Service
public class AlertSummaryService {

    private static final int MAX_ALERTS_IN_PROMPT = 20;

    private final AlertRepository alertRepository;
    private final LlmClient llmClient;

    public AlertSummaryService(AlertRepository alertRepository, LlmClient llmClient) {
        this.alertRepository = alertRepository;
        this.llmClient = llmClient;
    }

    public AlertSummaryResponse buildSummary(String cardId) {
        List<Alert> alerts = alertRepository.findAlertsByCardId(cardId);

        if (alerts.isEmpty()) {
            return new AlertSummaryResponse(cardId, 0, new ArrayList<>(),
                    "No alerts found for this card.");
        }

        List<String> ruleNames = new ArrayList<>();
        for (Alert alert : alerts) {
            if (!ruleNames.contains(alert.getRuleName())) {
                ruleNames.add(alert.getRuleName());
            }
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append("You are helping a fraud analyst. Card ").append(cardId)
                .append(" has ").append(alerts.size()).append(" alerts. ")
                .append("The newest ones are listed below, one per line as RULE: reason.\n");

        int used = 0;
        for (Alert alert : alerts) {
            if (used == MAX_ALERTS_IN_PROMPT) {
                break;
            }
            prompt.append("- ").append(alert.getRuleName())
                    .append(": ").append(alert.getReason()).append("\n");
            used++;
        }

        prompt.append("Using only these alerts, write a 3 to 4 line summary of the risk pattern. ")
                .append("Do not invent facts. Do not say fraud is confirmed.");

        String summary = llmClient.summarize(prompt.toString());
        if (summary == null) {
            summary = "Summary unavailable.";
        }

        return new AlertSummaryResponse(cardId, alerts.size(), ruleNames, summary);
    }
}