package io.dsal.persistent.index.layout;

public interface IndexedComparator<K> {
    int size();
    int compare(int idx, K key);
}
