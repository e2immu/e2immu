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

package org.e2immu.analyser.bytecode;

import ch.qos.logback.classic.Level;
import org.e2immu.analyser.config.Configuration;
import org.e2immu.analyser.config.InputConfiguration;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.TypeInspection;
import org.e2immu.analyser.model.TypeInspectionImpl;
import org.e2immu.analyser.parser.Input;
import org.e2immu.annotation.E2Immutable;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static org.e2immu.analyser.util.Logger.LogTarget.BYTECODE_INSPECTOR;
import static org.e2immu.analyser.util.Logger.LogTarget.BYTECODE_INSPECTOR_DEBUG;

public class TestPreloadAnnotations {

    @BeforeClass
    public static void beforeClass() {
        org.e2immu.analyser.util.Logger.configure(Level.INFO);
        org.e2immu.analyser.util.Logger.activate();
    }

    @Test
    public void test() throws IOException {
        InputConfiguration.Builder inputConfigurationBuilder = new InputConfiguration.Builder()
                .addSources("src/test/java")
                .addAnnotatedAPISources("../annotatedAPIs/src/main/java")
                .addClassPath(InputConfiguration.CLASSPATH_WITHOUT_ANNOTATED_APIS);

        Configuration configuration = new Configuration.Builder()
                .addDebugLogTargets(List.of(BYTECODE_INSPECTOR, BYTECODE_INSPECTOR_DEBUG)
                        .stream().map(Enum::toString).collect(Collectors.joining(",")))
                .setInputConfiguration(inputConfigurationBuilder.build())
                .build();
        configuration.initializeLoggers();

        Input input = Input.create(configuration);

        TypeInfo e2Immu = input.globalTypeContext().getFullyQualified(E2Immutable.class);
        Assert.assertNotNull(e2Immu);

        TypeInfo objectTypeInfo = input.globalTypeContext().getFullyQualified(Object.class);
        Assert.assertNotNull(objectTypeInfo);
        Assert.assertSame(objectTypeInfo, input.globalTypeContext().getPrimitives().objectTypeInfo);

        TypeInfo collection = input.globalTypeContext().getFullyQualified(Collection.class);
        Assert.assertNotNull(objectTypeInfo);
        TypeInspection collectionInspection = input.globalTypeContext().getTypeInspection(collection);
        Assert.assertEquals(TypeInspectionImpl.FINISHED_BYTECODE, collectionInspection.getInspectionState());

        // all toArray methods have a method inspection
        Assert.assertTrue(collectionInspection.methods().stream()
                .filter(mi -> mi.name.equals("toArray"))
                .allMatch(mi -> input.globalTypeContext().getMethodInspection(mi) != null));
    }
}
