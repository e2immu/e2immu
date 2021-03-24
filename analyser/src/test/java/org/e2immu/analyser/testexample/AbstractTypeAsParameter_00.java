/*
 * e2immu: a static code analyser for effective and eventual immutability
 * Copyright 2020-2021, Bart Naudts, https://www.e2immu.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details. You should have received a copy of the GNU Lesser General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.e2immu.analyser.testexample;

import org.e2immu.annotation.*;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/*
situation: consumer applied to field (non-parameter) of implicitly immutable type
 */
public class AbstractTypeAsParameter_00 {

    interface MyConsumer<T> {
        // UNMARKED
        void accept(T t); // PARAMETER T unmarked
    }

    @Container
    static class Circular<T> {

        private T x;
        private T y;
        private boolean next;

        public Circular() {
        }

        @Independent
        public Circular(@Dependent1 Circular<T> c) {
            x = c.x;
            y = c.y;
            next = c.next;
        }

        @Modified
        public void add(@Dependent T t) { // @Dependent means @NM + @Linked to fields
            if (next) {
                this.y = t;
            } else {
                this.x = t;
            }
            next = !next;
        }

        @NotModified // because T is implicitly immutable, the parameter of accept cannot touch it wrt Circular
        public void forEach(@NotModified @PropagateModification @Dependent MyConsumer<T> consumer) { // because forEach calls an unmarked method on consumer (and no other modifying method)
            consumer.accept(x);
            consumer.accept(y);
        }

        @NotModified
        @Dependent1
        public Stream<T> stream() {
            return Stream.of(x, y);
        }
    }

    public static void print(@NotModified @NotModified1 Circular<StringBuilder> c) {
        c.forEach(System.out::println); // nan-modifying method implies no modification on c
    }

    public static void addNewLine(@NotModified @Modified1 Circular<StringBuilder> c) {
        c.forEach(sb -> sb.append("\n")); // parameter-modifying lambda propagates modification to c
    }

    public static void replace(@Modified @NotModified1 Circular<StringBuilder> c) {
        c.forEach(sb -> c.add(new StringBuilder("x" + sb))); // object-modifying lambda changing c but not its subgraph
    }

    public static String oneMore1(@NotModified StringBuilder sb1, @NotModified StringBuilder sb2, @NotModified StringBuilder sb3) {
        Circular<StringBuilder> circular = new Circular<>();
        circular.add(sb1); // ties sb1 to circular at the 1 level (@Dependent)
        circular.add(sb2);
        circular.add(sb3); // now sb1 is not held by circular anymore
        print(circular); // no modifications all around
        return circular.stream().collect(Collectors.joining());
    }

    public static String oneMore2(@Modified StringBuilder sb1, @Modified StringBuilder sb2, @Modified StringBuilder sb3) {
        Circular<StringBuilder> circular = new Circular<>();
        circular.add(sb1); // ties sb1 to circular
        circular.add(sb2); // ties sb2 to circular
        circular.add(sb3); // now sb1 is not held by circular anymore
        addNewLine(circular); // @Modified1 implies that the elements tied to circular are modified (but not circular itself)
        return circular.stream().collect(Collectors.joining());
    }

    public static String oneMore3(@NotModified StringBuilder sb1, @NotModified StringBuilder sb2, @NotModified StringBuilder sb3) {
        Circular<StringBuilder> circular = new Circular<>();
        circular.add(sb1);
        circular.add(sb2);
        circular.add(sb3); // now sb1 is not held by circular anymore
        replace(circular); // only circular is modified, which has no visible effect
        return circular.stream().collect(Collectors.joining());
    }

    @Test
    public void test() {
        Circular<StringBuilder> circular = new Circular<>();
        StringBuilder sb1 = new StringBuilder("one");
        StringBuilder sb2 = new StringBuilder("two");

        circular.add(sb1);    // modifying on circular, non-modifying on sb1
        circular.add(sb2);    // modifying on circular, non-modifying on sb2
        print(circular);      // non-modifying on circular, non-modifying on sb1, sb2
        addNewLine(circular); // non-modifying on circular, but modifying on sb1, sb2
        print(circular);

        circular.forEach(sb -> sb.append("!")); // modifying on sb1, sb2
        print(circular);

        replace(circular);
        print(circular);
    }

    /*
    typical proxying of some methods to another background container
     */
    @E1Container
    static class Circular2<T> {
        private final Circular<T> circular;

        public Circular2(Circular<T> circular) {
            this.circular = circular;
        }

        @Modified
        public void add(@Dependent T t) {
            circular.add(t);
        }

        @Modified
        public void addAll(@Dependent1 Collection<? extends T> collection) {
            for (T t : collection) circular.add(t);
        }

        @NotModified
        public void forEach(@NotModified @PropagateModification @Independent @Dependent1 MyConsumer<T> consumer) {
            circular.forEach(consumer);
        }

        @Dependent1
        @NotModified
        @Independent
        public Stream<T> stream() {
            return circular.stream();
        }
    }

    /*
    typical proxying of some methods to another background container
    */
    @E2Container
    static class E2Circular<T> {
        @Linked1(to = {"E2Circular:0:circular"})
        private final Circular<T> circular;

        @Independent
        public E2Circular(@NotModified @Dependent1 Circular<T> circular) {
            this.circular = new Circular<>(circular);
        }

        @NotModified
        public void forEach(@NotModified @PropagateModification @Independent @Dependent1 MyConsumer<T> consumer) {
            circular.forEach(consumer);
        }

        @Dependent1
        @NotModified
        @Independent
        public Stream<T> stream() {
            return circular.stream();
        }
    }
}
