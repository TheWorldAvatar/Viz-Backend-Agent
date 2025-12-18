package com.cmclinnovations.agent.model.response;

/**
 * An invoice line with amount and the description.
 */
public record InvoiceLine(String amount, String description) {
}
