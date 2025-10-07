package com.cmclinnovations.agent;

import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cmclinnovations.agent.component.LocalisationTranslator;
import com.cmclinnovations.agent.component.ResponseEntityBuilder;
import com.cmclinnovations.agent.model.ParentField;
import com.cmclinnovations.agent.model.SparqlBinding;
import com.cmclinnovations.agent.model.response.StandardApiResponse;
import com.cmclinnovations.agent.service.AddService;
import com.cmclinnovations.agent.service.DeleteService;
import com.cmclinnovations.agent.service.GetService;
import com.cmclinnovations.agent.service.UpdateService;
import com.cmclinnovations.agent.service.application.GeocodingService;
import com.cmclinnovations.agent.service.core.ConcurrencyService;
import com.cmclinnovations.agent.utils.LocalisationResource;
import com.cmclinnovations.agent.utils.QueryResource;

@RestController
public class VisBackendAgent {
  private final ConcurrencyService concurrencyService;
  private final AddService addService;
  private final DeleteService deleteService;
  private final GetService getService;
  private final GeocodingService geocodingService;
  private final UpdateService updateService;
  private final ResponseEntityBuilder responseEntityBuilder;

  private static final Logger LOGGER = LogManager.getLogger(VisBackendAgent.class);

  public VisBackendAgent(ConcurrencyService concurrencyService, AddService addService, DeleteService deleteService,
      GetService getService,
      GeocodingService geocodingService, UpdateService updateService, ResponseEntityBuilder responseEntityBuilder) {
    this.concurrencyService = concurrencyService;
    this.addService = addService;
    this.deleteService = deleteService;
    this.getService = getService;
    this.geocodingService = geocodingService;
    this.updateService = updateService;
    this.responseEntityBuilder = responseEntityBuilder;
  }

  @GetMapping("/status")
  public ResponseEntity<StandardApiResponse<?>> getStatus() {
    LOGGER.info("Detected request to get agent status...");
    return this.responseEntityBuilder.success(null, LocalisationTranslator.getMessage(LocalisationResource.STATUS_KEY));
  }

  @GetMapping("/location")
  public ResponseEntity<StandardApiResponse<?>> getCoordinates(
      @RequestParam(required = true) String iri) {
    LOGGER.info("Received request to retrieve coordinates for {}...", iri);
    return this.geocodingService.getCoordinates(iri);
  }

  @GetMapping("/location/geocode")
  public ResponseEntity<StandardApiResponse<?>> getGeoCoordinates(
      @RequestParam(required = false) String block,
      @RequestParam(required = false) String street,
      @RequestParam(required = false) String city,
      @RequestParam(required = false) String country,
      @RequestParam(required = false) String postal_code) {
    LOGGER.info("Received geocoding request...");
    if (block != null && street == null) {
      throw new IllegalArgumentException(
          LocalisationTranslator.getMessage(LocalisationResource.ERROR_INVALID_GEOCODE_PARAMS_KEY));
    }

    return this.geocodingService.getCoordinates(block, street, city, country, postal_code);
  }

  @GetMapping("/location/addresses")
  public ResponseEntity<StandardApiResponse<?>> getAddress(@RequestParam(required = true) String postal_code) {
    LOGGER.info("Received request to search for address...");
    return this.geocodingService.getAddress(postal_code);
  }

  /**
   * Retrieves all instances belonging to the specified type in the knowledge
   * graph.
   */
  @GetMapping("/{type}")
  public ResponseEntity<StandardApiResponse<?>> getAllInstances(
      @PathVariable(name = "type") String type) {
    LOGGER.info("Received request to get all instances for {}...", type);
    return this.concurrencyService.executeInOptimisticReadLock(type, () -> {
      // This route does not require further restriction on parent instances
      Queue<SparqlBinding> instances = this.getService.getInstances(type, null, "", "", false, new HashMap<>());
      return this.responseEntityBuilder.success(null,
          instances.stream()
              .map(SparqlBinding::get)
              .toList());
    });
  }

  /**
   * Retrieves all instances belonging to the specified type in the knowledge
   * graph, and include human readable labels for all properties.
   */
  @GetMapping("/{type}/label")
  public ResponseEntity<StandardApiResponse<?>> getAllInstancesWithLabel(
      @PathVariable(name = "type") String type) {
    LOGGER.info("Received request to get all instances with labels for {}...", type);
    return this.concurrencyService.executeInOptimisticReadLock(type, () -> {
      // This route does not require further restriction on parent instances
      Queue<SparqlBinding> instances = this.getService.getInstances(type, null, "", "", true, new HashMap<>());
      return this.responseEntityBuilder.success(null,
          instances.stream()
              .map(SparqlBinding::get)
              .toList());
    });
  }

