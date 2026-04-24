package io.dsal.versioned.index.api;

/**
 * Immutable point-in-time read view over committed index state.
 *
 * <p>A snapshot is created from committed state and provides snapshot isolation:
 * every read from the same snapshot observes the same logical version.
 *
 * <p>Subsequent commits in the owning index do not change this snapshot.
 *
 * <p>Snapshots are intended for stable multi-step reads, including iteration
 * and range scans, without observing write-time changes from later commits.
 *
 * @param <K> key type
 * @param <V> value type
 */
public interface Snapshot<K, V> extends ReadView<K, V> {
}
