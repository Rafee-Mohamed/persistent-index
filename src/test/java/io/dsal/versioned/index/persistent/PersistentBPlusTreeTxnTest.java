package io.dsal.versioned.index.persistent;

import io.dsal.versioned.index.api.AbstractOrderedIndexTxnTest;
import io.dsal.versioned.index.api.OrderedVersionedIndex;
import io.dsal.versioned.index.persistent.testsupport.IndexTestSupport;

import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

class PersistentBPlusTreeTxnTest extends AbstractOrderedIndexTxnTest {

    @Override
    protected Stream<Supplier<OrderedVersionedIndex<Integer, String>>> indexFactories() {
        return IntStream.rangeClosed(2, 8)
                .mapToObj(n -> () -> new PersistentBPlusTree<>(n, IndexTestSupport.integerKeyStorageFactory()));
    }
}
