package com.cmclinnovations.agent.model.util;

import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf;

/**
 * Stores the task ranks
 */
public record TaskRank(String id, String lexorank) {
    public String getValueClauseValue() {
        return "(" + Rdf.literalOf(this.id()).getQueryString() + " " + Rdf.literalOf(this.lexorank()).getQueryString()
                + ")";
    }
}
