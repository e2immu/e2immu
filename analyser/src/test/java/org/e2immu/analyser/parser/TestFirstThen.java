
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

import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

public class TestFirstThen extends CommonTestRunner {

    StatementAnalyserVisitor statementAnalyserVisitor = d -> {
        if ("equals".equals(d.methodInfo().name) && "2".equals(d.statementId())) {
            Assert.assertEquals("(not (null == o) and this.getClass() == o.getClass() and not (this == o))", d.state().toString());
        }
        if ("set".equals(d.methodInfo().name) && "1.0.0".equals(d.statementId())) {
            if (d.iteration() == 0) {
                Assert.assertSame(EmptyExpression.NO_VALUE, d.state()); // delay
            } else {
                Assert.assertEquals("not (null == this.first)", d.state().toString());
                Assert.assertEquals("not (null == this.first)", d.statementAnalysis().stateData.getPrecondition().toString());
            }
        }
        if ("set".equals(d.methodInfo().name) && d.iteration() == 0 && "1.0.0".compareTo(d.statementId()) <= 0) {
            Assert.assertSame("StatementId: " + d.statementId(), EmptyExpression.NO_VALUE, d.state()); // delay
        }
    };

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if ("getFirst".equals(d.methodInfo().name) && "FirstThen.this.first".equals(d.variableName())) {
            if ("0".equals(d.statementId())) {
                Assert.assertTrue(d.variableInfo().isRead());
            }
            if ("1".equals(d.statementId())) {
                Assert.assertTrue(d.variableInfo().isRead());
            }
        }
        if ("equals".equals(d.methodInfo().name) && "o".equals(d.variableName())) {
            if ("2".equals(d.statementId())) {
                Assert.assertEquals(Level.FALSE, d.getProperty(VariableProperty.MODIFIED));
            }
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        String name = d.methodInfo().name;

        if ("set".equals(name)) {
            if (d.iteration() == 0) {
                Assert.assertNull(d.methodAnalysis().getPrecondition());
            } else {
                Assert.assertEquals("not (null == this.first)", d.methodAnalysis().getPrecondition().toString());
            }
        }
        if ("getFirst".equals(name)) {
            FieldInfo first = d.methodInfo().typeInfo.getFieldByName("first", true);
            VariableInfo vi = d.getFieldAsVariable(first);
            assert vi != null;
            Assert.assertTrue(vi.isRead());
        }
        if ("hashCode".equals(name)) {
            FieldInfo first = d.methodInfo().typeInfo.getFieldByName("first", true);
            VariableInfo vi = d.getFieldAsVariable(first);
            assert vi != null;
            Assert.assertTrue(vi.isRead());
            Assert.assertEquals(Level.DELAY, vi.getProperty(VariableProperty.METHOD_CALLED));

            if (d.iteration() > 0) {
                Assert.assertEquals(Level.FALSE, d.methodAnalysis().getProperty(VariableProperty.MODIFIED));
            }
        }
        if ("equals".equals(name)) {
            ParameterInfo o = d.methodInfo().methodInspection.get().getParameters().get(0);
            Assert.assertEquals(Level.FALSE, o.parameterAnalysis.get().getProperty(VariableProperty.MODIFIED));
        }
    };

    TypeMapVisitor typeMapVisitor = typeMap -> {
        TypeInfo objects = typeMap.get(Objects.class);
        MethodInfo hash = objects.typeInspection.get().methods().stream().filter(m -> m.name.equals("hash")).findFirst().orElseThrow();
        ParameterInfo objectsParam = hash.methodInspection.get().getParameters().get(0);
        Assert.assertEquals(Level.FALSE, objectsParam.parameterAnalysis.get().getProperty(VariableProperty.MODIFIED));
    };

    @Test
    public void test() throws IOException {
        testUtilClass(List.of("FirstThen"), 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addTypeMapVisitor(typeMapVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

}
