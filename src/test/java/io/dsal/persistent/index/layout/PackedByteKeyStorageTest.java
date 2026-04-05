package io.dsal.persistent.index.layout;

import io.dsal.persistent.index.testsupport.KeyStorageTestSupport;
import org.junit.jupiter.api.Test;

import static io.dsal.persistent.index.testsupport.KeyStorageTestSupport.LEXICOGRAPHIC_BYTE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Packed-byte-specific behavior not covered by {@link KeyStorageContractTest}.
 */
class PackedByteKeyStorageTest {

    @Test
    void keyReturnsCopy() {
        var ks = PackedByteKeyStorage.of(new byte[]{1, 2}, LEXICOGRAPHIC_BYTE);
        byte[] k0 = ks.key(0);
        k0[0] = 99;
        assertThat(ks.key(0)[0]).isEqualTo((byte) 1);
    }

    @Test
    void variableLengthKeys() {
        var empty = new byte[0];
        var a = new byte[]{1};
        var ab = new byte[]{1, 2};
        var ks = PackedByteKeyStorage.of(empty, LEXICOGRAPHIC_BYTE)
                .insert(1, a)
                .insert(2, ab);
        assertThat(ks.size()).isEqualTo(3);
        assertThat(ks.key(0).length).isZero();
        assertThat(ks.key(2)[1]).isEqualTo((byte) 2);
    }

    @Test
    void insertRemoveReplaceRoundtrip() {
        var ks = KeyStorageTestSupport.packedSorted(new byte[][]{{1}, {2, 3}, {4}});
        var ins = ks.insert(1, new byte[]{9});
        assertThat(ins.size()).isEqualTo(4);
        var rem = ins.remove(0);
        assertThat(rem.size()).isEqualTo(3);
        var rep = rem.replace(0, new byte[]{7, 8});
        assertThat(rep.key(0)).containsExactly((byte) 7, (byte) 8);
    }

    @Test
    void lexicographicOrderMatchesUnsigned() {
        var c = new LexigographicPackedByteComparator();
        byte[] buf = new byte[]{0, (byte) 0xFF};
        assertThat(c.compare(buf, 1, 2, new byte[]{(byte) 0xFF})).isZero();
        assertThat(c.compare(buf, 1, 2, new byte[]{0, 1})).isPositive();
    }
}
