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

package org.e2immu.analyser.parser.failing.testexample;

import org.e2immu.annotation.*;
import org.e2immu.annotation.rare.IgnoreModifications;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/*
In Road to Immutability.
 */
public class Consumer_11 {

    interface MyConsumer<T> {
        @Modified
        void accept(T t);
    }

    @FinalFields
    @Container
    static class MyList<T> {

        private final List<T> list;

        public MyList() {
            list = new ArrayList<>();
        }

        public MyList(@Independent(hc = true) Collection<? extends T> collection) { // inherited from ArrayList constructor
            list = new ArrayList<>(collection);
        }

        public MyList(@Independent(hc = true) List<? extends T> list) {
            this.list = new ArrayList<>();
            for (T t : list) { // t tied to list
                add(t);  // and t tied to this.list, so implies @Dependent2 on list
            }
        }

        @Modified
        public void clear() {
            list.clear();
        }

        @Modified
        public void add( T t) { // @Dependent means @NM + @Linked to fields
            list.add(t);
        }

        @NotModified
        public void forEach(@IgnoreModifications @Independent(hc = true) MyConsumer<T> consumer) {
            for (T t : list) { // t tied to list
                consumer.accept(t); // and tied to consumer, so @Dep2
            }
        }

        @NotModified
        @Independent(hc = true)
        public Stream<T> stream() {
            return list.stream();
        }
    }

    public static void print(@NotModified MyList<StringBuilder> c) {
        c.forEach(System.out::println); // nan-modifying method implies no modification on c
    }

    public static void addNewLine(@Modified MyList<StringBuilder> c) {
        c.forEach(sb -> sb.append("\n")); // parameter-modifying lambda propagates modification to c
    }

    public static void replace(@Modified MyList<StringBuilder> c) {
        c.forEach(sb -> c.add(new StringBuilder("x" + sb))); // object-modifying lambda changing c but not its subgraph
    }

    // !! This one is doable, but oneMore2 is not without additional information
    public static String direct(@Modified StringBuilder sb1, @Modified StringBuilder sb2, @Modified StringBuilder sb3) {
        List<StringBuilder> list = new ArrayList<>();
        list.add(sb1); // content links sb1 to list at the 1 level (@Dependent)
        list.add(sb2); // ""
        list.add(sb3); // ""
        for (StringBuilder sb : list) {
            sb.append("!"); // sb represents the content of list, sb1, sb2, sb3 are content linked
        }
        return list.stream().map(Object::toString).collect(Collectors.joining());
    }

    public static String oneMore1(@NotModified StringBuilder sb1, @NotModified StringBuilder sb2, @NotModified StringBuilder sb3) {
        MyList<StringBuilder> list = new MyList<>();
        list.add(sb1); // content links sb1 to list at the 1 level (@Dependent)
        list.add(sb2); // ""
        list.add(sb3); // ""
        print(list); // no modifications all around
        return list.stream().map(Object::toString).collect(Collectors.joining());
    }

    // IMPROVE without something like @Modified1, we cannot see the modification on sb1. There's nothing to distinguish the
    // type of modification.
    public static String oneMore2(@NotModified StringBuilder sb1, @NotModified StringBuilder sb2, @NotModified StringBuilder sb3) {
        MyList<StringBuilder> list = new MyList<>();
        list.add(sb1); // content links sb1 to list
        list.add(sb2); // ""
        list.add(sb3); // ""
        addNewLine(list); // @Modified1 implies that the elements content linked to list are modified (but not list itself)
        return list.stream().map(Object::toString).collect(Collectors.joining());
    }

    public static String oneMore3(@NotModified StringBuilder sb1, @NotModified StringBuilder sb2, @NotModified StringBuilder sb3) {
        MyList<StringBuilder> list = new MyList<>();
        list.add(sb1); // content links sb1 to list
        list.add(sb2); // ""
        list.add(sb3); // ""
        replace(list); // only list is modified, which has no visible effect
        return list.stream().map(Object::toString).collect(Collectors.joining());
    }

    @Test
    public void test() {
        MyList<StringBuilder> list = new MyList<>();
        StringBuilder sb1 = new StringBuilder("one");
        StringBuilder sb2 = new StringBuilder("two");

        list.add(sb1);    // modifying on list, non-modifying on sb1
        list.add(sb2);    // modifying on list, non-modifying on sb2
        print(list);      // non-modifying on list, non-modifying on sb1, sb2
        addNewLine(list); // non-modifying on list, but modifying on sb1, sb2
        print(list);

        list.forEach(sb -> sb.append("!")); // modifying on sb1, sb2
        print(list);

        //replace(list); FIXME causes concurrent modification exception
        print(list);
    }
}
