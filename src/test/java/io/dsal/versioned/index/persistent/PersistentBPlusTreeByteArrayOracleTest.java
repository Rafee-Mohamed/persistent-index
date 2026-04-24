package io.dsal.versioned.index.persistent;

import io.dsal.versioned.index.api.AbstractOrderedIndexOracleTest;
import io.dsal.versioned.index.api.OrderedVersionedIndex;
import io.dsal.versioned.index.persistent.testsupport.BPlusTreeValidator;
import io.dsal.versioned.index.persistent.testsupport.IndexTestSupport;
import io.dsal.versioned.index.persistent.testsupport.TreeMapOracle;
import io.dsal.versioned.index.persistent.testsupport.TreeTestAccess;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class PersistentBPlusTreeByteArrayOracleTest extends AbstractOrderedIndexOracleTest<byte[], String> {

    @Override
    protected Stream<OrderedVersionedIndex<byte[], String>> indices() {
        return IntStream.rangeClosed(2, 8)
                .mapToObj(n -> new PersistentBPlusTree<>(n, IndexTestSupport.byteArrayKeyStorageFactory()));
    }

    @Override
    protected TreeMapOracle<byte[], String> newOracle() {
        return new TreeMapOracle<>(IndexTestSupport.byteArrayComparator());
    }

    @Override
    protected byte[] key(int i) {
        return IndexTestSupport.intToBytes(i);
    }

    @Override
    protected String val(int i) {
        return "v" + i;
    }

    @Override
    protected int keySpace() {
        return 64;
    }

    @Override
    protected void assertEntryListsEqual(
            List<Map.Entry<byte[], String>> actual,
            List<Map.Entry<byte[], String>> expected) {
        assertThat(actual.size()).isEqualTo(expected.size());
        var cmp = IndexTestSupport.byteArrayComparator();
        for (int i = 0; i < actual.size(); i++) {
            assertThat(cmp.compare(actual.get(i).getKey(), expected.get(i).getKey())).isZero();
            assertThat(actual.get(i).getValue()).isEqualTo(expected.get(i).getValue());
        }
    }

    @Override
    protected void validateStructure(OrderedVersionedIndex<byte[], String> index) {
        if (index instanceof PersistentBPlusTree<byte[], String> tree) {
            BPlusTreeValidator.validate(
                    TreeTestAccess.root(tree),
                    IndexTestSupport.byteArrayComparator(),
                    TreeTestAccess.maxKeys(tree),
                    TreeTestAccess.minKeys(tree));
        }
    }
}
