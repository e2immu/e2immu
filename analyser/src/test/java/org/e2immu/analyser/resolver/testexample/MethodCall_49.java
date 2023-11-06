package org.e2immu.analyser.resolver.testexample;

import java.util.Arrays;

public class MethodCall_49 {

    record R(String date) {
    }

    void method1(R[] rs) {
        // Arrays.sort(T[] ts, Comparator<? super T> c)
        Arrays.sort(rs, (r1, r2) -> r1.date.compareTo(r2.date));
    }

    void method2(R[] rs) {
        Arrays.sort(rs, (r1, r2) -> r1.date().compareTo(r2.date()));
    }
}
