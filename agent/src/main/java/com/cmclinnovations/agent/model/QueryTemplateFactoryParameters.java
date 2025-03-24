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
}
