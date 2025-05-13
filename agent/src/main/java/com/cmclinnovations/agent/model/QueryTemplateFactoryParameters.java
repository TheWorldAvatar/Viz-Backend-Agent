package com.cmclinnovations.agent.model;

import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import com.fasterxml.jackson.databind.node.ObjectNode;

public record QueryTemplateFactoryParameters(
    Queue<Queue<SparqlBinding>> bindings,
    ObjectNode rootNode,
    String targetId,
    ParentField parent,
    Map<String, String> criterias,
    String addQueryStatements,
    Map<String, List<Integer>> addVars,
    Set<String> roles) {

  public QueryTemplateFactoryParameters(ObjectNode rootNode, String targetId) {
    this(null, rootNode, targetId, null, null, null, null, null);
  }

  public QueryTemplateFactoryParameters(Queue<Queue<SparqlBinding>> bindings, Map<String, String> criterias) {
    this(bindings, null, null, null, criterias, null, null, null);
  }

  public QueryTemplateFactoryParameters(Queue<Queue<SparqlBinding>> bindings, String targetId, ParentField parent,
      String addQueryStatements, Map<String, List<Integer>> addVars, Set<String> roles) {
    this(bindings, null, targetId, parent, null, addQueryStatements, addVars, roles);
  }
}
