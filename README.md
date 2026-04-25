# versioned-index

A versioned ordered key-value index designed for single-writer, multi-reader workloads, with atomic multi-operation transactions and lock-free snapshot reads. **Readers don't block the writer, the writer doesn't block readers.**

## OrderedVersionedIndex

`OrderedVersionedIndex<K, V>` is the core abstraction. It is built for systems where writes are serialized by design and reads are concurrent and frequent. Writer publishes writes atomically. Concurrent readers observe a complete, stable state with no locks or coordination.

It combines ordered reads, atomic multi-operation transactions, and snapshot isolation under a non-blocking reader-writer contract.

A `Txn<K, V>` accumulates mutations privately, exposes read-your-own-writes across all read APIs, and makes all changes atomically visible on `commit()`.

A `Snapshot<K, V>` is a stable, immutable read view of a specific committed state, unaffected by subsequent writes or commits.

Only one writer may hold an open transaction at a time. Concurrent readers require no coordination or locks.


## PersistentBPlusTree

`PersistentBPlusTree<K, V>` is the provided implementation of `OrderedVersionedIndex<K, V>`, built on a copy-on-write B+ tree.

### Architecture

The index is a copy-on-write B+ tree where every write rebuilds only the affected root-to-leaf path, reusing untouched subtrees via structural sharing. Commit is a single atomic pointer publish. Readers that captured a prior root continue on a stable, immutable view for the lifetime of their operation - no locks, no coordination.

Multi-operation transactions track which nodes have already been copied into an exclusive set. Subsequent mutations to the same node within the same transaction mutate in-place rather than copying again. This makes a multi-write transaction cheaper than the equivalent number of sequential single-operation writes.

![B+ Tree Copy-on-Write](docs/images/persistent_bplus_tree.svg)

**Write**

```java
var txn = index.txn();
txn.put(85, 'value');
txn.commit()
```
1. Copy only the affected path: `R0 -> [82 | 93] -> [82 87 91]`
2. Insert into the copied leaf, producing `[82 85 87 91]`
3. Commit the new root pointer `R1`

**Read behavior**
- Readers holding `R0` snapshot see a stable structure for the lifetime of their operation
- New reads after `R1` is published observe the updated state

### Pluggable key storage

Tree logic is decoupled from key representation through the `KeyStorage` interface. Two implementations are provided:

- **`ArrayKeyStorage`** - keys in an `Object[]`, ordered by a `Comparator`. General-purpose baseline.
- **`PackedByteKeyStorage`** - all keys in a node packed into a single `byte[]` with an offset table. Reduces heap pointer indirection and object overhead for byte-key workloads.

New storage representations require only a new `KeyStorage` implementation. The tree algorithms are unaffected.


## Testing

The implementation is validated through four tiers:

- **Stateful property-based tests** - jqwik generates randomized action chains of `Put`, `Remove`, `Range`, `Iterate`, and `TxnMultiOp` across 1000 runs with `maxKeys` sampled between 2 and 10. Every action is verified against a `TreeMap` backed oracle. Both storage implementations are tested independently.
- **Seeded deterministic stress tests** - 20,000 randomized operations against the tree and a `TreeMap` oracle in parallel, with periodic full-scan and structural checks. Fixed seed makes failures fully reproducible.
- **Structural invariant tests** - after puts and removes, the tree is walked to assert key ordering, separator alignment, uniform leaf depth, and fill bounds on every node.
- **Unit tests** - behavioral assertions covering specific operations and scenarios in isolation, including CRUD, range correctness, transaction read-your-own-writes, and snapshot isolation, across maxKeys 2-8 for both storage implementations.


## Usage

### Creating an index

```java
import io.dsal.versioned.index.api.OrderedVersionedIndex;
import io.dsal.versioned.index.persistent.PersistentBPlusTree;
import io.dsal.versioned.index.persistent.layout.ArrayKeyStorageFactory;

OrderedVersionedIndex<String, UserSession> index = new PersistentBPlusTree<>(
        8,
        new ArrayKeyStorageFactory<>(String::compareTo)
);
```

### Point reads

```java
Optional<UserSession> session = index.get("user:1001");
boolean exists = index.contains("user:1001");
int     count  = index.size();
```

### Iteration and range reads

Both `forEach` and `iterator` support a direction and an optional `Range` that restricts the key interval.

| Direction       | Order                |
|-----------------|----------------------|
| `Direction.ASC` | ascending key order  |
| `Direction.DESC`| descending key order |

| Range type         | Lower bound | Upper bound | Interval   |
|--------------------|-------------|-------------|------------|
| `closed`           | inclusive   | inclusive   | [from, to] |
| `open`             | exclusive   | exclusive   | (from, to) |
| `closedOpen`       | inclusive   | exclusive   | [from, to) |
| `openClosed`       | exclusive   | inclusive   | (from, to] |

