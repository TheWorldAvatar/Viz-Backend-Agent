package com.cmclinnovations.agent.model.response;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Represents the column metadata information for a specific variable, including
 * their header name, type (uri, literal, array), and datatype.
 */
public record ColumnMetaPayload(String value, String type, String datatype) {
    public ColumnMetaPayload {
        if (datatype == null) {
            datatype = "";
        }
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
