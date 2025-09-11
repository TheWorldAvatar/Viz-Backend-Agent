package com.cmclinnovations.agent.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.cmclinnovations.agent.component.LocalisationTranslator;
import com.cmclinnovations.agent.component.ResponseEntityBuilder;
import com.cmclinnovations.agent.model.response.StandardApiResponse;
import com.cmclinnovations.agent.service.core.KGService;
import com.cmclinnovations.agent.utils.LocalisationResource;

@Service
public class UpdateService {
  private final KGService kgService;
  private final ResponseEntityBuilder responseEntityBuilder;

  private static final Logger LOGGER = LogManager.getLogger(UpdateService.class);

  /**
   * Constructs a new service with the following dependencies.
   * 
   * @param kgService             KG service for performing the query.
   * @param responseEntityBuilder A component to build the response entity.
   */
  public UpdateService(KGService kgService, ResponseEntityBuilder responseEntityBuilder) {
    this.kgService = kgService;
    this.responseEntityBuilder = responseEntityBuilder;
  }

  /**
   * Executes an UPDATE query on the knowledge graph.
   * 
   * @param query Query for execution.
   */
  public ResponseEntity<StandardApiResponse> update(String query) {
    int statusCode = this.kgService.executeUpdate(query);
    if (statusCode == 200) {
      LOGGER.info("Instance has been successfully updated!");
      return this.responseEntityBuilder.success(null,
          LocalisationTranslator.getMessage(LocalisationResource.SUCCESS_UPDATE_KEY));
    }
    return this.responseEntityBuilder.error(
        LocalisationTranslator.getMessage(LocalisationResource.ERROR_INVALID_SERVER_KEY),
        HttpStatus.INTERNAL_SERVER_ERROR);
  }
}
