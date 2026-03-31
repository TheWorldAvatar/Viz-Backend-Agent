package com.cmclinnovations.agent.model;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import com.cmclinnovations.agent.model.response.ColumnMetaPayload;
import com.fasterxml.jackson.databind.node.ObjectNode;

public record QueryTemplateFactoryParameters(
    Queue<Queue<SparqlBinding>> bindings,
    ObjectNode rootNode,
    Queue<List<String>> targetIds,
    Map<String, String> criterias,
    String addQueryStatements,
    List<ColumnMetaPayload> addColumns,
    String branchName,
    Set<String> optVarNames) {

  public QueryTemplateFactoryParameters(ObjectNode rootNode, String targetId) {
    this(null, rootNode, new ArrayDeque<>(List.of(Arrays.asList(targetId))), null, null, null, null, null);
  }

  public QueryTemplateFactoryParameters(Queue<Queue<SparqlBinding>> bindings, Map<String, String> criterias) {
    this(bindings, null, new ArrayDeque<>(), criterias, null, null, null, null);
  }

  public QueryTemplateFactoryParameters(Queue<Queue<SparqlBinding>> bindings, Queue<List<String>> targetIds,
      String addQueryStatements, List<ColumnMetaPayload> addColumns) {
    this(bindings, null, targetIds, null, addQueryStatements, addColumns, null, null);
  }

  public QueryTemplateFactoryParameters(ObjectNode rootNode, String targetId, String branchName) {
    this(null, rootNode, new ArrayDeque<>(List.of(Arrays.asList(targetId))), null, null, null, branchName, null);
  }

  public QueryTemplateFactoryParameters(ObjectNode rootNode, String targetId, String branchName,
      Set<String> optVarNames) {
    this(null, rootNode, new ArrayDeque<>(List.of(Arrays.asList(targetId))), null, null, null, branchName, optVarNames);
  }
}
