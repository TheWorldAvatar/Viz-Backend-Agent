package com.cmclinnovations.agent.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Represents the 'error' field in the standardized API response.
 * Fields with null values will be excluded from the JSON output.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorPayload(int code, String message) {
}
