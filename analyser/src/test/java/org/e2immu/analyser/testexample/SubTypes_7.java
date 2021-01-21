package org.e2immu.analyser.testexample;

import org.e2immu.annotation.Identity;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NotNull;

import java.util.Iterator;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

public class SubTypes_7 {

    static class Example7<T> {
        final BiFunction<Integer, T, String> method = (i, t) -> {
            return i + t.toString();
        };
    }

    static class Example8<T> {
        final BiFunction<Integer, T, String> method2 = new BiFunction<Integer, T, String>() {
            @Override
            public String apply(Integer i, T t) {
                return i + t.toString();
            }
        };
    }
}
