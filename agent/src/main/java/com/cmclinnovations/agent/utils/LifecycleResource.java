package com.cmclinnovations.agent.utils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.rdf4j.sparqlbuilder.core.Variable;
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf;

import com.cmclinnovations.agent.model.type.LifecycleEventType;
import com.cmclinnovations.agent.service.core.FileService;

public class LifecycleResource {
  public static final String LIFECYCLE_RESOURCE = "lifecycle";
  public static final String SCHEDULE_RESOURCE = "schedule";
  public static final String FIXED_DATE_SCHEDULE_RESOURCE = "fixed_date_schedule";
  public static final String TASK_RESOURCE = "task";
  public static final String OCCURRENCE_INSTANT_RESOURCE = "occurrence instant";
  public static final String CANCEL_RESOURCE = "cancel";
  public static final String REPORT_RESOURCE = "report";
  public static final String TASK_ID_SORT_BY_PARAMS = ",+event_id";
  public static final String RECURRENCE_DAILY_TASK = "P1D";
  public static final String RECURRENCE_ALT_DAY_TASK = "P2D";
  public static final String RECURRENCE_FIXED_DATE_TASK = "fixed_date";
  public static final String EMPTY_STRING = "\"\"";
  public static final String RECURRENCE_DAILY_TASK_STRING = "\"" + RECURRENCE_DAILY_TASK + "\"";
  public static final String RECURRENCE_ALT_DAY_TASK_STRING = "\"" + RECURRENCE_ALT_DAY_TASK + "\"";
  public static final String RECURRENCE_FIXED_DATE_TASK_STRING = "\"" + RECURRENCE_FIXED_DATE_TASK + "\"";

  public static final String INSTANCE_KEY = "id_instance";
  public static final String CONTRACT_KEY = "contract";
  public static final String ORDER_KEY = "order";
  public static final String CURRENT_DATE_KEY = "current date";
  public static final String NEW_DATE_KEY = "newDate";
  public static final String CLOSED_DATE_KEY = "closedDate";
  public static final String DATE_KEY = "date";
  public static final String DATE_TIME_KEY = "dateTime";
  public static final String EVENT_KEY = "event";
  public static final String EVENT_ID_KEY = "event id";
  public static final String EVENT_STATUS_KEY = "event status";
  public static final String LAST_MODIFIED_KEY = "lastModified";
  public static final String STAGE_KEY = "stage";
  public static final String STATUS_KEY = "status";
  public static final String REMARKS_KEY = "remarks";
  public static final String TIMESTAMP_KEY = "timestamp";
  public static final String SCHEDULE_DURATION_KEY = "duration";
  public static final String SCHEDULE_DAY_KEY = "scheduleday";
  public static final String SCHEDULE_START_DATE_KEY = "start date";
  public static final String SCHEDULE_END_DATE_KEY = "end date";
  public static final String SCHEDULE_START_TIME_KEY = "start time";
  public static final String SCHEDULE_END_TIME_KEY = "end time";
  public static final String SCHEDULE_RECURRENCE_KEY = "recurrence";
  public static final String SCHEDULE_RECURRENCE_PLACEHOLDER_KEY = "recurrences";
  public static final String SCHEDULE_TYPE_KEY = "scheduleType";
  public static final String RESCHEDULE_DATE_KEY = "reschedule date";

  public static final String EXEMPLIFIES_RELATIONS = "https://spec.edmcouncil.org/fibo/ontology/FND/Relations/Relations/exemplifies";
  public static final String IS_ABOUT_RELATIONS = "https://www.omg.org/spec/Commons/Documents/isAbout";

