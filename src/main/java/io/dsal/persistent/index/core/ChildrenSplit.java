package io.dsal.persistent.index.core;

public record ChildrenSplit<K, V>(
        Children<K, V> left,
        Children<K, V> right
) {
}
