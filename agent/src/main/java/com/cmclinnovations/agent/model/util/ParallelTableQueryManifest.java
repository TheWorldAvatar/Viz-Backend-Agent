package com.cmclinnovations.agent.model.util;

public record ParallelTableQueryManifest<T>(T data, Integer filteredCount, Integer totalCount) {
}
