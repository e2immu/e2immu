
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
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.stream.Collectors;

import static org.e2immu.analyser.analyser.Property.*;
import static org.junit.jupiter.api.Assertions.*;

public class Test_Expressions extends CommonTestRunner {

    public Test_Expressions() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        int IT22 = 100;
        int IT53 = 100;
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("recursivelyCollectTerms".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    assertEquals("expression instanceof Sum", d.evaluationResult().value().toString());
                }
                if ("2".equals(d.statementId())) {
                    String expected = d.iteration() == 0
                            ? "<null-check>&&<instanceOf:ConstantExpression<?>>&&<null-check>&&expression instanceof Product"
                            : d.iteration() < IT53 ? "<null-check>&&<null-check>&&expression/*(Product)*/.lhs instanceof ConstantExpression<?>&&expression instanceof Product&&null!=expression/*(Product)*/.lhs"
                            : "expression/*(Product)*/.lhs instanceof ConstantExpression<?>&&expression/*(Product)*/.rhs instanceof MethodCall&&expression instanceof Product&&null!=expression/*(Product)*/.lhs&&null!=expression/*(Product)*/.rhs";
                    assertEquals(expected, d.evaluationResult().value().toString());
                }
                if ("4".equals(d.statementId())) {
                    String expected = d.iteration() < IT53 ? "<null-check>" : "expression instanceof MethodCall";
                    assertEquals(expected, d.evaluationResult().value().toString());
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("recursivelyCollectTerms".equals(d.methodInfo().name)) {
                if ("4".equals(d.statementId())) {
                    String expected = d.iteration() < IT53 ? "<null-check>" : "expression instanceof MethodCall";
                    assertEquals(expected, d.statementAnalysis().stateData().valueOfExpression.get().toString());
                }
                if ("6".equals(d.statementId())) {
                    assertEquals(d.iteration() >= IT53, d.statementAnalysis().stateData().conditionManagerForNextStatementStatus().isDone());
                }
            }
        };
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("recursivelyCollectTerms".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo pi && "expression".equals(pi.name)) {
                    if ("3".equals(d.statementId())) {
                        String expected = d.iteration() < IT53 ? "<p:expression>" : "instance type Expression/*@Identity*/";
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, IT53, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
                    }
                    if ("4".equals(d.statementId())) {
                        String expected = d.iteration() < IT53 ? "<p:expression>" : "instance type Expression/*@Identity*/";
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, IT53, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
                    }
                }
                if (d.variable() instanceof FieldReference fr && "lhs".equals(fr.fieldInfo.name)) {
                    assertNotNull(fr.scopeVariable);
                    if ("scope-product:2".equals(fr.scopeVariable.toString())) {
                        if ("6".equals(d.statementId())) {
                            assertDv(d, MultiLevel.NULLABLE_DV, CONTEXT_NOT_NULL);
                        }
                        if ("7".equals(d.statementId())) {
                            assertDv(d, 1, MultiLevel.NULLABLE_DV, CONTEXT_NOT_NULL);
                        }
                    } else if ("sum".equals(fr.scopeVariable.toString())) {
                        if ("0.0.0".equals(d.statementId())) {
                            assertDv(d, 0, MultiLevel.NULLABLE_DV, CONTEXT_NOT_NULL);
                        }
                        if ("0.0.1".equals(d.statementId())) {
                            assertDv(d, IT53, MultiLevel.NULLABLE_DV, CONTEXT_NOT_NULL);
                        }
                    } else if ("scope-sum:0".equals(fr.scopeVariable.toString())) {
                        assertDv(d, IT53, MultiLevel.NULLABLE_DV, CONTEXT_NOT_NULL);
                    } else if ("product".equals(fr.scopeVariable.toString())) {
                        assertDv(d, MultiLevel.NULLABLE_DV, CONTEXT_NOT_NULL);
                    } else fail("Have " + fr.scopeVariable);
                }
                if (d.variable() instanceof ReturnVariable) {
                    if ("7".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0 -> "<instanceOf:Negation>?<m:recursivelyCollectTerms>:([<m:recursivelyCollectTerms>,<m:recursivelyCollectTerms>,<instanceOf:Sum>,<null-check>,<instanceOf:ConstantExpression<?>>,<null-check>,<instanceOf:ConstantExpression<?>>,<null-check>,<too complex>])";
                            default -> "<instanceOf:Negation>?<m:recursivelyCollectTerms>:(<instanceOf:ConstantExpression<?>>||<null-check>||<instanceOf:Sum>)&&(<instanceOf:ConstantExpression<?>>||<null-check>||<m:recursivelyCollectTerms>)&&(<instanceOf:ConstantExpression<?>>||<null-check>||<m:recursivelyCollectTerms>)";
                            //        default -> "";
                        };
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
            }
            if ("accept1".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "perComponent".equals(fr.fieldInfo.name)) {
                    if ("0.0.1.0.0".equals(d.statementId())) {
                        assertDv(d, 14, DV.FALSE_DV, CONTEXT_MODIFIED);
                    }
                    if ("0.0.1".equals(d.statementId())) {
                        assertDv(d, 14, DV.FALSE_DV, CONTEXT_MODIFIED);
                    }
                    if ("0.0.2".equals(d.statementId())) {
                        assertDv(d, BIG, DV.FALSE_DV, CONTEXT_MODIFIED);
                    }
                    if ("0.0.2.0.0".equals(d.statementId())) {
                        assertDv(d, IT22, DV.FALSE_DV, CONTEXT_MODIFIED);
                    }
                    if ("0.0.2.0.1".equals(d.statementId())) {
                        assertDv(d, IT22, DV.FALSE_DV, CONTEXT_MODIFIED);
                    }
                    if ("0".equals(d.statementId())) {
                        assertDv(d, IT22, DV.FALSE_DV, CONTEXT_MODIFIED);
                    }
                }
            }
            if ("accept6".equals(d.methodInfo().name)) {
                if ("xEquals".equals(d.variableName())) {
                    if ("01".equals(d.statementId())) {
                        String expected = d.iteration() < IT53 ? "<s:Double>"
                                : "expressionsInX.stream().filter(/*inline test*/e instanceof Equals&&``eq`.lhs` instanceof ConstantExpression<?>&&!(``eq`.lhs` instanceof NullConstant)&&null!=e&&null!=``eq`.lhs`).map(`e/*(Equals)*/.lhs/*(ConstantExpression<?>)*/.t`/*(Number)*/.doubleValue()).findFirst().orElse(null)";
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("03".equals(d.statementId())) {
                        String expected = d.iteration() < IT53 ? "<s:Double>"
                                : "expressionsInX.stream().filter(/*inline test*/e instanceof Equals&&``eq`.lhs` instanceof ConstantExpression<?>&&!(``eq`.lhs` instanceof NullConstant)&&null!=e&&null!=``eq`.lhs`).map(`e/*(Equals)*/.lhs/*(ConstantExpression<?>)*/.t`/*(Number)*/.doubleValue()).findFirst().orElse(null)";
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
                if ("inequality".equals(d.variableName())) {
                    if ("04.0.0".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<s:LinearInequalityInOneVariable>"
                                : d.iteration() < IT53 ? "<new:LinearInequalityInOneVariable>"
                                : "new LinearInequalityInOneVariable(b,y,a*xEquals+c,allowEquals)";
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
                if ("intervalX".equals(d.variableName())) {
                    if ("06".equals(d.statementId())) {
                        String expected = d.iteration() < IT53 ? "<s:Interval>"
                                : "2==expressionsInX.size()?null==nullable instance type Interval?null:Double.isFinite(`i1.left`)&&Infinity==`i1.right`&&2==expressionsInX.size()?new Interval(`i1.left`,`i1.leftIncluded`,`i2.right`,`i2.rightIncluded`):Double.isFinite(`i1.right`)&&!expressionsInX.stream().allMatch(/*inline test*/(VariableExpression.class).isAssignableFrom(``eq`.rhs`.getClass())&&e instanceof Negation&&``eq`.lhs` instanceof ConstantExpression<?>&&``n`.expression` instanceof Equals&&null!=e&&null!=``n`.expression`&&null!=``eq`.lhs`)&&!expressionsInY.stream().allMatch(/*inline test*/(VariableExpression.class).isAssignableFrom(``eq`.rhs`.getClass())&&e instanceof Negation&&``eq`.lhs` instanceof ConstantExpression<?>&&``n`.expression` instanceof Equals&&null!=e&&null!=``n`.expression`&&null!=``eq`.lhs`)&&null!=nullable instance type Interval&&-Infinity==`left`&&2==expressionsInX.size()&&null==expressionsInX.stream().filter(/*inline test*/e instanceof Equals&&``eq`.lhs` instanceof ConstantExpression<?>&&!(``eq`.lhs` instanceof NullConstant)&&null!=e&&null!=``eq`.lhs`).map(`e/*(Equals)*/.lhs/*(ConstantExpression<?>)*/.t`/*(Number)*/.doubleValue()).findFirst().orElse(null)&&null==expressionsInY.stream().filter(/*inline test*/e instanceof Equals&&``eq`.lhs` instanceof ConstantExpression<?>&&!(``eq`.lhs` instanceof NullConstant)&&null!=e&&null!=``eq`.lhs`).map(`e/*(Equals)*/.lhs/*(ConstantExpression<?>)*/.t`/*(Number)*/.doubleValue()).findFirst().orElse(null)&&(!Double.isFinite(`i1.left`)||Infinity!=`i1.right`)?new Interval(`i2.left`,`i2.leftIncluded`,`i1.right`,`i2.rightIncluded`):<return value>:1==expressionsInX.size()?nullable instance type Interval:null";
                        assertEquals(expected, d.currentValue().toString());
                        if (d.iteration() >= IT53) {
                            assertEquals("expressionsInX, expressionsInX, expressionsInX, e, e, expressionsInY, e, e, expressionsInX, expressionsInX, e, e, expressionsInY, e, e, expressionsInX",
                                    d.currentValue().variables(true)
                                            .stream().map(Variable::simpleName).collect(Collectors.joining(", ")));
                        }
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
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("extractOneVariable".equals(d.methodInfo().name)) {
                String expected = d.iteration() <= 1 ? "<m:extractOneVariable>"
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
                String expected = d.iteration() <= 1 ? "Precondition[expression=<precondition>, causes=[]]"
                        : "Precondition[expression=true, causes=[]]";
                assertEquals(expected, d.methodAnalysis().getPreconditionForEventual().toString());
                String srv = d.iteration() <= 14 ? "<m:extractInterval1>" : "null";
                // FIXME this is really wrong, the null is only 1 of 3 return values
                //       : "/*inline extractInterval1*/2==expressions.size()?null==nullable instance type Interval?null:Double.isFinite(`i1.left`)&&null!=nullable instance type Interval&&Infinity==`i1.right`&&2==expressions.size()?new Interval(`i1.left`,`i1.leftIncluded`,`i2.right`,`i2.rightIncluded`):Double.isFinite(`i1.right`)&&null!=nullable instance type Interval&&-Infinity==`left`&&2==expressions.size()&&(!Double.isFinite(`i1.left`)||Infinity!=`i1.right`)?new Interval(`i2.left`,`i2.leftIncluded`,`i1.right`,`i2.rightIncluded`):<return value>:1==expressions.size()?nullable instance type Interval:null";
                assertEquals(srv, d.methodAnalysis().getSingleReturnValue().toString());
                if (d.iteration() >= 15) {
                    if (d.methodAnalysis().getSingleReturnValue() instanceof InlinedMethod im) {
                        assertEquals("expressions", im.variablesOfExpressionSorted());
                    } else fail();
                }
            }
            if ("extractInterval2".equals(d.methodInfo().name)) {
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
                String expected = d.iteration() <= 1 ? "Precondition[expression=<precondition>, causes=[]]"
                        : "Precondition[expression=true, causes=[]]";
                assertEquals(expected, d.methodAnalysis().getPreconditionForEventual().toString());
            }
            if ("extractEquals".equals(d.methodInfo().name)) {
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
            if ("accept2".equals(d.methodInfo().name)) {
                assertDv(d, 1, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
            if ("accept3".equals(d.methodInfo().name)) {
                assertDv(d, 3, DV.FALSE_DV, Property.MODIFIED_METHOD);
                String expected = d.iteration() < IT53 ? "<m:accept3>" :
                        "/*inline accept3*/null!=nullable instance type Interval&&1==expressionsInV.size()?instance type boolean:null==expressionsInV.stream().filter(/*inline test*/e instanceof Equals&&``eq`.lhs` instanceof ConstantExpression<?>&&!(``eq`.lhs` instanceof NullConstant)&&null!=e&&null!=``eq`.lhs`).map(`e/*(Equals)*/.lhs/*(ConstantExpression<?>)*/.t`/*(Number)*/.doubleValue()).findFirst().orElse(null)?null||expressionsInV.stream().allMatch(/*inline test*/(VariableExpression.class).isAssignableFrom(``eq`.rhs`.getClass())&&e instanceof Negation&&``eq`.lhs` instanceof ConstantExpression<?>&&``n`.expression` instanceof Equals&&null!=e&&null!=``n`.expression`&&null!=``eq`.lhs`):`allowEquals`?expressionsInV.stream().filter(/*inline test*/e instanceof Equals&&``eq`.lhs` instanceof ConstantExpression<?>&&!(``eq`.lhs` instanceof NullConstant)&&null!=e&&null!=``eq`.lhs`).map(`e/*(Equals)*/.lhs/*(ConstantExpression<?>)*/.t`/*(Number)*/.doubleValue()).findFirst().orElse(null)*`a`+`b`>=0:-1+expressionsInV.stream().filter(/*inline test*/e instanceof Equals&&``eq`.lhs` instanceof ConstantExpression<?>&&!(``eq`.lhs` instanceof NullConstant)&&null!=e&&null!=``eq`.lhs`).map(`e/*(Equals)*/.lhs/*(ConstantExpression<?>)*/.t`/*(Number)*/.doubleValue()).findFirst().orElse(null)*`a`+`b`>=0";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
                if (d.iteration() >= IT53) {
                    if (d.methodAnalysis().getSingleReturnValue() instanceof InlinedMethod im) {
                        assertEquals("expressionsInV", im.variablesOfExpressionSorted());
                    } else fail();
                }
            }
            if ("accept4".equals(d.methodInfo().name)) {
                assertDv(d, 1, DV.FALSE_DV, Property.MODIFIED_METHOD);
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
                assertDv(d, IT53 + 1, DV.FALSE_DV, Property.TEMP_MODIFIED_METHOD);

                assertDv(d, IT22, DV.FALSE_DV, Property.MODIFIED_METHOD);
                String expected = d.iteration() < IT53 ? "<m:accept6>" : "/*inline accept6*/instance type boolean";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
                if (d.iteration() >= IT53) {
                    if (d.methodAnalysis().getSingleReturnValue() instanceof InlinedMethod im) {
                        assertEquals("", im.variablesOfExpressionSorted());
                    } else fail();
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("a".equals(d.fieldInfo().name) && "LinearInequalityInTwoVariables".equals(d.fieldInfo().owner.simpleName)) {
                assertEquals("a", d.fieldAnalysis().getValue().toString());
            }
            if ("x".equals(d.fieldInfo().name) && "LinearInequalityInTwoVariables".equals(d.fieldInfo().owner.simpleName)) {
                assertEquals("x", d.fieldAnalysis().getValue().toString());
                assertDv(d, IT53, MultiLevel.EFFECTIVELY_E1IMMUTABLE_DV, Property.IMMUTABLE);
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("Interval".equals(d.typeInfo().simpleName)) {
                assertDv(d, 2, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
            }
            if ("Precondition_11".equals(d.typeInfo().simpleName)) {
                TypeAnalysisImpl.Builder b = (TypeAnalysisImpl.Builder) d.typeAnalysis();
                assertEquals(0L, b.nonModifiedCountForMethodCallCycle.stream().count());
            }
            if ("OneVariable".equals(d.typeInfo().simpleName)) {
                assertDv(d, MultiLevel.MUTABLE_DV, IMMUTABLE);
            }
            if ("Variable".equals(d.typeInfo().simpleName)) {
                assertDv(d, MultiLevel.MUTABLE_DV, IMMUTABLE);
            }
            if ("LinearInequalityInOneVariable".equals(d.typeInfo().simpleName)) {
                assertDv(d, 3, MultiLevel.INDEPENDENT_1_DV, INDEPENDENT);
                assertDv(d, 13, MultiLevel.CONTAINER_DV, CONTAINER);
            }
            if ("Term".equals(d.typeInfo().simpleName)) {
                assertDv(d, IT53 - 2, MultiLevel.EFFECTIVELY_E2IMMUTABLE_DV, IMMUTABLE);
            }
        };


        testClass("Expressions_0", 0, 16,
                new DebugConfiguration.Builder()
               //         .addEvaluationResultVisitor(evaluationResultVisitor)
               //         .addStatementAnalyserVisitor(statementAnalyserVisitor)
                //        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                //        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                //        .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                //        .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                        .build(),
                new AnalyserConfiguration.Builder()
                        .setComputeFieldAnalyserAcrossAllMethods(true)
                        .setForceAlphabeticAnalysisInPrimaryType(true)
                        .build());
    }
}
