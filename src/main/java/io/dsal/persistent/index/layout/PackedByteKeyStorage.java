package io.dsal.persistent.index.layout;

import java.util.Arrays;

/**
 * {@link KeyStorage} for variable-length {@code byte[]} keys stored in one
 * contiguous {@code byte[]} plus an offset table: key {@code i} occupies
 * {@code keys[offsets[i]) .. keys[offsets[i+1])}. Mutations allocate new
 * backing
 * arrays. {@link #key(int)} returns a copy of that range.
 *
 * <p>
 * {@link #compare(int, byte[])} forwards slices to
 * {@link PackedByteComparator}.
 * {@link #merge(KeyStorage)} and
 * {@link #insertAndMerge(int, byte[], KeyStorage)}
 * require the other storage to be {@code PackedByteKeyStorage} with the same
 * comparator semantics.
 * </p>
 *
 * @see PackedByteKeyStorageFactory
 */
public class PackedByteKeyStorage implements KeyStorage<byte[]> {

    // key at ith position -> keys[offsets[i] ... offsets[i+1])
    private final byte[] keys; // size k
    private final int[] offsets; // size k + 1
    private final PackedByteComparator comparator;

    /**
     * @param keys       packed bytes for all keys in order; not to be mutated by
     *                   callers after construction
     * @param offsets    length {@code keyCount + 1}; {@code offsets[i]} through
     *                   {@code offsets[i+1]} bound key {@code i}
     * @param comparator used for {@link #compare(int, byte[])}
     */
    public PackedByteKeyStorage(byte[] keys, int[] offsets, PackedByteComparator comparator) {
        this.keys = keys;
        this.offsets = offsets;
        this.comparator = comparator;
    }

    static PackedByteKeyStorage of(byte[] key, PackedByteComparator comparator) {
        var offsets = new int[] { 0, key.length };
        return new PackedByteKeyStorage(Arrays.copyOf(key, key.length), offsets, comparator);
    }

    @Override
    public int size() {
        return offsets.length - 1;
    }

    @Override
    public byte[] key(int idx) {
        checkIndexBounds(idx);
        return Arrays.copyOfRange(keys, offsets[idx], offsets[idx + 1]);
    }

    @Override
    public int compare(int idx, byte[] key) {
        checkIndexBounds(idx);
        return comparator.compare(keys, offsets[idx], offsets[idx + 1], key);
    }

    @Override
    public KeyStorage<byte[]> insert(int idx, byte[] key) {
        checkInsertBounds(idx);

        var newKeysSize = keys.length + key.length;
        var newKeys = new byte[newKeysSize];
        var insertPos = offsets[idx];

        System.arraycopy(keys, 0, newKeys, 0, insertPos);
        System.arraycopy(key, 0, newKeys, insertPos, key.length);
        System.arraycopy(keys, insertPos, newKeys, insertPos + key.length, keys.length - insertPos);

        var newOffsets = new int[offsets.length + 1];

        System.arraycopy(offsets, 0, newOffsets, 0, idx + 1);
        for (var i = idx; i < offsets.length; i++) {
            newOffsets[i + 1] = offsets[i] + key.length;
        }

        return new PackedByteKeyStorage(newKeys, newOffsets, comparator);
    }

    @Override
    public KeyStorage<byte[]> remove(int idx) {
        checkIndexBounds(idx);
        var removedKeyLen = offsets[idx + 1] - offsets[idx];
        var newKeys = new byte[keys.length - removedKeyLen];
        System.arraycopy(keys, 0, newKeys, 0, offsets[idx]);
        System.arraycopy(keys, offsets[idx + 1], newKeys, offsets[idx], keys.length - offsets[idx + 1]);

        var newOffsets = new int[offsets.length - 1];
        System.arraycopy(offsets, 0, newOffsets, 0, idx + 1);

        for (var i = idx + 1; i < newOffsets.length; i++) {
            newOffsets[i] = offsets[i + 1] - removedKeyLen;
        }

        return new PackedByteKeyStorage(newKeys, newOffsets, comparator);
    }

    @Override
    public KeyStorage<byte[]> replace(int idx, byte[] key) {

        var replacedKeyLen = offsets[idx + 1] - offsets[idx];
        var newKeys = new byte[keys.length - replacedKeyLen + key.length];

        System.arraycopy(keys, 0, newKeys, 0, offsets[idx]);
        System.arraycopy(key, 0, newKeys, offsets[idx], key.length);
        System.arraycopy(
                keys,
                offsets[idx + 1],
                newKeys,
                offsets[idx] + key.length,
                keys.length - offsets[idx + 1]);

        var newOffsets = new int[offsets.length];

        System.arraycopy(offsets, 0, newOffsets, 0, idx + 1);
        newOffsets[idx + 1] = newOffsets[idx] + key.length;

        for (var i = idx + 2; i < offsets.length; i++) {
            newOffsets[i] = offsets[i] - replacedKeyLen + key.length;
        }

        return new PackedByteKeyStorage(newKeys, newOffsets, comparator);
    }

