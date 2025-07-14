package com.cmclinnovations.agent.exception;

import java.nio.file.FileSystemNotFoundException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import com.cmclinnovations.agent.component.LocalisationTranslator;
import com.cmclinnovations.agent.component.ResponseEntityBuilder;
import com.cmclinnovations.agent.model.response.StandardApiResponse;
import com.cmclinnovations.agent.utils.LocalisationResource;

@ControllerAdvice
public class AgentExceptionHandler {
  private final ResponseEntityBuilder responseEntityBuilder;

  private static final Logger LOGGER = LogManager.getLogger(AgentExceptionHandler.class);

  public AgentExceptionHandler(ResponseEntityBuilder responseEntityBuilder) {
    this.responseEntityBuilder = responseEntityBuilder;
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<StandardApiResponse> badRequestHandling(Exception exception, WebRequest request) {
    LOGGER.error(exception.getMessage());
    return this.responseEntityBuilder.error(exception.getMessage(), HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(InvalidRouteException.class)
  public ResponseEntity<StandardApiResponse> invalidRouteHandling(Exception exception, WebRequest request) {
    LOGGER.error(exception.getMessage());
    return this.responseEntityBuilder.error(
        LocalisationTranslator.getMessage(LocalisationResource.ERROR_CONTACT_KEY, exception.getMessage()),
        HttpStatus.NOT_FOUND);
  }

  @ExceptionHandler(FileSystemNotFoundException.class)
  public ResponseEntity<StandardApiResponse> missingResourceHandling(Exception exception, WebRequest request) {
    LOGGER.error(exception.getMessage());
    return this.responseEntityBuilder.error(exception.getMessage(), HttpStatus.NOT_FOUND);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<StandardApiResponse> globalExceptionHandling(Exception exception, WebRequest request) {
    LocalDateTime currentTime = LocalDateTime.now();
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
    LOGGER.error("Error encountered:", exception);
    return this.responseEntityBuilder.error(
        LocalisationTranslator.getMessage(LocalisationResource.ERROR_TIMESTAMP_KEY, currentTime.format(formatter),
            exception.getMessage()),
        HttpStatus.INTERNAL_SERVER_ERROR);
  }
}
