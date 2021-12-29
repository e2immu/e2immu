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

package org.e2immu.analyser.bytecode;

import org.e2immu.analyser.annotationxml.AnnotationXmlReader;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.TypeModifier;
import org.e2immu.analyser.model.TypeNature;
import org.e2immu.analyser.parser.Input;
import org.e2immu.analyser.parser.TypeMap;
import org.e2immu.analyser.parser.TypeMapImpl;
import org.e2immu.analyser.util.Resources;
import org.e2immu.analyser.util.SMapList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TestByteCodeInspector {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestByteCodeInspector.class);

    @BeforeAll
    public static void beforeClass() {
        org.e2immu.analyser.util.Logger.activate(org.e2immu.analyser.util.Logger.LogTarget.BYTECODE_INSPECTOR,
                org.e2immu.analyser.util.Logger.LogTarget.BYTECODE_INSPECTOR_DEBUG);
    }

    private TypeMap parseFromJar(String path) throws IOException {
        String analyserJar = determineAnalyserJarName();
        Resources resources = new Resources();
        resources.addJar(new URL("jar:file:build/libs/" + analyserJar + "!/"));
        resources.addJmod(new URL("jar:file:" + System.getProperty("java.home") + "/jmods/java.base.jmod!/"));
        Resources annotationResources = new Resources();
        AnnotationXmlReader annotationParser = new AnnotationXmlReader(annotationResources);
        TypeContext typeContext = new TypeContext(new TypeMapImpl.Builder(resources));
        ByteCodeInspector byteCodeInspector = new ByteCodeInspector(resources, annotationParser, typeContext);
        typeContext.typeMapBuilder.setByteCodeInspector(byteCodeInspector);
        typeContext.loadPrimitives();
        Input.preload(typeContext, byteCodeInspector, resources, "java.lang");

        List<TypeInfo> types = byteCodeInspector.inspectFromPath(path);
        // in case the path is a subType, we need to inspect it explicitly
        types.forEach(typeContext.typeMapBuilder::getTypeInspection);
        return typeContext.typeMapBuilder.build();
    }

    public static String determineAnalyserJarName() {
        File libs = new File("./build/libs");
        assertTrue(libs.isDirectory());
        File[] analysers = libs.listFiles(file -> file.canRead() && file.getName().endsWith(".jar") && file.getName().startsWith("analyser-"));
        assertNotNull(analysers);
        Arrays.sort(analysers);
        assertTrue(analysers.length > 0);
        return analysers[analysers.length - 1].getName();
    }

    @Test
    public void test() throws IOException {
        TypeMap typeMap = parseFromJar("org/e2immu/analyser/parser/Parser");
        TypeInfo parser = typeMap.get("org.e2immu.analyser.parser.Parser");
        assertEquals(TypeNature.CLASS, parser.typeInspection.get().typeNature());
        LOGGER.info("Stream is\n{}", parser.output());
    }

    @Test
    public void testSubTypeParser() throws IOException {
        TypeMap typeMap = parseFromJar("org/e2immu/analyser/parser/Parser$InspectWithJavaParserImpl");
        TypeInfo subType = typeMap.get("org.e2immu.analyser.parser.Parser.InspectWithJavaParserImpl");

        assertEquals(TypeNature.CLASS, subType.typeInspection.get().typeNature());
        assertFalse(subType.typeInspection.get().isStatic());
        assertTrue(subType.typeInspection.get().modifiers().contains(TypeModifier.PRIVATE));
    }

    @Test
    public void testInterface() throws IOException {
        TypeMap typeMap = parseFromJar("org/e2immu/analyser/analyser/EvaluationContext");
        TypeInfo typeInfo = typeMap.get("org.e2immu.analyser.analyser.EvaluationContext");

        LOGGER.info("Stream is\n{}", typeInfo.output());
        assertEquals(TypeNature.INTERFACE, typeInfo.typeInspection.get().typeNature());
    }

    @Test
    public void testGenerics() throws IOException {
        TypeMap typeMap = parseFromJar("org/e2immu/analyser/util/SMapList.class");
        TypeInfo typeInfo = typeMap.get(SMapList.class);

        assertEquals(TypeNature.CLASS, typeInfo.typeInspection.get().typeNature());
        LOGGER.info("Stream is\n{}", typeInfo.output());
    }

    @Test
    public void testStringArray() throws IOException {
        TypeMap typeMap = parseFromJar("org/e2immu/analyser/model/PackagePrefix");
        TypeInfo typeInfo = typeMap.get("org.e2immu.analyser.model.PackagePrefix");

        assertEquals(TypeNature.CLASS, typeInfo.typeInspection.get().typeNature());
        LOGGER.info("Stream is\n{}", typeInfo.output());
    }

    @Test
    public void testEnum() throws IOException {
        TypeMap typeMap = parseFromJar("org/e2immu/analyser/model/Diamond.class");
        TypeInfo typeInfo = typeMap.get("org.e2immu.analyser.model.Diamond");

        assertEquals(TypeNature.ENUM, typeInfo.typeInspection.get().typeNature());
        LOGGER.info("Stream is\n{}", typeInfo.output());
    }

    @Test
    public void testDefaultMethod() throws IOException {
        TypeMap typeMap = parseFromJar("org/e2immu/analyser/output/OutputElement.class");
        TypeInfo typeInfo = typeMap.get("org.e2immu.analyser.output.OutputElement");

        assertEquals(TypeNature.INTERFACE, typeInfo.typeInspection.get().typeNature());
        LOGGER.info("Stream is\n{}", typeInfo.output());

        MethodInfo forDebug = typeInfo.findUniqueMethod("debug", 0);
        assertTrue(forDebug.methodInspection.get().isDefault());
    }
}
