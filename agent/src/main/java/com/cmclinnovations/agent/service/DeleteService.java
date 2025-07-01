package com.cmclinnovations.agent.service;

import java.util.Queue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.cmclinnovations.agent.model.response.StandardApiResponse;
import com.cmclinnovations.agent.service.core.KGService;
import com.cmclinnovations.agent.service.core.QueryTemplateService;

@Service
public class DeleteService {
  private final KGService kgService;
  private final QueryTemplateService queryTemplateService;

  private static final Logger LOGGER = LogManager.getLogger(DeleteService.class);

  /**
   * Constructs a new service with the following dependencies.
   * 
   * @param kgService            KG service for performing the query.
   * @param queryTemplateService Service for generating query templates.
   */
  public DeleteService(KGService kgService, QueryTemplateService queryTemplateService) {
    this.kgService = kgService;
    this.queryTemplateService = queryTemplateService;
  }

  /**
   * Delete the instance associated with the target identifier.
   * 
   * @param resourceID The target resource identifier for the instance.
   * @param targetId   The target instance IRI.
   */
  public ResponseEntity<StandardApiResponse> delete(String resourceID, String targetId) {
    LOGGER.debug("Deleting {} instance of {} ...", resourceID, targetId);
    Queue<String> outputs = this.queryTemplateService.genDeleteQuery(resourceID, targetId);
    return this.kgService.delete(outputs.poll(), targetId);
  }
}
