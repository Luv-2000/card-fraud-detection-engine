package com.luv.fraud_service.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.luv.fraud_service.entity.Alert;
import com.luv.fraud_service.repository.AlertRepository;

class AlertSummaryServiceTest {

    private final AlertRepository alertRepository = mock(AlertRepository.class);
    private final LlmClient llmClient = mock(LlmClient.class);
    private final AlertSummaryService service =
            new AlertSummaryService(alertRepository, llmClient);

    private Alert alert(String rule, String reason) {
        Alert a = mock(Alert.class);
        when(a.getRuleName()).thenReturn(rule);
        when(a.getReason()).thenReturn(reason);
        return a;
    }

    @Test
    void returnsSummaryAndDeduplicatedRuleNames() {
        List<Alert> alerts = new ArrayList<>();
        alerts.add(alert("HIGH_AMOUNT", "Amount 120000.00 exceeds threshold 50000"));
        alerts.add(alert("COUNTRY_CHANGE", "Country changed IN to SG"));
        alerts.add(alert("HIGH_AMOUNT", "Amount 90000.00 exceeds threshold 50000"));

        when(alertRepository.findAlertsByCardId("C-1042")).thenReturn(alerts);
        when(llmClient.summarize(anyString())).thenReturn("Pattern summary.");

        AlertSummaryResponse response = service.buildSummary("C-1042");

        assertEquals(3, response.alertCount());
        assertEquals(List.of("HIGH_AMOUNT", "COUNTRY_CHANGE"), response.ruleNames());
        assertEquals("Pattern summary.", response.summary());
    }

    @Test
    void whenLlmFailsStillReturnsCountAndRuleNames() {
        List<Alert> alerts = new ArrayList<>();
        alerts.add(alert("HIGH_AMOUNT", "Amount 120000.00 exceeds threshold 50000"));

        when(alertRepository.findAlertsByCardId("C-1042")).thenReturn(alerts);
        when(llmClient.summarize(anyString())).thenReturn(null);

        AlertSummaryResponse response = service.buildSummary("C-1042");

        assertEquals(1, response.alertCount());
        assertEquals(List.of("HIGH_AMOUNT"), response.ruleNames());
        assertEquals("Summary unavailable.", response.summary());
    }

    @Test
    void whenNoAlertsDoesNotCallLlm() {
        when(alertRepository.findAlertsByCardId("C-9999")).thenReturn(new ArrayList<>());

        AlertSummaryResponse response = service.buildSummary("C-9999");

        assertEquals(0, response.alertCount());
        assertEquals("No alerts found for this card.", response.summary());
        verify(llmClient, never()).summarize(anyString());
    }

    @Test
    void promptContainsAtMostTwentyAlertsButCountIsFullTotal() {
        List<Alert> alerts = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            alerts.add(alert("HIGH_AMOUNT", "Amount " + i));
        }
        when(alertRepository.findAlertsByCardId("C-1042")).thenReturn(alerts);
        when(llmClient.summarize(anyString())).thenReturn("ok");

        AlertSummaryResponse response = service.buildSummary("C-1042");

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmClient).summarize(promptCaptor.capture());

        int alertLines = 0;
        for (String line : promptCaptor.getValue().split("\n")) {
            if (line.startsWith("- ")) {
                alertLines++;
            }
        }
        assertEquals(20, alertLines);
        assertEquals(25, response.alertCount());
    }
}