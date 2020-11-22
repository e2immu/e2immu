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

package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.bytecode.ByteCodeInspector;
import org.e2immu.analyser.config.Configuration;
import org.e2immu.analyser.config.InputConfiguration;
import org.e2immu.analyser.config.UploadConfiguration;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.util.Resources;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.e2immu.analyser.util.Logger.LogTarget.BYTECODE_INSPECTOR;
import static org.e2immu.analyser.util.Logger.LogTarget.INSPECT;

public class TestInspectAnnotatedAPIs {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestInspectAnnotatedAPIs.class);

    @BeforeClass
    public static void beforeClass() {
        org.e2immu.analyser.util.Logger.activate(BYTECODE_INSPECTOR, INSPECT);
    }

    @Test
    public void testLoadSources() throws IOException {
        TypeContext globalTypeContext = new TypeContext(new TypeMapImpl.Builder());
        E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions = new E2ImmuAnnotationExpressions(globalTypeContext);
        URL url = new URL("file:src/main/resources/annotatedAPIs/java.util.annotated_api");
        Resources classPath = new Resources();
        ByteCodeInspector byteCodeInspector = new ByteCodeInspector(classPath, null, globalTypeContext, e2ImmuAnnotationExpressions);
        //ParseAndInspect parseAndInspect
        // FIXME update test
        AtomicInteger counter = new AtomicInteger(0);
        globalTypeContext.typeMapBuilder.visit(new String[0], (s, types) -> {
            for (TypeInfo typeInfo : types) {
                LOGGER.info("Have type {} in path {}", typeInfo.fullyQualifiedName, Arrays.toString(s));
                counter.incrementAndGet();
            }
        });
        Assert.assertTrue(counter.get() > 5);
    }

    @Test
    public void testInspectAnnotatedAPIFile() throws IOException {
        org.e2immu.analyser.util.Logger.activate(org.e2immu.analyser.util.Logger.LogTarget.BYTECODE_INSPECTOR,
                org.e2immu.analyser.util.Logger.LogTarget.INSPECT,
                org.e2immu.analyser.util.Logger.LogTarget.ANALYSER);
        Configuration configuration = new Configuration.Builder()
                .setInputConfiguration(new InputConfiguration.Builder()
                        .addClassPath("build/resources/main/annotatedAPIs")
                        .addClassPath("build/resources/main/annotations/jdkAnnotations")
                        .addClassPath("jmods/java.base.jmod").build())
                .setUploadConfiguration(new UploadConfiguration.Builder()
                        .setUpload(true).build())
                .build();
        URL url = new URL("file:src/main/resources/annotatedAPIs/java.util.annotated_api");
        Parser parser = new Parser(configuration);
        //parser.runAnnotatedAPIs(List.of(url));
        TypeInfo optional = parser.getTypeContext().typeMapBuilder.get("java.util.Optional");
        Assert.assertNotNull(optional);
        Assert.assertEquals(Level.TRUE, MultiLevel.value(optional.typeAnalysis.get().getProperty(VariableProperty.CONTAINER), 0));
        LOGGER.info("Source of Optional: " + optional.stream());
    }

}
