package org.e2immu.analyser.analyser;

import org.e2immu.graph.op.DijkstraShortestPath;

import java.util.*;
import java.util.stream.Collectors;

// integers represent type parameters, as result of HC.typeParameters()
public abstract sealed class HiddenContentSelector implements DijkstraShortestPath.Connection
        permits HiddenContentSelector.All, HiddenContentSelector.None, HiddenContentSelector.CsSet {

    public abstract HiddenContentSelector union(HiddenContentSelector other);

    public boolean isNone() {
        return false;
    }

    public boolean isAll() {
        return false;
    }

    public Set<Integer> set() {
        throw new UnsupportedOperationException();
    }

    public static final class All extends HiddenContentSelector {

        public static final HiddenContentSelector INSTANCE = new All();

        private All() {
        }

        @Override
        public boolean isAll() {
            return true;
        }

        @Override
        public HiddenContentSelector union(HiddenContentSelector other) {
            if (other instanceof All) {
                return this;
            }
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isDisjointFrom(DijkstraShortestPath.Connection required) {
            return false;
        }

        @Override
        public String toString() {
            return "*";
        }
    }

    public static final class None extends HiddenContentSelector {

        public static final HiddenContentSelector INSTANCE = new None();

        private None() {
        }

        @Override
        public boolean isNone() {
            return true;
        }

        @Override
        public HiddenContentSelector union(HiddenContentSelector other) {
            return other;
        }

        @Override
        public boolean isDisjointFrom(DijkstraShortestPath.Connection required) {
            return true;
        }

        @Override
        public String toString() {
            return "X";
        }
    }

    public static final class CsSet extends HiddenContentSelector {

        private final Set<Integer> set;

        public CsSet(Set<Integer> set) {
            assert set != null && !set.isEmpty() && set.stream().allMatch(i -> i >= 0);
            this.set = Set.copyOf(set);
        }

        public static HiddenContentSelector selectTypeParameter(int i) {
            return new CsSet(Set.of(i));
        }

        public static HiddenContentSelector selectTypeParameters(int... is) {
            return new CsSet(Arrays.stream(is).boxed().collect(Collectors.toUnmodifiableSet()));
        }

        @Override
        public boolean isDisjointFrom(DijkstraShortestPath.Connection other) {
            if (other instanceof None) throw new UnsupportedOperationException();
            return !(other instanceof All) && Collections.disjoint(set, ((CsSet) other).set);
        }

        @Override
        public String toString() {
            return set.stream().sorted().map(Object::toString)
                    .collect(Collectors.joining(",", "<", ">"));
        }

        public Set<Integer> set() {
            return set;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CsSet csSet = (CsSet) o;
            return Objects.equals(set, csSet.set);
        }

        @Override
        public int hashCode() {
            return Objects.hash(set);
        }


        @Override
        public HiddenContentSelector union(HiddenContentSelector other) {
            assert !(other instanceof All);
            if (other instanceof None) return this;
            Set<Integer> set = new HashSet<>(this.set);
            set.addAll(((CsSet) other).set);
            assert !set.isEmpty();
            return new CsSet(set);
        }
    }
}
