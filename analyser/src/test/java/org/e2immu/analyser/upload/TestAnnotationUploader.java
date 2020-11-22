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

package org.e2immu.analyser.upload;

import ch.qos.logback.classic.Level;
import org.e2immu.analyser.config.Configuration;
import org.e2immu.analyser.config.InputConfiguration;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.parser.Input;
import org.e2immu.analyser.parser.Parser;
import org.e2immu.analyser.parser.TypeContext;
import org.e2immu.analyser.testexample.Basics;
import org.e2immu.analyser.util.UpgradableBooleanMap;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import static org.e2immu.analyser.util.Logger.LogTarget.*;

public class TestAnnotationUploader {

    @BeforeClass
    public static void beforeClass() {
        org.e2immu.analyser.util.Logger.configure(Level.INFO);
        org.e2immu.analyser.util.Logger.activate(ANALYSER, INSPECT, RESOLVE);
    }

    @Test
    public void test() throws IOException {
        Configuration configuration = new Configuration.Builder()
                .setInputConfiguration(new InputConfiguration.Builder()
                        .addSources("src/test/java")
                        .addRestrictSourceToPackages("org.e2immu.analyser.testexample.Basics")
                        .addClassPath(InputConfiguration.DEFAULT_CLASSPATH)
                        .addClassPath(Input.JAR_WITH_PATH_PREFIX + "com/google/common/collect")
                        .addClassPath(Input.JAR_WITH_PATH_PREFIX + "org/junit")
                        .addClassPath(Input.JAR_WITH_PATH_PREFIX + "org/slf4j")
                        .addClassPath(Input.JAR_WITH_PATH_PREFIX + "ch/qos/logback/core/spi")
                        .addClassPath(Input.JAR_WITH_PATH_PREFIX + "io/vertx/core")
                        .build())
                .build();
        Parser parser = new Parser(configuration);
        parser.run();
        TypeContext typeContext = parser.getTypeContext();

        TypeInfo basics = typeContext.typeMapBuilder.get(Basics.class);
        UpgradableBooleanMap<TypeInfo> typesReferredTo = basics.typesReferenced();
        Assert.assertTrue(typesReferredTo.get(typeContext.getPrimitives().stringTypeInfo));

        AnnotationUploader annotationUploader = new AnnotationUploader(configuration.uploadConfiguration, parser.getE2ImmuAnnotationExpressions());
        Map<String, String> map = annotationUploader.createMap(Set.of(basics));
        map.forEach((k, v) -> System.out.println(k + " --> " + v));

        Assert.assertEquals("e2container-t", map.get("java.lang.String"));
    }
}
