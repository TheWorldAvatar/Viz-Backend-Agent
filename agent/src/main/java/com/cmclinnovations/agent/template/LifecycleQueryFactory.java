package com.cmclinnovations.agent.template;

import com.cmclinnovations.agent.model.type.LifecycleEventType;
import com.cmclinnovations.agent.utils.LifecycleResource;
import com.cmclinnovations.agent.utils.ShaclResource;
import com.cmclinnovations.agent.utils.StringResource;

public class LifecycleQueryFactory {

  /**
   * Constructs a new query factory.
   */
  public LifecycleQueryFactory() {
    // No set up required
  }

  /**
   * Retrieves the SPARQL query to get the current status of the contract.
   * 
   * @param contractId the target contract id.
   */
  public String getServiceStatusQuery(String contractId) {
    return StringResource.QUERY_TEMPLATE_PREFIX
        + "SELECT DISTINCT ?iri ?status WHERE{"
        + "{SELECT DISTINCT ?iri (MAX(?priority_val) AS ?priority) WHERE{"
        + "?iri fibo-fnd-arr-lif:hasLifecycle/fibo-fnd-arr-lif:hasStage/<https://www.omg.org/spec/Commons/Collections/comprises> ?event."
        + "?event " + LifecycleResource.LIFECYCLE_EVENT_TYPE_PREDICATE_PATH + " ?event_type."
        + "BIND(IF(?event_type=ontoservice:ContractDischarge||?event_type=ontoservice:ContractRescission||?event_type=ontoservice:ContractTermination,"
        + "2,IF(?event_type=ontoservice:ContractApproval,1,0)"
        + ") AS ?priority_val)"
        + "FILTER STRENDS(STR(?iri),\"" + contractId + "\")"
        + "}"
        + "GROUP BY ?iri}"
        + "BIND(IF(?priority=2,\"Archived\","
        + "IF(?priority=1,\"Active\",\"Pending\")"
        + ") AS ?status)"
        + "}";
  }

  /**
   * Retrieves the SPARQL query template for human readable schedule details.
   */
  public String getReadableScheduleQuery() {
    return this.getScheduleTemplate()
        + "BIND(IF(?" + LifecycleResource.SCHEDULE_RECURRENCE_KEY + "=\"P1D\",\"Single Service\","
        + "IF(?" + LifecycleResource.SCHEDULE_RECURRENCE_KEY + "=\"P2D\",\"Alternate Day Service\", "
        + "\"Regular Service\")" // Close IF statement
        + ") AS ?" + StringResource.parseQueryVariable(LifecycleResource.SCHEDULE_TYPE_KEY) + ")";
  }

  /**
   * Retrieves the SPARQL query to get the schedule of the contract.
   * 
   * @param contractId the target contract id.
   */
  public String getServiceScheduleQuery(String contractId) {
    return StringResource.QUERY_TEMPLATE_PREFIX
        + "SELECT DISTINCT * WHERE{"
        + this.getScheduleTemplate()
        // Nested query for all days
        + "{SELECT ?iri "
        + "(MAX(IF(?day=fibo-fnd-dt-fd:Monday,\"Monday\",\"\")) AS ?monday) "
        + "(MAX(IF(?day=fibo-fnd-dt-fd:Tuesday,\"Tuesday\",\"\")) AS ?tuesday) "
        + "(MAX(IF(?day=fibo-fnd-dt-fd:Wednesday,\"Wednesday\",\"\")) AS ?wednesday) "
        + "(MAX(IF(?day=fibo-fnd-dt-fd:Thursday,\"Thursday\",\"\")) AS ?thursday) "
        + "(MAX(IF(?day=fibo-fnd-dt-fd:Friday,\"Friday\",\"\")) AS ?friday) "
        + "(MAX(IF(?day=fibo-fnd-dt-fd:Saturday,\"Saturday\",\"\")) AS ?saturday) "
        + "(MAX(IF(?day=fibo-fnd-dt-fd:Sunday,\"Sunday\",\"\")) AS ?sunday) "
        + "WHERE{?iri " + LifecycleResource.LIFECYCLE_STAGE_PREDICATE_PATH
        + "/<https://spec.edmcouncil.org/fibo/ontology/FND/DatesAndTimes/FinancialDates/hasSchedule>/fibo-fnd-dt-fd:hasRecurrenceInterval ?day.}"
        + "GROUP BY ?iri}"
        // WARNING: FedX seems to execute filters at the end and will return inaccurate
        // values otherwise
        + "FILTER STRENDS(STR(?iri),\"" + contractId + "\")"
        + "}";
  }

