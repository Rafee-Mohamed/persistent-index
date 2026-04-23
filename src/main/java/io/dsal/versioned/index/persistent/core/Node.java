package io.dsal.versioned.index.persistent.core;

import io.dsal.versioned.index.persistent.PersistentBPlusTree;
import io.dsal.versioned.index.persistent.layout.KeyStorage;
import io.dsal.versioned.index.persistent.layout.ValueStorage;

/**
 * Discriminated union of B+ tree nodes: internal (routing) vs leaf (payload).
 * Structural invariants are enforced by {@link io.dsal.versioned.index.core.PersistentBPlusTree}; storage
 * primitives are {@link KeyStorage}, {@link Children}, and {@link ValueStorage}.
 *
 * <pre>
 *   Internal:   keys [ k0 | k1 | ... ] children  c0  c1  c2  ...   ({@code children.size() == keys.size() + 1})
 *   Leaf:       keys [ ... ]           values    (same length as keys)
 * </pre>
 *
 * @param <K> key type
 * @param <V> value type
 * @see PersistentBPlusTree
 */
public sealed interface Node<K, V> {

    KeyStorage<K> keys();

    final class Internal<K, V> implements Node<K, V> {
        private final KeyStorage<K> keys;
        private final Children<K, V> children;

        public Internal(KeyStorage<K> keys, Children<K, V> children) {
            this.keys = keys;
            this.children = children;
        }

        @Override
        public KeyStorage<K> keys() {
            return keys;
        }

        public Children<K, V> children() {
            return children;
        }


    }

    final class Leaf<K, V> implements Node<K, V> {
        private final KeyStorage<K> keys;
        private final ValueStorage<V> values;

        public Leaf(KeyStorage<K> keys, ValueStorage<V> values) {
            this.keys = keys;
            this.values = values;
        }

        @Override
        public KeyStorage<K> keys() {
            return keys;
        }

        public ValueStorage<V> values() {
            return values;
        }
    }
}
