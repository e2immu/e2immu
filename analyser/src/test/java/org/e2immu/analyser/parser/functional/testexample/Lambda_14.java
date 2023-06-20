package org.e2immu.analyser.parser.functional.testexample;

import java.util.List;
import java.util.function.Predicate;

public class Lambda_14 {

    public static List<String> same1(List<String> list) {
        Predicate<String> stringPredicate = s -> !s.isEmpty() && s.charAt(0) == 'A';
        return list.stream().filter(stringPredicate).toList();
    }

    public static List<String> same2(List<String> list) {
        Predicate<String> stringPredicate = new Predicate<String>() {
            @Override
            public boolean test(String s) {
                return !s.isEmpty() && s.charAt(0) == 'A';
            }
        };
        return list.stream().filter(stringPredicate).toList();
    }

}
