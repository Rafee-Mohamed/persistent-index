package io.dsal.versioned.index.persistent.core;

import io.dsal.versioned.index.persistent.PersistentBPlusTree;
import io.dsal.versioned.index.persistent.layout.KeyStorage;
import io.dsal.versioned.index.persistent.layout.ValueStorage;

import java.util.Set;

/**
 * Discriminated union of B+ tree nodes: internal (routing) vs leaf (payload).
 * Structural invariants are enforced by {@link io.dsal.versioned.index.persistent.core.PersistentBPlusTreeTxn};
 * storage primitives are {@link KeyStorage}, {@link Children}, and {@link ValueStorage}.
 *
 * <pre>
 *   Internal:  keys     [ k0 | k1 | ... ]
 *              children   c0   c1   c2  ...    (children.size() == keys.size() + 1)
 *
 *   Leaf:      keys     [ k0 | k1 | ... ]
 *              values   [ v0 | v1 | ... ]      (values.size() == keys.size())
 * </pre>
 *
 * <p>Node instances are not mutated in place by default. Within a transaction,
 * the exclusive-node optimization ({@link Internal#mutate} and {@link Leaf#mutate})
 * allows in-place field updates for nodes that were created or first copied inside
 * the same transaction, avoiding redundant path copies when the same node is
 * touched more than once.</p>
 *
 * @param <K> key type
 * @param <V> value type
 * @see PersistentBPlusTreeTxn
 */
public sealed interface Node<K, V> {

    /**
     * Returns the key sequence for this node.
     *
     * @return keys held by this node
     */
    KeyStorage<K> keys();

    /**
     * Internal (routing) node: holds separator keys and child pointers.
     *
     * <p>For a node with {@code n} keys there are {@code n + 1} children.
     * Child {@code i} contains all keys less than {@code keys[i]} (for
     * {@code i < n}) and child {@code n} contains all keys greater than
     * or equal to {@code keys[n - 1]}.</p>
     *
     * @param <K> key type
     * @param <V> value type
     */
    final class Internal<K, V> implements Node<K, V> {
        private KeyStorage<K> keys;
        private Children<K, V> children;

        /**
         * Creates an internal node with the given keys and child pointers.
         *
         * @param keys     separator key sequence; {@code children.size() == keys.size() + 1}
         * @param children child pointer array
         */
        public Internal(KeyStorage<K> keys, Children<K, V> children) {
            this.keys = keys;
            this.children = children;
        }

        /**
         * Returns the separator key sequence for this internal node.
         *
         * @return key sequence
         */
        @Override
        public KeyStorage<K> keys() {
            return keys;
        }

        /**
         * Returns the child pointer array for this internal node.
         *
         * @return child pointers; {@code size() == keys().size() + 1}
         */
        public Children<K, V> children() {
            return children;
        }

        /**
         * Returns this node updated to hold {@code keys} and {@code children},
         * reusing the same instance if it is already in {@code exclusive}
         * (created or first-copied within the current transaction), or
         * allocating a new {@code Internal<K, V>} and registering it otherwise.
         * Each node is copied at most once per transaction.
         *
         * @param keys      new key sequence
         * @param children  new child pointers
         * @param exclusive transaction-exclusive node set; updated on first copy
         * @return this instance if already exclusive, otherwise a new {@code Internal<K, V>}
         */
        public Internal<K, V> mutate(KeyStorage<K> keys, Children<K, V> children, Set<Node<K, V>> exclusive) {
            if (exclusive.contains(this)) {
                this.keys = keys;
                this.children = children;
                return this;
            }

            var exclusiveNode = new Node.Internal<>(keys, children);
            exclusive.add(exclusiveNode);
            return exclusiveNode;
        }

    }

    /**
     * Leaf (payload) node: holds keys and their associated values.
     *
     * <p>All stored entries are in this node type. {@code keys.size()} always
     * equals {@code values.size()}; key at index {@code i} pairs with value
     * at index {@code i}.</p>
     *
     * @param <K> key type
     * @param <V> value type
     */
    final class Leaf<K, V> implements Node<K, V> {
        private KeyStorage<K> keys;
        private ValueStorage<V> values;

        /**
         * Creates a leaf node with the given keys and values.
         *
         * @param keys   sorted key sequence
         * @param values value sequence aligned with {@code keys}
         */
        public Leaf(KeyStorage<K> keys, ValueStorage<V> values) {
            this.keys = keys;
            this.values = values;
        }

        /**
         * Returns the key sequence for this leaf node.
         *
         * @return key sequence
         */
        @Override
        public KeyStorage<K> keys() {
            return keys;
        }

        /**
         * Returns the value sequence for this leaf node.
         *
         * @return values aligned with {@link #keys()}
         */
        public ValueStorage<V> values() {
            return values;
        }

        /**
         * Returns this node updated to hold {@code keys} and {@code values},
         * reusing the same instance if it is already in {@code exclusive}
         * (created or first-copied within the current transaction), or
         * allocating a new {@code Leaf<K, V>} and registering it otherwise.
         * Each node is copied at most once per transaction.
         *
         * @param keys      new key sequence
         * @param values    new value sequence
         * @param exclusive transaction-exclusive node set; updated on first copy
         * @return this instance if already exclusive, otherwise a new {@code Leaf<K, V>}
         */
        public Leaf<K, V> mutate(KeyStorage<K> keys, ValueStorage<V> values, Set<Node<K, V>> exclusive) {
            if (exclusive.contains(this)) {
                this.keys = keys;
                this.values = values;
                return this;
            }

            var exclusiveNode = new Node.Leaf<>(keys, values);
            exclusive.add(exclusiveNode);
            return exclusiveNode;
        }
    }
}
