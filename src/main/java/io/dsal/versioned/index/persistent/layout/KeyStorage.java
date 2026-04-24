package io.dsal.versioned.index.persistent.layout;

/**
 * Ordered key sequence used by the B+ tree: positional access, index-based
 * comparison, and structural transforms on keys alone.
 *
 * <p>{@code KeyStorage} is not a generic collection API; it is the tree's
 * <em>key column</em> primitive. It encodes how keys are ordered, how they are
 * compared at a given index, and how the sequence is split, merged, or updated
 * when nodes change shape.</p>
 *
 * <h2>Ordering</h2>
 *
 * <p>Keys are maintained in <strong>strictly sorted</strong> order. This type
 * extends {@link IndexedComparator} so the tree can compare stored keys by index
 * against search keys; full semantics of {@code compare} are documented there.
 * {@link #key(int)} supplies the key at an index for navigation.</p>
 *
 * <p>The comparator must define a strict weak ordering (transitive, consistent).
 * If ordering is violated by callers (e.g. bad insert or replace), behavior of
 * the tree is undefined.</p>
 *
 * <h2>Indexed sequence (conceptual)</h2>
 *
 * <pre>
 *   Index:     0      1      2      3
 *   Keys:    [ k0  |  k1  |  k2  |  k3 ]     k0 &lt; k1 &lt; k2 &lt; k3
 *
 *   compare(i, key)  --  k_i  vs  key  (see {@link IndexedComparator})
 *   {@link #key(int)}(i)  returns  k_i
 * </pre>
 *
 * <h2>Mutability</h2>
 *
 * <p>Implementations may be in-place or copy-on-write; each method must return
 * the correct logical result. Callers use the returned instance as the new state;
 * reuse of the previous storage is an implementation detail.</p>
 *
 * <p>Default methods (e.g. fused {@code insert} + {@code split}, {@code remove} +
 * {@code insert}, {@code insert} + {@code merge}) are defined as the sequential
 * composition of primitive operations; concrete types may implement them with a
 * single pass or fewer allocations as long as the observable result matches.</p>
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>Local correctness of keyed sequence and transformations</li>
 *   <li>Index bounds where stated</li>
 * </ul>
 *
 * <p>Node-level min/max fan-out and other B+ tree constraints are enforced above
 * this layer. Concrete before/after diagrams for each operation are on the
 * corresponding methods below.</p>
 *
 * <h2>Exceptions and undefined behavior</h2>
 *
 * <ul>
 *   <li>Out-of-range indices: implementations typically throw
 *       {@link IndexOutOfBoundsException}</li>
 *   <li>Ordering violations are not required to be detected and may yield
 *       undefined behavior</li>
 *   <li>Null keys: {@code null} handling is implementation-defined unless stated</li>
 * </ul>
 *
 * @param <K> key type
 * @see IndexedComparator
 * @see KeySplit
 */
public interface KeyStorage<K> extends IndexedComparator<K> {

    /**
     * Returns the key at {@code idx} in the sorted sequence.
     *
     * <p>Does not modify storage.</p>
     *
     * @param idx index in {@code [0, size())}
     * @return key at {@code idx}
     * @throws IndexOutOfBoundsException if {@code idx} is out of range
     *                                   (implementation-defined exact type)
     */
    K key(int idx);

    /**
     * Returns storage with {@code key} inserted at {@code idx}, shifting keys at
     * and after {@code idx} to the right. Size increases by one.
     *
     * <p>The caller must choose {@code idx} so that sorting is preserved (typically
     * the insertion index from a lower-bound search). This method does not validate
     * global ordering beyond index bounds.</p>
     *
     * <pre>
     *   Before:  [ k0 | k1 | k2 | k3 ]     size = 4
     *   insert(2, X)  inserts between index 1 and 2 (X sorts between k1 and k2)
     *   After:   [ k0 | k1 | X | k2 | k3 ]   size = 5
     * </pre>
     *
     * @param idx insertion position in {@code [0, size()]}
     * @param key key to insert
     * @return storage after insertion
     * @throws IndexOutOfBoundsException if {@code idx} is out of range for insert
     */
    KeyStorage<K> insert(int idx, K key);

    /**
     * Returns storage with the key at {@code idx} removed, shifting keys after
     * {@code idx} left. Size decreases by one.
     *
     * <p>Does not enforce minimum size; the tree enforces fill rules.</p>
     *
     * <pre>
     *   Before:  [ k0 | k1 | k2 | k3 ]
     *   remove(1)
     *   After:   [ k0 | k2 | k3 ]        keys after index 1 shift left
     * </pre>
     *
     * @param idx index of key to remove in {@code [0, size())}
     * @return storage after removal
     * @throws IndexOutOfBoundsException if {@code idx} is out of range
     */
    KeyStorage<K> remove(int idx);

