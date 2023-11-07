package org.e2immu.analyser.resolver.testexample;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

// test is here because the crash shows in the method reference "contains"; problem is probably in forwarding of type info
public class MethodReference_3 {
    public static boolean isEmpty(Object[] array) {
        return array == null || array.length == 0;
    }

    // everything is fine when .parallel is excluded, or the 2nd part of the disjunction is left out
    // k must be a String (T of Stream = String; with parallel in between: = T)
    public Set<String> method(Set<String> keysWithPrefix, String[] pattern) {
        return keysWithPrefix
                .stream()
                .parallel()
                .filter(k -> isEmpty(pattern) || Arrays.stream(pattern).parallel().anyMatch(k::contains))
                .collect(Collectors.toSet());
    }
}
