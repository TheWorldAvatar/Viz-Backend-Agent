package com.cmclinnovations.agent.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.rdf4j.model.vocabulary.XSD;
import org.eclipse.rdf4j.sparqlbuilder.constraint.Expression;
import org.eclipse.rdf4j.sparqlbuilder.constraint.Expressions;
import org.eclipse.rdf4j.sparqlbuilder.constraint.SparqlFunction;
import org.eclipse.rdf4j.sparqlbuilder.core.Prefix;
import org.eclipse.rdf4j.sparqlbuilder.core.PrefixDeclarations;
import org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder;
import org.eclipse.rdf4j.sparqlbuilder.core.Variable;
import org.eclipse.rdf4j.sparqlbuilder.core.query.ModifyQuery;
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries;
import org.eclipse.rdf4j.sparqlbuilder.core.query.SelectQuery;
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPatternNotTriples;
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPatterns;
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.TriplePattern;
import org.eclipse.rdf4j.sparqlbuilder.rdf.Iri;
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf;
import org.springframework.http.MediaType;

import com.cmclinnovations.agent.component.LocalisationTranslator;

public class QueryResource {
    public static final MediaType JSON_MEDIA_TYPE = MediaType.valueOf("application/json");
    public static final MediaType LD_JSON_MEDIA_TYPE = MediaType.valueOf("application/ld+json");
    public static final MediaType SPARQL_MEDIA_TYPE = MediaType.valueOf("application/sparql-query");
    public static final MediaType TTL_MEDIA_TYPE = MediaType.valueOf("text/turtle");

    public static final Prefix CMNS_COL = genPrefix("cmns-col", "https://www.omg.org/spec/Commons/Collections/");
    public static final Prefix CMNS_DT = genPrefix("cmns-dt", "https://www.omg.org/spec/Commons/DatesAndTimes/");
    public static final Prefix CMNS_DSG = genPrefix("cmns-dsg", "https://www.omg.org/spec/Commons/Designators/");
    public static final Prefix CMNS_QTU = genPrefix("cmns-qtu",
            "https://www.omg.org/spec/Commons/QuantitiesAndUnits/");
    public static final Prefix CMNS_RLCMP = genPrefix("cmns-rlcmp",
            "https://www.omg.org/spec/Commons/RolesAndCompositions/");
    public static final Prefix DC_TERM = genPrefix("dc-terms", "http://purl.org/dc/terms/");
    public static final Prefix FIBO_FND_ACC_CUR = genPrefix("fibo-fnd-acc-cur",
            "https://spec.edmcouncil.org/fibo/ontology/FND/Accounting/CurrencyAmount/");
    public static final Prefix FIBO_FND_ARR_ID = genPrefix("fibo-fnd-arr-id",
            "https://spec.edmcouncil.org/fibo/ontology/FND/Arrangements/IdentifiersAndIndices/");
    public static final Prefix FIBO_FND_ARR_LIF = genPrefix("fibo-fnd-arr-lif",
            "https://spec.edmcouncil.org/fibo/ontology/FND/Arrangements/Lifecycles/");
    public static final Prefix FIBO_FND_ARR_REP = genPrefix("fibo-fnd-arr-rep",
            "https://spec.edmcouncil.org/fibo/ontology/FND/Arrangements/Reporting/");
    public static final Prefix FIBO_FND_PLC_ADR = genPrefix("fibo-fnd-plc-adr",
            "https://spec.edmcouncil.org/fibo/ontology/FND/Places/Addresses/");
    public static final Prefix FIBO_FND_PLC_LOC = genPrefix("fibo-fnd-plc-loc",
            "https://spec.edmcouncil.org/fibo/ontology/FND/Places/Locations/");
    public static final Prefix FIBO_FND_DT_FD = genPrefix("fibo-fnd-dt-fd",
            "https://spec.edmcouncil.org/fibo/ontology/FND/DatesAndTimes/FinancialDates/");
    public static final Prefix FIBO_FND_DT_OC = genPrefix("fibo-fnd-dt-oc",
            "https://spec.edmcouncil.org/fibo/ontology/FND/DatesAndTimes/Occurrences/");
    public static final Prefix FIBO_FND_REL_REL = genPrefix("fibo-fnd-rel-rel",
            "https://spec.edmcouncil.org/fibo/ontology/FND/Relations/Relations/");
    public static final Prefix FIBO_FND_PAS_PAS = genPrefix("fibo-fnd-pas-pas",
            "https://spec.edmcouncil.org/fibo/ontology/FND/ProductsAndServices/ProductsAndServices/");
    public static final Prefix FIBO_FND_PAS_PSCH = genPrefix("fibo-fnd-pas-psch",
            "https://spec.edmcouncil.org/fibo/ontology/FND/ProductsAndServices/PaymentsAndSchedules/");
    public static final Prefix GEO = genPrefix("geo", "http://www.opengis.net/ont/geosparql#");
    public static final Prefix ONTOSERVICE = genPrefix("ontoservice",
            "https://www.theworldavatar.com/kg/ontoservice/");
    public static final Prefix XSD_PREFIX = genPrefix(XSD.PREFIX, XSD.NAMESPACE);
    public static final String PREFIX_TEMPLATE = genPrefixTemplate().getQueryString();

