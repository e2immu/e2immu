
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
import org.e2immu.analyser.config.Configuration;
import org.e2immu.analyser.config.InputConfiguration;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.TypeInfo;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static org.e2immu.analyser.util.Logger.LogTarget.*;

public class TestWithSkeleton {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestWithSkeleton.class);

    @BeforeClass
    public static void beforeClass() {
        org.e2immu.analyser.util.Logger.configure(ch.qos.logback.classic.Level.INFO);
        org.e2immu.analyser.util.Logger.activate(ANALYSER, INSPECT, RESOLVE, VARIABLE_PROPERTIES);
    }

    @Before
    public void before() throws IOException {
        testClass("TestSkeleton");
    }

    private TypeContext typeContext;

    private void testClass(String className) throws IOException {
        // parsing the annotatedAPI files needs them being backed up by .class files, so we'll add the Java
        // test runner's classpath to ours
        Configuration configuration = new Configuration.Builder()
                .setSkipAnalysis(true)
                .setInputConfiguration(new InputConfiguration.Builder()
                        .addSources("src/test/java")
                        .addRestrictSourceToPackages("org.e2immu.analyser.testexample.withannotatedapi." + className)
                        .addClassPath(InputConfiguration.DEFAULT_CLASSPATH)
                        .addClassPath(Input.JAR_WITH_PATH_PREFIX + "com/google/common/collect")
                        .addClassPath(Input.JAR_WITH_PATH_PREFIX + "org/junit")
                        .addClassPath(Input.JAR_WITH_PATH_PREFIX + "org/slf4j")
                        .addClassPath(Input.JAR_WITH_PATH_PREFIX + "ch/qos/logback/core/spi")
                        .build())
                .build();

        Parser parser = new Parser(configuration);
        parser.run();
        typeContext = parser.getTypeContext();
    }

    @Test
    public void testProperties() {
        TypeInfo set = typeContext.typeStore.get("java.util.Set");
        Assert.assertNotNull(set);
        Assert.assertFalse(set.typeInspection.get().hasBeenDefined);
        Assert.assertTrue(set.annotatedWith(typeContext.container.get()));
        Assert.assertEquals(Level.TRUE, set.typeAnalysis.getProperty(VariableProperty.CONTAINER));
        Assert.assertEquals(Level.TRUE, set.typeAnalysis.getProperty(VariableProperty.NOT_NULL_PARAMETERS));
        Assert.assertEquals(Level.DELAY, set.typeAnalysis.getProperty(VariableProperty.NOT_NULL));
    }
}
