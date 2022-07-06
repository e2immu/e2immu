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

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.expression.MethodCall;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.statement.ExpressionAsStatement;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVisitor;
import org.e2immu.analyser.visitor.TypeAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class Test_16_Modification_12 extends CommonTestRunner {

    public Test_16_Modification_12() {
        super(true);
    }

    @Test
    public void test12() throws IOException {
        final String TYPE = "org.e2immu.analyser.parser.modification.testexample.Modification_12";
        final String PARENT_CLASS_THIS = TYPE + ".ParentClass.this";
        final String PARENT_CLASS_SET = TYPE + ".ParentClass.set";

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("clear".equals(d.methodInfo().name) && "ParentClass".equals(d.methodInfo().typeInfo.simpleName)
                    && PARENT_CLASS_SET.equals(d.variableName())) {
                assertEquals(DV.TRUE_DV, d.getProperty(Property.CONTEXT_MODIFIED));
            }

            if ("clearAndLog".equals(d.methodInfo().name) && "ParentClass".equals(d.methodInfo().typeInfo.simpleName)
                    && "0".equals(d.statementId()) && d.variable() instanceof This) {
                assertEquals(PARENT_CLASS_THIS, d.variableName());
                assertEquals(DV.TRUE_DV, d.getProperty(Property.CONTEXT_MODIFIED));
            }

            if ("clearAndLog".equals(d.methodInfo().name) && "ChildClass".equals(d.methodInfo().typeInfo.simpleName)
                    && "0".equals(d.statementId())) {
                if (d.variable() instanceof This thisVar && thisVar.writeSuper) {
                    assertEquals("ParentClass", thisVar.typeInfo.simpleName);
                    assertEquals(PARENT_CLASS_THIS, d.variableName());
                    assertDv(d, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    // we have to wait for clearAndLog in ParentClass, which is analysed AFTER this one
                }
            }

            if ("clear".equals(d.methodInfo().name) && "InnerOfChild".equals(d.methodInfo().typeInfo.simpleName)) {
                if (d.variable() instanceof This thisVar && thisVar.writeSuper) {
                    assertEquals("ChildClass", thisVar.explicitlyWriteType.simpleName);
                    assertEquals("ParentClass", thisVar.typeInfo.simpleName);
                    assertEquals(PARENT_CLASS_THIS, d.variableName());
                    assertDv(d, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("clear".equals(d.methodInfo().name) && "InnerOfChild".equals(d.methodInfo().typeInfo.simpleName)) {
                Expression scope = ((MethodCall) ((ExpressionAsStatement) d.statementAnalysis().statement()).expression).object;
                VariableExpression variableExpression = (VariableExpression) scope;
                This t = (This) variableExpression.variable();
                assertNotNull(t.explicitlyWriteType);
                assertTrue(t.writeSuper);
            }
            // we make sure that super.clearAndLog refers to the method in ParentClass
            if ("clearAndLog".equals(d.methodInfo().name) && "ChildClass".equals(d.methodInfo().typeInfo.simpleName)
                    && "0".equals(d.statementId())) {
                if (d.statementAnalysis().statement() instanceof ExpressionAsStatement expressionAsStatement) {
                    Expression expression = expressionAsStatement.expression;
                    if (expression instanceof MethodCall methodCall) {
                        assertEquals("org.e2immu.analyser.parser.modification.testexample.Modification_12.ParentClass.clearAndLog()",
                                methodCall.methodInfo.fullyQualifiedName);
                    } else fail();
                } else fail();
                assertTrue(d.statementAnalysis().stateData().preconditionIsFinal());
                assertTrue(d.statementAnalysis().stateData().getPrecondition().isEmpty());
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            String name = d.methodInfo().name;
            if ("clear".equals(name) && "ParentClass".equals(d.methodInfo().typeInfo.simpleName)) {
                assertEquals(DV.TRUE_DV, d.methodAnalysis().getProperty(Property.MODIFIED_METHOD));
            }
            if ("clearAndAdd".equals(name) && "ChildClass".equals(d.methodInfo().typeInfo.simpleName)) {
                assertDv(d, DV.TRUE_DV, Property.MODIFIED_METHOD);
            }
            if ("clear".equals(name) && "InnerOfChild".equals(d.methodInfo().typeInfo.simpleName)) {
                if (d.iteration() > 0) {
                    assertEquals(DV.TRUE_DV, d.getThisAsVariable().getProperty(Property.CONTEXT_MODIFIED));
                }
                assertTrue(d.getThisAsVariable().isRead());
                assertDv(d, DV.TRUE_DV, Property.MODIFIED_METHOD);
            }
            if ("clearAndLog".equals(name) && "ParentClass".equals(d.methodInfo().typeInfo.simpleName)) {
                assertTrue(d.methodAnalysis().getPrecondition().isEmpty());
            }
            if ("clearAndLog".equals(name) && "ChildClass".equals(d.methodInfo().typeInfo.simpleName)) {
                if (d.iteration() > 0) assertTrue(d.methodAnalysis().getPrecondition().isEmpty());
            }
        };


        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            TypeInfo typeInfo = d.typeInfo();
            if ("ParentClass".equals(typeInfo.simpleName)) {
                assertEquals("Modification_12", typeInfo.packageNameOrEnclosingType.getRight().simpleName);
            }
            if ("ChildClass".equals(typeInfo.simpleName)) {
                assertEquals("Modification_12", typeInfo.packageNameOrEnclosingType.getRight().simpleName);
                assertDv(d, 1, MultiLevel.EFFECTIVELY_E1IMMUTABLE_DV, Property.PARTIAL_IMMUTABLE);
                assertDv(d, 2, MultiLevel.EFFECTIVELY_E1IMMUTABLE_DV, Property.IMMUTABLE);
            }
            if ("InnerOfChild".equals(typeInfo.simpleName)) {
                assertEquals("ChildClass", typeInfo.packageNameOrEnclosingType.getRight().simpleName);
                assertDv(d, MultiLevel.EFFECTIVELY_E1IMMUTABLE_DV, Property.PARTIAL_IMMUTABLE);
                assertDv(d, 2, MultiLevel.EFFECTIVELY_E1IMMUTABLE_DV, Property.IMMUTABLE);
            }
            if ("ModifiedThis".equals(typeInfo.simpleName)) {
                assertEquals("org.e2immu.analyser.parser.failing.testexample", typeInfo.packageNameOrEnclosingType.getLeft());
            }
        };

        testClass("Modification_12", 0, 0, new DebugConfiguration.Builder()
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }
}
