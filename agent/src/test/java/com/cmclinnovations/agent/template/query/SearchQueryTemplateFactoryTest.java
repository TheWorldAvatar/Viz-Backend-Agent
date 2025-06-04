package com.cmclinnovations.agent.template.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.cmclinnovations.agent.TestUtils;
import com.cmclinnovations.agent.model.QueryTemplateFactoryParameters;
import com.cmclinnovations.agent.model.ShaclPropertyBindingTest;
import com.cmclinnovations.agent.model.ShaclPropertyBindingTest.SparqlBindingTestParameters;
import com.cmclinnovations.agent.model.SparqlBinding;
import com.cmclinnovations.agent.utils.StringResource;

public class SearchQueryTemplateFactoryTest {
    private static SearchQueryTemplateFactory TEMPLATE_FACTORY;

    public static final String EXPECTED_SIMPLE_FILE = "template/query/search/search_query_simple.sparql";
    public static final String EXPECTED_SIMPLE_MIXED_FILE = "template/query/search/search_query_simple_mixed.sparql";
    private static final String EXPECTED_SIMPLE_MINMAX_FILE = "template/query/search/search_query_simple_minmax.sparql";
    private static final String EXPECTED_SIMPLE_MINMAX_MIXED_FILE = "template/query/search/search_query_simple_minmax_mixed.sparql";
    private static final String EXPECTED_SIMPLE_MIN_FILE = "template/query/search/search_query_simple_min.sparql";
    private static final String EXPECTED_SIMPLE_MIN_MIXED_FILE = "template/query/search/search_query_simple_min_mixed.sparql";
    private static final String EXPECTED_SIMPLE_MAX_FILE = "template/query/search/search_query_simple_max.sparql";
    private static final String EXPECTED_SIMPLE_MAX_MIXED_FILE = "template/query/search/search_query_simple_max_mixed.sparql";
    private static final String SAMPLE_PREFIX = "http://example.com/";
    private static final String SAMPLE_CONCEPT = SAMPLE_PREFIX + "Concept";
    private static final String SAMPLE_PRED_PATH = SAMPLE_PREFIX + "propPath1";
    private static final String SAMPLE_NESTED_PRED_PATH = SAMPLE_PREFIX + "propPath2";
    private static final int SAMPLE_MIN_VAL = 0;
    private static final int SAMPLE_MAX_VAL = 10;
    public static final String SAMPLE_FIELD = "field";
    public static final String SAMPLE_FILTER = "01j82";

    private static final String NAME_VAR = "name";
    private static final String IS_OPTIONAL_VAR = "isoptional";
    private static final String MULTIPATH_VAR = "multipath";
    private static final String MULTISUBPATH_VAR = "multisubpath";

    @BeforeAll
    static void setup() {
        TEMPLATE_FACTORY = new SearchQueryTemplateFactory();
    }

    @Test
    void testWrite_SimpleNoCriteria() {
        // Set up
        Queue<Queue<SparqlBinding>> testBindings = initTestBindings();
        // Execute
        Queue<String> results = TEMPLATE_FACTORY.write(
                new QueryTemplateFactoryParameters(testBindings, new HashMap<>()));
        // Assert
        assertEquals(2, results.size());
        assertEquals(
                "SELECT DISTINCT ?iri WHERE {?iri a <http://example.com/Concept>.?iri <http://example.com/propPath1>/<http://example.com/propPath2> ?field.}",
                results.poll());
        assertEquals(
                "SELECT DISTINCT ?iri WHERE {?iri a/rdfs:subClassOf* <http://example.com/Concept>.?iri <http://example.com/propPath1>/<http://example.com/propPath2> ?field.}",
                results.poll());
    }

    @Test
    void testWrite_SimpleStringCriteria() throws IOException {
        // Set up
        Queue<Queue<SparqlBinding>> testBindings = initTestBindings();
        // Execute
        Queue<String> results = TEMPLATE_FACTORY.write(
                new QueryTemplateFactoryParameters(testBindings, genCriterias(SAMPLE_FIELD, SAMPLE_FILTER)));
        // Assert
        assertEquals(2, results.size());
        assertEquals(TestUtils.getSparqlQuery(EXPECTED_SIMPLE_FILE), results.poll());
        assertEquals(TestUtils.getSparqlQuery(EXPECTED_SIMPLE_MIXED_FILE), results.poll());
    }

