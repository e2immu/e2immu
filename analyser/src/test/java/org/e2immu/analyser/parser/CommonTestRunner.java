
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
    public static final String DEFAULT_ANNOTATED_API_DIRS = "../annotatedAPIs/src/main/java";

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
        AnnotatedAPIConfiguration.Builder builder = new AnnotatedAPIConfiguration.Builder();
        if (withAnnotatedAPIs) {
            builder.addAnnotatedAPISourceDirs(DEFAULT_ANNOTATED_API_DIRS);
        }
        return testClass(List.of(className), errorsToExpect, warningsToExpect, debugConfiguration, new AnalyserConfiguration.Builder().build(),
                builder.build());
    }

    protected TypeContext testClass(String className, int errorsToExpect, int warningsToExpect, DebugConfiguration debugConfiguration,
                                    AnalyserConfiguration analyserConfiguration) throws IOException {
        AnnotatedAPIConfiguration.Builder apiBuilder = new AnnotatedAPIConfiguration.Builder();
        if (withAnnotatedAPIs) {
            apiBuilder.addAnnotatedAPISourceDirs(DEFAULT_ANNOTATED_API_DIRS);
        }
        return testClass(List.of(className), errorsToExpect, warningsToExpect, debugConfiguration, analyserConfiguration,
                apiBuilder.build());
    }

    protected TypeContext testClass(List<String> classNames, int errorsToExpect, int warningsToExpect,
                                    DebugConfiguration debugConfiguration,
                                    AnalyserConfiguration analyserConfiguration,
                                    AnnotatedAPIConfiguration annotatedAPIConfiguration) throws IOException {
        return testClass(classNames, List.of(), errorsToExpect, warningsToExpect, debugConfiguration,
                analyserConfiguration, annotatedAPIConfiguration);
    }

    protected TypeContext testClass(List<String> classNames,
                                    List<String> extraClassPath,
                                    int errorsToExpect,
                                    int warningsToExpect,
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
        extraClassPath.forEach(inputConfigurationBuilder::addClassPath);

        classNames.forEach(className -> inputConfigurationBuilder
                .addRestrictSourceToPackages(ORG_E2IMMU_ANALYSER_TESTEXAMPLE + "." + className));

        Configuration configuration = new Configuration.Builder()
                .setDebugConfiguration(debugConfiguration)
                .setAnalyserConfiguration(analyserConfiguration)
                .setAnnotatedAPIConfiguration(annotatedAPIConfiguration)
                .addDebugLogTargets(List.of(ANALYSER, // INSPECT, RESOLVE,

                        LAMBDA,
                        // RESOLVE,
                        DELAYED,
                        CONTEXT_MODIFICATION,
                        FINAL,
                        LINKED_VARIABLES,
                        INDEPENDENCE,
                        IMMUTABLE_LOG,
                        METHOD_ANALYSER,
                        TYPE_ANALYSER,
                        NOT_NULL,
                        MODIFICATION,
                        EVENTUALLY
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
                .addSources("../../e2immu-support/src/main/java")
                .addClassPath(InputConfiguration.DEFAULT_CLASSPATH)
                .addClassPath(Input.JAR_WITH_PATH_PREFIX + "org/slf4j")
                .addClassPath(Input.JAR_WITH_PATH_PREFIX + "ch/qos/logback/core/spi")
                .addClassPath("jmods/java.xml.jmod");

        testClasses.forEach(className -> builder.addRestrictSourceToPackages(ORG_E2IMMU_ANALYSER_TESTEXAMPLE + "." + className));
        utilClasses.forEach(className -> builder.addRestrictSourceToPackages(packageString + "." + className));

        AnnotatedAPIConfiguration annotatedAPIConfiguration = new AnnotatedAPIConfiguration.Builder()
                .addAnnotatedAPISourceDirs(DEFAULT_ANNOTATED_API_DIRS)
                .build();

        Configuration configuration = new Configuration.Builder()
                .setAnnotatedAPIConfiguration(annotatedAPIConfiguration)
                .addDebugLogTargets(List.of(ANALYSER,
                        LAMBDA,
                        DELAYED,
                        FINAL,
                        LINKED_VARIABLES,
                        INDEPENDENCE,
                        IMMUTABLE_LOG,
                        METHOD_ANALYSER,
                        TYPE_ANALYSER,
                        NOT_NULL,
                        MODIFICATION,
                        EVENTUALLY

                ).stream().map(Enum::toString).collect(Collectors.joining(",")))
                .setDebugConfiguration(debugConfiguration)
                .setInputConfiguration(builder.build())
                .build();
        execute(configuration, errorsToExpect, warningsToExpect);
    }

    private TypeContext execute(Configuration configuration, int errorsToExpect, int warningsToExpect) throws IOException {
        configuration.initializeLoggers();
        Parser parser = new Parser(configuration);
        List<SortedType> types = parser.run().sourceSortedTypes();
        for (SortedType sortedType : types) {
            OutputBuilder outputBuilder = sortedType.primaryType().output();
            Formatter formatter = new Formatter(FormattingOptions.DEFAULT);
            LOGGER.info("Stream:\n{}\n", formatter.write(outputBuilder));
            //LOGGER.info("\n----\nOutput builder:\n{}", outputBuilder.generateJavaForDebugging());
        }
        assertFalse(types.isEmpty());
        parser.getMessages().forEach(message -> LOGGER.info(message.toString()));
        assertEquals(errorsToExpect, (int) parser.getMessages()
                .filter(m -> m.message().severity == Message.Severity.ERROR).count(), "ERRORS: ");
        assertEquals(warningsToExpect, (int) parser.getMessages()
                .filter(m -> m.message().severity == Message.Severity.WARN).count(), "WARNINGS: ");
        return parser.getTypeContext();
    }

    protected void assertSubMap(Map<AnalysisStatus, Set<String>> expect, Map<String, AnalysisStatus> statuses) {
        expect.forEach((as, set) -> set.forEach(label -> assertEquals(as, statuses.get(label),
                "Expected " + as + " for " + label + "; map is\n" + statuses)));
    }
}
