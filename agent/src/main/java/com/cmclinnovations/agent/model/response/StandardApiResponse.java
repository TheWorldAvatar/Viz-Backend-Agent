package com.cmclinnovations.agent.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The main wrapper class for standardised API responses based on Google' JSON
 * Style guide https://google.github.io/styleguide/jsoncstyleguide.xml.
 * Fields with null values will be excluded from the JSON output.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StandardApiResponse<T>(String apiVersion, DataPayload<T> data, ErrorPayload error) {
}
