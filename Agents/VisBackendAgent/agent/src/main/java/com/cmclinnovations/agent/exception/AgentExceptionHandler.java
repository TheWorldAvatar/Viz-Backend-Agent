package com.cmclinnovations.agent.exception;

import java.text.MessageFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

@ControllerAdvice
public class AgentExceptionHandler {
  @ExceptionHandler(Exception.class)
  public ResponseEntity<String> globalExceptionHandling(Exception exception, WebRequest request) {
    LocalDateTime currentTime = LocalDateTime.now();
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
    return new ResponseEntity<>(
        MessageFormat.format("Error encountered at {0}: {1}", currentTime.format(formatter), exception.getMessage()),
        HttpStatus.INTERNAL_SERVER_ERROR);
  }
}