  /**
   * Retrieves the SPARQL query to get active contracts that have passed the end
   * date ie expired.
   */
  public String getExpiredActiveContractQuery() {
    StringBuilder activeFilter = new StringBuilder();
    this.appendFilterExists(activeFilter, true, LifecycleResource.EVENT_APPROVAL);
    this.appendArchivedFilterExists(activeFilter, false);
    return StringResource.QUERY_TEMPLATE_PREFIX
        + "SELECT DISTINCT ?iri WHERE{"
        + "?iri fibo-fnd-arr-lif:hasLifecycle/fibo-fnd-arr-lif:hasStage ?stage."
        // Nested query for all days
        + "?stage fibo-fnd-rel-rel:exemplifies <"
        + LifecycleEventType.SERVICE_EXECUTION.getStage() + ">;"
        + "<https://www.omg.org/spec/Commons/PartiesAndSituations/holdsDuring>/cmns-dt:hasEndDate/cmns-dt:hasDateValue ?end_date."
        + activeFilter
        + "FILTER(?end_date<xsd:date(NOW()))"
        + "}";
  }

  /**
   * Retrieves the SPARQL query to get the service tasks for the specified
   * date and/or contract.
   * 
   * @param contract  Target contract iri. Optional if null is passed.
   * @param startDate Target start date in YYYY-MM-DD format. Optional if null is
   *                  passed.
   * @param endDate   Target end date in YYYY-MM-DD format. Optional if null is
   *                  passed.
   */
  public String getServiceTasksQuery(String contract, String startDate, String endDate) {
    String eventDateVar = ShaclResource.VARIABLE_MARK + LifecycleResource.DATE_KEY;
    String eventDatePlaceholderVar = ShaclResource.VARIABLE_MARK + "event_date";
    String eventVar = ShaclResource.VARIABLE_MARK + LifecycleResource.EVENT_KEY;
    String eventIdVar = StringResource.parseQueryVariable(ShaclResource.VARIABLE_MARK + LifecycleResource.EVENT_ID_KEY);

    String filterContractStatement = contract != null ? "FILTER STRENDS(STR(?iri),\"" + contract + "\")" : "";
    // Filter dates
    String filterDateStatement = "";
    if (contract == null && endDate != null) {
      // For outstanding tasks, start dates are omitted
      if (!startDate.isEmpty()) {
        filterDateStatement = eventDateVar + ">=\"" + startDate + "\"^^xsd:date && ";
      }
      filterDateStatement = "FILTER(" + filterDateStatement + eventDateVar + "<=\"" + endDate
          + "\"^^xsd:date)";
    }
    return "?iri <https://spec.edmcouncil.org/fibo/ontology/FND/Arrangements/Lifecycles/hasLifecycle>/<https://spec.edmcouncil.org/fibo/ontology/FND/Arrangements/Lifecycles/hasStage> ?stage."
        + "?stage <https://spec.edmcouncil.org/fibo/ontology/FND/Relations/Relations/exemplifies> <"
        + LifecycleEventType.SERVICE_EXECUTION.getStage() + ">;"
        + "<https://www.omg.org/spec/Commons/Collections/comprises> ?order_event."
        + "?order_event <https://spec.edmcouncil.org/fibo/ontology/FND/Relations/Relations/exemplifies> "
        + StringResource.parseIriForQuery(LifecycleResource.EVENT_ORDER_RECEIVED) + "."
        + "BIND(xsd:date(" + eventDatePlaceholderVar + ") AS " + eventDateVar + ")"
        + eventIdVar + " <https://spec.edmcouncil.org/fibo/ontology/FND/Relations/Relations/exemplifies> "
        + eventVar + ";"
        + "<https://spec.edmcouncil.org/fibo/ontology/FND/DatesAndTimes/Occurrences/hasEventDate> "
        + eventDatePlaceholderVar
        + ";<https://www.omg.org/spec/Commons/DatesAndTimes/succeeds>* ?order_event."
        + filterDateStatement
        + filterContractStatement
        // Event must be the last in the chain ie no other events will succeed it
        + "MINUS{" + eventIdVar + " ^<https://www.omg.org/spec/Commons/DatesAndTimes/succeeds> ?any_event}";
  }