    @Override
    public KeySplit<byte[]> split(int idx) {
        checkSplitBounds(idx);
        var leftKeys = new byte[offsets[idx]];
        var leftOffsets = new int[idx + 1];

        System.arraycopy(keys, 0, leftKeys, 0, leftKeys.length);
        System.arraycopy(offsets, 0, leftOffsets, 0, leftOffsets.length);

        var rightKeys = new byte[keys.length - offsets[idx]];
        var rightOffsets = new int[offsets.length - idx];

        System.arraycopy(keys, offsets[idx], rightKeys, 0, rightKeys.length);

        for (var i = idx; i < offsets.length; i++) {
            rightOffsets[i - idx] = offsets[i] - offsets[idx];
        }

        var leftKeyStorage = new PackedByteKeyStorage(leftKeys, leftOffsets, comparator);
        var rightKeyStorage = new PackedByteKeyStorage(rightKeys, rightOffsets, comparator);

        return new KeySplit<>(leftKeyStorage, rightKeyStorage, rightKeyStorage.key(0));
    }

    public KeyStorage<byte[]> copy(int from, int to) {
        if (from > to) {
            throw new IllegalArgumentException("Invalid range for copy [from, to]: [" + from + ", " + to + "]");
        }

        checkIndexBounds(from);
        checkIndexBounds(to - 1);

        var newKeys = new byte[offsets[to] - offsets[from]];
        var newOffsets = new int[to - from + 1];

        System.arraycopy(keys, offsets[from], newKeys, 0, newKeys.length);

        for (var i = from; i <= to; i++) {
            newOffsets[i - from] = offsets[i] - offsets[from];
        }

        return new PackedByteKeyStorage(newKeys, newOffsets, comparator);
    }

    @Override
    public KeySplit<byte[]> splitAround(int idx) {
        checkSplitBounds(idx);
        var leftKeys = new byte[offsets[idx]];
        var leftOffsets = new int[idx + 1];

        System.arraycopy(keys, 0, leftKeys, 0, leftKeys.length);
        System.arraycopy(offsets, 0, leftOffsets, 0, leftOffsets.length);

        var rightKeys = new byte[keys.length - offsets[idx + 1]];
        var rightOffsets = new int[offsets.length - idx - 1];

        System.arraycopy(keys, offsets[idx + 1], rightKeys, 0, rightKeys.length);

        for (var i = idx + 1; i < offsets.length; i++) {
            rightOffsets[i - idx - 1] = offsets[i] - offsets[idx + 1];
        }

        var leftKeyStorage = new PackedByteKeyStorage(leftKeys, leftOffsets, comparator);
        var rightKeyStorage = new PackedByteKeyStorage(rightKeys, rightOffsets, comparator);

        return new KeySplit<>(leftKeyStorage, rightKeyStorage, key(idx));
    }

    @Override
    public KeyStorage<byte[]> merge(KeyStorage<byte[]> other) {
        if (!(other instanceof PackedByteKeyStorage otherStorage)) {
            throw new IllegalArgumentException("Incompatible KeyStorage to merge");
        }

        var otherKeys = otherStorage.keys;
        var otherOffsets = otherStorage.offsets;

        var newKeys = new byte[keys.length + otherKeys.length];
        System.arraycopy(keys, 0, newKeys, 0, keys.length);
        System.arraycopy(otherKeys, 0, newKeys, keys.length, otherKeys.length);

        var newOffsets = new int[offsets.length + otherOffsets.length - 1];
        System.arraycopy(offsets, 0, newOffsets, 0, offsets.length);

        for (var i = 0; i < otherOffsets.length - 1; i++) {
            newOffsets[offsets.length + i] = keys.length + otherOffsets[i + 1];
        }

        return new PackedByteKeyStorage(newKeys, newOffsets, comparator);
    }

