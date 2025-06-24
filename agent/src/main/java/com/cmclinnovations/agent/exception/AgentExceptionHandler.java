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
import com.cmclinnovations.agent.utils.LocalisationResource;

@ControllerAdvice
public class AgentExceptionHandler {
  private static final Logger LOGGER = LogManager.getLogger(AgentExceptionHandler.class);

  public AgentExceptionHandler() {
    // No dependencies required
  }

  @ExceptionHandler(FileSystemNotFoundException.class)
  public ResponseEntity<String> invalidResourceHandling(Exception exception, WebRequest request) {
    LOGGER.error(exception.getMessage());
    return new ResponseEntity<>(
        LocalisationTranslator.getMessage(LocalisationResource.ERROR_CONTACT_KEY, exception.getMessage()),
        HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<String> globalExceptionHandling(Exception exception, WebRequest request) {
    LocalDateTime currentTime = LocalDateTime.now();
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
    LOGGER.error("Error encountered:", exception);
    return new ResponseEntity<>(
        LocalisationTranslator.getMessage(LocalisationResource.ERROR_TIMESTAMP_KEY, currentTime.format(formatter),
            exception.getMessage()),
        HttpStatus.INTERNAL_SERVER_ERROR);
  }
}
