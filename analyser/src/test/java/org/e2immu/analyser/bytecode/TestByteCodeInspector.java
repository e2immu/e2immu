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
import org.e2immu.analyser.parser.TypeMap;
import org.e2immu.analyser.parser.TypeMapImpl;
import org.e2immu.analyser.util.Resources;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.List;

public class TestByteCodeInspector {
    public static final String VERSION = "0.0.1"; // TODO determine dynamically
    private static final Logger LOGGER = LoggerFactory.getLogger(TestByteCodeInspector.class);

    @BeforeClass
    public static void beforeClass() {
        org.e2immu.analyser.util.Logger.activate(org.e2immu.analyser.util.Logger.LogTarget.BYTECODE_INSPECTOR,
                org.e2immu.analyser.util.Logger.LogTarget.BYTECODE_INSPECTOR_DEBUG);
    }

    private TypeMap parseFromJar(String path) throws IOException {
        Resources resources = new Resources();
        resources.addJar(new URL("jar:file:build/libs/analyser-" + VERSION + ".jar!/"));
        resources.addJmod(new URL("jar:file:" + System.getProperty("java.home") + "/jmods/java.base.jmod!/"));
        Resources annotationResources = new Resources();
        AnnotationXmlReader annotationParser = new AnnotationXmlReader(annotationResources);
        TypeContext typeContext = new TypeContext(new TypeMapImpl.Builder());
        ByteCodeInspector byteCodeInspector = new ByteCodeInspector(resources, annotationParser, typeContext);
        typeContext.typeMapBuilder.setByteCodeInspector(byteCodeInspector);
        typeContext.loadPrimitives();

        List<TypeInfo> types = byteCodeInspector.inspectFromPath(path);
        // in case the path is a subType, we need to inspect it explicitly
        types.forEach(typeContext.typeMapBuilder::getTypeInspection);
        return typeContext.typeMapBuilder.build();
    }

    @Test
    public void test() throws IOException {
        TypeMap typeMap = parseFromJar("org/e2immu/analyser/parser/Parser");
        TypeInfo parser = typeMap.get("org.e2immu.analyser.parser.Parser");
        Assert.assertEquals(TypeNature.CLASS, parser.typeInspection.get().typeNature());
        LOGGER.info("Stream is\n{}", parser.stream(0));
    }

    @Test
    public void testSubTypeParser() throws IOException {
        TypeMap typeMap = parseFromJar("org/e2immu/analyser/parser/Parser$InspectWithJavaParserImpl");
        TypeInfo subType = typeMap.get("org.e2immu.analyser.parser.Parser.InspectWithJavaParserImpl");

        Assert.assertEquals(TypeNature.CLASS, subType.typeInspection.get().typeNature());
        LOGGER.info("Stream is\n{}", subType.stream(0));
    }

    @Test
    public void testInterface() throws IOException {
        TypeMap typeMap = parseFromJar("org/e2immu/analyser/model/EvaluationContext");
        TypeInfo typeInfo = typeMap.get("org.e2immu.analyser.model.EvaluationContext");

        LOGGER.info("Stream is\n{}", typeInfo.stream(0));
        Assert.assertEquals(TypeNature.INTERFACE, typeInfo.typeInspection.get().typeNature());
    }

    @Test
    public void testGenerics() throws IOException {
        TypeMap typeMap = parseFromJar("org/e2immu/analyser/util/Lazy.class");
        TypeInfo typeInfo = typeMap.get("org.e2immu.analyser.util.Lazy");

        Assert.assertEquals(TypeNature.CLASS, typeInfo.typeInspection.get().typeNature());
        LOGGER.info("Stream is\n{}", typeInfo.stream(0));
    }

    @Test
    public void testGenerics2() throws IOException {
        TypeMap typeMap = parseFromJar("org/e2immu/analyser/util/Either");
        TypeInfo typeInfo = typeMap.get("org.e2immu.analyser.util.Either");
        Assert.assertEquals(TypeNature.CLASS, typeInfo.typeInspection.get().typeNature());
        LOGGER.info("Stream is\n{}", typeInfo.stream(0));
    }

    @Test
    public void testStringArray() throws IOException {
        TypeMap typeMap = parseFromJar("org/e2immu/analyser/model/PackagePrefix");
        TypeInfo typeInfo = typeMap.get("org.e2immu.analyser.model.PackagePrefix");

        Assert.assertEquals(TypeNature.CLASS, typeInfo.typeInspection.get().typeNature());
        LOGGER.info("Stream is\n{}", typeInfo.stream(0));
    }

    @Test
    public void testSetOnce() throws IOException {
        TypeMap typeMap = parseFromJar("org/e2immu/analyser/util/SetOnce.class");
        TypeInfo typeInfo = typeMap.get("org.e2immu.analyser.util.SetOnce");

        Assert.assertEquals(TypeNature.CLASS, typeInfo.typeInspection.get().typeNature());
        LOGGER.info("Stream is\n{}", typeInfo.stream(0));
    }

    @Test
    public void testEnum() throws IOException {
        TypeMap typeMap = parseFromJar("org/e2immu/analyser/model/SideEffect.class");
        TypeInfo typeInfo = typeMap.get("org.e2immu.analyser.model.SideEffect");

        Assert.assertEquals(TypeNature.ENUM, typeInfo.typeInspection.get().typeNature());
        LOGGER.info("Stream is\n{}", typeInfo.stream(0));
    }

    @Test
    public void testImplements() throws IOException {
        TypeMap typeMap = parseFromJar("org/e2immu/analyser/analyser/StatementAnalyser.class");
        TypeInfo typeInfo = typeMap.get("org.e2immu.analyser.analyser.StatementAnalyser");

        Assert.assertEquals(TypeNature.CLASS, typeInfo.typeInspection.get().typeNature());
        LOGGER.info("Stream is\n{}", typeInfo.stream(0));
    }
}
