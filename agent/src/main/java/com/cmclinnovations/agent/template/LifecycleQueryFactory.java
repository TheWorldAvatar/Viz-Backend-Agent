package com.cmclinnovations.agent.template;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.rdf4j.model.vocabulary.RDFS;
import org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder;
import org.eclipse.rdf4j.sparqlbuilder.core.Variable;
import org.eclipse.rdf4j.sparqlbuilder.core.query.ModifyQuery;
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPatternNotTriples;
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.TriplePattern;
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf;

import com.cmclinnovations.agent.component.LocalisationTranslator;
import com.cmclinnovations.agent.model.type.LifecycleEventType;
import com.cmclinnovations.agent.utils.LifecycleResource;
import com.cmclinnovations.agent.utils.LocalisationResource;
import com.cmclinnovations.agent.utils.QueryResource;
import com.cmclinnovations.agent.utils.ShaclResource;

public class LifecycleQueryFactory {
  private static final Map<String, String> SCHEDULE_QUERY_MAPPINGS;
  private static final Map<String, String> SCHEDULE_CONTRACT_FILTER_QUERY_MAPPINGS;

  /**
   * Constructs a new query factory.
   */
  public LifecycleQueryFactory() {
    // No set up required
  }

  static {
    Map<String, String> template = new HashMap<>();
    template.put(LifecycleResource.SCHEDULE_RESOURCE, "?iri " + LifecycleResource.LIFECYCLE_STAGE_PREDICATE_PATH
        + "/<https://spec.edmcouncil.org/fibo/ontology/FND/DatesAndTimes/FinancialDates/hasSchedule> ?schedule.");
    template.put(QueryResource.SCHEDULE_START_DATE_VAR.getVarName(),
        "?schedule <https://www.omg.org/spec/Commons/DatesAndTimes/hasStartDate>/<https://www.omg.org/spec/Commons/DatesAndTimes/hasDateValue> "
            + QueryResource.SCHEDULE_START_DATE_VAR.getQueryString() + ShaclResource.FULL_STOP);
    template.put(QueryResource.SCHEDULE_START_TIME_VAR.getVarName(),
        "?schedule <https://www.omg.org/spec/Commons/DatesAndTimes/hasTimePeriod>/<https://www.omg.org/spec/Commons/DatesAndTimes/hasStart>/<https://www.omg.org/spec/Commons/DatesAndTimes/hasTimeValue> "
            + QueryResource.SCHEDULE_START_TIME_VAR.getQueryString() + ShaclResource.FULL_STOP);
    template.put(QueryResource.SCHEDULE_END_TIME_VAR.getVarName(),
        "?schedule <https://www.omg.org/spec/Commons/DatesAndTimes/hasTimePeriod>/<https://www.omg.org/spec/Commons/DatesAndTimes/hasEndTime>/<https://www.omg.org/spec/Commons/DatesAndTimes/hasTimeValue> "
            + QueryResource.SCHEDULE_END_TIME_VAR.getQueryString() + ShaclResource.FULL_STOP);
    template.put(QueryResource.SCHEDULE_END_DATE_VAR.getVarName(),
        "OPTIONAL{?schedule ^<https://spec.edmcouncil.org/fibo/ontology/FND/DatesAndTimes/FinancialDates/hasSchedule>/<https://www.omg.org/spec/Commons/PartiesAndSituations/holdsDuring>/<https://www.omg.org/spec/Commons/DatesAndTimes/hasEndDate>/<https://www.omg.org/spec/Commons/DatesAndTimes/hasDateValue> "
            + QueryResource.SCHEDULE_END_DATE_VAR.getQueryString() + ".}");
    template.put(QueryResource.SCHEDULE_RECURRENCE_VAR.getVarName(),
        "OPTIONAL{?schedule <https://spec.edmcouncil.org/fibo/ontology/FND/DatesAndTimes/FinancialDates/hasRecurrenceInterval>/<https://www.omg.org/spec/Commons/DatesAndTimes/hasDurationValue> ?"
            + LifecycleResource.SCHEDULE_RECURRENCE_PLACEHOLDER_KEY + ".}");
    SCHEDULE_QUERY_MAPPINGS = Collections.unmodifiableMap(template);
    // Add extended statements to the right mappings with a full reset
    Map<String, String> filterTemplate = new HashMap<>();
    LifecycleResource.convertVarForStrFilter(QueryResource.SCHEDULE_START_DATE_VAR, template, filterTemplate);
    LifecycleResource.convertVarForStrFilter(QueryResource.SCHEDULE_END_DATE_VAR, template, filterTemplate);
    LifecycleResource.convertVarForStrFilter(QueryResource.SCHEDULE_START_TIME_VAR, template, filterTemplate);
    LifecycleResource.convertVarForStrFilter(QueryResource.SCHEDULE_END_TIME_VAR, template, filterTemplate);
    SCHEDULE_CONTRACT_FILTER_QUERY_MAPPINGS = Collections.unmodifiableMap(filterTemplate);
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
   * Retrieves the SPARQL query to get the schedule of the contract.
   * 
   * @param contractId the target contract id.
   */
  public String getServiceScheduleQuery(String contractId) {
    return QueryResource.PREFIX_TEMPLATE
        + "SELECT DISTINCT * WHERE{"
        + SCHEDULE_QUERY_MAPPINGS.values().stream().collect(Collectors.joining("\n"))
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
   * Retrieves the simplified SPARQL query to get the service tasks with the right
   * event.
   * 
   * @param startDate Target start date in YYYY-MM-DD format. Optional when
   *                  passing "".
   * @param endDate   Target end date in YYYY-MM-DD format.
   * @param isClosed  Indicates if task has been closed or not.
   */
  public Map<String, String> getServiceTasksFilter(String startDate, String endDate, boolean isClosed) {
    String eventDateVar = QueryResource.genVariable(LifecycleResource.DATE_KEY).getQueryString();
    Map<String, String> results = new HashMap<>();
    String targetEventStatements;
    if (isClosed) {
      targetEventStatements = QueryResource.union(
          this.genEventClause(QueryResource.IRI_VAR, LifecycleEventType.SERVICE_INCIDENT_REPORT).getQueryString(),
          this.genEventClause(QueryResource.IRI_VAR, LifecycleEventType.SERVICE_CANCELLATION).getQueryString(),
          this.genEventClause(QueryResource.IRI_VAR, LifecycleEventType.SERVICE_EXECUTION,
              LifecycleResource.COMPLETION_EVENT_COMPLETED_STATUS).getQueryString());
    } else {
      targetEventStatements = QueryResource.union(
          this.genEventClause(QueryResource.IRI_VAR, LifecycleEventType.SERVICE_ORDER_RECEIVED).getQueryString(),
          this.genEventClause(QueryResource.IRI_VAR, LifecycleEventType.SERVICE_ORDER_DISPATCHED).getQueryString(),
          this.genEventClause(QueryResource.IRI_VAR, LifecycleEventType.SERVICE_EXECUTION,
              LifecycleResource.EVENT_PENDING_STATUS).getQueryString());
    }
    results.put(LifecycleResource.LIFECYCLE_RESOURCE,
        "?stage <https://spec.edmcouncil.org/fibo/ontology/FND/Relations/Relations/exemplifies> <"
            + LifecycleEventType.SERVICE_EXECUTION.getStage() + ">;"
            + "<https://www.omg.org/spec/Commons/Collections/comprises> " + QueryResource.IRI_VAR.getQueryString() + "."
            // Event must be the last in the chain ie no other events will succeed it
            + "MINUS{" + QueryResource.IRI_VAR.getQueryString()
            + " ^<https://www.omg.org/spec/Commons/DatesAndTimes/succeeds> ?any_event}"
            + targetEventStatements);
    // Generate date query
    String filterDateStatement = "";
    // For outstanding tasks, start dates are omitted
    if (!startDate.isEmpty()) {
      filterDateStatement = "xsd:date(" + eventDateVar + ")>=\"" + startDate + "\"^^xsd:date && ";
    }
    filterDateStatement = "FILTER(" + filterDateStatement + "xsd:date(" + eventDateVar + ")<=\"" + endDate
        + "\"^^xsd:date)";
    results.put(LifecycleResource.DATE_KEY, QueryResource.IRI_VAR.getQueryString()
        + " <https://spec.edmcouncil.org/fibo/ontology/FND/DatesAndTimes/Occurrences/hasEventDate> "
        + eventDateVar + "."
        + filterDateStatement);
    results.put(LifecycleResource.LAST_MODIFIED_KEY,
        "?iri <https://spec.edmcouncil.org/fibo/ontology/FND/DatesAndTimes/Occurrences/hasEventDate> "
            + QueryResource.LAST_MODIFIED_VAR.getQueryString() + ShaclResource.FULL_STOP);
    return results;
  }

  /**
   * Generates an event clause for the target event type. Overloaded method that
   * ignores event status.
   * 
   * @param subject   The subject variable.
   * @param eventType Lifecycle event to be generated.
   */
  public TriplePattern genEventClause(Variable subject, LifecycleEventType eventType) {
    return this.genEventClause(subject, eventType, "");
  }

  /**
   * Generates an event clause for the target event type.
   * 
   * @param subject     The subject variable.
   * @param eventType   Lifecycle event to be generated.
   * @param eventStatus Optional IRI to describe the event status.
   */
  public TriplePattern genEventClause(Variable subject, LifecycleEventType eventType, String eventStatus) {
    TriplePattern pattern = subject.has(
        QueryResource.FIBO_FND_REL_REL_EXEMPLIFIES,
        Rdf.iri(eventType.getEvent()));
    if (!eventStatus.isEmpty()) {
      return pattern.andHas(QueryResource.CMNS_DSG_DESCRIBES,
          Rdf.iri(eventStatus));
    }
    return pattern;
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
   * @param isClosed  Indicates whether to retrieve closed tasks.
   */
  public Map<String, String> getServiceTasksQuery(String contract, String startDate, String endDate, Boolean isClosed) {
    String eventDateVar = QueryResource.genVariable(LifecycleResource.DATE_KEY).getQueryString();
    String eventDatePlaceholderVar = QueryResource.genVariable("event_date").getQueryString();
    String eventVar = QueryResource.genVariable(LifecycleResource.EVENT_KEY).getQueryString();
    String eventIdVar = QueryResource.EVENT_ID_VAR.getQueryString();
    String eventStatusVar = QueryResource.EVENT_STATUS_VAR.getQueryString();
    String lastModifiedVar = QueryResource.genVariable(LifecycleResource.LAST_MODIFIED_KEY).getQueryString();

    Map<String, String> results = new HashMap<>();
    String filterContractStatement = contract != null ? "?iri dc-terms:identifier \"" + contract + "\"." : "";
    // Dates must be included in the template to sort out different task status
    String filterDateStatement = "";
    if (contract == null && endDate != null) {
      // For outstanding tasks, start dates are omitted
      if (!startDate.isEmpty()) {
        filterDateStatement = eventDateVar + ">=\"" + startDate + "\"^^xsd:date && ";
      }
      filterDateStatement = "FILTER(" + filterDateStatement + eventDateVar + "<=\"" + endDate
          + "\"^^xsd:date)";
    }
    // Generates query statements to target specific events based on closed status
    String eventTargetStatements;
    if (isClosed == null) {
      eventTargetStatements = "";
    } else if (isClosed) {
      eventTargetStatements = QueryResource.union(
          this.genEventClause(QueryResource.EVENT_ID_VAR, LifecycleEventType.SERVICE_INCIDENT_REPORT).getQueryString(),
          this.genEventClause(QueryResource.EVENT_ID_VAR, LifecycleEventType.SERVICE_CANCELLATION).getQueryString(),
          this.genEventClause(QueryResource.EVENT_ID_VAR, LifecycleEventType.SERVICE_EXECUTION,
              LifecycleResource.COMPLETION_EVENT_COMPLETED_STATUS).getQueryString());
    } else {
      eventTargetStatements = QueryResource.union(
          this.genEventClause(QueryResource.EVENT_ID_VAR, LifecycleEventType.SERVICE_ORDER_RECEIVED).getQueryString(),
          this.genEventClause(QueryResource.EVENT_ID_VAR, LifecycleEventType.SERVICE_ORDER_DISPATCHED).getQueryString(),
          this.genEventClause(QueryResource.EVENT_ID_VAR, LifecycleEventType.SERVICE_EXECUTION,
              LifecycleResource.EVENT_PENDING_STATUS).getQueryString());
    }
    results.put(LifecycleResource.LIFECYCLE_RESOURCE,
        "?iri <https://spec.edmcouncil.org/fibo/ontology/FND/Arrangements/Lifecycles/hasLifecycle>/<https://spec.edmcouncil.org/fibo/ontology/FND/Arrangements/Lifecycles/hasStage> ?stage."
            + "?stage <https://spec.edmcouncil.org/fibo/ontology/FND/Relations/Relations/exemplifies> <"
            + LifecycleEventType.SERVICE_EXECUTION.getStage() + ">;"
            + "<https://www.omg.org/spec/Commons/Collections/comprises> ?order_event."
            + "?order_event <https://spec.edmcouncil.org/fibo/ontology/FND/Relations/Relations/exemplifies> "
            + Rdf.iri(LifecycleResource.EVENT_ORDER_RECEIVED).getQueryString()
            + ";<https://spec.edmcouncil.org/fibo/ontology/FND/DatesAndTimes/Occurrences/hasEventDate> "
            + eventDatePlaceholderVar
            + ". BIND(xsd:date(" + eventDatePlaceholderVar + ") AS " + eventDateVar + ")"
            + filterDateStatement
            + eventIdVar + " <https://www.omg.org/spec/Commons/DatesAndTimes/succeeds>* ?order_event."
            + eventTargetStatements + filterContractStatement
            // Event must be the last in the chain ie no other events will succeed it
            + "MINUS{" + eventIdVar + " ^<https://www.omg.org/spec/Commons/DatesAndTimes/succeeds> ?any_event}");
    results.put(LifecycleResource.EVENT_KEY,
        eventIdVar + " <https://spec.edmcouncil.org/fibo/ontology/FND/Relations/Relations/exemplifies> "
            + eventVar + ".OPTIONAL{" + eventIdVar + " <https://www.omg.org/spec/Commons/Designators/describes> "
            + eventStatusVar + "}");
    results.put(LifecycleResource.LAST_MODIFIED_KEY, eventIdVar
        + "<https://spec.edmcouncil.org/fibo/ontology/FND/DatesAndTimes/Occurrences/hasEventDate> "
        + lastModifiedVar + ShaclResource.FULL_STOP);
    results.put(LifecycleResource.SCHEDULE_RECURRENCE_KEY, "OPTIONAL{?iri "
        + LifecycleResource.LIFECYCLE_STAGE_PREDICATE_PATH +
        "/<https://spec.edmcouncil.org/fibo/ontology/FND/DatesAndTimes/FinancialDates/hasSchedule>/<https://spec.edmcouncil.org/fibo/ontology/FND/DatesAndTimes/FinancialDates/hasRecurrenceInterval>/<https://www.omg.org/spec/Commons/DatesAndTimes/hasDurationValue> ?recurrences.}"
        + "BIND(IF(BOUND(?recurrences),?recurrences,\"\") AS "
        + QueryResource.SCHEDULE_RECURRENCE_VAR.getQueryString()
        + ")");
    return results;
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
    return QueryResource.PREFIX_TEMPLATE +
        "SELECT DISTINCT ?iri ?id WHERE{" +
        "?contract dc-terms:identifier \"" + contract + "\";" +
        "fibo-fnd-arr-lif:hasLifecycle/fibo-fnd-arr-lif:hasStage ?stage." +
        "?stage cmns-col:comprises ?event;" +
        "cmns-col:comprises ?iri." +
        "?event fibo-fnd-rel-rel:exemplifies <https://www.theworldavatar.com/kg/ontoservice/OrderReceivedEvent>;" +
        "fibo-fnd-dt-oc:hasEventDate ?date;" +
        "^cmns-dt:succeeds* ?iri." +
        "?iri dc-terms:identifier ?id;" +
        "fibo-fnd-rel-rel:exemplifies <" + eventType.getEvent() + ">." +
        dateFilter +
        "}";
  }

  /**
   * Retrieves the SPARQL query to retrieve the specific event occurrence instance
   * associated with the target event type based on the latest event id.
   * 
   * @param latestEventId The identifier of the latest event in the succeeds
   *                      chain.
   * @param eventType     The target event type to retrieve.
   */
  public String getContractEventQuery(String latestEventId, LifecycleEventType eventType) {
    Variable eventVar = SparqlBuilder.var(LifecycleResource.EVENT_KEY);
    return QueryResource.getSelectQuery(true, null).select(QueryResource.IRI_VAR, QueryResource.ID_VAR)
        .where(eventVar.has(QueryResource.DC_TERM_ID, Rdf.literalOf(latestEventId))
            .andHas(p -> p.pred(QueryResource.CMNS_DT_SUCCEEDS).zeroOrMore(), QueryResource.IRI_VAR))
        .where(QueryResource.IRI_VAR.has(QueryResource.DC_TERM_ID, QueryResource.ID_VAR)
            .andHas(p -> p.pred(QueryResource.FIBO_FND_REL_REL_EXEMPLIFIES), Rdf.iri(eventType.getEvent())))
        .getQueryString();
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
   * Generates a UPDATE query to update the contract event status.
   * 
   * @param id Target contract identifier.
   */
  public String genContractEventStatusUpdateQuery(String id) {
    Variable instance = QueryResource.genVariable(LifecycleResource.INSTANCE_KEY);
    TriplePattern eventStatusPattern = instance.has(
        QueryResource.CMNS_DSG_DESCRIBES,
        QueryResource.genVariable(LifecycleResource.EVENT_STATUS_KEY));
    ModifyQuery updateQuery = QueryResource.getUpdateQuery()
        .insert(instance.has(
            QueryResource.CMNS_DSG_DESCRIBES,
            Rdf.iri(LifecycleResource.EVENT_PENDING_STATUS)))
        .delete(eventStatusPattern)
        .where(QueryResource.IRI_VAR.has(QueryResource.DC_TERM_ID, Rdf.literalOf(id))
            .andHas(p -> p.pred(QueryResource.FIBO_FND_ARR_LIF_HAS_LIFECYCLE)
                .then(QueryResource.FIBO_FND_ARR_LIF_HAS_STAGE)
                .then(QueryResource.CMNS_COL_COMPRISES),
                instance),
            eventStatusPattern);
    return updateQuery.getQueryString();
  }

  /**
   * Generates lifecycle filter statements for SPARQL if required based on the
   * specified event.
   * 
   * @param lifecycleEvent Target event for filter.
   */
  public Map<String, String> genLifecycleFilterStatements(LifecycleEventType lifecycleEvent) {
    Map<String, String> statementMappings = new HashMap<>();
    // Generate schedule related mappings
    String recurrenceVar = QueryResource.genVariable(LifecycleResource.SCHEDULE_RECURRENCE_PLACEHOLDER_KEY)
        .getQueryString();
    statementMappings.putAll(SCHEDULE_QUERY_MAPPINGS);
    statementMappings.put(LifecycleResource.SCHEDULE_RECURRENCE_KEY,
        statementMappings.get(LifecycleResource.SCHEDULE_RECURRENCE_KEY) + "BIND(IF(BOUND("
            + recurrenceVar + ")," + recurrenceVar + ",\"\") AS "
            + QueryResource.SCHEDULE_RECURRENCE_VAR.getQueryString()
            + ")");
    StringBuilder coreQueryBuilder = new StringBuilder();
    switch (lifecycleEvent) {
      case LifecycleEventType.APPROVED:
        Variable creationVar = QueryResource.genVariable(LifecycleResource.EVENT_KEY);
        // Add last modified statement separately
        statementMappings.put(LifecycleResource.LAST_MODIFIED_KEY, creationVar
            .has(QueryResource.FIBO_FND_DT_OC_HAS_EVENT_DATE,
                QueryResource.LAST_MODIFIED_VAR)
            .getQueryString());
        // Continue with required lifecycle statements
        String creationStatement = QueryResource.IRI_VAR.has(p -> p.pred(QueryResource.FIBO_FND_ARR_LIF_HAS_LIFECYCLE)
            .then(QueryResource.FIBO_FND_ARR_LIF_HAS_STAGE)
            .then(QueryResource.CMNS_COL_COMPRISES), creationVar).getQueryString();
        String creationEventStatement = creationVar
            .has(QueryResource.FIBO_FND_REL_REL_EXEMPLIFIES, QueryResource.ONTOSERVICE.iri("ContractCreation"))
            .andHas(p -> p.pred(QueryResource.CMNS_DSG_DESCRIBES).then(RDFS.LABEL),
                QueryResource.genVariable(LocalisationTranslator.getMessage(LocalisationResource.VAR_STATUS_KEY)))
            .getQueryString();
        coreQueryBuilder.append(creationStatement).append(creationEventStatement);
        this.appendFilterExists(coreQueryBuilder, false, LifecycleResource.EVENT_APPROVAL);
        break;
      case LifecycleEventType.ACTIVE_SERVICE:
        this.appendFilterExists(coreQueryBuilder, true, LifecycleResource.EVENT_APPROVAL);
        this.appendArchivedFilterExists(coreQueryBuilder, false);
        break;
      case LifecycleEventType.ARCHIVE_COMPLETION:
        this.appendArchivedStateQuery(coreQueryBuilder);
        break;
      default:
        // Do nothing if it doesnt meet the above events
        break;
    }
    statementMappings.put(LifecycleResource.LIFECYCLE_RESOURCE, coreQueryBuilder.toString());
    return statementMappings;
  }

  /**
   * Insert and generate new mappings with the extended schedule filters for
   * lifecycle filter .
   * 
   * @param queryMappings Target mappings containing the existing statements.
   */
  public Map<String, String> insertExtendedScheduleFilters(Map<String, String> queryMappings) {
    Map<String, String> statementMappings = new HashMap<>(queryMappings);
    // Replaces all existing mappings with the same key to the extended version
    statementMappings.putAll(SCHEDULE_CONTRACT_FILTER_QUERY_MAPPINGS);
    LifecycleResource.convertVarForStrFilter(QueryResource.LAST_MODIFIED_VAR, queryMappings, statementMappings);
    return statementMappings;
  }

  /**
   * Update mappings with the extended task filters for lifecycle queries.
   * 
   * @param queryMappings Target mappings containing the existing statements.
   */
  public Map<String, String> insertExtendedTaskFilters(Map<String, String> queryMappings) {
    Map<String, String> statementMappings = new HashMap<>(queryMappings);
    // Replaces all existing mappings with the same key to the extended version
    LifecycleResource.convertVarForStrFilter(QueryResource.LAST_MODIFIED_VAR, queryMappings, statementMappings);
    return statementMappings;
  }

  /**
   * Appends a query statement to retrieve the status of an archived contract.
   * 
   * @param query Builder for the query template.
   */
  private void appendArchivedStateQuery(StringBuilder query) {
    Variable eventVar = QueryResource.genVariable(LifecycleResource.EVENT_KEY);
    TriplePattern eventPattern = QueryResource.IRI_VAR.has(p -> p.pred(QueryResource.FIBO_FND_ARR_LIF_HAS_LIFECYCLE)
        .then(QueryResource.FIBO_FND_ARR_LIF_HAS_STAGE)
        .then(QueryResource.CMNS_COL_COMPRISES)
        .then(QueryResource.FIBO_FND_REL_REL_EXEMPLIFIES), eventVar);
    String statement = "BIND("
        + "IF(" + eventVar.getQueryString() + "="
        + Rdf.iri(LifecycleResource.EVENT_CONTRACT_COMPLETION).getQueryString()
        + ",\"Completed\","
        + "IF(" + eventVar.getQueryString() + "="
        + Rdf.iri(LifecycleResource.EVENT_CONTRACT_RESCISSION).getQueryString()
        + ",\"Rescinded\","
        + "IF(" + eventVar.getQueryString() + "="
        + Rdf.iri(LifecycleResource.EVENT_CONTRACT_TERMINATION).getQueryString()
        + ",\"Terminated\""
        + ",\"Unknown\"))) AS ?" + LifecycleResource.STATUS_KEY
        + ")"
        + "FILTER(?" + LifecycleResource.STATUS_KEY + "!=\"Unknown\")";
    query.append(eventPattern.getQueryString())
        .append(statement);
  }

  /**
   * Appends FILTER EXISTS or MINUS statements for an archived contract.
   * 
   * @param query  Builder for the query template.
   * @param exists Indicate if using FILTER EXISTS or MINUS.
   */
  private void appendArchivedFilterExists(StringBuilder query, boolean exists) {
    Variable stageVar = QueryResource.genVariable(LifecycleResource.STAGE_KEY + "_archived");
    TriplePattern pattern = QueryResource.IRI_VAR.has(p -> p.pred(QueryResource.FIBO_FND_ARR_LIF_HAS_LIFECYCLE)
        .then(QueryResource.FIBO_FND_ARR_LIF_HAS_STAGE), stageVar)
        .andHas(QueryResource.FIBO_FND_REL_REL_EXEMPLIFIES, Rdf.iri(LifecycleEventType.ARCHIVE_COMPLETION.getStage()))
        .andHas(QueryResource.CMNS_COL_COMPRISES, QueryResource.genVariable(LifecycleResource.EVENT_KEY));
    GraphPatternNotTriples filterClause = QueryResource.genFilterExists(pattern, exists);
    query.append(filterClause.getQueryString());
  }

  /**
   * Appends FILTER EXISTS or MINUS statements for the specified object
   * instance.
   * 
   * @param query    Builder for the query template.
   * @param exists   Indicate if using FILTER EXISTS or MINUS.
   * @param instance Target IRI instance. Typically the object in a triple.
   */
  private void appendFilterExists(StringBuilder query, boolean exists, String instance) {
    TriplePattern pattern = QueryResource.IRI_VAR.has(p -> p.pred(QueryResource.FIBO_FND_ARR_LIF_HAS_LIFECYCLE)
        .then(QueryResource.FIBO_FND_ARR_LIF_HAS_STAGE)
        .then(QueryResource.CMNS_COL_COMPRISES)
        .then(QueryResource.FIBO_FND_REL_REL_EXEMPLIFIES), Rdf.iri(instance));
    GraphPatternNotTriples filterClause = QueryResource.genFilterExists(pattern, exists);
    query.append(filterClause.getQueryString());
  }
}
