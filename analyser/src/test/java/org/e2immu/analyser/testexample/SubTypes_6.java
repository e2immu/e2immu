package org.e2immu.analyser.testexample;

import org.e2immu.annotation.Identity;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NotNull;

import java.util.Iterator;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

public class SubTypes_6 {
    // here we come in the

    // somehow we should add the @NotModified(absent=true)
    private static Function<Set<String>, Set<String>> removeElement = set -> {
        Iterator<String> it1 = set.iterator();
        if (it1.hasNext()) it1.remove();
        return set;
    };

    @FunctionalInterface
    interface RemoveOne {
        @Identity
        Set<String> go(@NotNull @NotModified(absent = true) Set<String> in);
    }

    final static RemoveOne removeOne = set -> {
        Iterator<String> it2 = set.iterator();
        if (it2.hasNext()) it2.remove();
        return set;
    };

}
