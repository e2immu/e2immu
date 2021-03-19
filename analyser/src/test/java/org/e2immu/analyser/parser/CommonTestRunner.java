
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
import org.e2immu.analyser.analyser.AnalysisStatus;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.output.Formatter;
import org.e2immu.analyser.output.FormattingOptions;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.resolver.SortedType;
import org.junit.jupiter.api.BeforeAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.e2immu.analyser.util.Logger.LogTarget.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public abstract class CommonTestRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(CommonTestRunner.class);
    public static final String ORG_E2IMMU_SUPPORT = "org.e2immu.support";
    public static final String ORG_E2IMMU_ANALYSER_UTIL = "org.e2immu.analyser.util";
    public static final String ORG_E2IMMU_ANALYSER_TESTEXAMPLE = "org.e2immu.analyser.testexample";

    public final boolean withAnnotatedAPIs;

    protected CommonTestRunner(boolean withAnnotatedAPIs) {
        this.withAnnotatedAPIs = withAnnotatedAPIs;
    }

    protected CommonTestRunner() {
        this.withAnnotatedAPIs = false;
    }

    @BeforeAll
    public static void beforeClass() {
        org.e2immu.analyser.util.Logger.configure(Level.INFO);
        org.e2immu.analyser.util.Logger.activate();
    }

    protected TypeContext testClass(String className, int errorsToExpect, int warningsToExpect, DebugConfiguration debugConfiguration) throws IOException {
        return testClass(List.of(className), errorsToExpect, warningsToExpect, debugConfiguration, new AnalyserConfiguration.Builder().build(),
                new AnnotatedAPIConfiguration.Builder().build());
    }

    protected TypeContext testClass(String className, int errorsToExpect, int warningsToExpect, DebugConfiguration debugConfiguration,
                                    AnalyserConfiguration analyserConfiguration) throws IOException {
        return testClass(List.of(className), errorsToExpect, warningsToExpect, debugConfiguration, analyserConfiguration,
                new AnnotatedAPIConfiguration.Builder().build());
    }

    protected TypeContext testClass(List<String> classNames, int errorsToExpect, int warningsToExpect,
                                    DebugConfiguration debugConfiguration,
                                    AnalyserConfiguration analyserConfiguration,
                                    AnnotatedAPIConfiguration annotatedAPIConfiguration) throws IOException {
        // parsing the annotatedAPI files needs them being backed up by .class files, so we'll add the Java
        // test runner's classpath to ours
        InputConfiguration.Builder inputConfigurationBuilder = new InputConfiguration.Builder()
                .addSources("src/test/java")
                .addClassPath(withAnnotatedAPIs ? InputConfiguration.DEFAULT_CLASSPATH
                        : InputConfiguration.CLASSPATH_WITHOUT_ANNOTATED_APIS)
                .addClassPath(Input.JAR_WITH_PATH_PREFIX + "org/slf4j")
                .addClassPath(Input.JAR_WITH_PATH_PREFIX + "org/junit/jupiter/api")
                .addClassPath(Input.JAR_WITH_PATH_PREFIX + "ch/qos/logback/core/spi");
        if (withAnnotatedAPIs) {
            inputConfigurationBuilder.addAnnotatedAPISources("../annotatedAPIs/src/main/java");
        }
        classNames.forEach(className -> inputConfigurationBuilder.addRestrictSourceToPackages(ORG_E2IMMU_ANALYSER_TESTEXAMPLE + "." + className));

        Configuration configuration = new Configuration.Builder()
                .setDebugConfiguration(debugConfiguration)
                .setAnalyserConfiguration(analyserConfiguration)
                .setAnnotatedAPIConfiguration(annotatedAPIConfiguration)
                .addDebugLogTargets(List.of(ANALYSER, // INSPECT, RESOLVE,

                        TRANSFORM,
                        LAMBDA,
                        // RESOLVE,
                        DELAYED, SIZE,
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
                        MARK
                        // OBJECT_FLOW
                ).stream().map(Enum::toString).collect(Collectors.joining(",")))
                .setInputConfiguration(inputConfigurationBuilder.build())
                .build();
        return execute(configuration, errorsToExpect, warningsToExpect);
    }

    protected void testUtilClass(List<String> classes,
                                 int errorsToExpect,
                                 int warningsToExpect,
                                 DebugConfiguration debugConfiguration) throws IOException {
        testSupportAndUtilClasses(List.of(), classes, ORG_E2IMMU_ANALYSER_UTIL,
                errorsToExpect, warningsToExpect, debugConfiguration);
    }

    protected void testSupportClass(List<String> classes,
                                    int errorsToExpect,
                                    int warningsToExpect,
                                    DebugConfiguration debugConfiguration) throws IOException {
        testSupportAndUtilClasses(List.of(), classes, ORG_E2IMMU_SUPPORT,
                errorsToExpect, warningsToExpect, debugConfiguration);
    }

    protected void testSupportAndUtilClasses(List<String> testClasses,
                                             List<String> utilClasses,
                                             String packageString,
                                             int errorsToExpect,
                                             int warningsToExpect,
                                             DebugConfiguration debugConfiguration) throws IOException {
        InputConfiguration.Builder builder = new InputConfiguration.Builder()
                .addSources("src/main/java")
                .addSources("src/test/java")
                .addSources("../annotations/src/main/java")
                .addAnnotatedAPISources("../annotatedAPIs/src/main/java")
                .addClassPath(InputConfiguration.DEFAULT_CLASSPATH)
                .addClassPath(Input.JAR_WITH_PATH_PREFIX + "org/slf4j")
                .addClassPath(Input.JAR_WITH_PATH_PREFIX + "ch/qos/logback/core/spi")
                .addClassPath("jmods/java.xml.jmod");

        testClasses.forEach(className -> builder.addRestrictSourceToPackages(ORG_E2IMMU_ANALYSER_TESTEXAMPLE + "." + className));
        utilClasses.forEach(className -> builder.addRestrictSourceToPackages(packageString + "." + className));

        Configuration configuration = new Configuration.Builder()
                .addDebugLogTargets(List.of(ANALYSER,
                        TRANSFORM,
                        LAMBDA,
                        DELAYED,
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
                        MARK

                ).stream().map(Enum::toString).collect(Collectors.joining(",")))
                .setDebugConfiguration(debugConfiguration)
                .setInputConfiguration(builder.build())
                .build();
        execute(configuration, errorsToExpect, warningsToExpect);
    }

    private TypeContext execute(Configuration configuration, int errorsToExpect, int warningsToExpect) throws IOException {
        configuration.initializeLoggers();
        Parser parser = new Parser(configuration);
        List<SortedType> types = parser.run();
        for (SortedType sortedType : types) {
            OutputBuilder outputBuilder = sortedType.primaryType().output();
            Formatter formatter = new Formatter(FormattingOptions.DEFAULT);
            LOGGER.info("Stream:\n{}\n", formatter.write(outputBuilder));
            //LOGGER.info("\n----\nOutput builder:\n{}", outputBuilder.generateJavaForDebugging());
        }
        assertFalse(types.isEmpty());
        parser.getMessages().forEach(message -> LOGGER.info(message.toString()));
        assertEquals(errorsToExpect, (int) parser.getMessages()
                .filter(m -> m.severity == Message.Severity.ERROR).count(), "ERRORS: ");
        assertEquals(warningsToExpect, (int) parser.getMessages()
                .filter(m -> m.severity == Message.Severity.WARN).count(), "WARNINGS: ");
        return parser.getTypeContext();
    }

    protected void assertSubMap(Map<AnalysisStatus, Set<String>> expect, Map<String, AnalysisStatus> statuses) {
        expect.forEach((as, set) -> set.forEach(label -> assertEquals(as, statuses.get(label),
                "Expected " + as + " for " + label + "; map is\n" + statuses)));
    }
}
