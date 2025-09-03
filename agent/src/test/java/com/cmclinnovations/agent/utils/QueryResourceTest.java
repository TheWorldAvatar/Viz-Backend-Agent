package com.cmclinnovations.agent.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.eclipse.rdf4j.sparqlbuilder.core.Prefix;
import org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder;
import org.eclipse.rdf4j.sparqlbuilder.core.Variable;
import org.junit.jupiter.api.Test;

public class QueryResourceTest {
    private static final String TEST_VAR_NAME = "test";
    private static final Variable TEST_VAR = SparqlBuilder.var(TEST_VAR_NAME);

    @Test
    void testPrefixTemplate() {
        String expectedPrefixes = "PREFIX cmns-col: <https://www.omg.org/spec/Commons/Collections/>\n" +
                "PREFIX cmns-dt: <https://www.omg.org/spec/Commons/DatesAndTimes/>\n" +
                "PREFIX cmns-dsg: <https://www.omg.org/spec/Commons/Designators/>\n" +
                "PREFIX cmns-qtu: <https://www.omg.org/spec/Commons/QuantitiesAndUnits/>\n" +
                "PREFIX cmns-rlcmp: <https://www.omg.org/spec/Commons/RolesAndCompositions/>\n" +
                "PREFIX dc-terms: <http://purl.org/dc/terms/>\n" +
                "PREFIX fibo-fnd-acc-cur: <https://spec.edmcouncil.org/fibo/ontology/FND/Accounting/CurrencyAmount/>\n"
                +
                "PREFIX fibo-fnd-arr-id: <https://spec.edmcouncil.org/fibo/ontology/FND/Arrangements/IdentifiersAndIndices/>\n"
                +
                "PREFIX fibo-fnd-arr-lif: <https://spec.edmcouncil.org/fibo/ontology/FND/Arrangements/Lifecycles/>\n"
                +
                "PREFIX fibo-fnd-arr-rep: <https://spec.edmcouncil.org/fibo/ontology/FND/Arrangements/Reporting/>\n" +
                "PREFIX fibo-fnd-plc-adr: <https://spec.edmcouncil.org/fibo/ontology/FND/Places/Addresses/>\n" +
                "PREFIX fibo-fnd-plc-loc: <https://spec.edmcouncil.org/fibo/ontology/FND/Places/Locations/>\n" +
                "PREFIX fibo-fnd-dt-fd: <https://spec.edmcouncil.org/fibo/ontology/FND/DatesAndTimes/FinancialDates/>\n"
                +
                "PREFIX fibo-fnd-dt-oc: <https://spec.edmcouncil.org/fibo/ontology/FND/DatesAndTimes/Occurrences/>\n"
                +
                "PREFIX fibo-fnd-rel-rel: <https://spec.edmcouncil.org/fibo/ontology/FND/Relations/Relations/>\n" +
                "PREFIX fibo-fnd-pas-pas: <https://spec.edmcouncil.org/fibo/ontology/FND/ProductsAndServices/ProductsAndServices/>\n"
                +
                "PREFIX fibo-fnd-pas-psch: <https://spec.edmcouncil.org/fibo/ontology/FND/ProductsAndServices/PaymentsAndSchedules/>\n"
                +
                "PREFIX geo: <http://www.opengis.net/ont/geosparql#>\n" +
                "PREFIX ontoservice: <https://www.theworldavatar.com/kg/ontoservice/>\n" +
                "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>";
        assertEquals(expectedPrefixes, QueryResource.PREFIX_TEMPLATE);
    }

    @Test
    void testGenPrefix() {
        String testNamespace = "http://www.example.org/test/";
        Prefix sample = QueryResource.genPrefix(TEST_VAR_NAME, testNamespace);
        assertEquals("PREFIX " + TEST_VAR_NAME + ": <" + testNamespace + ">", sample.getQueryString());
    }

    @Test
    void testGenSelectQuery() {
        String testClass = "Test";
        String selectQuery = QueryResource.genSelectQuery(TEST_VAR.isA(QueryResource.FIBO_FND_PLC_ADR.iri(testClass)),
                true, TEST_VAR);
        String expected = "SELECT DISTINCT ?test\n" +
                "WHERE { ?test a fibo-fnd-plc-adr:Test . }\n";
        assertEquals(QueryResource.PREFIX_TEMPLATE + expected, selectQuery);
    }
}
