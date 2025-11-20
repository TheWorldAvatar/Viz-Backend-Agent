package com.cmclinnovations.agent.service.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
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
import com.cmclinnovations.agent.utils.ShaclResource;
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
        String results = testService.genDeleteQuery(TEST_RESOURCE, DeleteQueryTemplateFactoryTest.SAMPLE_ID);
        assertEquals(TestUtils.getSparqlQuery(DeleteQueryTemplateFactoryTest.EXPECTED_SIMPLE_FILE),
                results.replace("\n", ""));
    }

    @Test
    void testGenFormTemplate() throws IOException {
        Map<String, Object> result = testService
                .genFormTemplate(TestUtils.getArrayJson(FormTemplateFactoryTest.TEST_SIMPLE_FILE), new HashMap<>());
        ((List<Map<String, Object>>) result.get("property")).get(0).put(ShaclResource.ID_KEY, "string_id");
        assertEquals(
                TestUtils.getMapJson(FormTemplateFactoryTest.EXPECTED_SIMPLE_FILE),
                new ObjectMapper().writeValueAsString(result));
    }

    @Test
    void testGenGetQuery() throws IOException {
        Queue<Queue<SparqlBinding>> testBindings = GetQueryTemplateFactoryTest.initTestBindings();
        String results = testService.genGetQuery(testBindings);
        TestUtils.validateGeneratedQueryOutput(GetQueryTemplateFactoryTest.EXPECTED_SIMPLE_FILE, results);
    }

    @Test
    void testGenGetQuery_WithFilter() throws IOException {
        Queue<Queue<SparqlBinding>> testBindings = GetQueryTemplateFactoryTest.initTestBindings();
        String results = testService.genGetQuery(testBindings,
                new ArrayDeque<>(List.of(Arrays.asList(GetQueryTemplateFactoryTest.SAMPLE_FILTER))), null,
                "", new HashMap<>());
        TestUtils.validateGeneratedQueryOutput(GetQueryTemplateFactoryTest.EXPECTED_SIMPLE_ID_FILE, results);
    }

    @Test
    void testGenSearchQuery() throws IOException {
        Queue<Queue<SparqlBinding>> testBindings = SearchQueryTemplateFactoryTest.initTestBindings();
        String results = testService.genSearchQuery(testBindings, SearchQueryTemplateFactoryTest.genCriterias(
                SearchQueryTemplateFactoryTest.SAMPLE_FIELD, SearchQueryTemplateFactoryTest.SAMPLE_FILTER));
        TestUtils.validateGeneratedQueryOutput(SearchQueryTemplateFactoryTest.EXPECTED_SIMPLE_FILE, results);
    }
}
