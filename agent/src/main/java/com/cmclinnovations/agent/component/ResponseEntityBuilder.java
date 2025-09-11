package com.cmclinnovations.agent.component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import com.cmclinnovations.agent.model.response.DataPayload;
import com.cmclinnovations.agent.model.response.ErrorPayload;
import com.cmclinnovations.agent.model.response.StandardApiResponse;

@Component
public class ResponseEntityBuilder {
  @Value("${app.version}")
  private String appVersion;

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
  public ResponseEntity<StandardApiResponse<?>> success(String id, String message) {
    return success(id, message, null, null);
  }

  /**
   * Builds a successful response for one item.
   *
   * @param message An optional message for its corresponding key in the data
   *                payload.
   * @param item    An item to be added into the response payload's collection.
   */
  public ResponseEntity<StandardApiResponse<?>> success(String message, Map<String, Object> item) {
    List<Map<String, Object>> items = new ArrayList<>();
    items.add(item);
    return success(null, message, null, items);
  }

  /**
   * Builds a successful response for multiple items.
   *
   * @param message An optional message for its corresponding key in the data
   *                payload.
   * @param items   A collection of instances/data.
   */
  public ResponseEntity<StandardApiResponse<?>> success(String message, List<Map<String, Object>> items) {
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
  public ResponseEntity<StandardApiResponse<?>> success(String id, String message, Boolean deleted,
      List<Map<String, Object>> items) {
    DataPayload<Map<String, Object>> dataPayload = new DataPayload<>(id, message, deleted, items);
    return new ResponseEntity<>(
        new StandardApiResponse<>(this.appVersion, dataPayload, null),
        HttpStatus.OK);
  }

  /**
   * Builds a successful response with the data payload for a list of strings.
   *
   * @param items An optional collection of strings.
   */
  public ResponseEntity<StandardApiResponse<?>> success(List<String> items) {
    DataPayload<String> dataPayload = new DataPayload<>(null, null, null, items);
    return new ResponseEntity<>(
        new StandardApiResponse<>(this.appVersion, dataPayload, null),
        HttpStatus.OK);
  }

  /**
   * Builds error response based on the required message.
   *
   * @param message    The error message to include in the response.
   * @param httpStatus The HTTP status.
   */
  public ResponseEntity<StandardApiResponse<?>> error(String message, HttpStatus httpStatus) {
    return new ResponseEntity<>(
        new StandardApiResponse<>(this.appVersion, null, new ErrorPayload(httpStatus.value(), message)),
        httpStatus);
  }
}
