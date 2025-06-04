package com.cmclinnovations.agent.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.cmclinnovations.agent.TestUtils;
import com.cmclinnovations.agent.utils.ShaclResource;
import com.cmclinnovations.agent.utils.StringResource;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class ShaclPropertyBindingTest {
        private static final String EXAMPLE_PREFIX = "http://example.org/";
        private static final String SAMPLE_PREDICATE = EXAMPLE_PREFIX + "associated";
        private static final String SAMPLE_SECOND_PREDICATE = EXAMPLE_PREFIX + "next";
        private static final String SAMPLE_CLASS = EXAMPLE_PREFIX + "Class";

        private static final String PROPERTY_FIELD = "propertytest";
        private static final String GROUP_FIELD = "grouptest";
        private static final String BRANCH_FIELD = "branchtest";

        public static final String EXPECTED_SIMPLE_FILE = "model/shacl_property_simple.sparql";
        public static final String EXPECTED_SIMPLE_ID_FILE = "model/shacl_property_simple_id.sparql";
        public static final String EXPECTED_SIMPLE_CLAZZ_FILE = "model/shacl_property_simple_class.sparql";
        public static final String EXPECTED_SIMPLE_GROUP_FILE = "model/shacl_property_simple_group.sparql";
        public static final String EXPECTED_SIMPLE_INSTANCE_FILE = "model/shacl_property_simple_instance.sparql";
        public static final String EXPECTED_SIMPLE_NESTED_CLASS_FILE = "model/shacl_property_simple_nested_class.sparql";
        public static final String EXPECTED_SIMPLE_APPEND_FILE = "model/shacl_property_simple_append.sparql";

        public record SparqlBindingTestParameters(
                        String name,
                        String clazz,
                        String group,
                        String branch,
                        String multipath,
                        String nameMultipath,
                        String subject,
                        String instanceClass,
                        String nestedClass,
                        String order,
                        boolean inverseMultipath,
                        boolean isArray,
                        boolean isClass,
                        boolean isOptional) {
        }

        private static Stream<Arguments> provideParametersForTestGetters() {
                return Stream.of(
                                Arguments.of(PROPERTY_FIELD, "", "", false, false),
                                Arguments.of(PROPERTY_FIELD, GROUP_FIELD, "", true, true),
                                Arguments.of(PROPERTY_FIELD, "", BRANCH_FIELD, true, false),
                                Arguments.of(PROPERTY_FIELD, GROUP_FIELD, BRANCH_FIELD, false, false));
        }

        @ParameterizedTest
        @MethodSource("provideParametersForTestGetters")
        void testGetters(String name,
                        String group,
                        String branch,
                        boolean isArray,
                        boolean isOptional) {
                ShaclPropertyBinding sample = new ShaclPropertyBinding(
                                genMockSparqlBinding(
                                                new SparqlBindingTestParameters(name, null, group, branch,
                                                                SAMPLE_PREDICATE, null, null, null, null, null, false,
                                                                isArray, false, isOptional)));
                assertEquals(name, sample.getName());
                assertEquals(group, sample.getGroup());
                assertEquals(branch, sample.getBranch());
                assertEquals(isArray, sample.isArray());
                assertEquals(isOptional, sample.isOptional());
        }

        private static Stream<Arguments> provideParametersForTestWrite() {
                return Stream.of(
                                // Basic test
                                Arguments.of(EXPECTED_SIMPLE_FILE, PROPERTY_FIELD, null, null, null, "", false, false),
                                // Test for simple isClazz
                                Arguments.of(EXPECTED_SIMPLE_CLAZZ_FILE, PROPERTY_FIELD, null, null, null, "", false,
                                                true),
                                // Test for simple ID
                                Arguments.of(EXPECTED_SIMPLE_ID_FILE, "id", null, SAMPLE_PREDICATE, null, "", true,
                                                false),
                                // Test for simple group field
                                Arguments.of(EXPECTED_SIMPLE_GROUP_FILE, PROPERTY_FIELD, GROUP_FIELD, null, null, "",
                                                false, false),
                                // Test for simple nested class
                                Arguments.of(EXPECTED_SIMPLE_NESTED_CLASS_FILE, PROPERTY_FIELD, null, null, null,
                                                SAMPLE_CLASS, false, false),
                                // Test for simple instance
                                Arguments.of(EXPECTED_SIMPLE_INSTANCE_FILE, PROPERTY_FIELD, null, null, SAMPLE_CLASS,
                                                "", false, false));
        }

        @ParameterizedTest
        @MethodSource("provideParametersForTestWrite")
        void testWrite(String expectedFileOutput,
                        String name,
                        String group,
                        String labelPred,
                        String instanceClazz,
                        String nestedClazz,
                        boolean inverseMultipath,
                        boolean isClazz) throws IOException {
                ShaclPropertyBinding sample = new ShaclPropertyBinding(
                                genMockSparqlBinding(
                                                new SparqlBindingTestParameters(name, null, group, null,
                                                                SAMPLE_PREDICATE, labelPred, null, instanceClazz,
                                                                nestedClazz, null, inverseMultipath, false, isClazz,
                                                                false)));
                assertEquals(TestUtils.getSparqlQuery(expectedFileOutput), sample.write(!nestedClazz.isEmpty()));
        }

        @Test
        void testAppendPred() throws IOException {
                ShaclPropertyBinding sample = new ShaclPropertyBinding(genMockSparqlBinding(
                                new SparqlBindingTestParameters(PROPERTY_FIELD, null, null, null, SAMPLE_PREDICATE,
                                                null, null, null, null, null,
                                                false, false, false, false)));
                SparqlBinding sampleWithSecondPred = genMockSparqlBinding(
                                new SparqlBindingTestParameters(PROPERTY_FIELD, null, null, null,
                                                SAMPLE_SECOND_PREDICATE, null, null, null, null,
                                                null, false, false, false, false));
                sample.appendPred(sampleWithSecondPred);
                assertEquals(TestUtils.getSparqlQuery(EXPECTED_SIMPLE_APPEND_FILE), sample.write(false));
        }

        /**
         * Generates a SPARQL binding from the input params.
         * 
         * @param name             The name of the field
         * @param clazz            The target clazz of the node shape
         * @param group            The parent group of the field
         * @param branch           The branch of the group
         * @param multipath        The multi path for the predicate
         * @param nameMultipath    The multi path for the name portion of the predicate
         * @param subject          The target subject of the field
         * @param instanceClass    The class of the field if it is an instance
         * @param nestedClass      The related parent class if the field is nested
         *                         within a node shape
         * @param order            The order that the property must be displayed
         * @param inverseMultipath Indicates if the multipath predicate should be
         *                         inversed
         * @param isClass          Indicates if the field is a class
         * @param isOptional       Indicates if the field is optional
         * @param isArray          Indicates if the field is an array
         */
        public static SparqlBinding genMockSparqlBinding(SparqlBindingTestParameters params) {
                ObjectNode jsonBinding = TestUtils.genEmptyObjectNode();
                SparqlBindingTest.addResponseField(jsonBinding, ShaclResource.NAME_PROPERTY, params.name);
                SparqlBindingTest.addResponseField(jsonBinding, StringResource.CLAZZ_VAR, params.clazz);
                SparqlBindingTest.addResponseField(jsonBinding, ShaclResource.NODE_GROUP_VAR, params.group);
                SparqlBindingTest.addResponseField(jsonBinding, ShaclResource.BRANCH_VAR, params.branch);

                SparqlBindingTest.addResponseField(jsonBinding, ShaclResource.MULTIPATH_VAR, params.multipath);
                SparqlBindingTest.addResponseField(jsonBinding, ShaclResource.MULTI_NAME_PATH_VAR,
                                params.nameMultipath);
                if (params.inverseMultipath) {
                        SparqlBindingTest.addResponseField(jsonBinding,
                                        ShaclResource.MULTIPATH_VAR + ShaclResource.PATH_PREFIX,
                                        ShaclResource.SHACL_PREFIX + "inversePath");
                }

                SparqlBindingTest.addResponseField(jsonBinding, ShaclResource.SUBJECT_VAR, params.subject);
                SparqlBindingTest.addResponseField(jsonBinding, ShaclResource.INSTANCE_CLASS_VAR, params.instanceClass);
                SparqlBindingTest.addResponseField(jsonBinding, ShaclResource.NESTED_CLASS_VAR, params.nestedClass);
                SparqlBindingTest.addResponseField(jsonBinding, ShaclResource.ORDER_PROPERTY, params.order);
                SparqlBindingTest.addResponseField(jsonBinding, ShaclResource.IS_ARRAY_VAR,
                                String.valueOf(params.isArray));
                SparqlBindingTest.addResponseField(jsonBinding, ShaclResource.IS_CLASS_VAR,
                                String.valueOf(params.isClass));
                SparqlBindingTest.addResponseField(jsonBinding, ShaclResource.IS_OPTIONAL_VAR,
                                String.valueOf(params.isOptional));

                return new SparqlBinding(jsonBinding);
        }
}