    @Override
    public KeySplit<byte[]> insertAndSplit(int insertIdx, int splitIdx, byte[] key) {
        checkInsertBounds(insertIdx);
        checkSplitBounds(splitIdx);

        // ['a', 'b', 'c', 'e']
        // [0, 1, 2, 3, 4]
        // key = 'd'
        // insertIdx = 3
        // splitIdx = 2

        if (insertIdx >= splitIdx) {
            var leftKeys = new byte[offsets[splitIdx]];
            System.arraycopy(keys, 0, leftKeys, 0, leftKeys.length);

            var leftOffsets = new int[splitIdx + 1];
            System.arraycopy(offsets, 0, leftOffsets, 0, leftOffsets.length);

            var leftKeyStorage = new PackedByteKeyStorage(leftKeys, leftOffsets, comparator);

            var rightKeys = new byte[keys.length - offsets[splitIdx] + key.length];

            // prefix, newKey, suffix
            // offsets -> [splitIdx, insertIdx) [insertIdx, insertIdx + key.length)
            // [insertIdx + key.length, keys.length + key.length)

            var prefixStart = offsets[splitIdx];
            var newKeyStart = offsets[insertIdx];
            var prefixLen = newKeyStart - prefixStart;
            var suffixStart = prefixLen + key.length;
            var suffixLen = keys.length - newKeyStart;

            System.arraycopy(keys, prefixStart, rightKeys, 0, prefixLen);
            System.arraycopy(key, 0, rightKeys, prefixLen, key.length);
            System.arraycopy(keys, newKeyStart, rightKeys, suffixStart, suffixLen);

            var rightOffsets = new int[offsets.length - splitIdx + 1];

            // prefix offset
            for (var i = splitIdx; i <= insertIdx; i++) {
                rightOffsets[i - splitIdx] = offsets[i] - prefixStart;
            }

            // insert offset
            rightOffsets[insertIdx - splitIdx + 1] = rightOffsets[insertIdx - splitIdx] + key.length;

            // suffix offset
            for (var i = insertIdx + 1; i < offsets.length; i++) {
                rightOffsets[i - splitIdx + 1] = offsets[i] - prefixStart + key.length;
            }

            var rightKeyStorage = new PackedByteKeyStorage(rightKeys, rightOffsets, comparator);

            return new KeySplit<>(leftKeyStorage, rightKeyStorage, rightKeyStorage.key(0));
        }

        var leftKeys = new byte[offsets[splitIdx] + key.length];

        var prefixStart = 0;
        var prefixLen = offsets[insertIdx];
        var suffixStart = prefixLen + key.length;
        var suffixLen = offsets[splitIdx] - prefixLen;

        System.arraycopy(keys, prefixStart, leftKeys, 0, prefixLen);
        System.arraycopy(key, 0, leftKeys, prefixLen, key.length);
        System.arraycopy(keys, prefixLen, leftKeys, suffixStart, suffixLen);

        var leftOffsets = new int[splitIdx + 1];

        // prefix offset
        System.arraycopy(offsets, 0, leftOffsets, 0, insertIdx + 1);

        // insert offset
        leftOffsets[insertIdx + 1] = leftOffsets[insertIdx] + key.length;

        // suffix offset
        for (var i = insertIdx + 1; i < splitIdx; i++) {
            leftOffsets[i + 1] = offsets[i] + key.length;
        }

        var leftKeyStorage = new PackedByteKeyStorage(leftKeys, leftOffsets, comparator);

        var splitIdxAfterInsertion = splitIdx - 1;
        var rightKeys = new byte[keys.length - offsets[splitIdxAfterInsertion]];
        System.arraycopy(keys, offsets[splitIdxAfterInsertion], rightKeys, 0, rightKeys.length);

        var rightOffsets = new int[offsets.length - splitIdxAfterInsertion];
        for (var i = 0; i < rightOffsets.length; i++) {
            rightOffsets[i] = offsets[i + splitIdxAfterInsertion] - offsets[splitIdxAfterInsertion];
        }

        var rightKeyStorage = new PackedByteKeyStorage(rightKeys, rightOffsets, comparator);

        // splitIdx is checked to be within bounds - 0 < splitIdx <= size
        // therefore, at splitIdx a key will be present which is the first key
        // of rightKeyStorage, so key(0) won't fail.
        return new KeySplit<>(leftKeyStorage, rightKeyStorage, rightKeyStorage.key(0));
    }

