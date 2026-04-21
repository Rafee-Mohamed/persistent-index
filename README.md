# persistent-index

A persistent, ordered index built on a copy-on-write B+ tree, designed for single-writer, multi-reader workloads enabling lock-free consistent snapshot reads during concurrent writes - **Readers don't block the writer, the writer doesn't block readers**.

> **On the name:** "Persistent" is used in the immutable data structures sense - each write produces a new version while prior versions remain valid. This is not a disk-backed store.


## Overview

`persistent-index` is built for systems where writes are serialized by design and reads are concurrent and frequent. Writer publishes writes atomically. Concurrent readers observe a complete, stable state with no locks or coordination.

The index supports point lookup, inclusive range reads, ordered iteration, insert with replace semantics, and delete with value-return semantics. Structural invariants are preserved throughout all write operations.


## Architecture

### Copy-on-write and SWMR

The index is a B+ tree where every write rebuilds only the affected root-to-leaf path, reusing untouched subtrees through structural sharing. A new root is published atomically. Readers that captured a previous root continue on a stable, immutable snapshot for the lifetime of their operation - no locks, no coordination required.

Multiple concurrent readers need no synchronization. Multiple concurrent writers are not coordinated - the intended model is a single external writer, matching workloads where writes are already serialized upstream.

![B+ Tree Copy-on-Write](docs/images/persistent_bplus_tree.svg)

**Write: put(85)**
1. Copy only the affected path: `R0 → [82 | 93] → [82 87 91]`
2. Insert into the copied leaf, producing `[82 85 87 91]`
3. Publish the new root pointer `R1`

**Read behavior**
- Readers holding `R0` see a stable structure for the lifetime of their operation
- New reads after `R1` is published observe the updated snapshot

### Pluggable key storage

Tree logic is decoupled from key representation through the `KeyStorage` interface - it defines how keys are stored, compared, and transformed during splits and merges. Two implementations are provided:

- **`ArrayKeyStorage`** - keys in an `Object[]`, ordered by a `Comparator`. General purpose baseline.
- **`PackedByteKeyStorage`** - all keys in a node packed into a single `byte[]` with an offset table. Reduces heap pointer indirection and object overhead for byte-key workloads. Designed for cache-friendly node scans.

New storage representations - such as prefix-compressed keys - require only a new `KeyStorage` implementation. The tree algorithms are unaffected.


## Testing

The implementation is validated through three tiers:

- **Stateful property-based tests** - jqwik generates randomized action chains of `Put`, `Get`, `Remove`, `Range`, and `Iterate` across 1000 runs, each with a randomly sampled `maxKeys` between 2 and 10. Every action is verified against a `TreeMap`-backed oracle. Low `maxKeys` values force deep trees and aggressive rebalancing. Both storage implementations are tested independently.
- **Seeded deterministic stress tests** - 20,000 operations against the tree and oracle in parallel, with periodic full iteration and structural checks. Fixed seed makes failures fully reproducible.
- **Structural invariant validation** - after operations, the tree is walked to assert key ordering, separator alignment, uniform leaf depth, and fill bounds on every node.


## Usage

### Creating an index

```java
import io.dsal.versioned.index.core.PersistentBPlusTree;
import io.dsal.versioned.index.layout.ArrayKeyStorageFactory;

var index = new PersistentBPlusTree<String, UserSession>(
        8,
        new ArrayKeyStorageFactory<>(String::compareTo)
);
```

### Writing

```java
index.put("user:1001", new UserSession("noah",   Role.ADMIN));
index.put("user:1002", new UserSession("sophia", Role.VIEWER));
index.put("user:1002", new UserSession("sophia", Role.EDITOR)); // replaces; returns previous
index.remove("user:1001");                                       // returns removed session
```

### Reading

```java
var session = index.get("user:1002");

var activeSessions = index.range("user:1000", "user:1999"); // inclusive range
```

### Iteration

```java
for (var entry : index) {
    System.out.println(entry.key() + " -> " + entry.val());
}

var it = index.rangeIterator("user:1000", "user:1999");
while (it.hasNext()) {
    var entry = it.next();
    System.out.println(entry.key() + " -> " + entry.val());
}
```

Both `iterator()` and `rangeIterator(K, K)` capture the current root at creation time and are unaffected by subsequent writes.

### Single writer, multiple readers

```java
final var index = new PersistentBPlusTree<String, UserSession>(
        8,
        new ArrayKeyStorageFactory<>(String::compareTo)
);

Thread writer = new Thread(() -> {
    index.put("user:1001", new UserSession("noah",   Role.ADMIN));
    index.put("user:1002", new UserSession("sophia", Role.EDITOR));
    index.remove("user:1000");
});

Thread reader1 = new Thread(() -> {
    var snapshot = index.iterator();
    while (snapshot.hasNext()) snapshot.next();
});

Thread reader2 = new Thread(() -> {
    var snapshot = index.rangeIterator("user:1000", "user:1999");
    while (snapshot.hasNext()) snapshot.next();
});

writer.start();
reader1.start();
reader2.start();
```

### Packed byte key storage

```java
import io.dsal.versioned.index.layout.PackedByteKeyStorageFactory;
import io.dsal.versioned.index.layout.LexigographicPackedByteComparator;
import java.nio.charset.StandardCharsets;

var index = new PersistentBPlusTree<byte[], ServiceRoute>(
        8,
        new PackedByteKeyStorageFactory(new LexigographicPackedByteComparator())
);

index.put("/api/users".getBytes(StandardCharsets.UTF_8),  new ServiceRoute("user-service",   8080));
index.put("/api/orders".getBytes(StandardCharsets.UTF_8), new ServiceRoute("order-service",  8081));
index.put("/api/search".getBytes(StandardCharsets.UTF_8), new ServiceRoute("search-service", 8082));
```

## Roadmap

- **Performance benchmarking** - JMH benchmarks against `TreeMap` with read-write locks and `ConcurrentSkipListMap` under SWMR workloads
- **`NavigableMap` compatibility** - implement the standard Java interface for interoperability and discoverability
- **Pinned read views** - explicit snapshot objects that expose the full read API against a fixed tree version
- **Batched writes** - accumulate multiple mutations into a single path-copying pass, reducing redundant copies and providing atomic all-or-none visibility for readers
- **Prefix-compressed key storage** - a third `KeyStorage` implementation for byte-key workloads with common prefixes, reducing memory and comparison cost


## Background

`persistent-index` was built as the indexing layer for [Axis](https://github.com/Rafee-Mohamed/axis) - a fault-tolerant, strongly consistent distributed key-value store backed by Raft, designed for cluster coordination, distributed locking, and metadata management.

Axis maintains an inverted index of user keys to internal revisions to serve point and range queries. Raft serializes writes via a single leader, enforcing total order and making the system inherently SWMR. Readers need stable snapshots to answer queries consistently against an established state, without being affected by concurrent writes. Rather than reaching for read-write locks, this index was built to match the workload directly: a copy-on-write structure where readers hold lock-free snapshots and the writer publishes atomically. It was later extracted from Axis for independent development and use.


## Requirements

Java 25+

## License

TBD