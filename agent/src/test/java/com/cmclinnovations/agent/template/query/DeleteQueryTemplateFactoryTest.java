package com.cmclinnovations.agent.template.query;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Queue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.cmclinnovations.agent.TestUtils;
import com.cmclinnovations.agent.model.QueryTemplateFactoryParameters;
import com.cmclinnovations.agent.service.core.JsonLdService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class DeleteQueryTemplateFactoryTest {
    private static DeleteQueryTemplateFactory TEMPLATE_FACTORY;

    public static final String TEST_SIMPLE_FILE = "template/query/delete/test/delete_simple.json";
    public static final String EXPECTED_SIMPLE_FILE = "template/query/delete/expected/delete_simple.sparql";
    private static final String TEST_SIMPLE_REVERSE_FILE = "template/query/delete/test/delete_simple_reverse.json";
    private static final String EXPECTED_SIMPLE_REVERSE_FILE = "template/query/delete/expected/delete_simple_reverse.sparql";
    public static final String SAMPLE_ID = "01j82";

    @BeforeAll
    static void setup() {
        JsonLdService jsonLdService = new JsonLdService(new ObjectMapper());
        TEMPLATE_FACTORY = new DeleteQueryTemplateFactory(jsonLdService);
    }

    @Test
    void testWrite_Simple() throws Exception {
        ObjectNode sample = TestUtils.getJson(TEST_SIMPLE_FILE);
        Queue<String> results = TEMPLATE_FACTORY.write(new QueryTemplateFactoryParameters(sample, SAMPLE_ID));
        assertEquals(1, results.size());
        assertEquals(TestUtils.getSparqlQuery(EXPECTED_SIMPLE_FILE), results.poll());
    }

    @Test
    void testWrite_SimpleReverse() throws Exception {
        ObjectNode sample = TestUtils.getJson(TEST_SIMPLE_REVERSE_FILE);
        Queue<String> results = TEMPLATE_FACTORY.write(new QueryTemplateFactoryParameters(sample, SAMPLE_ID));
        assertEquals(1, results.size());
        assertEquals(TestUtils.getSparqlQuery(EXPECTED_SIMPLE_REVERSE_FILE), results.poll());
    }
}
