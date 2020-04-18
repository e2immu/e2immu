
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

    @BeforeClass
    public static void beforeClass() {
        org.e2immu.analyser.util.Logger.configure(Level.INFO);
        org.e2immu.analyser.util.Logger.activate(
                BYTECODE_INSPECTOR,
                INSPECT,
                RESOLVE,
               // LAMBDA,
               // METHOD_CALL,

                STATIC_METHOD_CALLS,

                ANALYSER,
                CONSTANT,
                NOT_NULL,
                VARIABLE_PROPERTIES,
                LINKED_VARIABLES,
                INDEPENDENT,
                MODIFY_CONTENT,
                E2IMMUTABLE,
                ANNOTATION_EXPRESSION,
                CONTAINER,
                VALUE_CLASS,
                SIDE_EFFECT,
                UTILITY_CLASS,
                NULL_NOT_ALLOWED,
                NOT_MODIFIED
        );
    }

    @Test
    public void test() throws IOException {
        // parsing the annotatedAPI files needs them being backed up by .class files, so we'll add the Java
        // test runner's classpath to ours
        Configuration configuration = new Configuration.Builder()
                .setInputConfiguration(new InputConfiguration.Builder()
                        .addSources("src/test/java")
                        .addRestrictSourceToPackages("org.e2immu.analyser.testexample.withannotatedapi.SimpleNotModifiedChecks")
                        .addClassPath(InputConfiguration.DEFAULT_CLASSPATH)
                        .addClassPath(Input.JAR_WITH_PATH_PREFIX + "com/google/common/collect")
                        .addClassPath(Input.JAR_WITH_PATH_PREFIX + "org/junit")
                        .addClassPath(Input.JAR_WITH_PATH_PREFIX + "org/slf4j")
                        .addClassPath(Input.JAR_WITH_PATH_PREFIX + "ch/qos/logback/core/spi")
                        .build())
                .build();
        // important: we do not need to include
        //                       //  .addClassPath(Input.JAR_WITH_PATH_PREFIX + "org/hamcrest")
        // in the classpath; as no actual methods of hamcrest are being parsed!

        BasicConfigurator basicConfigurator;
        Parser parser = new Parser(configuration);
        List<SortedType> types = parser.run();
        //Assert.assertTrue(10 <= types.size());
        for (SortedType sortedType : types) {
            LOGGER.info("Stream:\n{}", sortedType.typeInfo.stream());
        }
        for (Message message : parser.getMessages()) {
            LOGGER.info(message.toString());
        }
        Assert.assertTrue(parser.getMessages().stream().noneMatch(m -> m.severity == Message.Severity.ERROR));
    }

}
