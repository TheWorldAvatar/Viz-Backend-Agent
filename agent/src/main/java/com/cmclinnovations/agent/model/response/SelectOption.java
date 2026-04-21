package com.cmclinnovations.agent.model.response;

/**
 * A select option with label, value, and disabled state.
 */
public record SelectOption(String label, String value, Boolean disabled) {
    public SelectOption(String label, String value) {
        this(label, value, false);
    }
}
