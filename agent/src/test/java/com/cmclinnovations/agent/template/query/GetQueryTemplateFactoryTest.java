package com.cmclinnovations.agent.template.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Queue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.cmclinnovations.agent.TestUtils;
import com.cmclinnovations.agent.model.ParentField;
import com.cmclinnovations.agent.model.QueryTemplateFactoryParameters;
import com.cmclinnovations.agent.model.SparqlBinding;
import com.cmclinnovations.agent.utils.StringResource;

class GetQueryTemplateFactoryTest {
  private static GetQueryTemplateFactory TEMPLATE_FACTORY;

  private static final String EXPECTED_SIMPLE_FILE = "template/query/get_query_simple.sparql";
  private static final String EXPECTED_SIMPLE_MIXED_FILE = "template/query/get_query_simple_mixed.sparql";
  private static final String EXPECTED_SIMPLE_ID_FILE = "template/query/get_query_simple_id.sparql";
  private static final String EXPECTED_SIMPLE_ID_MIXED_FILE = "template/query/get_query_simple_id_mixed.sparql";
  private static final String EXPECTED_SIMPLE_PARENT_FILE = "template/query/get_query_simple_parent.sparql";
  private static final String EXPECTED_SIMPLE_PARENT_MIXED_FILE = "template/query/get_query_simple_parent_mixed.sparql";
  private static final String EXPECTED_SIMPLE_OPTIONAL_FILE = "template/query/get_query_simple_optional.sparql";
  private static final String EXPECTED_SIMPLE_OPTIONAL_MIXED_FILE = "template/query/get_query_simple_optional_mixed.sparql";
  private static final String SAMPLE_PREFIX = "http://example.com/";
  private static final String SAMPLE_CONCEPT = SAMPLE_PREFIX + "Concept";
  private static final String SAMPLE_PRED_PATH = SAMPLE_PREFIX + "propPath1";
  private static final String SAMPLE_NESTED_PRED_PATH = SAMPLE_PREFIX + "propPath2";
  private static final String SAMPLE_PARENT_PATH = SAMPLE_PREFIX + "parentPath1";
  private static final String SAMPLE_SUB_PATH = SAMPLE_PREFIX + "subPath1";
  private static final String SAMPLE_OPTIONAL_PATH = SAMPLE_PREFIX + "optionalPath1";
  private static final String SAMPLE_FIELD = "field";
  private static final String SAMPLE_PARENT_FIELD = "parent field";
  private static final String SAMPLE_OPTIONAL_FIELD = "optional field";

  private static final String SAMPLE_FILTER = "01j82";

  private static final String NAME_VAR = "name";
  private static final String IS_OPTIONAL_VAR = "isoptional";
  private static final String MULTIPATH_VAR = "multipath";
  private static final String MULTISUBPATH_VAR = "multisubpath";

  @BeforeAll
  static void setup() {
    TEMPLATE_FACTORY = new GetQueryTemplateFactory();
  }

  @Test
  void testWrite_Simple() throws IOException {
    // Set up
    Queue<Queue<SparqlBinding>> nestedBindings = new ArrayDeque<>();
    Queue<SparqlBinding> bindings = new ArrayDeque<>();
    genMockSPARQLBinding(bindings, SAMPLE_CONCEPT, SAMPLE_FIELD, SAMPLE_PRED_PATH, "", false);
    nestedBindings.offer(bindings);
    bindings = new ArrayDeque<>();
    genMockSPARQLBinding(bindings, SAMPLE_CONCEPT, SAMPLE_FIELD, SAMPLE_NESTED_PRED_PATH, "", false);
    nestedBindings.offer(bindings);
    // Execute
    Queue<String> results = TEMPLATE_FACTORY.write(
        new QueryTemplateFactoryParameters(nestedBindings, null, "", null, null, null));
    // Assert
    assertEquals(2, results.size());
    assertEquals(TestUtils.getSparqlQuery(EXPECTED_SIMPLE_FILE), results.poll());
    assertEquals(TestUtils.getSparqlQuery(EXPECTED_SIMPLE_MIXED_FILE), results.poll());
  }

  @Test
  void testWrite_SimpleWithFilter() throws IOException {
    // Set up
    Queue<Queue<SparqlBinding>> nestedBindings = new ArrayDeque<>();
    Queue<SparqlBinding> bindings = new ArrayDeque<>();
    genMockSPARQLBinding(bindings, SAMPLE_CONCEPT, SAMPLE_FIELD, SAMPLE_PRED_PATH, "", false);
    nestedBindings.offer(bindings);
    bindings = new ArrayDeque<>();
    genMockSPARQLBinding(bindings, SAMPLE_CONCEPT, SAMPLE_FIELD, SAMPLE_NESTED_PRED_PATH, "", false);
    nestedBindings.offer(bindings);
    // Execute
    Queue<String> results = TEMPLATE_FACTORY
        .write(new QueryTemplateFactoryParameters(nestedBindings, null, SAMPLE_FILTER, null, null, null));
    // Assert
    assertEquals(2, results.size());
    assertEquals(TestUtils.getSparqlQuery(EXPECTED_SIMPLE_ID_FILE), results.poll());
    assertEquals(TestUtils.getSparqlQuery(EXPECTED_SIMPLE_ID_MIXED_FILE), results.poll());
  }

