package org.e2immu.analyser.resolver.testexample;

import java.util.Set;
import java.util.stream.Collectors;

public class MethodReference_3B {
    public static boolean isEmpty(Object[] array) {
        return array == null || array.length == 0;
    }


    public Set<String> method(Set<String> keysWithPrefix) {
        return keysWithPrefix
                .stream()
                .parallel()
                .filter("abc"::contains)
                .collect(Collectors.toSet());
    }
}
