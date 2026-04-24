package io.dsal.versioned.index.persistent.util;

import io.dsal.versioned.index.persistent.layout.IndexedComparator;

/**
 * Binary-search utilities for {@link IndexedComparator} key sequences. All
 * operations run in O(log n) time and do not modify storage.
 *
 * <p>The sign convention for {@link IndexedComparator#compare} is the same as
 * {@code java.util.Comparator}: negative if stored &lt; key, zero if equal,
 * positive if stored &gt; key.</p>
 */
public class Search {

    /**
     * Returns the index of {@code key} in {@code cmp}, or {@code -1} if absent.
     *
     * @param cmp indexed key sequence to search
     * @param key key to locate
     * @param <K> key type
     * @return index of an exact match, or {@code -1}
     */
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

    /**
     * Result of a combined exact-match and lower-bound search.
     *
     * @param found {@code true} if an exact match for the key was found
     * @param idx   index of the exact match when {@code found}, or the insertion
     *              point (first index whose stored key is greater than the search key)
     *              when not found
     */
    public record LowerBound(boolean found, int idx) {}

    /**
     * Searches for {@code key} and returns both a found/not-found flag and the
     * lower-bound insertion index in one pass. Equivalent to calling {@link #find}
     * and {@link #lowerBound} but with a single traversal.
     *
     * @param cmp indexed key sequence to search
     * @param key key to locate
     * @param <K> key type
     * @return lower-bound result with an exact-match flag
     */
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

    /**
     * Returns the index of the first stored key that is greater than or equal to
     * {@code key} (the lower bound / insertion point). Returns {@code cmp.size()}
     * if all stored keys are less than {@code key}.
     *
     * @param cmp indexed key sequence
     * @param key search key
     * @param <K> key type
     * @return first index {@code i} such that {@code cmp[i] >= key}, or {@code cmp.size()}
     */
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

    /**
     * Returns the index of the first stored key that is strictly greater than
     * {@code key} (the upper bound). Returns {@code cmp.size()} if all stored keys
     * are less than or equal to {@code key}.
     *
     * @param cmp indexed key sequence
     * @param key search key
     * @param <K> key type
     * @return first index {@code i} such that {@code cmp[i] > key}, or {@code cmp.size()}
     */
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

    /**
     * Returns the index of the greatest stored key that is less than or equal to
     * {@code key}, or {@code -1} if no such key exists. Equivalent to
     * {@code upperBound(cmp, key) - 1}.
     *
     * @param cmp indexed key sequence
     * @param key search key
     * @param <K> key type
     * @return index of the floor key, or {@code -1}
     */
    public static <K> int floor(IndexedComparator<K> cmp, K key) {
        return upperBound(cmp, key) - 1;
    }

    /**
     * Returns the index of the greatest stored key that is strictly less than
     * {@code key}, or {@code -1} if no such key exists. Equivalent to
     * {@code lowerBound(cmp, key) - 1}.
     *
     * @param cmp indexed key sequence
     * @param key search key
     * @param <K> key type
     * @return index of the strict predecessor key, or {@code -1}
     */
    public static <K> int predecessor(IndexedComparator<K> cmp, K key) {
        return lowerBound(cmp, key) - 1;
    }


}