  /**
   * Retrieves the SPARQL query to get a stage associated with the target
   * contract.
   * 
   * @param contract  The target contract instance.
   * @param eventType The target event type to retrieve.
   */
  public String getStageQuery(String contract, LifecycleEventType eventType) {
    return StringResource.QUERY_TEMPLATE_PREFIX
        + "SELECT DISTINCT ?iri WHERE {" +
        "?contract fibo-fnd-arr-lif:hasLifecycle ?lifecycle ." +
        "?lifecycle fibo-fnd-arr-lif:hasStage ?iri ." +
        "?iri fibo-fnd-rel-rel:exemplifies <" + eventType.getStage() + "> ." +
        "FILTER STRENDS(STR(?contract),\"" + contract + "\")" +
        "}";
  }

  /**
   * Retrieves the SPARQL query to retrieve the event instance associated with the
   * target event type and the event instance input. The output event must occur
   * before the input event.
   * 
   * @param event     The input event instance.
   * @param eventType The target event type to retrieve.
   */
  public String getEventQuery(String event, LifecycleEventType eventType) {
    return StringResource.QUERY_TEMPLATE_PREFIX +
        "SELECT DISTINCT ?iri ?event WHERE{" +
        "?contract fibo-fnd-arr-lif:hasLifecycle/fibo-fnd-arr-lif:hasStage ?stage." +
        "?stage cmns-col:comprises ?event;" +
        "cmns-col:comprises ?iri." +
        "?event cmns-dt:succeeds? ?iri." +
        "?iri fibo-fnd-rel-rel:exemplifies <" + eventType.getEvent() + ">." +
        "FILTER STRENDS(STR(?event),\"" + event + "\")" +
        "}";
  }

  /**
   * Retrieves the SPARQL query to retrieve the event instance associated with the
   * target event type for a specific contract.
   * 
   * @param contract  The input contract instance.
   * @param eventType The target event type to retrieve.
   */
  public String getContractEventQuery(String contract, LifecycleEventType eventType) {
    return StringResource.QUERY_TEMPLATE_PREFIX +
        "SELECT DISTINCT ?iri ?event WHERE{" +
        "?contract fibo-fnd-arr-lif:hasLifecycle/fibo-fnd-arr-lif:hasStage ?stage." +
        "?stage cmns-col:comprises ?event;" +
        "cmns-col:comprises ?iri." +
        "?event fibo-fnd-rel-rel:exemplifies <https://www.theworldavatar.com/kg/ontoservice/OrderReceivedEvent>;" +
        "^cmns-dt:succeeds* ?iri." +
        "?iri fibo-fnd-rel-rel:exemplifies <" + eventType.getEvent() + ">." +
        "FILTER STRENDS(STR(?contract),\"" + contract + "\")" +
        "}";
  }

  /**
   * Retrieves the SPARQL query to get a report associated with the target stage.
   * 
   * @param stage The target stage occurrence instance.
   */
  public String getReportQuery(String stage) {
    return StringResource.QUERY_TEMPLATE_PREFIX
        + "SELECT DISTINCT ?iri WHERE {"
        + "?iri a " + StringResource.parseIriForQuery(LifecycleResource.LIFECYCLE_REPORT) + ";"
        + StringResource.parseIriForQuery(LifecycleResource.IS_ABOUT_RELATIONS)
        + "/fibo-fnd-arr-lif:hasLifecycle/fibo-fnd-arr-lif:hasStage <" + stage + ">."
        + "}";
  }

