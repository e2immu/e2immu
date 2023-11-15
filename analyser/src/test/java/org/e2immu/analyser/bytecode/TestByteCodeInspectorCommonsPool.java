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

import org.apache.commons.pool.impl.GenericKeyedObjectPool;
import org.apache.commons.pool.impl.GenericObjectPool;
import org.e2immu.analyser.annotationxml.AnnotationXmlReader;
import org.e2immu.analyser.bytecode.asm.ByteCodeInspectorImpl;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.Input;
import org.e2immu.analyser.parser.TypeMap;
import org.e2immu.analyser.parser.impl.TypeMapImpl;
import org.e2immu.analyser.util.Resources;
import org.e2immu.analyser.util.Source;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TestByteCodeInspectorCommonsPool {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestByteCodeInspectorCommonsPool.class);

    private TypeMap parseFromJar(String path, Class<?>... classes) throws IOException, URISyntaxException {
        Resources resources = new Resources();
        int entries = resources.addJarFromClassPath("org/apache/commons/pool");
        assertTrue(entries > 0);
        resources.addJmod(new URL("jar:file:" + System.getProperty("java.home") + "/jmods/java.base.jmod!/"));
        Resources annotationResources = new Resources();
        AnnotationXmlReader annotationParser = new AnnotationXmlReader(annotationResources);
        TypeContext typeContext = new TypeContext(new TypeMapImpl.Builder(resources));
        ByteCodeInspectorImpl byteCodeInspector = new ByteCodeInspectorImpl(resources, annotationParser, typeContext);
        typeContext.typeMap.setByteCodeInspector(byteCodeInspector);
        typeContext.loadPrimitives();
        Input.preload(typeContext, byteCodeInspector, resources, "java.lang");
        String pathWithClass = Source.ensureDotClass(path);
        // we'll take some random jar
        String analyserJar = TestByteCodeInspector.determineAnalyserJarName();
        URL jarUrl = new URL("jar:file:build/libs/" + analyserJar + "!/");

        List<TypeData> data = byteCodeInspector.inspectFromPath(new Source(pathWithClass, jarUrl.toURI()));
        // in case the path is a subType, we need to inspect it explicitly
        typeContext.typeMap.copyIntoTypeMap(data.get(0).getTypeInspectionBuilder().typeInfo(), data);
        return typeContext.typeMap.build();
    }

    @Test
    public void test1() throws IOException, URISyntaxException {
        TypeMap typeMap = parseFromJar("org/apache/commons/pool/impl/GenericKeyedObjectPool$ObjectTimestampPair");
        TypeInfo pool = typeMap.get(GenericKeyedObjectPool.class);
        TypeInspection poolInspection = pool.typeInspection.get();
        assertEquals(TypeNature.CLASS, poolInspection.typeNature());
        TypeInfo subType = poolInspection.subTypes().stream()
                .filter(st -> "ObjectTimestampPair".equals(st.simpleName)).findFirst().orElseThrow();
        TypeInspection subInspection = subType.typeInspection.get();
        assertEquals(1, subInspection.typeParameters().size());
        assertEquals("T", subInspection.typeParameters().get(0).getName());
    }


    @Test
    public void test2() throws IOException, URISyntaxException {
        TypeMap typeMap = parseFromJar("org/apache/commons/pool/impl/GenericObjectPool$Latch");
        TypeInfo parser = typeMap.get(GenericObjectPool.class);
        assertEquals(TypeNature.CLASS, parser.typeInspection.get().typeNature());
        //LOGGER.info("Stream is\n{}", parser.output());
    }

}
