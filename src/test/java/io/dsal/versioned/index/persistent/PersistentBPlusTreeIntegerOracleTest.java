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

class PersistentBPlusTreeIntegerOracleTest extends AbstractOrderedIndexOracleTest<Integer, String> {

    @Override
    protected Stream<OrderedVersionedIndex<Integer, String>> indices() {
        return IntStream.rangeClosed(2, 8)
                .mapToObj(n -> new PersistentBPlusTree<>(n, IndexTestSupport.integerKeyStorageFactory()));
    }

    @Override
    protected TreeMapOracle<Integer, String> newOracle() {
        return new TreeMapOracle<>(IndexTestSupport.INTEGER_COMPARATOR);
    }

    @Override
    protected Integer key(int i) {
        return i;
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
            List<Map.Entry<Integer, String>> actual,
            List<Map.Entry<Integer, String>> expected) {
        assertThat(actual).isEqualTo(expected);
    }

    @Override
    protected void validateStructure(OrderedVersionedIndex<Integer, String> index) {
        if (index instanceof PersistentBPlusTree<Integer, String> tree) {
            BPlusTreeValidator.validate(
                    TreeTestAccess.root(tree),
                    IndexTestSupport.INTEGER_COMPARATOR,
                    TreeTestAccess.maxKeys(tree),
                    TreeTestAccess.minKeys(tree));
        }
    }
}