  @Test
  void testWrite_MissingParentField() {
    // Set up
    Queue<Queue<SparqlBinding>> nestedBindings = new ArrayDeque<>();
    Queue<SparqlBinding> bindings = new ArrayDeque<>();
    genMockSPARQLBinding(bindings, SAMPLE_CONCEPT, SAMPLE_FIELD, SAMPLE_PARENT_PATH, SAMPLE_SUB_PATH, false);
    nestedBindings.offer(bindings);
    // The method should throw an exception because filterId is required when
    // hasParent is true
    IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
      TEMPLATE_FACTORY.write(
          new QueryTemplateFactoryParameters(nestedBindings, null, SAMPLE_FILTER,
              new ParentField(SAMPLE_FILTER, SAMPLE_PARENT_FIELD), null, null));
    });
    assertEquals("Unable to find matching variable for parent field: " + SAMPLE_PARENT_FIELD, thrown.getMessage());
  }

  @Test
  void testWrite_ParentField() throws IOException {
    // Set up
    Queue<Queue<SparqlBinding>> nestedBindings = new ArrayDeque<>();
    Queue<SparqlBinding> bindings = new ArrayDeque<>();
    genMockSPARQLBinding(bindings, SAMPLE_CONCEPT, SAMPLE_PARENT_FIELD, SAMPLE_PARENT_PATH, SAMPLE_SUB_PATH, false);
    nestedBindings.offer(bindings);
    // Execute
    Queue<String> results = TEMPLATE_FACTORY.write(
        new QueryTemplateFactoryParameters(nestedBindings, null, SAMPLE_FILTER,
            new ParentField(SAMPLE_FILTER, SAMPLE_PARENT_FIELD), null, null));
    // Assert
    assertEquals(2, results.size());
    assertEquals(TestUtils.getSparqlQuery(EXPECTED_SIMPLE_PARENT_FILE), results.poll());
    assertEquals(TestUtils.getSparqlQuery(EXPECTED_SIMPLE_PARENT_MIXED_FILE), results.poll());
  }

  // Mock isOptional doesnt work at nested level
  @Test
  void testWrite_Simple_Optional() throws IOException {
    // Set up
    Queue<Queue<SparqlBinding>> nestedBindings = new ArrayDeque<>();
    Queue<SparqlBinding> bindings = new ArrayDeque<>();
    genMockSPARQLBinding(bindings, SAMPLE_CONCEPT, SAMPLE_OPTIONAL_FIELD, SAMPLE_OPTIONAL_PATH, "", true);
    nestedBindings.offer(bindings);
    // Execute
    Queue<String> results = TEMPLATE_FACTORY.write(
        new QueryTemplateFactoryParameters(nestedBindings, null, "", null, null, null));
    // Assert
    assertEquals(2, results.size());
    assertEquals(TestUtils.getSparqlQuery(EXPECTED_SIMPLE_OPTIONAL_FILE), results.poll());
    assertEquals(TestUtils.getSparqlQuery(EXPECTED_SIMPLE_OPTIONAL_MIXED_FILE), results.poll());
  }

  /**
   * Generates a mock version of one SPARQL binding.
   * 
   * @param resultBindings   Stores the binding generated
   * @param clazz            The target class of the query
   * @param multiPathPred    The multi path for the predicate
   * @param multiSubPathPred The multi sub path for the predicate
   * @param varName          The variable name
   * @param isOptional       Indicates if the field is optional
   */
  private static void genMockSPARQLBinding(Queue<SparqlBinding> resultBindings, String clazz, String varName,
      String multiPathPred, String multiSubPathPred, boolean isOptional) {
    SparqlBinding binding = mock(SparqlBinding.class);
    when(binding.getFieldValue(StringResource.CLAZZ_VAR)).thenReturn(clazz);
    when(binding.getFieldValue(NAME_VAR)).thenReturn(varName);
    // Only create mock interactions if there are values

    if (!multiPathPred.isEmpty()) {
      when(binding.containsField(MULTIPATH_VAR)).thenReturn(true);
      when(binding.getFieldValue(MULTIPATH_VAR)).thenReturn(multiPathPred);
    }
    // Only create mock interactions if there are values
    if (!multiSubPathPred.isEmpty()) {
      when(binding.containsField(MULTISUBPATH_VAR)).thenReturn(true);
      when(binding.getFieldValue(MULTISUBPATH_VAR)).thenReturn(multiSubPathPred);
    }
    when(binding.getFieldValue(IS_OPTIONAL_VAR)).thenReturn(String.valueOf(isOptional));
    resultBindings.offer(binding);
  }
}
