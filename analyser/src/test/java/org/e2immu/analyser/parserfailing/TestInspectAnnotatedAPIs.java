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

package org.e2immu.analyser.parserfailing;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.bytecode.ByteCodeInspector;
import org.e2immu.analyser.config.Configuration;
import org.e2immu.analyser.config.InputConfiguration;
import org.e2immu.analyser.config.UploadConfiguration;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.parser.Parser;
import org.e2immu.analyser.parser.TypeMapImpl;
import org.e2immu.analyser.util.Resources;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import static org.e2immu.analyser.util.Logger.LogTarget.BYTECODE_INSPECTOR;
import static org.e2immu.analyser.util.Logger.LogTarget.INSPECTOR;
import static org.junit.jupiter.api.Assertions.*;

public class TestInspectAnnotatedAPIs {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestInspectAnnotatedAPIs.class);

    @BeforeAll
    public static void beforeClass() {
        org.e2immu.analyser.util.Logger.activate(BYTECODE_INSPECTOR, INSPECTOR);
    }

    @Test
    public void testLoadSources() throws IOException {
        Resources classPath = new Resources();
        TypeContext globalTypeContext = new TypeContext(new TypeMapImpl.Builder(classPath));
        URL url = new URL("file:src/main/resources/annotatedAPIs/java.util.annotated_api");
        ByteCodeInspector byteCodeInspector = new ByteCodeInspector(classPath, null, globalTypeContext);
        //ParseAndInspect parseAndInspect
        // FIXME update test
        AtomicInteger counter = new AtomicInteger(0);
        globalTypeContext.typeMapBuilder.visit(new String[0], (s, types) -> {
            for (TypeInfo typeInfo : types) {
                LOGGER.info("Have type {} in path {}", typeInfo.fullyQualifiedName, Arrays.toString(s));
                counter.incrementAndGet();
            }
        });
        assertTrue(counter.get() > 5);
    }

    @Test
    public void testInspectAnnotatedAPIFile() throws IOException {
        org.e2immu.analyser.util.Logger.activate(org.e2immu.analyser.util.Logger.LogTarget.BYTECODE_INSPECTOR,
                org.e2immu.analyser.util.Logger.LogTarget.INSPECTOR,
                org.e2immu.analyser.util.Logger.LogTarget.ANALYSER);
        Configuration configuration = new Configuration.Builder()
                .setInputConfiguration(new InputConfiguration.Builder()
                        .addClassPath("build/resources/main/annotatedAPIs")
                        .addClassPath("build/resources/main/annotations/jdkAnnotations")
                        .addClassPath("jmods/java.base.jmod").build())
                .setUploadConfiguration(new UploadConfiguration.Builder()
                        .setUpload(true).build())
                .build();
        //URL url = new URL("file:src/main/resources/annotatedAPIs/java.util.annotated_api");
        Parser parser = new Parser(configuration);
        //parser.runAnnotatedAPIs(List.of(url));
        TypeInfo optional = parser.getTypeContext().typeMapBuilder.get("java.util.Optional");
        assertNotNull(optional);
        assertEquals(Level.TRUE, optional.typeAnalysis.get().getProperty(VariableProperty.CONTAINER));
        LOGGER.info("Source of Optional: " + optional.output().toString());
    }

}