```java
import io.dsal.versioned.index.api.Direction;
import io.dsal.versioned.index.api.Range;

// full scan ascending
index.forEach(Direction.ASC, (k, v) -> System.out.println(k + " -> " + v));

// range scan descending, closed [user:1000, user:1999]
index.forEach(
        Direction.DESC, 
        Range.closed("user:1000", "user:1999"),
        (k, v) -> System.out.println(k + " -> " + v)
);

// half-open range iterator [user:1000, user:2000)
var it = index.iterator(Direction.ASC, Range.closedOpen("user:1000", "user:2000"));
while (it.hasNext()) {
var entry = it.next();
    System.out.println(entry.key() + " -> " + entry.val());
}
```

### Transactions

```java
// explicit transaction
var txn = index.txn();
txn.put("user:1001", new UserSession("noah",   Role.ADMIN));
txn.put("user:1002", new UserSession("sophia", Role.EDITOR));
txn.remove("user:1000");

// read-your-own-writes before commit
Optional<UserSession> local = txn.get("user:1001"); // present
int size = txn.size();                               // reflects all txn mutations

txn.commit(); // atomic: all changes or none
```

The `txn(TxnAction)` and `txn(TxnBlock)` overloads scope a transaction to a lambda and commit automatically:

```java
// action form - no return value
index.txn(txn -> {
        txn.put("user:1001", new UserSession("noah",   Role.ADMIN));
        txn.put("user:1002", new UserSession("sophia", Role.EDITOR));
        txn.remove("user:1000");
});

// block form - returns a value
Optional<UserSession> previous = index.txn(txn -> {
    txn.remove("user:1000");
    return txn.put("user:1001", new UserSession("noah", Role.ADMIN));
});
```

### Snapshots

```java
// pin the current committed state
var snap = index.snapshot();

index.put("user:1003", new UserSession("alex", Role.VIEWER));

snap.get("user:1003"); // Optional.empty() - snap is unaffected
snap.forEach(Direction.ASC, (k, v) -> process(k, v));
```

A snapshot captured from inside a transaction reflects the committed state at the time the transaction was opened:

```java
var txn = index.txn();
var base = txn.snapshot(); // committed state before this txn

txn.put("user:1004", new UserSession("sam", Role.ADMIN));
txn.commit();

base.get("user:1004"); // Optional.empty()
```

### Single writer, multiple readers

```java
final OrderedVersionedIndex<String, UserSession> index = new PersistentBPlusTree<>(
        8, new ArrayKeyStorageFactory<>(String::compareTo));

Thread writer = new Thread(() ->
        index.txn(txn -> {
            txn.put("user:1001", new UserSession("noah",   Role.ADMIN));
            txn.put("user:1002", new UserSession("sophia", Role.EDITOR));
            txn.remove("user:1000");
        }));

Thread reader1 = new Thread(() ->
        index.snapshot().forEach(Direction.ASC, (k, v) -> process(k, v)));

Thread reader2 = new Thread(() ->
        index.snapshot().forEach(Direction.ASC,
                Range.closed("user:1000", "user:1999"), (k, v) -> process(k, v)));

writer.start();
reader1.start();
reader2.start();
```

### Packed byte key storage

```java
import io.dsal.versioned.index.persistent.layout.PackedByteKeyStorageFactory;
import io.dsal.versioned.index.persistent.layout.LexigographicPackedByteComparator;
import java.nio.charset.StandardCharsets;

OrderedVersionedIndex<byte[], ServiceRoute> index = new PersistentBPlusTree<>(
        8,
        new PackedByteKeyStorageFactory(new LexigographicPackedByteComparator())
);

index.txn(txn -> {
        txn.put("/api/users".getBytes(StandardCharsets.UTF_8),  new ServiceRoute("user-service",  8080));
        txn.put("/api/orders".getBytes(StandardCharsets.UTF_8), new ServiceRoute("order-service", 8081));
        txn.put("/api/search".getBytes(StandardCharsets.UTF_8), new ServiceRoute("search-service",8082));
});

index.forEach(
        Direction.ASC,
        Range.closedOpen("/api/".getBytes(UTF_8), "/api/z".getBytes(UTF_8)),
        (k, v) -> System.out.println(new String(k) + " -> " + v)
);
```


## Roadmap

- **Performance benchmarking** - JMH benchmarks against `TreeMap` with read-write locks and `ConcurrentSkipListMap` under SWMR workloads
- **`NavigableMap` compatibility** - implement the standard Java interface for interoperability
- **Prefix-compressed key storage** - a third `KeyStorage` implementation for byte-key workloads with common prefixes


## Background

`versioned-index` was built as the indexing layer for [Axis](https://github.com/Rafee-Mohamed/axis) - a fault-tolerant, strongly consistent distributed key-value store backed by Raft, designed for cluster coordination, distributed locking, and metadata management.

Axis maintains an inverted index of user keys to internal revisions to serve point and range queries. Raft serializes writes via a single leader, enforcing total order and making the system inherently SWMR. Readers need stable snapshots to answer queries consistently against an established state, without being affected by concurrent writes. Rather than reaching for read-write locks, this index was built to match the workload directly: a copy-on-write structure where readers hold lock-free snapshots and the writer publishes atomically. It was later extracted from Axis for independent development and use.


## Requirements

Java 25+

## License

TBD
