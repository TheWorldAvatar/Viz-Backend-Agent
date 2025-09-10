package com.cmclinnovations.agent.component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
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
     */
    public String genSelectQuery(String targetQuery) {
        LOGGER.debug("Constructing a SELECT query from the WHERE clause....");
        Query query = QueryFactory.create(targetQuery);
        if (!query.isConstructType()) {
            throw new IllegalStateException("The provided query is not a CONSTRUCT query: " + targetQuery);
        }
        StringBuilder selectQueryBuilder = new StringBuilder();
        selectQueryBuilder.append("SELECT *").append(System.lineSeparator());
        selectQueryBuilder.append("WHERE ").append(query.getQueryPattern().toString());
        return selectQueryBuilder.toString();
    }

    /**
     * Generates a SPARQL INSERT DATA statement based on the CONSTRUCT template and
     * data to replace their associated variables in the query.
     *
     * @param targetQuery The input SPARQL query string.
     * @param data        Query results containing data to be replaced in the
     *                    CONSTRUCT query.
     */
    public String genInsertDataQuery(String targetQuery, Queue<SparqlBinding> data) {
        LOGGER.debug("Generating the INSERT DATA content....");
        Query query = QueryFactory.create(targetQuery);
        List<Triple> tripleList = query.getConstructTemplate().getTriples();

        StringBuilder insertBuilder = new StringBuilder();
        insertBuilder.append("INSERT DATA {");
        while (!data.isEmpty()) {
            SparqlBinding currentData = data.poll();
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
     * Parses the Target Node into the required string to be inserted into the
     * SPARQL query.
     *
     * @param targetNode  The input node of interest.
     * @param currentData Replacements for the target node if it is a variable.
     */
    private String parseNode(Node targetNode, SparqlBinding currentData) {
        if (targetNode.isVariable()) {
            String varName = targetNode.getName();
            String fieldVal = currentData.getFieldValue(varName);
            // If field value is not an IRI, return it in exception as its a value
            try {
                return Rdf.iri(fieldVal).getQueryString();
            } catch (IllegalArgumentException e) {
                return fieldVal;
            }
        } else if (targetNode.isURI()) {
            return Rdf.iri(targetNode.getURI()).getQueryString();
        } else if (targetNode.isLiteral()) {
            String literal = "\"" + targetNode.getLiteralLexicalForm() + "\"";
            if (targetNode.getLiteralLanguage() != null) {
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
