package org.e2immu.analyser.testexample;

import java.util.Collection;
import java.util.function.Predicate;
import java.util.stream.IntStream;

public class StreamChecks {

    public static void main(String... args) {
        print(10);
        System.out.println(appendRange2(11));
    }

    public static void print(int n) {
        IntStream.range(0, n).forEach(System.out::println);
    }

    public static <T> T find(Collection<T> ts, Predicate<T> predicate) {
        return ts.stream().filter(predicate).findFirst().orElse(null);
    }

    public static String appendRange(int n) {
        StringBuilder sb = new StringBuilder();
        IntStream.range(0, n).peek(System.out::println).forEach(sb::append);
        return sb.toString();
    }

    public static String appendRange2(int n) {
        return IntStream.range(0, n)
                .peek(System.out::println)
                .collect(StringBuilder::new,
                        StringBuilder::append,
                        StringBuilder::append).toString();
    }
}
