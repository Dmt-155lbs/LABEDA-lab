package edu.usfq.logistpulse.contract;

public record StockoutRiskDetected(String eventId, String occurredAt, String sku,
                                   String location, int available, int minimum) {}
