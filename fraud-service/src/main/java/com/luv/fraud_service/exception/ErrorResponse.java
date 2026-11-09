package com.luv.fraud_service.exception;

import java.time.Instant;

public record ErrorResponse(int status, String error, String message, Instant timestamp) {
}