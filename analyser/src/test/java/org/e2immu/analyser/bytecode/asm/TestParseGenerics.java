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
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.TypeParameterImpl;
import org.e2immu.analyser.parser.TypeMapImpl;
import org.e2immu.analyser.util.Logger;
import org.e2immu.analyser.util.Resources;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.net.URL;

import static org.e2immu.analyser.bytecode.TestByteCodeInspector.VERSION;
import static org.e2immu.analyser.inspector.TypeInspectionImpl.InspectionState.STARTING_BYTECODE;
import static org.e2immu.analyser.inspector.TypeInspectionImpl.InspectionState.TRIGGER_BYTECODE_INSPECTION;

public class TestParseGenerics {

    private static TypeContext typeContext;

    @BeforeClass
    public static void beforeClass() throws IOException {
        Logger.activate(Logger.LogTarget.BYTECODE_INSPECTOR);

        Resources resources = new Resources();
        resources.addJar(new URL("jar:file:build/libs/analyser-" + VERSION + ".jar!/"));
        resources.addJmod(new URL("jar:file:" + System.getProperty("java.home") + "/jmods/java.base.jmod!/"));
        Resources annotationResources = new Resources();
        AnnotationXmlReader annotationParser = new AnnotationXmlReader(annotationResources);
        typeContext = new TypeContext(new TypeMapImpl.Builder());
        ByteCodeInspector byteCodeInspector = new ByteCodeInspector(resources, annotationParser, typeContext);
        typeContext.typeMapBuilder.setByteCodeInspector(byteCodeInspector);
        typeContext.loadPrimitives();
    }

    @Test
    public void testGenericsAbstractClassLoaderValue() {
        TypeContext newTypeContext = new TypeContext(typeContext);
        newTypeContext.addToContext(new TypeParameterImpl("V", 0));
        newTypeContext.addToContext(new TypeParameterImpl("CLV", 1));
        FindType findType = (fqn, path) -> newTypeContext.typeMapBuilder.getOrCreate(fqn, TRIGGER_BYTECODE_INSPECTION);
        TypeInfo typeInfo = TypeInfo.fromFqn("jdk.internal.loader.AbstractClassLoaderValue");
        TypeInspectionImpl.Builder typeInspectionBuilder = new TypeInspectionImpl.Builder(typeInfo, STARTING_BYTECODE);
        ParseGenerics parseGenerics = new ParseGenerics(newTypeContext, typeInfo, typeInspectionBuilder,
                findType);
        String signature = "<K:Ljava/lang/Object;>Ljdk/internal/loader/AbstractClassLoaderValue<Ljdk/internal/loader/AbstractClassLoaderValue<TCLV;TV;>.Sub<TK;>;TV;>;";

        int expected = "<K:Ljava/lang/Object;>".length();
        int pos = parseGenerics.parseTypeGenerics(signature) + 1;
        Assert.assertEquals(expected, pos);

        ParameterizedTypeFactory.Result result =
                ParameterizedTypeFactory.from(newTypeContext, findType, signature.substring(expected));
        Assert.assertEquals("Type jdk.internal.loader.AbstractClassLoaderValue<jdk.internal.loader.AbstractClassLoaderValue<CLV, V>.Sub<K>, V>", result.parameterizedType.toString());
    }
}
