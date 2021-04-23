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

package org.e2immu.analyser.parser;

import org.e2immu.analyser.config.Configuration;
import org.e2immu.analyser.config.InputConfiguration;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.resolver.ShallowMethodResolver;
import org.e2immu.analyser.resolver.SortedType;
import org.e2immu.analyser.util.Trie;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.e2immu.analyser.inspector.TypeInspectionImpl.InspectionState.TRIGGER_JAVA_PARSER;
import static org.junit.jupiter.api.Assertions.*;

public class Test_13_MethodOverloadAndSuperTypes {
    private static final Logger LOGGER = LoggerFactory.getLogger(Test_13_MethodOverloadAndSuperTypes.class);
    public static final String SRC_TEST_JAVA_ORG_E2IMMU_ANALYSER = "src/test/java/org/e2immu/analyser/";

    @BeforeAll
    public static void beforeClass() {
        org.e2immu.analyser.util.Logger.activate(org.e2immu.analyser.util.Logger.LogTarget.INSPECTOR);
    }

    Parser parser;
    TypeContext typeContext;

    @BeforeEach
    public void before() throws IOException {
        parser = new Parser(new Configuration.Builder()
                .setInputConfiguration(new InputConfiguration.Builder()
                        .addSources("src/main/java")
                        .addRestrictSourceToPackages("some.unknown.package")
                        .build()).build());
        parser.run();
        typeContext = parser.getTypeContext();
    }

    @Test
    public void testSetCollection() {
        TypeInfo set = typeContext.typeMapBuilder.get("java.util.Set");
        assertNotNull(set);
        MethodInfo containsAll = set.findUniqueMethod("containsAll", 1);
        Set<MethodInfo> overloads = containsAll.methodResolution.get().overrides();
        TypeInfo collection = typeContext.typeMapBuilder.get("java.util.Collection");
        assertNotNull(collection);
        MethodInfo containsAllInCollection = collection.findUniqueMethod("containsAll", 1);
        assertTrue(overloads.contains(containsAllInCollection));
    }

    @Test
    public void testThrowable() {
        TypeInfo throwable = typeContext.typeMapBuilder.get("java.lang.Throwable");
        assertNotNull(throwable);
        Set<TypeInfo> superTypes = throwable.typeResolution.get().superTypesExcludingJavaLangObject();
        assertEquals("[java.io.Serializable]", superTypes.toString());
    }

    @Test
    public void testSetCollectionEquals() {
        TypeInfo set = typeContext.typeMapBuilder.get("java.util.Set");
        assertNotNull(set);
        MethodInfo equalsInSet = set.findUniqueMethod("equals", 1);
        Set<MethodInfo> overloads = equalsInSet.methodResolution.get().overrides();
        TypeInfo collection = typeContext.typeMapBuilder.get("java.util.Collection");
        assertNotNull(collection);
        MethodInfo equalsInCollection = collection.findUniqueMethod("equals", 1);
        assertTrue(overloads.contains(equalsInCollection));
        TypeInfo object = typeContext.typeMapBuilder.get("java.lang.Object");
        assertNotNull(object);
        MethodInfo equalsInObject = object.findUniqueMethod("equals", 1);
        assertTrue(overloads.contains(equalsInObject));
    }

    @Test
    public void test() throws IOException {
        TypeInfo methodOverloadOrig = typeContext.typeMapBuilder.getOrCreate(
                "org.e2immu.analyser.testexample", "MethodOverload", TRIGGER_JAVA_PARSER);
        URL url = new File(SRC_TEST_JAVA_ORG_E2IMMU_ANALYSER + "testexample/MethodOverload.java").toURI().toURL();
        List<SortedType> types = parser.inspectAndResolve(Map.of(methodOverloadOrig, url), new Trie<>(), true, false);
        LOGGER.info("Have {} types", types.size());

        // method: hashCode
        TypeInfo methodOverload = types.get(0).primaryType();
        assertSame(methodOverload, methodOverloadOrig);

        MethodInfo hashCode = methodOverload.typeInspection.get().methods()
                .stream().filter(m -> m.name.equals("hashCode")).findFirst().orElseThrow();
        Set<MethodInfo> overloadsOfHashCode = hashCode.methodResolution.get().overrides();
        LOGGER.info("Overloads of hashCode: {}", overloadsOfHashCode);
        assertEquals("[java.lang.Object.hashCode()]", overloadsOfHashCode.toString());

        // method: C1.method(int)
        TypeInfo c1 = methodOverload.typeInspection.get().subTypes().stream().filter(t -> t.simpleName.equals("C1")).findFirst().orElseThrow();

        Set<TypeInfo> superTypesC1 = c1.typeResolution.get().superTypesExcludingJavaLangObject();
        assertEquals("[org.e2immu.analyser.testexample.MethodOverload.I1]", superTypesC1.toString());
        List<ParameterizedType> directSuperTypesC1 = ShallowMethodResolver.directSuperTypes(parser.getTypeContext(), c1);
        assertEquals("[Type java.lang.Object, Type org.e2immu.analyser.testexample.MethodOverload.I1]", directSuperTypesC1.toString());


        LOGGER.info("Distinguishing names of C1 methods: " +
                c1.typeInspection.get().methods().stream().map(MethodInfo::distinguishingName).collect(Collectors.joining(", ")));
        MethodInfo m1 = c1.typeInspection.get().methods().stream().filter(m -> m.distinguishingName()
                .equals("org.e2immu.analyser.testexample.MethodOverload.C1.method(int)")).findFirst().orElseThrow();
        Set<MethodInfo> overloadsOfM1 = m1.methodResolution.get().overrides();
        LOGGER.info("Overloads of m1: {}", overloadsOfM1);
        assertEquals("[org.e2immu.analyser.testexample.MethodOverload.I1.method(int)]", overloadsOfM1.toString());

        // method C2.toString()
        TypeInfo c2 = methodOverload.typeInspection.get().subTypes().stream().filter(t -> t.simpleName.equals("C2")).findFirst().orElseThrow();
        LOGGER.info("Distinguishing names of C2 methods: " +
                c2.typeInspection.get().methods().stream().map(MethodInfo::distinguishingName).collect(Collectors.joining(", ")));

        Set<TypeInfo> superTypesC2 = c2.typeResolution.get().superTypesExcludingJavaLangObject();
        assertEquals("org.e2immu.analyser.testexample.MethodOverload.C1,org.e2immu.analyser.testexample.MethodOverload.I1",
                superTypesC2.stream().map(Objects::toString).sorted().collect(Collectors.joining(",")));
        List<ParameterizedType> directSuperTypesC2 = ShallowMethodResolver.directSuperTypes(parser.getTypeContext(), c2);
        assertEquals("[Type org.e2immu.analyser.testexample.MethodOverload.C1]", directSuperTypesC2.toString());

        MethodInfo toString = c2.findUniqueMethod("toString", 0);
        Set<MethodInfo> overloadsOfToString = toString.methodResolution.get().overrides();
        LOGGER.info("Overloads of toString: {}", overloadsOfToString);
        assertEquals("java.lang.Object.toString(),org.e2immu.analyser.testexample.MethodOverload.C1.toString()",
                overloadsOfToString.stream().map(MethodInfo::toString).sorted().collect(Collectors.joining(",")));
    }

}
