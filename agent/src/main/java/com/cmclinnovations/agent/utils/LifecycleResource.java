package com.cmclinnovations.agent.utils;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.cmclinnovations.agent.model.type.CalculationType;
import com.cmclinnovations.agent.model.type.LifecycleEventType;
import com.cmclinnovations.agent.service.core.FileService;

public class LifecycleResource {
  public static final String LIFECYCLE_RESOURCE = "lifecycle";
  public static final String SCHEDULE_RESOURCE = "schedule";
  public static final String OCCURRENCE_INSTANT_RESOURCE = "occurrence instant";
  public static final String OCCURRENCE_LINK_RESOURCE = "occurrence link";

  public static final String IRI_KEY = "iri";
  public static final String CONTRACT_KEY = "contract";
  public static final String ORDER_KEY = "order";
  public static final String CURRENT_DATE_KEY = "current date";
  public static final String DATE_KEY = "date";
  public static final String DATE_TIME_KEY = "dateTime";
  public static final String EVENT_KEY = "event";
  public static final String EVENT_ID_KEY = "event id";
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
  public static final String SCHEDULE_TYPE_KEY = "schedule type";

  public static final String EXEMPLIFIES_RELATIONS = "https://spec.edmcouncil.org/fibo/ontology/FND/Relations/Relations/exemplifies";
  public static final String HAS_AMOUNT_RELATIONS = "https://spec.edmcouncil.org/fibo/ontology/FND/Accounting/CurrencyAmount/hasAmount";
  public static final String HAS_LOWER_BOUND_RELATIONS = "https://www.omg.org/spec/Commons/QuantitiesAndUnits/hasLowerBound";
  public static final String HAS_UPPER_BOUND_RELATIONS = "https://www.omg.org/spec/Commons/QuantitiesAndUnits/hasUpperBound";
  public static final String HAS_ARGUMENT_RELATIONS = "https://www.omg.org/spec/Commons/QuantitiesAndUnits/hasArgument";
  public static final String HAS_MINUEND_RELATIONS = "https://spec.edmcouncil.org/fibo/ontology/FND/Utilities/Analytics/hasMinuend";
  public static final String HAS_SUBTRAHEND_RELATIONS = "https://spec.edmcouncil.org/fibo/ontology/FND/Utilities/Analytics/hasSubtrahend";
  public static final String HAS_QTY_VAL_RELATIONS = "https://www.omg.org/spec/Commons/QuantitiesAndUnits/hasQuantityValue";
  public static final String IS_ABOUT_RELATIONS = "https://www.omg.org/spec/Commons/Documents/isAbout";
  public static final String REPORTS_ON_RELATIONS = "https://spec.edmcouncil.org/fibo/ontology/FND/Arrangements/Reporting/reportsOn";
  public static final String RECORDS_RELATIONS = "https://www.omg.org/spec/Commons/Documents/records";
  public static final String SUCCEEDS_RELATIONS = "https://www.omg.org/spec/Commons/DatesAndTimes/succeeds";

  public static final String LIFECYCLE_STAGE_PREDICATE_PATH = "<https://spec.edmcouncil.org/fibo/ontology/FND/Arrangements/Lifecycles/hasLifecycle>/<https://spec.edmcouncil.org/fibo/ontology/FND/Arrangements/Lifecycles/hasStage>";
  public static final String LIFECYCLE_STAGE_EVENT_PREDICATE_PATH = "<https://www.omg.org/spec/Commons/Collections/comprises>";
  public static final String LIFECYCLE_EVENT_TYPE_PREDICATE_PATH = "<" + EXEMPLIFIES_RELATIONS + ">";
  public static final String LIFECYCLE_EVENT_PREDICATE_PATH = LIFECYCLE_STAGE_PREDICATE_PATH + "/"
      + LIFECYCLE_STAGE_EVENT_PREDICATE_PATH + "/" + LIFECYCLE_EVENT_TYPE_PREDICATE_PATH;
  public static final String CREATION_STAGE = "https://www.theworldavatar.com/kg/ontoservice/CreationStage";
  public static final String SERVICE_EXECUTION_STAGE = "https://www.theworldavatar.com/kg/ontoservice/ServiceExecutionStage";
  public static final String EXPIRATION_STAGE = "https://www.theworldavatar.com/kg/ontoservice/ExpirationStage";
  public static final String EVENT_APPROVAL = "https://www.theworldavatar.com/kg/ontoservice/ContractApproval";
  public static final String EVENT_ORDER_RECEIVED = "https://www.theworldavatar.com/kg/ontoservice/OrderReceivedEvent";
  public static final String EVENT_DISPATCH = "https://www.theworldavatar.com/kg/ontoservice/ServiceDispatchEvent";
  public static final String EVENT_DELIVERY = "https://www.theworldavatar.com/kg/ontoservice/ServiceDeliveryEvent";
  public static final String EVENT_CANCELLATION = "https://www.theworldavatar.com/kg/ontoservice/TerminatedServiceEvent";
  public static final String EVENT_INCIDENT_REPORT = "https://www.theworldavatar.com/kg/ontoservice/IncidentReportEvent";
  public static final String EVENT_CONTRACT_COMPLETION = "https://www.theworldavatar.com/kg/ontoservice/ContractDischarge";
  public static final String EVENT_CONTRACT_RESCISSION = "https://www.theworldavatar.com/kg/ontoservice/ContractRescission";
  public static final String EVENT_CONTRACT_TERMINATION = "https://www.theworldavatar.com/kg/ontoservice/ContractTermination";
  public static final String LIFECYCLE_RECORD = "https://www.omg.org/spec/Commons/Documents/Record";
  public static final String LIFECYCLE_REPORT = "https://spec.edmcouncil.org/fibo/ontology/FND/Arrangements/Reporting/Report";
  public static final String PAYMENT_OBLIGATION = "https://spec.edmcouncil.org/fibo/ontology/FND/ProductsAndServices/PaymentsAndSchedules/PaymentObligation";

