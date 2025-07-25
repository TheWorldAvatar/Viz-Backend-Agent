package com.cmclinnovations.agent.template.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.cmclinnovations.agent.TestUtils;
import com.cmclinnovations.agent.model.ParentField;
import com.cmclinnovations.agent.model.QueryTemplateFactoryParameters;
import com.cmclinnovations.agent.model.ShaclPropertyBindingTest;
import com.cmclinnovations.agent.model.ShaclPropertyBindingTest.SparqlBindingTestParameters;
import com.cmclinnovations.agent.model.SparqlBinding;
import com.cmclinnovations.agent.service.core.AuthenticationService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class GetQueryTemplateFactoryTest {
  @Mock
  private AuthenticationService authService;
  private GetQueryTemplateFactory testFactory;

  public static final String EXPECTED_SIMPLE_FILE = "template/query/get/get_query_simple";
  public static final String EXPECTED_SIMPLE_ID_FILE = "template/query/get/get_query_simple_id";
  private static final String EXPECTED_SIMPLE_PARENT_FILE = "template/query/get/get_query_simple_parent";
  private static final String EXPECTED_SIMPLE_OPTIONAL_FILE = "template/query/get/get_query_simple_optional";
  private static final String EXPECTED_SIMPLE_ARRAY_FILE = "template/query/get/get_query_simple_array";
  private static final String EXPECTED_COMPLEX_ARRAY_FILE = "template/query/get/get_query_complex_array";
  private static final String EXPECTED_SIMPLE_ADDITIONAL_STATEMENT_FILE = "template/query/get/get_query_add_statement_only";
  private static final String EXPECTED_COMPLEX_ADDITIONAL_FILE = "template/query/get/get_query_add_statement";
  private static final String SAMPLE_PREFIX = "http://example.com/";
  private static final String SAMPLE_CONCEPT = SAMPLE_PREFIX + "Concept";
  private static final String SAMPLE_PRED_PATH = SAMPLE_PREFIX + "propPath1";
  private static final String SAMPLE_NESTED_PRED_PATH = SAMPLE_PREFIX + "propPath2";
  private static final String SAMPLE_PARENT_PATH = SAMPLE_PREFIX + "parentPath1";
  private static final String SAMPLE_OPTIONAL_PATH = SAMPLE_PREFIX + "optionalPath1";
  private static final String SAMPLE_FIELD = "field";
  private static final String SAMPLE_ARRAY_FIELD = "array field";
  private static final String SAMPLE_GROUP_FIELD = "node field";
  private static final String SAMPLE_PARENT_FIELD = "parent field";
  private static final String SAMPLE_OPTIONAL_FIELD = "optional field";
  private static final String SAMPLE_GROUP = "group test";
  private static final String SAMPLE_ARRAY_GROUP = "array group test";

  public static final String SAMPLE_FILTER = "01j82";
  private static final String SAMPLE_ADDITIONAL_FIELD = "addProperty";
  private static final String SAMPLE_ADDITIONAL_STATEMENT = "?iri <http://example.com/addPath> ?"
      + SAMPLE_ADDITIONAL_FIELD + ".";
  private static final Map<String, List<Integer>> SAMPLE_ADD_VARS = new HashMap<>();

  @BeforeEach
  void setup() {
    this.testFactory = new GetQueryTemplateFactory(authService);
    SAMPLE_ADD_VARS.put(SAMPLE_ADDITIONAL_FIELD, List.of(0, 0));
  }

  @Test
  void testWrite_Simple() throws IOException {
    // Set up
    Queue<Queue<SparqlBinding>> nestedBindings = initTestBindings();
    // Execute
    Queue<String> results = this.testFactory.write(
        new QueryTemplateFactoryParameters(nestedBindings, "", null, "", new HashMap<>()));
    // Assert
    validateTestOutput(results, EXPECTED_SIMPLE_FILE);
  }

  @Test
  void testWrite_SimpleWithFilter() throws IOException {
    // Set up
    Queue<Queue<SparqlBinding>> nestedBindings = initTestBindings();
    // Execute
    Queue<String> results = this.testFactory
        .write(new QueryTemplateFactoryParameters(nestedBindings, SAMPLE_FILTER, null, "", new HashMap<>()));
    // Assert
    validateTestOutput(results, EXPECTED_SIMPLE_ID_FILE);
  }

  @Test
  void testWrite_MissingParentField() {
    // Set up
    Queue<Queue<SparqlBinding>> nestedBindings = new ArrayDeque<>();
    Queue<SparqlBinding> bindings = new ArrayDeque<>();
    SparqlBinding binding = ShaclPropertyBindingTest
        .genMockSparqlBinding(new SparqlBindingTestParameters(SAMPLE_FIELD,
            SAMPLE_CONCEPT, null, null, SAMPLE_PARENT_PATH, null, null, null, null, null,
            false, false, false, false));
    bindings.offer(binding);
    nestedBindings.offer(bindings);
    // The method should throw an exception because filterId is required when
    // hasParent is true
    IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
      this.testFactory.write(
          new QueryTemplateFactoryParameters(nestedBindings, SAMPLE_FILTER,
              new ParentField(SAMPLE_FILTER, SAMPLE_PARENT_FIELD), "", new HashMap<>()));
    });
    assertEquals("Unable to find matching variable for parent field: " + SAMPLE_PARENT_FIELD, thrown.getMessage());
  }

  @Test
  void testWrite_ParentField() throws IOException {
    // Set up
    Queue<Queue<SparqlBinding>> nestedBindings = new ArrayDeque<>();
    Queue<SparqlBinding> bindings = new ArrayDeque<>();
    SparqlBinding binding = ShaclPropertyBindingTest
        .genMockSparqlBinding(new SparqlBindingTestParameters(SAMPLE_PARENT_FIELD,
            SAMPLE_CONCEPT, null, null, SAMPLE_PARENT_PATH, null, null, null, null, null,
            false, false, false, false));
    bindings.offer(binding);
    nestedBindings.offer(bindings);
    // Execute
    Queue<String> results = this.testFactory.write(
        new QueryTemplateFactoryParameters(nestedBindings, SAMPLE_FILTER,
            new ParentField(SAMPLE_FILTER, SAMPLE_PARENT_FIELD), "", new HashMap<>()));
    // Assert
    validateTestOutput(results, EXPECTED_SIMPLE_PARENT_FILE);
  }

  @Test
  void testWrite_Simple_Optional() throws IOException {
    // Set up
    Queue<Queue<SparqlBinding>> nestedBindings = new ArrayDeque<>();
    Queue<SparqlBinding> bindings = new ArrayDeque<>();
    SparqlBinding binding = ShaclPropertyBindingTest
        .genMockSparqlBinding(new SparqlBindingTestParameters(SAMPLE_OPTIONAL_FIELD,
            SAMPLE_CONCEPT, null, null, SAMPLE_OPTIONAL_PATH, null, null, null, null, null,
            false, false, false, true));
    bindings.offer(binding);
    nestedBindings.offer(bindings);
    // Execute
    Queue<String> results = this.testFactory.write(
        new QueryTemplateFactoryParameters(nestedBindings, "", null, "", new HashMap<>()));
    // Assert
    validateTestOutput(results, EXPECTED_SIMPLE_OPTIONAL_FILE);
  }

  @Test
  void testWrite_Simple_Array() throws IOException {
    // Set up
    Queue<Queue<SparqlBinding>> nestedBindings = new ArrayDeque<>();
    Queue<SparqlBinding> bindings = new ArrayDeque<>();
    SparqlBinding binding = ShaclPropertyBindingTest.genMockSparqlBinding(new SparqlBindingTestParameters(SAMPLE_GROUP,
        SAMPLE_CONCEPT, null, null, SAMPLE_PRED_PATH, null, null, null, null, null,
        false, true, false, false));
    bindings.offer(binding);
    nestedBindings.offer(bindings);

    bindings = new ArrayDeque<>();
    binding = ShaclPropertyBindingTest.genMockSparqlBinding(new SparqlBindingTestParameters(SAMPLE_FIELD,
        SAMPLE_CONCEPT, SAMPLE_GROUP, null, SAMPLE_NESTED_PRED_PATH, null, null, null, null, null,
        false, false, false, false));
    bindings.offer(binding);

    binding = ShaclPropertyBindingTest.genMockSparqlBinding(new SparqlBindingTestParameters(SAMPLE_ARRAY_FIELD,
        SAMPLE_CONCEPT, SAMPLE_GROUP, null, SAMPLE_NESTED_PRED_PATH, null, null, null, null, null,
        false, false, false, false));
    bindings.offer(binding);
    nestedBindings.offer(bindings);
    // Execute
    Queue<String> results = this.testFactory.write(
        new QueryTemplateFactoryParameters(nestedBindings, "", null, "", new HashMap<>()));
    // Assert
    validateTestOutput(results, EXPECTED_SIMPLE_ARRAY_FILE);
    Map<String, Set<String>> arrayVarsMapping = this.testFactory.getArrayVariables();
    assertEquals(1, arrayVarsMapping.size());
    arrayVarsMapping.forEach((key, arrayVars) -> {
      assertEquals(2, arrayVars.size());
      assertTrue(arrayVars.contains(SAMPLE_FIELD));
      assertTrue(arrayVars.contains(SAMPLE_ARRAY_FIELD));
    });
  }

  @Test
  void testWrite_Complex_Array() throws IOException {
    // Set up
    Queue<Queue<SparqlBinding>> nestedBindings = new ArrayDeque<>();
    Queue<SparqlBinding> bindings = new ArrayDeque<>();
    SparqlBinding binding = ShaclPropertyBindingTest.genMockSparqlBinding(new SparqlBindingTestParameters(SAMPLE_GROUP,
        SAMPLE_CONCEPT, null, null, SAMPLE_PRED_PATH, null, null, null, null, null,
        false, true, false, false));
    bindings.offer(binding);
    binding = ShaclPropertyBindingTest.genMockSparqlBinding(new SparqlBindingTestParameters(SAMPLE_ARRAY_GROUP,
        SAMPLE_CONCEPT, null, null, SAMPLE_PRED_PATH, null, null, null, null, null,
        false, true, false, false));
    bindings.offer(binding);
    nestedBindings.offer(bindings);

    bindings = new ArrayDeque<>();
    binding = ShaclPropertyBindingTest.genMockSparqlBinding(new SparqlBindingTestParameters(SAMPLE_FIELD,
        SAMPLE_CONCEPT, SAMPLE_GROUP, null, SAMPLE_NESTED_PRED_PATH, null, null, null, null, null,
        false, false, false, false));
    bindings.offer(binding);

    binding = ShaclPropertyBindingTest.genMockSparqlBinding(new SparqlBindingTestParameters(SAMPLE_ARRAY_FIELD,
        SAMPLE_CONCEPT, SAMPLE_ARRAY_GROUP, null, SAMPLE_NESTED_PRED_PATH, null, null, null, null, null,
        false, false, false, false));
    bindings.offer(binding);
    nestedBindings.offer(bindings);
    // Execute
    Queue<String> results = this.testFactory.write(
        new QueryTemplateFactoryParameters(nestedBindings, "", null, "", new HashMap<>()));
    // Assert
    validateTestOutput(results, EXPECTED_COMPLEX_ARRAY_FILE);
    Map<String, Set<String>> arrayVarsMapping = this.testFactory.getArrayVariables();
    assertEquals(2, arrayVarsMapping.size());
    assertTrue(arrayVarsMapping.get(SAMPLE_GROUP).contains(SAMPLE_FIELD));
    assertTrue(arrayVarsMapping.get(SAMPLE_ARRAY_GROUP).contains(SAMPLE_ARRAY_FIELD));
  }

  @Test
  void testWrite_OnlyAdditionalQuery() throws IOException {
    Queue<Queue<SparqlBinding>> nestedBindings = initTestBindings();
    // Execute
    Queue<String> results = this.testFactory.write(
        new QueryTemplateFactoryParameters(nestedBindings, "", null, SAMPLE_ADDITIONAL_STATEMENT, new HashMap<>()));
    // Assert
    validateTestOutput(results, EXPECTED_SIMPLE_ADDITIONAL_STATEMENT_FILE);
  }

  @Test
  void testWrite_AdditionalQuery() throws IOException {
    Queue<Queue<SparqlBinding>> nestedBindings = initTestBindings();
    // Execute
    Queue<String> results = this.testFactory.write(
        new QueryTemplateFactoryParameters(nestedBindings, "", null, SAMPLE_ADDITIONAL_STATEMENT, SAMPLE_ADD_VARS));
    // Assert
    validateTestOutput(results, EXPECTED_COMPLEX_ADDITIONAL_FILE);
  }

  @Test
  void testGetSequence() {
    Queue<Queue<SparqlBinding>> nestedBindings = new ArrayDeque<>();
    Queue<SparqlBinding> bindings = new ArrayDeque<>();
    SparqlBinding binding = ShaclPropertyBindingTest
        .genMockSparqlBinding(new SparqlBindingTestParameters(SAMPLE_OPTIONAL_FIELD, SAMPLE_CONCEPT, null, null,
            SAMPLE_PRED_PATH, null, null, null, null, "3",
            false, false, false, false));
    bindings.offer(binding);

    binding = ShaclPropertyBindingTest.genMockSparqlBinding(new SparqlBindingTestParameters(SAMPLE_PARENT_FIELD,
        SAMPLE_CONCEPT, null, null, SAMPLE_PRED_PATH, null, null, null, null, "1",
        false, false, false, false));
    bindings.offer(binding);

    binding = ShaclPropertyBindingTest.genMockSparqlBinding(new SparqlBindingTestParameters(SAMPLE_FIELD,
        SAMPLE_CONCEPT, null, null, SAMPLE_PRED_PATH, null, null, null, null, "0",
        false, false, false, false));
    bindings.offer(binding);
    nestedBindings.offer(bindings);

    bindings = new ArrayDeque<>();
    binding = ShaclPropertyBindingTest.genMockSparqlBinding(new SparqlBindingTestParameters(SAMPLE_GROUP_FIELD,
        SAMPLE_CONCEPT, SAMPLE_PARENT_FIELD, null, SAMPLE_NESTED_PRED_PATH, null, null, null, null, "1",
        false, false, false, false));
    bindings.offer(binding);
    nestedBindings.offer(bindings);
    // Execute
    this.testFactory.write(
        new QueryTemplateFactoryParameters(nestedBindings, "", null, "", new HashMap<>()));
    // Assert
    List<String> sequence = this.testFactory.getSequence();
    assertEquals(3, sequence.size());
    assertEquals(List.of(SAMPLE_FIELD, SAMPLE_GROUP_FIELD, SAMPLE_OPTIONAL_FIELD), sequence);
  }

  /**
   * Initialise test bindings.
   */
  public static Queue<Queue<SparqlBinding>> initTestBindings() {
    Queue<Queue<SparqlBinding>> nestedBindings = new ArrayDeque<>();
    Queue<SparqlBinding> bindings = new ArrayDeque<>();
    SparqlBinding binding = ShaclPropertyBindingTest.genMockSparqlBinding(new SparqlBindingTestParameters(SAMPLE_FIELD,
        SAMPLE_CONCEPT, null, null, SAMPLE_PRED_PATH, null, null, null, null, null,
        false, false, false, false));
    bindings.offer(binding);
    nestedBindings.offer(bindings);
    // Attach second set
    bindings = new ArrayDeque<>();
    binding = ShaclPropertyBindingTest.genMockSparqlBinding(new SparqlBindingTestParameters(SAMPLE_FIELD,
        SAMPLE_CONCEPT, null, null, SAMPLE_NESTED_PRED_PATH, null, null, null, null, null,
        false, false, false, false));
    bindings.offer(binding);
    nestedBindings.offer(bindings);
    return nestedBindings;
  }

  /**
   * Validate test outputs
   */
  public static void validateTestOutput(Queue<String> results, String expectedSparql)
      throws IOException {
    assertEquals(2, results.size());
    assertEquals(TestUtils.getSparqlQuery(expectedSparql + TestUtils.SPARQL_FILE_EXTENSION), results.poll());
    assertEquals(TestUtils.getSparqlQuery(expectedSparql + "_mixed" + TestUtils.SPARQL_FILE_EXTENSION), results.poll());
  }
}
