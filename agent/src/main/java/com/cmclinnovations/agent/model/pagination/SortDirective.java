package com.cmclinnovations.agent.model.pagination;

import org.eclipse.rdf4j.sparqlbuilder.core.OrderCondition;
import org.eclipse.rdf4j.sparqlbuilder.core.Variable;

public record SortDirective(Variable field, OrderCondition order) {
}