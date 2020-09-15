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

package org.e2immu.analyser.bytecode;

import org.e2immu.analyser.annotationxml.AnnotationXmlReader;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.TypeNature;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.TypeContext;
import org.e2immu.analyser.util.Resources;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;

public class TestByteCodeInspector {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestByteCodeInspector.class);

    @BeforeClass
    public static void beforeClass() {
        org.e2immu.analyser.util.Logger.activate(org.e2immu.analyser.util.Logger.LogTarget.BYTECODE_INSPECTOR,
                org.e2immu.analyser.util.Logger.LogTarget.BYTECODE_INSPECTOR_DEBUG);
    }

    private TypeInfo parseFromJar(String path) throws IOException {
        Resources resources = new Resources();
        resources.addJar(new URL("jar:file:build/libs/analyser.jar!/"));
        resources.addJmod(new URL("jar:file:" + System.getProperty("java.home") + "/jmods/java.base.jmod!/"));
        Resources annotationResources = new Resources();
        AnnotationXmlReader annotationParser = new AnnotationXmlReader(annotationResources);
        TypeContext typeContext = new TypeContext();
        ByteCodeInspector byteCodeInspector = new ByteCodeInspector(resources, annotationParser, typeContext,
                new E2ImmuAnnotationExpressions(typeContext.typeStore));
        List<TypeInfo> types = byteCodeInspector.inspectFromPath(path);
        return types.get(0);
    }

    private TypeInfo parseFromDirectory(String path) throws IOException {
        Resources resources = new Resources();
        resources.addDirectoryFromFileSystem(new File("build/classes/java/test"));
        resources.addJmod(new URL("jar:file:" + System.getProperty("java.home") + "/jmods/java.base.jmod!/"));
        Resources annotationResources = new Resources();
        AnnotationXmlReader annotationParser = new AnnotationXmlReader(annotationResources);
        TypeContext typeContext = new TypeContext();
        ByteCodeInspector byteCodeInspector = new ByteCodeInspector(resources, annotationParser, typeContext,
                new E2ImmuAnnotationExpressions(typeContext.typeStore));
        List<TypeInfo> types = byteCodeInspector.inspectFromPath(path);
        if (types.isEmpty()) throw new UnsupportedOperationException("Cannot find path " + path);
        return types.get(0);
    }

    @Test
    public void test() throws IOException {
        TypeInfo typeInfo = parseFromJar("org/e2immu/analyser/parser/Parser");
        Assert.assertEquals(TypeNature.CLASS, typeInfo.typeInspection.getPotentiallyRun().typeNature);
        LOGGER.info("Stream is\n{}", typeInfo.stream(0));
    }

    @Test
    public void testInterface() throws IOException {
        TypeInfo typeInfo = parseFromJar("org/e2immu/analyser/model/EvaluationContext");
        LOGGER.info("Stream is\n{}", typeInfo.stream(0));
        Assert.assertEquals(TypeNature.INTERFACE, typeInfo.typeInspection.getPotentiallyRun().typeNature);
    }

    @Test
    public void testSubTypes() throws IOException {
        TypeInfo typeInfo = parseFromDirectory("org/e2immu/analyser/testexample/SubTypes");
        Assert.assertEquals("org.e2immu.analyser.testexample.SubTypes", typeInfo.fullyQualifiedName);
        Assert.assertEquals(3, typeInfo.typeInspection.getPotentiallyRun().subTypes.size());

        TypeInfo staticSubType = typeInfo.typeInspection.getPotentiallyRun().subTypes.stream()
                .filter(st -> st.simpleName.equals("StaticSubType")).findFirst().orElseThrow();
        Assert.assertEquals("org.e2immu.analyser.testexample.SubTypes.StaticSubType",
                staticSubType.fullyQualifiedName);
        Assert.assertEquals(1, staticSubType.typeInspection.getPotentiallyRun().subTypes.size());

        TypeInfo subSubType = staticSubType.typeInspection.getPotentiallyRun().subTypes.stream()
                .filter(st -> st.simpleName.equals("SubTypeOfStaticSubType")).findFirst().orElseThrow();
        Assert.assertEquals("org.e2immu.analyser.testexample.SubTypes.StaticSubType.SubTypeOfStaticSubType",
                subSubType.fullyQualifiedName);
        Assert.assertEquals(0, subSubType.typeInspection.getPotentiallyRun().subTypes.size());
    }

    @Test
    public void testGenerics() throws IOException {
        TypeInfo type = parseFromJar("org/e2immu/analyser/util/Lazy.class");
        Assert.assertEquals(TypeNature.CLASS, type.typeInspection.getPotentiallyRun().typeNature);
        LOGGER.info("Stream is\n{}", type.stream(0));
    }

    @Test
    public void testGenerics2() throws IOException {
        TypeInfo typeInfo = parseFromJar("org/e2immu/analyser/util/Either");
        Assert.assertEquals(TypeNature.CLASS, typeInfo.typeInspection.getPotentiallyRun().typeNature);
        LOGGER.info("Stream is\n{}", typeInfo.stream(0));
    }

    @Test
    public void testStringArray() throws IOException {
        TypeInfo typeInfo = parseFromJar("org/e2immu/analyser/model/PackagePrefix");
        Assert.assertEquals(TypeNature.CLASS, typeInfo.typeInspection.getPotentiallyRun().typeNature);
        LOGGER.info("Stream is\n{}", typeInfo.stream(0));
    }

    @Test
    public void testSetOnce() throws IOException {
        TypeInfo type = parseFromJar("org/e2immu/analyser/util/SetOnce.class");
        Assert.assertEquals(TypeNature.CLASS, type.typeInspection.getPotentiallyRun().typeNature);
        LOGGER.info("Stream is\n{}", type.stream(0));
    }

    @Test
    public void testEnum() throws IOException {
        TypeInfo type = parseFromJar("org/e2immu/analyser/model/SideEffect.class");
        Assert.assertEquals(TypeNature.ENUM, type.typeInspection.getPotentiallyRun().typeNature);
        LOGGER.info("Stream is\n{}", type.stream(0));
    }

    @Test
    public void testImplements() throws IOException {
        TypeInfo type = parseFromJar("org/e2immu/analyser/analyser/NumberedStatement.class");
        Assert.assertEquals(TypeNature.CLASS, type.typeInspection.getPotentiallyRun().typeNature);
        LOGGER.info("Stream is\n{}", type.stream(0));
    }
}
