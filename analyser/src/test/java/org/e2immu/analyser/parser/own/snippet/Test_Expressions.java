
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

package org.e2immu.analyser.parser.own.snippet;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analysis.impl.TypeAnalysisImpl;
import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.expression.InlinedMethod;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.model.variable.VariableNature;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.e2immu.analyser.analyser.Property.*;
import static org.junit.jupiter.api.Assertions.*;

public class Test_Expressions extends CommonTestRunner {

    public Test_Expressions() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("recursivelyCollectTerms".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    assertEquals("expression instanceof Sum", d.evaluationResult().value().toString());
                }
                if ("2".equals(d.statementId())) {
                    String expected = d.iteration() == 0
                            ? "<null-check>&&<instanceOf:ConstantExpression<?>>&&<null-check>&&expression instanceof Product"
                            : d.iteration() < 62 ? "<null-check>&&<null-check>&&expression/*(Product)*/.lhs instanceof ConstantExpression<?>&&expression instanceof Product&&null!=expression/*(Product)*/.lhs"
                            : "expression/*(Product)*/.lhs instanceof ConstantExpression<?>&&expression/*(Product)*/.rhs instanceof MethodCall&&expression instanceof Product&&null!=expression/*(Product)*/.lhs&&null!=expression/*(Product)*/.rhs&&null!=expression/*(Product)*/.rhs/*(MethodCall)*/";
                    assertEquals(expected, d.evaluationResult().value().toString());
                }
                if ("4".equals(d.statementId())) {
                    String expected = d.iteration() < 72 ? "<null-check>" : "expression instanceof MethodCall";
                    assertEquals(expected, d.evaluationResult().value().toString());
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("recursivelyCollectTerms".equals(d.methodInfo().name)) {
                if ("4".equals(d.statementId())) {
                    String expected = d.iteration() < 72 ? "<null-check>" : "expression instanceof MethodCall";
                    assertEquals(expected, d.statementAnalysis().stateData().valueOfExpression.get().toString());
                }
                if ("6".equals(d.statementId())) {
                    assertEquals(d.iteration() >= 72, d.statementAnalysis().stateData().conditionManagerForNextStatementStatus().isDone());
                }
                if ("6.0.2".equals(d.statementId())) {
                    assertEquals("modified in context=true:1, not null in context=not_null:5, read=true:1",
                            d.statementAnalysis().propertiesFromSubAnalysers()
                                    .filter(e -> e.getKey() instanceof ParameterInfo pi && "terms".equals(pi.name))
                                    .map(e -> e.getValue().sortedToString()).findFirst().orElse(""));
                }
            }
            if ("accept3".equals(d.methodInfo().name)) {
                if ("5".equals(d.statementId())) {
                    assertEquals("Precondition[expression=true, causes=[]]",
                            d.statementAnalysis().stateData().getPreconditionFromMethodCalls().toString());
                }
            }
        };
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("recursivelyCollectTerms".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo pi && "expression".equals(pi.name)) {
                    if ("3".equals(d.statementId())) {
                        String expected = d.iteration() < 62 ? "<p:expression>" : "instance type Expression/*@Identity*/";
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 62, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
                    }
                    if ("4".equals(d.statementId())) {
                        String expected = d.iteration() < 72 ? "<p:expression>" : "instance type Expression/*@Identity*/";
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 72, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
                    }
                }
                if (d.variable() instanceof ParameterInfo pi && "terms".equals(pi.name)) {
                    if ("6.0.2".equals(d.statementId())) {
                        // has to travel from sub-analyser
                        assertDv(d, DV.TRUE_DV, CONTEXT_MODIFIED);
                    }
                }
                if (d.variable() instanceof FieldReference fr && "lhs".equals(fr.fieldInfo.name)) {
                    assertNotNull(fr.scopeVariable);
                    if ("scope-product:2".equals(fr.scopeVariable.toString())) {
                        if ("6".equals(d.statementId())) {
                            assertDv(d, MultiLevel.NULLABLE_DV, CONTEXT_NOT_NULL);
                        }
                        if ("7".equals(d.statementId())) {
                            assertDv(d, MultiLevel.NULLABLE_DV, CONTEXT_NOT_NULL);
                        }
                    } else if ("sum".equals(fr.scopeVariable.toString())) {
                        if ("0.0.0".equals(d.statementId())) {
                            assertDv(d, 0, MultiLevel.NULLABLE_DV, CONTEXT_NOT_NULL);
                        }
                        if ("0.0.1".equals(d.statementId())) {
                            assertDv(d, 0, MultiLevel.NULLABLE_DV, CONTEXT_NOT_NULL);
                        }
                    } else if ("scope-sum:0".equals(fr.scopeVariable.toString())) {
                        assertDv(d, 62, MultiLevel.NULLABLE_DV, CONTEXT_NOT_NULL);
                    } else if ("product".equals(fr.scopeVariable.toString())) {
                        assertDv(d, MultiLevel.NULLABLE_DV, CONTEXT_NOT_NULL);
                    } else fail("Have " + fr.scopeVariable);
                }
                if (d.variable() instanceof ReturnVariable) {
                    if ("7".equals(d.statementId())) {
                        assertCurrentValue(d, 72, "expression instanceof Negation?Expressions_0.recursivelyCollectTerms(expression/*(Negation)*/.expression,new ArrayList<>()/*0==this.size()*/):(Expressions_0.recursivelyCollectTerms(expression/*(Sum)*/.lhs,terms)||expression instanceof ConstantExpression<?>||expression instanceof MethodCall)&&(Expressions_0.recursivelyCollectTerms(expression/*(Sum)*/.rhs,terms)||expression instanceof ConstantExpression<?>||expression instanceof MethodCall)&&(expression instanceof ConstantExpression<?>||expression instanceof MethodCall||expression instanceof Sum)");
                    }
                }
                if (d.variable() instanceof FieldReference fr && "rhs".equals(fr.fieldInfo.name)) {
                    if ("product".equals(fr.scope.toString())) {
                        if ("2".equals(d.statementId())) {
                            String linked = d.iteration() < 62
                                    ? "ce:-1,expression:-1,oneVariableRhs:-1,product.lhs:-1,product:-1,scope-sum:0.lhs:-1,scope-sum:0.rhs:-1,scope-sum:0:-1"
                                    : "ce:2,expression:2,oneVariableRhs:1,product.lhs:2,product:2,scope-sum:0.lhs:2,scope-sum:0.rhs:2,scope-sum:0:2";
                            assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                        }
                    }
                }
                if ("product".equals(d.variableName())) {
                    assertTrue(d.variableInfoContainer().variableNature() instanceof VariableNature.Pattern);
                    assertTrue(d.statementId().startsWith("2"));
                    if ("2".equals(d.statementId())) {
                        String linked = d.iteration() < 62 ? "ce:-1,expression:-1,oneVariableRhs:-1,product.lhs:-1,product.rhs:-1,scope-sum:0.lhs:-1,scope-sum:0.rhs:-1,scope-sum:0:-1"
                                : "ce:2,expression:1,oneVariableRhs:2,product.lhs:2,product.rhs:2,scope-sum:0.lhs:2,scope-sum:0.rhs:2,scope-sum:0:2";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    }
                }
            }
            if ("accept1".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "perComponent".equals(fr.fieldInfo.name)) {
                    if ("0.0.1.0.0".equals(d.statementId())) {
                        assertDv(d, 20, DV.FALSE_DV, CONTEXT_MODIFIED);
                    }
                    if ("0.0.1".equals(d.statementId())) {
                        assertDv(d, 75, DV.FALSE_DV, CONTEXT_MODIFIED);
                    }
                    if ("0.0.2".equals(d.statementId())) {
                        assertDv(d, 76, DV.FALSE_DV, CONTEXT_MODIFIED);
                    }
                    if ("0.0.2.0.0".equals(d.statementId())) {
                        assertDv(d, 20, DV.FALSE_DV, CONTEXT_MODIFIED);
                    }
                    if ("0.0.2.0.1".equals(d.statementId())) {
                        assertDv(d, 20, DV.FALSE_DV, CONTEXT_MODIFIED);
                    }
                    if ("0".equals(d.statementId())) {
                        assertDv(d, 76, DV.FALSE_DV, CONTEXT_MODIFIED);
                    }
                }
                if (d.variable() instanceof ParameterInfo pi && "expression".equals(pi.name)) {
                    if ("1".equals(d.statementId())) {
                        assertDv(d, MultiLevel.NULLABLE_DV, CONTEXT_NOT_NULL);
                    }
                }
            }
            if ("accept6".equals(d.methodInfo().name)) {
                if ("xEquals".equals(d.variableName())) {
                    if ("01".equals(d.statementId())) {
                        assertCurrentValue(d, 48, "expressionsInX.stream().filter(/*inline test*/e instanceof Equals&&``eq`.lhs` instanceof ConstantExpression<?>&&!(``eq`.lhs` instanceof NullConstant)&&null!=e&&null!=``eq`.lhs`).map(`e/*(Equals)*/.lhs/*(ConstantExpression<?>)*/.t`/*(Number)*/.doubleValue()).findFirst().orElse(null)");
                    }
                    if ("03".equals(d.statementId())) {
                        assertCurrentValue(d, 48, "expressionsInX.stream().filter(/*inline test*/e instanceof Equals&&``eq`.lhs` instanceof ConstantExpression<?>&&!(``eq`.lhs` instanceof NullConstant)&&null!=e&&null!=``eq`.lhs`).map(`e/*(Equals)*/.lhs/*(ConstantExpression<?>)*/.t`/*(Number)*/.doubleValue()).findFirst().orElse(null)");
                    }
                }
                if ("inequality".equals(d.variableName())) {
                    if ("04.0.0".equals(d.statementId())) {
                        assertCurrentValue(d, 53, "new LinearInequalityInOneVariable(b,y,a*xEquals+c,allowEquals)");
                    }
                }
                if ("intervalX".equals(d.variableName())) {
                    if ("06".equals(d.statementId())) {
                        String expected = d.iteration() < 76 ? "<s:Interval>" : "Interval.extractInterval1(expressionsInX)";
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
            }
            if ("evaluate".equals(d.methodInfo().name)) {
                if ("and".equals(d.variableName())) {
                    if ("0.0.0".equals(d.statementId())) {
                        String expected = d.iteration() <= 1 ? "<vp:expression:container@Record_And>/*(And)*/" : "expression/*(And)*/";
                        assertEquals(expected, d.currentValue().toString());
                        String expectLv = d.iteration() <= 1 ? "expression:-1" : "expression:1";
                        assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());
                        assertDv(d, 2, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                        assertDv(d, 2, MultiLevel.NULLABLE_DV, CONTEXT_NOT_NULL);
                    }
                }
            }
            if ("accept".equals(d.methodInfo().name)) {
                assertEquals("$4", d.methodInfo().typeInfo.simpleName);
                if (d.variable() instanceof ParameterInfo pi && "terms".equals(pi.name)) {
                    assertDv(d, DV.TRUE_DV, CONTEXT_MODIFIED);
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("extractOneVariable".equals(d.methodInfo().name)) {
                String expected = d.iteration() <= 2 ? "<m:extractOneVariable>"
                        : "/*inline extractOneVariable*/expression instanceof MethodCall&&null!=expression?expression/*(MethodCall)*/:null";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
                assertDv(d.p(0), 1, MultiLevel.NULLABLE_DV, Property.NOT_NULL_PARAMETER);
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
            if ("accept5".equals(d.methodInfo().name)) {
                assertDv(d, 1, DV.FALSE_DV, Property.MODIFIED_METHOD);
                String expected = d.iteration() == 0 ? "<m:accept5>"
                        : "/*inline accept5*/allowEquals?a*px+py*b+c>=0:-1+a*px+py*b+c>=0";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
                if (d.iteration() >= 1) {
                    if (d.methodAnalysis().getSingleReturnValue() instanceof InlinedMethod im) {
                        assertEquals("a, allowEquals, b, c, px, py, this", im.variablesOfExpressionSorted());
                    } else fail();
                }
            }
            if ("extractInterval1".equals(d.methodInfo().name)) {
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
                String expected = d.iteration() == 0 ? "Precondition[expression=<precondition>, causes=[]]"
                        : "Precondition[expression=true, causes=[]]";
                assertEquals(expected, d.methodAnalysis().getPreconditionForEventual().toString());
                String srv = d.iteration() <= 75 ? "<m:extractInterval1>" : "<undetermined return value>";
                assertEquals(srv, d.methodAnalysis().getSingleReturnValue().toString());
            }
            if ("extractInterval2".equals(d.methodInfo().name)) {
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
                String expected = d.iteration() == 0 ? "Precondition[expression=<precondition>, causes=[]]"
                        : "Precondition[expression=true, causes=[]]";
                assertEquals(expected, d.methodAnalysis().getPreconditionForEventual().toString());
            }
            if ("extractEquals".equals(d.methodInfo().name)) {
                assertDv(d, 4, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
            if ("accept2".equals(d.methodInfo().name)) {
                assertDv(d, 1, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
            if ("accept3".equals(d.methodInfo().name)) {
                assertDv(d, 51, DV.FALSE_DV, Property.MODIFIED_METHOD);
                String expected = d.iteration() < 76 ? "<m:accept3>" : "<undetermined return value>";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
            }
            if ("accept4".equals(d.methodInfo().name)) {
                assertDv(d, 2, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
            if ("interval".equals(d.methodInfo().name)) {
                assertDv(d, 1, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
            if ("LinearInequalityInOneVariable".equals(d.methodInfo().name)) {
                assertDv(d.p(0), DV.FALSE_DV, Property.MODIFIED_VARIABLE);
                assertDv(d.p(1), 1, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
                assertDv(d.p(2), DV.FALSE_DV, Property.MODIFIED_VARIABLE);
                assertDv(d.p(3), DV.FALSE_DV, Property.MODIFIED_VARIABLE);
            }
            if ("accept6".equals(d.methodInfo().name)) {
                assertDv(d, 15, DV.FALSE_DV, Property.MODIFIED_METHOD);
                String expected = d.iteration() < 76 ? "<m:accept6>" : "/*inline accept6*/instance type boolean";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
            }
            if ("accept1".equals(d.methodInfo().name)) {
                assertDv(d, 76, DV.FALSE_DV, Property.MODIFIED_METHOD);
                String expected = d.iteration() < 76 ? "<m:accept1>" : "<undetermined return value>";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
            }
            if ("test".equals(d.methodInfo().name) && "$6".equals(d.methodInfo().typeInfo.simpleName)) {
                String expected = d.iteration() <= 2 ? "<m:test>"
                        : "/*inline test*/e instanceof Equals&&e/*(Equals)*/.lhs instanceof ConstantExpression<?>&&!(e/*(Equals)*/.lhs instanceof NullConstant)&&null!=e&&null!=e/*(Equals)*/.lhs";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("a".equals(d.fieldInfo().name) && "LinearInequalityInTwoVariables".equals(d.fieldInfo().owner.simpleName)) {
                assertEquals("a", d.fieldAnalysis().getValue().toString());
            }
            if ("x".equals(d.fieldInfo().name) && "LinearInequalityInTwoVariables".equals(d.fieldInfo().owner.simpleName)) {
                assertEquals("x", d.fieldAnalysis().getValue().toString());
                assertDv(d, DV.TRUE_DV, FINAL);
                assertDv(d, 2, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, EXTERNAL_IMMUTABLE);
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("Interval".equals(d.typeInfo().simpleName)) {
                assertDv(d, 3, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
            }
            if ("Precondition_11".equals(d.typeInfo().simpleName)) {
                TypeAnalysisImpl.Builder b = (TypeAnalysisImpl.Builder) d.typeAnalysis();
                assertEquals(0L, b.nonModifiedCountForMethodCallCycle.stream().count());
            }
            if ("OneVariable".equals(d.typeInfo().simpleName)) {
                assertDv(d, 1, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, IMMUTABLE);
            }
            if ("Variable".equals(d.typeInfo().simpleName)) {
                assertDv(d, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, IMMUTABLE);
            }
            if ("LinearInequalityInOneVariable".equals(d.typeInfo().simpleName)) {
                assertDv(d, 51, MultiLevel.INDEPENDENT_1_DV, INDEPENDENT);
                assertDv(d, 52, MultiLevel.CONTAINER_DV, CONTAINER);
            }
            if ("Term".equals(d.typeInfo().simpleName)) {
                assertDv(d, 71, MultiLevel.EFFECTIVELY_E2IMMUTABLE_DV, IMMUTABLE);
            }
        };


        testClass("Expressions_0", 2, 16,
                new DebugConfiguration.Builder()
                     //   .addEvaluationResultVisitor(evaluationResultVisitor)
                     //   .addStatementAnalyserVisitor(statementAnalyserVisitor)
                     //   .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                        .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                        .build(),
                new AnalyserConfiguration.Builder()
                        .setComputeFieldAnalyserAcrossAllMethods(true)
                        .setForceAlphabeticAnalysisInPrimaryType(true)
                        .build());
    }
}