    public static final Iri CMNS_COL_COMPRISES = CMNS_COL.iri("comprises");
    public static final Iri CMNS_DSG_DESCRIBES = CMNS_DSG.iri("describes");
    public static final Iri CMNS_DT_HAS_DATE_VALUE = CMNS_DT.iri("hasDateValue");
    public static final Iri CMNS_DT_HAS_TIME_PERIOD = CMNS_DT.iri("hasTimePeriod");
    public static final Iri CMNS_DT_SUCCEEDS = CMNS_DT.iri("succeeds");
    public static final Iri DC_TERM_ID = DC_TERM.iri("identifier");
    public static final Iri FIBO_FND_ARR_LIF_HAS_LIFECYCLE = FIBO_FND_ARR_LIF.iri("hasLifecycle");
    public static final Iri FIBO_FND_ARR_LIF_HAS_STAGE = FIBO_FND_ARR_LIF.iri("hasStage");
    public static final Iri FIBO_FND_DT_FD_HAS_SCHEDULE = FIBO_FND_DT_FD.iri("hasSchedule");
    public static final Iri FIBO_FND_DT_OC_HAS_EVENT_DATE = FIBO_FND_DT_OC.iri("hasEventDate");
    public static final Iri FIBO_FND_REL_REL_EXEMPLIFIES = FIBO_FND_REL_REL.iri("exemplifies");

    public static final String NULL_KEY = "null";
    public static final String IRI_KEY = "iri";
    public static final Variable IRI_VAR = SparqlBuilder.var(IRI_KEY);
    public static final String ID_KEY = "id";
    public static final Variable ID_VAR = SparqlBuilder.var(ID_KEY);
    public static final Variable DATE_VAR = QueryResource.genVariable(LifecycleResource.DATE_KEY);
    public static final Variable EVENT_ID_VAR = QueryResource.genVariable(LifecycleResource.EVENT_ID_KEY);
    public static final Variable EVENT_STATUS_VAR = QueryResource.genVariable(LifecycleResource.EVENT_STATUS_KEY);
    public static final Variable LAST_MODIFIED_VAR = QueryResource.genVariable(LifecycleResource.LAST_MODIFIED_KEY);
    public static final Variable SCHEDULE_START_DATE_VAR = QueryResource
            .genVariable(LifecycleResource.SCHEDULE_START_DATE_KEY);
    public static final Variable SCHEDULE_END_DATE_VAR = QueryResource
            .genVariable(LifecycleResource.SCHEDULE_END_DATE_KEY);
    public static final Variable SCHEDULE_START_TIME_VAR = QueryResource
            .genVariable(LifecycleResource.SCHEDULE_START_TIME_KEY);
    public static final Variable SCHEDULE_END_TIME_VAR = QueryResource
            .genVariable(LifecycleResource.SCHEDULE_END_TIME_KEY);
    public static final Variable SCHEDULE_RECURRENCE_VAR = QueryResource
            .genVariable(LifecycleResource.SCHEDULE_RECURRENCE_KEY);
    public static final Variable LATEST_DATE_VAR = QueryResource.genVariable("latest_date");