  public static final String LIFECYCLE_STAGE_PREDICATE_PATH = "<https://spec.edmcouncil.org/fibo/ontology/FND/Arrangements/Lifecycles/hasLifecycle>/<https://spec.edmcouncil.org/fibo/ontology/FND/Arrangements/Lifecycles/hasStage>";
  public static final String LIFECYCLE_EVENT_TYPE_PREDICATE_PATH = "<" + EXEMPLIFIES_RELATIONS + ">";
  public static final String CREATION_STAGE = "https://www.theworldavatar.com/kg/ontoservice/CreationStage";
  public static final String SERVICE_EXECUTION_STAGE = "https://www.theworldavatar.com/kg/ontoservice/ServiceExecutionStage";
  public static final String EXPIRATION_STAGE = "https://www.theworldavatar.com/kg/ontoservice/ExpirationStage";
  public static final String EVENT_APPROVAL = "https://www.theworldavatar.com/kg/ontoservice/ContractApproval";
  public static final String EVENT_ORDER_RECEIVED = "https://www.theworldavatar.com/kg/ontoservice/OrderReceivedEvent";
  public static final String EVENT_DISPATCH = "https://www.theworldavatar.com/kg/ontoservice/ServiceDispatchEvent";
  public static final String EVENT_DELIVERY = "https://www.theworldavatar.com/kg/ontoservice/ServiceDeliveryEvent";
  public static final String EVENT_CANCELLATION = "https://www.theworldavatar.com/kg/ontoservice/TerminatedServiceEvent";
  public static final String EVENT_INCIDENT_REPORT = "https://www.theworldavatar.com/kg/ontoservice/IncidentReportEvent";
  public static final String EVENT_ACCRUAL = "https://www.theworldavatar.com/kg/ontoservice/ServiceAccrualEvent";
  public static final String EVENT_CONTRACT_COMPLETION = "https://www.theworldavatar.com/kg/ontoservice/ContractDischarge";
  public static final String EVENT_CONTRACT_RESCISSION = "https://www.theworldavatar.com/kg/ontoservice/ContractRescission";
  public static final String EVENT_CONTRACT_TERMINATION = "https://www.theworldavatar.com/kg/ontoservice/ContractTermination";
  public static final String EVENT_AMENDED_STATUS = "https://www.theworldavatar.com/kg/ontoservice/AmendedStatus";
  public static final String EVENT_PENDING_STATUS = "https://www.theworldavatar.com/kg/ontoservice/PendingStatus";
  public static final String COMPLETION_EVENT_COMPLETED_STATUS = "https://www.theworldavatar.com/kg/ontoservice/CompletedStatus";
  public static final String LIFECYCLE_REPORT = "https://spec.edmcouncil.org/fibo/ontology/FND/Arrangements/Reporting/Report";
  public static final String EVENT_OCCURRENCE_IRI = "https://spec.edmcouncil.org/fibo/ontology/FBC/ProductsAndServices/FinancialProductsAndServices/ContractLifecycleEventOccurrence";
  public static final Pattern OCCURRENCE_VARIABLES_PATTERN = Pattern.compile("SELECT\\s+DISTINCT\\s+(.*?)\\s+WHERE",
      Pattern.DOTALL);
  public static final Pattern OCCURRENCE_WHERE_CLAUSE_PATTERN = Pattern.compile("WHERE\\s*\\{(.*)\\}$", Pattern.DOTALL);
  public static final Map<String, String> NEGATE_RECURRENCE_MAP;

  // Private constructor to prevent instantiation
  private LifecycleResource() {
    throw new UnsupportedOperationException("This class cannot be instantiated!");
  }

  static {
    Map<String, String> template = new HashMap<>();
    template.put(RECURRENCE_DAILY_TASK_STRING, "?recurrence!=\"P1D\"");
    template.put(RECURRENCE_ALT_DAY_TASK, "?recurrence!=\"P2D\"");
    template.put(EMPTY_STRING, "?recurrence!=\"\"");
    template.put(RECURRENCE_FIXED_DATE_TASK_STRING, "?recurrence!=\"" + RECURRENCE_FIXED_DATE_TASK + "\"");
    NEGATE_RECURRENCE_MAP = Collections.unmodifiableMap(template);
  }

