package com.cmclinnovations.agent.model.response;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Represents the column metadata information for a specific variable, including
 * their header name, type (uri, literal, array), datatype, and if they have a
 * specific lifecycle stage. Fields with null values will be excluded from the
 * JSON output.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ColumnMetaPayload(String value, String type, String datatype, String stage,
        Set<ColumnMetaPayload> arrayFields) {
    public ColumnMetaPayload {
        if (datatype == null) {
            datatype = "";
        }
    }

    public ColumnMetaPayload(String value, String type, String datatype, Set<ColumnMetaPayload> arrayFields) {
        this(value, type, datatype, null, arrayFields);
    }

    public ColumnMetaPayload(String value, String type, String datatype) {
        this(value, type, datatype, null, null);
    }

    /**
     * Returns a comparator that looks up List<Integer> from an external map
     * using the value as the key.
     */
    public static Comparator<ColumnMetaPayload> lexComparator(Map<String, List<Integer>> dataMap) {
        return (a, b) -> {
            // Retrieve the lists from the map using the names
            List<Integer> list1 = dataMap.getOrDefault(a.value(), Collections.emptyList());
            List<Integer> list2 = dataMap.getOrDefault(b.value(), Collections.emptyList());
            int size1 = list1.size();
            int size2 = list2.size();
            int minSize = Math.min(size1, size2);

            // Compare element by element
            for (int i = 0; i < minSize; i++) {
                int difference = Integer.compare(list1.get(i), list2.get(i));
                if (difference != 0) {
                    return difference; // Return at the first difference
                }
            }

            // If all compared elements are equal, the shorter list is smaller
            return Integer.compare(size1, size2);
        };
    }
}