  /**
   * Generates lifecycle filter statements for SPARQL if required based on the
   * specified event.
   * 
   * @param lifecycleEvent Target event for filter.
   */
  public String genLifecycleFilterStatements(LifecycleEventType lifecycleEvent) {
    StringBuilder query = new StringBuilder();
    query.append(this.getReadableScheduleQuery());
    switch (lifecycleEvent) {
      case LifecycleEventType.APPROVED:
        this.appendFilterExists(query, false, LifecycleResource.EVENT_APPROVAL);
        break;
      case LifecycleEventType.SERVICE_EXECUTION:
        this.appendFilterExists(query, true, LifecycleResource.EVENT_APPROVAL);
        this.appendArchivedFilterExists(query, false);
        break;
      case LifecycleEventType.ARCHIVE_COMPLETION:
        this.appendArchivedStateQuery(query);
        break;
      default:
        // Do nothing if it doesnt meet the above events
        break;
    }
    return query.toString();
  }

  /**
   * Appends a query statement to retrieve the status of an archived contract.
   * 
   * @param query Builder for the query template.
   */
  private void appendArchivedStateQuery(StringBuilder query) {
    String eventVar = ShaclResource.VARIABLE_MARK + LifecycleResource.EVENT_KEY;
    StringBuilder tempBuilder = new StringBuilder();
    StringResource.appendTriple(tempBuilder, ShaclResource.VARIABLE_MARK + LifecycleResource.IRI_KEY,
        LifecycleResource.LIFECYCLE_STAGE_PREDICATE_PATH + "/" + LifecycleResource.LIFECYCLE_STAGE_EVENT_PREDICATE_PATH
            + "/" + LifecycleResource.LIFECYCLE_EVENT_TYPE_PREDICATE_PATH,
        ShaclResource.VARIABLE_MARK + LifecycleResource.EVENT_KEY);
    String statement = "BIND("
        + "IF(" + eventVar + "=" + StringResource.parseIriForQuery(LifecycleResource.EVENT_CONTRACT_COMPLETION)
        + ",\"Completed\","
        + "IF(" + eventVar + "=" + StringResource.parseIriForQuery(LifecycleResource.EVENT_CONTRACT_RESCISSION)
        + ",\"Rescinded\","
        + "IF(" + eventVar + "=" + StringResource.parseIriForQuery(LifecycleResource.EVENT_CONTRACT_TERMINATION)
        + ",\"Terminated\""
        + ",\"Unknown\"))) AS ?" + LifecycleResource.STATUS_KEY
        + ")"
        + "FILTER(?" + LifecycleResource.STATUS_KEY + "!=\"Unknown\")";
    query.append(StringResource.genGroupGraphPattern(tempBuilder.toString()))
        .append(statement);
  }

  /**
   * Appends FILTER EXISTS or NOT EXISTS statements for an archived contract.
   * 
   * @param query  Builder for the query template.
   * @param exists Indicate if using FILTER EXISTS or FILTER NOT EXISTS.
   */
  private void appendArchivedFilterExists(StringBuilder query, boolean exists) {
    String stageVar = ShaclResource.VARIABLE_MARK + LifecycleResource.STAGE_KEY + "_archived";
    StringBuilder tempBuilder = new StringBuilder();
    StringResource.appendTriple(tempBuilder, ShaclResource.VARIABLE_MARK + LifecycleResource.IRI_KEY,
        LifecycleResource.LIFECYCLE_STAGE_PREDICATE_PATH, stageVar);
    StringResource.appendTriple(tempBuilder, stageVar, LifecycleResource.LIFECYCLE_EVENT_TYPE_PREDICATE_PATH,
        StringResource.parseIriForQuery(LifecycleEventType.ARCHIVE_COMPLETION.getStage()));
    StringResource.appendTriple(tempBuilder, stageVar, LifecycleResource.LIFECYCLE_STAGE_EVENT_PREDICATE_PATH,
        ShaclResource.VARIABLE_MARK + LifecycleResource.EVENT_KEY);
    this.appendFilterExists(query, tempBuilder.toString(), exists);
  }