    public static final String ADD_BRANCH_KEY = "branch_add";
    public static final String DELETE_BRANCH_KEY = "branch_delete";
    public static final String FIXED_DATE_DATE_KEY = "entry_date";
    public static final String FIXED_DATE_SCHEDULE_KEY = "schedule entry";
    public static final String FIXED_DATE_SCHEDULE_DATE_KEY = "schedule entry date";

    // Private constructor to prevent instantiation
    private QueryResource() {
        throw new UnsupportedOperationException("This class cannot be instantiated!");
    }

    /**
     * Generates a PREFIX object.
     * 
     * @param alias The alias of the PREFIX.
     * @param iri   The associated iri.
     */
    public static Prefix genPrefix(String alias, String iri) {
        return SparqlBuilder.prefix(alias, Rdf.iri(iri));
    }

    /**
     * Generates a Variable object, where the variable name has no white spaces.
     * 
     * @param varName The variable name.
     */
    public static Variable genVariable(String varName) {
        return SparqlBuilder.var(varName.replaceAll("\\s+", "_"));
    }

    /**
     * Generates an empty DELETE query template.
     */
    public static ModifyQuery getDeleteQuery() {
        return Queries.DELETE()
                .prefix(DC_TERM);
    }

    /**
     * Generates an empty SELECT query template with no DISTINCT or LIMIT modifiers.
     */
    public static SelectQuery getSelectQuery() {
        return getSelectQuery(false, null);
    }

    /**
     * Generates an empty SELECT query template with DISTNCT and LIMIT modifier.
     * 
     * @param isDistinct Indicates if the select query requires
     *                   the DISTINCT modifier.
     * @param limit      Indicates if the select query requires LIMIT modifier.
     */
    public static SelectQuery getSelectQuery(boolean isDistinct, Integer limit) {
        SelectQuery query = Queries.SELECT()
                .distinct(isDistinct)
                .prefix(genPrefixTemplate())
                .where();
        if (limit != null) {
            query.limit(limit);
        }
        return query;
    }

    /**
     * Generates an empty INSERT DELETE WHERE query template.
     */
    public static ModifyQuery getUpdateQuery() {
        return Queries.INSERT()
                .prefix(genPrefixTemplate());
    }

    /**
     * Generates a filter exists or minus graph pattern over the triple contents.
     * 
     * @param tripleContents The target triple pattern to be added.
     * @param exists         Set FILTER EXISTS if true. Else, uses MINUS.
     */
    public static GraphPatternNotTriples genFilterExists(TriplePattern tripleContents, boolean exists) {
        if (exists) {
            return GraphPatterns.filterExists(tripleContents);
        }
        return GraphPatterns.minus(tripleContents);
    }

    /**
     * Generates an expression that matches the literal value's string to its
     * variable in lowercase. It is intended to pass this expression into a filter
     * clause.
     * 
     * @param variable     The target variable for filtering.
     * @param literalValue The literal value that should be matched.
     */
    public static Expression<?> genLowercaseExpression(Variable variable, String literalValue) {
        return Expressions.equals(
                Expressions.function(SparqlFunction.LCASE, variable), Rdf.literalOf(literalValue));
    }

    /**
     * Wraps the query statements into a MINUS clause.
     * 
     * @param queryStatements Query statements to be added to minus.
     */
    public static String minus(String queryStatements) {
        return "MINUS{" + queryStatements + "}";

    }

    /**
     * Wraps the query statements into an OPTIONAL clause. Clause will not be
     * appended if no statements are given.
     * 
     * @param queryStatements Query statements to be added.
     */
    public static String optional(String queryStatements) {
        if (queryStatements.isEmpty()) {
            return queryStatements;
        }
        return "OPTIONAL{" + queryStatements + "}";
    }

