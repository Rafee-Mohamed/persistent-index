package io.dsal.persistent.index.layout;

public interface KeyStorageFactory<K> {
    KeyStorage<K> single(K key);
}