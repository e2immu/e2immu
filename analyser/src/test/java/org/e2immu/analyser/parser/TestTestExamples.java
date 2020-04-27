
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
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.annotation.NotModified;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Map;

import static org.e2immu.analyser.util.Logger.LogTarget.*;

public class TestTestExamples {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestTestExamples.class);

    @BeforeClass
    public static void beforeClass() {
        org.e2immu.analyser.util.Logger.configure(Level.INFO);
        org.e2immu.analyser.util.Logger.activate(ANALYSER, INSPECT, RESOLVE,

                //LAMBDA,
                //METHOD_CALL,

                VARIABLE_PROPERTIES,
                DELAYED,

                FINAL,
                LINKED_VARIABLES,
                INDEPENDENT,
                MODIFY_CONTENT,
                E2IMMUTABLE,
                ANNOTATION_EXPRESSION,
                CONSTANT,
                CONTAINER,
                E1IMMUTABLE,
                SIDE_EFFECT,
                UTILITY_CLASS,
                NULL_NOT_ALLOWED,
                NOT_NULL,
                NOT_MODIFIED);
    }

    @Test
    public void testEvaluateConstants() throws IOException {
        goTest("EvaluateConstants", 2);
    }

    @Test
    public void testEvaluationErrors() throws IOException {
        goTest("EvaluationErrors", 2);
    }

    @Test
    public void testFieldResolution() throws IOException {
        goTest("FieldResolution");
    }

    @Test
    public void testFinalChecks() throws IOException {
        goTest("FinalChecks");
    }

    @Test
    public void testIfStatementChecks() throws IOException {
        goTest("IfStatementChecks");
    }


    @Test
    public void testLoopStatementChecks() throws IOException {
        goTest("LoopStatementChecks");
    }


    @Test
    public void testMethodMustBeStatic() throws IOException {
        goTest("MethodMustBeStatic");
    }

    @Test
    public void testMethodReferences() throws IOException {
        goTest("MethodReferences");
    }

    @Test
    public void testModifyParameterChecks() throws IOException {
        goTest("ModifyParameterChecks", 2);
    }

    @Test
    public void testStaticSideEffectsOnlyChecks() throws IOException {
        goTest("StaticSideEffectsOnlyChecks");
    }

    @Test
    public void testSwitchStatementChecks() throws IOException {
        goTest("SwitchStatementChecks", 4);
    }

    @Test
    public void testTryStatementChecks() throws IOException {
        goTest("TryStatementChecks", 1);
    }

    @Test
    public void testTypeParameters() throws IOException {
        goTest("TypeParameters");
    }

    @Test
    public void testUtilityClassChecks() throws IOException {
        goTest("UtilityClassChecks");
    }

    @NotModified
    private void goTest(String fileName) throws IOException {
        goTest(fileName, 0);
    }

    @NotModified
    private void goTest(String typeName, long countError) throws IOException {
        Parser parser = new Parser();
        URL url = new File("src/test/java/org/e2immu/analyser/testexample/" + typeName + ".java").toURI().toURL();
        TypeInfo typeInfo = parser.getTypeContext().typeStore.getOrCreate("org.e2immu.analyser.testexample." + typeName);
        List<SortedType> types = parser.parseJavaFiles(Map.of(typeInfo, url));
        for (SortedType sortedType : types) {
            LOGGER.info("Stream:\n{}", sortedType.typeInfo.stream());
        }
        for (Message message : parser.getMessages()) {
            LOGGER.info(message.toString());
        }
        Assert.assertEquals(countError, parser.getMessages().stream().filter(m -> m.severity == Message.Severity.ERROR).count());
    }

}
