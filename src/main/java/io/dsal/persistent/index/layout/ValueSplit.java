package io.dsal.persistent.index.layout;

public record ValueSplit<V>(
        ValueStorage<V> left,
        ValueStorage<V> right
) {
}
