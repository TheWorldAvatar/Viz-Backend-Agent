package com.cmclinnovations.agent.service;

import java.text.MessageFormat;
import java.util.Map;

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
  private final AddService addService;
  private final DeleteService deleteService;
  private final KGService kgService;
  private final ResponseEntityBuilder responseEntityBuilder;

  private static final Logger LOGGER = LogManager.getLogger(UpdateService.class);

  /**
   * Constructs a new service with the following dependencies.
   * 
   * @param addService            KG service to add instances.
   * @param deleteService         KG service to delete instances.
   * @param kgService             KG service for performing the query.
   * @param responseEntityBuilder A component to build the response entity.
   */
  public UpdateService(AddService addService, DeleteService deleteService, KGService kgService,
      ResponseEntityBuilder responseEntityBuilder) {
    this.addService = addService;
    this.deleteService = deleteService;
    this.kgService = kgService;
    this.responseEntityBuilder = responseEntityBuilder;
  }

  /**
   * Updates the instance in the knowledge graph by executing DELETE and ADD
   * actions separately. Note that the DELETE action can fail gracefully and this
   * effectively becomes an ADD action.
   * 
   * @param id               Target instance identifier .
   * @param resourceID       The resource identifier ie type for the instance.
   * @param successMessageId Successful message identifier.
   * @param editedParams     Edited parameters to replace the current values.
   */
  public ResponseEntity<StandardApiResponse<?>> update(String id, String resourceId, String successMessageId,
      Map<String, Object> editedParams) {
    ResponseEntity<StandardApiResponse<?>> deleteResponse = this.deleteService.delete(resourceId, id);
    if (deleteResponse.getStatusCode().equals(HttpStatus.OK)) {
      return this.addService.instantiate(resourceId, id, editedParams,
          MessageFormat.format("{0} has been successfully updated for {1}", resourceId, id), successMessageId);
    } else {
      return deleteResponse;
    }
  }

  /**
   * Executes an UPDATE query on the knowledge graph.
   * 
   * @param query Query for execution.
   */
  public ResponseEntity<StandardApiResponse<?>> update(String query) {
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
