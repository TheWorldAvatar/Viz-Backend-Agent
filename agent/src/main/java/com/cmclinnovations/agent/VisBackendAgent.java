package com.cmclinnovations.agent;

import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

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
import com.cmclinnovations.agent.model.SparqlBinding;
import com.cmclinnovations.agent.model.pagination.PaginationState;
import com.cmclinnovations.agent.model.response.SelectOption;
import com.cmclinnovations.agent.model.response.StandardApiResponse;
import com.cmclinnovations.agent.model.type.TrackActionType;
import com.cmclinnovations.agent.service.AddService;
import com.cmclinnovations.agent.service.DeleteService;
import com.cmclinnovations.agent.service.GetService;
import com.cmclinnovations.agent.service.UpdateService;
import com.cmclinnovations.agent.service.application.GeocodingService;
import com.cmclinnovations.agent.service.core.ConcurrencyService;
import com.cmclinnovations.agent.utils.LocalisationResource;
import com.cmclinnovations.agent.utils.StringResource;

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
   * graph. By default, without a search parameter, this will return 21 instances.
   * Matching search results for instances are given up to 21.
   */
  @GetMapping("/{type}")
  public ResponseEntity<StandardApiResponse<?>> getAllInstances(
      @PathVariable(name = "type") String type, @RequestParam(required = false) String search) {
    LOGGER.info("Received request to get all instances for {}...", type);
    return this.concurrencyService.executeInOptimisticReadLock(type, () -> {
      // This route does not require further restriction on parent instances
      List<SelectOption> options = this.getService.getAllFilterOptions(type, search);
      return this.responseEntityBuilder.success(options);
    });
  }

  /**
   * Retrieves the count of all instances belonging to the specified type in the
   * knowledge graph.
   */
  @GetMapping("/{type}/count")
  public ResponseEntity<StandardApiResponse<?>> getInstancesCount(
      @PathVariable(name = "type") String type,
      @RequestParam Map<String, String> allRequestParams) {
    LOGGER.info("Received request to get all instances for {}...", type);
    return this.concurrencyService.executeInOptimisticReadLock(type, () -> {
      return this.responseEntityBuilder.success(null, String.valueOf(this.getService.getCount(type, allRequestParams)));
    });
  }

  /**
   * Retrieves all instances belonging to the specified type in the knowledge
   * graph, and include human readable labels for all properties.
   */
  @GetMapping("/{type}/label")
  public ResponseEntity<StandardApiResponse<?>> getAllInstancesWithLabel(
      @PathVariable(name = "type") String type,
      @RequestParam Map<String, String> allRequestParams) {
    LOGGER.info("Received request to get all instances with labels for {}...", type);
    Integer page = Integer.valueOf(allRequestParams.remove(StringResource.PAGE_REQUEST_PARAM));
    Integer limit = Integer.valueOf(allRequestParams.remove(StringResource.LIMIT_REQUEST_PARAM));
    String sortBy = allRequestParams.getOrDefault(StringResource.SORT_BY_REQUEST_PARAM, StringResource.DEFAULT_SORT_BY);
    allRequestParams.remove(StringResource.SORT_BY_REQUEST_PARAM);
    return this.concurrencyService.executeInOptimisticReadLock(type, () -> {
      // This route does not require further restriction on parent instances
      Queue<SparqlBinding> instances = this.getService.getInstances(type, true,
          new PaginationState(page, limit, sortBy, allRequestParams));
      return this.responseEntityBuilder.success(null,
          instances.stream()
              .map(SparqlBinding::get)
              .toList());
    });
  }

  /**
   * Retrieves all distinct filter options for the specified type.
   */
  @GetMapping("/{type}/filter")
  public ResponseEntity<StandardApiResponse<?>> getDistinctFilterOptions(
      @PathVariable(name = "type") String type,
      @RequestParam Map<String, String> allRequestParams) {
    LOGGER.info("Received request to get all distinct filter options for {}...", type);
    // Extract non-filter related request parameters directly, and remove them so
    // that the mappings only contain filters
    String field = allRequestParams.remove(StringResource.FIELD_REQUEST_PARAM);
    String search = allRequestParams.getOrDefault(StringResource.SEARCH_REQUEST_PARAM, "");
    allRequestParams.remove(StringResource.SEARCH_REQUEST_PARAM);
    return this.concurrencyService.executeInOptimisticReadLock(type, () -> {
      Map<String, Set<String>> parsedFilters = StringResource.parseFilters(allRequestParams, null);
      parsedFilters.remove(field);
      List<String> options = this.getService.getAllFilterOptionsAsStrings(type, field, "", search, parsedFilters);
      return this.responseEntityBuilder.success(options);
    });
  }

  /**
   * Retrieves all instances belonging to the specified type and associated with a
   * parent in the knowledge graph. Assumes the field name is the same as the
   * parent resource identifier. By default, without a search parameter, this will
   * return 21 instances. Matching search results for instances are returned up to
   * 21.
   */
  @GetMapping("/{parent}/{id}/{type}")
  public ResponseEntity<StandardApiResponse<?>> getAllInstancesWithParent(@PathVariable(name = "parent") String parent,
      @PathVariable(name = "id") String id,
      @PathVariable(name = "type") String type,
      @RequestParam(required = false) String search) {
    LOGGER.info("Received request to get all instances of target {} associated with the parent type {}...", type,
        parent);
    return this.concurrencyService.executeInOptimisticReadLock(type, () -> {
      Map<String, Set<String>> parentFilter = this.getService.getParentFilter(parent, id);
      List<SelectOption> options = this.getService.getAllFilterOptions(type, search, parentFilter);
      return this.responseEntityBuilder.success(options);
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
   * Retrieve the changes associated with the target instance of the specified
   * type in the knowledge graph.
   */
  @GetMapping("/changes/{type}/{id}")
  public ResponseEntity<StandardApiResponse<?>> getChangelog(@PathVariable String type, @PathVariable String id) {
    LOGGER.info("Received request to get the changelog for the instance of type {}...", type);
    return this.concurrencyService.executeInOptimisticReadLock(type, () -> {
      return this.getService.getChanges(type, id);
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
    return this.concurrencyService.executeInWriteLock(type, () -> {
      return this.addService.instantiate(type, instance, TrackActionType.CREATION);
    });
  }

  /**
   * Removes the specified instance from the knowledge graph.
   * 
   * @param type         The resource type
   * @param id           The identifier of the instance to delete
   * @param branchDelete Optional branch name to filter deletion
   */

  @DeleteMapping("/{type}/{id}")
  public ResponseEntity<StandardApiResponse<?>> removeEntity(@PathVariable String type, @PathVariable String id,
      @RequestParam(name = "branch_delete", required = false) String branchDelete) {
    LOGGER.info("Received request to delete {}...", type);
    return this.concurrencyService.executeInWriteLock(type, () -> {
      return this.deleteService.delete(type, id, branchDelete);
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
      return this.updateService.update(id, type, LocalisationResource.SUCCESS_UPDATE_KEY, updatedEntity,
          TrackActionType.MODIFICATION);
    });
  }
}