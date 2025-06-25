package com.cmclinnovations.agent.service.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.cmclinnovations.agent.TestUtils;
import com.cmclinnovations.agent.model.SparqlBinding;
import com.cmclinnovations.agent.template.FormTemplateFactoryTest;
import com.cmclinnovations.agent.template.query.DeleteQueryTemplateFactoryTest;
import com.cmclinnovations.agent.template.query.GetQueryTemplateFactoryTest;
import com.cmclinnovations.agent.template.query.SearchQueryTemplateFactoryTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QueryTemplateServiceTest {
    @Mock
    private AuthenticationService authService;
    @Mock
    private FileService fileService;

    private static QueryTemplateService testService;
    private static final String TEST_RESOURCE = "test";
    public static final String TEST_JSONLD_FILE = "service/add/sample.jsonld";

    @BeforeEach
    void setup() {
        JsonLdService jsonLdService = new JsonLdService(new ObjectMapper());
        testService = new QueryTemplateService(authService, fileService, jsonLdService);
    }

    @Test
    void testGetJsonLdTemplate() throws IOException {
        // Set up mocks
        ObjectNode sample = TestUtils.getJson(TEST_JSONLD_FILE);
        when(fileService.getTargetFileName(TEST_RESOURCE)).thenReturn(TEST_RESOURCE);
        when(fileService.getJsonContents(Mockito.anyString())).thenReturn(sample);

        // Execution
        ObjectNode results = testService.getJsonLdTemplate(TEST_RESOURCE);
        assertEquals(sample, results);
    }

    @Test
    void testGenDeleteQuery() throws IOException {
        // Set up mocks
        ObjectNode sample = TestUtils.getJson(DeleteQueryTemplateFactoryTest.TEST_SIMPLE_FILE);
        when(fileService.getTargetFileName(TEST_RESOURCE)).thenReturn(TEST_RESOURCE);
        when(fileService.getJsonContents(Mockito.anyString())).thenReturn(sample);

        // Execution
        Queue<String> results = testService.genDeleteQuery(TEST_RESOURCE, DeleteQueryTemplateFactoryTest.SAMPLE_ID);
        assertEquals(2, results.size());
        assertEquals(TestUtils.getSparqlQuery(DeleteQueryTemplateFactoryTest.EXPECTED_SIMPLE_FILE), results.poll());
    }

    @Test
    void testGenFormTemplate() throws IOException {
        Map<String, Object> result = testService
                .genFormTemplate(TestUtils.getArrayJson(FormTemplateFactoryTest.TEST_SIMPLE_FILE), new HashMap<>());
        assertEquals(
                TestUtils.getMapJson(FormTemplateFactoryTest.EXPECTED_SIMPLE_FILE),
                result);
    }

    @Test
    void testGenGetQuery() throws IOException {
        Queue<Queue<SparqlBinding>> testBindings = GetQueryTemplateFactoryTest.initTestBindings();
        Queue<String> results = testService.genGetQuery(testBindings);
        GetQueryTemplateFactoryTest.validateTestOutput(results, GetQueryTemplateFactoryTest.EXPECTED_SIMPLE_FILE);
    }

    @Test
    void testGenGetQuery_WithFilter() throws IOException {
        Queue<Queue<SparqlBinding>> testBindings = GetQueryTemplateFactoryTest.initTestBindings();
        Queue<String> results = testService.genGetQuery(testBindings, GetQueryTemplateFactoryTest.SAMPLE_FILTER, null,
                "", new HashMap<>());
        GetQueryTemplateFactoryTest.validateTestOutput(results, GetQueryTemplateFactoryTest.EXPECTED_SIMPLE_ID_FILE);
    }

    @Test
    void testGenSearchQuery() throws IOException {
        Queue<Queue<SparqlBinding>> testBindings = SearchQueryTemplateFactoryTest.initTestBindings();
        Queue<String> results = testService.genSearchQuery(testBindings, SearchQueryTemplateFactoryTest.genCriterias(
                SearchQueryTemplateFactoryTest.SAMPLE_FIELD, SearchQueryTemplateFactoryTest.SAMPLE_FILTER));
        assertEquals(2, results.size());
        assertEquals(TestUtils.getSparqlQuery(SearchQueryTemplateFactoryTest.EXPECTED_SIMPLE_FILE), results.poll());
        assertEquals(TestUtils.getSparqlQuery(SearchQueryTemplateFactoryTest.EXPECTED_SIMPLE_MIXED_FILE),
                results.poll());
    }
}