    @Override
    public KeySplit<byte[]> insertAndSplitAround(int insertIdx, int splitIdx, byte[] key) {
        if (insertIdx == splitIdx) {
            return new KeySplit<>(
                    copy(0, splitIdx),
                    copy(splitIdx, size()),
                    key);
        }

        checkInsertBounds(insertIdx);
        checkSplitBounds(splitIdx);

        if (insertIdx > splitIdx) {
            var leftKeys = new byte[offsets[splitIdx]];
            System.arraycopy(keys, 0, leftKeys, 0, leftKeys.length);

            var leftOffsets = new int[splitIdx + 1];
            System.arraycopy(offsets, 0, leftOffsets, 0, leftOffsets.length);

            var leftKeyStorage = new PackedByteKeyStorage(leftKeys, leftOffsets, comparator);

            var rightKeys = new byte[keys.length - offsets[splitIdx + 1] + key.length];

            // prefix, newKey, suffix
            // offsets -> [splitIdx, insertIdx) [insertIdx, insertIdx + key.length)
            // [insertIdx + key.length, keys.length + key.length)

            var prefixStart = offsets[splitIdx + 1];
            var newKeyStart = offsets[insertIdx];
            var prefixLen = newKeyStart - prefixStart;
            var suffixStart = prefixLen + key.length;
            var suffixLen = keys.length - newKeyStart;

            System.arraycopy(keys, prefixStart, rightKeys, 0, prefixLen);
            System.arraycopy(key, 0, rightKeys, prefixLen, key.length);
            System.arraycopy(keys, newKeyStart, rightKeys, suffixStart, suffixLen);

            var rightOffsets = new int[offsets.length - splitIdx];

            // prefix offset
            for (var i = splitIdx + 1; i <= insertIdx; i++) {
                rightOffsets[i - splitIdx - 1] = offsets[i] - prefixStart;
            }

            // insert offset
            rightOffsets[insertIdx - splitIdx] = rightOffsets[insertIdx - splitIdx - 1] + key.length;

            // suffix offset
            for (var i = insertIdx + 1; i < offsets.length; i++) {
                rightOffsets[i - splitIdx] = offsets[i] - prefixStart + key.length;
            }

            var rightKeyStorage = new PackedByteKeyStorage(rightKeys, rightOffsets, comparator);

            return new KeySplit<>(leftKeyStorage, rightKeyStorage, key(splitIdx));
        }

        var leftKeys = new byte[offsets[splitIdx - 1] + key.length];

        var prefixLen = offsets[insertIdx];
        var suffixStart = prefixLen + key.length;
        var suffixLen = offsets[splitIdx - 1] - prefixLen;

        System.arraycopy(keys, 0, leftKeys, 0, prefixLen);
        System.arraycopy(key, 0, leftKeys, prefixLen, key.length);
        System.arraycopy(keys, prefixLen, leftKeys, suffixStart, suffixLen);

        var leftOffsets = new int[splitIdx + 1];

        // prefix offset
        System.arraycopy(offsets, 0, leftOffsets, 0, insertIdx + 1);

        // insert offset
        leftOffsets[insertIdx + 1] = leftOffsets[insertIdx] + key.length;

        // suffix offset
        for (var i = insertIdx + 1; i < splitIdx; i++) {
            leftOffsets[i + 1] = offsets[i] + key.length;
        }

        var leftKeyStorage = new PackedByteKeyStorage(leftKeys, leftOffsets, comparator);

        var rightKeys = new byte[keys.length - offsets[splitIdx]];
        System.arraycopy(keys, offsets[splitIdx], rightKeys, 0, rightKeys.length);

        var rightOffsets = new int[offsets.length - splitIdx];
        for (var i = 0; i < rightOffsets.length; i++) {
            rightOffsets[i] = offsets[i + splitIdx] - offsets[splitIdx];
        }

        var rightKeyStorage = new PackedByteKeyStorage(rightKeys, rightOffsets, comparator);

        return new KeySplit<>(leftKeyStorage, rightKeyStorage, key(splitIdx - 1));
    }

