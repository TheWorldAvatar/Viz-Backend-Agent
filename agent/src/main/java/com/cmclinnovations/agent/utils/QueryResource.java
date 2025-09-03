package com.cmclinnovations.agent.utils;

import org.eclipse.rdf4j.model.vocabulary.XSD;
import org.eclipse.rdf4j.sparqlbuilder.constraint.Expression;
import org.eclipse.rdf4j.sparqlbuilder.constraint.Expressions;
import org.eclipse.rdf4j.sparqlbuilder.constraint.SparqlFunction;
import org.eclipse.rdf4j.sparqlbuilder.core.Prefix;
import org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder;
import org.eclipse.rdf4j.sparqlbuilder.core.Variable;
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries;
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPattern;
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
    public static final String PREFIX_TEMPLATE = genPrefixTemplate();

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
     * Generates a SELECT query from the inputs.
     * 
     * @param whereClause The where clause body
     * @param isDistinct  Indicates if the select query requires distinct variables
     * @param selectVars  The list of select variables to be queried from the where
     *                    clause
     */
    public static String genSelectQuery(GraphPattern whereClause, boolean isDistinct, Variable... selectVars) {
        return PREFIX_TEMPLATE +
                Queries.SELECT(selectVars)
                        .distinct(isDistinct)
                        .where(whereClause)
                        .getQueryString();
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
    private static String genPrefixTemplate() {
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
                XSD_PREFIX)
                .getQueryString();
    }
}