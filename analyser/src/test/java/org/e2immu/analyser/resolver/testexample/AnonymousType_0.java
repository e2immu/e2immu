package org.e2immu.analyser.resolver.testexample;

import java.util.List;
import java.util.function.Predicate;

public class AnonymousType_0 {

    private String mask;

    public static List<String> method(List<String> list, String mask) {
        Predicate<String> stringPredicate = new Predicate<String>() {
            @Override
            public boolean test(String s) {
                // must be the 'mask' parameter, not the field!
                return !s.isEmpty() && s.charAt(0) == mask.charAt(0);
            }
        };
        return list.stream().filter(stringPredicate).toList();
    }

    class NonStatic {
        @Override
        public String toString() {
            // at the same time, the 'mask' field must be accessible
            return mask;
        }
    }
}
