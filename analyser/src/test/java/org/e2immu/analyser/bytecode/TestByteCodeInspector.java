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

import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.TypeNature;
import org.apache.commons.io.FileUtils;
import org.e2immu.analyser.parser.TypeContext;
import org.e2immu.analyser.util.Resources;
import org.e2immu.analyser.annotationxml.AnnotationXmlReader;
import org.e2immu.analyser.annotationxml.model.TypeItem;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.HashSet;
import java.util.List;
import java.util.Stack;

public class TestByteCodeInspector {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestByteCodeInspector.class);

    public static final String BUILD_CLASSES_JAVA_MAIN = "build/classes/java/main";
    public static final String BUILD_CLASSES_JAVA_TEST = "build/classes/java/test";

    @BeforeClass
    public static void beforeClass() {
        org.e2immu.analyser.util.Logger.activate(org.e2immu.analyser.util.Logger.LogTarget.BYTECODE_INSPECTOR, org.e2immu.analyser.util.Logger.LogTarget.BYTECODE_INSPECTOR_DEBUG);
    }

    private TypeInfo parse(String path, String where) throws IOException {
        Resources resources = new Resources();
        resources.addDirectoryFromFileSystem(new File(where));
        Resources annotationResources = new Resources();
        AnnotationXmlReader annotationParser = new AnnotationXmlReader(annotationResources);
        ByteCodeInspector byteCodeInspector = new ByteCodeInspector(resources, annotationParser, new TypeContext());
        List<TypeInfo> types = byteCodeInspector.inspectFromPath(path);
        return types.get(0);
    }

    @Test
    public void testConfigRetriever() throws IOException {
        Resources resources = new Resources();
        resources.addJar(new URL("jar:file:/Users/bnaudts/Downloads/vertx/lib/vertx-config-3.8.5.jar!/"));
        Resources annotationResources = new Resources();
        AnnotationXmlReader annotationParser = new AnnotationXmlReader(annotationResources);
        ByteCodeInspector byteCodeInspector = new ByteCodeInspector(resources, annotationParser, new TypeContext());
        List<TypeInfo> types = byteCodeInspector.inspectFromPath("io/vertx/config/ConfigRetriever");
        TypeInfo typeInfo = types.get(0);
        Assert.assertEquals(TypeNature.INTERFACE, typeInfo.typeInspection.get().typeNature);
        MethodInfo getConfig = typeInfo.typeInspection.get().methods.stream().filter(m -> "getConfig".equals(m.name)).findAny().orElseThrow();
        Assert.assertEquals(1, getConfig.methodInspection.get().parameters.size());
        LOGGER.info("Stream is\n{}", typeInfo.stream(0));
    }

    @Test
    public void test() throws IOException {
        TypeInfo typeInfo = parse("org/e2immu/analyser/parser/Parser", BUILD_CLASSES_JAVA_MAIN);
        Assert.assertEquals(TypeNature.CLASS, typeInfo.typeInspection.get().typeNature);
        LOGGER.info("Stream is\n{}", typeInfo.stream(0));
    }

    @Test
    public void testInterface() throws IOException {
        TypeInfo typeInfo = parse("org/e2immu/analyser/model/EvaluationContext", BUILD_CLASSES_JAVA_MAIN);
        LOGGER.info("Stream is\n{}", typeInfo.stream(0));
        Assert.assertEquals(TypeNature.INTERFACE, typeInfo.typeInspection.get().typeNature);
    }

    @Test
    public void testSubTypes() throws IOException {
        TypeInfo typeInfo = parse("org/e2immu/analyser/testexample/SubTypes", BUILD_CLASSES_JAVA_TEST);
        Assert.assertEquals("org.e2immu.analyser.testexample.SubTypes", typeInfo.fullyQualifiedName);
        Assert.assertEquals(2, typeInfo.typeInspection.get().subTypes.size());

        TypeInfo staticSubType = typeInfo.typeInspection.get().subTypes.stream()
                .filter(st -> st.simpleName.equals("StaticSubType")).findFirst().orElseThrow();
        Assert.assertEquals("org.e2immu.analyser.testexample.SubTypes.StaticSubType",
                staticSubType.fullyQualifiedName);
        Assert.assertEquals(1, staticSubType.typeInspection.get().subTypes.size());

        TypeInfo subSubType = staticSubType.typeInspection.get().subTypes.stream()
                .filter(st -> st.simpleName.equals("SubTypeOfStaticSubType")).findFirst().orElseThrow();
        Assert.assertEquals("org.e2immu.analyser.testexample.SubTypes.StaticSubType.SubTypeOfStaticSubType",
                subSubType.fullyQualifiedName);
        Assert.assertEquals(0, subSubType.typeInspection.get().subTypes.size());
    }

    @Test
    public void testGenerics() throws IOException {
        File base = new File(BUILD_CLASSES_JAVA_MAIN);
        File path = new File("org/e2immu/analyser/util/Lazy.class");
        byte[] bytes = FileUtils.readFileToByteArray(new File(base, path.getPath()));
        ByteCodeInspector byteCodeInspector = new ByteCodeInspector();
        List<TypeInfo> types = byteCodeInspector.inspectByteArray(bytes, new HashSet<>(), new Stack<>(), new TypeContext());
        TypeInfo type = types.get(0);
        Assert.assertEquals(TypeNature.CLASS, type.typeInspection.get().typeNature);
        LOGGER.info("Stream is\n{}", types.get(0).stream(0));
    }

    @Test
    public void testGenerics2() throws IOException {
        TypeInfo typeInfo = parse("org/e2immu/analyser/util/Either", BUILD_CLASSES_JAVA_MAIN);
        Assert.assertEquals(TypeNature.CLASS, typeInfo.typeInspection.get().typeNature);
        LOGGER.info("Stream is\n{}", typeInfo.stream(0));
    }

    @Test
    public void testStringArray() throws IOException {
        TypeInfo typeInfo = parse("org/e2immu/analyser/model/PackagePrefix", BUILD_CLASSES_JAVA_MAIN);
        Assert.assertEquals(TypeNature.CLASS, typeInfo.typeInspection.get().typeNature);
        LOGGER.info("Stream is\n{}", typeInfo.stream(0));
    }

    @Test
    public void testSetOnce() throws IOException {
        File base = new File(BUILD_CLASSES_JAVA_MAIN);
        File path = new File("org/e2immu/analyser/util/SetOnce.class");
        byte[] bytes = FileUtils.readFileToByteArray(new File(base, path.getPath()));
        ByteCodeInspector byteCodeInspector = new ByteCodeInspector();
        List<TypeInfo> types = byteCodeInspector.inspectByteArray(bytes, new HashSet<>(), new Stack<>(), new TypeContext());
        TypeInfo type = types.get(0);
        Assert.assertEquals(TypeNature.CLASS, type.typeInspection.get().typeNature);
        LOGGER.info("Stream is\n{}", types.get(0).stream(0));
    }


    @Test
    public void testEnum() throws IOException {
        File base = new File(BUILD_CLASSES_JAVA_MAIN);
        File path = new File("org/e2immu/analyser/model/SideEffect.class");
        byte[] bytes = FileUtils.readFileToByteArray(new File(base, path.getPath()));
        ByteCodeInspector byteCodeInspector = new ByteCodeInspector();
        List<TypeInfo> types = byteCodeInspector.inspectByteArray(bytes, new HashSet<>(), new Stack<>(), new TypeContext());
        TypeInfo type = types.get(0);
        Assert.assertEquals(TypeNature.ENUM, type.typeInspection.get().typeNature);
        LOGGER.info("Stream is\n{}", types.get(0).stream(0));
    }

    @Test
    public void testImplements() throws IOException {
        File base = new File(BUILD_CLASSES_JAVA_MAIN);
        File path = new File("org/e2immu/analyser/analyser/NumberedStatement.class");
        byte[] bytes = FileUtils.readFileToByteArray(new File(base, path.getPath()));
        ByteCodeInspector byteCodeInspector = new ByteCodeInspector();
        List<TypeInfo> types = byteCodeInspector.inspectByteArray(bytes, new HashSet<>(), new Stack<>(), new TypeContext());
        TypeInfo type = types.get(0);
        Assert.assertEquals(TypeNature.CLASS, type.typeInspection.get().typeNature);
        LOGGER.info("Stream is\n{}", types.get(0).stream(0));
    }
}
