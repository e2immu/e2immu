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

package org.e2immu.analyser.model;

import org.e2immu.analyser.inspector.MethodInspectionImpl;
import org.e2immu.analyser.inspector.ParameterInspectionImpl;
import org.e2immu.analyser.inspector.TypeInspectionImpl;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.e2immu.analyser.inspector.TypeInspectionImpl.InspectionState.BY_HAND;
import static org.e2immu.analyser.model.ParameterizedType.WildCard.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
https://docs.oracle.com/javase/tutorial/java/generics/index.html
 */
public class TestIsAssignableFromGenerics {

    interface MyComparable<T> {
        int compareTo(T t);
    }

    interface Node extends MyComparable<Node> {
    }

    interface Sub1 extends Node {
    }

    interface Sub2 extends Node {
    }

    interface MyList1<T extends Node> extends MyComparable<MyList1<T>> {
        void add(T t);
    }

    interface MyList2<T extends Node> extends MyComparable<MyList2<? super T>> {
    }

    interface MyList3<T extends Node> extends MyComparable<MyList3<? extends T>> {
    }

    // arrays are covariant
    public void testArrays1(Sub1[] s1) {
        // FAILS Sub2[] sub2s = s1;
        Sub1[] sub1s = s1;
        Node[] nodes = s1;
    }

    public void testArrays2(Node[] n1) {
        // FAILS Sub2[] sub2s = n1;
        // FAILS Sub1[] sub1s = n1;
        Node[] nodes = n1;
    }

    // generics are invariant

    public void testGenericsInvariant(MyComparable<Sub1> s1) {
        // FAILS MyComparable<Node> n = s1;
        // FAILS MyComparable<Sub2> n = s1;
        MyComparable<Sub1> n = s1;
    }

    public void testSub1(MyList1<Node> myList1, Node n1, Sub1 sub1, Sub2 sub2) {
        myList1.add(n1);
        myList1.add(sub1);
        myList1.add(sub2);
        // FAILS myList1.add(new Object());
    }

    public void testSub2(MyList1<Sub1> myList1, Node n1, Sub1 sub1, Sub2 sub2) {
        // FAILS myList1.add(n1);
        myList1.add(sub1);
        // FAILS myList1.add(sub2);
        // FAILS myList1.add(new Object());
    }

    public void testSub3(MyList1<? super Sub1> myList1, Node n1, Sub1 sub1, Sub2 sub2) {
        // FAILS myList1.add(n1);
        myList1.add(sub1);
        // FAILS myList1.add(sub2);
        // FAILS myList1.add(new Object());
    }

    public void testSub4(MyList1<? extends Node> myList1, Node n1, Sub1 sub1, Sub2 sub2) {
        // FAILS myList1.add(n1);
        // FAILS myList1.add(sub1);
        // FAILS myList1.add(sub2);
        // FAILS myList1.add(new Object());
    }

    public void testSub5(MyList1<? super Node> myList1, Node n1, Sub1 sub1, Sub2 sub2) {
        myList1.add(n1);
        myList1.add(sub1);
        myList1.add(sub2);
        // FAILS myList1.add(new Object());
    }

    public void testMyList(MyList1<? extends Node> myList1, MyList1<Node> myList2) {
        // FAILS MyList1<Sub1> subs1 = myList1;
        // FAILS MyList1<Node> nodes = myList1;
        // FAILS myList1.compareTo(myList2);
    }

    public void test1a(MyList1<Sub1> myListSub1, MyList1<Sub2> myListSub2) {
        // FAILS myListSub1.compareTo(myListSub2);
        // FAILS myListSub2.compareTo(myListSub1);
    }

    public void test1b(MyList1<Node> myListSub1, MyList1<Node> myListSub2) {
        myListSub1.compareTo(myListSub2);
        myListSub2.compareTo(myListSub1);
    }

    public void test1c(MyList1<Node> myListSub1, MyList1<Sub1> myListSub2) {
        // FAILS myListSub1.compareTo(myListSub2);
        // FAILS myListSub2.compareTo(myListSub1);
    }


    public void test2a(MyList2<Sub1> myListSub1, MyList2<Sub2> myListSub2) {
        // FAILS myListSub1.compareTo(myListSub2);
        // FAILS myListSub2.compareTo(myListSub1);
    }

