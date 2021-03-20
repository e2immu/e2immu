/*
 * e2immu: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.e2immu.analyser.parserfailing;

import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.FieldInfo;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.expression.Negation;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVisitor;
import org.e2immu.analyser.visitor.TypeAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestExampleManualEventuallyE1Container extends CommonTestRunner {

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        String name = d.methodInfo().name;
        int iteration = d.iteration();

        if ("addIfGreater".equals(name)) {
            if (iteration > 0) {
                List<Expression> preconditions = d.methodAnalysis().getPreconditionForEventual();
                assertEquals(1, preconditions.size());
                assertEquals("this.j > 0", preconditions.get(0).toString());
                assertEquals("(-this.j) >= 0", Negation.negate(d.evaluationContext(), preconditions.get(0)).toString());
            }
        }
        if ("setNegativeJ".equals(name)) {
            if (iteration > 0) {
                assertEquals("((-this.j) >= 0 and (-j) >= 0)", d.methodAnalysis().getPrecondition().toString());
                assertEquals("[(-this.j) >= 0]", d.methodAnalysis().getPreconditionForEventual().toString());

                FieldInfo fieldJ = d.methodInfo().typeInfo.getFieldByName("j", true);
                VariableInfo tv = d.getFieldAsVariable(fieldJ);
                assert tv != null;
                Expression value = tv.getValue();
                assertEquals("j", value.toString());
                //  Expression state = tv.getStateOnAssignment();
                // TODO  assertEquals("(-this.j) >= 0", state.toString());
            }
        }
        if ("getIntegers".equals(name)) {
            if (iteration > 0) {
                VariableInfo tv = d.getReturnAsVariable();
                assertEquals(1, tv.getLinkedVariables().variables().size());
            }
            if (iteration > 1) {
                Set<Variable> variables = d.getReturnAsVariable().getLinkedVariables().variables();
                assertEquals(1, variables.size());
                int independent = d.methodAnalysis().getProperty(VariableProperty.INDEPENDENT);
                assertEquals(MultiLevel.FALSE, independent);
            }
        }
    };

    StatementAnalyserVisitor statementAnalyserVisitor = d -> {
        if ("setNegativeJ".equals(d.methodInfo().name)) {
            if ("0".equals(d.statementId())) {
                if (d.iteration() <= 1) {
                    assertEquals("(-j) >= 0", d.state().toString());
                } else {
                    assertEquals("((-this.j) >= 0 and (-j) >= 0)", d.state().toString());
                }
            }
            if ("1".equals(d.statementId()) && d.iteration() > 0) {
                assertEquals("((-this.j) >= 0 and (-j) >= 0)", d.state().toString());
            }
        }
    };

    TypeAnalyserVisitor typeAnalyserVisitor = d -> {
        if (d.iteration() > 0) {
            assertEquals(1, d.typeAnalysis().getApprovedPreconditionsE1().size());
            assertEquals("j=(-this.j) >= 0", d.typeAnalysis().getApprovedPreconditionsE1().entrySet().stream()
                    .map(Object::toString)
                    .collect(Collectors.joining(";")));
        }
        Set<ParameterizedType> implicitlyImmutable = d.typeAnalysis().getImplicitlyImmutableDataTypes();
        assertTrue(implicitlyImmutable.isEmpty());
    };

    @Test
    public void test() throws IOException {
        testClass("ExampleManualEventuallyE1Container", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .build());
    }

}
