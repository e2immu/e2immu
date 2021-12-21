
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
import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.FieldAnalysis;
import org.e2immu.analyser.output.Formatter;
import org.e2immu.analyser.output.FormattingOptions;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.resolver.SortedType;
import org.e2immu.analyser.visitor.CommonVisitorData;
import org.e2immu.analyser.visitor.FieldAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVisitor;
import org.junit.jupiter.api.BeforeAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.util.Logger.LogTarget.*;
import static org.junit.jupiter.api.Assertions.*;

public abstract class CommonTestRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(CommonTestRunner.class);
    public static final String ORG_E2IMMU_ANALYSER_TESTEXAMPLE = "org.e2immu.analyser.testexample";
    public static final String DEFAULT_ANNOTATED_API_DIRS = "../annotatedAPIs/src/main/java";
    public static final String JDK_16 = "/Library/Java/JavaVirtualMachines/adoptopenjdk-16.jdk/Contents/Home";

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
                .setAlternativeJREDirectory(JDK_16)
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
                .addDebugLogTargets(Stream.of(ANALYSER,
                        //INSPECTOR,
                        //RESOLVER,
                        PRIMARY_TYPE_ANALYSER,
                        LAMBDA,
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
                ).map(Enum::toString).collect(Collectors.joining(",")))
                .setInputConfiguration(inputConfigurationBuilder.build())
                .build();
        return execute(configuration, errorsToExpect, warningsToExpect);
    }

    protected TypeContext testSupportAndUtilClasses(List<Class<?>> classes,
                                                    int errorsToExpect,
                                                    int warningsToExpect,
                                                    DebugConfiguration debugConfiguration) throws IOException {
        InputConfiguration.Builder builder = new InputConfiguration.Builder()
                .setAlternativeJREDirectory(JDK_16)
                .addSources("src/main/java")
                .addSources("src/test/java")
                .addSources("../../e2immu-support/src/main/java")
                .addClassPath(InputConfiguration.DEFAULT_CLASSPATH)
                .addClassPath(Input.JAR_WITH_PATH_PREFIX + "org/slf4j")
                .addClassPath(Input.JAR_WITH_PATH_PREFIX + "ch/qos/logback/core/spi")
                .addClassPath(Input.JAR_WITH_PATH_PREFIX + "org/apache/commons/io")
                .addClassPath("jmods/java.xml.jmod");

        classes.forEach(clazz -> builder.addRestrictSourceToPackages(clazz.getCanonicalName()));

        AnnotatedAPIConfiguration annotatedAPIConfiguration = new AnnotatedAPIConfiguration.Builder()
                .addAnnotatedAPISourceDirs(DEFAULT_ANNOTATED_API_DIRS)
                .build();

        Configuration configuration = new Configuration.Builder()
                .setAnnotatedAPIConfiguration(annotatedAPIConfiguration)
                .addDebugLogTargets(Stream.of(ANALYSER,
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

                ).map(Enum::toString).collect(Collectors.joining(",")))
                .setDebugConfiguration(debugConfiguration)
                .setInputConfiguration(builder.build())
                .build();
        return execute(configuration, errorsToExpect, warningsToExpect);
    }

    private TypeContext execute(Configuration configuration, int errorsToExpect, int warningsToExpect) throws IOException {
        configuration.initializeLoggers();
        Parser parser = new Parser(configuration);
        List<SortedType> types = parser.run().sourceSortedTypes();

        if (!mustSee.isEmpty()) {
            mustSee.forEach((label, iteration) -> {
                LOGGER.error("MustSee: {} has only reached iteration {}", label, iteration);
            });
            assertEquals(0, mustSee.size());
        }

        for (SortedType sortedType : types) {
            OutputBuilder outputBuilder = sortedType.primaryType().output();
            Formatter formatter = new Formatter(FormattingOptions.DEFAULT);
            LOGGER.info("Stream:\n{}\n", formatter.write(outputBuilder));
            //LOGGER.info("\n----\nOutput builder:\n{}", outputBuilder.generateJavaForDebugging());
        }
        assertFalse(types.isEmpty());
        List<Message> messages = parser.getMessages().toList();
        List<Message> filteredMessages;

        // there are some errors thrown by AnnotatedAPIAnalyser.validateIndependence when there are no Annotated API files
        // as long as they pertain to the JDK, we don't bother
        if (withAnnotatedAPIs) {
            filteredMessages = messages;
        } else {
            filteredMessages = messages.stream()
                    .filter(m -> m.message() != Message.Label.TYPE_HAS_HIGHER_VALUE_FOR_INDEPENDENT ||
                            m.location().info == null ||
                            !m.location().info.getTypeInfo().packageName().startsWith("java."))
                    .toList();
        }
        filteredMessages
                .stream()
                .filter(message -> message.message().severity != Message.Severity.INFO)
                .sorted(Message::SORT)
                .forEach(message -> LOGGER.info(message.toString()));
        assertEquals(errorsToExpect, (int) filteredMessages.stream()
                .filter(m -> m.message().severity == Message.Severity.ERROR).count(), "ERRORS: ");
        assertEquals(warningsToExpect, (int) filteredMessages.stream()
                .filter(m -> m.message().severity == Message.Severity.WARN).count(), "WARNINGS: ");
        return parser.getTypeContext();
    }

    protected void assertSubMap(Map<AnalysisStatus, Set<String>> expect, Map<String, AnalysisStatus> statuses) {
        expect.forEach((as, set) -> set.forEach(label -> assertEquals(as, statuses.get(label),
                "Expected " + as + " for " + label + "; map is\n" + statuses)));
    }

    public void assertDvInitial(StatementAnalyserVariableVisitor.Data d, DV expect, Property property) {
        DV value = d.variableInfoContainer().getPreviousOrInitial().getProperty(property);
        assertEquals(expect, value);
    }

    public void assertDv(CommonVisitorData d, DV expect, Property property) {
        DV value = d.getProperty(property);
        assertEquals(expect, value);
    }

    public void assertDvInitial(StatementAnalyserVariableVisitor.Data d, String delayed, int delayedBeforeIteration, DV expect, Property property) {
        DV value = d.variableInfoContainer().getPreviousOrInitial().getProperty(property);
        if (d.iteration() < delayedBeforeIteration) {
            assertEquals(delayed, value.causesOfDelay().toString(), value.isDone() ? "Expected delay in iteration " + d.iteration() + "<=" + delayedBeforeIteration + ", but got " + value + " for property " + property :
                    "Expected delay " + delayed + ", but got " + value + " in iteration " + d.iteration() + "<" + delayedBeforeIteration + " for property " + property);
        } else {
            assertEquals(expect, value, "Expected " + expect + " from iteration " + d.iteration() + ">=" + delayedBeforeIteration + ", but got " + value + " for property " + property);
        }
    }

    public void assertDv(CommonVisitorData d, int delayedBeforeIteration, DV expect, Property property) {
        DV value = d.getProperty(property);
        if (d.iteration() < delayedBeforeIteration) {
            assertTrue(value == null || value.isDelayed(),
                    "Expected delay in iteration " + d.iteration() + "<" + delayedBeforeIteration + ", but got " + value + " for property " + property);
        } else {
            assertEquals(expect, value, "Expected " + expect + " from iteration " + d.iteration() + ">=" + delayedBeforeIteration + ", but got " + value + " for property " + property);
        }
    }

    public void assertDv(CommonVisitorData d, String delayed, int delayedBeforeIteration, DV expect, Property property) {
        DV value = d.getProperty(property);
        if (d.iteration() < delayedBeforeIteration) {
            assertEquals(delayed, value.toString(),
                    "Expected delay in iteration " + d.iteration() + "<" + delayedBeforeIteration + ", but got " + value + " for property " + property);
        } else {
            assertEquals(expect, value, "Expected " + expect + " from iteration " + d.iteration() + ">=" + delayedBeforeIteration + ", but got " + value + " for property " + property);
        }
    }

    public void assertDv(StatementAnalyserVisitor.Data d, int delayedBeforeIteration, DV expect, DV actual) {
        if (d.iteration() < delayedBeforeIteration) {
            assertTrue(actual.isDelayed(),
                    "Expected delay in iteration " + d.iteration() + "<" + delayedBeforeIteration + ", but got " + actual);
        } else {
            assertEquals(expect, actual, "Expected " + expect + " from iteration " + d.iteration() + ">=" + delayedBeforeIteration + ", but got " + actual);
        }
    }

    public void assertDv(StatementAnalyserVisitor.Data d, int delayedBeforeIteration, AnalysisStatus expect, AnalysisStatus actual) {
        if (d.iteration() < delayedBeforeIteration) {
            assertTrue(actual.isDelayed(),
                    "Expected delay in iteration " + d.iteration() + "<" + delayedBeforeIteration + ", but got " + actual);
        } else {
            assertEquals(expect, actual, "Expected " + expect + " from iteration " + d.iteration() + ">=" + delayedBeforeIteration + ", but got " + actual);
        }
    }

    public void assertCurrentValue(StatementAnalyserVariableVisitor.Data d, int delayedBeforeIteration, String causesOfDelay, String value) {
        if (d.iteration() < delayedBeforeIteration) {
            assertTrue(d.currentValue().isDelayed(), "Expected current value to be delayed in iteration " + d.iteration() + "<" + delayedBeforeIteration + ", but was " + d.currentValue() + " for variable " + d.variableName());
            assertEquals(extract(causesOfDelay, d.iteration()), d.currentValue().causesOfDelay().toString());
        } else {
            assertTrue(d.currentValue().isDone(), "Expected current value to be done in iteration " + d.iteration() + ">=" + delayedBeforeIteration + ", but got " + d.currentValue()
                    .causesOfDelay() + " for variable " + d.variableName());
            assertEquals(value, d.currentValue().toString());
        }
    }

    /*
    a -> a
    a|b -> abbbb
    a||b -> aabbbb
    a|b||c -> abbccc
     */
    private static String extract(String causesOfDelay, int iteration) {
        int pipe = causesOfDelay.indexOf('|');
        if (pipe >= 0) {
            String[] split = causesOfDelay.split("\\|");
            if (iteration >= split.length) return split[split.length - 1];
            int pos = iteration;
            while (pos > 0 && split[pos].isEmpty()) pos--;
            return split[pos];
        }
        return causesOfDelay;
    }

    public void assertInitialValue(StatementAnalyserVariableVisitor.Data d, int delayedBeforeIteration, String causesOfDelay, String value) {
        Expression initialValue = d.variableInfoContainer().getPreviousOrInitial().getValue();
        if (d.iteration() < delayedBeforeIteration) {
            assertTrue(initialValue.isDelayed(), "Expected current value to be delayed in iteration " + d.iteration() + "<" + delayedBeforeIteration + ", but was " + initialValue + " for variable " + d.variableName());
            assertEquals(causesOfDelay, initialValue.causesOfDelay().toString());
        } else {
            assertTrue(initialValue.isDone(), "Expected current value to be done in iteration " + d.iteration() + ">=" + delayedBeforeIteration + ", but got " + initialValue
                    .causesOfDelay() + " for variable " + d.variableName());
            assertEquals(value, initialValue.toString());
        }
    }

    public void assertLinked(StatementAnalyserVariableVisitor.Data d, int delayedBeforeIteration, String causesOfDelay, String value) {
        LinkedVariables linkedVariables = d.variableInfo().getLinkedVariables();
        if (d.iteration() < delayedBeforeIteration) {
            assertTrue(linkedVariables.isDelayed(), "Expected linked variables to be delayed in iteration "
                    + d.iteration() + "<" + delayedBeforeIteration + " for variable " + d.variableName());
            assertEquals(causesOfDelay, linkedVariables.causesOfDelay().toString());
        } else {
            assertTrue(linkedVariables.isDone(), "Expected linked variables to be done in iteration "
                    + d.iteration() + "<" + delayedBeforeIteration + " for variable " + d.variableName() + ", got delays "
                    + linkedVariables.causesOfDelay());
            assertEquals(value, linkedVariables.toString());
        }
    }

    public void assertLinked(FieldAnalyserVisitor.Data d, int delayedBeforeIteration, String causesOfDelay, String value) {
        LinkedVariables linkedVariables = d.fieldAnalysis().getLinkedVariables();
        if (d.iteration() < delayedBeforeIteration) {
            assertTrue(linkedVariables.isDelayed(), "Expected linked variables to be delayed in iteration "
                    + d.iteration() + "<" + delayedBeforeIteration + " for field " + d.fieldInfo().name);
            assertEquals(causesOfDelay, linkedVariables.causesOfDelay().toString());
        } else {
            assertTrue(linkedVariables.isDone(), "Expected linked variables to be done in iteration "
                    + d.iteration() + "<" + delayedBeforeIteration + " for field " + d.fieldInfo().name + ", got delays "
                    + linkedVariables.causesOfDelay());
            assertEquals(value, linkedVariables.toString());
        }
    }

    private final Map<String, Integer> mustSee = new HashMap<>();

    public void mustSeeIteration(CommonVisitorData cvd, int targetIteration) {
        String label = cvd.label();
        if (cvd.iteration() < targetIteration) {
            mustSee.put(label, cvd.iteration());
        } else {
            mustSee.remove(label);
        }
    }

}