    public void test2b(MyList2<Node> myListSub1, MyList2<Node> myListSub2) {
        myListSub1.compareTo(myListSub2);
        myListSub2.compareTo(myListSub1);
    }

    public void test2c(MyList2<Node> myListSub1, MyList2<Sub2> myListSub2) {
        // FAILS myListSub1.compareTo(myListSub2);
        myListSub2.compareTo(myListSub1);  // !!!
    }

    public void test3a(MyList3<Sub1> myListSub1, MyList3<Sub2> myListSub2) {
        // FAILS myListSub1.compareTo(myListSub2);
        // FAILS myListSub2.compareTo(myListSub1);
    }

    public void test3b(MyList3<Node> myListSub1, MyList3<Node> myListSub2) {
        myListSub1.compareTo(myListSub2);
        myListSub2.compareTo(myListSub1);
    }

    public void test3c(MyList3<Node> myListSub1, MyList3<Sub2> myListSub2) {
        myListSub1.compareTo(myListSub2);   // !!!
        // FAILS myListSub2.compareTo(myListSub1);
    }

    @BeforeAll
    public static void beforeClass() {
        Logger.activate();
    }

    Primitives primitives;
    InspectionProvider IP;
    TypeInfo myComparable, node, sub1, sub2, myList1, myList2, myList3;