    @Override
    public KeyStorage<byte[]> removeAndInsert(int removeIdx, int insertIdx, byte[] key) {
        checkIndexBounds(removeIdx);
        checkIndexBounds(insertIdx);

        var removedKeyLen = offsets[removeIdx + 1] - offsets[removeIdx];
        var newKeys = new byte[keys.length - removedKeyLen + key.length];
        var newOffsets = new int[offsets.length];

        // prefix is unchanged before insertIdx or removeIdx
        var unchangedPrefixEnd = Math.min(removeIdx, insertIdx);
        System.arraycopy(keys, 0, newKeys, 0, offsets[unchangedPrefixEnd]);
        System.arraycopy(offsets, 0, newOffsets, 0, unchangedPrefixEnd + 1);

        // remove then insert, removeIdx == prefixEnd
        if (insertIdx > removeIdx) {
            // leave removed key and populate prefix before insert
            System.arraycopy(
                    keys,
                    offsets[removeIdx + 1],
                    newKeys,
                    offsets[removeIdx],
                    offsets[insertIdx + 1] - offsets[removeIdx + 1]);
            // insert the key
            System.arraycopy(key, 0, newKeys, offsets[insertIdx + 1] - removedKeyLen, key.length);
            // suffix after insert
            System.arraycopy(
                    keys,
                    offsets[insertIdx + 1],
                    newKeys,
                    offsets[insertIdx + 1] + key.length - removedKeyLen,
                    keys.length - offsets[insertIdx + 1]);

            for (var i = removeIdx + 1; i <= insertIdx + 1; i++) {
                newOffsets[i - 1] = offsets[i] - removedKeyLen;
            }

            newOffsets[insertIdx + 1] = newOffsets[insertIdx] + key.length;
            for (var i = insertIdx + 2; i < newOffsets.length; i++) {
                newOffsets[i] = offsets[i] - removedKeyLen + key.length;
            }
        } else { // insert then remove, insertIdx == prefixEnd
            // insert the key
            System.arraycopy(key, 0, newKeys, offsets[insertIdx], key.length);
            // populate the prefix before remove
            System.arraycopy(
                    keys,
                    offsets[insertIdx],
                    newKeys,
                    offsets[insertIdx] + key.length,
                    offsets[removeIdx] - offsets[insertIdx]);
            // suffix after remove
            System.arraycopy(
                    keys,
                    offsets[removeIdx + 1],
                    newKeys,
                    offsets[removeIdx] + key.length,
                    keys.length - offsets[removeIdx + 1]);

            newOffsets[insertIdx + 1] = newOffsets[insertIdx] + key.length;

            for (var i = insertIdx + 2; i <= removeIdx; i++) {
                newOffsets[i] = offsets[i] + key.length;
            }

            for (var i = removeIdx + 1; i < newOffsets.length; i++) {
                newOffsets[i] = offsets[i] - removedKeyLen + key.length;
            }

        }

        return new PackedByteKeyStorage(newKeys, newOffsets, comparator);
    }

    @Override
    public KeyStorage<byte[]> insertAndMerge(int insertIdx, byte[] key, KeyStorage<byte[]> other) {
        checkInsertBounds(insertIdx);

        if (!(other instanceof PackedByteKeyStorage otherStorage)) {
            throw new IllegalArgumentException("Incompatible KeyStorage to merge");
        }
        var otherKeys = otherStorage.keys;
        var otherOffsets = otherStorage.offsets;

        var newKeys = new byte[keys.length + key.length + otherKeys.length];

        if (insertIdx == size()) {
            System.arraycopy(keys, 0, newKeys, 0, keys.length);
            System.arraycopy(key, 0, newKeys, keys.length, key.length);
        } else {
            System.arraycopy(keys, 0, newKeys, 0, offsets[insertIdx]);
            System.arraycopy(key, 0, newKeys, offsets[insertIdx], key.length);
            System.arraycopy(
                    keys,
                    offsets[insertIdx],
                    newKeys,
                    offsets[insertIdx] + key.length,
                    keys.length - offsets[insertIdx]);
        }

        System.arraycopy(otherKeys, 0, newKeys, keys.length + key.length, otherKeys.length);

        var newOffsets = new int[offsets.length + otherOffsets.length];

        if (insertIdx == size()) {
            System.arraycopy(offsets, 0, newOffsets, 0, offsets.length);
            newOffsets[offsets.length] = newOffsets[offsets.length - 1] + key.length;
        } else {
            System.arraycopy(offsets, 0, newOffsets, 0, insertIdx + 1);
            newOffsets[insertIdx + 1] = newOffsets[insertIdx] + key.length;

            for (var i = insertIdx + 1; i < offsets.length; i++) {
                newOffsets[i + 1] = offsets[i] + key.length;
            }
        }

        for (var i = 1; i < otherOffsets.length; i++) {
            newOffsets[offsets.length + i] = newOffsets[offsets.length] + otherOffsets[i];
        }

        return new PackedByteKeyStorage(newKeys, newOffsets, comparator);
    }

    private void checkSplitBounds(int idx) {
        if (idx <= 0 || idx > size()) {
            throw new IndexOutOfBoundsException(
                    "Index " + idx + " is out of bounds for split: " + "(" + 0 + " " + size() + ")");
        }
    }

    private void checkInsertBounds(int idx) {
        if (idx < 0 || idx > size()) {
            throw new IndexOutOfBoundsException(
                    "Index " + idx + " is out of bounds for insert: " + "[" + 0 + " " + size() + ")");
        }
    }

    private void checkIndexBounds(int idx) {
        if (idx < 0 || idx >= size()) {
            throw new IndexOutOfBoundsException("Index " + idx + " is out of bounds: " + "[" + 0 + " " + size() + ")");
        }
    }
}
