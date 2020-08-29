
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
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.FieldAnalyserVisitor;
import org.e2immu.analyser.config.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.config.TypeContextVisitor;
import org.e2immu.analyser.model.FieldAnalysis;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.value.StringValue;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class TestBasics extends CommonTestRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestBasics.class);

    public TestBasics() {
        super(false);
    }

    FieldAnalyserVisitor afterFieldAnalyserVisitor = (iteration, fieldInfo) -> {
        FieldAnalysis fieldAnalysis = fieldInfo.fieldAnalysis.get();
        if ("explicitlyFinal".equals(fieldInfo.name)) {
            if (iteration == 0) {
                Assert.assertEquals(Level.TRUE, fieldAnalysis.getProperty(VariableProperty.FINAL));
                Assert.assertEquals("abc", fieldAnalysis.effectivelyFinalValue.get().toString());
                Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, fieldAnalysis.effectivelyFinalValue.get()
                        .getPropertyOutsideContext(VariableProperty.NOT_NULL));
            }
            if (iteration > 0) {
                Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, fieldAnalysis.getProperty(VariableProperty.NOT_NULL));
                Assert.assertEquals(MultiLevel.EFFECTIVELY_E2IMMUTABLE, fieldAnalysis.getProperty(VariableProperty.IMMUTABLE));
            }
        }
    };

    StatementAnalyserVariableVisitor statementAnalyserVisitor = d -> {
        if (d.methodInfo.name.equals("getExplicitlyFinal")
                && "0".equals(d.statementId)
                && "Basics.this.explicitlyFinal".equals(d.variableName)) {
            if (d.iteration == 0) {
                LOGGER.info("Properties after 1 iteration are {}", d.properties);
                Assert.assertEquals(Level.TRUE, (int) d.properties.get(VariableProperty.READ));
                Assert.assertNull(d.properties.get(VariableProperty.ASSIGNED));
                Assert.assertNull(d.properties.get(VariableProperty.NOT_NULL));
                Assert.assertEquals(new StringValue("abc"), d.currentValue);
                return;
            }
            if (d.iteration == 1 || d.iteration == 2) {
                LOGGER.info("Properties after 2 iterations are {}", d.properties);
                Assert.assertEquals(Level.TRUE, (int) d.properties.get(VariableProperty.READ));
                Assert.assertNull(d.properties.get(VariableProperty.ASSIGNED));
                Assert.assertNull(d.properties.get(VariableProperty.NOT_NULL));
                return;
            }
        }
        Assert.fail();
    };

    TypeContextVisitor typeContextVisitor = typeContext -> {
        // check that the XML annotations have been read properly
        TypeInfo stringType = Primitives.PRIMITIVES.stringTypeInfo;
        Assert.assertEquals(MultiLevel.EFFECTIVELY_E2IMMUTABLE, stringType.typeAnalysis.get().getProperty(VariableProperty.IMMUTABLE));

    };

    @Test
    public void test() throws IOException {
        testClass("Basics", 0, new DebugConfiguration.Builder()
                .addAfterFieldAnalyserVisitor(afterFieldAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVisitor)
                .addTypeContextVisitor(typeContextVisitor)
                .build());
    }

}
