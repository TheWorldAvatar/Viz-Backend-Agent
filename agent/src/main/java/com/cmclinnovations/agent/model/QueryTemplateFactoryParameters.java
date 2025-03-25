package com.cmclinnovations.agent.model;

import java.util.List;
import java.util.Map;
import java.util.Queue;

import com.fasterxml.jackson.databind.node.ObjectNode;

public record QueryTemplateFactoryParameters(
    Queue<Queue<SparqlBinding>> bindings,
    ObjectNode rootNode,
    String targetId,
    ParentField parent,
    Map<String, String> criterias,
    String addQueryStatements,
    Map<String, List<Integer>> addVars) {

  public QueryTemplateFactoryParameters(ObjectNode rootNode, String targetId) {
    this(null, rootNode, targetId, null, null, null, null);
  }

  public QueryTemplateFactoryParameters(Queue<Queue<SparqlBinding>> bindings, Map<String, String> criterias) {
    this(bindings, null, null, null, criterias, null, null);
  }

  public QueryTemplateFactoryParameters(Queue<Queue<SparqlBinding>> bindings, String targetId, ParentField parent,
      String addQueryStatements, Map<String, List<Integer>> addVars) {
    this(bindings, null, targetId, parent, null, addQueryStatements, addVars);
  }
}
