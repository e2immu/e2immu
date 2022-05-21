
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
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.stream.Collectors;

import static org.e2immu.analyser.analyser.Property.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class Test_Expressions extends CommonTestRunner {

    public static final String X_EQUALS = "expressionsInX.stream().filter(/*inline test*/e instanceof Equals&&``eq`.lhs` instanceof ConstantExpression<?>&&!(``eq`.lhs` instanceof NullConstant)&&null!=e&&null!=``eq`.lhs`).map(`e/*(Equals)*/.lhs/*(ConstantExpression<?>)*/.t`/*(Number)*/.doubleValue()).findFirst().orElse(null)";

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
                    String expected = switch (d.iteration()) {
                        case 0 -> "<instanceOf:ConstantExpression<?>>&&<null-check>&&expression instanceof Product";
                        case 1 -> "<null-check>&&expression/*(Product)*/.lhs instanceof ConstantExpression<?>&&expression instanceof Product&&null!=expression/*(Product)*/.lhs";
                        case 2 -> "<simplification>&&expression/*(Product)*/.lhs instanceof ConstantExpression<?>&&expression instanceof Product&&null!=expression/*(Product)*/.lhs";
                        default -> "expression/*(Product)*/.lhs instanceof ConstantExpression<?>&&expression/*(Product)*/.rhs instanceof MethodCall&&expression instanceof Product&&null!=expression/*(Product)*/.lhs&&null!=expression/*(Product)*/.rhs&&null!=expression/*(Product)*/.rhs/*(MethodCall)*/";
                    };
                    assertEquals(expected, d.evaluationResult().value().toString());
                }
                if ("4".equals(d.statementId())) {
                    String expected = switch (d.iteration()) {
                        case 0, 1, 2 -> "<null-check>";
                        default -> "expression instanceof MethodCall";
                    };
                    assertEquals(expected, d.evaluationResult().value().toString());
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("recursivelyCollectTerms".equals(d.methodInfo().name)) {
                if ("4".equals(d.statementId())) {
                    String expected = d.iteration() <= 2 ? "<null-check>" : "expression instanceof MethodCall";
                    assertEquals(expected, d.statementAnalysis().stateData().valueOfExpression.get().toString());
                }
                if ("6".equals(d.statementId())) {
                    String expected = switch (d.iteration()) {
                        case 0 -> "CM{state=!<null-check>&&!<instanceOf:ConstantExpression<?>>&&!<instanceOf:Negation>&&!(expression instanceof Sum)&&(!<instanceOf:ConstantExpression<?>>||!<null-check>||!(expression instanceof Product));parent=CM{}}";
                        case 1 -> "CM{state=!<null-check>&&!<instanceOf:ConstantExpression<?>>&&!<instanceOf:Negation>&&!(expression instanceof Sum)&&(!<null-check>||!(scope-product:2.lhs instanceof ConstantExpression<?>)||!(expression instanceof Product)||null==scope-product:2.lhs);parent=CM{}}";
                        case 2 -> "CM{state=!<null-check>&&!<instanceOf:ConstantExpression<?>>&&!<instanceOf:Negation>&&!(expression instanceof Sum)&&(!<simplification>||!(scope-product:2.lhs instanceof ConstantExpression<?>)||!(expression instanceof Product)||null==scope-product:2.lhs);parent=CM{}}";
                        default -> "CM{state=!(expression instanceof ConstantExpression<?>)&&!(expression instanceof MethodCall)&&!(expression instanceof Negation)&&!(expression instanceof Sum)&&(!(scope-product:2.lhs instanceof ConstantExpression<?>)||!(scope-product:2.rhs instanceof MethodCall)||!(expression instanceof Product)||null==scope-product:2.lhs||null==scope-product:2.rhs||null==scope-product:2.rhs/*(MethodCall)*/);parent=CM{}}";
                    };
                    assertEquals(expected, d.statementAnalysis().stateData().getConditionManagerForNextStatement().toString());
                    assertEquals(d.iteration() >= 3, d.statementAnalysis().stateData().conditionManagerForNextStatementStatus().isDone());
                }
            }
        };
        int BIG = 30;
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("recursivelyCollectTerms".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo pi && "expression".equals(pi.name)) {
                    if ("3".equals(d.statementId())) {
                        String expected = d.iteration() <= 2 ? "<p:expression>" : "instance type Expression/*@Identity*/";
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 3, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
                    }
                    if ("4".equals(d.statementId())) {
                        String expected = d.iteration() <= 2 ? "<p:expression>" : "instance type Expression/*@Identity*/";
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 3, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
                    }
                }
            }
            if ("accept1".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "perComponent".equals(fr.fieldInfo.name)) {
                    if ("0.0.1.0.0".equals(d.statementId())) {
                        assertDv(d, DV.FALSE_DV, CONTEXT_MODIFIED);
                    }
                    if ("0.0.1".equals(d.statementId())) {
                        assertDv(d, 4, DV.FALSE_DV, CONTEXT_MODIFIED);
                    }
                    if ("0.0.2".equals(d.statementId())) {
                        assertDv(d, 5, DV.FALSE_DV, CONTEXT_MODIFIED);
                    }
                    if ("0.0.2.0.0".equals(d.statementId())) {
                        assertDv(d, 4, DV.FALSE_DV, CONTEXT_MODIFIED);
                    }
                    if ("0.0.2.0.1".equals(d.statementId())) {
                        assertDv(d, 4, DV.FALSE_DV, CONTEXT_MODIFIED);
                    }
                    if ("0".equals(d.statementId())) {
                        assertDv(d, 5, DV.FALSE_DV, CONTEXT_MODIFIED);
                    }
                }
            }
            if ("accept6".equals(d.methodInfo().name)) {
                if ("xEquals".equals(d.variableName())) {
                    if ("01".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0, 1, 2, 3, 4, 5, 6 -> "<s:Double>";
                           // case 6 -> "<m:extractEquals>";
                            default -> X_EQUALS;
                        };
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("03".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0, 1, 2, 3, 4, 5, 6 -> "<s:Double>";
                            default -> X_EQUALS;
                        };
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
                if ("inequality".equals(d.variableName())) {
                    if ("04.0.0".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0 -> "<s:LinearInequalityInOneVariable>";
                            case 1, 2, 3, 4, 5, 6, 7, 8 -> "<new:LinearInequalityInOneVariable>";
                            default -> "new LinearInequalityInOneVariable(b,y,a*xEquals+c,allowEquals)";
                        };
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
                if ("intervalX".equals(d.variableName())) {
                    if ("06".equals(d.statementId())) {
                        String expected = d.iteration() <= 8 ? "<s:Interval>"
                                : "2==expressionsInX.size()?null==Interval.extractInterval2(expressionsInX.get(0))||null==Interval.extractInterval2(expressionsInX.get(1))?null:Double.isFinite(`Interval.extractInterval2(expressions.get(0)).left`)&&Infinity==`Interval.extractInterval2(expressions.get(0)).right`&&2==expressionsInX.size()?new Interval(`Interval.extractInterval2(expressions.get(0)).left`,`Interval.extractInterval2(expressions.get(0)).leftIncluded`,`Interval.extractInterval2(expressions.get(1)).right`,`Interval.extractInterval2(expressions.get(1)).rightIncluded`):([expressionsInX,expressionsInY,instance type boolean])?new Interval(`Interval.extractInterval2(expressions.get(1)).left`,`Interval.extractInterval2(expressions.get(1)).leftIncluded`,`Interval.extractInterval2(expressions.get(0)).right`,`Interval.extractInterval2(expressions.get(1)).rightIncluded`):<return value>:1==expressionsInX.size()?Interval.extractInterval2(expressionsInX.get(0)):null";
                        assertEquals(expected, d.currentValue().toString());
                        if (d.iteration() >= 9) {
                            assertEquals("expressionsInX, expressionsInX, expressionsInX, expressionsInX, expressionsInX, expressionsInY, expressionsInX, expressionsInX",
                                    d.currentValue().variables(true)
                                            .stream().map(Variable::simpleName).collect(Collectors.joining(", ")));
                        }
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
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
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
                assertDv(d, DV.FALSE_DV, Property.TEMP_MODIFIED_METHOD);
                assertDv(d, BIG, DV.FALSE_DV, Property.MODIFIED_METHOD);
                String expected = d.iteration() <= 1 ? "Precondition[expression=<precondition>, causes=[]]"
                        : "Precondition[expression=true, causes=[]]";
                assertEquals(expected, d.methodAnalysis().getPreconditionForEventual().toString());
                String srv = d.iteration() <= 3 ? "<m:extractInterval1>"
                        : "/*inline extractInterval1*/2==expressions.size()?null==Interval.extractInterval2(expressions.get(0))||null==Interval.extractInterval2(expressions.get(1))?null:Double.isFinite(`Interval.extractInterval2(expressions.get(0)).left`)&&Infinity==`Interval.extractInterval2(expressions.get(0)).right`&&2==expressions.size()?new Interval(`Interval.extractInterval2(expressions.get(0)).left`,`Interval.extractInterval2(expressions.get(0)).leftIncluded`,`Interval.extractInterval2(expressions.get(1)).right`,`Interval.extractInterval2(expressions.get(1)).rightIncluded`):Double.isFinite(`Interval.extractInterval2(expressions.get(0)).right`)&&-Infinity==`left`&&2==expressions.size()&&null!=Interval.extractInterval2(expressions.get(0))&&null!=Interval.extractInterval2(expressions.get(1))&&(!Double.isFinite(`Interval.extractInterval2(expressions.get(0)).left`)||Infinity!=`Interval.extractInterval2(expressions.get(0)).right`)?new Interval(`Interval.extractInterval2(expressions.get(1)).left`,`Interval.extractInterval2(expressions.get(1)).leftIncluded`,`Interval.extractInterval2(expressions.get(0)).right`,`Interval.extractInterval2(expressions.get(1)).rightIncluded`):<return value>:1==expressions.size()?Interval.extractInterval2(expressions.get(0)):null";
                assertEquals(srv, d.methodAnalysis().getSingleReturnValue().toString());
                if (d.iteration() >= 4) {
                    if (d.methodAnalysis().getSingleReturnValue() instanceof InlinedMethod im) {
                        assertEquals("expressions", im.variablesOfExpressionSorted());
                    } else fail();
                }
            }
            if ("extractInterval2".equals(d.methodInfo().name)) {
                assertDv(d, 2, DV.FALSE_DV, Property.MODIFIED_METHOD);
                String expected = d.iteration() <= 1 ? "Precondition[expression=<precondition>, causes=[]]"
                        : "Precondition[expression=true, causes=[]]";
                assertEquals(expected, d.methodAnalysis().getPreconditionForEventual().toString());
            }
            if ("extractEquals".equals(d.methodInfo().name)) {
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
            if ("accept2".equals(d.methodInfo().name)) {
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
            if ("accept3".equals(d.methodInfo().name)) {
                assertDv(d, 2, DV.FALSE_DV, Property.TEMP_MODIFIED_METHOD);
                assertDv(d, BIG, DV.FALSE_DV, Property.MODIFIED_METHOD);
                String expected = d.iteration() <= 7 ? "<m:accept3>" :
                        "/*inline accept3*/([<return value>,expressionsInV,instance type boolean])?null==expressionsInV.stream().filter(/*inline test*/e instanceof Equals&&``eq`.lhs` instanceof ConstantExpression<?>&&!(``eq`.lhs` instanceof NullConstant)&&null!=e&&null!=``eq`.lhs`).map(`e/*(Equals)*/.lhs/*(ConstantExpression<?>)*/.t`/*(Number)*/.doubleValue()).findFirst().orElse(null)?null||expressionsInV.stream().allMatch(/*inline test*/(VariableExpression.class).isAssignableFrom(``eq`.rhs`.getClass())&&e instanceof Negation&&``eq`.lhs` instanceof ConstantExpression<?>&&``n`.expression` instanceof Equals&&null!=e&&null!=``n`.expression`&&null!=``eq`.lhs`):`allowEquals`?`a`*expressionsInV.stream().filter(/*inline test*/e instanceof Equals&&``eq`.lhs` instanceof ConstantExpression<?>&&!(``eq`.lhs` instanceof NullConstant)&&null!=e&&null!=``eq`.lhs`).map(`e/*(Equals)*/.lhs/*(ConstantExpression<?>)*/.t`/*(Number)*/.doubleValue()).findFirst().orElse(null)+`b`>=0:-1+`a`*expressionsInV.stream().filter(/*inline test*/e instanceof Equals&&``eq`.lhs` instanceof ConstantExpression<?>&&!(``eq`.lhs` instanceof NullConstant)&&null!=e&&null!=``eq`.lhs`).map(`e/*(Equals)*/.lhs/*(ConstantExpression<?>)*/.t`/*(Number)*/.doubleValue()).findFirst().orElse(null)+`b`>=0:instance type boolean";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
                if (d.iteration() >= 8) {
                    if (d.methodAnalysis().getSingleReturnValue() instanceof InlinedMethod im) {
                        assertEquals("expressionsInV", im.variablesOfExpressionSorted());
                    } else fail();
                }
            }
            if ("accept4".equals(d.methodInfo().name)) {
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
            if ("interval".equals(d.methodInfo().name)) {
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
            if ("LinearInequalityInOneVariable".equals(d.methodInfo().name)) {
                assertDv(d.p(0), DV.FALSE_DV, Property.MODIFIED_VARIABLE);
                assertDv(d.p(1), 1, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
                assertDv(d.p(2), DV.FALSE_DV, Property.MODIFIED_VARIABLE);
                assertDv(d.p(3), DV.FALSE_DV, Property.MODIFIED_VARIABLE);
            }
            if ("accept6".equals(d.methodInfo().name)) {
                assertDv(d, 7, DV.FALSE_DV, Property.TEMP_MODIFIED_METHOD);
                if (d.iteration() == 9) {
                    assertEquals("", d.getProperty(Property.TEMP_MODIFIED_METHOD).causesOfDelay().toString());
                }
                assertDv(d, BIG, DV.FALSE_DV, Property.MODIFIED_METHOD);
                String expected = d.iteration() <= 8 ? "<m:accept6>" : "/*inline accept6*/[a,b,this,instance type boolean]";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
                if (d.iteration() >= 9) {
                    if (d.methodAnalysis().getSingleReturnValue() instanceof InlinedMethod im) {
                        assertEquals("a, b, this", im.variablesOfExpressionSorted());
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
                assertDv(d, BIG, MultiLevel.EFFECTIVELY_E1IMMUTABLE_DV, Property.IMMUTABLE);
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("Interval".equals(d.typeInfo().simpleName)) {
                assertDv(d, 1, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
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
                assertDv(d, 2, MultiLevel.INDEPENDENT_1_DV, INDEPENDENT);
                assertDv(d, BIG, MultiLevel.CONTAINER_DV, CONTAINER);
            }
            if ("Term".equals(d.typeInfo().simpleName)) {
                assertDv(d, 2, MultiLevel.EFFECTIVELY_E1IMMUTABLE_DV, IMMUTABLE);
            }
        };


        testClass("Expressions_0", 0, 16,
                new DebugConfiguration.Builder()
                   //     .addEvaluationResultVisitor(evaluationResultVisitor)
                    //    .addStatementAnalyserVisitor(statementAnalyserVisitor)
                     //   .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                     //   .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                   //     .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                    //    .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                        .build(),
                new AnalyserConfiguration.Builder()
                        .setComputeFieldAnalyserAcrossAllMethods(true)
                        .setForceAlphabeticAnalysisInPrimaryType(true)
                        .build());
    }
}