package com.luv.fraud_service.llm;

import java.util.List;

public record AlertSummaryResponse(
        String cardId,
        int alertCount,
        List<String> ruleNames,
        String summary) {
}