  /**
   * Appends FILTER EXISTS or NOT EXISTS statements for the specified object
   * instance.
   * 
   * @param query    Builder for the query template.
   * @param exists   Indicate if using FILTER EXISTS or FILTER NOT EXISTS.
   * @param instance Target IRI instance. Typically the object in a triple.
   */
  private void appendFilterExists(StringBuilder query, boolean exists, String instance) {
    StringBuilder tempBuilder = new StringBuilder();
    StringResource.appendTriple(tempBuilder, "?iri", LifecycleResource.LIFECYCLE_EVENT_PREDICATE_PATH,
        StringResource.parseIriForQuery(instance));
    this.appendFilterExists(query, tempBuilder.toString(), exists);
  }

  /**
   * Appends FILTER EXISTS or NOT EXISTS statements for lifecycles.
   * 
   * @param query    Builder for the query template.
   * @param contents Contents for the clause.
   * @param exists   Indicate if using FILTER EXISTS or FILTER NOT EXISTS.
   */
  private void appendFilterExists(StringBuilder query, String contents, boolean exists) {
    String constraintKeyword = "";
    // Add NOT parameter if this filter should not exist
    if (exists) {
      constraintKeyword = "FILTER EXISTS";
    } else {
      constraintKeyword = "MINUS";
    }
    query.append(constraintKeyword).append(StringResource.genGroupGraphPattern(contents));
  }

  /**
   * Gets the template query for regular schedules.
   */
  private String getScheduleTemplate() {
    return "?iri " + LifecycleResource.LIFECYCLE_STAGE_PREDICATE_PATH
        + "/<https://spec.edmcouncil.org/fibo/ontology/FND/DatesAndTimes/FinancialDates/hasSchedule> ?schedule."
        + "?schedule <https://www.omg.org/spec/Commons/DatesAndTimes/hasStartDate>/<https://www.omg.org/spec/Commons/DatesAndTimes/hasDateValue> ?"
        + StringResource.parseQueryVariable(LifecycleResource.SCHEDULE_START_DATE_KEY) + ";"
        + "^<https://spec.edmcouncil.org/fibo/ontology/FND/DatesAndTimes/FinancialDates/hasSchedule>/<https://www.omg.org/spec/Commons/PartiesAndSituations/holdsDuring>/<https://www.omg.org/spec/Commons/DatesAndTimes/hasEndDate>/<https://www.omg.org/spec/Commons/DatesAndTimes/hasDateValue> ?"
        + StringResource.parseQueryVariable(LifecycleResource.SCHEDULE_END_DATE_KEY) + ";"
        + "<https://www.omg.org/spec/Commons/DatesAndTimes/hasTimePeriod>/<https://www.omg.org/spec/Commons/DatesAndTimes/hasStart>/<https://www.omg.org/spec/Commons/DatesAndTimes/hasTimeValue> ?"
        + StringResource.parseQueryVariable(LifecycleResource.SCHEDULE_START_TIME_KEY) + ";"
        + "<https://www.omg.org/spec/Commons/DatesAndTimes/hasTimePeriod>/<https://www.omg.org/spec/Commons/DatesAndTimes/hasEndTime>/<https://www.omg.org/spec/Commons/DatesAndTimes/hasTimeValue> ?"
        + StringResource.parseQueryVariable(LifecycleResource.SCHEDULE_END_TIME_KEY) + ";"
        + "<https://spec.edmcouncil.org/fibo/ontology/FND/DatesAndTimes/FinancialDates/hasRecurrenceInterval>/<https://www.omg.org/spec/Commons/DatesAndTimes/hasDurationValue> ?"
        + LifecycleResource.SCHEDULE_RECURRENCE_KEY + ShaclResource.FULL_STOP;
  }
}