    /**
     * Returns storage with the key at {@code idx} replaced by {@code key}. Size
     * unchanged.
     *
     * <p>The caller must ensure the new key keeps the sequence sorted relative to
     * neighbors. Replacing with a value that is out of order relative to adjacent
     * keys breaks tree invariants and is undefined behavior.</p>
     *
     * <pre>
     *   Before:  [ k0 | k1 | k2 ]
     *   replace(1, Y)     length unchanged; k1 and Y must keep order vs k0, k2
     *   After:   [ k0 | Y | k2 ]
     * </pre>
     *
     * @param idx index to replace in {@code [0, size())}
     * @param key new key value
     * @return storage after replace
     * @throws IndexOutOfBoundsException if {@code idx} is out of range
     */
    KeyStorage<K> replace(int idx, K key);

    /**
     * Splits this storage into a left part, a right part, and a promoted separator
     * for the parent.
     *
     * <p>Keys with indices {@code [0, idx)} go to {@link KeySplit#left()}; keys
     * with indices {@code [idx, size())} go to {@link KeySplit#right()}.
     * {@link KeySplit#promotedKey()} is the smallest key of the right part (the
     * new boundary for the parent), matching typical B+ node split usage.</p>
     *
     * <p>No keys are lost or duplicated; concatenating left then right keys in
     * order equals the original sequence.</p>
     *
     * <pre>
     *   Original:  [ k0 | k1 | k2 | k3 | k4 ]     indices 0 .. 4
     *   split(2):
     *     left()   = [ k0 | k1 ]                 indices 0 .. 1
     *     right()  = [ k2 | k3 | k4 ]              indices 0 .. 2 in right storage
     *     promotedKey() = k2  (minimum key of the right part; parent separator)
     * </pre>
     *
     * @param idx split position (valid range depends on implementation; typically
     *            {@code 0 &lt; idx &lt; size()} when both sides are non-empty)
     * @return left storage, right storage, and promoted key
     * @throws IndexOutOfBoundsException if {@code idx} is not a valid split index
     */
    KeySplit<K> split(int idx);

    /**
     * Splits this storage into a left part, a right part, and a promoted separator
     * for the parent, <em>around</em> the key at {@code idx}.
     *
     * <p>Keys with indices {@code [0, idx)} go to {@link KeySplit#left()}; keys with
     * indices {@code [idx + 1, size())} go to {@link KeySplit#right()}. The key at
     * {@code idx} appears in neither child sequence; {@link KeySplit#promotedKey()} is
     * that key (the parent separator).
     *
     * <pre>
     *   Original:  [ k0 | k1 | k2 | k3 ]     indices 0 .. 3
     *   splitAround(1):
     *     left()   = [ k0 ]
     *     right()  = [ k2 | k3 ]
     *     promotedKey() = k1
     * </pre>
     *
     * @param idx index of the key split around (excluded from left and right; becomes
     *            {@link KeySplit#promotedKey()})
     * @return left storage, right storage, and promoted key
     * @throws IndexOutOfBoundsException if {@code idx} is not a valid split index
     */
    KeySplit<K> splitAround(int idx);

    /**
     * Returns storage containing all keys of this sequence followed by all keys of
     * {@code other}, in order.
     *
     * <p>The caller must ensure every key in this storage is less than or equal to
     * every key in {@code other} (no interleaving). If that fails, the merged
     * sequence is not sorted and behavior is undefined.</p>
     *
     * <pre>
     *   this:   [ 10 | 20 ]
     *   other:  [ 30 | 40 | 50 ]     every key in this &lt;= every key in other
     *
     *   merge(other)  --&gt;  [ 10 | 20 | 30 | 40 | 50 ]
     * </pre>
     *
     * @param other keys to append after this storage's keys
     * @return merged storage
     * @throws IllegalArgumentException if implementations reject incompatible
     *                                  storage types (optional)
     */
    KeyStorage<K> merge(KeyStorage<K> other);