    /**
     * Generate a FILTER clause with multiple expressions separated by OR.
     * 
     * @param firstExpression The first expression.
     * @param expressions     The subsequent set of expressions.
     */
    public static String filter(String firstExpression, String... expressions) {
        StringBuilder filterBuilder = new StringBuilder("FILTER(");
        filterBuilder.append(firstExpression);
        for (String currentExpression : expressions) {
            filterBuilder.append("||").append(currentExpression);
        }
        filterBuilder.append(")");
        return filterBuilder.toString();
    }

    /**
     * Generate an UNION clause between several set of query statements
     * 
     * @param firstStatements The first set of statements.
     * @param statements      The subsequent set of statements to be placed into
     *                        separate UNION clauses.
     */
    public static String union(String firstStatements, String... statements) {
        StringBuilder unionBuilder = new StringBuilder();
        unionBuilder.append("{").append(firstStatements).append("}");
        for (String currentStatements : statements) {
            unionBuilder.append("UNION{").append(currentStatements).append("}");
        }
        return unionBuilder.toString();
    }

    /**
     * Generate a VALUES clause for the specific field and values
     * 
     * @param field  The field of interest.
     * @param values The values to be inserted into the VALUES clause.
     */
    public static String values(String field, Collection<String> values) {
        StringBuilder valuesBuilder = new StringBuilder();
        valuesBuilder.append("VALUES ?")
                .append(field)
                .append(" {");
        values.forEach(value -> {
            valuesBuilder.append(ShaclResource.WHITE_SPACE)
                    .append(value);
        });
        valuesBuilder.append("}");
        return valuesBuilder.toString();
    }

    /**
     * Generates query statements for filtering targets based on the filters if
     * available using VALUES clause.
     * 
     * @param query   The query to be added.
     * @param field   The field of interest.
     * @param filters The list of filter values to target by.
     * @param builder Stores the output.
     */
    public static void genFilterStatements(String query, String field, Set<String> filters, StringBuilder builder) {
        // Special parsing for recurrence/schedule type
        if (field.equals(LifecycleResource.SCHEDULE_RECURRENCE_KEY)) {
            builder.append(query); // Append general query
            boolean hasRegularService = filters.stream()
                    .anyMatch(scheduleType -> scheduleType.substring(1, scheduleType.length() - 1).equals(
                            LocalisationTranslator.getMessage(LocalisationResource.LABEL_REGULAR_SERVICE_KEY)));
            Set<String> parsedFilters = filters.stream()
                    // Filter out regular service
                    .filter(scheduleType -> !scheduleType.substring(1, scheduleType.length() - 1).equals(
                            LocalisationTranslator.getMessage(LocalisationResource.LABEL_REGULAR_SERVICE_KEY)))
                    .map(scheduleType -> {
                        String scheduleTypeContent = scheduleType.substring(1, scheduleType.length() - 1);
                        if (scheduleTypeContent.equals(
                                LocalisationTranslator.getMessage(LocalisationResource.LABEL_PERPETUAL_SERVICE_KEY))) {
                            return LifecycleResource.EMPTY_STRING;
                        } else if (scheduleTypeContent
                                .equals(LocalisationTranslator
                                        .getMessage(LocalisationResource.LABEL_SINGLE_SERVICE_KEY))) {
                            return LifecycleResource.RECURRENCE_DAILY_TASK_STRING;
                        } else if (scheduleTypeContent.equals(
                                LocalisationTranslator
                                        .getMessage(LocalisationResource.LABEL_ALTERNATE_DAY_SERVICE_KEY))) {
                            return LifecycleResource.RECURRENCE_ALT_DAY_TASK_STRING;
                        } else if (scheduleTypeContent.equals(
                                LocalisationTranslator
                                        .getMessage(LocalisationResource.LABEL_FIXED_DATE_SERVICE_KEY))) {
                            return LifecycleResource.RECURRENCE_FIXED_DATE_TASK_STRING;
                        }
                        return scheduleType;
                    }).collect(Collectors.toSet());
            if (hasRegularService) {
                // Remove the negation if the filter is present
                Map<String, String> negationMappings = new HashMap<>(LifecycleResource.NEGATE_RECURRENCE_MAP);
                parsedFilters.forEach(filter -> negationMappings.remove(filter));
                builder.append("FILTER(").append(negationMappings.values().stream().collect(Collectors.joining("&&")))
                        .append(")");
            } else {
                String valuesClause = QueryResource.values(field, parsedFilters);
                builder.append(valuesClause);
            }
            // Special parsing for events at task level
        } else if (field.equals(LifecycleResource.EVENT_KEY)) {
            builder.append(query);
            Set<String> parsedFilters = filters.stream()
                    .map(eventStatus -> {
                        String eventStatusContent = eventStatus.substring(1, eventStatus.length() - 1);
                        return LocalisationTranslator.getEventFromLocalisedEventKey(eventStatusContent);
                    }).collect(Collectors.toSet());
            String valuesClause = QueryResource.values(field, parsedFilters);
            builder.append(valuesClause);
        } else {
            // When there are null filter values, the user has requested for blank values,
            // and this should be excluded from the query via a MINUS clause
            if (filters.contains(QueryResource.NULL_KEY)) {
                String minusStatement = QueryResource.minus(query);
                // If there is only one null filter, this should merely be a MINUS clause
                if (filters.size() == 1) {
                    builder.append(minusStatement);
                } else {
                    // When there are multiple filters, MINUS and default clause with values should
                    // be provided; Remove the null key before generating the VALUES clause
                    filters.remove(QueryResource.NULL_KEY);
                    String valuesClause = QueryResource.values(field, filters);
                    builder.append(QueryResource.union(minusStatement, query + valuesClause));
                }
            } else {
                // For default filters, add clause to restrict them
                // But only add VALUES if they are available
                builder.append(query);
                if (!filters.isEmpty()) {
                    String valuesClause = QueryResource.values(field, filters);
                    builder.append(valuesClause);
                }
            }
        }
    }

