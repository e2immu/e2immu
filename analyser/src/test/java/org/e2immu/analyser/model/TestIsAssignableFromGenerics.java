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
import org.e2immu.analyser.inspector.MethodResolution;
import org.e2immu.analyser.inspector.ParameterInspectionImpl;
import org.e2immu.analyser.inspector.TypeInspectionImpl;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.e2immu.analyser.inspector.TypeInspectionImpl.InspectionState.BY_HAND;
import static org.e2immu.analyser.model.ParameterizedType.WildCard.*;
import static org.junit.jupiter.api.Assertions.*;

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

    @BeforeAll
    public static void beforeClass() {
        Logger.activate();
    }

    Primitives primitives;
    InspectionProvider IP;
    TypeInfo myComparable, node, sub1, sub2, myList1, myList2, myList3;
    MethodInfo add;

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
                    .noParent(primitives)
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
                    .noParent(primitives);
            node.typeInspection.set(builder.setTypeNature(TypeNature.INTERFACE).build());
        }
        sub1 = new TypeInfo(PACKAGE, "Sub1");
        {
            TypeInspectionImpl.Builder builder = new TypeInspectionImpl.Builder(sub1, BY_HAND)
                    .addInterfaceImplemented(new ParameterizedType(node, List.of()))
                    .noParent(primitives);
            sub1.typeInspection.set(builder.setTypeNature(TypeNature.INTERFACE).build());
        }
        sub2 = new TypeInfo(PACKAGE, "Sub2");
        {
            TypeInspectionImpl.Builder builder = new TypeInspectionImpl.Builder(sub2, BY_HAND)
                    .addInterfaceImplemented(new ParameterizedType(node, List.of()))
                    .noParent(primitives);
            sub2.typeInspection.set(builder.setTypeNature(TypeNature.INTERFACE).build());
        }

        // interface MyList1<T extends Node> extends MyComparable<MyList1<T>>
        myList1 = new TypeInfo(PACKAGE, "MyList1");
        {
            TypeParameterImpl t = new TypeParameterImpl(myList1, "T", 0);
            t.setTypeBounds(List.of(new ParameterizedType(node, List.of())));

            TypeInspectionImpl.Builder builder = new TypeInspectionImpl.Builder(myList1, BY_HAND)
                    .noParent(primitives)
                    .addTypeParameter(t);
            MethodInspectionImpl.Builder addBuilder = new MethodInspectionImpl.Builder(myList1, "add");
            add = addBuilder
                    .setReturnType(primitives.voidParameterizedType)
                    .addParameter(new ParameterInspectionImpl.Builder(
                            new ParameterizedType(t, 0, NONE), "t", 0))
                    .build(IP).getMethodInfo();
            add.methodResolution.set(new MethodResolution.Builder().build());
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
            t.setTypeBounds(List.of(new ParameterizedType(node, List.of())));

            TypeInspectionImpl.Builder builder = new TypeInspectionImpl.Builder(myList2, BY_HAND)
                    .noParent(primitives)
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
            t.setTypeBounds(List.of(new ParameterizedType(node, List.of())));

            TypeInspectionImpl.Builder builder = new TypeInspectionImpl.Builder(myList3, BY_HAND)
                    .noParent(primitives)
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
    public void testOutputPt() {
        assertEquals("T extends Node",
                myList1.typeInspection.get().typeParameters().get(0)
                        .output(InspectionProvider.DEFAULT, Qualification.EMPTY, new HashSet<>()).toString());
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
                        .contains("interface MyList1<T extends Node> extends MyComparable<MyList1<T>>{void add(T t){}}"),
                () -> myList1.output().toString());
        assertTrue(myList2.output().toString()
                        .contains("interface MyList2<T extends Node> extends MyComparable<MyList2<? super T>>{"),
                () -> myList2.output().toString());
        assertTrue(myList3.output().toString()
                        .contains("interface MyList3<T extends Node> extends MyComparable<MyList3<? extends T>>{"),
                () -> myList3.output().toString());
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

    @Test
    public void testArrays() {
        ParameterizedType sub1s = new ParameterizedType(sub1, 1);
        ParameterizedType sub2s = new ParameterizedType(sub2, 1);
        ParameterizedType nodes = new ParameterizedType(node, 1);

        assertFalse(sub1s.isAssignableFrom(IP, sub2s));
        assertFalse(sub1s.isAssignableFrom(IP, nodes));

        assertTrue(nodes.isAssignableFrom(IP, nodes));
        assertTrue(nodes.isAssignableFrom(IP, sub1s));
        assertTrue(sub1s.isAssignableFrom(IP, sub1s));
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

    @Test
    public void testSub1_2() {
        ParameterizedType nodePt = new ParameterizedType(node, List.of());
        ParameterizedType sub1Pt = new ParameterizedType(sub1, List.of());
        ParameterizedType sub2Pt = new ParameterizedType(sub2, List.of());

        assertTrue(nodePt.isAssignableFrom(IP, nodePt));
        assertTrue(nodePt.isAssignableFrom(IP, sub1Pt));
        assertTrue(nodePt.isAssignableFrom(IP, sub2Pt));
        assertFalse(nodePt.isAssignableFrom(IP, primitives.objectParameterizedType));

        assertFalse(sub1Pt.isAssignableFrom(IP, nodePt));
        assertTrue(sub1Pt.isAssignableFrom(IP, sub1Pt));
        assertFalse(sub2Pt.isAssignableFrom(IP, sub1Pt));
        assertFalse(sub1Pt.isAssignableFrom(IP, primitives.objectParameterizedType));
    }


    public void testSub3(MyList1<? super Sub1> myList1, Node n1, Sub1 sub1, Sub2 sub2) {
        // FAILS myList1.add(n1);
        myList1.add(sub1);
        // FAILS myList1.add(sub2);
        // FAILS myList1.add(new Object());
    }

    @Test
    public void testSub3() {
        ParameterizedType superSub1 = new ParameterizedType(sub1, ParameterizedType.WildCard.SUPER);
        ParameterizedType nodePt = new ParameterizedType(node, List.of());
        ParameterizedType sub1Pt = new ParameterizedType(sub1, List.of());
        ParameterizedType sub2Pt = new ParameterizedType(sub2, List.of());

        assertFalse(superSub1.isAssignableFrom(IP, nodePt));
        assertTrue(superSub1.isAssignableFrom(IP, sub1Pt));
        assertFalse(superSub1.isAssignableFrom(IP, sub2Pt));
        assertFalse(superSub1.isAssignableFrom(IP, primitives.objectParameterizedType));
    }

    public void testSub4(MyList1<? extends Node> myList1, Node n1, Sub1 sub1, Sub2 sub2) {
        // FAILS myList1.add(n1);
        // FAILS myList1.add(sub1);
        // FAILS myList1.add(sub2);
        // FAILS myList1.add(new Object());

        //TODO no idea how to translate this into a test
    }

    @Test
    public void testSub4() {
        ParameterizedType extendsNode = new ParameterizedType(node, EXTENDS);
        ParameterizedType nodePt = new ParameterizedType(node, List.of());
        ParameterizedType sub1Pt = new ParameterizedType(sub1, List.of());
        ParameterizedType sub2Pt = new ParameterizedType(sub2, List.of());

        assertTrue(extendsNode.isAssignableFrom(IP, nodePt));
        assertTrue(extendsNode.isAssignableFrom(IP, sub1Pt));
        assertTrue(extendsNode.isAssignableFrom(IP, sub2Pt));
        assertFalse(extendsNode.isAssignableFrom(IP, primitives.objectParameterizedType));

    }


    public void testSub5(MyList1<? super Node> myList1, Node n1, Sub1 sub1, Sub2 sub2) {
        myList1.add(n1);
        myList1.add(sub1);
        myList1.add(sub2);
        // FAILS myList1.add(new Object());
    }

    @Test
    public void testSub5() {
        ParameterizedType superNode = new ParameterizedType(node, SUPER);
        ParameterizedType nodePt = new ParameterizedType(node, List.of());
        ParameterizedType sub1Pt = new ParameterizedType(sub1, List.of());
        ParameterizedType sub2Pt = new ParameterizedType(sub2, List.of());

        assertTrue(superNode.isAssignableFrom(IP, nodePt));
        assertTrue(superNode.isAssignableFrom(IP, sub1Pt));
        assertTrue(superNode.isAssignableFrom(IP, sub2Pt));
        assertFalse(superNode.isAssignableFrom(IP, primitives.objectParameterizedType));
    }


    public void testMyList(MyList1<? extends Node> myList1ExtendsNode,
                           MyList1<Node> myList1Node,
                           MyList1<Sub1> myList1Sub1,
                           MyList1<? super Sub1> myList1SuperSub1,
                           MyList1<? extends Sub1> myList1ExtendsSub1,
                           MyList1<? super Node> myList1SuperNode) {
        // FAILS MyList1<Sub1> subs1 = myList1ExtendsNode;
        // FAILS MyList1<Node> nodes = myList1ExtendsNode;
        // FAILS MyList1<Node> nodes = myList1SuperNode;

        myList1ExtendsNode = myList1Node;
        myList1SuperNode = myList1Node;
        myList1ExtendsNode = myList1Sub1;
        myList1ExtendsNode = myList1SuperSub1;
        myList1ExtendsNode = myList1ExtendsSub1;
    }

    @Test
    public void testMyList() {
        ParameterizedType myListNode = new ParameterizedType(myList1, List.of(new ParameterizedType(node, List.of())));
        ParameterizedType myListExtendsNode = new ParameterizedType(myList1, List.of(new ParameterizedType(node, EXTENDS)));
        ParameterizedType myListSuperNode = new ParameterizedType(myList1, List.of(new ParameterizedType(node, SUPER)));
        ParameterizedType myListSub1 = new ParameterizedType(myList1, List.of(new ParameterizedType(sub1, List.of())));
        ParameterizedType myListSuperSub1 = new ParameterizedType(myList1, List.of(new ParameterizedType(sub1, SUPER)));
        ParameterizedType myListExtendsSub1 = new ParameterizedType(myList1, List.of(new ParameterizedType(sub1, EXTENDS)));

        assertFalse(myListSub1.isAssignableFrom(IP, myListExtendsNode));
        assertFalse(myListNode.isAssignableFrom(IP, myListExtendsNode));
        assertFalse(myListNode.isAssignableFrom(IP, myListSuperNode));

        assertTrue(myListExtendsNode.isAssignableFrom(IP, myListNode));
        assertTrue(myListSuperNode.isAssignableFrom(IP, myListNode));
        assertTrue(myListExtendsNode.isAssignableFrom(IP, myListSub1));
        assertTrue(myListExtendsNode.isAssignableFrom(IP, myListSuperSub1));
        assertTrue(myListExtendsNode.isAssignableFrom(IP, myListExtendsSub1));
    }

    public void test1(MyList1<Node> myList1Node, MyList1<Sub1> myList1Sub1, MyList1<Sub2> myList1Sub2) {
        // FAILS myList1Sub1.compareTo(myList1Sub2);
        // FAILS myList1Sub2.compareTo(myList1Sub1);

        myList1Node.compareTo(myList1Node);
        myList1Sub1.compareTo(myList1Sub1);

        // FAILS myList1Sub1.compareTo(myList1Node);
        // FAILS myList1Node.compareTo(myList1Sub1);
    }

    public void test1bis(MyComparable<MyList1<Node>> cMyList1Node,
                         MyComparable<MyList1<Sub1>> cMyList1Sub1,
                         MyComparable<MyList1<Sub2>> cMyList1Sub2) {
        // FAILS cMyList1Node = cMyList1Sub1;
        // FAILS cMyList1Sub2 = cMyList1Sub1;
        // FAILS cMyList1Sub1 = cMyList1Node;
    }

    @Test
    public void test1() {
        ParameterizedType nodePt = new ParameterizedType(node, List.of());
        ParameterizedType sub1Pt = new ParameterizedType(sub1, List.of());
        ParameterizedType sub2Pt = new ParameterizedType(sub2, List.of());
        ParameterizedType myList1Node = new ParameterizedType(myList1, List.of(nodePt));
        ParameterizedType myList1Sub1 = new ParameterizedType(myList1, List.of(sub1Pt));
        ParameterizedType myList1Sub2 = new ParameterizedType(myList1, List.of(sub2Pt));

        ParameterizedType myComparableMyList1Node = new ParameterizedType(myComparable, List.of(myList1Node));
        ParameterizedType myComparableMyList1Sub1 = new ParameterizedType(myComparable, List.of(myList1Sub1));
        ParameterizedType myComparableMyList1Sub2 = new ParameterizedType(myComparable, List.of(myList1Sub2));

        // generics are invariant, so assigning any combination should fail
        assertFalse(myComparableMyList1Node.isAssignableFrom(IP, myComparableMyList1Sub1));
        assertFalse(myComparableMyList1Sub1.isAssignableFrom(IP, myComparableMyList1Node));
        assertFalse(myComparableMyList1Sub2.isAssignableFrom(IP, myComparableMyList1Sub1));
    }


    public void test2(MyList2<Node> myList2Node, MyList2<Sub1> myList2Sub1, MyList2<Sub2> myList2Sub2) {
        // FAILS myList2Sub1.compareTo(myList2Sub2);
        // FAILS myList2Sub2.compareTo(myList2Sub1);

        myList2Node.compareTo(myList2Node);
        myList2Sub1.compareTo(myList2Sub1);

        myList2Sub1.compareTo(myList2Node);
        // FAILS myList2Node.compareTo(myList2Sub1);
    }

    public void test2bis(MyComparable<? extends MyList1<? super Node>> cSuperNode,
                         MyComparable<? extends MyList1<? super Sub1>> cSub1,
                         MyComparable<? extends MyList1<? super Sub2>> cSub2,
                         MyComparable<? extends MyList1<Node>> cNode) {
        cSub1 = cSub1;
        // FAILS cSub1 = cSub2;
        cSub1 = cNode;
        cSub1 = cSuperNode;
        // FAILS cNode = cSub1;
    }


    @Test
    public void test2() {
        ParameterizedType nodePt = new ParameterizedType(node, List.of());
        ParameterizedType superNode = new ParameterizedType(node, SUPER);
        ParameterizedType superSub1 = new ParameterizedType(sub1, SUPER);
        ParameterizedType superSub2 = new ParameterizedType(sub2, SUPER);
        ParameterizedType extendsMyList1Node = new ParameterizedType(myList1, 0, EXTENDS,
                List.of(nodePt));
        ParameterizedType extendsMyList1SuperNode = new ParameterizedType(myList1, 0, EXTENDS,
                List.of(superNode));
        ParameterizedType extendsMyList1SuperSub1 = new ParameterizedType(myList1, 0, EXTENDS,
                List.of(superSub1));
        ParameterizedType extendsMyList1SuperSub2 = new ParameterizedType(myList1, 0, EXTENDS,
                List.of(superSub2));

        ParameterizedType cExtendsMyList1Node = new ParameterizedType(myComparable,
                List.of(extendsMyList1Node));
        assertEquals("MyComparable<? extends MyList1<Node>>",
                cExtendsMyList1Node.output(Qualification.EMPTY).toString());
        ParameterizedType cExtendsMyList1SuperNode = new ParameterizedType(myComparable,
                List.of(extendsMyList1SuperNode));
        assertEquals("MyComparable<? extends MyList1<? super Node>>",
                cExtendsMyList1SuperNode.output(Qualification.EMPTY).toString());
        ParameterizedType cExtendsMyList1SuperSub1 = new ParameterizedType(myComparable,
                List.of(extendsMyList1SuperSub1));
        assertEquals("MyComparable<? extends MyList1<? super Sub1>>",
                cExtendsMyList1SuperSub1.output(Qualification.EMPTY).toString());
        ParameterizedType cExtendsMyList1SuperSub2 = new ParameterizedType(myComparable,
                List.of(extendsMyList1SuperSub2));
        assertEquals("MyComparable<? extends MyList1<? super Sub2>>",
                cExtendsMyList1SuperSub2.output(Qualification.EMPTY).toString());

        assertTrue(cExtendsMyList1SuperSub1.isAssignableFrom(IP, cExtendsMyList1SuperSub1));
        assertFalse(cExtendsMyList1SuperSub1.isAssignableFrom(IP, cExtendsMyList1SuperSub2));
        assertTrue(cExtendsMyList1SuperSub1.isAssignableFrom(IP, cExtendsMyList1Node));
        assertTrue(cExtendsMyList1SuperSub1.isAssignableFrom(IP, cExtendsMyList1SuperNode));
        assertFalse(cExtendsMyList1SuperNode.isAssignableFrom(IP, cExtendsMyList1SuperSub1));
    }

    public void test3(MyList3<Node> myList3Node, MyList3<Sub1> myList3Sub1, MyList3<Sub2> myList3Sub2) {
        // FAILS myList3Sub1.compareTo(myList3Sub2);
        // FAILS myList3Sub2.compareTo(myList3Sub1);

        myList3Node.compareTo(myList3Node);
        myList3Sub1.compareTo(myList3Sub1);

        // FAILS myList3Sub1.compareTo(myList3Node);
        myList3Node.compareTo(myList3Sub1);
    }

    public void test3bis(MyComparable<? super MyList1<? super Node>> cNode,
                         MyComparable<? super MyList1<? super Sub1>> cSub1,
                         MyComparable<? super MyList1<? super Sub2>> cSub2) {
        cSub1 = cSub1;
        // FAILS cSub1 = cSub2;
        // FAILS cSub1 = cNode;
        cNode = cSub1;
    }

    @Test
    public void test3() {
        ParameterizedType superNode = new ParameterizedType(node, SUPER);
        ParameterizedType superSub1 = new ParameterizedType(sub1, SUPER);
        ParameterizedType superSub2 = new ParameterizedType(sub2, SUPER);
        ParameterizedType superMyList1SuperNode = new ParameterizedType(myList1, 0, SUPER, List.of(superNode));
        ParameterizedType superMyList1SuperSub1 = new ParameterizedType(myList1, 0, SUPER, List.of(superSub1));
        ParameterizedType superMyList1SuperSub2 = new ParameterizedType(myList1, 0, SUPER, List.of(superSub2));

        ParameterizedType cSuperMyList1SuperNode = new ParameterizedType(myComparable, List.of(superMyList1SuperNode));
        assertEquals("MyComparable<? super MyList1<? super Node>>",
                cSuperMyList1SuperNode.output(Qualification.EMPTY).toString());
        ParameterizedType cSuperMyList1SuperSub1 = new ParameterizedType(myComparable, List.of(superMyList1SuperSub1));
        assertEquals("MyComparable<? super MyList1<? super Sub1>>",
                cSuperMyList1SuperSub1.output(Qualification.EMPTY).toString());
        ParameterizedType cSuperMyList1SuperSub2 = new ParameterizedType(myComparable, List.of(superMyList1SuperSub2));
        assertEquals("MyComparable<? super MyList1<? super Sub2>>",
                cSuperMyList1SuperSub2.output(Qualification.EMPTY).toString());

        assertTrue(cSuperMyList1SuperSub1.isAssignableFrom(IP, cSuperMyList1SuperSub1));
        assertFalse(cSuperMyList1SuperSub1.isAssignableFrom(IP, cSuperMyList1SuperSub2));
        assertFalse(cSuperMyList1SuperSub1.isAssignableFrom(IP, cSuperMyList1SuperNode));
        assertTrue(cSuperMyList1SuperNode.isAssignableFrom(IP, cSuperMyList1SuperSub1));
    }


    public void test4bis(MyComparable<? super MyList1<? extends Node>> cNode,
                         MyComparable<? super MyList1<? extends Sub1>> cSub1,
                         MyComparable<? super MyList1<? extends Sub2>> cSub2) {
        cSub1 = cSub1;
        // FAILS cSub1 = cSub2;
        cSub1 = cNode;
        // FAILS cNode = cSub1;
    }

    @Test
    public void test4() {
        ParameterizedType extendsNode = new ParameterizedType(node, EXTENDS);
        ParameterizedType extendsSub1 = new ParameterizedType(sub1, EXTENDS);
        ParameterizedType extendsSub2 = new ParameterizedType(sub2, EXTENDS);
        ParameterizedType superMyList1ExtendsNode = new ParameterizedType(myList1, 0, SUPER, List.of(extendsNode));
        ParameterizedType superMyList1ExtendsSub1 = new ParameterizedType(myList1, 0, SUPER, List.of(extendsSub1));
        ParameterizedType superMyList1ExtendsSub2 = new ParameterizedType(myList1, 0, SUPER, List.of(extendsSub2));

        ParameterizedType cSuperMyList1ExtendsNode = new ParameterizedType(myComparable, List.of(superMyList1ExtendsNode));
        assertEquals("MyComparable<? super MyList1<? extends Node>>",
                cSuperMyList1ExtendsNode.output(Qualification.EMPTY).toString());
        ParameterizedType cSuperMyList1ExtendsSub1 = new ParameterizedType(myComparable, List.of(superMyList1ExtendsSub1));
        assertEquals("MyComparable<? super MyList1<? extends Sub1>>",
                cSuperMyList1ExtendsSub1.output(Qualification.EMPTY).toString());
        ParameterizedType cSuperMyList1ExtendsSub2 = new ParameterizedType(myComparable, List.of(superMyList1ExtendsSub2));
        assertEquals("MyComparable<? super MyList1<? extends Sub2>>",
                cSuperMyList1ExtendsSub2.output(Qualification.EMPTY).toString());


        assertTrue(cSuperMyList1ExtendsSub1.isAssignableFrom(IP, cSuperMyList1ExtendsSub1));
        assertFalse(cSuperMyList1ExtendsSub1.isAssignableFrom(IP, cSuperMyList1ExtendsSub2));
        assertTrue(cSuperMyList1ExtendsSub1.isAssignableFrom(IP, cSuperMyList1ExtendsNode));
        assertFalse(cSuperMyList1ExtendsNode.isAssignableFrom(IP, cSuperMyList1ExtendsSub1));
    }

    public void test5bis(MyComparable<? extends MyList1<? extends Node>> cNode,
                         MyComparable<? extends MyList1<? extends Sub1>> cSub1,
                         MyComparable<? extends MyList1<? extends Sub2>> cSub2) {
        cSub1 = cSub1;
        // FAILS cSub1 = cSub2;
        // FAILS cSub1 = cNode;
        cNode = cSub1;
    }

    @Test
    public void test5() {
        ParameterizedType extendsNode = new ParameterizedType(node, EXTENDS);
        ParameterizedType extendsSub1 = new ParameterizedType(sub1, EXTENDS);
        ParameterizedType extendsSub2 = new ParameterizedType(sub2, EXTENDS);
        ParameterizedType extendsMyList1ExtendsNode = new ParameterizedType(myList1, 0, EXTENDS,
                List.of(extendsNode));
        ParameterizedType extendsMyList1ExtendsSub1 = new ParameterizedType(myList1, 0, EXTENDS,
                List.of(extendsSub1));
        ParameterizedType extendsMyList1ExtendsSub2 = new ParameterizedType(myList1, 0, EXTENDS,
                List.of(extendsSub2));

        ParameterizedType cExtendsMyList1ExtendsNode = new ParameterizedType(myComparable,
                List.of(extendsMyList1ExtendsNode));
        assertEquals("MyComparable<? extends MyList1<? extends Node>>",
                cExtendsMyList1ExtendsNode.output(Qualification.EMPTY).toString());
        ParameterizedType cExtendsMyList1ExtendsSub1 = new ParameterizedType(myComparable,
                List.of(extendsMyList1ExtendsSub1));
        assertEquals("MyComparable<? extends MyList1<? extends Sub1>>",
                cExtendsMyList1ExtendsSub1.output(Qualification.EMPTY).toString());
        ParameterizedType cExtendsMyList1ExtendsSub2 = new ParameterizedType(myComparable,
                List.of(extendsMyList1ExtendsSub2));
        assertEquals("MyComparable<? extends MyList1<? extends Sub2>>",
                cExtendsMyList1ExtendsSub2.output(Qualification.EMPTY).toString());

        assertTrue(cExtendsMyList1ExtendsSub1.isAssignableFrom(IP, cExtendsMyList1ExtendsSub1));
        assertFalse(cExtendsMyList1ExtendsSub1.isAssignableFrom(IP, cExtendsMyList1ExtendsSub2));
        assertFalse(cExtendsMyList1ExtendsSub1.isAssignableFrom(IP, cExtendsMyList1ExtendsNode));
        assertTrue(cExtendsMyList1ExtendsNode.isAssignableFrom(IP, cExtendsMyList1ExtendsSub1));
    }

    // normal rules of invariance apply, one level down

    public void test6bis(MyComparable<? extends MyList1<Node>> cNode,
                         MyComparable<? extends MyList1<Sub1>> cSub1,
                         MyComparable<? extends MyList1<Sub2>> cSub2) {
        cSub1 = cSub1;
        // FAILS cSub1 = cSub2;
        // FAILS cSub1 = cNode;
        // FAILS cNode = cSub1;
    }

    @Test
    public void test6() {
        ParameterizedType nodePt = new ParameterizedType(node, List.of());
        ParameterizedType sub1Pt = new ParameterizedType(sub1, List.of());
        ParameterizedType sub2Pt = new ParameterizedType(sub2, List.of());
        ParameterizedType extendsMyList1Node = new ParameterizedType(myList1, 0, EXTENDS,
                List.of(nodePt));
        ParameterizedType extendsMyList1Sub1 = new ParameterizedType(myList1, 0, EXTENDS,
                List.of(sub1Pt));
        ParameterizedType extendsMyList1Sub2 = new ParameterizedType(myList1, 0, EXTENDS,
                List.of(sub2Pt));

        ParameterizedType cExtendsMyList1Node = new ParameterizedType(myComparable,
                List.of(extendsMyList1Node));
        assertEquals("MyComparable<? extends MyList1<Node>>",
                cExtendsMyList1Node.output(Qualification.EMPTY).toString());
        ParameterizedType cExtendsMyList1Sub1 = new ParameterizedType(myComparable,
                List.of(extendsMyList1Sub1));
        assertEquals("MyComparable<? extends MyList1<Sub1>>",
                cExtendsMyList1Sub1.output(Qualification.EMPTY).toString());
        ParameterizedType cExtendsMyList1Sub2 = new ParameterizedType(myComparable,
                List.of(extendsMyList1Sub2));
        assertEquals("MyComparable<? extends MyList1<Sub2>>",
                cExtendsMyList1Sub2.output(Qualification.EMPTY).toString());

        assertTrue(cExtendsMyList1Sub1.isAssignableFrom(IP, cExtendsMyList1Sub1));
        assertFalse(cExtendsMyList1Sub1.isAssignableFrom(IP, cExtendsMyList1Sub2));
        assertFalse(cExtendsMyList1Sub1.isAssignableFrom(IP, cExtendsMyList1Node));
        assertFalse(cExtendsMyList1Node.isAssignableFrom(IP, cExtendsMyList1Sub1));
    }

    // normal rules for invariance apply!

    public void test7bis(MyComparable<MyList1<? extends Node>> cNode,
                         MyComparable<MyList1<? extends Sub1>> cSub1,
                         MyComparable<MyList1<? extends Sub2>> cSub2) {
        cSub1 = cSub1;
        // FAILS cSub1 = cSub2;
        // FAILS cSub1 = cNode;
        // FAILS cNode = cSub1;
    }

    @Test
    public void test7() {
        ParameterizedType extendsNode = new ParameterizedType(node, EXTENDS);
        ParameterizedType extendsSub1 = new ParameterizedType(sub1, EXTENDS);
        ParameterizedType extendsSub2 = new ParameterizedType(sub2, EXTENDS);
        ParameterizedType extendsMyList1ExtendsNode = new ParameterizedType(myList1, List.of(extendsNode));
        ParameterizedType extendsMyList1ExtendsSub1 = new ParameterizedType(myList1, List.of(extendsSub1));
        ParameterizedType extendsMyList1ExtendsSub2 = new ParameterizedType(myList1, List.of(extendsSub2));

        ParameterizedType cMyList1ExtendsNode = new ParameterizedType(myComparable,
                List.of(extendsMyList1ExtendsNode));
        assertEquals("MyComparable<MyList1<? extends Node>>",
                cMyList1ExtendsNode.output(Qualification.EMPTY).toString());
        ParameterizedType cMyList1ExtendsSub1 = new ParameterizedType(myComparable,
                List.of(extendsMyList1ExtendsSub1));
        assertEquals("MyComparable<MyList1<? extends Sub1>>",
                cMyList1ExtendsSub1.output(Qualification.EMPTY).toString());
        ParameterizedType cMyList1ExtendsSub2 = new ParameterizedType(myComparable,
                List.of(extendsMyList1ExtendsSub2));
        assertEquals("MyComparable<MyList1<? extends Sub2>>",
                cMyList1ExtendsSub2.output(Qualification.EMPTY).toString());

        assertTrue(cMyList1ExtendsSub1.isAssignableFrom(IP, cMyList1ExtendsSub1));
        assertFalse(cMyList1ExtendsSub1.isAssignableFrom(IP, cMyList1ExtendsSub2));
        assertFalse(cMyList1ExtendsSub1.isAssignableFrom(IP, cMyList1ExtendsNode));
        assertFalse(cMyList1ExtendsNode.isAssignableFrom(IP, cMyList1ExtendsSub1));
    }

}
