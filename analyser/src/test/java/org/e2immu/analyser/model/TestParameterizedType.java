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

import org.e2immu.analyser.inspector.impl.MethodInspectionImpl;
import org.e2immu.analyser.inspector.impl.ParameterInspectionImpl;
import org.e2immu.analyser.inspector.impl.TypeInspectionImpl;
import org.e2immu.analyser.model.impl.TypeParameterImpl;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.parser.impl.PrimitivesImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.e2immu.analyser.inspector.InspectionState.BY_HAND;
import static org.e2immu.analyser.model.ParameterizedType.WildCard.NONE;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestParameterizedType {

    Primitives primitives;
    InspectionProvider IP;
    TypeInfo map;
    TypeInfo hashMap;
    TypeInfo stringMap;
    TypeInfo table;
    TypeInfo function;
    TypeInfo a;
    TypeInfo b;
    TypeInfo c;


    @BeforeEach
    public void before() {
        primitives = new PrimitivesImpl();
        IP = InspectionProvider.defaultFrom(primitives);
        String PACKAGE = "org.e2immu";

        primitives.objectTypeInfo().typeInspection.set(new TypeInspectionImpl.Builder(primitives.objectTypeInfo(), BY_HAND)
                .setTypeNature(TypeNature.CLASS)
                .setAccess(Inspection.Access.PUBLIC)
                .build(null));
        primitives.integerTypeInfo().typeInspection.set(new TypeInspectionImpl.Builder(primitives.integerTypeInfo(), BY_HAND)
                .noParent(primitives)
                .setTypeNature(TypeNature.CLASS)
                .setAccess(Inspection.Access.PUBLIC)
                .build(null));

        // Map<K, V>
        map = new TypeInfo(PACKAGE, "Map");
        {
            TypeParameter mapK = new TypeParameterImpl(map, "K", 0);
            TypeParameter mapV = new TypeParameterImpl(map, "V", 1);

            TypeInspection.Builder mapInspection = new TypeInspectionImpl.Builder(map, BY_HAND)
                    .noParent(primitives)
                    .setAccess(Inspection.Access.PUBLIC)
                    .addTypeParameter(mapK)
                    .addTypeParameter(mapV);
            map.typeInspection.set(mapInspection.build(null));
        }
        // HashMap<K, V> implements Map<K, V>
        hashMap = new TypeInfo(PACKAGE, "HashMap");
        {
            TypeParameter hashMapK = new TypeParameterImpl(hashMap, "K", 0);
            TypeParameter hashMapV = new TypeParameterImpl(hashMap, "V", 1);

            TypeInspection.Builder hashMapInspection = new TypeInspectionImpl.Builder(map, BY_HAND)
                    .noParent(primitives)
                    .setAccess(Inspection.Access.PUBLIC)
                    .addInterfaceImplemented(new ParameterizedType(map,
                            List.of(new ParameterizedType(hashMapK, 0, NONE),
                                    new ParameterizedType(hashMapV, 0, NONE))))
                    .addTypeParameter(hashMapK)
                    .addTypeParameter(hashMapV);
            hashMap.typeInspection.set(hashMapInspection.build(null));
        }
        // StringMap<V> extends HashMap<String, V>
        stringMap = new TypeInfo(PACKAGE, "StringMap");
        {
            TypeParameter stringMapV = new TypeParameterImpl(stringMap, "V", 0);

            TypeInspection.Builder stringMapInspection = new TypeInspectionImpl.Builder(map, BY_HAND)
                    .setParentClass(new ParameterizedType(hashMap, List.of(primitives.stringParameterizedType(),
                            new ParameterizedType(stringMapV, 0, NONE))))
                    .addTypeParameter(stringMapV);
            stringMap.typeInspection.set(stringMapInspection
                    .setAccess(Inspection.Access.PUBLIC)
                    .build(null));
        }
        // Table extends StringMap<Integer>
        table = new TypeInfo(PACKAGE, "Table");
        {
            TypeInspection.Builder tableInspection = new TypeInspectionImpl.Builder(map, BY_HAND)
                    .setParentClass(new ParameterizedType(stringMap, List.of(primitives.integerTypeInfo().asParameterizedType(IP))));
            table.typeInspection.set(tableInspection
                    .setAccess(Inspection.Access.PUBLIC).build(null));
        }
        function = new TypeInfo(PACKAGE, "Function");
        {
            TypeParameter functionT = new TypeParameterImpl(function, "T", 0);
            TypeParameter functionR = new TypeParameterImpl(function, "R", 1);

            MethodInspectionImpl.Builder applyBuilder = new MethodInspectionImpl.Builder(function, "apply");
            MethodInfo apply = applyBuilder
                    .setReturnType(new ParameterizedType(functionR, 0, NONE))
                    .addParameter(new ParameterInspectionImpl.Builder(Identifier.generate("apply"),
                            new ParameterizedType(functionT, 0, NONE), "t", 0))
                    .build(IP).getMethodInfo();
            TypeInspection.Builder functionInspection = new TypeInspectionImpl.Builder(function, BY_HAND)
                    .noParent(primitives)
                    .setTypeNature(TypeNature.INTERFACE)
                    .setAccess(Inspection.Access.PUBLIC)
                    .addAnnotation(primitives.functionalInterfaceAnnotationExpression())
                    .addTypeParameter(functionT)
                    .addTypeParameter(functionR)
                    .addMethod(apply);
            function.typeInspection.set(functionInspection.build(null));
        }
        // A<K, V>
        a = new TypeInfo(PACKAGE, "A");
        {
            TypeParameter aK = new TypeParameterImpl(a, "K", 0);
            TypeParameter aV = new TypeParameterImpl(a, "V", 1);

            TypeInspection.Builder aInspection = new TypeInspectionImpl.Builder(a, BY_HAND)
                    .noParent(primitives)
                    .setAccess(Inspection.Access.PUBLIC)
                    .addTypeParameter(aK)
                    .addTypeParameter(aV);
            a.typeInspection.set(aInspection.build(null));
        }
        // B<X> extends A<String, X>
        b = new TypeInfo(PACKAGE, "B");
        {
            TypeParameter bX = new TypeParameterImpl(b, "X", 0);

            TypeInspection.Builder bInspection = new TypeInspectionImpl.Builder(b, BY_HAND)
                    .setParentClass(new ParameterizedType(a, List.of(
                            primitives.stringParameterizedType(),
                            new ParameterizedType(bX, 0, NONE))))
                    .setAccess(Inspection.Access.PUBLIC)
                    .addTypeParameter(bX);
            b.typeInspection.set(bInspection.build(null));
        }
        // C extends B<Integer>
        c = new TypeInfo(PACKAGE, "C");
        {
            TypeInspection.Builder cInspection = new TypeInspectionImpl.Builder(c, BY_HAND)
                    .setParentClass(new ParameterizedType(b, List.of(
                            primitives.integerTypeInfo().asParameterizedType(IP))));
            c.typeInspection.set(cInspection
                    .setAccess(Inspection.Access.PUBLIC).build(null));
        }
    }

    @Test
    public void test_0() {
        ParameterizedType integerPt = primitives.integerTypeInfo().asSimpleParameterizedType();

        ParameterizedType mapStringInteger = new ParameterizedType(map, List.of(primitives.stringParameterizedType(), integerPt));
        Map<NamedType, ParameterizedType> translation = mapStringInteger.initialTypeParameterMap(IP);
        assertEquals("{K as #0 in org.e2immu.Map=Type java.lang.String, V as #1 in org.e2immu.Map=Type java.lang.Integer}", translation.toString());

        // true or false, does not matter because both have the same typeInfo
        Map<NamedType, ParameterizedType> translation2 = map.asParameterizedType(IP).translateMap(IP, mapStringInteger, true);
        assertEquals(translation, translation2);

        ParameterizedType hashMapStringInteger = new ParameterizedType(hashMap, List.of(primitives.stringParameterizedType(),
                integerPt));
        Map<NamedType, ParameterizedType> translation3 = hashMapStringInteger.initialTypeParameterMap(IP);
        assertEquals("{K as #0 in org.e2immu.HashMap=Type java.lang.String, V as #1 in org.e2immu.HashMap=Type java.lang.Integer}", translation3.toString());
    }

    @Test
    public void test_1() {
        ParameterizedType integerPt = primitives.integerTypeInfo().asSimpleParameterizedType();

        ParameterizedType mapStringInteger = new ParameterizedType(map, List.of(primitives.stringParameterizedType(), integerPt));
        ParameterizedType functionMapToBoolean = new ParameterizedType(function, List.of(mapStringInteger, primitives.boxedBooleanTypeInfo().asSimpleParameterizedType()));
        assertEquals("Type org.e2immu.Function<org.e2immu.Map<java.lang.String,java.lang.Integer>,java.lang.Boolean>", functionMapToBoolean.toString());

        Map<NamedType, ParameterizedType> translation = functionMapToBoolean.initialTypeParameterMap(IP);
        assertEquals("K as #0 in org.e2immu.Map=Type java.lang.String, " +
                "R as #1 in org.e2immu.Function=Type java.lang.Boolean, " +
                "T as #0 in org.e2immu.Function=Type org.e2immu.Map<java.lang.String,java.lang.Integer>, " +
                "V as #1 in org.e2immu.Map=Type java.lang.Integer", translation
                .entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).sorted().collect(Collectors.joining(", ")));

        Map<NamedType, ParameterizedType> translation2 = function.asParameterizedType(IP)
                .translateMap(IP, functionMapToBoolean, true);
        assertEquals("R as #1 in org.e2immu.Function=Type java.lang.Boolean, " +
                "T as #0 in org.e2immu.Function=Type org.e2immu.Map<java.lang.String,java.lang.Integer>", translation2
                .entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).sorted().collect(Collectors.joining(", ")));
    }

    @Test
    public void test_2A() {
        // 1 level
        ParameterizedType mapPt = map.asParameterizedType(InspectionProvider.DEFAULT);
        Map<NamedType, ParameterizedType> t = hashMap.mapInTermsOfParametersOfSuperType(IP, mapPt);
        assertEquals("{K as #0 in org.e2immu.Map=Type param K, V as #1 in org.e2immu.Map=Type param V}", t.toString());

        // 2 levels (1x combine)
        Map<NamedType, ParameterizedType> t2 = stringMap.mapInTermsOfParametersOfSuperType(IP, mapPt);
        assertEquals("{K as #0 in org.e2immu.Map=Type java.lang.String, V as #1 in org.e2immu.Map=Type param V}", t2.toString());

        // 3 levels (2x combine)
        Map<NamedType, ParameterizedType> t3 = table.mapInTermsOfParametersOfSuperType(IP, mapPt);
        assertEquals("{K as #0 in org.e2immu.Map=Type java.lang.String, V as #1 in org.e2immu.Map=Type java.lang.Integer}", t3.toString());
    }

    @Test
    public void test_2B() {
        // FIELD TYPE BASED ON SCOPE

        ParameterizedType integerPt = primitives.integerTypeInfo().asSimpleParameterizedType();

        /* we simulate
        class A<K, V> { private Map<K, V> field = ... } scope = new A<String, Boolean>()  scope.field
        */
        ParameterizedType field = new ParameterizedType(map, List.of(
                new ParameterizedType(a.typeInspection.get().typeParameters().get(0), 0, NONE),
                new ParameterizedType(a.typeInspection.get().typeParameters().get(1), 0, NONE)));
        ParameterizedType scope = new ParameterizedType(a, List.of(primitives.stringParameterizedType(), integerPt));
        ParameterizedType concreteField = field.inferConcreteFieldTypeFromConcreteScope(IP, a.asParameterizedType(IP), scope);
        assertEquals("Type org.e2immu.Map<java.lang.String,java.lang.Integer>", concreteField.toString());

        /* we simulate
        class A<K, V> { private Map<K, V> field = ... }
        class B<X> extends A<String, X> { ... } b.field
        */
        ParameterizedType scope2 = new ParameterizedType(b, List.of(
                new ParameterizedType(b.typeInspection.get().typeParameters().get(0), 0, NONE)));
        ParameterizedType concreteField2 = field.inferConcreteFieldTypeFromConcreteScope(IP, a.asParameterizedType(IP), scope2);
        assertEquals("Type org.e2immu.Map<java.lang.String,X>", concreteField2.toString());

         /* we simulate
        class A<K, V> { private Map<K, V> field = ... }
        class C extends B<Integer> (extends A<String, Integer>) { ... } c.field
        */
        ParameterizedType scope3 = c.asParameterizedType(IP);
        ParameterizedType concreteField3 = field.inferConcreteFieldTypeFromConcreteScope(IP, a.asParameterizedType(IP), scope3);
        assertEquals("Type org.e2immu.Map<java.lang.String,java.lang.Integer>", concreteField3.toString());
    }

    @Test
    public void test_3A() {
        ParameterizedType mapPt = map.asSimpleParameterizedType();
        // 1 level
        Map<NamedType, ParameterizedType> t = hashMap.mapInTermsOfParametersOfSubType(IP, mapPt);
        assertEquals("{K as #0 in org.e2immu.HashMap=Type param K, V as #1 in org.e2immu.HashMap=Type param V}", t.toString());

        // 2 levels (1x combine)
        Map<NamedType, ParameterizedType> t2 = stringMap.mapInTermsOfParametersOfSubType(IP, mapPt);
        assertEquals("{V as #0 in org.e2immu.StringMap=Type param V}", t2.toString());

        // 3 levels (2x combine)
        Map<NamedType, ParameterizedType> t3 = table.mapInTermsOfParametersOfSubType(IP, mapPt);
        assertEquals("{}", t3.toString());
    }

    @Test
    public void test_3B() {
        // NEW OBJECT CREATION

        // we simulate Map<String, Integer> map = new HashMap<>(), where we need to obtain the concrete pt of HashMap
        ParameterizedType mapStringInteger = new ParameterizedType(map, List.of(primitives.stringParameterizedType(),
                primitives.integerTypeInfo().asSimpleParameterizedType()));
        ParameterizedType hashMapPt = hashMap.asParameterizedType(IP);
        Map<NamedType, ParameterizedType> t1 = hashMapPt.translateMap(IP, mapStringInteger, false);
        assertEquals("{K as #0 in org.e2immu.HashMap=Type java.lang.String, V as #1 in org.e2immu.HashMap=Type java.lang.Integer}", t1.toString());
        ParameterizedType concreteHashMap = hashMapPt.inferDiamondNewObjectCreation(IP, mapStringInteger);
        assertEquals("Type org.e2immu.HashMap<java.lang.String,java.lang.Integer>", concreteHashMap.toString());

        // we simulate Map<String, Integer> map = new StringMap<>(), where we need to obtain the concrete pt of StringMap
        ParameterizedType stringMapPt = stringMap.asParameterizedType(IP);
        Map<NamedType, ParameterizedType> t2 = stringMapPt.translateMap(IP, mapStringInteger, false);
        assertEquals("{V as #0 in org.e2immu.StringMap=Type java.lang.Integer}", t2.toString());
        ParameterizedType concreteStringMap = stringMapPt.inferDiamondNewObjectCreation(IP, mapStringInteger);
        assertEquals("Type org.e2immu.StringMap<java.lang.Integer>", concreteStringMap.toString());
    }
}
