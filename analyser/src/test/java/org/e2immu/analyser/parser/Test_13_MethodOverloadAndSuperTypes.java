/*
 * e2immu-analyser: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.e2immu.analyser.parser;

import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.TypeInfo;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class Test_13_MethodOverloadAndSuperTypes {
    private static final Logger LOGGER = LoggerFactory.getLogger(Test_13_MethodOverloadAndSuperTypes.class);
    public static final String SRC_TEST_JAVA_ORG_E2IMMU_ANALYSER = "src/test/java/org/e2immu/analyser/";

    @BeforeClass
    public static void beforeClass() {
        org.e2immu.analyser.util.Logger.activate(org.e2immu.analyser.util.Logger.LogTarget.INSPECT);
    }

    @Test
    public void testSetCollection() throws IOException {
        Parser parser = new Parser();
        TypeContext typeContext = parser.getTypeContext();
        Primitives primitives = typeContext.getPrimitives();
        TypeInfo set = typeContext.typeStore.get("java.util.Set");
        Assert.assertNotNull(set);
        MethodInfo containsAll = set.findUniqueMethod("containsAll", 1);
        Set<MethodInfo> overloads = set.overrides(primitives, containsAll, true);
        TypeInfo collection = typeContext.typeStore.get("java.util.Collection");
        Assert.assertNotNull(collection);
        MethodInfo containsAllInCollection = collection.findUniqueMethod("containsAll", 1);
        Assert.assertTrue(overloads.contains(containsAllInCollection));
    }

    @Test
    public void testThrowable() throws IOException {
        Parser parser = new Parser();
        TypeContext typeContext = parser.getTypeContext();
        Primitives primitives = typeContext.getPrimitives();
        TypeInfo throwable = typeContext.typeStore.get("java.lang.Throwable");
        Assert.assertNotNull(throwable);
        List<TypeInfo> superTypes = throwable.superTypes(primitives);
        TypeInfo object = typeContext.typeStore.get("java.lang.Object");
        Assert.assertNotNull(object);
        Assert.assertTrue(superTypes.contains(object));
    }

    @Test
    public void testSetCollectionEquals() throws IOException {
        Parser parser = new Parser();
        TypeContext typeContext = parser.getTypeContext();
        Primitives primitives = typeContext.getPrimitives();

        TypeInfo set = typeContext.typeStore.get("java.util.Set");
        Assert.assertNotNull(set);
        MethodInfo equalsInSet = set.findUniqueMethod("equals", 1);
        Set<MethodInfo> overloads = set.overrides(primitives, equalsInSet, true);
        TypeInfo collection = typeContext.typeStore.get("java.util.Collection");
        Assert.assertNotNull(collection);
        MethodInfo equalsInCollection = collection.findUniqueMethod("equals", 1);
        Assert.assertTrue(overloads.contains(equalsInCollection));
        TypeInfo object = typeContext.typeStore.get("java.lang.Object");
        Assert.assertNotNull(object);
        MethodInfo equalsInObject = object.findUniqueMethod("equals", 1);
        Assert.assertTrue(overloads.contains(equalsInObject));
    }

    @Test
    public void test() throws IOException {
        Parser parser = new Parser();
        Primitives primitives = parser.getTypeContext().getPrimitives();

        TypeInfo methodOverloadOrig = parser.getTypeContext().typeStore.getOrCreate("org.e2immu.analyser.testexample.MethodOverload");
        URL url = new File(SRC_TEST_JAVA_ORG_E2IMMU_ANALYSER + "testexample/MethodOverload.java").toURI().toURL();
        List<SortedType> types = parser.parseJavaFiles(Map.of(methodOverloadOrig, url));
        LOGGER.info("Have {} types", types.size());

        // method: hashCode
        TypeInfo methodOverload = types.get(0).primaryType;
        Assert.assertSame(methodOverload, methodOverloadOrig);

        MethodInfo hashCode = methodOverload.typeInspection.getPotentiallyRun().methods
                .stream().filter(m -> m.name.equals("hashCode")).findFirst().orElseThrow();
        Set<MethodInfo> overloadsOfHashCode = methodOverload.overrides(primitives, hashCode, true);
        LOGGER.info("Overloads of hashCode: {}", overloadsOfHashCode);
        Assert.assertEquals("[java.lang.Object.hashCode()]", overloadsOfHashCode.toString());

        // method: C1.method(int)
        TypeInfo c1 = methodOverload.typeInspection.getPotentiallyRun().subTypes.stream().filter(t -> t.simpleName.equals("C1")).findFirst().orElseThrow();

        List<TypeInfo> superTypesC1 = c1.superTypes(primitives);
        Assert.assertEquals("[java.lang.Object, org.e2immu.analyser.testexample.MethodOverload.I1]", superTypesC1.toString());
        List<ParameterizedType> directSuperTypesC1 = c1.directSuperTypes(primitives);
        Assert.assertEquals("[Type java.lang.Object, Type org.e2immu.analyser.testexample.MethodOverload.I1]", directSuperTypesC1.toString());


        LOGGER.info("Distinguishing names of C1 methods: " +
                c1.typeInspection.getPotentiallyRun().methods.stream().map(MethodInfo::distinguishingName).collect(Collectors.joining(", ")));
        MethodInfo m1 = c1.typeInspection.getPotentiallyRun().methods.stream().filter(m -> m.distinguishingName()
                .equals("org.e2immu.analyser.testexample.MethodOverload.C1.method(int)")).findFirst().orElseThrow();
        Set<MethodInfo> overloadsOfM1 = c1.overrides(primitives, m1, true);
        LOGGER.info("Overloads of m1: {}", overloadsOfM1);
        Assert.assertEquals("[org.e2immu.analyser.testexample.MethodOverload.I1.method(int)]", overloadsOfM1.toString());

        // method C2.toString()
        TypeInfo c2 = methodOverload.typeInspection.getPotentiallyRun().subTypes.stream().filter(t -> t.simpleName.equals("C2")).findFirst().orElseThrow();
        LOGGER.info("Distinguishing names of C2 methods: " +
                c2.typeInspection.getPotentiallyRun().methods.stream().map(MethodInfo::distinguishingName).collect(Collectors.joining(", ")));

        List<TypeInfo> superTypesC2 = c2.superTypes(primitives);
        Assert.assertEquals("[org.e2immu.analyser.testexample.MethodOverload.C1, java.lang.Object, org.e2immu.analyser.testexample.MethodOverload.I1]", superTypesC2.toString());
        List<ParameterizedType> directSuperTypesC2 = c2.directSuperTypes(primitives);
        Assert.assertEquals("[Type org.e2immu.analyser.testexample.MethodOverload.C1]", directSuperTypesC2.toString());

        MethodInfo toString = c2.findUniqueMethod("toString", 0);
        Set<MethodInfo> overloadsOfToString = c2.overrides(primitives, toString, true);
        LOGGER.info("Overloads of toString: {}", overloadsOfToString);
        Assert.assertEquals("[java.lang.Object.toString(), org.e2immu.analyser.testexample.MethodOverload.C1.toString()]",
                overloadsOfToString.toString());
    }

}