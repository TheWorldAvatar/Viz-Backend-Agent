package com.cmclinnovations.agent.service.application;

import java.util.AbstractMap;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.cmclinnovations.agent.component.LocalisationTranslator;
import com.cmclinnovations.agent.model.SparqlBinding;
import com.cmclinnovations.agent.model.SparqlResponseField;
import com.cmclinnovations.agent.model.type.LifecycleEventType;
import com.cmclinnovations.agent.service.GetService;
import com.cmclinnovations.agent.service.core.DateTimeService;
import com.cmclinnovations.agent.service.core.FileService;
import com.cmclinnovations.agent.utils.LifecycleResource;
import com.cmclinnovations.agent.utils.QueryResource;
import com.cmclinnovations.agent.utils.StringResource;
import com.cmclinnovations.agent.utils.TypeCastUtils;

@Service
public class LifecycleQueryService {
  private final DateTimeService dateTimeService;
  private final GetService getService;
  private final FileService fileService;
  private static final String[] SCHEDULE_VARIABLES = new String[] {
      QueryResource.SCHEDULE_START_DATE_VAR.getVarName(), QueryResource.SCHEDULE_END_DATE_VAR.getVarName(),
      QueryResource.SCHEDULE_START_TIME_VAR.getVarName(), QueryResource.SCHEDULE_END_TIME_VAR.getVarName(),
      QueryResource.SCHEDULE_RECURRENCE_VAR.getVarName()
  };
  private static final Map<String, Set<String>> AD_HOC_SCHEDULE_ARRAY_VARS = new HashMap<>();

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
    AD_HOC_SCHEDULE_ARRAY_VARS.put(QueryResource.AD_HOC_DATE_KEY, Set.of(QueryResource.AD_HOC_DATE_KEY));
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

  /**
   * Generates the query statements required for targeting lifecycle. These
   * statements will be included if they are required via filtering.
   * 
   * @param queryMappings All possible lifecycle statements mapped to a variable.
   * @param sortedFields  Set of fields for sorting that should be included.
   * @param filters       Filters set by the user.
   * @param field         Optional target to include statements if required for
   *                      filtering filters.
   */
  public String genLifecycleStatements(Map<String, String> queryMappings, Set<String> sortedFields,
      Map<String, Set<String>> filters, String field) {
    StringBuilder addStatementBuilder = new StringBuilder();
    // Sorted field statements should also be added
    Map<String, Set<String>> filtersWithSortedFields = new HashMap<>(filters);
    if (!sortedFields.isEmpty()) {
      sortedFields.forEach(sortField -> filtersWithSortedFields.putIfAbsent(sortField, new HashSet<>()));
    }
    queryMappings.forEach((fieldKey, statements) -> {
      // Lifecycle statements itself must be included; When the current field requires
      // to be queried, the statements must also be present
      if (fieldKey.equals(LifecycleResource.LIFECYCLE_RESOURCE) || field.equals(fieldKey)) {
        addStatementBuilder.append(statements);
        // Special filter statement if a date key is required for filtering
      } else if (fieldKey.equals(LifecycleResource.DATE_KEY) && filtersWithSortedFields.containsKey(fieldKey)) {
        QueryResource.genFilterStatements(statements, LifecycleResource.NEW_DATE_KEY,
            filtersWithSortedFields.get(fieldKey),
            addStatementBuilder);
        // Generate filter statements if the filters require them
      } else if (filtersWithSortedFields.containsKey(fieldKey)) {
        QueryResource.genFilterStatements(statements, fieldKey, filtersWithSortedFields.get(fieldKey),
            addStatementBuilder);
      }
    });
    // Include main schedule statements if any schedule variables is present
    if (queryMappings.containsKey(LifecycleResource.LIFECYCLE_RESOURCE)
        && (Arrays.stream(SCHEDULE_VARIABLES).anyMatch(field::equals)
            || Arrays.stream(SCHEDULE_VARIABLES).anyMatch(filtersWithSortedFields.keySet()::contains))) {
      addStatementBuilder.append(queryMappings.getOrDefault(LifecycleResource.SCHEDULE_RESOURCE, ""));
    }
    return addStatementBuilder.toString();
  }

  /**
   * Parses the binding to transform certain fields accordingly.
   * 
   * @param binding binding.
   */
  public Map<String, Object> parseLifecycleBinding(Map<String, Object> binding) {
    return (Map<String, Object>) binding.entrySet().stream()
        .filter(entry -> !entry.getKey()
            .equals(QueryResource.genVariable(LifecycleResource.EVENT_STATUS_KEY).getVarName()))
        .map(entry -> {
          if (entry.getKey().equals(LifecycleResource.SCHEDULE_RECURRENCE_KEY)) {
            SparqlResponseField recurrence = TypeCastUtils.castToObject(entry.getValue(),
                SparqlResponseField.class);
            return new AbstractMap.SimpleEntry<>(
                LifecycleResource.SCHEDULE_TYPE_KEY,
                new SparqlResponseField(recurrence.type(),
                    LocalisationTranslator.getScheduleTypeFromRecurrence(recurrence.value()),
                    recurrence.dataType(), recurrence.lang()));
          }
          if (entry.getKey().equals(LifecycleResource.EVENT_KEY)) {
            SparqlResponseField eventField = TypeCastUtils.castToObject(entry.getValue(),
                SparqlResponseField.class);
            // For any pending completion events, simply reset it to the previous event
            // status as they are incomplete or in a saved state, and should still be
            // outstanding
            String eventType = eventField.value();
            return new AbstractMap.SimpleEntry<>(
                LifecycleResource.STATUS_KEY,
                // Add a new response field
                new SparqlResponseField(eventField.type(),
                    LocalisationTranslator.getEvent(eventType),
                    eventField.dataType(), eventField.lang()));
          }
          return entry;
        })
        .collect(Collectors.toMap(
            Map.Entry::getKey,
            (entry -> entry.getValue() == null ? "" : TypeCastUtils.castToObject(entry.getValue(), Object.class)),
            (oldVal, newVal) -> newVal,
            LinkedHashMap::new));
  }

  
  /**
   * Query for schedule of a contract.
   * 
   * @param contract identifier of contract.
   */
  public SparqlBinding querySchedule(String contract) {
    SparqlBinding result = this.queryRegularSchedule(contract);
    if (result==null) {
      // try query as ad hoc schedule
      Queue<SparqlBinding> results = this.queryAdHocSchedule(contract);
      SparqlBinding temp = results.poll();
      // Iterate over results to get entry dates as an array
      results.stream().forEach(binding -> {
        temp.addFieldArray(binding, AD_HOC_SCHEDULE_ARRAY_VARS);
      });
      result = temp;
    }
    return result;
  }

  /**
   * Query for regular schedule of a contract.
   * 
   * @param contract identifier of contract.
   */
  private SparqlBinding queryRegularSchedule(String contract) {
    return this.getInstance(FileService.CONTRACT_SCHEDULE_QUERY_RESOURCE,
        contract, contract);
  }

  /**
   * Query for ad hoc schedule of a contract.
   * 
   * @param contract identifier of contract.
   */
  private Queue<SparqlBinding> queryAdHocSchedule(String contract) {
    return this.getInstances(FileService.AD_HOC_CONTRACT_SCHEDULE_QUERY_RESOURCE,
        contract, contract);
  }
}