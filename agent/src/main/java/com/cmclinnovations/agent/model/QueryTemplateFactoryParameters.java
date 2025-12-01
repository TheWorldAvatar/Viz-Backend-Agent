package com.cmclinnovations.agent.model;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import org.eclipse.rdf4j.sparqlbuilder.core.Variable;

import com.fasterxml.jackson.databind.node.ObjectNode;

public record QueryTemplateFactoryParameters(
    Queue<Queue<SparqlBinding>> bindings,
    ObjectNode rootNode,
    Queue<List<String>> targetIds,
    ParentField parent,
    Map<String, String> criterias,
    String sortDirectives,
    String addQueryStatements,
    Map<Variable, List<Integer>> addVars) {

  public QueryTemplateFactoryParameters(ObjectNode rootNode, String targetId) {
    this(null, rootNode, new ArrayDeque<>(List.of(Arrays.asList(targetId))), null, null, null, null,
        null);
  }

  public QueryTemplateFactoryParameters(Queue<Queue<SparqlBinding>> bindings, Map<String, String> criterias) {
    this(bindings, null, new ArrayDeque<>(), null, criterias, null, null, null);
  }

  public QueryTemplateFactoryParameters(Queue<Queue<SparqlBinding>> bindings, Queue<List<String>> targetIds,
      ParentField parent, String sortDirectives, String addQueryStatements, Map<Variable, List<Integer>> addVars) {
    this(bindings, null, targetIds, parent, null, sortDirectives, addQueryStatements, addVars);
  }
}
