package com.cmclinnovations.agent.model.util;

import java.util.List;

import com.cmclinnovations.agent.model.response.ColumnMetaPayload;

/**
 * Stores the data and its associated column metadata
 */
public record DataManifest<T>(T data, List<ColumnMetaPayload> columns) {
}
