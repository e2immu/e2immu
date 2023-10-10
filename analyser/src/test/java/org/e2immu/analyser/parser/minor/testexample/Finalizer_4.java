package org.e2immu.analyser.parser.minor.testexample;

import org.e2immu.annotation.Container;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public class Finalizer_4 {

    public static String method1(Stream<String> stream) {
        return stream.findFirst().orElse(null);
    }

    public static String method2(List<String> list) {
        return list.stream().findFirst().orElse(null);
    }

    public static String method2b(List<String> list) {
        Stream<String> stream = list.stream();
        return stream.findFirst().orElse(null);
    }


    @Container
    static class I {
        private final Set<String> set = new HashSet<>();

        public Set<String> getSet() {
            return set;
        }
    }

    public static void method3(List<I> list) {
        list.forEach(i -> i.set.add("s"));
    }

    public static void method3b(List<I> list) {
        Stream<I> stream = list.stream();
        stream.forEach(i -> i.getSet().add("s"));
    }
}