    /**
     * Fused {@link #insert insert(int, K)} then {@link #split(int)}: inserts
     * {@code key} at {@code insertIdx}, then splits the result at {@code splitIdx}.
     *
     * <p>Semantically equivalent to {@code insert(insertIdx, key).split(splitIdx)}.
     * The split index applies to the storage <em>after</em> insertion. Default
     * implementations follow that order; optimized implementations may fuse
     * work but must match the same observable result.</p>
     *
     * <pre>
     *   Before insert:  [ 10 | 20 | 30 | 40 ]     size 4, indices 0 .. 3
     *   insert(2, 25)   --&gt;  [ 10 | 20 | 25 | 30 | 40 ]     size 5; splitIdx counts this row
     *   split(3)        --&gt;  left()  = [ 10 | 20 | 25 ]
     *                        right() = [ 30 | 40 ]
     *                        promotedKey() = 30
     *
     *   Equivalent to:  insert(insertIdx, key).split(splitIdx)
     * </pre>
     *
     * @param insertIdx index at which to insert {@code key} in the pre-insert
     *                  sequence
     * @param splitIdx  split index in the post-insert sequence
     * @param key       key to insert
     * @return split result after insert
     */
    default KeySplit<K> insertAndSplit(int insertIdx, int splitIdx, K key) {
        return insert(insertIdx, key).split(splitIdx);
    }

    /**
     * Fused {@link #insert insert(int, K)} then {@link #splitAround(int)}: inserts
     * {@code key} at {@code insertIdx}, then splits around {@code splitIdx} on the
     * post-insert sequence.
     *
     * <p>Semantically equivalent to {@code insert(insertIdx, key).splitAround(splitIdx)}.
     * The split-around index applies to the storage <em>after</em> insertion. Default
     * implementations follow that order; optimized implementations may fuse work but must
     * match the same observable result.</p>
     *
     * <pre>
     *   Before insert:  [ 10 | 20 | 30 | 40 ]     size 4, indices 0 .. 3
     *   insert(1, 15)   --&gt;  [ 10 | 15 | 20 | 30 | 40 ]     size 5
     *   splitAround(3)  --&gt;  per {@link #splitAround(int)} on that row
     *
     *   Equivalent to:  insert(insertIdx, key).splitAround(splitIdx)
     * </pre>
     *
     * @param insertIdx index at which to insert {@code key} in the pre-insert
     *                  sequence
     * @param splitIdx  split-around index in the post-insert sequence
     * @param key       key to insert
     * @return split result after insert and split-around
     */
    default KeySplit<K> insertAndSplitAround(int insertIdx, int splitIdx, K key) {
        return insert(insertIdx, key).splitAround(splitIdx);
    }

    /**
     * Fused {@link #remove(int)} then {@link #insert insert(int, K)}: removes the
     * key at {@code removeIdx}, then inserts {@code key} at {@code insertIdx} in
     * the <em>reduced</em> sequence.
     *
     * <p>Semantically equivalent to {@code remove(removeIdx).insert(insertIdx, key)}.
     * The insertion index is interpreted after removal (size is one less). Used
     * when rebalancing moves a key from one index to another in one logical step.</p>
     *
     * <pre>
     *   Before:  [ 10 | 20 | 30 | 40 ]
     *   remove(1)     --&gt;  [ 10 | 30 | 40 ]     reduced sequence, indices 0 .. 2
     *   insert(1, 25) --&gt;  [ 10 | 25 | 30 | 40 ]     insertIdx is in the reduced row
     *
     *   Equivalent to:  remove(removeIdx).insert(insertIdx, key)
     * </pre>
     *
     * @param removeIdx index of key to remove first
     * @param insertIdx index after removal at which to insert {@code key}
     * @param key       key to insert
     * @return storage after remove then insert
     */
    default KeyStorage<K> removeAndInsert(int removeIdx, int insertIdx, K key) {
        return remove(removeIdx).insert(insertIdx, key);
    }

    /**
     * Fused {@link #insert insert(int, K)} then {@link #merge(KeyStorage)}: inserts
     * {@code key} at {@code insertIdx}, then merges with {@code other}.
     *
     * <p>Semantically equivalent to {@code insert(insertIdx, key).merge(other)}.
     * Typical use is B+ internal-node merge: the inserted {@code key} is often the
     * parent separator between two child key runs, and {@code other} holds the
     * sibling's keys being concatenated.</p>
     *
     * <pre>
     *   this:   [ 10 | 20 ]
     *   other:  [ 30 | 40 ]     max(this) &lt;= min(other)
     *   insert(2, 25)   --&gt;  [ 10 | 20 | 25 ]     index 2 appends; 25 often parent separator
     *   merge(other)    --&gt;  [ 10 | 20 | 25 | 30 | 40 ]
     *
     *   Equivalent to:  insert(insertIdx, key).merge(other)
     * </pre>
     *
     * @param insertIdx index at which to insert {@code key} before merging
     * @param key       key to insert; often the parent separator in merge paths
     * @param other     keys to merge after the insert
     * @return merged storage
     */
    default KeyStorage<K> insertAndMerge(int insertIdx, K key, KeyStorage<K> other) {
        return insert(insertIdx, key).merge(other);
    }
}
