package org.e2immu.analyser.parser.start.testexample;

import org.e2immu.annotation.Container;
import org.e2immu.annotation.FinalFields;
import org.e2immu.annotation.ImmutableContainer;

import java.util.HashSet;
import java.util.Set;

public class E2Immutable_17 {

    @FinalFields
    @Container
    record R<T>(Set<T> set) {}

    @ImmutableContainer(hc = true)
    public static <S> R<S> create(S s) {
        Set<S> set = new HashSet<>();
        set.add(s);
        Set<S> immSet = Set.copyOf(set);
        return new R<>(immSet);
    }
}
