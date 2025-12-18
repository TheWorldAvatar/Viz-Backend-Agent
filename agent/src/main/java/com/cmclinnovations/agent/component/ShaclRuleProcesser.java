package com.cmclinnovations.agent.component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.TreeMap;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.vocabulary.RDF;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf;
import org.springframework.stereotype.Component;

import com.cmclinnovations.agent.model.SparqlBinding;
import com.cmclinnovations.agent.model.SparqlResponseField;
import com.cmclinnovations.agent.utils.QueryResource;
import com.cmclinnovations.agent.utils.StringResource;

@Component
public class ShaclRuleProcesser {
    private final Resource shaclSparqlRule;
    private final Property shaclOrder;
    private final Property shaclConstruct;
    private static final Logger LOGGER = LogManager.getLogger(ShaclRuleProcesser.class);

    /**
     * Constructs a new query processor.
     */
    public ShaclRuleProcesser() {
        this.shaclSparqlRule = ResourceFactory.createResource("http://www.w3.org/ns/shacl#SPARQLRule");
        this.shaclOrder = ResourceFactory.createProperty("http://www.w3.org/ns/shacl#order");
        this.shaclConstruct = ResourceFactory.createProperty("http://www.w3.org/ns/shacl#construct");
    }

    /**
     * Retrieve all CONSTRUCT queries associated with a sh:SPARQLRule in the defined
     * SHACL rules.
     *
     * @param rules The model containing SHACL rules.
     */
    public Queue<String> getConstructQueries(Model rules) {
        LOGGER.debug("Retrieving SPARQL Construct rules....");
        Map<Integer, List<String>> queryOrderMappings = new TreeMap<>();

        StmtIterator ruleStatements = rules.listStatements(null, RDF.type, this.shaclSparqlRule);
        while (ruleStatements.hasNext()) {
            Statement ruleStmt = ruleStatements.nextStatement();

            // Extract the order and construct query from the subject
            Resource rule = ruleStmt.getSubject();
            Statement orderStatement = rule.getProperty(this.shaclOrder);
            Statement constructStatement = rule.getProperty(this.shaclConstruct);
            if (orderStatement == null) {
                throw new IllegalStateException("Missing order property for SHACL SPARQL Rule: " + constructStatement);
            } else if (constructStatement != null) {
                int order = orderStatement.getInt();
                String constructQuery = constructStatement.getString();
                queryOrderMappings.computeIfAbsent(order, k -> new ArrayList<>()).add(constructQuery);
            }
        }
        Queue<String> queries = new ArrayDeque<>();
        for (List<String> values : queryOrderMappings.values()) {
            queries.addAll(values);
        }
        return queries;
    }

    /**
     * Generates a SELECT SPARQL query from the CONSTRUCT query using the same WHERE
     * clause.
     *
     * @param targetQuery The input SPARQL query string.
     * @param instanceIri The instance IRI string.
     */

    public String genSelectQuery(String targetQuery, String instanceIri) {
        return this.genSelectQuery(targetQuery, List.of(instanceIri));
    }

    /**
     * Generates a SELECT SPARQL query from the CONSTRUCT query using the same WHERE
     * clause.
     *
     * @param targetQuery The input SPARQL query string.
     * @param iris        List of IRI strings.
     */
    public String genSelectQuery(String targetQuery, List<String> iris) {
        LOGGER.debug("Constructing a SELECT query from the WHERE clause....");
        Query query = QueryFactory.create(targetQuery);
        if (!query.isConstructType()) {
            throw new IllegalStateException("The provided query is not a CONSTRUCT query: " + targetQuery);
        }
        StringBuilder selectQueryBuilder = new StringBuilder();
        selectQueryBuilder.append("SELECT *").append(System.lineSeparator());
        String whereClause = query.getQueryPattern().toString();
        selectQueryBuilder.append("WHERE ")
                .append(whereClause.substring(0, whereClause.length() - 1))
                .append(this.getIriClause(QueryResource.THIS_KEY, iris)).append("}");
        return selectQueryBuilder.toString();
    }

    /**
     * Generates a list of triples for the CONSTRUCT template.
     *
     * @param targetQuery The input SPARQL query string.
     */
    public List<Triple> genConstructTriples(String targetQuery) {
        LOGGER.debug("Generating the triple content in the CONSTRUCT template...");
        Query query = QueryFactory.create(targetQuery);
        return query.getConstructTemplate().getTriples();
    }

