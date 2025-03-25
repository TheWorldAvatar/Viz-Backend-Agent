package com.cmclinnovations.agent.model;

import java.util.Map;
import java.util.Queue;

import com.cmclinnovations.agent.model.type.LifecycleEventType;
import com.fasterxml.jackson.databind.node.ObjectNode;

public record QueryTemplateFactoryParameters(
    Queue<Queue<SparqlBinding>> bindings,
    ObjectNode rootNode,
    String targetId,
    ParentField parent,
    Map<String, String> criterias,
    LifecycleEventType lifecycleEvent) {

  public QueryTemplateFactoryParameters(ObjectNode rootNode, String targetId) {
    this(null, rootNode, targetId, null, null, null);
  }

  public QueryTemplateFactoryParameters(Queue<Queue<SparqlBinding>> bindings, Map<String, String> criterias) {
    this(bindings, null, null, null, criterias, null);
  }

  public QueryTemplateFactoryParameters(Queue<Queue<SparqlBinding>> bindings, String targetId, ParentField parent,
      LifecycleEventType lifecycleEvent) {
    this(bindings, null, targetId, parent, null, lifecycleEvent);
  }
}
