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

package org.e2immu.analyser.bytecode.asm;

import org.e2immu.analyser.annotationxml.AnnotationXmlReader;
import org.e2immu.analyser.bytecode.ByteCodeInspector;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.inspector.TypeInspectionImpl;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.Input;
import org.e2immu.analyser.parser.TypeMapImpl;
import org.e2immu.analyser.util.Logger;
import org.e2immu.analyser.util.Resources;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.EnumMap;
import java.util.SortedSet;
import java.util.Spliterator;

import static org.e2immu.analyser.inspector.TypeInspectionImpl.InspectionState.STARTING_BYTECODE;
import static org.e2immu.analyser.inspector.TypeInspectionImpl.InspectionState.TRIGGER_BYTECODE_INSPECTION;

public class TestParseGenerics {

    private static TypeContext typeContext;

    @BeforeClass
    public static void beforeClass() throws IOException {
        Logger.activate(Logger.LogTarget.BYTECODE_INSPECTOR, Logger.LogTarget.BYTECODE_INSPECTOR_DEBUG);

        Resources resources = new Resources();
        resources.addJmod(new URL("jar:file:" + System.getProperty("java.home") + "/jmods/java.base.jmod!/"));
        Resources annotationResources = new Resources();
        AnnotationXmlReader annotationParser = new AnnotationXmlReader(annotationResources);
        typeContext = new TypeContext(new TypeMapImpl.Builder());
        ByteCodeInspector byteCodeInspector = new ByteCodeInspector(resources, annotationParser, typeContext);
        typeContext.typeMapBuilder.setByteCodeInspector(byteCodeInspector);
        typeContext.loadPrimitives();
        Input.preload(typeContext, byteCodeInspector, resources, "java.util");
    }

    @Test
    public void testNormalTypeParameter() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Spliterator.class);
        Assert.assertEquals("Spliterator<T>", typeInfo.asParameterizedType(typeContext).stream(typeContext, false));
    }

    @Test
    public void testWildcard() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Collection.class);
        Assert.assertEquals("Collection<E>", typeInfo.asParameterizedType(typeContext).stream(typeContext, false));
        TypeInspection typeInspection = typeContext.getTypeInspection(typeInfo);
        MethodInfo containsAll = typeInspection.methods().stream().filter(m -> m.name.equals("containsAll")).findFirst().orElseThrow();
        Assert.assertEquals("java.util.Collection.containsAll(Collection<?>)", containsAll.fullyQualifiedName);
    }

    @Test
    public void testExtends1() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Collection.class);
        TypeInspection typeInspection = typeContext.getTypeInspection(typeInfo);
        MethodInfo addAll = typeInspection.methods().stream().filter(m -> m.name.equals("addAll")).findFirst().orElseThrow();
        Assert.assertEquals("java.util.Collection.addAll(Collection<? extends E>)", addAll.fullyQualifiedName);
    }

    @Test
    public void testExtends2() {
        TypeInfo typeInfo = typeContext.getFullyQualified(EnumMap.class);
        TypeInspectionImpl.Builder typeInspectionBuilder = (TypeInspectionImpl.Builder) typeContext.getTypeInspection(typeInfo);
        TypeContext newTypeContext = new TypeContext(typeContext);
        FindType findType = (fqn, path) -> newTypeContext.typeMapBuilder.getOrCreate(fqn, TRIGGER_BYTECODE_INSPECTION);

        String signature = "<K:Ljava/lang/Enum<TK;>;V:Ljava/lang/Object;>Ljava/util/AbstractMap<TK;TV;>;Ljava/io/Serializable;Ljava/lang/Cloneable;";
        ParseGenerics parseGenerics = new ParseGenerics(newTypeContext, typeInfo, typeInspectionBuilder,
                findType);
        int expected = "<K:Ljava/lang/Enum<TK;>;V:Ljava/lang/Object;>".length();
        int pos = parseGenerics.parseTypeGenerics(signature) + 1;
        Assert.assertEquals(expected, pos);

        Assert.assertEquals("EnumMap<K extends Enum<K>, V>", typeInfo.asParameterizedType(typeContext).stream(typeContext, false));
    }

    @Test
    public void testSuper() {
        TypeInfo sortedSet = typeContext.getFullyQualified(SortedSet.class);
        TypeInspection typeInspection = typeContext.getTypeInspection(sortedSet);
        MethodInfo comparator = typeInspection.methods().stream().filter(m -> m.name.equals("comparator")).findFirst().orElseThrow();
        MethodInspection comparatorInspection = typeContext.getMethodInspection(comparator);
        Assert.assertEquals("Comparator<? super E>", comparatorInspection.getReturnType().stream(typeContext, false));
    }

    @Test
    public void testExtends3() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Spliterator.OfPrimitive.class);
        Assert.assertEquals("OfPrimitive<T, T_CONS, T_SPLITR extends Spliterator.OfPrimitive<T, T_CONS, T_SPLITR>>", typeInfo.asParameterizedType(typeContext).stream(typeContext, false));
    }

    @Test
    public void testGenericsAbstractClassLoaderValue() {
        TypeContext newTypeContext = new TypeContext(typeContext);
        newTypeContext.addToContext(new TypeParameterImpl("V", 0));
        newTypeContext.addToContext(new TypeParameterImpl("CLV", 1));
        FindType findType = (fqn, path) -> newTypeContext.typeMapBuilder.getOrCreate(fqn, TRIGGER_BYTECODE_INSPECTION);
        TypeInfo typeInfo = TypeInfo.fromFqn("jdk.internal.loader.AbstractClassLoaderValue");
        TypeInspectionImpl.Builder typeInspectionBuilder = typeContext.typeMapBuilder.add(typeInfo, STARTING_BYTECODE);

        ParseGenerics parseGenerics = new ParseGenerics(newTypeContext, typeInfo, typeInspectionBuilder,
                findType);
        String signature = "<K:Ljava/lang/Object;>Ljdk/internal/loader/AbstractClassLoaderValue<Ljdk/internal/loader/AbstractClassLoaderValue<TCLV;TV;>.Sub<TK;>;TV;>;";

        int expected = "<K:Ljava/lang/Object;>".length();
        int pos = parseGenerics.parseTypeGenerics(signature) + 1;
        Assert.assertEquals(expected, pos);
    }
}
