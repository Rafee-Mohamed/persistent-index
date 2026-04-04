package io.dsal.persistent.index.layout;

import java.util.Arrays;

/**
 * Leaf value vector aligned with {@link KeyStorage}: {@link #size()} matches the
 * sibling key storage, and index {@code i} pairs with key {@code i}. Values are
 * not compared or ordered; only keys define tree order. Mutations return new
 * instances (copy-on-write).
 *
 * <p>Insert, remove, replace, merge, and fused helpers use the same index
 * contracts as the corresponding {@link KeyStorage} operations. When a leaf
 * splits, apply the same logical split to keys and values so {@link KeySplit} and
 * {@link ValueSplit} stay aligned.</p>
 *
 * @param <V> value type
 * @see KeyStorage
 * @see ValueSplit
 */
public class ValueStorage<V> {
    private final V[] vals;

    private ValueStorage(V[] vals) {
        this.vals = vals;
    }

    /**
     * Returns storage holding a single value (for example a new one-key leaf).
     *
     * @param val sole payload
     */
    public static <V> ValueStorage<V> of(V val) {
        return new ValueStorage<>((V[]) new Object[]{val});
    }

    /** Number of values (equals paired {@link KeyStorage#size()} in a leaf). */
    public int size() {
        return vals.length;
    }

    /**
     * Value at {@code idx}.
     *
     * @param idx index in {@code [0, size())}
     * @return value at {@code idx}
     * @throws IndexOutOfBoundsException if {@code idx} is out of range
     */
    public V val(int idx) {
        checkIndexBounds(idx);
        return vals[idx];
    }

    /**
     * Returns storage with {@code val} inserted at {@code idx}, shifting higher
     * indices right. Same bounds as {@link KeyStorage#insert(int, Object)}.
     *
     * @param idx insertion position in {@code [0, size()]}
     * @param val value to insert
     * @return storage after insert
     * @throws IndexOutOfBoundsException if {@code idx} is out of range for insert
     */
    public ValueStorage<V> insert(int idx, V val) {
        checkInsertBounds(idx);
        var newVals = (V[]) new Object[vals.length + 1];

        System.arraycopy(vals, 0, newVals, 0, idx);
        newVals[idx] = val;
        System.arraycopy(vals, idx, newVals, idx + 1, vals.length - idx);

        return new ValueStorage<>(newVals);
    }

    /**
     * Replaces the value at {@code idx}; length unchanged.
     *
     * @param idx index in {@code [0, size())}
     * @param val new value
     * @return storage after replace
     * @throws IndexOutOfBoundsException if {@code idx} is out of range
     */
    public ValueStorage<V> replace(int idx, V val) {
        checkIndexBounds(idx);

        var newVals = Arrays.copyOf(vals, vals.length);
        newVals[idx] = val;

        return new ValueStorage<>(newVals);
    }

    /**
     * Removes the value at {@code idx}, shifting later indices left.
     *
     * @param idx index to remove
     * @return storage after removal
     * @throws IndexOutOfBoundsException if {@code idx} is out of range
     */
    public ValueStorage<V> remove(int idx) {
        checkIndexBounds(idx);
        var newVals = (V[]) new Object[vals.length - 1];

        System.arraycopy(vals, 0, newVals, 0, idx);
        System.arraycopy(vals, idx + 1, newVals, idx, vals.length - idx - 1);

        return new ValueStorage<>(newVals);
    }

    /**
     * Fused remove then insert; same semantics as {@link KeyStorage#removeAndInsert(int, int, Object)}.
     *
     * @param removeIdx index removed first
     * @param insertIdx insertion index in the reduced sequence
     * @param val       value to insert
     * @return storage after remove then insert
     * @throws IndexOutOfBoundsException if an index is invalid for its step
     */
    public ValueStorage<V> removeAndInsert(int removeIdx, int insertIdx, V val) {
        checkIndexBounds(removeIdx);
        checkIndexBounds(insertIdx);

        var newVals = (V[]) new Object[vals.length];

        // prefix is unchanged before insertIdx or removeIdx
        var unchangedPrefixEnd = Math.min(removeIdx, insertIdx);
        System.arraycopy(vals, 0, newVals, 0, unchangedPrefixEnd);

        if (insertIdx > removeIdx) {
            System.arraycopy(vals, removeIdx + 1, newVals, removeIdx, insertIdx - removeIdx - 1);
            newVals[insertIdx] = val;
            System.arraycopy(vals, insertIdx, newVals, insertIdx + 1, vals.length - insertIdx);
        } else { // insert then remove, insertIdx == prefixEnd
            newVals[insertIdx] = val;
            System.arraycopy(vals, insertIdx, newVals, insertIdx + 1, removeIdx - insertIdx);
            System.arraycopy(vals, removeIdx + 1, newVals, removeIdx, vals.length - removeIdx - 1);
        }

        return new ValueStorage<>(newVals);
    }

