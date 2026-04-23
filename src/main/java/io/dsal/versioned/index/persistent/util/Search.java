package io.dsal.versioned.index.persistent.util;

import io.dsal.versioned.index.persistent.layout.IndexedComparator;

public class Search {

    public static <K> int find(IndexedComparator<K> cmp, K key) {
        var left = 0;
        var right = cmp.size() - 1;

        while (left <= right) {
            var mid = left + (right - left) / 2;
            var ord = cmp.compare(mid, key);

            if (ord == 0) {
                return mid;
            }

            if (ord > 0) {
                right = mid - 1;
            } else {
                left = mid + 1;
            }
        }

        return -1;
    }

    public record LowerBound(boolean found, int idx) {}

    public static <K> LowerBound findAndLowerBound(IndexedComparator<K> cmp, K key) {
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

    public static <K> int lowerBound(IndexedComparator<K> cmp, K key) {
        var left = 0;
        var right = cmp.size() - 1;

        while (left <= right) {
            var mid = left + (right - left) / 2;
            var ord = cmp.compare(mid, key);

            if (ord >= 0) {
                right = mid - 1;
            } else {
                left = mid + 1;
            }
        }

        return left;
    }

    public static <K> int upperBound(IndexedComparator<K> cmp, K key) {
        var left = 0;
        var right = cmp.size() - 1;

        while (left <= right) {
            var mid = left + (right - left) / 2;
            var ord = cmp.compare(mid, key);

            if (ord > 0) {
                right = mid - 1;
            } else {
                left = mid + 1;
            }
        }

        return left;
    }

    public static <K> int floor(IndexedComparator<K> cmp, K key) {
        return upperBound(cmp, key) - 1;
    }

    public static <K> int predecessor(IndexedComparator<K> cmp, K key) {
        return lowerBound(cmp, key) - 1;
    }


}
