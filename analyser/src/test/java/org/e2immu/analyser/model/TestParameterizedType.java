/*
 * e2immu: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.e2immu.analyser.model;

import org.e2immu.analyser.inspector.MethodInspectionImpl;
import org.e2immu.analyser.inspector.ParameterInspectionImpl;
import org.e2immu.analyser.inspector.TypeInspectionImpl;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.Logger;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.e2immu.analyser.inspector.TypeInspectionImpl.InspectionState.BY_HAND;
import static org.e2immu.analyser.model.ParameterizedType.WildCard.NONE;

public class TestParameterizedType {

    @BeforeClass
    public static void beforeClass() {
        Logger.activate();
    }

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


    @Before
    public void before() {
        primitives = new Primitives();
        IP = InspectionProvider.DEFAULT;
        String PACKAGE = "org.e2immu";

        primitives.objectTypeInfo.typeInspection.set(new TypeInspectionImpl.Builder(primitives.objectTypeInfo, BY_HAND)
                .setTypeNature(TypeNature.CLASS)
                .build());
        primitives.integerTypeInfo.typeInspection.set(new TypeInspectionImpl.Builder(primitives.integerTypeInfo, BY_HAND)
                .setParentClass(primitives.objectParameterizedType)
                .setTypeNature(TypeNature.CLASS)
                .build());

        // Map<K, V>
        map = new TypeInfo(PACKAGE, "Map");
        {
            TypeParameter mapK = new TypeParameterImpl(map, "K", 0);
            TypeParameter mapV = new TypeParameterImpl(map, "V", 1);

            TypeInspectionImpl.Builder mapInspection = new TypeInspectionImpl.Builder(map, BY_HAND)
                    .setParentClass(primitives.objectParameterizedType)
                    .addTypeParameter(mapK)
                    .addTypeParameter(mapV);
            map.typeInspection.set(mapInspection.build());
        }
        // HashMap<K, V> implements Map<K, V>
        hashMap = new TypeInfo(PACKAGE, "HashMap");
        {
            TypeParameter hashMapK = new TypeParameterImpl(hashMap, "K", 0);
            TypeParameter hashMapV = new TypeParameterImpl(hashMap, "V", 1);

            TypeInspectionImpl.Builder hashMapInspection = new TypeInspectionImpl.Builder(map, BY_HAND)
                    .setParentClass(primitives.objectParameterizedType)
                    .addInterfaceImplemented(new ParameterizedType(map,
                            List.of(new ParameterizedType(hashMapK, 0, NONE),
                                    new ParameterizedType(hashMapV, 0, NONE))))
                    .addTypeParameter(hashMapK)
                    .addTypeParameter(hashMapV);
            hashMap.typeInspection.set(hashMapInspection.build());
        }
        // StringMap<V> extends HashMap<String, V>
        stringMap = new TypeInfo(PACKAGE, "StringMap");
        {
            TypeParameter stringMapV = new TypeParameterImpl(stringMap, "V", 0);

            TypeInspectionImpl.Builder stringMapInspection = new TypeInspectionImpl.Builder(map, BY_HAND)
                    .setParentClass(new ParameterizedType(hashMap, List.of(primitives.stringParameterizedType,
                            new ParameterizedType(stringMapV, 0, NONE))))
                    .addTypeParameter(stringMapV);
            stringMap.typeInspection.set(stringMapInspection.build());
        }
        // Table extends StringMap<Integer>
        table = new TypeInfo(PACKAGE, "Table");
        {
            TypeInspectionImpl.Builder tableInspection = new TypeInspectionImpl.Builder(map, BY_HAND)
                    .setParentClass(new ParameterizedType(stringMap, List.of(primitives.integerTypeInfo.asParameterizedType(IP))));
            table.typeInspection.set(tableInspection.build());
        }
        function = new TypeInfo(PACKAGE, "Function");
        {
            TypeParameter functionT = new TypeParameterImpl(map, "T", 0);
            TypeParameter functionR = new TypeParameterImpl(map, "R", 1);

            MethodInspectionImpl.Builder applyBuilder = new MethodInspectionImpl.Builder(function, "apply");
            MethodInfo apply = applyBuilder
                    .setReturnType(new ParameterizedType(functionR, 0, NONE))
                    .addParameter(new ParameterInspectionImpl.Builder(
                            new ParameterizedType(functionT, 0, NONE), "t", 0))
                    .build(IP).getMethodInfo();
            TypeInspectionImpl.Builder functionInspection = new TypeInspectionImpl.Builder(function, BY_HAND)
                    .setParentClass(primitives.objectParameterizedType)
                    .setTypeNature(TypeNature.INTERFACE)
                    .addAnnotation(primitives.functionalInterfaceAnnotationExpression)
                    .addTypeParameter(functionT)
                    .addTypeParameter(functionR)
                    .addMethod(apply);
            function.typeInspection.set(functionInspection.build());
        }
        // A<K, V>
        a = new TypeInfo(PACKAGE, "A");
        {
            TypeParameter aK = new TypeParameterImpl(a, "K", 0);
            TypeParameter aV = new TypeParameterImpl(a, "V", 1);

            TypeInspectionImpl.Builder aInspection = new TypeInspectionImpl.Builder(a, BY_HAND)
                    .setParentClass(primitives.objectParameterizedType)
                    .addTypeParameter(aK)
                    .addTypeParameter(aV);
            a.typeInspection.set(aInspection.build());
        }
        // B<X> extends A<String, X>
        b = new TypeInfo(PACKAGE, "B");
        {
            TypeParameter bX = new TypeParameterImpl(b, "X", 0);

            TypeInspectionImpl.Builder bInspection = new TypeInspectionImpl.Builder(b, BY_HAND)
                    .setParentClass(new ParameterizedType(a, List.of(
                            primitives.stringParameterizedType,
                            new ParameterizedType(bX, 0, NONE))))
                    .addTypeParameter(bX);
            b.typeInspection.set(bInspection.build());
        }
        // C extends B<Integer>
        c = new TypeInfo(PACKAGE, "C");
        {
            TypeInspectionImpl.Builder cInspection = new TypeInspectionImpl.Builder(c, BY_HAND)
                    .setParentClass(new ParameterizedType(b, List.of(
                            primitives.integerTypeInfo.asParameterizedType(IP))));
            c.typeInspection.set(cInspection.build());
        }
    }

    @Test
    public void test_0() {
        ParameterizedType mapStringInteger = new ParameterizedType(map, List.of(primitives.stringParameterizedType,
                primitives.intParameterizedType));
        Map<NamedType, ParameterizedType> translation = mapStringInteger.initialTypeParameterMap(IP);
        Assert.assertEquals("{K as #0 in org.e2immu.Map=Type java.lang.String, V as #1 in org.e2immu.Map=Type int}", translation.toString());

        // true or false, does not matter because both have the same typeInfo
        Map<NamedType, ParameterizedType> translation2 = map.asParameterizedType(IP).translateMap(IP, mapStringInteger, true);
        Assert.assertEquals(translation, translation2);

        ParameterizedType hashMapStringInteger = new ParameterizedType(hashMap, List.of(primitives.stringParameterizedType,
                primitives.intParameterizedType));
        Map<NamedType, ParameterizedType> translation3 = hashMapStringInteger.initialTypeParameterMap(IP);
        Assert.assertEquals("{K as #0 in org.e2immu.HashMap=Type java.lang.String, V as #1 in org.e2immu.HashMap=Type int}", translation3.toString());
    }

    @Test
    public void test_1() {
        ParameterizedType mapStringInteger = new ParameterizedType(map, List.of(primitives.stringParameterizedType,
                primitives.integerTypeInfo.asSimpleParameterizedType()));
        ParameterizedType functionMapToBoolean = new ParameterizedType(function, List.of(mapStringInteger, primitives.boxedBooleanTypeInfo.asSimpleParameterizedType()));
        Map<NamedType, ParameterizedType> translation = functionMapToBoolean.initialTypeParameterMap(IP);
        Assert.assertEquals("{T as #0 in org.e2immu.Map=Type org.e2immu.Map<java.lang.String,java.lang.Integer>, " +
                "R as #1 in org.e2immu.Map=Type java.lang.Boolean}", translation.toString());

        Map<NamedType, ParameterizedType> translation2 = function.asParameterizedType(IP).translateMap(IP, functionMapToBoolean, true);
        Assert.assertEquals(translation, translation2);
    }

    @Test
    public void test_2A() {
        // 1 level
        Map<NamedType, ParameterizedType> t = hashMap.mapInTermsOfParametersOfSuperType(IP, map);
        Assert.assertEquals("{K as #0 in org.e2immu.Map=Type param K, V as #1 in org.e2immu.Map=Type param V}", t.toString());

        // 2 levels (1x combine)
        Map<NamedType, ParameterizedType> t2 = stringMap.mapInTermsOfParametersOfSuperType(IP, map);
        Assert.assertEquals("{K as #0 in org.e2immu.Map=Type java.lang.String, V as #1 in org.e2immu.Map=Type param V}", t2.toString());

        // 3 levels (2x combine)
        Map<NamedType, ParameterizedType> t3 = table.mapInTermsOfParametersOfSuperType(IP, map);
        Assert.assertEquals("{K as #0 in org.e2immu.Map=Type java.lang.String, V as #1 in org.e2immu.Map=Type java.lang.Integer}", t3.toString());
    }

    @Test
    public void test_2B() {
        // FIELD TYPE BASED ON SCOPE

        /* we simulate
        class A<K, V> { private Map<K, V> field = ... } scope = new A<String, Boolean>()  scope.field
        */
        ParameterizedType field = new ParameterizedType(map, List.of(
                new ParameterizedType(a.typeInspection.get().typeParameters().get(0), 0, NONE),
                new ParameterizedType(a.typeInspection.get().typeParameters().get(1), 0, NONE)));
        ParameterizedType scope = new ParameterizedType(a, List.of(primitives.stringParameterizedType, primitives.intParameterizedType));
        ParameterizedType concreteField = field.inferConcreteFieldTypeFromConcreteScope(IP, a.asParameterizedType(IP), scope);
        Assert.assertEquals("Type org.e2immu.Map<java.lang.String,int>", concreteField.toString());

        /* we simulate
        class A<K, V> { private Map<K, V> field = ... }
        class B<X> extends A<String, X> { ... } b.field
        */
        ParameterizedType scope2 = new ParameterizedType(b, List.of(
                new ParameterizedType(b.typeInspection.get().typeParameters().get(0), 0, NONE)));
        ParameterizedType concreteField2 = field.inferConcreteFieldTypeFromConcreteScope(IP, a.asParameterizedType(IP), scope2);
        Assert.assertEquals("Type org.e2immu.Map<java.lang.String,X>", concreteField2.toString());

         /* we simulate
        class A<K, V> { private Map<K, V> field = ... }
        class C extends B<Integer> (extends A<String, Integer>) { ... } c.field
        */
        ParameterizedType scope3 = c.asParameterizedType(IP);
        ParameterizedType concreteField3 = field.inferConcreteFieldTypeFromConcreteScope(IP, a.asParameterizedType(IP), scope3);
        Assert.assertEquals("Type org.e2immu.Map<java.lang.String,java.lang.Integer>", concreteField3.toString());
    }

    @Test
    public void test_3A() {
        // 1 level
        Map<NamedType, ParameterizedType> t = hashMap.mapInTermsOfParametersOfSubType(IP, map);
        Assert.assertEquals("{K as #0 in org.e2immu.HashMap=Type param K, V as #1 in org.e2immu.HashMap=Type param V}", t.toString());

        // 2 levels (1x combine)
        Map<NamedType, ParameterizedType> t2 = stringMap.mapInTermsOfParametersOfSubType(IP, map);
        Assert.assertEquals("{V as #0 in org.e2immu.StringMap=Type param V}", t2.toString());

        // 3 levels (2x combine)
        Map<NamedType, ParameterizedType> t3 = table.mapInTermsOfParametersOfSubType(IP, map);
        Assert.assertEquals("{}", t3.toString());
    }

    @Test
    public void test_3B() {
        // NEW OBJECT CREATION

        // we simulate Map<String, Integer> map = new HashMap<>(), where we need to obtain the concrete pt of HashMap
        ParameterizedType mapStringInteger = new ParameterizedType(map, List.of(primitives.stringParameterizedType,
                primitives.intParameterizedType));
        ParameterizedType hashMapPt = hashMap.asParameterizedType(IP);
        Map<NamedType, ParameterizedType> t1 = hashMapPt.translateMap(IP, mapStringInteger, false);
        Assert.assertEquals("{K as #0 in org.e2immu.HashMap=Type java.lang.String, V as #1 in org.e2immu.HashMap=Type int}", t1.toString());
        ParameterizedType concreteHashMap = hashMapPt.inferDiamondNewObjectCreation(IP, mapStringInteger);
        Assert.assertEquals("Type org.e2immu.HashMap<java.lang.String,int>", concreteHashMap.toString());

        // we simulate Map<String, Integer> map = new StringMap<>(), where we need to obtain the concrete pt of StringMap
        ParameterizedType stringMapPt = stringMap.asParameterizedType(IP);
        Map<NamedType, ParameterizedType> t2 = stringMapPt.translateMap(IP, mapStringInteger, false);
        Assert.assertEquals("{V as #0 in org.e2immu.StringMap=Type int}", t2.toString());
        ParameterizedType concreteStringMap = stringMapPt.inferDiamondNewObjectCreation(IP, mapStringInteger);
        Assert.assertEquals("Type org.e2immu.StringMap<int>", concreteStringMap.toString());
    }
}