    /**
     * Concatenates this sequence and {@code other} (this first). Same requirement
     * as {@link KeyStorage#merge(KeyStorage)}: paired with key merge when combining
     * leaves.
     *
     * @param other values to append after this storage
     * @return merged storage
     */
    public ValueStorage<V> merge(ValueStorage<V> other) {
        var newVals = (V[]) new Object[vals.length + other.size()];
        System.arraycopy(vals, 0, newVals, 0, vals.length);
        System.arraycopy(other.vals, 0, newVals, vals.length, other.size());

        return new ValueStorage<>(newVals);
    }

    /**
     * Inserts {@code val} at {@code idx}, then appends {@code other}. Same layout
     * as {@link KeyStorage#insertAndMerge(int, Object, KeyStorage)} for paired
     * key/value merge paths.
     *
     * @param insertIdx index at which to insert before merging
     * @param val       inserted value
     * @param other     tail values to append
     * @return merged storage
     * @throws IndexOutOfBoundsException if {@code insertIdx} is invalid for insert
     */
    public ValueStorage<V> insertAndMerge(int insertIdx, V val, ValueStorage<V> other) {
        checkInsertBounds(insertIdx);

        var otherVals = other.vals;
        var newVals = (V[]) new Object[vals.length + otherVals.length + 1];

        System.arraycopy(vals, 0, newVals, 0, insertIdx);
        newVals[insertIdx] = val;
        System.arraycopy(vals, insertIdx, newVals, insertIdx + 1, vals.length - insertIdx);

        System.arraycopy(otherVals, 0, newVals, vals.length + 1, otherVals.length);

        return new ValueStorage<>(newVals);
    }

    /**
     * Fused insert then split; mirrors {@link KeyStorage#insertAndSplit(int, int, Object)}.
     * Use with {@link KeySplit} from the same operation so left/right value runs
     * match left/right keys.
     *
     * @param insertIdx index for the inserted value in the pre-insert sequence
     * @param splitIdx  split index in the post-insert sequence
     * @param val       value inserted before the split
     * @return left and right value storages
     * @throws IndexOutOfBoundsException if an index is invalid for insert or split
     */
    public ValueSplit<V> insertAndSplit(int insertIdx, int splitIdx, V val) {
        checkInsertBounds(insertIdx);
        checkSplitBounds(splitIdx);

        if (insertIdx >= splitIdx) {
            var leftVals = (V[]) new Object[splitIdx];
            System.arraycopy(vals, 0, leftVals, 0, splitIdx);

            var rightVals = (V[]) new Object[vals.length - splitIdx + 1];
            var prefixLen = insertIdx - splitIdx;
            var suffixLen = vals.length - insertIdx;

            System.arraycopy(vals, splitIdx, rightVals, 0, prefixLen);
            rightVals[prefixLen] = val;
            System.arraycopy(vals, insertIdx, rightVals, prefixLen + 1, suffixLen);

            return new ValueSplit<>(
                    new ValueStorage<>(leftVals),
                    new ValueStorage<>(rightVals)
            );
        }

        var leftVals = (V[]) new Object[splitIdx + 1];
        System.arraycopy(vals, 0, leftVals, 0, insertIdx);
        leftVals[insertIdx] = val;
        System.arraycopy(vals, insertIdx, leftVals, insertIdx + 1, splitIdx - insertIdx);

        var splitIdxAfterInsertion = splitIdx - 1;
        var rightVals = (V[]) new Object[vals.length - splitIdxAfterInsertion];
        System.arraycopy(vals, splitIdxAfterInsertion, rightVals, 0, rightVals.length);

        return new ValueSplit<>(
                new ValueStorage<>(leftVals),
                new ValueStorage<>(rightVals)
        );
    }

    private void checkSplitBounds(int idx) {
        if (idx <= 0 || idx > size()) {
            throw new IndexOutOfBoundsException("Index " + idx + " is out of bounds for split: " + "(" + 0 + " " + size() + ")");
        }
    }

    private void checkInsertBounds(int idx) {
        if (idx < 0 || idx > size()) {
            throw new IndexOutOfBoundsException("Index " + idx + " is out of bounds for insert: " + "[" + 0 + " " + size() + ")");
        }
    }

    private void checkIndexBounds(int idx) {
        if (idx < 0 || idx >= size()) {
            throw new IndexOutOfBoundsException("Index " + idx + " is out of bounds: " + "[" + 0 + " " + size() + ")");
        }
    }
}
