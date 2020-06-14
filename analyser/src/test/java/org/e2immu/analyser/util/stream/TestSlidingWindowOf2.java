package org.e2immu.analyser.util.stream;

import org.e2immu.analyser.util.stream.SlidingWindowOf2;
import org.junit.Test;

import java.util.List;

public class TestSlidingWindowOf2 {

    @Test
    public void test() {
        List<String> list1 = List.of("a", "b", "c", "d");
        SlidingWindowOf2.streamWith2(list1)
                .forEach(pc -> System.out.println(pc.previous + " => " + pc.current));
    }
}
