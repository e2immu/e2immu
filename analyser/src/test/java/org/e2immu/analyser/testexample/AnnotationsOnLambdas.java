package org.e2immu.analyser.testexample;

import org.e2immu.annotation.Fluent;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NotNull;

import java.util.Iterator;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

import static org.e2immu.annotation.AnnotationType.VERIFY_ABSENT;

public class AnnotationsOnLambdas {
    // here we come in the

    // somehow we should add the @NotModified(absent=true)
    static Function<Set<String>, Set<String>> removeElement = set -> {
        Iterator<String> it1 = set.iterator();
        if (it1.hasNext()) it1.remove();
        return set;
    };

    @FunctionalInterface
    interface RemoveOne {
        @Fluent
        Set<String> go(@NotNull @NotModified(type = VERIFY_ABSENT) Set<String> in);
    }

    static RemoveOne removeOne = set -> {
        Iterator<String> it2 = set.iterator();
        if (it2.hasNext()) it2.remove();
        return set;
    };

    static class Example7<T> {
        BiFunction<Integer, T, String> method = (i, t) -> {
            return i + t.toString();
        };
    }

    static class Example8<T> {
        BiFunction<Integer, T, String> method2 = new BiFunction<Integer, T, String>() {
            @Override
            public String apply(Integer i, T t) {
                return i + t.toString();
            }
        };
    }
}
