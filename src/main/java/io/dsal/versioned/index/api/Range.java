package io.dsal.versioned.index.api;

public record  Range<K>(
        K from,
        K to,
        RangeType type
) {

    public static <K> Range<K> closed(K from, K to) {
        return new Range<>(from, to, RangeType.CLOSED);
    }

    public static <K> Range<K> open(K from, K to) {
        return new Range<>(from, to, RangeType.OPEN);
    }

    public static <K> Range<K> closedOpen(K from, K to) {
        return new Range<>(from, to, RangeType.CLOSED_OPEN);
    }

    public static <K> Range<K> openClosed(K from, K to) {
        return new Range<>(from, to, RangeType.OPEN_CLOSED);
    }
}
