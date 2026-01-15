package com.cmclinnovations.agent.service.core;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.cmclinnovations.agent.utils.LifecycleResource;
import com.cmclinnovations.agent.utils.QueryResource;

@Service
public class ChangelogService {
  private final DateTimeService dateTimeService;
  private static final String CREATION_MESSAGE = "Created";

  /**
   * Constructs a new service with the following dependencies.
   */
  public ChangelogService(DateTimeService dateTimeService) {
    this.dateTimeService = dateTimeService;
  }

  /**
   * Generates a map of replacements for logging the creation of an entity.
   * 
   * @param iri The entity IRI to be appended with creation logs.
   */
  public Map<String, Object> logCreation(String iri) {
    String currentDateTime = this.dateTimeService.getCurrentDateTime();
    Map<String, Object> replacements = new HashMap<>();
    replacements.put(QueryResource.IRI_KEY, iri);
    replacements.put(LifecycleResource.REMARKS_KEY, CREATION_MESSAGE);
    replacements.put(LifecycleResource.TIMESTAMP_KEY, currentDateTime);
    return replacements;
  }
}
