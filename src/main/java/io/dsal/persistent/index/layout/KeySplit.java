package io.dsal.persistent.index.layout;

public record KeySplit<K>(
        KeyStorage<K> left,
        KeyStorage<K> right,
        K promotedKey
) {
}