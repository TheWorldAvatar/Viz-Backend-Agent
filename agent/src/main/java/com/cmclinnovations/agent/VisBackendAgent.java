package com.cmclinnovations.agent;

import java.util.HashMap;
import java.util.Map;
import java.util.Queue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cmclinnovations.agent.model.ParentField;
import com.cmclinnovations.agent.model.SparqlBinding;
import com.cmclinnovations.agent.model.response.ApiResponse;
import com.cmclinnovations.agent.service.AddService;
import com.cmclinnovations.agent.service.DeleteService;
import com.cmclinnovations.agent.service.GetService;
import com.cmclinnovations.agent.service.application.GeocodingService;

@RestController
public class VisBackendAgent {
  private final AddService addService;
  private final DeleteService deleteService;
  private final GetService getService;
  private final GeocodingService geocodingService;
  private static final Logger LOGGER = LogManager.getLogger(VisBackendAgent.class);

  public VisBackendAgent(AddService addService, DeleteService deleteService, GetService getService,
      GeocodingService geocodingService) {
    this.addService = addService;
    this.deleteService = deleteService;
    this.getService = getService;
    this.geocodingService = geocodingService;
  }

  @GetMapping("/status")
  public ResponseEntity<String> getStatus() {
    LOGGER.info("Detected request to get agent status...");
    return new ResponseEntity<>(
        "Agent is ready to receive requests.",
        HttpStatus.OK);
  }

  @GetMapping("/location")
  public ResponseEntity<?> getCoordinates(
      @RequestParam(required = true) String iri) {
    LOGGER.info("Received request to retrieve coordinates for {}...", iri);
    return this.geocodingService.getCoordinates(iri);
  }

  @GetMapping("/location/geocode")
  public ResponseEntity<?> getGeoCoordinates(
      @RequestParam(required = false) String block,
      @RequestParam(required = false) String street,
      @RequestParam(required = false) String city,
      @RequestParam(required = false) String country,
      @RequestParam(required = false) String postal_code) {
    LOGGER.info("Received geocoding request...");
    if (block != null && street == null) {
      String errorMsg = "Invalid geocoding parameters! Detected a block number but no street is provided!";
      LOGGER.error(errorMsg);
      return new ResponseEntity<>(errorMsg, HttpStatus.BAD_REQUEST);
    }

    return this.geocodingService.getCoordinates(block, street, city, country, postal_code);
  }

  @GetMapping("/location/addresses")
  public ResponseEntity<?> getAddress(@RequestParam(required = true) String postal_code) {
    LOGGER.info("Received request to search for address...");
    return this.geocodingService.getAddress(postal_code);
  }

  /**
   * Retrieves all instances belonging to the specified type in the knowledge
   * graph.
   */
  @GetMapping("/{type}")
  public ResponseEntity<?> getAllInstances(
      @PathVariable(name = "type") String type) {
    LOGGER.info("Received request to get all instances for {}...", type);
    // This route does not require further restriction on parent instances
    Queue<SparqlBinding> instances = this.getService.getInstances(type, null, "", "", false, new HashMap<>());
    return new ResponseEntity<>(
        instances.stream()
            .map(SparqlBinding::get)
            .toList(),
        HttpStatus.OK);
  }

  /**
   * Retrieves all instances belonging to the specified type in the knowledge
   * graph, and include human readable labels for all properties.
   */
  @GetMapping("/{type}/label")
  public ResponseEntity<?> getAllInstancesWithLabel(
      @PathVariable(name = "type") String type) {
    LOGGER.info("Received request to get all instances with labels for {}...", type);
    // This route does not require further restriction on parent instances
    Queue<SparqlBinding> instances = this.getService.getInstances(type, null, "", "", true, new HashMap<>());
    return new ResponseEntity<>(
        instances.stream()
            .map(SparqlBinding::get)
            .toList(),
        HttpStatus.OK);
  }

  /**
   * Retrieves all instances belonging to the specified type and associated with a
   * parent in the knowledge graph. Assumes the field name is the same as the
   * parent resource identifier.
   */
  @GetMapping("/{parent}/{id}/{type}")
  public ResponseEntity<?> getAllInstancesWithParent(@PathVariable(name = "parent") String parent,
      @PathVariable(name = "id") String id,
      @PathVariable(name = "type") String type) {
    LOGGER.info("Received request to get all instances of target {} associated with the parent type {}...", type,
        parent);
    Queue<SparqlBinding> instances = this.getService.getInstances(type, new ParentField(id, parent), "", "", false,
        new HashMap<>());
    return new ResponseEntity<>(
        instances.stream()
            .map(SparqlBinding::get)
            .toList(),
        HttpStatus.OK);
  }