  // Private constructor to prevent instantiation
  private LifecycleResource() {
    throw new UnsupportedOperationException("This class cannot be instantiated!");
  }

  /**
   * Check if the date input is either before and after the current date.
   * 
   * @param dateParam   The date parameter for checking.
   * @param checkBefore Indicator if the method should check if the date is before
   *                    the current date. Use false to check if date is after the
   *                    current date.
   */
  public static boolean checkDate(String dateParam, boolean checkBefore) {
    // Parse input date
    LocalDate inputDate = LocalDate.parse(dateParam);
    LocalDate currentDate = LocalDate.now();

    if (checkBefore) {
      return inputDate.isBefore(currentDate); // Check if the date is before today
    } else {
      return inputDate.isAfter(currentDate); // Check if the date is after today
    }
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
   * Retrieve the priority level of the specified event.
   * 
   * @param event Target event.
   */
  public static int getEventPriority(String event) {
    switch (event) {
      case EVENT_INCIDENT_REPORT:
        return 4;
      case EVENT_CANCELLATION:
        return 3;
      case EVENT_DELIVERY:
        return 2;
      case EVENT_DISPATCH:
        return 1;
      case EVENT_ORDER_RECEIVED:
        return 0;
      default:
        return -1;
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
      case LifecycleResource.OCCURRENCE_INSTANT_RESOURCE:
        return FileService.OCCURRENCE_INSTANT_JSON_LD_RESOURCE;
      case LifecycleResource.OCCURRENCE_LINK_RESOURCE:
        return FileService.OCCURRENCE_LINK_JSON_LD_RESOURCE;
      case LifecycleResource.SCHEDULE_RESOURCE:
        return FileService.SCHEDULE_JSON_LD_RESOURCE;
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
  public static Map<String, List<Integer>> extractOccurrenceVariables(String query, int groupIndex) {
    Pattern pattern = Pattern.compile("SELECT\\s+DISTINCT\\s+(.*?)\\s+WHERE", Pattern.DOTALL);
    Matcher matcher = pattern.matcher(query);

    if (!matcher.find()) {
      return new HashMap<>();
    }
    String selectClause = matcher.group(1).trim();
    String[] variables = selectClause.split("\\s?\\?");
    Map<String, List<Integer>> varSequence = new HashMap<>();
    for (int i = 0; i < variables.length; i++) {
      String varName = variables[i].trim();
      if (varName.isEmpty() || varName.equals("id")) {
        continue; // Skip empty variables and ID key
      }
      varSequence.put(varName, List.of(groupIndex, i));
    }
    return varSequence;
  }

  /**
   * Extract the occurrence's WHERE clause for an additional query additions.
   * 
   * @param query          Target query for extraction.
   * @param lifecycleEvent Target event type.
   */
  public static String extractOccurrenceQuery(String query, LifecycleEventType lifecycleEvent) {
    Pattern pattern = Pattern.compile("WHERE\\s*\\{(.*?)\\}$", Pattern.DOTALL);
    Matcher matcher = pattern.matcher(query);

    if (!matcher.find()) {
      return "";
    }
    String eventVar = ShaclResource.VARIABLE_MARK + lifecycleEvent.getId() + "_event";
    String parsedWhereClause = matcher.group(1)
        .trim()
        // Remove the following unneeded statements
        // Use of replaceFirst to improve performance as it occurs only once
        .replaceFirst(
            "\\?iri a\\/rdfs\\:subClassOf\\* \\<https\\:\\/\\/spec\\.edmcouncil\\.org\\/fibo\\/ontology\\/FBC\\/ProductsAndServices\\/FinancialProductsAndServices\\/ContractLifecycleEventOccurrence\\>\\.",
            "")
        .replaceFirst("BIND\\(\\?iri AS \\?id\\)", "")
        // Replace iri with event variable
        .replace(ShaclResource.VARIABLE_MARK + IRI_KEY, eventVar);
    return StringResource.genOptionalClause(
        eventVar + " <https://spec.edmcouncil.org/fibo/ontology/FND/Relations/Relations/exemplifies> "
            + StringResource.parseIriForQuery(lifecycleEvent.getEvent())
            + ";<https://www.omg.org/spec/Commons/DatesAndTimes/succeeds>* ?order_event." + parsedWhereClause);
  }
}