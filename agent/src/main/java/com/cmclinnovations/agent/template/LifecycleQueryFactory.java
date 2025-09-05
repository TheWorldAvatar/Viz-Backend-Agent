package com.cmclinnovations.agent.template;

import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf;

import com.cmclinnovations.agent.model.type.LifecycleEventType;
import com.cmclinnovations.agent.utils.LifecycleResource;
import com.cmclinnovations.agent.utils.QueryResource;
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
    return QueryResource.PREFIX_TEMPLATE
        + "SELECT DISTINCT ?iri ?status WHERE{"
        + "{SELECT DISTINCT ?iri (MAX(?priority_val) AS ?priority) WHERE{"
        + "?iri dc-terms:identifier \"" + contractId + "\";"
        + "fibo-fnd-arr-lif:hasLifecycle/fibo-fnd-arr-lif:hasStage/<https://www.omg.org/spec/Commons/Collections/comprises> ?event."
        + "?event " + LifecycleResource.LIFECYCLE_EVENT_TYPE_PREDICATE_PATH + " ?event_type."
        + "BIND(IF(?event_type=ontoservice:ContractDischarge||?event_type=ontoservice:ContractRescission||?event_type=ontoservice:ContractTermination,"
        + "2,IF(?event_type=ontoservice:ContractApproval,1,0)"
        + ") AS ?priority_val)"
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
        + "BIND(IF(" + QueryResource.genVariable(LifecycleResource.SCHEDULE_RECURRENCE_KEY).getQueryString()
        + "=\"P1D\",\"Single Service\","
        + "IF(" + QueryResource.genVariable(LifecycleResource.SCHEDULE_RECURRENCE_KEY).getQueryString()
        + "=\"P2D\",\"Alternate Day Service\", "
        + "\"Regular Service\")" // Close IF statement
        + ") AS " + QueryResource.genVariable(LifecycleResource.SCHEDULE_TYPE_KEY).getQueryString() + ")";
  }

  /**
   * Retrieves the SPARQL query to get the schedule of the contract.
   * 
   * @param contractId the target contract id.
   */
  public String getServiceScheduleQuery(String contractId) {
    return QueryResource.PREFIX_TEMPLATE
        + "SELECT DISTINCT * WHERE{"
        + this.getScheduleTemplate()
        + "?iri dc-terms:identifier \"" + contractId + "\";"
        // Nested query for all days
        + "{SELECT ?iri "
        + "(MAX(IF(?day=fibo-fnd-dt-fd:Monday,\"Monday\",\"\")) AS ?monday) "
        + "(MAX(IF(?day=fibo-fnd-dt-fd:Tuesday,\"Tuesday\",\"\")) AS ?tuesday) "
        + "(MAX(IF(?day=fibo-fnd-dt-fd:Wednesday,\"Wednesday\",\"\")) AS ?wednesday) "
        + "(MAX(IF(?day=fibo-fnd-dt-fd:Thursday,\"Thursday\",\"\")) AS ?thursday) "
        + "(MAX(IF(?day=fibo-fnd-dt-fd:Friday,\"Friday\",\"\")) AS ?friday) "
        + "(MAX(IF(?day=fibo-fnd-dt-fd:Saturday,\"Saturday\",\"\")) AS ?saturday) "
        + "(MAX(IF(?day=fibo-fnd-dt-fd:Sunday,\"Sunday\",\"\")) AS ?sunday) "
        + "WHERE{?iri dc-terms:identifier \"" + contractId + "\";"
        + LifecycleResource.LIFECYCLE_STAGE_PREDICATE_PATH
        + "/<https://spec.edmcouncil.org/fibo/ontology/FND/DatesAndTimes/FinancialDates/hasSchedule>/fibo-fnd-dt-fd:hasRecurrenceInterval ?day.}"
        + "GROUP BY ?iri}"
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
    return QueryResource.PREFIX_TEMPLATE
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
    String eventDateVar = QueryResource.genVariable(LifecycleResource.DATE_KEY).getQueryString();
    String eventDatePlaceholderVar = QueryResource.genVariable("event_date").getQueryString();
    String eventVar = QueryResource.genVariable(LifecycleResource.EVENT_KEY).getQueryString();
    String eventIdVar = QueryResource.genVariable(LifecycleResource.EVENT_ID_KEY).getQueryString();
    String eventStatusVar = QueryResource.genVariable(LifecycleResource.EVENT_STATUS_KEY).getQueryString();

    String filterContractStatement = contract != null ? "?iri dc-terms:identifier \"" + contract + "\"." : "";
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
        + Rdf.iri(LifecycleResource.EVENT_ORDER_RECEIVED).getQueryString() + "."
        + "BIND(xsd:date(" + eventDatePlaceholderVar + ") AS " + eventDateVar + ")"
        + eventIdVar + " <https://spec.edmcouncil.org/fibo/ontology/FND/Relations/Relations/exemplifies> "
        + eventVar + ";"
        + "<https://spec.edmcouncil.org/fibo/ontology/FND/DatesAndTimes/Occurrences/hasEventDate> "
        + eventDatePlaceholderVar
        + ";<https://www.omg.org/spec/Commons/DatesAndTimes/succeeds>* ?order_event."
        + "OPTIONAL{" + eventIdVar + " <https://www.omg.org/spec/Commons/Designators/describes> " + eventStatusVar + "}"
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
    return QueryResource.PREFIX_TEMPLATE
        + "SELECT DISTINCT ?iri WHERE {" +
        "?contract fibo-fnd-arr-lif:hasLifecycle ?lifecycle;" +
        "dc-terms:identifier \"" + contract + "\"." +
        "?lifecycle fibo-fnd-arr-lif:hasStage ?iri ." +
        "?iri fibo-fnd-rel-rel:exemplifies <" + eventType.getStage() + "> ." +
        "}";
  }

  /**
   * Retrieves the SPARQL query to retrieve the event instance associated with the
   * target event type for a specific contract.
   * 
   * @param contract  The input contract instance.
   * @param date      Optional date for filtering.
   * @param taskId    Optional identifier of the final task for filtering.
   * @param eventType The target event type to retrieve.
   */
  public String getContractEventQuery(String contract, String date, String taskId, LifecycleEventType eventType) {
    String dateFilter = "";
    if (date != null) {
      dateFilter = "FILTER(xsd:date(?date)=\"" + date + "\"^^xsd:date)";
    }
    String finalTaskIdFilter = taskId != null
        ? QueryResource.DC_TERM_ID.getQueryString() + " " + Rdf.literalOf(taskId).getQueryString() + ";"
        : "";
    return QueryResource.PREFIX_TEMPLATE +
        "SELECT DISTINCT ?iri ?id WHERE{" +
        "?contract dc-terms:identifier \"" + contract + "\";" +
        "fibo-fnd-arr-lif:hasLifecycle/fibo-fnd-arr-lif:hasStage ?stage." +
        "?stage cmns-col:comprises ?event;" +
        "cmns-col:comprises ?iri." +
        "?event fibo-fnd-rel-rel:exemplifies <https://www.theworldavatar.com/kg/ontoservice/OrderReceivedEvent>;" +
        "^cmns-dt:succeeds* ?final_event;" +
        "^cmns-dt:succeeds* ?iri." +
        "?final_event " + finalTaskIdFilter + "fibo-fnd-dt-oc:hasEventDate ?date." +
        "?iri dc-terms:identifier ?id;" +
        "fibo-fnd-rel-rel:exemplifies <" + eventType.getEvent() + ">." +
        dateFilter +
        "MINUS{?final_event ^<https://www.omg.org/spec/Commons/DatesAndTimes/succeeds> ?any_event}" +
        "}";
  }

  /**
   * Retrieves the SPARQL query to get a report associated with the target stage.
   * 
   * @param stage The target stage occurrence instance.
   */
  public String getReportQuery(String stage) {
    return QueryResource.PREFIX_TEMPLATE
        + "SELECT DISTINCT ?iri WHERE {"
        + "?iri a " + Rdf.iri(LifecycleResource.LIFECYCLE_REPORT).getQueryString() + ";"
        + Rdf.iri(LifecycleResource.IS_ABOUT_RELATIONS).getQueryString()
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
    String eventVar = QueryResource.genVariable(LifecycleResource.EVENT_KEY).getQueryString();
    StringBuilder tempBuilder = new StringBuilder();
    StringResource.appendTriple(tempBuilder, QueryResource.IRI_VAR.getQueryString(),
        LifecycleResource.LIFECYCLE_STAGE_PREDICATE_PATH + "/" + LifecycleResource.LIFECYCLE_STAGE_EVENT_PREDICATE_PATH
            + "/" + LifecycleResource.LIFECYCLE_EVENT_TYPE_PREDICATE_PATH,
        eventVar);
    String statement = "BIND("
        + "IF(" + eventVar + "=" + Rdf.iri(LifecycleResource.EVENT_CONTRACT_COMPLETION).getQueryString()
        + ",\"Completed\","
        + "IF(" + eventVar + "=" + Rdf.iri(LifecycleResource.EVENT_CONTRACT_RESCISSION).getQueryString()
        + ",\"Rescinded\","
        + "IF(" + eventVar + "=" + Rdf.iri(LifecycleResource.EVENT_CONTRACT_TERMINATION).getQueryString()
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
    String stageVar = QueryResource.genVariable(LifecycleResource.STAGE_KEY + "_archived").getQueryString();
    StringBuilder tempBuilder = new StringBuilder();
    StringResource.appendTriple(tempBuilder, QueryResource.IRI_VAR.getQueryString(),
        LifecycleResource.LIFECYCLE_STAGE_PREDICATE_PATH, stageVar);
    StringResource.appendTriple(tempBuilder, stageVar, LifecycleResource.LIFECYCLE_EVENT_TYPE_PREDICATE_PATH,
        Rdf.iri(LifecycleEventType.ARCHIVE_COMPLETION.getStage()).getQueryString());
    StringResource.appendTriple(tempBuilder, stageVar, LifecycleResource.LIFECYCLE_STAGE_EVENT_PREDICATE_PATH,
        QueryResource.genVariable(LifecycleResource.EVENT_KEY).getQueryString());
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
        Rdf.iri(instance).getQueryString());
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
        + "?schedule <https://www.omg.org/spec/Commons/DatesAndTimes/hasStartDate>/<https://www.omg.org/spec/Commons/DatesAndTimes/hasDateValue> "
        + QueryResource.genVariable(LifecycleResource.SCHEDULE_START_DATE_KEY).getQueryString() + ";"
        + "^<https://spec.edmcouncil.org/fibo/ontology/FND/DatesAndTimes/FinancialDates/hasSchedule>/<https://www.omg.org/spec/Commons/PartiesAndSituations/holdsDuring>/<https://www.omg.org/spec/Commons/DatesAndTimes/hasEndDate>/<https://www.omg.org/spec/Commons/DatesAndTimes/hasDateValue> "
        + QueryResource.genVariable(LifecycleResource.SCHEDULE_END_DATE_KEY).getQueryString() + ";"
        + "<https://www.omg.org/spec/Commons/DatesAndTimes/hasTimePeriod>/<https://www.omg.org/spec/Commons/DatesAndTimes/hasStart>/<https://www.omg.org/spec/Commons/DatesAndTimes/hasTimeValue> "
        + QueryResource.genVariable(LifecycleResource.SCHEDULE_START_TIME_KEY).getQueryString() + ";"
        + "<https://www.omg.org/spec/Commons/DatesAndTimes/hasTimePeriod>/<https://www.omg.org/spec/Commons/DatesAndTimes/hasEndTime>/<https://www.omg.org/spec/Commons/DatesAndTimes/hasTimeValue> "
        + QueryResource.genVariable(LifecycleResource.SCHEDULE_END_TIME_KEY).getQueryString() + ";"
        + "<https://spec.edmcouncil.org/fibo/ontology/FND/DatesAndTimes/FinancialDates/hasRecurrenceInterval>/<https://www.omg.org/spec/Commons/DatesAndTimes/hasDurationValue> "
        + QueryResource.genVariable(LifecycleResource.SCHEDULE_RECURRENCE_KEY).getQueryString()
        + ShaclResource.FULL_STOP;
  }
}
