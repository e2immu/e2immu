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
Multi-example

3 interfaces, one with unmarked modification (up to the implementation), two with explicitly marked modification.
'MyIterator.next' demands @Modified @Dependent1
'MyIterable.iterator' demands @NotModified @Dependent2 (which implies @Independent)

Consumer applied to field (non-parameter) of implicitly immutable type
Examples of propagation of modification:
print, addNewLine, addNewLine2, replace

Examples of propagation of modification to parameters content linked to a parameter:
oneMore1, oneMore2, oneMore3

E1Circular shows @Linked  @Dependent                -> @E1Container,
E2Circular shows @Linked1 @Dependent2 @Independent  -> @E2Container

@PropagateModification is generated when an abstract method is called on a parameter (or variable linked to parameter)
@Dependent1 is generated on
(1) assignment of II type parameter to field
(2) exposure to parameter of abstract method
(3) propagation

@Dependent2 is generated when a @Dependent1 object is linked to FIXME
 */
public class AbstractTypeAsParameter_00 {

    interface MyIterator<T> {
        @Modified
        @Dependent1
            // content bind the result to my IID (which is the implementation's IID)
        T next();

        @Modified
        boolean hasNext();
    }

    interface MyIterable<T> {
        @NotModified
        @Dependent2
            // the result is content bound to my IID; independent of SD
        MyIterator<T> iterator();
    }

    interface MyConsumer<T> {
        // Method unmarked in terms of modification
        void accept(T t); // Parameter unmarked in terms of modification
    }

    @Container
    static class Circular<T> implements MyIterable<T> {

        private T x; // implicitly immutable type T
        private T y;
        private boolean next;

        public Circular() {
        }

        @Independent
        public Circular(@Dependent2 Circular<T> c) { // @Dependent2: my IID becomes part of the IID
            x = c.x;
            y = c.y;
            next = c.next;
        }

        @Modified
        public void add(@Dependent1 T t) { // @Dependent1: becomes part of IID
            if (next) {
                this.y = t;
            } else {
                this.x = t;
            }
            next = !next;
        }

        @NotModified // because T is implicitly immutable, the parameter of accept cannot touch it wrt Circular
        public void forEach(@NotModified @PropagateModification @Dependent1 MyConsumer<T> consumer) {
            consumer.accept(x); // sending in x implies @Dependent1; using accept implies @PM
            consumer.accept(y);
        }

        @NotModified
        @E2Container // the stream itself is level2 immutable
        @Dependent2 // the stream's IID is content bound to my IID
        public Stream<T> stream() {
            return Stream.of(x, y);
        }

        @Override
        @Independent // my SD cannot be reached/is independent
        @NotModified // not changing my SD in this operation
        @Dependent2 // my IID is bound to 'this' 's IID
        public MyIterator<T> iterator() {
            return new IteratorImpl();
        }

        @Independent
                // means: no way to modify the implicitly present parent class's SD (next boolean)
        class IteratorImpl implements MyIterator<T> {
            boolean returnY;

            @Override
            @Modified
            public boolean hasNext() {
                if (returnY) return false;
                returnY = true;
                return true;
            }

            @Override // @NotModified, even if the interface allows for modification
            @Dependent1 // returning my IID
            public T next() {
                return returnY ? y : x;
            }
        }
    }

    public static void print(@NotModified @NotModified1 Circular<StringBuilder> c) {
        c.forEach(System.out::println); // nan-modifying method implies no modification on c
    }

    public static void addNewLine(@NotModified @Modified1 Circular<StringBuilder> c) {
        c.forEach(sb -> sb.append("\n")); // parameter-modifying lambda propagates modification to c
    }

    public static void addNewLine2(@NotModified @Modified1 Circular<StringBuilder> c) {
        MyIterator<StringBuilder> iterator = c.iterator(); // non-modifying operation on c, but content binding
        while (iterator.hasNext()) { // modifying operation on iterator
            StringBuilder sb = iterator.next();
            // modify a local variable which is content bound to iterator, which in turn is content bound to c
            // this implies the @Modified1 on c
            sb.append("\n");
        }
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
    static class E1Circular<T> {
        @Linked(to = {"E1Circular:0:circular"})
        private final Circular<T> circular;

        @Dependent // assignment
        public E1Circular(Circular<T> circular) {
            this.circular = circular;
        }

        @Modified
        public void add(@Dependent1 T t) {
            circular.add(t);
        }

        @Modified
        public void addAll(@Dependent2 Collection<? extends T> collection) {
            for (T t : collection) circular.add(t); // links t to circular, and t was linked to collection -> @Dep2
        }

        @NotModified
        public void forEach(@NotModified @PropagateModification @Independent @Dependent1 MyConsumer<T> consumer) {
            circular.forEach(consumer); // t -> consumer.apply(t)
        }

        @Dependent2
        @NotModified
        @E2Container
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
        public E2Circular(@NotModified @Dependent2 Circular<T> circular) {
            this.circular = new Circular<>(circular);
        }

        @NotModified
        public void forEach(@NotModified @PropagateModification @Independent @Dependent1 MyConsumer<T> consumer) {
            circular.forEach(consumer);
        }

        @Dependent2
        @NotModified
        @E2Container
        public Stream<T> stream() {
            return circular.stream();
        }
    }
}
