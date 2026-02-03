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
import java.util.function.Function;

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
import org.apache.jena.sparql.core.Var;
import org.apache.jena.vocabulary.RDF;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf;
import org.springframework.stereotype.Component;

import com.cmclinnovations.agent.model.SparqlBinding;
import com.cmclinnovations.agent.model.SparqlResponseField;
import com.cmclinnovations.agent.model.type.ShaclRuleType;
import com.cmclinnovations.agent.utils.QueryResource;
import com.cmclinnovations.agent.utils.StringResource;

@Component
public class ShaclRuleProcesser {
    private final Property shaclOrder;
    private final Property shaclConstruct;
    private final Property shaclSelect;
    private static final String ID_TRIPLE_STATEMENT = QueryResource.genVariable(QueryResource.THIS_KEY)
            .has(QueryResource.DC_TERM_ID, QueryResource.ID_VAR).getQueryString();
    private static final Logger LOGGER = LogManager.getLogger(ShaclRuleProcesser.class);

    /**
     * Constructs a new query processor.
     */
    public ShaclRuleProcesser() {
        this.shaclOrder = ResourceFactory.createProperty("http://www.w3.org/ns/shacl#order");
        this.shaclConstruct = ResourceFactory.createProperty("http://www.w3.org/ns/shacl#construct");
        this.shaclSelect = ResourceFactory.createProperty("http://www.w3.org/ns/shacl#select");
    }

    /**
     * Retrieve all virtual queries to be executed at query time.
     *
     * @param rules The model containing SHACL rules.
     * @param iris  The list of IRIs to be targeted.
     */
    public Queue<String> getVirtualQueries(Model rules, List<String> iris) {
        LOGGER.debug("Retrieving SHACL virtual rules....");
        Queue<String> queries = this.execVirtualQueryOperation(rules, (String selectStatement) -> {
            // Update the query with ID filters and variable
            return QueryResource.DC_TERM.getQueryString() +
                    selectStatement.replaceFirst("(?i)WHERE\\s*\\{",
                            "?id WHERE{" + ID_TRIPLE_STATEMENT
                                    + this.getIriClause(QueryResource.ID_KEY, iris));
        });
        return queries;
    }

    /**
     * Retrieve the virtual queries associated with the fields.
     *
     * @param rules         The model containing SHACL rules.
     * @param fields        The fields of interest.
     * @param virtualFields Stores the fields that are in virtual queries.
     */
    public Queue<String> getVirtualQueries(Model rules, Set<String> fields, Set<String> virtualFields) {
        LOGGER.debug("Retrieving SHACL virtual rules....");
        Queue<String> queries = this.execVirtualQueryOperation(rules, (String selectStatement) -> {
            String selectQuery = QueryResource.DC_TERM.getQueryString() +
                    selectStatement.replaceFirst("(?i)WHERE\\s*\\{",
                            "?id WHERE{" + ID_TRIPLE_STATEMENT);
            Query query = QueryFactory.create(selectQuery);
            List<Var> variables = query.getProjectVars();
            // Skip this iteration if the field is not present
            if (variables.stream()
                    .noneMatch(v -> fields.contains(v.getVarName()))) {
                return null;
            }
            // Filter out the variables in the current query that are present in the fields
            variables.stream().filter(v -> fields.contains(v.getVarName()))
                    // If so, add them to the virtual fields
                    .forEach(v -> virtualFields.add(v.getVarName()));
            // Reset prefixes to use full IRIs
            query.getPrefixMapping().clearNsPrefixMap();
            return query.serialize();
        });
        return queries;
    }

    /**
     * Execute the operations to retrieve virtual queries based on the operation
     * required.
     *
     * @param rules          The model containing SHACL rules.
     * @param queryOperation The function to apply for each select query if it
     *                       exists.
     */
    public Queue<String> execVirtualQueryOperation(Model rules, Function<String, String> queryOperation) {
        LOGGER.debug("Retrieving SHACL virtual rules....");
        Queue<String> queries = new ArrayDeque<>();
        StmtIterator ruleStatements = rules.listStatements(null, RDF.type,
                ShaclRuleType.SPARQL_VIRTUAL_RULE.getResource());
        while (ruleStatements.hasNext()) {
            Statement ruleStmt = ruleStatements.nextStatement();
            Resource rule = ruleStmt.getSubject();
            Statement selectStatement = rule.getProperty(this.shaclSelect);
            if (selectStatement != null) {
                String selectQuery = queryOperation.apply(selectStatement.getString());
                if (selectQuery != null) {
                    queries.offer(selectQuery);
                }
            }
        }
        return queries;
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

        StmtIterator ruleStatements = rules.listStatements(null, RDF.type, ShaclRuleType.SPARQL_RULE.getResource());
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
            // for now, only cares about subject and object
            allVar.add(this.getVarName(subject));
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

    /**
     * Return the variable name of a target Node.
     * If the target node is not a variable, it will reutrn null.
     *
     * @param targetNode The input node of interest.
     */

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
