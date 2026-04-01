package com.cmclinnovations.agent.model.util;

import com.cmclinnovations.agent.utils.ShaclResource;

/**
 * Represents the identifier information for use in a mapping key. Stores the
 * id, group, and branch name.
 */
public record ShaclPropertyId(String id, String group, String branch) {
    public String getFormattedId() {
        return ShaclResource.getMappingKey(id, group, branch);
    }
}