  /**
   * Generates the ID and instance parameters. IDs will not be overwritten.
   * 
   * @param prefix    The prefix for the instance.
   * @param eventType The target event type to support a unique instance IRI
   * @param params    The source and destination of parameter mappings.
   */
  public static void genIdAndInstanceParameters(String prefix, LifecycleEventType eventType,
      Map<String, Object> params) {
    String identifier = params.containsKey(QueryResource.ID_KEY) ? params.get(QueryResource.ID_KEY).toString()
        : UUID.randomUUID().toString();
    params.putIfAbsent(QueryResource.ID_KEY, identifier);
    params.put(LifecycleResource.INSTANCE_KEY,
        prefix + "/" + eventType.getId() + "/" + identifier);
  }

  /**
   * Retrieve the event type associated with the order enum number.
   * 
   * @param orderEnum The target enum number.
   */
  public static LifecycleEventType getEventClassFromOrderEnum(String orderEnum) {
    switch (orderEnum) {
      case "0":
        return LifecycleEventType.SERVICE_ORDER_RECEIVED;
      case "1":
        return LifecycleEventType.SERVICE_ORDER_DISPATCHED;
      default:
        throw new IllegalArgumentException("Invalid order enum number!");
    }
  }

  /**
   * Retrieve the lifecycle resource file path associated with the resource ID.
   * 
   * @param resourceID The identifier for the resource.
   */
  public static String getLifecycleResourceFilePath(String resourceID) {
    switch (resourceID) {
      case LifecycleResource.LIFECYCLE_RESOURCE:
        return FileService.LIFECYCLE_JSON_LD_RESOURCE;
      case BillingResource.CUSTOMER_ACCOUNT_RESOURCE:
        return FileService.CUSTOMER_ACCOUNT_JSON_LD_RESOURCE;
      case BillingResource.CUSTOMER_ACCOUNT_PRICING_RESOURCE:
        return FileService.ACCOUNT_PRICING_JSON_LD_RESOURCE;
      case LifecycleResource.OCCURRENCE_INSTANT_RESOURCE:
        return FileService.OCCURRENCE_INSTANT_JSON_LD_RESOURCE;
      case BillingResource.CONTRACT_PRICING_RESOURCE:
        return FileService.CONTRACT_PRICING_JSON_LD_RESOURCE;
      case LifecycleResource.CANCEL_RESOURCE:
      case LifecycleResource.REPORT_RESOURCE:
        return FileService.OCCURRENCE_LINK_JSON_LD_RESOURCE;
      case LifecycleResource.SCHEDULE_RESOURCE:
        return FileService.SCHEDULE_JSON_LD_RESOURCE;
      case LifecycleResource.FIXED_DATE_SCHEDULE_RESOURCE:
        return FileService.FIXED_DATE_SCHEDULE_JSON_LD_RESOURCE;
      case QueryResource.HISTORY_ACTIVITY_RESOURCE:
        return FileService.HISTORY_ACTIVITY_JSON_LD_RESOURCE;
      case QueryResource.HISTORY_AGENT_RESOURCE:
        return FileService.HISTORY_AGENT_JSON_LD_RESOURCE;
      default:
        return null;
    }
  }

  /**
   * Extract the variables from the query with their sequence order.
   * 
   * @param query      Target query for extraction.
   * @param groupIndex The group index for the variables.
   */
  public static Map<Variable, List<Integer>> extractOccurrenceVariables(String query, int groupIndex) {
    Matcher matcher = OCCURRENCE_VARIABLES_PATTERN.matcher(query);
    if (!matcher.find()) {
      return new HashMap<>();
    }
    String selectClause = matcher.group(1).trim();
    String[] variables = selectClause.split("\\s?\\?");
    Map<Variable, List<Integer>> varSequence = new HashMap<>();
    for (int i = 0; i < variables.length; i++) {
      String varName = variables[i].trim();
      if (varName.isEmpty() || varName.equals(QueryResource.ID_KEY)) {
        continue; // Skip empty variables and ID key
      }
      varSequence.put(QueryResource.genVariable(varName), List.of(groupIndex, i));
    }
    return varSequence;
  }

