package org.e2immu.analyser.parser.modification.testexample;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class Linking_1 {

    // the archetypal modifiable type
    static class M {
        private int i;

        public int getI() {
            return i;
        }

        public void setI(int i) {
            this.i = i;
        }
    }

    static <X> X m0(Supplier<X> supplier) {
        return supplier.get();
    }

    static M m1(Supplier<M> supplier) {
        return supplier.get();
    }

    static Integer m2(Supplier<Integer> supplier) {
        return supplier.get();
    }

    static Stream<M> m3(Stream<M> stream) {
        return stream.filter(m -> m.i == 3);
    }

    static Optional<M> m4(Stream<M> stream) {
        return stream.filter(m -> m.i == 3).findFirst();
    }

    static M m5(Stream<M> stream) {
        return stream.filter(m -> m.i == 3).findFirst().orElseThrow();
    }

    static Stream<Integer> m6(Stream<Integer> stream) {
        return stream.filter(i -> i == 3);
    }

    static Optional<Integer> m7(Stream<Integer> stream) {
        return stream.filter(i -> i == 3).findFirst();
    }

    static Integer m8(Stream<Integer> stream) {
        return stream.filter(i -> i == 3).findFirst().orElseThrow();
    }

    static <X> Stream<X> m9(Stream<X> stream, Predicate<X> predicate) {
        //noinspection ALL
        return stream.filter(i -> predicate.test(i));
    }

    static <X> Optional<X> m10(Stream<X> stream, Predicate<X> predicate) {
        //noinspection ALL
        return stream.filter(i -> predicate.test(i)).findFirst();
    }

    static <X> X m11(Stream<X> stream, Predicate<X> predicate) {
        //noinspection ALL
        return stream.filter(i -> predicate.test(i)).findFirst().orElseThrow();
    }
/*  FIXME bug1
    static <X, Y> Stream<Y> m12(Stream<X> stream, Function<X, Y> function) {
        //noinspection ALL
        return stream.map(x -> function.apply(x));
    }*/

    static <X, Y> Stream<Y> m13(Stream<X> stream, Function<X, Y> function) {
        //noinspection ALL
        return stream.map(function::apply);
    }

    static List<String> m14(List<String> in, List<String> out) {
        //noinspection ALL
        in.forEach(out::add);
        return out;
    }

    static <X> List<X> m15(List<X> in, List<X> out) {
        //noinspection ALL
        in.forEach(out::add);
        return out;
    }

    static List<M> m16(List<M> in, List<M> out) {
        //noinspection ALL
        in.forEach(out::add);
        return out;
    }

    static <X> Stream<X> m17(Supplier<X> supplier) {
        return IntStream.of(3).mapToObj(i -> supplier.get());
    }

    static Stream<M> m18(Supplier<M> supplier) {
        return IntStream.of(3).mapToObj(i -> supplier.get());
    }

    static <X> Stream<X> m19(List<X> list) {
        //noinspection ALL
        return IntStream.of(3).mapToObj(i -> list.get(i));
    }

    static Stream<M> m20(List<M> list) {
        //noinspection ALL
        return IntStream.of(3).mapToObj(i -> list.get(i));
    }

    static <X> Stream<X> m21(List<X> list) {
        return IntStream.of(3).mapToObj(list::get);
    }

    static Stream<M> m22(List<M> list) {
        return IntStream.of(3).mapToObj(list::get);
    }
}
