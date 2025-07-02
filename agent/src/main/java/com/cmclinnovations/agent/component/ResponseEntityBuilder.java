package com.cmclinnovations.agent.component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import com.cmclinnovations.agent.model.response.DataPayload;
import com.cmclinnovations.agent.model.response.ErrorPayload;
import com.cmclinnovations.agent.model.response.StandardApiResponse;

@Component
public class ResponseEntityBuilder {
  private static final String API_VERSION = "1.7.0";

  /**
   * Constructs a component to build the response entity.
   */
  ResponseEntityBuilder() {
  }

  /**
   * Builds a successful response with the data payload without a deleted key.
   *
   * @param id      An optional id for its corresponding key in the data payload.
   * @param message An optional message for its corresponding key in the data
   *                payload.
   */
  public static ResponseEntity<StandardApiResponse> success(String id, String message) {
    return success(id, message, null, null);
  }

  /**
   * Builds a successful response for one item.
   *
   * @param message An optional message for its corresponding key in the data
   *                payload.
   * @param item    An item to be added into the response payload's collection.
   */
  public static ResponseEntity<StandardApiResponse> success(String message, Map<String, Object> item) {
    List<Map<String, Object>> items = new ArrayList<>();
    items.add(item);
    return success(null, message, null, items);
  }

  /**
   * Builds a successful response with the data payload.
   *
   * @param id      An optional id for its corresponding key in the data payload.
   * @param message An optional message for its corresponding key in the data
   *                payload.
   * @param deleted An optional boolean to indicate the deleted status for any
   *                DELETE request.
   * @param items   An optional collection of instances/data.
   */
  public static ResponseEntity<StandardApiResponse> success(String id, String message, Boolean deleted,
      List<Map<String, Object>> items) {
    DataPayload dataPayload = new DataPayload(id, message, deleted, items);
    return new ResponseEntity<>(
        new StandardApiResponse(API_VERSION, dataPayload, null),
        HttpStatus.OK);
  }

  /**
   * Builds error response based on the required message.
   *
   * @param message    The error message to include in the response.
   * @param httpStatus The HTTP status.
   */
  public static ResponseEntity<StandardApiResponse> error(String message, HttpStatus httpStatus) {
    return new ResponseEntity<>(
        new StandardApiResponse(API_VERSION, null, new ErrorPayload(httpStatus.value(), message)),
        httpStatus);
  }
}
