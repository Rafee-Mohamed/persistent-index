package io.dsal.persistent.index.util;

import io.dsal.persistent.index.layout.IndexedComparator;

public class Search {

    public record LowerBound(boolean found, int idx) {}

    public static <K> LowerBound lowerBound(IndexedComparator<K> cmp, K key) {
        var left = 0;
        var right = cmp.size() - 1;

        while (left <= right) {
            var mid = left + (right - left) / 2;
            var ord = cmp.compare(mid, key);

            if (ord == 0) {
                return new LowerBound(true, mid);
            }

            if (ord > 0) {
                right = mid - 1;
            } else {
                left = mid + 1;
            }
        }

        return new LowerBound(false, left);
    }

}
