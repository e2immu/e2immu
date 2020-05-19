package org.e2immu.analyser.testexample.withannotatedapi;

import org.e2immu.annotation.E1Immutable;
import org.e2immu.annotation.E2Container;
import org.e2immu.annotation.Size;

import java.util.Set;

@E2Container
public class SimpleSizeChecks {

    @Size(equals = 2)
    private final Set<Integer> intSet;

    public SimpleSizeChecks(int a) {
        intSet = Set.of(a, 1);
    }

    @Size(equals = 1)
    public static Set<String> method1() {
        Set<String> set = Set.of();
        if(set.isEmpty()) { // ERROR: constant true evaluation
            System.out.println("Hello"); // should be no error, out has @NotNull
        }
        return Set.of("a");
    }

    @Size(equals = 2)
    public Set<Integer> method2() {
        return intSet;
    }
}
