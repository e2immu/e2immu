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

package org.e2immu.analyser.parser.modification;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.parser.modification.testexample.Modification_0A;
import org.e2immu.analyser.parser.modification.testexample.Modification_0B;
import org.e2immu.analyser.visitor.EvaluationResultVisitor;
import org.e2immu.analyser.visitor.FieldAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.TypeMapVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Set;

import static org.e2immu.analyser.parser.VisitorTestSupport.IterationInfo.it;
import static org.e2immu.analyser.parser.VisitorTestSupport.IterationInfo.it0;
import static org.junit.jupiter.api.Assertions.*;

public class Test_16_Modification_0 extends CommonTestRunner {

    public Test_16_Modification_0() {
        super(true);
    }

    TypeMapVisitor typeMapVisitor = d -> {
        TypeInfo set = d.typeMap().get(Set.class);
        MethodInfo add = set.findUniqueMethod("add", 1);
        MethodAnalysis addMa = d.getMethodAnalysis(add);
        assertEquals(DV.TRUE_DV, addMa.getProperty(Property.MODIFIED_METHOD));
        ParameterAnalysis addPa0 = addMa.getParameterAnalyses().get(0);
        assertEquals(MultiLevel.INDEPENDENT_HC_DV, addPa0.getProperty(Property.INDEPENDENT));

        MethodInfo addAll = set.findUniqueMethod("addAll", 1);
        assertEquals(DV.TRUE_DV, d.getMethodAnalysis(addAll).getProperty(Property.MODIFIED_METHOD));
        ParameterInfo first = addAll.methodInspection.get().getParameters().get(0);
        assertEquals(DV.FALSE_DV, d.getParameterAnalysis(first).getProperty(Property.MODIFIED_VARIABLE));

        MethodInfo size = set.findUniqueMethod("size", 0);
        assertEquals(DV.FALSE_DV, d.getMethodAnalysis(size).getProperty(Property.MODIFIED_METHOD));

        TypeInfo hashSet = d.typeMap().get(Set.class);
        assertEquals(MultiLevel.CONTAINER_DV, d.getTypeAnalysis(hashSet).getProperty(Property.CONTAINER));

        TypeInfo sb = d.typeMap().get(StringBuilder.class);
        assertEquals(MultiLevel.MUTABLE_DV, d.getTypeAnalysis(sb).getProperty(Property.IMMUTABLE));
    };

    // String is immutable
    @Test
    public void test_0A() throws IOException {

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("add".equals(d.methodInfo().name)) {
                String expected = d.iteration() == 0 ? "<m:add>" : "instance 0 type boolean";
                assertEquals(expected, d.evaluationResult().value().toString());
                ChangeData cd = d.findValueChangeByToString("set");
                if (d.iteration() == 0) {
                    assertNull(cd.value());
                } else {
                    assertEquals("instance 0 type Set<String>/*this.size()>=1&&this.contains(v)*/",
                            cd.value().toString());
                }
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("add".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "set".equals(fr.fieldInfo().name)) {
                    assertTrue(d.variableInfoContainer().hasEvaluation()
                               && !d.variableInfoContainer().hasMerge());
                    assertTrue(d.variableInfo().isRead());
                    String expectValue = d.iteration() == 0 ? "<f:set>"
                            : "instance 0 type Set<String>/*this.size()>=1&&this.contains(v)*/";
                    assertEquals(expectValue, d.currentValue().toString());
                    assertDv(d, 1, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    assertLinked(d, it(0, "this:2"));

                    // separate test on VE:
                    LinkedVariables lvSet = new VariableExpression(d.variableInfo().getIdentifier(), d.variable())
                            .evaluate(d.context(), ForwardEvaluationInfo.DEFAULT)
                            .linkedVariablesOfExpression();

                    assertEquals("this.set:0,this:2", lvSet.toString());
                    // end separate test
                }
                // important: the link from set ->2-> this is unidirectional, so this is not linked to set.
                if (d.variable() instanceof This) {
                    assertLinked(d, it(0, ""));
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("set".equals(d.fieldInfo().name)) {
                assertDv(d, 1, DV.TRUE_DV, Property.MODIFIED_OUTSIDE_METHOD);

                Expression e = d.fieldAnalysis().getValue();
                assertEquals("instance type HashSet<String>", e.toString());

                assertLinked(d, d.fieldAnalysis().getLinkedVariables(), it(0, ""));
            }
        };

        testClass("Modification_0A", 0, 0, new DebugConfiguration.Builder()
                .addTypeMapVisitor(typeMapVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    // T is immutable_hc
    @Test
    public void test_0B() throws IOException {

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("add".equals(d.methodInfo().name)) {
                ChangeData cd = d.findValueChangeByToString(Modification_0B.class.getCanonicalName() + ".add(T):0:v");
                assertLinked(d, cd.linkedVariables(), it0("this.set:4,this:-1"), it(1, "this.set:4,this:4"));
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("add".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo pi && "v".equals(pi.name)) {
                    assertLinked(d, it0("this.set:-1,this:-1"), it(1, "this.set:4,this:4"));
                }
                if (d.variable() instanceof FieldReference fr && "set".equals(fr.fieldInfo().name)) {
                    String expectValue = d.iteration() == 0 ? "<f:set>"
                            : "instance 0 type Set<T>/*this.size()>=1&&this.contains(v)*/";
                    assertEquals(expectValue, d.currentValue().toString());
                    assertDv(d, 1, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    assertLinked(d, it(0, "this:2,v:4"));
                }
                // important: the link from set ->2-> this is unidirectional!
                if (d.variable() instanceof This) {
                    assertLinked(d, it(0, ""));
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("set".equals(d.fieldInfo().name)) {
                assertDv(d, 1, DV.TRUE_DV, Property.MODIFIED_OUTSIDE_METHOD);

                Expression e = d.fieldAnalysis().getValue();
                assertEquals("instance type HashSet<T>", e.toString());
                assertLinked(d, d.fieldAnalysis().getLinkedVariables(), it(0, "v:4"));
            }
        };


        testClass("Modification_0B", 0, 0, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addTypeMapVisitor(typeMapVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    // StringBuilder is mutable
    // after adding 'v' to 'set', 'v' should be linked to '->2->set', and transitively to '->2->this'
    // note that 'set' should not be linked to 'v': modifications to set do not imply modifications to v
    //
    @Test
    public void test_0C() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {

            if ("add".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo pi && "v".equals(pi.name)) {
                    assertLinked(d, it0("this.set:-1,this:-1"), it(1, "this.set:4,this:4"));
                }
                if (d.variable() instanceof FieldReference fr && "set".equals(fr.fieldInfo().name)) {
                    String expectValue = d.iteration() == 0 ? "<f:set>"
                            : "instance 0 type Set<StringBuilder>/*this.size()>=1&&this.contains(v)*/";
                    assertEquals(expectValue, d.currentValue().toString());
                    assertDv(d, 1, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    assertLinked(d, it(0, "this:2,v:4"));
                }
                if (d.variable() instanceof This) {
                    assertLinked(d, it(0, ""));
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("set".equals(d.fieldInfo().name)) {
                assertDv(d, 1, DV.TRUE_DV, Property.MODIFIED_OUTSIDE_METHOD);

                Expression e = d.fieldAnalysis().getValue();
                assertEquals("instance type HashSet<StringBuilder>", e.toString());
                assertLinked(d, d.fieldAnalysis().getLinkedVariables(), it(0, "v:4"));
            }
        };


        testClass("Modification_0C", 0, 0, new DebugConfiguration.Builder()
                .addTypeMapVisitor(typeMapVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }
}