  /**
   * Retrieve the target instance of the specified type in the knowledge graph.
   */
  @GetMapping("/{type}/{id}")
  public ResponseEntity<?> getInstance(@PathVariable String type, @PathVariable String id) {
    LOGGER.info("Received request to get a specific instance of {}...", type);
    return this.getService.getInstance(id, type, false);
  }

  /**
   * Retrieve the target instance of the specified type in the knowledge graph
   * with human readable properties.
   */
  @GetMapping("/{type}/label/{id}")
  public ResponseEntity<?> getInstanceWithLabels(@PathVariable String type, @PathVariable String id) {
    LOGGER.info("Received request to get a specific instance of {} with human readable data...", type);
    return this.getService.getInstance(id, type, true);
  }

  /**
   * Retrieve the instances that matches the search criterias.
   */
  @PostMapping("/{type}/search")
  public ResponseEntity<?> getMatchingInstances(@PathVariable String type, @RequestBody Map<String, String> criterias) {
    LOGGER.info("Received request to get matching instances of {}...", type);
    return this.getService.getMatchingInstances(type, criterias);
  }

  /**
   * Retrieves all instances belonging to the specified type in the knowledge
   * graph in the csv format.
   */
  @GetMapping("/csv/{type}")
  public ResponseEntity<String> getAllInstancesInCSV(@PathVariable(name = "type") String type) {
    LOGGER.info("Received request to get all instances of {} type in the CSV format...", type);
    return this.getService.getInstancesInCSV(type);
  }

  /**
   * Retrieves the form template for the specified type from the knowledge graph.
   */
  @GetMapping("/form/{type}")
  public ResponseEntity<Map<String, Object>> getFormTemplate(@PathVariable(name = "type") String type) {
    LOGGER.info("Received request to get the form template for {}...", type);
    return this.getService.getForm(type, false, new HashMap<>());
  }

  /**
   * Retrieves the form template for the target entity of the specified type from
   * the knowledge graph.
   */
  @GetMapping("/form/{type}/{id}")
  public ResponseEntity<Map<String, Object>> retrieveFormTemplate(@PathVariable String type, @PathVariable String id) {
    LOGGER.info("Received request to get specific form template for {} ...", type);
    Map<String, Object> currentEntity = new HashMap<>();
    ResponseEntity<?> currentEntityResponse = this.getService.getInstance(id, type, false);
    if (currentEntityResponse.getStatusCode() == HttpStatus.OK) {
      currentEntity = (Map<String, Object>) currentEntityResponse.getBody();
    }
    return this.getService.getForm(type, false, currentEntity);
  }

  /**
   * Retrieve the metadata (IRI, label, and description) of the concept associated
   * with the specified type in the knowledge graph.
   */
  @GetMapping("/type")
  public ResponseEntity<?> getConceptMetadata(@RequestParam(name = "uri") String uri) {
    LOGGER.info("Received request to get the metadata for the concept: {}...", uri);
    return this.getService.getConceptMetadata(uri);
  }

  /**
   * Instantiates a new instance in the knowledge graph.
   */
  @PostMapping("/{type}")
  public ResponseEntity<ApiResponse> addInstance(@PathVariable String type,
      @RequestBody Map<String, Object> instance) {
    LOGGER.info("Received request to add one {}...", type);
    return this.addService.instantiate(type, instance);
  }

  /**
   * Removes the specified instance from the knowledge graph.
   */
  @DeleteMapping("/{type}/{id}")
  public ResponseEntity<ApiResponse> removeEntity(@PathVariable String type, @PathVariable String id) {
    LOGGER.info("Received request to delete {}...", type);
    return this.deleteService.delete(type, id);
  }

  /**
   * Update the target instance in the knowledge graph.
   */
  @PutMapping("/{type}/{id}")
  public ResponseEntity<ApiResponse> updateEntity(@PathVariable String type, @PathVariable String id,
      @RequestBody Map<String, Object> updatedEntity) {
    LOGGER.info("Received request to update {}...", type);
    ResponseEntity<ApiResponse> deleteResponse = this.deleteService.delete(type, id);
    if (deleteResponse.getStatusCode().equals(HttpStatus.OK)) {
      ResponseEntity<ApiResponse> addResponse = this.addService.instantiate(type, id, updatedEntity);
      if (addResponse.getStatusCode() == HttpStatus.CREATED) {
        LOGGER.info("{} has been successfully updated for {}", type, id);
        return new ResponseEntity<>(
            new ApiResponse(type + " has been successfully updated for " + id,
                addResponse.getBody().getIri()),
            HttpStatus.CREATED);
      } else {
        return addResponse;
      }
    } else {
      return deleteResponse;
    }
  }
}