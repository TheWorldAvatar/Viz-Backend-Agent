package com.cmclinnovations.agent.template;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.rdf4j.model.vocabulary.RDFS;
import org.eclipse.rdf4j.sparqlbuilder.core.Variable;
import org.eclipse.rdf4j.sparqlbuilder.core.query.ModifyQuery;
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPatternNotTriples;
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.TriplePattern;
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf;

import com.cmclinnovations.agent.model.type.LifecycleEventType;
import com.cmclinnovations.agent.utils.LifecycleResource;
import com.cmclinnovations.agent.utils.QueryResource;
import com.cmclinnovations.agent.utils.ShaclResource;

public class LifecycleQueryFactory {
  private static final String CLOSED_QUERY_STATEMENTS = "{?iri fibo-fnd-rel-rel:exemplifies ontoservice:TerminatedServiceEvent .}UNION"
      + "{?iri fibo-fnd-rel-rel:exemplifies ontoservice:IncidentReportEvent .}UNION"
      + "{?iri fibo-fnd-rel-rel:exemplifies ontoservice:ServiceDeliveryEvent ; cmns-dsg:describes ontoservice:CompletedStatus .}";
  private static final String UNCLOSED_QUERY_STATEMENTS = "{?iri fibo-fnd-rel-rel:exemplifies ontoservice:OrderReceivedEvent .}UNION"
      + "{?iri fibo-fnd-rel-rel:exemplifies ontoservice:ServiceDispatchEvent .}UNION"
      + "{?iri fibo-fnd-rel-rel:exemplifies ontoservice:ServiceDeliveryEvent ; cmns-dsg:describes ontoservice:PendingStatus .}";
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
   * Retrieves the SPARQL query to get the date of the last order generated for
   * active contracts.
   * 
   * @param taskGenerationCutoffDate The cutoff date for generating further tasks.
   */
  public String getLatestOrderQuery(String taskGenerationCutoffDate) {
    StringBuilder activeFilter = new StringBuilder();
    this.appendFilterExists(activeFilter, true, LifecycleResource.EVENT_APPROVAL);
    this.appendArchivedFilterExists(activeFilter, false);
    String latestDateVar = QueryResource.LATEST_DATE_VAR.getQueryString();
    return QueryResource.PREFIX_TEMPLATE
        + "SELECT DISTINCT ?id (MAX(?date) AS " + latestDateVar + ") WHERE{"
        + "?iri fibo-fnd-arr-lif:hasLifecycle/fibo-fnd-arr-lif:hasStage ?stage;"
        + "dc-terms:identifier ?id."
        // Nested query for all days
        + "?stage fibo-fnd-rel-rel:exemplifies <"
        + LifecycleEventType.SERVICE_EXECUTION.getStage() + ">;"
        // not optional, so it would ignore perpetual service
        + "<https://www.omg.org/spec/Commons/PartiesAndSituations/holdsDuring>/cmns-dt:hasEndDate/cmns-dt:hasDateValue ?end_date;"
        + "<https://www.omg.org/spec/Commons/Collections/comprises> ?order_event."
        + "?order_event <https://spec.edmcouncil.org/fibo/ontology/FND/Relations/Relations/exemplifies> "
        + Rdf.iri(LifecycleResource.EVENT_ORDER_RECEIVED).getQueryString()
        + ";<https://spec.edmcouncil.org/fibo/ontology/FND/DatesAndTimes/Occurrences/hasEventDate> ?event_date . "
        + "BIND(xsd:date(?event_date) AS ?date)"
        + activeFilter
        + "}"
        + "GROUP BY ?id ?end_date"
        + "HAVING (" + latestDateVar + " < \"" + taskGenerationCutoffDate + "\"^^xsd:date && ?end_date > "
        + latestDateVar + ")";
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
      eventTargetStatements = CLOSED_QUERY_STATEMENTS.replace(QueryResource.IRI_KEY,
          QueryResource.EVENT_ID_VAR.getVarName());
    } else {
      eventTargetStatements = UNCLOSED_QUERY_STATEMENTS.replace(QueryResource.IRI_KEY,
          QueryResource.EVENT_ID_VAR.getVarName());
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
        eventIdVar + " <https://spec.edmcouncil.org/fibo/ontology/FND/Relations/Relations/exemplifies> ?temp_event."
            + "OPTIONAL{" + eventIdVar + " <https://www.omg.org/spec/Commons/Designators/describes> " + eventStatusVar
            + "} BIND(CONCAT(STR(?temp_event),IF(BOUND(?event_status),CONCAT(\";\",STR(?event_status)),\"\")) AS "
            + eventVar + ")");
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
   * Generates a UPDATE query to update the contract event status.
   * 
   * @param id Target contract identifier.
   */
  public String genContractEventStatusUpdateQuery(String id) {
    Variable instance = QueryResource.genVariable(LifecycleResource.INSTANCE_KEY);
    TriplePattern eventStatusPattern = instance.has(
        QueryResource.CMNS_DSG_DESCRIBES,
        QueryResource.EVENT_STATUS_VAR);
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
                QueryResource.genVariable(LifecycleResource.STATUS_KEY))
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
    Map<String, String> statementMappings = this.insertExtendedLastModifiedFilters(queryMappings);
    statementMappings.putAll(SCHEDULE_CONTRACT_FILTER_QUERY_MAPPINGS);
    return statementMappings;
  }

  /**
   * Update mappings with the extended filters for last modified query statements.
   * 
   * @param queryMappings Target mappings containing the existing statements.
   */
  public Map<String, String> insertExtendedLastModifiedFilters(Map<String, String> queryMappings) {
    Map<String, String> statementMappings = new HashMap<>(queryMappings);
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