    @Test
    void testWrite_SimpleMinMaxCriteria() throws IOException {
        // Set up
        Queue<Queue<SparqlBinding>> testBindings = initTestBindings();
        // Execute
        Queue<String> results = TEMPLATE_FACTORY.write(
                new QueryTemplateFactoryParameters(testBindings,
                        this.genMinMaxCriterias(SAMPLE_FIELD, SAMPLE_MIN_VAL, SAMPLE_MAX_VAL)));
        // Assert
        assertEquals(2, results.size());
        assertEquals(TestUtils.getSparqlQuery(EXPECTED_SIMPLE_MINMAX_FILE), results.poll());
        assertEquals(TestUtils.getSparqlQuery(EXPECTED_SIMPLE_MINMAX_MIXED_FILE), results.poll());
    }

    @Test
    void testWrite_SimpleMinCriteria() throws IOException {
        // Set up
        Queue<Queue<SparqlBinding>> testBindings = initTestBindings();
        // Execute
        Queue<String> results = TEMPLATE_FACTORY.write(
                new QueryTemplateFactoryParameters(testBindings,
                        this.genMinMaxCriterias(SAMPLE_FIELD, SAMPLE_MIN_VAL, null)));
        // Assert
        assertEquals(2, results.size());
        assertEquals(TestUtils.getSparqlQuery(EXPECTED_SIMPLE_MIN_FILE), results.poll());
        assertEquals(TestUtils.getSparqlQuery(EXPECTED_SIMPLE_MIN_MIXED_FILE), results.poll());
    }

    @Test
    void testWrite_SimpleMaxCriteria() throws IOException {
        // Set up
        Queue<Queue<SparqlBinding>> testBindings = initTestBindings();
        // Execute
        Queue<String> results = TEMPLATE_FACTORY.write(
                new QueryTemplateFactoryParameters(testBindings,
                        this.genMinMaxCriterias(SAMPLE_FIELD, null, SAMPLE_MAX_VAL)));
        // Assert
        assertEquals(2, results.size());
        assertEquals(TestUtils.getSparqlQuery(EXPECTED_SIMPLE_MAX_FILE), results.poll());
        assertEquals(TestUtils.getSparqlQuery(EXPECTED_SIMPLE_MAX_MIXED_FILE), results.poll());
    }

    /**
     * Initialise test bindings.
     */
    public static Queue<Queue<SparqlBinding>> initTestBindings() {
        Queue<Queue<SparqlBinding>> nestedBindings = new ArrayDeque<>();
        Queue<SparqlBinding> bindings = new ArrayDeque<>();
        SparqlBinding binding = ShaclPropertyBindingTest
                .genMockSparqlBinding(new SparqlBindingTestParameters(SAMPLE_FIELD, SAMPLE_CONCEPT, null, null,
                        SAMPLE_PRED_PATH, null, null, null, null, null,
                        false, false, false, false));
        bindings.offer(binding);
        nestedBindings.offer(bindings);
        bindings = new ArrayDeque<>();
        binding = ShaclPropertyBindingTest.genMockSparqlBinding(new SparqlBindingTestParameters(SAMPLE_FIELD,
                SAMPLE_CONCEPT, null, null, SAMPLE_NESTED_PRED_PATH, null, null, null, null, null,
                false, false, false, false));
        bindings.offer(binding);
        nestedBindings.offer(bindings);
        return nestedBindings;
    }

    /**
     * Generates criterias from the inputs.
     * 
     * @param criteria Key and field criteria inputs as pairs.
     */
    public static Map<String, String> genCriterias(String... criteria) {
        if ((criteria.length / 2) != 1) {
            throw new IllegalArgumentException("Criteria should be in pairs!");
        }
        Map<String, String> criterias = new HashMap<>();
        for (int i = 0; i < criteria.length; i += 2) {
            criterias.put(criteria[i], criteria[i + 1]);
        }
        return criterias;
    }

    /**
     * Generates min max criterias from the inputs.
     * 
     * @param criteria Sets of three criteria - field, min and max value, in which
     *                 min and max may be optional.
     */
    private Map<String, String> genMinMaxCriterias(Object... criteria) {
        if ((criteria.length / 3) != 1) {
            throw new IllegalArgumentException("Criteria should be in sets of three!");
        }
        Map<String, String> criterias = new HashMap<>();
        for (int i = 0; i < criteria.length; i += 3) {
            criterias.put(criteria[i].toString(), "range");
            this.putInMapIfPresent(criterias, "min " + criteria[i], criteria[i + 1]);
            this.putInMapIfPresent(criterias, "max " + criteria[i], criteria[i + 2]);
        }
        return criterias;
    }

    private void putInMapIfPresent(Map<String, String> criterias, String field, Object fieldVal) {
        if (fieldVal != null) {
            criterias.put(field, fieldVal.toString());
        } else {
            criterias.put(field, "");
        }

    }
}
