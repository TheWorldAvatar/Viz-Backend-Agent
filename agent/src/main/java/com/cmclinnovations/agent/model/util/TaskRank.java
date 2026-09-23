package com.cmclinnovations.agent.model.util;

/**
 * Stores the task ranks
 */
public record TaskRank(String id, String lexorank) {
    public String getValueClauseValue() {
        return "(\"" + this.id() + "\" \"" + this.lexorank() + "\")";
    }
}
