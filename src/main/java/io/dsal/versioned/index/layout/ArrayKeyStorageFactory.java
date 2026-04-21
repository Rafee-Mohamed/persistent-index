package io.dsal.versioned.index.layout;

import java.util.Comparator;

/**
 * {@link KeyStorageFactory} for {@link ArrayKeyStorage}: keys live in an
 * {@code Object[]} and ordering comes from a {@link Comparator}.
 *
 * @param <K> key type
 */
public class ArrayKeyStorageFactory<K> implements KeyStorageFactory<K> {
    private final Comparator<K> comparator;

    /**
     * @param comparator ordering for all keys produced by this factory; must match
     *                   how the tree searches and inserts
     */
    public ArrayKeyStorageFactory(Comparator<K> comparator) {
        this.comparator = comparator;
    }

    @Override
    public KeyStorage<K> single(K key) {
        return ArrayKeyStorage.of(key, comparator);
    }
}
