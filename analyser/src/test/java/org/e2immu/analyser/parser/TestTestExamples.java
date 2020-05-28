
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
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static org.e2immu.analyser.util.Logger.LogTarget.*;

public class TestTestExamples extends CommonTestRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestTestExamples.class);

    public TestTestExamples() {
        super(false);
    }

    @BeforeClass
    public static void beforeClass() {
        org.e2immu.analyser.util.Logger.configure(Level.INFO);
        org.e2immu.analyser.util.Logger.activate(ANALYSER, INSPECT, RESOLVE,

                LAMBDA,
                METHOD_CALL,

                VARIABLE_PROPERTIES,
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
                NOT_MODIFIED);
    }

    @Test
    public void testFieldResolution() throws IOException {
        testClass("FieldResolution", 0);
    }

    @Test
    public void testIfStatementChecks() throws IOException {
        testClass("IfStatementChecks", 0);
    }

    @Test
    public void testInnerClass() throws IOException {
        testClass("InnerClass", 3);
    }

    @Test
    public void testModifyParameterChecks() throws IOException {
        testClass("ModifyParameterChecks", 2);
    }
}