    @BeforeEach
    public void before() {
        primitives = new Primitives();
        IP = InspectionProvider.DEFAULT;
        String PACKAGE = "org.e2immu";
        primitives.objectTypeInfo.typeInspection.set(new TypeInspectionImpl.Builder(primitives.objectTypeInfo, BY_HAND)
                .setTypeNature(TypeNature.CLASS)
                .build());

        myComparable = new TypeInfo(PACKAGE, "MyComparable");
        {
            TypeParameter myComparableT = new TypeParameterImpl(myComparable, "T", 0);

            TypeInspectionImpl.Builder myComparableInspection = new TypeInspectionImpl.Builder(myComparable, BY_HAND)
                    .setParentClass(primitives.objectParameterizedType)
                    .addTypeParameter(myComparableT);
            MethodInspectionImpl.Builder compareToBuilder = new MethodInspectionImpl.Builder(myComparable, "compareTo");
            MethodInfo compareTo = compareToBuilder
                    .setReturnType(primitives.intParameterizedType)
                    .addParameter(new ParameterInspectionImpl.Builder(
                            new ParameterizedType(myComparableT, 0, NONE), "t", 0))
                    .build(IP).getMethodInfo();
            myComparable.typeInspection.set(myComparableInspection
                    .setTypeNature(TypeNature.INTERFACE)
                    .addMethod(compareTo)
                    .build());
        }
        node = new TypeInfo(PACKAGE, "Node");
        {
            TypeInspectionImpl.Builder builder = new TypeInspectionImpl.Builder(node, BY_HAND)
                    .addInterfaceImplemented(new ParameterizedType(myComparable, List.of(new ParameterizedType(node, List.of()))))
                    .setParentClass(primitives.objectParameterizedType);
            node.typeInspection.set(builder.setTypeNature(TypeNature.INTERFACE).build());
        }
        sub1 = new TypeInfo(PACKAGE, "Sub1");
        {
            TypeInspectionImpl.Builder builder = new TypeInspectionImpl.Builder(sub1, BY_HAND)
                    .addInterfaceImplemented(new ParameterizedType(node, List.of()))
                    .setParentClass(primitives.objectParameterizedType);
            sub1.typeInspection.set(builder.setTypeNature(TypeNature.INTERFACE).build());
        }
        sub2 = new TypeInfo(PACKAGE, "Sub2");
        {
            TypeInspectionImpl.Builder builder = new TypeInspectionImpl.Builder(sub2, BY_HAND)
                    .addInterfaceImplemented(new ParameterizedType(node, List.of()))
                    .setParentClass(primitives.objectParameterizedType);
            sub2.typeInspection.set(builder.setTypeNature(TypeNature.INTERFACE).build());
        }

        // interface MyList1<T extends Node> extends MyComparable<MyList1<T>>
        myList1 = new TypeInfo(PACKAGE, "MyList1");
        {
            TypeParameterImpl t = new TypeParameterImpl(myList1, "T", 0);
            t.setTypeBounds(List.of(new ParameterizedType(node, ParameterizedType.WildCard.EXTENDS)));

            TypeInspectionImpl.Builder builder = new TypeInspectionImpl.Builder(myList1, BY_HAND)
                    .setParentClass(primitives.objectParameterizedType)
                    .addTypeParameter(t);
            MethodInspectionImpl.Builder addBuilder = new MethodInspectionImpl.Builder(myList1, "add");
            MethodInfo add = addBuilder
                    .setReturnType(primitives.voidParameterizedType)
                    .addParameter(new ParameterInspectionImpl.Builder(
                            new ParameterizedType(t, 0, NONE), "t", 0))
                    .build(IP).getMethodInfo();
            myList1.typeInspection.set(builder
                    .setTypeNature(TypeNature.INTERFACE)
                    .addInterfaceImplemented(new ParameterizedType(myComparable, List.of(
                            new ParameterizedType(myList1, List.of(new ParameterizedType(t, 0, NONE)))
                    )))
                    .addMethod(add)
                    .build());
        }
        // interface MyList2<T extends Node> extends MyComparable<MyList2<? super T>>
        myList2 = new TypeInfo(PACKAGE, "MyList2");
        {
            TypeParameterImpl t = new TypeParameterImpl(myList2, "T", 0);
            t.setTypeBounds(List.of(new ParameterizedType(node, ParameterizedType.WildCard.EXTENDS)));

            TypeInspectionImpl.Builder builder = new TypeInspectionImpl.Builder(myList2, BY_HAND)
                    .setParentClass(primitives.objectParameterizedType)
                    .addTypeParameter(t);
            myList2.typeInspection.set(builder
                    .setTypeNature(TypeNature.INTERFACE)
                    .addInterfaceImplemented(new ParameterizedType(myComparable, List.of(
                            new ParameterizedType(myList2, List.of(new ParameterizedType(t, 0, SUPER)))
                    )))
                    .build());
        }
        // interface MyList3<T extends Node> extends MyComparable<MyList3<? extends T>>
        myList3 = new TypeInfo(PACKAGE, "MyList3");
        {
            TypeParameterImpl t = new TypeParameterImpl(myList3, "T", 0);
            t.setTypeBounds(List.of(new ParameterizedType(node, ParameterizedType.WildCard.EXTENDS)));

            TypeInspectionImpl.Builder builder = new TypeInspectionImpl.Builder(myList3, BY_HAND)
                    .setParentClass(primitives.objectParameterizedType)
                    .addTypeParameter(t);
            myList3.typeInspection.set(builder
                    .setTypeNature(TypeNature.INTERFACE)
                    .addInterfaceImplemented(new ParameterizedType(myComparable, List.of(
                            new ParameterizedType(myList3, List.of(new ParameterizedType(t, 0, EXTENDS)))
                    )))
                    .build());
        }
    }

    @Test
    public void testOutput() {
        assertTrue(myComparable.output().toString().contains("interface MyComparable<T>{int compareTo(T t){}}"),
                () -> myComparable.output().toString());
        assertTrue(node.output().toString().contains("interface Node extends MyComparable<Node>{"),
                () -> node.output().toString());
        assertTrue(sub1.output().toString().contains("interface Sub1 extends Node{"), () -> sub1.output().toString());
        assertTrue(sub2.output().toString().contains("interface Sub2 extends Node{"), () -> sub2.output().toString());
        assertTrue(myList1.output().toString()
                        .contains("interface MyList1<T extends Node> extends MyComparable<MyList1<T>>{"),
                () -> myList1.output().toString());
        assertTrue(myList2.output().toString()
                        .contains("interface MyList2<T extends Node> extends MyComparable<MyList2<? super T>>{"),
                () -> myList2.output().toString());
        assertTrue(myList3.output().toString()
                        .contains("interface MyList3<T extends Node> extends MyComparable<MyList3<? extends T>>{"),
                () -> myList3.output().toString());
    }

    @Test
    public void realTest1a() {

    }

}
