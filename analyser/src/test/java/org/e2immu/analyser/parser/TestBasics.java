
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

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.value.StringValue;
import org.e2immu.analyser.model.value.UnknownValue;
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

public class TestBasics {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestBasics.class);

    @BeforeClass
    public static void beforeClass() {
        org.e2immu.analyser.util.Logger.configure(ch.qos.logback.classic.Level.INFO);
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

    FieldAnalyserVisitor beforeFieldAnalyserVisitor = (iteration, fieldInfo) -> {
        FieldAnalysis fieldAnalysis = fieldInfo.fieldAnalysis.get();
        if ("explicitlyFinal".equals(fieldInfo.name)) {
            if (iteration == 0) {
                Assert.assertEquals(Level.TRUE, fieldAnalysis.getProperty(VariableProperty.FINAL));
                Assert.assertFalse(fieldAnalysis.effectivelyFinalValue.isSet());

                return;
            }
            if (iteration == 1) {
                Assert.assertEquals(Level.TRUE, fieldAnalysis.getProperty(VariableProperty.FINAL));
                Assert.assertEquals("abc", fieldAnalysis.effectivelyFinalValue.get().toString());
                Assert.assertEquals(Level.TRUE, fieldAnalysis.getProperty(VariableProperty.NOT_MODIFIED));
                Assert.assertEquals(Level.DELAY, fieldAnalysis.getProperty(VariableProperty.NOT_NULL));
                return;
            }
            if (iteration == 2) {
                Assert.assertEquals(Level.TRUE, fieldAnalysis.getProperty(VariableProperty.NOT_NULL));
                return;
            }
        }
        Assert.fail();
    };

    FieldAnalyserVisitor afterFieldAnalyserVisitor = (iteration, fieldInfo) -> {
        FieldAnalysis fieldAnalysis = fieldInfo.fieldAnalysis.get();

        if ("explicitlyFinal".equals(fieldInfo.name)) {
            if (iteration == 0) {
                Assert.assertEquals(Level.TRUE, fieldAnalysis.getProperty(VariableProperty.FINAL));
                Assert.assertEquals("abc", fieldAnalysis.effectivelyFinalValue.get().toString());
                Assert.assertEquals(Level.TRUE, fieldAnalysis.getProperty(VariableProperty.NOT_MODIFIED));
                Assert.assertEquals(Level.DELAY, fieldAnalysis.getProperty(VariableProperty.NOT_NULL));
                return;
            }
            if (iteration == 1 || iteration == 2) {
                Assert.assertEquals(Level.TRUE, fieldAnalysis.getProperty(VariableProperty.NOT_NULL));
                return;
            }
        }
        Assert.fail();
    };

    StatementAnalyserVariableVisitor statementAnalyserVisitor = (iteration, methodInfo, statementId,
                                                                 variableName, variable, currentValue, properties) -> {
        if (methodInfo.name.equals("getExplicitlyFinal")
                && "0".equals(statementId)
                && "Basics.this.explicitlyFinal".equals(variableName)) {
            if (iteration == 0) {
                LOGGER.info("Properties after 1 iteration are {}", properties);
                Assert.assertEquals(Level.TRUE, (int) properties.get(VariableProperty.READ));
                Assert.assertNull(properties.get(VariableProperty.ASSIGNED));
                Assert.assertNull(properties.get(VariableProperty.NOT_NULL));
                Assert.assertEquals(Level.TRUE, (int) properties.get(VariableProperty.FINAL));

                Assert.assertEquals(new StringValue("abc"), currentValue);
                return;
            }
            if (iteration == 1 || iteration == 2) {
                LOGGER.info("Properties after 2 iterations are {}", properties);
                Assert.assertEquals(Level.TRUE, (int) properties.get(VariableProperty.READ));
                Assert.assertNull(properties.get(VariableProperty.ASSIGNED));

                Assert.assertEquals(Level.TRUE, (int) properties.get(VariableProperty.NOT_NULL));
                Assert.assertEquals(Level.TRUE, (int) properties.get(VariableProperty.FINAL));
                return;
            }
        }
        Assert.fail();
    };

    @Test
    public void testBasics() throws IOException {
        String typeName = "Basics";
        Configuration configuration = new Configuration.Builder()
                .setDebugConfiguration(new DebugConfiguration.Builder()
                        .addBeforeFieldAnalyserVisitor(beforeFieldAnalyserVisitor)
                        .addAfterFieldAnalyserVisitor(afterFieldAnalyserVisitor)
                        .addStatementAnalyserVariableVisitor(statementAnalyserVisitor)
                        .build())
                .build();
        Parser parser = new Parser(configuration);
        URL url = new File("src/test/java/org/e2immu/analyser/testexample/" + typeName + ".java").toURI().toURL();
        TypeInfo typeInfo = parser.getTypeContext().typeStore.getOrCreate("org.e2immu.analyser.testexample." + typeName);
        List<SortedType> types = parser.parseJavaFiles(Map.of(typeInfo, url));
        for (SortedType sortedType : types) {
            if (sortedType.typeInfo.typeInspection.get().packageNameOrEnclosingType.isLeft()) {
                LOGGER.info("\n\nStream:\n{}", sortedType.typeInfo.stream());
            }
        }
        for (Message message : parser.getMessages()) {
            LOGGER.info(message.toString());
        }
        Assert.assertEquals(0, parser.getMessages().stream().filter(m -> m.severity == Message.Severity.ERROR).count());
    }

}
