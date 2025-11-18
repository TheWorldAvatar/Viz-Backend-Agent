package com.cmclinnovations.agent.service.application;

import java.util.Map;
import java.util.Queue;

import org.springframework.stereotype.Service;

import com.cmclinnovations.agent.model.SparqlBinding;
import com.cmclinnovations.agent.model.type.LifecycleEventType;
import com.cmclinnovations.agent.service.GetService;
import com.cmclinnovations.agent.service.core.DateTimeService;
import com.cmclinnovations.agent.service.core.FileService;
import com.cmclinnovations.agent.utils.LifecycleResource;
import com.cmclinnovations.agent.utils.QueryResource;
import com.cmclinnovations.agent.utils.StringResource;

@Service
public class LifecycleQueryService {
  private final DateTimeService dateTimeService;
  private final GetService getService;
  private final FileService fileService;

  /**
   * Constructs a new service.
   * 
   * @param dateTimeService Service to handle date and times.
   * @param getService      Service to get values.
   * @param fileService     File service for accessing file resources.
   */
  public LifecycleQueryService(DateTimeService dateTimeService, GetService getService, FileService fileService) {
    this.dateTimeService = dateTimeService;
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
    return this.getInstances(resourceId, replacements).poll();
  }

  /**
   * Get all instances using a default query based on the resource ID.
   * 
   * @param resourceId   The identifier of the query resource.
   * @param replacements Replacements for at least one [target] value.
   */
  public Queue<SparqlBinding> getInstances(String resourceId, String... replacements) {
    String query = this.fileService.getContentsWithReplacement(resourceId, replacements);
    return this.getService.getInstances(query);
  }

  /**
   * Retrieves the SPARQL query to retrieve the event instance associated with the
   * target event type for a specific contract and date.
   * 
   * @param contract  The input contract instance.
   * @param date      Date for filtering.
   * @param eventType The target event type to retrieve.
   */
  public Queue<SparqlBinding> getContractEventQuery(String contract, String date, LifecycleEventType eventType) {
    String dateFilter = "";
    if (date != null) {
      dateFilter = "FILTER(xsd:date(?date)=\"" + date + "\"^^xsd:date)";
    }
    return this.getInstances(FileService.CONTRACT_EVENT_QUERY_RESOURCE, contract,
        eventType.getEvent(), dateFilter);
  }

  /**
   * Populate the remaining occurrence parameters into the request parameters.
   * 
   * @param params    The target parameters to update.
   * @param eventType The target event type to retrieve.
   */
  public void addOccurrenceParams(Map<String, Object> params, LifecycleEventType eventType) {
    String contractId = params.get(LifecycleResource.CONTRACT_KEY).toString();
    String stage = this.getInstance(FileService.CONTRACT_STAGE_QUERY_RESOURCE, contractId,
        eventType.getStage()).getFieldValue(QueryResource.IRI_KEY);
    LifecycleResource.genIdAndInstanceParameters(StringResource.getPrefix(stage), eventType, params);
    params.put(LifecycleResource.STAGE_KEY, stage);

    params.put(LifecycleResource.EVENT_KEY, eventType.getEvent());
    params.putIfAbsent(LifecycleResource.DATE_TIME_KEY, this.dateTimeService.getCurrentDateTime());
    // Update the order enum with the specific event instance if it exist
    params.computeIfPresent(LifecycleResource.ORDER_KEY, (key, value) -> {
      String orderEnum = value.toString();
      return this
          .getContractEventQuery(params.get(LifecycleResource.CONTRACT_KEY).toString(),
              params.get(LifecycleResource.DATE_KEY).toString(),
              LifecycleResource.getEventClassFromOrderEnum(orderEnum))
          .poll()
          .getFieldValue(QueryResource.IRI_KEY);
    });
  }
}