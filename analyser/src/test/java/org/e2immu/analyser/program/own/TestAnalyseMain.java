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

package org.e2immu.analyser.program.own;

import org.e2immu.analyser.config.*;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.parser.Input;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Parser;
import org.e2immu.analyser.visitor.StatementAnalyserVisitor;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class TestAnalyseMain {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestAnalyseMain.class);

    @Test
    public void test() throws IOException {

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("targetIsATypeParameter".equals(d.methodInfo().name)) {
                if ("4.0.2".equals(d.statementId())) {
                    assertEquals("!<m:isEmpty>", d.state().toString());
                }
            }
        };

        InputConfiguration inputConfiguration = new InputConfiguration.Builder()
                .setAlternativeJREDirectory(CommonTestRunner.JDK_16)
                .addSources("src/main/java")
                .addClassPath("jmods/java.base.jmod")
                .addClassPath("jmods/java.compiler.jmod")
                .addClassPath("jmods/java.xml.jmod") // org.w3c.dom.Document
                .addClassPath(Input.JAR_WITH_PATH_PREFIX + "org/slf4j")
                .addClassPath(Input.JAR_WITH_PATH_PREFIX + "com/github/javaparser")
                .addClassPath(Input.JAR_WITH_PATH_PREFIX + "org/objectweb/asm")
                .addClassPath(Input.JAR_WITH_PATH_PREFIX + "ch/qos/logback/classic")
                .addClassPath(Input.JAR_WITH_PATH_PREFIX + "ch/qos/logback/core")
                .addClassPath(Input.JAR_WITH_PATH_PREFIX + "org/junit/jupiter/api")
                .build();

        AnalyserConfiguration analyserConfiguration = new AnalyserConfiguration.Builder()
                .setComputeFieldAnalyserAcrossAllMethods(true)
                .setAnalyserProgram(AnalyserProgram.from(AnalyserProgram.Step.ALL))
                .build();

        DebugConfiguration debugConfiguration = new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor).build();

        // we'll encounter some tests with dollar types. For our current purpose, they're simply Java POJOs, we don't
        // want to see them as AnnotatedAPI
        AnnotatedAPIConfiguration annotatedAPIConfiguration = new AnnotatedAPIConfiguration.Builder()
                .addAnnotatedAPISourceDirs(CommonTestRunner.DEFAULT_ANNOTATED_API_DIRS)
                .build();
        Configuration configuration = new Configuration.Builder()
                .setSkipAnalysis(false)
                .setInputConfiguration(inputConfiguration)
                .setAnnotatedAPIConfiguration(annotatedAPIConfiguration)
                .setAnalyserConfiguration(analyserConfiguration)
                .setDebugConfiguration(debugConfiguration)
                //     .addDebugLogTargets("analyser")
                .build();
        configuration.initializeLoggers();
        Parser parser = new Parser(configuration);
        parser.run();

        Catalog catalog = makeCatalog(parser.getMessages());
        int ignoreResultErrors = 0;
        int potentialNullPointerErrors = 0;
        for (Map.Entry<Message.Label, Set<Message>> e : catalog.byLabel.entrySet()) {
            LOGGER.warn("---- have {} of {} ----", e.getValue().size(), e.getKey());
            e.getValue().stream().map(Object::toString).sorted().forEach(LOGGER::warn);
            if (e.getKey() == Message.Label.POTENTIAL_NULL_POINTER_EXCEPTION) {
                LOGGER.warn("++++ TOP potential np ++++");
                catalog.resultOfMethodCall
                        .entrySet().stream()
                        .sorted((e1, e2) -> e2.getValue() - e1.getValue())
                        .limit(10)
                        .forEach(ee -> LOGGER.warn("{}: {}", ee.getKey(), ee.getValue()));
                potentialNullPointerErrors += e.getValue().size();
            }

            // ignore results of method call: typically a method has not been marked @Modified
            //
            if (e.getKey() == Message.Label.IGNORING_RESULT_OF_METHOD_CALL) {
                LOGGER.warn("++++ TOP ignore results ++++");
                catalog.ignoreResults
                        .entrySet().stream()
                        .sorted((e1, e2) -> e2.getValue() - e1.getValue())
                        .limit(10)
                        .forEach(ee -> LOGGER.warn("{}: {}", ee.getKey(), ee.getValue()));
                ignoreResultErrors += e.getValue().size();
            }
        }
        LOGGER.warn("----");
        catalog.notAvailable.forEach(s -> LOGGER.warn("Not available: " + s));

        assertEquals(0, potentialNullPointerErrors);
        assertEquals(0, ignoreResultErrors);
    }

    record Catalog(Set<String> notAvailable,
                   Map<Message.Label, Set<Message>> byLabel,
                   Map<String, Integer> resultOfMethodCall,
                   Map<String, Integer> ignoreResults) {
    }

    private Catalog makeCatalog(Stream<Message> messages) {
        Catalog catalog = new Catalog(new HashSet<>(), new HashMap<>(), new HashMap<>(), new HashMap<>());
        messages.forEach(message -> {
            if (message.message() == Message.Label.TYPE_ANALYSIS_NOT_AVAILABLE) {
                catalog.notAvailable.add(message.extra());
                assertFalse(message.extra().startsWith("org.e2immu"));
            } else {
                Set<Message> set = catalog.byLabel.computeIfAbsent(message.message(), m -> new HashSet<>());
                set.add(message);
                if (message.message() == Message.Label.POTENTIAL_NULL_POINTER_EXCEPTION) {
                    catalog.resultOfMethodCall.merge(message.extra(), 1, Integer::sum);
                } else if (message.message() == Message.Label.IGNORING_RESULT_OF_METHOD_CALL) {
                    catalog.ignoreResults.merge(message.extra(), 1, Integer::sum);
                }
            }
        });
        return catalog;
    }
}
