package org.e2immu.analyser.parser.functional.testexample;

import java.util.List;
import java.util.function.IntFunction;
import java.util.function.Predicate;

public class Lambda_16 {
    public static String[] same1(List<String> list, String mask) {
        return list
                .stream().filter(s -> mask.startsWith(s))
                .toArray(n -> new String[n]);
    }

    public static String[] same2(List<String> list, String mask) {
        return list
                .stream().filter(mask::startsWith)
                .toArray(String[]::new);
    }

    public static String[] same3(List<String> list, String mask) {
        Predicate<String> stringPredicate = new Predicate<String>() {
            @Override
            public boolean test(String s) {
                return mask.startsWith(s);
            }
        };
        IntFunction<String[]> intFunction = new IntFunction<String[]>() {
            @Override
            public String[] apply(int value) {
                return new String[value];
            }
        };
        return list.stream().filter(stringPredicate).toArray(intFunction);
    }

}
