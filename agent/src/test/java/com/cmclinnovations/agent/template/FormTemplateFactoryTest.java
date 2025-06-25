package com.cmclinnovations.agent.template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.cmclinnovations.agent.TestUtils;
import com.cmclinnovations.agent.service.core.AuthenticationService;
import com.cmclinnovations.agent.utils.ShaclResource;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class FormTemplateFactoryTest {
        @Mock
        private AuthenticationService authService;

        private FormTemplateFactory formTemplateFactory;
        private static ObjectMapper objectMapper;

        public static final String TEST_SIMPLE_FILE = "template/form/test/form_simple.json";
        public static final String EXPECTED_SIMPLE_FILE = "template/form/expected/form_simple.json";

        private static final String XSD_INTEGER_TYPE = "integer";
        private static final String XSD_STRING_TYPE = "string";

        @BeforeAll
        static void init() {
                objectMapper = new ObjectMapper();
        }

        @BeforeEach
        void setup() {
                this.formTemplateFactory = new FormTemplateFactory(authService);
        }

        @Test
        void testGenTemplate_EmptyInput() {
                // Set up
                ArrayNode emptyData = objectMapper.createArrayNode();
                // Execute
                Map<String, Object> result = this.formTemplateFactory.genTemplate(emptyData, new HashMap<>());
                // Assert
                assertTrue(result.isEmpty(), "Template should be empty when input data is empty");
        }

        @Test
        void testGenTemplate_InvalidInput() {
                // Set up
                ArrayNode sample = objectMapper.createArrayNode();
                // Mocking an invalid JSON object that does not have a valid type
                ObjectNode invalidShape = genPropertyShape("testInvalidShape", "invalidType", "invalid field",
                                "This shape should fail", ShaclResource.XSD_PREFIX + XSD_STRING_TYPE, "");
                sample.add(invalidShape);
                // Execute & assert
                IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                        this.formTemplateFactory.genTemplate(sample, new HashMap<>());
                });
                assertEquals("Invalid input node! Only property shape, property group, and node shape is allowed.",
                                exception.getMessage());
        }

        @Test
        void testGenTemplate() throws IOException {
                // Set up
                ArrayNode sample = TestUtils.getArrayJson(TEST_SIMPLE_FILE);
                // Execute
                Map<String, Object> result = this.formTemplateFactory.genTemplate(sample, new HashMap<>());
                // Assert
                assertEquals(TestUtils.getMapJson(EXPECTED_SIMPLE_FILE), result);
        }

        /**
         * Generate a sample property shape.
         *
         * @param id          Identifier value
         * @param typeClass   The type of class for this property
         * @param name        The name of this property
         * @param description The description for this property
         * @param dataType    The data type of the property, which must correspond to an
         *                    xsd type
         * @param order       A field to arrange the properties
         */
        public static ObjectNode genPropertyShape(String id, String typeClass, String name, String description,
                        String dataType, String order) {
                // Init empty JSON object
                ObjectNode propertyShape = objectMapper.createObjectNode();
                // Add ID
                propertyShape.put(ShaclResource.ID_KEY, id);
                // Add type as "@type:[class]"
                ArrayNode typeValueNode = objectMapper.createArrayNode()
                                .add(typeClass);
                propertyShape.set(ShaclResource.TYPE_KEY, typeValueNode);
                // Add name as`"sh:name":[{"@value" : "name"}]`
                ObjectNode nameValueNode = objectMapper.createObjectNode()
                                .put(ShaclResource.VAL_KEY, name);
                propertyShape.set(ShaclResource.SHACL_PREFIX + ShaclResource.NAME_PROPERTY,
                                objectMapper.createArrayNode().add(nameValueNode));
                // Add description as `"sh:description":[{"@value" : "description"}]`
                ObjectNode descriptionValueNode = objectMapper.createObjectNode()
                                .put(ShaclResource.VAL_KEY, description);
                propertyShape.set(ShaclResource.SHACL_PREFIX + ShaclResource.DESCRIPTION_PROPERTY,
                                objectMapper.createArrayNode().add(descriptionValueNode));
                // Add datatype as `"sh:datatype":[{"@id" : "data type"}]`
                ObjectNode dataTypeValueNode = objectMapper.createObjectNode()
                                .put(ShaclResource.ID_KEY, dataType);
                propertyShape.set(ShaclResource.SHACL_PREFIX + ShaclResource.DATA_TYPE_PROPERTY,
                                objectMapper.createArrayNode().add(dataTypeValueNode));
                // Add order as`"sh:order":[{"@type": "xsd:type", "@value" : "order"}]`
                ObjectNode orderValueNode = objectMapper.createObjectNode()
                                .put(ShaclResource.TYPE_KEY, ShaclResource.XSD_PREFIX + XSD_INTEGER_TYPE)
                                .put(ShaclResource.VAL_KEY, order);
                propertyShape.set(ShaclResource.SHACL_PREFIX + ShaclResource.ORDER_PROPERTY,
                                objectMapper.createArrayNode().add(orderValueNode));
                // Return object
                return propertyShape;
        }
}
