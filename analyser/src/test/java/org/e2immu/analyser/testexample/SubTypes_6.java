package org.e2immu.analyser.testexample;

import org.e2immu.annotation.Identity;
import org.e2immu.annotation.Modified;
import org.e2immu.annotation.NotNull;

import java.util.Iterator;
import java.util.Set;
import java.util.function.Function;

public class SubTypes_6 {

    private static Function<Set<String>, Set<String>> removeElement =
            (@NotNull @Modified Set<String> set) -> {
                Iterator<String> it1 = set.iterator();
                if (it1.hasNext()) it1.remove();
                return set;
            };

    // an alternative approach is to use an interface, which also allows for the @Identity

    @FunctionalInterface
    interface RemoveOne {
        @Identity
        Set<String> go(@NotNull @Modified Set<String> in);
    }

    final static RemoveOne removeOne = set -> {
        Iterator<String> it2 = set.iterator();
        if (it2.hasNext()) it2.remove();
        return set;
    };

}
