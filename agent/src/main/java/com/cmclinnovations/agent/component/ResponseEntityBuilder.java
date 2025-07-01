package com.cmclinnovations.agent.component;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import com.cmclinnovations.agent.model.response.ErrorPayload;
import com.cmclinnovations.agent.model.response.StandardApiResponse;

@Component
public class ResponseEntityBuilder {
  private static final String API_VERSION = "1.7.0";
  private static final String DATA_MESSAGE_KEY = "message";

  /**
   * Constructs a component to build the response entity.
   */
  ResponseEntityBuilder() {
  }

  /**
   * Builds a successful response with the given message.
   *
   * @param message An optional message to include in the response under the
   *                message key.
   */
  public static ResponseEntity<StandardApiResponse> success(String message) {
    return success(message, new HashMap<>());
  }

  /**
   * Builds a successful response with the given message and other success data.
   *
   * @param message     An optional message to include in the response under the
   *                    message key.
   * @param successData Remaining fields to include in the response.
   */
  public static ResponseEntity<StandardApiResponse> success(String message, Map<String, Object> successData) {
    if (message != null) {
      successData.put(DATA_MESSAGE_KEY, message);
    }
    return new ResponseEntity<>(
        new StandardApiResponse(API_VERSION, successData, null),
        HttpStatus.OK);
  }

  /**
   * Builds a successful response with other success data.
   *
   * @param successData Fields to include in the response.
   */
  public static ResponseEntity<StandardApiResponse> success(Map<String, Object> successData) {
    return new ResponseEntity<>(
        new StandardApiResponse(API_VERSION, successData, null),
        HttpStatus.OK);
  }

  /**
   * Builds error response based on the required message.
   *
   * @param message  The error message to include in the response.
   * @param httpCode The HTTP status code.
   * @param status   The HTTP status to be generated.
   */
  public static ResponseEntity<StandardApiResponse> error(String message, int httpCode, HttpStatus status) {
    return new ResponseEntity<>(
        new StandardApiResponse(API_VERSION, null, new ErrorPayload(httpCode, message)),
        status);
  }
}
