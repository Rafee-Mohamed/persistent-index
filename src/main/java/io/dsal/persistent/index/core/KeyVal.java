package io.dsal.persistent.index.core;

public record KeyVal<K, V>(K key, V val) {
    static <K, V> KeyVal<K, V> of(K key, V val) {
        return new KeyVal<>(key, val);
    }
}