  /**
   * Converts the current query statement for the target variable into a string
   * that can be compared to the filter value.
   * 
   * @param variable       The variable of interest.
   * @param inputMappings  Mappings to retrieve the current statements.
   * @param outputMappings Mappings to store the updated statements.
   */
  public static void convertVarForStrFilter(Variable variable, Map<String, String> inputMappings,
      Map<String, String> outputMappings) {
    String varName = variable.getVarName();
    String newVarName = StringResource.ORIGINAL_PREFIX + varName;
    outputMappings.put(varName,
        inputMappings.getOrDefault(varName, "").replace(varName, newVarName) + "BIND(STR(?" + newVarName
            + ") AS " + variable.getQueryString() + ")");
  }

  /**
   * Extract the occurrence's WHERE clause for an additional query additions.
   * 
   * @param query          Target query for extraction.
   * @param lifecycleEvent Target event type.
   */
  public static String extractOccurrenceQuery(String query, LifecycleEventType lifecycleEvent) {
    Matcher matcher = OCCURRENCE_WHERE_CLAUSE_PATTERN.matcher(query);
    if (!matcher.find()) {
      return "";
    }
    String eventVar = QueryResource.genVariable(lifecycleEvent.getId() + "_event").getQueryString();
    String parsedWhereClause = matcher.group(1).trim()
        // Remove the following unneeded statements
        // Use of replaceFirst to improve performance as it occurs only once
        .replaceFirst(
            "\\?iri \\<http\\:\\/\\/www\\.w3\\.org\\/1999\\/02\\/22\\-rdf\\-syntax\\-ns\\#type\\> \\<https\\:\\/\\/spec\\.edmcouncil\\.org\\/fibo\\/ontology\\/FBC\\/ProductsAndServices\\/FinancialProductsAndServices\\/ContractLifecycleEventOccurrence\\>\\ .",
            "")
        .replaceFirst("\\?iri dc\\-terms\\:identifier \\?id \\.",
            genOccurrenceTargetQueryStatement(eventVar, lifecycleEvent))
        // Replace iri with event variable
        .replace(QueryResource.IRI_VAR.getQueryString(), eventVar);
    return QueryResource.optional(parsedWhereClause);
  }

  /**
   * Generates a query to target the specific occurrence.
   * 
   * @param eventVar       The event variable of interest.
   * @param lifecycleEvent Target event type.
   */
  public static String genOccurrenceTargetQueryStatement(String eventVar, LifecycleEventType lifecycleEvent) {
    return eventVar + " <https://spec.edmcouncil.org/fibo/ontology/FND/Relations/Relations/exemplifies> "
        + Rdf.iri(lifecycleEvent.getEvent()).getQueryString()
        + ";<https://www.omg.org/spec/Commons/DatesAndTimes/succeeds>* ?order_event.";
  }

  /**
   * Reverts back the special fields for lifecycle back to their original variable
   * form.
   * 
   * @param field      The special field name.
   * @param isContract Indicates if it is a contract or task otherwise.
   */
  public static String revertLifecycleSpecialFields(String field, Boolean isContract) {
    if (isContract == null) {
      return field;
    }
    String lowerCaseField = field.toLowerCase();
    if (lowerCaseField.equals(STATUS_KEY)) {
      return isContract ? STATUS_KEY : EVENT_KEY;
    } else if (lowerCaseField.equals(SCHEDULE_TYPE_KEY.toLowerCase())) {
      return SCHEDULE_RECURRENCE_KEY;
    }
    return field;
  }
}