    /**
     * Generates a FILTER clause for filtering targets based on the filters if
     * available using the FILTER expression.
     * 
     * @param field   The field of interest.
     * @param filters The list of filter values to target by.
     */
    public static String filterOrExpressions(String field, Set<String> filters) {
        StringBuilder builder = new StringBuilder();
        if (!filters.isEmpty()) {
            List<String> expressions = new ArrayList<>();
            if (filters.contains(QueryResource.NULL_KEY)) {
                filters.remove(QueryResource.NULL_KEY);
                expressions.add("!BOUND(?" + field + ")");
            }
            // On removing null, verify if there are still other filters
            if (!filters.isEmpty()) {
                filters.forEach(filter -> {
                    if (!filter.isEmpty()) {
                        expressions.add("?" + field + "=" + filter);
                    }
                });
            }
            String filterClause;
            if (expressions.size() == 1) {
                filterClause = QueryResource.filter(expressions.get(0));
            } else {
                filterClause = QueryResource.filter(expressions.get(0),
                        expressions.stream()
                                .skip(1)
                                .toArray(String[]::new));
            }
            builder.append(filterClause);
        }
        return builder.toString();
    }

    /**
     * Generates a PREFIX template for the queries.
     */
    private static PrefixDeclarations genPrefixTemplate() {
        return SparqlBuilder.prefixes(
                CMNS_COL,
                CMNS_DT,
                CMNS_DSG,
                CMNS_QTU,
                CMNS_RLCMP,
                DC_TERM,
                FIBO_FND_ACC_CUR,
                FIBO_FND_ARR_ID,
                FIBO_FND_ARR_LIF,
                FIBO_FND_ARR_REP,
                FIBO_FND_PLC_ADR,
                FIBO_FND_PLC_LOC,
                FIBO_FND_DT_FD,
                FIBO_FND_DT_OC,
                FIBO_FND_REL_REL,
                FIBO_FND_PAS_PAS,
                FIBO_FND_PAS_PSCH,
                GEO,
                ONTOSERVICE,
                XSD_PREFIX);
    }
}