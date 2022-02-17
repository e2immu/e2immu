
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

import org.e2immu.analyser.config.*;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.model.impl.LocationImpl;
import org.e2immu.analyser.output.Formatter;
import org.e2immu.analyser.output.FormattingOptions;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.resolver.SortedType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

import static org.e2immu.analyser.config.AnalyserProgram.Step.ALL;
import static org.junit.jupiter.api.Assertions.*;

public abstract class CommonTestRunner extends VisitorTestSupport {
    private static final Logger LOGGER = LoggerFactory.getLogger(CommonTestRunner.class);
    public static final String DEFAULT_ANNOTATED_API_DIRS = "../annotatedAPIs/src/main/java";
    public static final String JDK_16 = "/Library/Java/JavaVirtualMachines/adoptopenjdk-16.jdk/Contents/Home";

    public final boolean withAnnotatedAPIs;

    protected CommonTestRunner(boolean withAnnotatedAPIs) {
        this.withAnnotatedAPIs = withAnnotatedAPIs;
    }

    protected CommonTestRunner() {
        this.withAnnotatedAPIs = false;
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
        assertTrue(analyserConfiguration.analyserProgram().accepts(ALL));
        // parsing the annotatedAPI files needs them being backed up by .class files, so we'll add the Java
        // test runner's classpath to ours
        InputConfiguration.Builder inputConfigurationBuilder = new InputConfiguration.Builder()
                .setAlternativeJREDirectory(JDK_16)
                .addSources("src/test/java")
                .addClassPath(withAnnotatedAPIs ? InputConfiguration.DEFAULT_CLASSPATH
                        : InputConfiguration.CLASSPATH_WITHOUT_ANNOTATED_APIS)
                .addClassPath(Input.JAR_WITH_PATH_PREFIX + "org/slf4j")
                .addClassPath(Input.JAR_WITH_PATH_PREFIX + "org/junit/jupiter/api")
                .addClassPath(Input.JAR_WITH_PATH_PREFIX + "ch/qos/logback/core/spi")
                .addClassPath(Input.JAR_WITH_PATH_PREFIX + "com/github/javaparser/ast");
        extraClassPath.forEach(inputConfigurationBuilder::addClassPath);

        String prefix = getClass().getPackageName() + ".testexample";
        classNames.forEach(className -> inputConfigurationBuilder
                .addRestrictSourceToPackages(prefix + "." + className));

        Configuration configuration = new Configuration.Builder()
                .setDebugConfiguration(debugConfiguration)
                .setAnalyserConfiguration(analyserConfiguration)
                .setAnnotatedAPIConfiguration(annotatedAPIConfiguration)
                .addDebugLogTargets("analyser")
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
                .addClassPath(Input.JAR_WITH_PATH_PREFIX + "ch/qos/logback/core")
                .addClassPath(Input.JAR_WITH_PATH_PREFIX + "com/github/javaparser/ast");

        classes.forEach(clazz -> builder.addRestrictSourceToPackages(clazz.getCanonicalName()));

        // IMPORTANT: when analysing SetOnce or EventuallyFinal, we cannot parse the AnnotatedAPI file OrgE2ImmuSupport.java as well.
        AnnotatedAPIConfiguration.Builder b = new AnnotatedAPIConfiguration.Builder().addAnnotatedAPISourceDirs(DEFAULT_ANNOTATED_API_DIRS);
        if (!withAnnotatedAPIs) {
            b.addReadAnnotatedAPIPackages("org.e2immu.annotatedapi.java");
        }
        AnnotatedAPIConfiguration annotatedAPIConfiguration = b.build();

        Configuration configuration = new Configuration.Builder()
                .setAnnotatedAPIConfiguration(annotatedAPIConfiguration)
                .addDebugLogTargets("analyser")
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
            mustSee.forEach((label, iteration) -> LOGGER.error("MustSee: {} has only reached iteration {}", label, iteration));
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
                            ((LocationImpl) m.location()).info == null ||
                            !((LocationImpl) m.location()).info.getTypeInfo().packageName().startsWith("java."))
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
}
