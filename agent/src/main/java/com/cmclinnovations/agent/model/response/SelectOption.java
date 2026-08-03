package com.cmclinnovations.agent.model.response;

/**
 * A select option with label, value, parent id and disabled state.
 */
public record SelectOption(String label, String value, String parent, Boolean disabled) {
    public SelectOption(String label, String value, String parent) {
        this(label, value, parent, false);
    }
}
