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
import org.e2immu.analyser.output.Formatter;
import org.e2immu.analyser.output.FormattingOptions;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.parser.Input;
import org.e2immu.analyser.parser.TypeMapImpl;
import org.e2immu.analyser.util.Logger;
import org.e2immu.analyser.util.Resources;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.net.URL;
import java.util.*;

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
        Assert.assertEquals("java.util.Spliterator<T>", typeInfo.asParameterizedType(typeContext).printForMethodFQN(typeContext, false, Diamond.SHOW_ALL));
        Assert.assertEquals("java.util.Spliterator<>", typeInfo.asParameterizedType(typeContext).printForMethodFQN(typeContext, false, Diamond.YES));
        Assert.assertEquals("java.util.Spliterator", typeInfo.asParameterizedType(typeContext).printForMethodFQN(typeContext, false, Diamond.NO));
    }

    @Test
    public void testWildcard() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Collection.class);
        Assert.assertEquals("java.util.Collection<E>", typeInfo.asParameterizedType(typeContext)
                .printForMethodFQN(typeContext, false, Diamond.SHOW_ALL));
        TypeInspection typeInspection = typeContext.getTypeInspection(typeInfo);
        MethodInfo containsAll = typeInspection.methods().stream()
                .filter(m -> m.name.equals("containsAll")).findFirst().orElseThrow();
        Assert.assertEquals("java.util.Collection.containsAll(java.util.Collection<?>)", containsAll.fullyQualifiedName);
    }

    @Test
    public void testExtends1() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Collection.class);
        TypeInspection typeInspection = typeContext.getTypeInspection(typeInfo);
        MethodInfo addAll = typeInspection.methods().stream().filter(m -> m.name.equals("addAll")).findFirst().orElseThrow();
        Assert.assertEquals("java.util.Collection.addAll(java.util.Collection<? extends E>)", addAll.fullyQualifiedName);
    }

    @Test
    public void testExtends2() {
        TypeInfo typeInfo = typeContext.getFullyQualified(EnumMap.class);
        TypeInspectionImpl.Builder typeInspectionBuilder = (TypeInspectionImpl.Builder) typeContext.getTypeInspection(typeInfo);
        TypeContext newTypeContext = new TypeContext(typeContext);
        FindType findType = (fqn, path) -> newTypeContext.typeMapBuilder.getOrCreateFromPath(path, TRIGGER_BYTECODE_INSPECTION);

        String signature = "<K:Ljava/lang/Enum<TK;>;V:Ljava/lang/Object;>Ljava/util/AbstractMap<TK;TV;>;Ljava/io/Serializable;Ljava/lang/Cloneable;";
        ParseGenerics parseGenerics = new ParseGenerics(newTypeContext, typeInfo, typeInspectionBuilder,
                findType);
        int expected = "<K:Ljava/lang/Enum<TK;>;V:Ljava/lang/Object;>".length();
        int pos = parseGenerics.parseTypeGenerics(signature) + 1;
        Assert.assertEquals(expected, pos);

        TypeParameter K = typeInspectionBuilder.typeParameters().get(0);
        Assert.assertEquals(1, K.getTypeBounds().size());
        ParameterizedType typeBoundK = K.getTypeBounds().get(0);
        Assert.assertEquals(ParameterizedType.WildCard.NONE, typeBoundK.wildCard);

        Set<TypeParameter> visited = new HashSet<>();
        visited.add(K);
        Assert.assertEquals("java.lang.Enum<K>", ParameterizedTypePrinter.print(newTypeContext,
                Qualification.FULLY_QUALIFIED_NAME, typeBoundK, false, Diamond.SHOW_ALL, false, visited).toString());
        Assert.assertSame(K, typeBoundK.parameters.get(0).typeParameter);

        OutputBuilder outputBuilder = ParameterizedTypePrinter.print(typeContext, Qualification.FULLY_QUALIFIED_NAME,
                typeInfo.asParameterizedType(typeContext), false, Diamond.SHOW_ALL, false);
        System.out.println(outputBuilder.generateJavaForDebugging());
        FormattingOptions options = FormattingOptions.DEFAULT;
        org.e2immu.analyser.output.Formatter formatter = new Formatter(options);
        Assert.assertEquals("java.util.EnumMap<K extends java.lang.Enum<K>, V>\n", formatter.write(outputBuilder));
        Assert.assertEquals("java.util.EnumMap<K extends java.lang.Enum<K>,V>", outputBuilder.toString());
    }

    @Test
    public void testSuper() {
        TypeInfo sortedSet = typeContext.getFullyQualified(SortedSet.class);
        TypeInspection typeInspection = typeContext.getTypeInspection(sortedSet);
        MethodInfo comparator = typeInspection.methods().stream().filter(m -> m.name.equals("comparator")).findFirst().orElseThrow();
        MethodInspection comparatorInspection = typeContext.getMethodInspection(comparator);
        Assert.assertEquals("java.util.Comparator<? super E>", comparatorInspection.getReturnType().printForMethodFQN(typeContext, false, Diamond.SHOW_ALL));
    }

    /*
      <T:Ljava/lang/Object;T_CONS:Ljava/lang/Object;T_SPLITR::Ljava/util/Spliterator$OfPrimitive<TT;TT_CONS;TT_SPLITR;>;>Ljava/lang/Object;Ljava/util/Spliterator<TT;>;

     The double colon indicates that there is no extension for a class, but there is one for an interface (OfPrimitive is a sub-interface of Spliterator)
     */
    @Test
    public void testExtends3() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Spliterator.OfPrimitive.class);
        ParameterizedType pt = typeInfo.asParameterizedType(typeContext);
        TypeInspectionImpl.Builder typeInspectionBuilder = (TypeInspectionImpl.Builder) typeContext.getTypeInspection(typeInfo);

        TypeParameter splitter = typeInspectionBuilder.typeParameters().get(2);
        ParameterizedType typeBoundSplitter = splitter.getTypeBounds().get(0);
        Assert.assertEquals(ParameterizedType.WildCard.NONE, typeBoundSplitter.wildCard); // EXTENDS

        Assert.assertSame(splitter, typeBoundSplitter.parameters.get(2).typeParameter);

        Assert.assertEquals("java.util.Spliterator.OfPrimitive<T,T_CONS,T_SPLITR extends java.util.Spliterator.OfPrimitive<T,T_CONS,T_SPLITR>>",
                pt.printForMethodFQN(typeContext, false, Diamond.SHOW_ALL));
    }

    @Test
    public void testGenericsAbstractClassLoaderValue() {
        TypeContext newTypeContext = new TypeContext(typeContext);
        newTypeContext.addToContext(new TypeParameterImpl("V", 0));
        newTypeContext.addToContext(new TypeParameterImpl("CLV", 1));
        FindType findType = (fqn, path) -> newTypeContext.typeMapBuilder.getOrCreateFromPath(path, TRIGGER_BYTECODE_INSPECTION);
        TypeInfo typeInfo = new TypeInfo("jdk.internal.loader", "AbstractClassLoaderValue");
        TypeInspectionImpl.Builder typeInspectionBuilder = typeContext.typeMapBuilder.add(typeInfo, STARTING_BYTECODE);

        ParseGenerics parseGenerics = new ParseGenerics(newTypeContext, typeInfo, typeInspectionBuilder,
                findType);
        String signature = "<K:Ljava/lang/Object;>Ljdk/internal/loader/AbstractClassLoaderValue<Ljdk/internal/loader/AbstractClassLoaderValue<TCLV;TV;>.Sub<TK;>;TV;>;";

        int expected = "<K:Ljava/lang/Object;>".length();
        int pos = parseGenerics.parseTypeGenerics(signature) + 1;
        Assert.assertEquals(expected, pos);
    }
}
