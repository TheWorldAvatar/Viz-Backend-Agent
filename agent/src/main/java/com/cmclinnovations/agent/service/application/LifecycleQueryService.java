package com.cmclinnovations.agent.service.application;

import org.springframework.stereotype.Service;

import com.cmclinnovations.agent.model.SparqlBinding;
import com.cmclinnovations.agent.model.type.LifecycleEventType;
import com.cmclinnovations.agent.service.GetService;
import com.cmclinnovations.agent.service.core.FileService;

@Service
public class LifecycleQueryService {
  private final GetService getService;
  private final FileService fileService;

  /**
   * Constructs a new service.
   * 
   * @param getService  Service to get values.
   * @param fileService File service for accessing file resources.
   */
  public LifecycleQueryService(GetService getService, FileService fileService) {
    this.getService = getService;
    this.fileService = fileService;
  }

  /**
   * Get one instance value using a default query based on the resource ID.
   * 
   * @param resourceId   The identifier of the query resource.
   * @param replacements Replacements for at least one [target] value.
   */
  public SparqlBinding getInstance(String resourceId, String... replacements) {
    String query = this.fileService.getContentsWithReplacement(resourceId, replacements);
    return this.getService.getInstance(query);
  }

  /**
   * Retrieves the SPARQL query to retrieve the event instance associated with the
   * target event type for a specific contract and date.
   * 
   * @param contract  The input contract instance.
   * @param date      Date for filtering.
   * @param eventType The target event type to retrieve.
   */
  public String getContractEventQuery(String contract, String date, LifecycleEventType eventType) {
    String dateFilter = "";
    if (date != null) {
      dateFilter = "FILTER(xsd:date(?date)=\"" + date + "\"^^xsd:date)";
    }
    return this.fileService.getContentsWithReplacement(FileService.CONTRACT_EVENT_QUERY_RESOURCE, contract,
        eventType.getEvent(), dateFilter);
  }
}