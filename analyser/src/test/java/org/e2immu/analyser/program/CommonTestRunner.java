
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

import org.e2immu.analyser.config.*;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.log.LogTarget;
import org.e2immu.analyser.output.Formatter;
import org.e2immu.analyser.output.FormattingOptions;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.parser.Input;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Parser;
import org.e2immu.analyser.parser.VisitorTestSupport;
import org.e2immu.analyser.resolver.SortedTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public abstract class CommonTestRunner extends VisitorTestSupport {
    private static final Logger LOGGER = LoggerFactory.getLogger(CommonTestRunner.class);
    public static final String ORG_E2IMMU_ANALYSER_TESTEXAMPLE = "org.e2immu.analyser.parser.basics.testexample";
    public static final String DEFAULT_ANNOTATED_API_DIRS = "../annotatedAPIs/src/main/java";
    public static final String JDK_16 = "/Library/Java/JavaVirtualMachines/adoptopenjdk-16.jdk/Contents/Home";

    protected TypeContext testClass(String className,
                                    int errorsToExpect,
                                    int warningsToExpect,
                                    DebugConfiguration debugConfiguration) throws IOException {
        AnnotatedAPIConfiguration.Builder builder = new AnnotatedAPIConfiguration.Builder();
        builder.addAnnotatedAPISourceDirs(DEFAULT_ANNOTATED_API_DIRS);
        builder.addReadAnnotatedAPIPackages("org.e2immu.annotatedapi.java", "org.e2immu.annotatedapi.log");
        return testClass(List.of(className), List.of(), errorsToExpect, warningsToExpect, debugConfiguration,
                new AnalyserConfiguration.Builder().build(),
                builder.build());
    }

    private TypeContext testClass(List<String> classNames,
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
                .addDebugLogTargets(LogTarget.ANALYSIS, LogTarget.COMPUTING_ANALYSERS)
                .setInputConfiguration(inputConfigurationBuilder.build())
                .build();
        return execute(configuration, errorsToExpect, warningsToExpect);
    }

    private TypeContext execute(Configuration configuration, int errorsToExpect, int warningsToExpect) throws IOException {
        configuration.initializeLoggers();
        Parser parser = new Parser(configuration);
        SortedTypes types = parser.run().sourceSortedTypes();

        if (!mustSee.isEmpty()) {
            mustSee.forEach((label, iteration) ->
                    LOGGER.error("MustSee: {} has only reached iteration {}", label, iteration));
            assertEquals(0, mustSee.size());
        }

        types.primaryTypeStream().forEach(primaryType -> {
            OutputBuilder outputBuilder = primaryType.output();
            Formatter formatter = new Formatter(FormattingOptions.DEFAULT);
            LOGGER.info("Stream:\n{}\n", formatter.write(outputBuilder));
        });

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
