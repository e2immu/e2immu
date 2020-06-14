package org.e2immu.analyser.util.stream;

import org.junit.Test;

import java.util.List;

public class TestPairsInListWithIndex {

    @Test
    public void test() {
        List<String> list1 = List.of("a", "b", "c", "d");
        PairsInListWithIndex.stream(list1).forEach(System.out::println);
    }
}
