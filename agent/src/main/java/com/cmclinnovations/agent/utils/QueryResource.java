package com.cmclinnovations.agent.utils;

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
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPattern;
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPatternNotTriples;
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPatterns;
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.TriplePattern;
import org.eclipse.rdf4j.sparqlbuilder.rdf.Iri;
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf;

public class QueryResource {
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
    public static final Iri CMNS_DT_HAS_DATE_VALUE = CMNS_DT.iri("hasDateValue");
    public static final Iri CMNS_DT_HAS_TIME_PERIOD = CMNS_DT.iri("hasTimePeriod");
    public static final Iri DC_TERM_ID = DC_TERM.iri("identifier");
    public static final Iri FIBO_FND_ARR_LIF_HAS_LIFECYCLE = FIBO_FND_ARR_LIF.iri("hasLifecycle");
    public static final Iri FIBO_FND_ARR_LIF_HAS_STAGE = FIBO_FND_ARR_LIF.iri("hasStage");
    public static final Iri FIBO_FND_DT_FD_HAS_SCHEDULE = FIBO_FND_DT_FD.iri("hasSchedule");
    public static final Iri FIBO_FND_REL_REL_EXEMPLIFIES = FIBO_FND_REL_REL.iri("exemplifies");
    public static final Iri REPLACEMENT_PREDICATE = Rdf.iri("http://replacement/org/replace");

    public static final Variable ID_VAR = SparqlBuilder.var("id");
    public static final Variable IRI_VAR = SparqlBuilder.var("iri");

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
                .prefix(DC_TERM)
                .where();
    }

    /**
     * Generates an empty SELECT query template.
     */
    public static SelectQuery getSelectQuery() {
        return Queries.SELECT()
                .prefix(genPrefixTemplate())
                .where();
    }

    /**
     * Gents a SELECT query from the inputs.
     * 
     * @param whereClause The where clause body
     * @param isDistinct  Indicates if the select query requires distinct variables
     * @param limit       Indicates if the select query requires LIMIT modifier
     * @param selectVars  The list of select variables to be queried from the where
     *                    clause
     */
    public static String getSelectQuery(GraphPattern whereClause, boolean isDistinct, Integer limit,
            Variable... selectVars) {
        return genSelectQuery(whereClause, isDistinct, limit, selectVars)
                .getQueryString();
    }

    /**
     * Overloaded method to get a SELECT query from the inputs.
     * 
     * @param whereClause The where clause body
     * @param isDistinct  Indicates if the select query requires distinct variables
     * @param selectVars  The list of select variables to be queried from the where
     *                    clause
     */
    public static String getSelectQuery(GraphPattern whereClause, boolean isDistinct, Variable... selectVars) {
        return genSelectQuery(whereClause, isDistinct, null, selectVars)
                .getQueryString();
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

    /**
     * Generates a SELECT query from the inputs.
     * 
     * @param whereClause The where clause body
     * @param isDistinct  Indicates if the select query requires distinct variables
     * @param limit       Indicates if the select query requires LIMIT modifier
     * @param selectVars  The list of select variables to be queried from the where
     *                    clause
     */
    private static SelectQuery genSelectQuery(GraphPattern whereClause, boolean isDistinct, Integer limit,
            Variable... selectVars) {
        SelectQuery query = Queries.SELECT(selectVars)
                .distinct(isDistinct)
                .where(whereClause);
        if (limit != null) {
            query.limit(limit);
        }
        return query;
    }
}