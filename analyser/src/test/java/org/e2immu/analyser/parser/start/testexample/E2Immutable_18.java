package org.e2immu.analyser.parser.start.testexample;

import org.e2immu.annotation.Container;
import org.e2immu.annotation.FinalFields;
import org.e2immu.annotation.ImmutableContainer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class E2Immutable_18 {

    @FinalFields
    @Container
    static class R<T> {
        private final Set<T> set;
        private final List<T> list = new ArrayList<>();

        public R(Set<T> set) {
            this.set = set;
        }

        public List<T> getList() {
            return list;
        }

        public Set<T> getSet() {
            return set;
        }
    }

    // there is a mutable final field not linked to a parameter in the constructor call
    @ImmutableContainer(absent = true)
    public static <S> R<S> create(S s) {
        Set<S> set = new HashSet<>();
        set.add(s);
        Set<S> immSet = Set.copyOf(set);
        return new R<>(immSet);
    }
}
