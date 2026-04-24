package io.dsal.versioned.index.persistent.testsupport;

import io.dsal.versioned.index.api.Direction;
import io.dsal.versioned.index.api.Range;
import io.dsal.versioned.index.api.RangeType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

public final class TreeMapOracle<K, V> {

    private final TreeMap<K, V> map;
    private final Comparator<? super K> cmp;

    public TreeMapOracle(Comparator<? super K> keyOrder) {
        this.cmp = keyOrder;
        this.map = new TreeMap<>(keyOrder);
    }

    public Optional<V> put(K key, V value) {
        return Optional.ofNullable(map.put(key, value));
    }

    public Optional<V> remove(K key) {
        return Optional.ofNullable(map.remove(key));
    }

    public Optional<V> get(K key) {
        return Optional.ofNullable(map.get(key));
    }

    public boolean contains(K key) {
        return map.containsKey(key);
    }

    public int size() {
        return map.size();
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }

    public List<Map.Entry<K, V>> allEntries(Direction direction) {
        var out = new ArrayList<Map.Entry<K, V>>(map.size());
        if (direction == Direction.ASC) {
            out.addAll(map.entrySet());
        } else {
            out.addAll(map.descendingMap().entrySet());
        }
        return out;
    }

    public List<Map.Entry<K, V>> range(Range<K> range, Direction direction) {
        boolean fromInclusive = range.type() == RangeType.CLOSED || range.type() == RangeType.CLOSED_OPEN;
        boolean toInclusive   = range.type() == RangeType.CLOSED || range.type() == RangeType.OPEN_CLOSED;

        int cmpResult = cmp.compare(range.from(), range.to());
        if (cmpResult > 0) {
            return List.of();
        }
        if (cmpResult == 0 && (!fromInclusive || !toInclusive)) {
            return List.of();
        }

        var subMap = map.subMap(range.from(), fromInclusive, range.to(), toInclusive);
        var out = new ArrayList<Map.Entry<K, V>>(subMap.size());
        if (direction == Direction.ASC) {
            out.addAll(subMap.entrySet());
        } else {
            out.addAll(subMap.descendingMap().entrySet());
        }
        return out;
    }
}
