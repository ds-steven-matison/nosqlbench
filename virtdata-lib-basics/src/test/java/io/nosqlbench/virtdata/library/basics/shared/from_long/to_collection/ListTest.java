package io.nosqlbench.virtdata.library.basics.shared.from_long.to_collection;

import io.nosqlbench.virtdata.library.basics.shared.from_long.to_int.HashRange;
import org.junit.Test;

import java.util.function.LongFunction;

import static org.assertj.core.api.Assertions.assertThat;

public class ListTest {

    @Test
    public void testList() {
        io.nosqlbench.virtdata.library.basics.shared.from_long.to_collection.List lf =
                new io.nosqlbench.virtdata.library.basics.shared.from_long.to_collection.List(new HashRange(2, 3), (LongFunction) (l) -> "_" + l);
        java.util.List<Object> l1 = lf.apply(2L);
        assertThat(l1).containsExactly("_2","_3","_4");
    }

    @Test
    public void testStringList() {
        io.nosqlbench.virtdata.library.basics.shared.from_long.to_collection.StringList slf = new io.nosqlbench.virtdata.library.basics.shared.from_long.to_collection.StringList((s) -> 2,(v)->v);
        java.util.List<String> sl1 = slf.apply(13L);
        assertThat(sl1).containsExactly("13","14");
    }
}