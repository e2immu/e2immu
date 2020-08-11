
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

import ch.qos.logback.classic.Level;
import org.e2immu.analyser.config.Configuration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.InputConfiguration;
import org.e2immu.annotation.NotModified;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import static org.e2immu.analyser.util.Logger.LogTarget.*;

public abstract class CommonTestRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(CommonTestRunner.class);

    public final boolean withAnnotatedAPIs;

    protected CommonTestRunner(boolean withAnnotatedAPIs) {
        this.withAnnotatedAPIs = withAnnotatedAPIs;
    }

    protected CommonTestRunner() {
        this.withAnnotatedAPIs = false;
    }

    @BeforeClass
    public static void beforeClass() {
        org.e2immu.analyser.util.Logger.configure(Level.INFO);
        org.e2immu.analyser.util.Logger.activate();
    }

    protected TypeContext testClass(String className, int errorsToExpect) throws IOException {
        return testClass(className, errorsToExpect, 0, new DebugConfiguration.Builder().build());
    }

    protected TypeContext testClass(String className, int errorsToExpect, DebugConfiguration debugConfiguration) throws IOException {
        return testClass(className, errorsToExpect, 0, debugConfiguration);
    }

    protected TypeContext testClass(String className, int errorsToExpect, int warningsToExpect, DebugConfiguration debugConfiguration) throws IOException {
        // parsing the annotatedAPI files needs them being backed up by .class files, so we'll add the Java
        // test runner's classpath to ours

        Configuration configuration = new Configuration.Builder()
                .setDebugConfiguration(debugConfiguration)
                .addDebugLogTargets(List.of(ANALYSER, INSPECT, RESOLVE,

                        //LAMBDA,
                        //METHOD_CALL,

                        //VARIABLE_PROPERTIES,
                        FINAL,
                        LINKED_VARIABLES,
                        INDEPENDENT,
                        E2IMMUTABLE,
                        ANNOTATION_EXPRESSION,
                        CONSTANT,
                        CONTAINER,
                        E1IMMUTABLE,
                        SIDE_EFFECT,
                        UTILITY_CLASS,
                        NOT_NULL,
                        NOT_MODIFIED,
                        PATTERN,
                        MARK,
                        OBJECT_FLOW).stream().map(Enum::toString).collect(Collectors.joining(",")))

                .setInputConfiguration(new InputConfiguration.Builder()
                        .addSources("src/test/java")
                        .addRestrictSourceToPackages("org.e2immu.analyser.testexample." + className)
                        .addClassPath(withAnnotatedAPIs ? InputConfiguration.DEFAULT_CLASSPATH : InputConfiguration.CLASSPATH_WITHOUT_ANNOTATED_APIS)
                        .addClassPath(Input.JAR_WITH_PATH_PREFIX + "com/google/common/collect")
                        .addClassPath(Input.JAR_WITH_PATH_PREFIX + "org/junit")
                        .addClassPath(Input.JAR_WITH_PATH_PREFIX + "org/slf4j")
                        .addClassPath(Input.JAR_WITH_PATH_PREFIX + "ch/qos/logback/core/spi")
                        .addClassPath(Input.JAR_WITH_PATH_PREFIX + "io/vertx/core")
                        .build())
                .build();
        return execute(configuration, errorsToExpect, warningsToExpect);
    }

    protected TypeContext testUtilClass(String className, int errorsToExpect, int warningsToExpect, DebugConfiguration debugConfiguration) throws IOException {
        Configuration configuration = new Configuration.Builder()
                .setDebugConfiguration(debugConfiguration)
                .setInputConfiguration(new InputConfiguration.Builder()
                        .addSources("src/main/java")
                        .addRestrictSourceToPackages("org.e2immu.analyser.util." + className)
                        .addClassPath(InputConfiguration.DEFAULT_CLASSPATH)
                        .addClassPath(Input.JAR_WITH_PATH_PREFIX + "com/google/common/collect")
                        .addClassPath(Input.JAR_WITH_PATH_PREFIX + "org/junit")
                        .addClassPath(Input.JAR_WITH_PATH_PREFIX + "org/slf4j")
                        .addClassPath(Input.JAR_WITH_PATH_PREFIX + "ch/qos/logback/core/spi")
                        .addClassPath(Input.JAR_WITH_PATH_PREFIX + "org/apache/commons/io")
                        .addClassPath(Input.JAR_WITH_PATH_PREFIX + "org/objectweb/asm")
                        .addClassPath(Input.JAR_WITH_PATH_PREFIX + "com/google/gson")
                        .addClassPath(Input.JAR_WITH_PATH_PREFIX + "com/github/javaparser")
                        .addClassPath(Input.JAR_WITH_PATH_PREFIX + "org/apache/http")
                        .addClassPath(Input.JAR_WITH_PATH_PREFIX + "org/apache/commons/cli")
                        .addClassPath("jmods/java.xml.jmod")
                        .build())
                .build();
        return execute(configuration, errorsToExpect, warningsToExpect);
    }

    private TypeContext execute(Configuration configuration, int errorsToExpect, int warningsToExpect) throws IOException {
        Parser parser = new Parser(configuration);
        List<SortedType> types = parser.run();
        for (SortedType sortedType : types) {
            LOGGER.info("Stream:\n{}", sortedType.typeInfo.stream());
        }
        parser.getMessages().forEach(message -> {
            LOGGER.info(message.toString());
        });
        Assert.assertEquals("ERRORS: ", errorsToExpect, (int) parser.getMessages()
                .filter(m -> m.severity == Message.Severity.ERROR).count());
        Assert.assertEquals("WARNINGS: ", warningsToExpect, (int) parser.getMessages()
                .filter(m -> m.severity == Message.Severity.WARN).count());
        return parser.getTypeContext();
    }

}