  /**
   * Retrieves all instances belonging to the specified type and associated with a
   * parent in the knowledge graph. Assumes the field name is the same as the
   * parent resource identifier.
   */
  @GetMapping("/{parent}/{id}/{type}")
  public ResponseEntity<StandardApiResponse<?>> getAllInstancesWithParent(@PathVariable(name = "parent") String parent,
      @PathVariable(name = "id") String id,
      @PathVariable(name = "type") String type) {
    LOGGER.info("Received request to get all instances of target {} associated with the parent type {}...", type,
        parent);
    return this.concurrencyService.executeInOptimisticReadLock(type, () -> {
      Queue<SparqlBinding> instances = this.getService.getInstances(type, new ParentField(id, parent), "", "", false,
          new HashMap<>());
      return this.responseEntityBuilder.success(null,
          instances.stream()
              .map(SparqlBinding::get)
              .toList());
    });
  }

  /**
   * Retrieve the target instance of the specified type in the knowledge graph.
   */
  @GetMapping("/{type}/{id}")
  public ResponseEntity<StandardApiResponse<?>> getInstance(@PathVariable String type, @PathVariable String id) {
    LOGGER.info("Received request to get a specific instance of {}...", type);
    return this.concurrencyService.executeInOptimisticReadLock(type, () -> {
      return this.getService.getInstance(id, type, false);
    });
  }

  /**
   * Retrieve the target instance of the specified type in the knowledge graph
   * with human readable properties.
   */
  @GetMapping("/{type}/label/{id}")
  public ResponseEntity<StandardApiResponse<?>> getInstanceWithLabels(@PathVariable String type,
      @PathVariable String id) {
    LOGGER.info("Received request to get a specific instance of {} with human readable data...", type);
    return this.concurrencyService.executeInOptimisticReadLock(type, () -> {
      return this.getService.getInstance(id, type, true);
    });
  }

  /**
   * Retrieve the instances that matches the search criterias.
   */
  @PostMapping("/{type}/search")
  public ResponseEntity<StandardApiResponse<?>> getMatchingInstances(@PathVariable String type,
      @RequestBody Map<String, String> criterias) {
    LOGGER.info("Received request to get matching instances of {}...", type);
    return this.concurrencyService.executeInOptimisticReadLock(type, () -> {
      return this.getService.getMatchingInstances(type, criterias);
    });
  }

  /**
   * Retrieves the form template for the specified type from the knowledge graph.
   */
  @GetMapping("/form/{type}")
  public ResponseEntity<StandardApiResponse<?>> getFormTemplate(@PathVariable(name = "type") String type) {
    LOGGER.info("Received request to get the form template for {}...", type);
    return this.concurrencyService.executeInOptimisticReadLock(type, () -> {
      // Access to this empty form is prefiltered on the UI and need not be enforced
      return this.getService.getForm(type, false);
    });
  }

  /**
   * Retrieves the form template for the target entity of the specified type from
   * the knowledge graph.
   */
  @GetMapping("/form/{type}/{id}")
  public ResponseEntity<StandardApiResponse<?>> retrieveFormTemplate(@PathVariable String type,
      @PathVariable String id) {
    LOGGER.info("Received request to get specific form template for {} ...", type);
    return this.concurrencyService.executeInOptimisticReadLock(type, () -> {
      return this.getService.getForm(id, type, false, null);
    });
  }

  /**
   * Retrieve the metadata (IRI, label, and description) of the concept associated
   * with the specified type in the knowledge graph.
   */
  @GetMapping("/type")
  public ResponseEntity<StandardApiResponse<?>> getConceptMetadata(@RequestParam(name = "uri") String uri) {
    LOGGER.info("Received request to get the metadata for the concept: {}...", uri);
    return this.concurrencyService.executeInOptimisticReadLock(uri, () -> {
      return this.getService.getConceptMetadata(uri);
    });
  }

  /**
   * Instantiates a new instance in the knowledge graph.
   */
  @PostMapping("/{type}")
  public ResponseEntity<StandardApiResponse<?>> addInstance(@PathVariable String type,
      @RequestBody Map<String, Object> instance) {
    LOGGER.info("Received request to add one {}...", type);
    String id = instance.getOrDefault(QueryResource.ID_KEY, UUID.randomUUID()).toString();
    return this.concurrencyService.executeInWriteLock(type, () -> {
      return this.addService.instantiate(type, instance);
    });
  }

  /**
   * Removes the specified instance from the knowledge graph.
   */
  @DeleteMapping("/{type}/{id}")
  public ResponseEntity<StandardApiResponse<?>> removeEntity(@PathVariable String type, @PathVariable String id) {
    LOGGER.info("Received request to delete {}...", type);
    return this.concurrencyService.executeInWriteLock(type, () -> {
      return this.deleteService.delete(type, id);
    });
  }

  /**
   * Update the target instance in the knowledge graph.
   */
  @PutMapping("/{type}/{id}")
  public ResponseEntity<StandardApiResponse<?>> updateEntity(@PathVariable String type, @PathVariable String id,
      @RequestBody Map<String, Object> updatedEntity) {
    LOGGER.info("Received request to update {}...", type);
    return this.concurrencyService.executeInWriteLock(type, () -> {
      return this.updateService.update(id, type, LocalisationResource.SUCCESS_UPDATE_KEY, updatedEntity);
    });
  }
}