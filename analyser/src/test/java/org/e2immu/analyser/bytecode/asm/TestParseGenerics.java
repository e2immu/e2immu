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

package org.e2immu.analyser.bytecode.asm;

import org.e2immu.analyser.annotationxml.AnnotationXmlReader;
import org.e2immu.analyser.config.Configuration;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.inspector.impl.TypeInspectionImpl;
import org.e2immu.analyser.log.LogTarget;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.impl.TypeParameterImpl;
import org.e2immu.analyser.parser.Input;
import org.e2immu.analyser.parser.impl.TypeMapImpl;
import org.e2immu.analyser.util.Resources;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;

import static org.e2immu.analyser.inspector.InspectionState.STARTING_BYTECODE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class TestParseGenerics {

    public static final String SOME_JAR = "jar:file:build/libs/my.jar";
    private static TypeContext typeContext;
    private static Resources classPath;
    private static ByteCodeInspectorImpl byteCodeInspector;

    @BeforeAll
    public static void beforeClass() throws IOException {
        Configuration configuration = new Configuration.Builder()
                .addDebugLogTargets(LogTarget.BYTECODE, LogTarget.PARSER)
                .build();
        configuration.initializeLoggers();

        classPath = new Resources();
        classPath.addJmod(new URL("jar:file:" + System.getProperty("java.home") + "/jmods/java.base.jmod!/"));
        Resources annotationResources = new Resources();
        AnnotationXmlReader annotationParser = new AnnotationXmlReader(annotationResources);
        typeContext = new TypeContext(new TypeMapImpl.Builder(classPath));
        byteCodeInspector = new ByteCodeInspectorImpl(classPath, annotationParser, typeContext);
        typeContext.typeMap.setByteCodeInspector(byteCodeInspector);
        typeContext.loadPrimitives();
        Input.preload(typeContext, byteCodeInspector, classPath, "java.util");
    }

    @Test
    public void testNormalTypeParameter() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Spliterator.class);
        assertEquals("java.util.Spliterator<T>", typeInfo.asParameterizedType(typeContext)
                .printForMethodFQN(typeContext, false, Diamond.SHOW_ALL));
        assertEquals("java.util.Spliterator<>", typeInfo.asParameterizedType(typeContext).
                printForMethodFQN(typeContext, false, Diamond.YES));
        assertEquals("java.util.Spliterator", typeInfo.asParameterizedType(typeContext)
                .printForMethodFQN(typeContext, false, Diamond.NO));
    }

    @Test
    public void testWildcard() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Collection.class);
        assertEquals("java.util.Collection<E>", typeInfo.asParameterizedType(typeContext)
                .printForMethodFQN(typeContext, false, Diamond.SHOW_ALL));
        TypeInspection typeInspection = typeContext.getTypeInspection(typeInfo);
        MethodInfo containsAll = typeInspection.methods().stream()
                .filter(m -> m.name.equals("containsAll")).findFirst().orElseThrow();
        assertEquals("java.util.Collection.containsAll(java.util.Collection<?>)", containsAll.fullyQualifiedName);
    }

    @Test
    public void testExtends1() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Collection.class);
        TypeInspection typeInspection = typeContext.getTypeInspection(typeInfo);
        MethodInfo addAll = typeInspection.methods().stream().filter(m -> m.name.equals("addAll")).findFirst().orElseThrow();
        assertEquals("java.util.Collection.addAll(java.util.Collection<? extends E>)", addAll.fullyQualifiedName);
    }

    @Test
    public void testExtends2() throws URISyntaxException {
        TypeInfo typeInfo = typeContext.getFullyQualified(EnumMap.class);
        TypeInspectionImpl.Builder typeInspectionBuilder = (TypeInspectionImpl.Builder)
                typeContext.getTypeInspection(typeInfo);
        TypeContext newTypeContext = new TypeContext(typeContext);


        String signature = "<K:Ljava/lang/Enum<TK;>;V:Ljava/lang/Object;>Ljava/util/AbstractMap<TK;TV;>;Ljava/io/Serializable;Ljava/lang/Cloneable;";
        ParseGenerics parseGenerics = new ParseGenerics(newTypeContext, typeInfo, typeInspectionBuilder,
                byteCodeInspector.localTypeMap(), true);
        int expected = "<K:Ljava/lang/Enum<TK;>;V:Ljava/lang/Object;>".length();
        int pos = parseGenerics.parseTypeGenerics(signature) + 1;
        assertEquals(expected, pos);

        TypeParameter K = typeInspectionBuilder.typeParameters().get(0);
        assertEquals(1, K.getTypeBounds().size());
        ParameterizedType typeBoundK = K.getTypeBounds().get(0);
        assertEquals(ParameterizedType.WildCard.NONE, typeBoundK.wildCard);

        Set<TypeParameter> visited = new HashSet<>();
        visited.add(K);
        assertEquals("Enum<K>", ParameterizedTypePrinter.print(newTypeContext,
                Qualification.FULLY_QUALIFIED_NAME, typeBoundK, false, Diamond.SHOW_ALL,
                false, visited).toString());
        assertSame(K, typeBoundK.parameters.get(0).typeParameter);

        ParameterizedType pt = typeInfo.asParameterizedType(typeContext);
        assertEquals("java.util.EnumMap<K extends Enum<K>,V>",
                pt.printForMethodFQN(typeContext, false, Diamond.SHOW_ALL));
    }

    @Test
    public void testSuper() {
        TypeInfo sortedSet = typeContext.getFullyQualified(SortedSet.class);
        TypeInspection typeInspection = typeContext.getTypeInspection(sortedSet);
        MethodInfo comparator = typeInspection.methods().stream().filter(m -> m.name.equals("comparator"))
                .findFirst().orElseThrow();
        MethodInspection comparatorInspection = typeContext.getMethodInspection(comparator);
        assertEquals("java.util.Comparator<? super E>",
                comparatorInspection.getReturnType().printForMethodFQN(typeContext, false, Diamond.SHOW_ALL));
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
        assertEquals(ParameterizedType.WildCard.NONE, typeBoundSplitter.wildCard); // EXTENDS

        assertSame(splitter, typeBoundSplitter.parameters.get(2).typeParameter);

        assertEquals("java.util.Spliterator.OfPrimitive<T,T_CONS,T_SPLITR extends java.util.Spliterator.OfPrimitive<T,T_CONS,T_SPLITR>>",
                pt.printForMethodFQN(typeContext, false, Diamond.SHOW_ALL));
    }

    @Test
    public void testGenericsAbstractClassLoaderValue() throws URISyntaxException {
        TypeContext newTypeContext = new TypeContext(typeContext);
        newTypeContext.addToContext(new TypeParameterImpl("V", 0).noTypeBounds());
        newTypeContext.addToContext(new TypeParameterImpl("CLV", 1).noTypeBounds());
        ByteCodeInspectorImpl byteCodeInspector = new ByteCodeInspectorImpl(classPath, null, newTypeContext);
        TypeInfo typeInfo = new TypeInfo("jdk.internal.loader", "AbstractClassLoaderValue");
        TypeInspection.Builder typeInspectionBuilder = typeContext.typeMap.add(typeInfo, STARTING_BYTECODE);

        ParseGenerics parseGenerics = new ParseGenerics(newTypeContext, typeInfo, typeInspectionBuilder,
                byteCodeInspector.localTypeMap(), true);
        String signature = "<K:Ljava/lang/Object;>Ljdk/internal/loader/AbstractClassLoaderValue<Ljdk/internal/loader/AbstractClassLoaderValue<TCLV;TV;>.Sub<TK;>;TV;>;";

        int expected = "<K:Ljava/lang/Object;>".length();
        int pos = parseGenerics.parseTypeGenerics(signature) + 1;
        assertEquals(expected, pos);
    }
}
