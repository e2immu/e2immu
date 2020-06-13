package org.e2immu.analyser.util;

import org.junit.Test;

import java.util.List;

public class TestZipWithIndex {

    @Test
    public void test() {
        List<String> list1 = List.of("a", "b", "c", "d");
        ZipWithIndex.streamWithIndex(list1)
                .filter(wi -> wi.index % 2 == 0)
                .forEach(wi -> System.out.println(wi.index + " = " + wi.t));
    }
}
