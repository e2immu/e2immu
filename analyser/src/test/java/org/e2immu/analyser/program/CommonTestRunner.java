
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

package org.e2immu.analyser.program;

import ch.qos.logback.classic.Level;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.output.Formatter;
import org.e2immu.analyser.output.FormattingOptions;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.parser.Input;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Parser;
import org.e2immu.analyser.parser.VisitorTestSupport;
import org.e2immu.analyser.resolver.SortedType;
import org.junit.jupiter.api.BeforeAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.config.AnalyserProgram.Step.ALL;
import static org.e2immu.analyser.util.Logger.LogTarget.*;
import static org.junit.jupiter.api.Assertions.*;

public abstract class CommonTestRunner extends VisitorTestSupport {
    private static final Logger LOGGER = LoggerFactory.getLogger(CommonTestRunner.class);
    public static final String ORG_E2IMMU_ANALYSER_TESTEXAMPLE = "org.e2immu.analyser.parser.basics.testexample";
    public static final String DEFAULT_ANNOTATED_API_DIRS = "../annotatedAPIs/src/main/java";
    public static final String JDK_16 = "/Library/Java/JavaVirtualMachines/adoptopenjdk-16.jdk/Contents/Home";

    @BeforeAll
    public static void beforeClass() {
        org.e2immu.analyser.util.Logger.configure(Level.INFO);
        org.e2immu.analyser.util.Logger.activate();
    }

    protected TypeContext testClass(String className,
                                    int errorsToExpect,
                                    int warningsToExpect,
                                    AnalyserProgram analyserProgram,
                                    DebugConfiguration debugConfiguration) throws IOException {
        AnnotatedAPIConfiguration.Builder builder = new AnnotatedAPIConfiguration.Builder();
        builder.addAnnotatedAPISourceDirs(DEFAULT_ANNOTATED_API_DIRS);

        return testClass(List.of(className), List.of(), errorsToExpect, warningsToExpect, debugConfiguration,
                new AnalyserConfiguration.Builder().setAnalyserProgram(analyserProgram).build(),
                builder.build());
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
                .addClassPath(InputConfiguration.DEFAULT_CLASSPATH)
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

    private TypeContext execute(Configuration configuration, int errorsToExpect, int warningsToExpect) throws IOException {
        configuration.initializeLoggers();
        Parser parser = new Parser(configuration);
        List<SortedType> types = parser.run().sourceSortedTypes();

        if (!mustSee.isEmpty()) {
            mustSee.forEach((label, iteration) ->
                    LOGGER.error("MustSee: {} has only reached iteration {}", label, iteration));
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
        messages.stream()
                .filter(message -> message.message().severity != Message.Severity.INFO)
                .sorted(Message::SORT)
                .forEach(message -> LOGGER.info(message.toString()));
        assertEquals(errorsToExpect, (int) messages.stream()
                .filter(m -> m.message().severity == Message.Severity.ERROR).count(), "ERRORS: ");
        assertEquals(warningsToExpect, (int) messages.stream()
                .filter(m -> m.message().severity == Message.Severity.WARN).count(), "WARNINGS: ");
        return parser.getTypeContext();
    }
}
