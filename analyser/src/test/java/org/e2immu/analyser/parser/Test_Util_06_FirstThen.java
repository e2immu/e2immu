
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
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class Test_Util_06_FirstThen extends CommonTestRunner {

    public Test_Util_06_FirstThen() {
        super(true);
    }

    StatementAnalyserVisitor statementAnalyserVisitor = d -> {
        if ("equals".equals(d.methodInfo().name) && "2".equals(d.statementId())) {
            Assert.assertEquals("null!=o&&o.getClass()==this.getClass()&&o!=this", d.state().toString());
        }
        if ("set".equals(d.methodInfo().name)) {
            if ("1.0.0.0.0".equals(d.statementId())) {
                String expectCondition = d.iteration() == 0 ? "null==<f:first>" : "null==org.e2immu.analyser.util.FirstThen.first$0";
                Assert.assertEquals(expectCondition, d.condition().toString());
            }
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
                Assert.assertEquals(Level.FALSE, d.getProperty(VariableProperty.MODIFIED_VARIABLE));
            }
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        String name = d.methodInfo().name;

        if ("set".equals(name)) {
            if (d.iteration() == 0) {
                Assert.assertNull(d.methodAnalysis().getPrecondition());
            } else {
                Assert.assertEquals("null!=first", d.methodAnalysis().getPrecondition().toString());
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

            int expectModified = d.iteration() <= 1 ? Level.DELAY : Level.FALSE;
            Assert.assertEquals(expectModified, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
        }

        if ("equals".equals(name)) {
            int expectModified = d.iteration() <= 1 ? Level.DELAY : Level.FALSE;
            Assert.assertEquals(expectModified, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));

            ParameterAnalysis o = d.parameterAnalyses().get(0);
            Assert.assertEquals(Level.FALSE, o.getProperty(VariableProperty.MODIFIED_VARIABLE));
        }
    };

    TypeAnalyserVisitor typeAnalyserVisitor = d -> {
        Assert.assertEquals("Type param S,Type param T", d.typeAnalysis().getImplicitlyImmutableDataTypes()
                .stream().map(Object::toString).sorted().collect(Collectors.joining(",")));
        Assert.assertEquals(d.iteration() > 0, d.typeAnalysis().approvedPreconditionsIsFrozen(false));
    };

    TypeMapVisitor typeMapVisitor = typeMap -> {
        TypeInfo objects = typeMap.get(Objects.class);
        MethodInfo hash = objects.typeInspection.get().methods().stream().filter(m -> m.name.equals("hash")).findFirst().orElseThrow();
        ParameterInfo objectsParam = hash.methodInspection.get().getParameters().get(0);
        Assert.assertEquals(Level.FALSE, objectsParam.parameterAnalysis.get().getProperty(VariableProperty.MODIFIED_VARIABLE));
    };

    @Test
    public void test() throws IOException {
        testUtilClass(List.of("FirstThen"), 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addTypeMapVisitor(typeMapVisitor)
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

}