    /**
     * Generates a SPARQL INSERT DATA statement based on the CONSTRUCT template and
     * data to replace their associated variables in the query.
     *
     * @param tripleList The list of triples in the CONSTRUCT template.
     * @param data       Query results containing data to be replaced in the
     *                   CONSTRUCT query.
     */
    public String genInsertDataQuery(List<Triple> tripleList, List<SparqlBinding> data) {
        LOGGER.debug("Generating the INSERT DATA content....");
        StringBuilder insertBuilder = new StringBuilder("INSERT DATA {");
        for (SparqlBinding currentData : data) {
            tripleList.forEach(triple -> {
                Node subject = triple.getSubject();
                Node pred = triple.getPredicate();
                Node object = triple.getObject();
                StringResource.appendTriple(insertBuilder, parseNode(subject, currentData),
                        parseNode(pred, currentData), parseNode(object, currentData));
            });
        }
        insertBuilder.append("}");
        return insertBuilder.toString();
    }

    /**
     * Generates a SPARQL DELETE WHERE statement based on the CONSTRUCT template and
     * data to replace their associated variables in the query.
     *
     * @param tripleList The list of triples in the CONSTRUCT template.
     * @param results    Query results containing data to be replaced in the
     *                   CONSTRUCT query.
     */

    public String genDeleteWhereQuery(List<Triple> tripleList, List<SparqlBinding> results) {
        LOGGER.debug("Generating the DELETE content....");
        StringBuilder deleteContentBuilder = new StringBuilder();

        Set<String> allVar = new HashSet<>();

        tripleList.forEach(triple -> {
            Node subject = triple.getSubject();
            Node pred = triple.getPredicate();
            Node object = triple.getObject();
            String subjectForm = parseNode(subject, null);
            String predicateForm = parseNode(pred, null);
            String objectForm = parseNode(object, null);
            StringResource.appendTriple(deleteContentBuilder, subjectForm, predicateForm, objectForm);
            // store all variables needed
            allVar.add(this.getVarName(subject));
            allVar.add(this.getVarName(pred));
            allVar.add(this.getVarName(object));
        });
        allVar.remove(null);

        // filter all existing IRIs

        Map<String, Set<String>> iriMap = new HashMap<>();

        for (SparqlBinding currentData : results) {
            for (String field : currentData.getFields()) {
                if (allVar.contains(field)) {
                    SparqlResponseField responseField = currentData.getFieldResponse(field);
                    String fieldValue = responseField.value();
                    if (fieldValue != null && responseField.type().equals("uri")) {
                        iriMap.computeIfAbsent(field, k -> new HashSet<>()).add(Rdf.iri(fieldValue).getQueryString());
                    }
                }
            }
        }

        String deleteContents = deleteContentBuilder.toString();
        StringBuilder deleteBuilder = new StringBuilder("DELETE{")
                .append(deleteContents).append("} WHERE{")
                .append(deleteContents);

        iriMap.forEach((field, values) -> {
            deleteBuilder.append(this.getIriClause(field, new ArrayList<>(values)));
        });

        return deleteBuilder.append("}").toString();
    }

    private String getVarName(Node targetNode) {
        if (targetNode.isVariable()) {
            return targetNode.getName();
        }
        return null;
    }

    /**
     * Generates a SPARQL VALUES clause that enforce variable "this" to take values
     * of a list of IRIs.
     *
     * @param iris List of IRI strings.
     */

    private String getIriClause(String field, List<String> iris) {
        return "\n" + QueryResource.values(field, iris);
    }

    /**
     * Parses the Target Node into the required string to be inserted into the
     * SPARQL query.
     *
     * @param targetNode  The input node of interest.
     * @param currentData Replacements for the target node if it is a variable.
     */
    private String parseNode(Node targetNode, SparqlBinding currentData) {
        if (targetNode.isVariable()) {
            String varName = targetNode.getName();
            if (currentData == null) {
                return QueryResource.genVariable(varName).getQueryString();
            }
            SparqlResponseField field = currentData.getFieldResponse(varName);
            if (field.type().equals("uri")) {
                return Rdf.iri(field.value()).getQueryString();
            }
            String literal = "\"" + field.value() + "\"";
            if (!field.lang().isEmpty()) {
                literal += "@" + field.lang();
            } else if (!field.dataType().isEmpty()) {
                literal += "^^" + Rdf.iri(field.dataType()).getQueryString();
            }
            return literal;
        } else if (targetNode.isURI()) {
            return Rdf.iri(targetNode.getURI()).getQueryString();
        } else if (targetNode.isLiteral()) {
            String literal = "\"" + targetNode.getLiteralLexicalForm() + "\"";
            if (targetNode.getLiteralLanguage() != null && !targetNode.getLiteralLanguage().isEmpty()) {
                literal += "@" + targetNode.getLiteralLanguage();
            } else if (targetNode.getLiteralDatatype() != null) {
                literal += "^^" + Rdf.iri(targetNode.getLiteralDatatypeURI()).getQueryString();
            }
            return literal;
        } else {
            return targetNode.toString();
        }
    }
}
