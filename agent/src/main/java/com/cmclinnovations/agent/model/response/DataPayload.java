package com.cmclinnovations.agent.model.response;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Represents the 'data' field in the standardized API response.
 * Fields with null values will be excluded from the JSON output.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DataPayload(String id, String message, boolean deleted, List<Map<String, Object>> items) {
}
