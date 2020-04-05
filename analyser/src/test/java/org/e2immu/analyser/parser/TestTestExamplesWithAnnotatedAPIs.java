
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

import ch.qos.logback.classic.BasicConfigurator;
import ch.qos.logback.classic.Level;
import org.e2immu.analyser.config.Configuration;
import org.e2immu.analyser.config.InputConfiguration;
import org.e2immu.analyser.util.Resources;
import org.e2immu.annotation.NotModified;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.e2immu.analyser.util.Logger.LogTarget.*;

public class TestTestExamplesWithAnnotatedAPIs {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestTestExamplesWithAnnotatedAPIs.class);
    public static final String SRC_TEST_JAVA_ORG_E2IMMU_ANALYSER = "src/test/java/org/e2immu/analyser/";

    @BeforeClass
    public static void beforeClass() {
        org.e2immu.analyser.util.Logger.configure(Level.INFO);
        org.e2immu.analyser.util.Logger.activate(ANALYSER, INSPECT, RESOLVE, LAMBDA,
                METHOD_CALL, VARIABLE_PROPERTIES, LINKED_VARIABLES, INDEPENDENT, MODIFY_CONTENT,
                E2IMMUTABLE, ANNOTATION_EXPRESSION, BYTECODE_INSPECTOR,
                CONTAINER, VALUE_CLASS, SIDE_EFFECT, UTILITY_CLASS, CONTEXT_ANNOTATIONS, PURE_ANNOTATIONS,
                NULL_NOT_ALLOWED, NOT_MODIFIED);
    }


    @Test
    public void testE2ImmutableChecks() throws IOException {
        goTest("testexample/E2ImmutableChecks.java");
    }

    @Test
    public void testContainerChecks() throws IOException {
        goTest("testexample/ContainerChecks.java");
    }

    @Test
    public void testCyclicReferences() throws IOException {
        goTest("testexample/CyclicReferences.java");
    }

    @Test
    public void testEvaluateConstants() throws IOException {
        goTest("testexample/EvaluateConstants.java");
    }

    @Test
    public void testIdentityChecks() throws IOException {
        goTest("testexample/IdentityChecks.java");
    }

    @Test
    public void testNotModifiedChecks() throws IOException {
        goTest("testexample/NotModifiedChecks.java");
    }

    // simplified version of NotModifiedChecks to debug one of the issues there
    @Test
    public void testNotModifiedChecks2() throws IOException {
        goTest("testexample/NotModifiedChecks2.java");
    }

    @Test
    public void testNullParameterChecks() throws IOException {
        goTest("testexample/NullParameterChecks.java");
    }

    @Test
    public void testSimpleNotModifiedChecks() throws IOException {
        goTest("testexample/SimpleNotModifiedChecks.java");
    }

    @Test
    public void testStaticImports() throws IOException {
        goTest("testexample/StaticImports.java");
    }

    @Test
    public void testSubTypes() throws IOException {
        goTest("testexample/SubTypes.java");
    }

    @NotModified
    private void goTest(String fileName) throws IOException {
        goTest(fileName, 0);
    }

    @NotModified
    private void goTest(String fileName, long countError) throws IOException {
        // parsing the annotatedAPI files needs them being backed up by .class files, so we'll add the Java
        // test runner's classpath to ours
        Configuration configuration = new Configuration.Builder()
                .setInputConfiguration(new InputConfiguration.Builder()
                        .addSources("src/test/java")
                        .addRestrictSourceToPackages("org.e2immu.analyser.testexample.withannotatedapi")
                        .addClassPath(InputConfiguration.DEFAULT_CLASSPATH)
                        .addClassPath(Input.JAR_WITH_PATH_PREFIX + "com/google/common/collect")
                        .addClassPath(Input.JAR_WITH_PATH_PREFIX + "org/junit")
                        .addClassPath(Input.JAR_WITH_PATH_PREFIX + "org/hamcrest")
                        .addClassPath(Input.JAR_WITH_PATH_PREFIX + "org/slf4j")
                        .addClassPath(Input.JAR_WITH_PATH_PREFIX + "ch/qos/logback/core/spi")
                        .addClassPath(Input.JAR_WITH_PATH_PREFIX + "javax/servlet/http")
                        .build())
                .build();
        BasicConfigurator basicConfigurator;
        Parser parser = new Parser(configuration);
        List<SortedType> types = parser.run();
        for (SortedType sortedType : types) {
            LOGGER.info("Stream:\n{}", sortedType.typeInfo.stream());
        }
        for (Message message : parser.getMessages()) {
            LOGGER.info(message.toString());
        }
        Assert.assertEquals(countError, parser.getMessages().stream().filter(m -> m.severity == Message.Severity.ERROR).count());
    }

}
