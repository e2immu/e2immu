
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
import org.e2immu.analyser.model.variable.VariableNature;
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
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("recursivelyCollectTerms".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    assertEquals("expression instanceof Sum", d.evaluationResult().value().toString());
                }
                if ("2".equals(d.statementId())) {
                    String expected = d.iteration() == 0
                            ? "<null-check>&&<instanceOf:ConstantExpression<?>>&&<null-check>&&expression instanceof Product"
                            : d.iteration() < 64 ? "<null-check>&&<null-check>&&expression/*(Product)*/.lhs instanceof ConstantExpression<?>&&expression instanceof Product&&null!=expression/*(Product)*/.lhs"
                            : "expression/*(Product)*/.lhs instanceof ConstantExpression<?>&&expression/*(Product)*/.rhs instanceof MethodCall&&expression instanceof Product&&null!=expression/*(Product)*/.lhs&&null!=expression/*(Product)*/.rhs&&null!=expression/*(Product)*/.rhs/*(MethodCall)*/";
                    assertEquals(expected, d.evaluationResult().value().toString());
                }
                if ("4".equals(d.statementId())) {
                    String expected = d.iteration() < BIG ? "<null-check>" : "expression instanceof MethodCall";
                    assertEquals(expected, d.evaluationResult().value().toString());
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("recursivelyCollectTerms".equals(d.methodInfo().name)) {
                if ("4".equals(d.statementId())) {
                    String expected = d.iteration() < BIG ? "<null-check>" : "expression instanceof MethodCall";
                    assertEquals(expected, d.statementAnalysis().stateData().valueOfExpression.get().toString());
                }
                if ("6".equals(d.statementId())) {
                    assertEquals(d.iteration() >= BIG, d.statementAnalysis().stateData().conditionManagerForNextStatementStatus().isDone());
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
                        String expected = d.iteration() < BIG ? "<p:expression>" : "instance type Expression/*@Identity*/";
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, BIG, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
                    }
                    if ("4".equals(d.statementId())) {
                        String expected = d.iteration() < BIG ? "<p:expression>" : "instance type Expression/*@Identity*/";
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, BIG, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
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
                        assertDv(d, 64, MultiLevel.NULLABLE_DV, CONTEXT_NOT_NULL);
                    } else if ("product".equals(fr.scopeVariable.toString())) {
                        assertDv(d, MultiLevel.NULLABLE_DV, CONTEXT_NOT_NULL);
                    } else fail("Have " + fr.scopeVariable);
                }
                if (d.variable() instanceof ReturnVariable) {
                    if ("7".equals(d.statementId())) {
                        assertCurrentValue(d, BIG, "expression instanceof Negation?Expressions_0.recursivelyCollectTerms(expression/*(Negation)*/.expression,new ArrayList<>()/*0==this.size()*/):(Expressions_0.recursivelyCollectTerms(expression/*(Sum)*/.lhs,terms)||expression instanceof ConstantExpression<?>||expression instanceof MethodCall)&&(Expressions_0.recursivelyCollectTerms(expression/*(Sum)*/.rhs,terms)||expression instanceof ConstantExpression<?>||expression instanceof MethodCall)&&(expression instanceof ConstantExpression<?>||expression instanceof MethodCall||expression instanceof Sum)");
                    }
                }
                if (d.variable() instanceof FieldReference fr && "rhs".equals(fr.fieldInfo.name)) {
                    if ("product".equals(fr.scope.toString())) {
                        if ("2".equals(d.statementId())) {
                            String linked = d.iteration() < BIG
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
                        String linked = d.iteration() < 64 ? "ce:-1,expression:-1,oneVariableRhs:-1,product.lhs:-1,product.rhs:-1,scope-sum:0.lhs:-1,scope-sum:0.rhs:-1,scope-sum:0:-1"
                                : "ce:2,expression:1,oneVariableRhs:2,product.lhs:2,product.rhs:2,scope-sum:0.lhs:2,scope-sum:0.rhs:2,scope-sum:0:2";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    }
                }
            }
            if ("accept1".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "perComponent".equals(fr.fieldInfo.name)) {
                    if ("0.0.1.0.0".equals(d.statementId())) {
                        assertDv(d, BIG, DV.FALSE_DV, CONTEXT_MODIFIED);
                    }
                    if ("0.0.1".equals(d.statementId())) {
                        assertDv(d, BIG, DV.FALSE_DV, CONTEXT_MODIFIED);
                    }
                    if ("0.0.2".equals(d.statementId())) {
                        assertDv(d, BIG, DV.FALSE_DV, CONTEXT_MODIFIED);
                    }
                    if ("0.0.2.0.0".equals(d.statementId())) {
                        assertDv(d, BIG, DV.FALSE_DV, CONTEXT_MODIFIED);
                    }
                    if ("0.0.2.0.1".equals(d.statementId())) {
                        assertDv(d, BIG, DV.FALSE_DV, CONTEXT_MODIFIED);
                    }
                    if ("0".equals(d.statementId())) {
                        assertDv(d, BIG, DV.FALSE_DV, CONTEXT_MODIFIED);
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
                        assertCurrentValue(d, 53, "expressionsInX.stream().filter(/*inline test*/e instanceof Equals&&``eq`.lhs` instanceof ConstantExpression<?>&&!(``eq`.lhs` instanceof NullConstant)&&null!=e&&null!=``eq`.lhs`).map(`e/*(Equals)*/.lhs/*(ConstantExpression<?>)*/.t`/*(Number)*/.doubleValue()).findFirst().orElse(null)");
                    }
                    if ("03".equals(d.statementId())) {
                        assertCurrentValue(d, 53, "expressionsInX.stream().filter(/*inline test*/e instanceof Equals&&``eq`.lhs` instanceof ConstantExpression<?>&&!(``eq`.lhs` instanceof NullConstant)&&null!=e&&null!=``eq`.lhs`).map(`e/*(Equals)*/.lhs/*(ConstantExpression<?>)*/.t`/*(Number)*/.doubleValue()).findFirst().orElse(null)");
                    }
                }
                if ("inequality".equals(d.variableName())) {
                    if ("04.0.0".equals(d.statementId())) {
                        assertCurrentValue(d, BIG,  "new LinearInequalityInOneVariable(b,y,a*xEquals+c,allowEquals)");
                    }
                }
                if ("intervalX".equals(d.variableName())) {
                    if ("06".equals(d.statementId())) {
                        String expected = d.iteration() < BIG ? "<s:Interval>"
                                : "2==expressionsInX.size()?instance type boolean?null:([expressionsInX.stream().filter(/*inline test*/e instanceof Equals&&``eq`.lhs` instanceof ConstantExpression<?>&&!(``eq`.lhs` instanceof NullConstant)&&null!=e&&null!=``eq`.lhs`).map(`e/*(Equals)*/.lhs/*(ConstantExpression<?>)*/.t`/*(Number)*/.doubleValue()).findFirst().orElse(null),expressionsInY.stream().filter(/*inline test*/e instanceof Equals&&``eq`.lhs` instanceof ConstantExpression<?>&&!(``eq`.lhs` instanceof NullConstant)&&null!=e&&null!=``eq`.lhs`).map(`e/*(Equals)*/.lhs/*(ConstantExpression<?>)*/.t`/*(Number)*/.doubleValue()).findFirst().orElse(null),expressionsInX.stream().allMatch(/*inline test*/(VariableExpression.class).isAssignableFrom(``eq`.rhs`.getClass())&&e instanceof Negation&&``eq`.lhs` instanceof ConstantExpression<?>&&``n`.expression` instanceof Equals&&null!=e&&null!=``n`.expression`&&null!=``eq`.lhs`),expressionsInY.stream().allMatch(/*inline test*/(VariableExpression.class).isAssignableFrom(``eq`.rhs`.getClass())&&e instanceof Negation&&``eq`.lhs` instanceof ConstantExpression<?>&&``n`.expression` instanceof Equals&&null!=e&&null!=``n`.expression`&&null!=``eq`.lhs`),instance type boolean])&&!instance type boolean&&Double.isFinite(`left`)&&Infinity==`right`&&2==expressionsInX.size()&&null==expressionsInX.stream().filter(/*inline test*/e instanceof Equals&&``eq`.lhs` instanceof ConstantExpression<?>&&!(``eq`.lhs` instanceof NullConstant)&&null!=e&&null!=``eq`.lhs`).map(`e/*(Equals)*/.lhs/*(ConstantExpression<?>)*/.t`/*(Number)*/.doubleValue()).findFirst().orElse(null)&&null==expressionsInY.stream().filter(/*inline test*/e instanceof Equals&&``eq`.lhs` instanceof ConstantExpression<?>&&!(``eq`.lhs` instanceof NullConstant)&&null!=e&&null!=``eq`.lhs`).map(`e/*(Equals)*/.lhs/*(ConstantExpression<?>)*/.t`/*(Number)*/.doubleValue()).findFirst().orElse(null)?new Interval(`expressions.get(0) instanceof GreaterThanZero&&2==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInTwoVariables(``scope-t1:7`.a`,``scope-t1:7`.v`,``scope-t2:7`.a`,``scope-t2:7`.v`,`c`,expressions.get(0)/*(GreaterThanZero)*/.allowEquals()):1==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInOneVariable(``scope-t1:6`.a`,``scope-t1:6`.v`,`c`,expressions.get(0)/*(GreaterThanZero)*/.allowEquals()):null instanceof LinearInequalityInOneVariable&&2==expressions.size()&&(1==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()||2==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size())?`2==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInTwoVariables(``scope-t1:7`.a`,``scope-t1:7`.v`,``scope-t2:7`.a`,``scope-t2:7`.v`,`c`,expression/*(GreaterThanZero)*/.allowEquals()):1==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInOneVariable(``scope-t1:6`.a`,``scope-t1:6`.v`,`c`,expression/*(GreaterThanZero)*/.allowEquals()):null/*(LinearInequalityInOneVariable)*/.a`>0?new Interval(-`bOverA`,`2==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInTwoVariables(``scope-t1:7`.a`,``scope-t1:7`.v`,``scope-t2:7`.a`,``scope-t2:7`.v`,`c`,expression/*(GreaterThanZero)*/.allowEquals()):1==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInOneVariable(``scope-t1:6`.a`,``scope-t1:6`.v`,`c`,expression/*(GreaterThanZero)*/.allowEquals()):null/*(LinearInequalityInOneVariable)*/.allowEquals`,`Double.POSITIVE_INFINITY`,true):new Interval(`Double.NEGATIVE_INFINITY`,true,`bOverA`,`2==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInTwoVariables(``scope-t1:7`.a`,``scope-t1:7`.v`,``scope-t2:7`.a`,``scope-t2:7`.v`,`c`,expression/*(GreaterThanZero)*/.allowEquals()):1==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInOneVariable(``scope-t1:6`.a`,``scope-t1:6`.v`,`c`,expression/*(GreaterThanZero)*/.allowEquals()):null/*(LinearInequalityInOneVariable)*/.allowEquals`):null.left`,`expressions.get(0) instanceof GreaterThanZero&&2==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInTwoVariables(``scope-t1:7`.a`,``scope-t1:7`.v`,``scope-t2:7`.a`,``scope-t2:7`.v`,`c`,expressions.get(0)/*(GreaterThanZero)*/.allowEquals()):1==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInOneVariable(``scope-t1:6`.a`,``scope-t1:6`.v`,`c`,expressions.get(0)/*(GreaterThanZero)*/.allowEquals()):null instanceof LinearInequalityInOneVariable&&2==expressions.size()&&(1==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()||2==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size())?`2==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInTwoVariables(``scope-t1:7`.a`,``scope-t1:7`.v`,``scope-t2:7`.a`,``scope-t2:7`.v`,`c`,expression/*(GreaterThanZero)*/.allowEquals()):1==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInOneVariable(``scope-t1:6`.a`,``scope-t1:6`.v`,`c`,expression/*(GreaterThanZero)*/.allowEquals()):null/*(LinearInequalityInOneVariable)*/.a`>0?new Interval(-`bOverA`,`2==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInTwoVariables(``scope-t1:7`.a`,``scope-t1:7`.v`,``scope-t2:7`.a`,``scope-t2:7`.v`,`c`,expression/*(GreaterThanZero)*/.allowEquals()):1==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInOneVariable(``scope-t1:6`.a`,``scope-t1:6`.v`,`c`,expression/*(GreaterThanZero)*/.allowEquals()):null/*(LinearInequalityInOneVariable)*/.allowEquals`,`Double.POSITIVE_INFINITY`,true):new Interval(`Double.NEGATIVE_INFINITY`,true,`bOverA`,`2==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInTwoVariables(``scope-t1:7`.a`,``scope-t1:7`.v`,``scope-t2:7`.a`,``scope-t2:7`.v`,`c`,expression/*(GreaterThanZero)*/.allowEquals()):1==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInOneVariable(``scope-t1:6`.a`,``scope-t1:6`.v`,`c`,expression/*(GreaterThanZero)*/.allowEquals()):null/*(LinearInequalityInOneVariable)*/.allowEquals`):null.leftIncluded`,`expressions.get(1) instanceof GreaterThanZero&&2==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInTwoVariables(``scope-t1:7`.a`,``scope-t1:7`.v`,``scope-t2:7`.a`,``scope-t2:7`.v`,`c`,expressions.get(1)/*(GreaterThanZero)*/.allowEquals()):1==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInOneVariable(``scope-t1:6`.a`,``scope-t1:6`.v`,`c`,expressions.get(1)/*(GreaterThanZero)*/.allowEquals()):null instanceof LinearInequalityInOneVariable&&2==expressions.size()&&(1==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()||2==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size())?`2==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInTwoVariables(``scope-t1:7`.a`,``scope-t1:7`.v`,``scope-t2:7`.a`,``scope-t2:7`.v`,`c`,expression/*(GreaterThanZero)*/.allowEquals()):1==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInOneVariable(``scope-t1:6`.a`,``scope-t1:6`.v`,`c`,expression/*(GreaterThanZero)*/.allowEquals()):null/*(LinearInequalityInOneVariable)*/.a`>0?new Interval(-`bOverA`,`2==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInTwoVariables(``scope-t1:7`.a`,``scope-t1:7`.v`,``scope-t2:7`.a`,``scope-t2:7`.v`,`c`,expression/*(GreaterThanZero)*/.allowEquals()):1==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInOneVariable(``scope-t1:6`.a`,``scope-t1:6`.v`,`c`,expression/*(GreaterThanZero)*/.allowEquals()):null/*(LinearInequalityInOneVariable)*/.allowEquals`,`Double.POSITIVE_INFINITY`,true):new Interval(`Double.NEGATIVE_INFINITY`,true,`bOverA`,`2==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInTwoVariables(``scope-t1:7`.a`,``scope-t1:7`.v`,``scope-t2:7`.a`,``scope-t2:7`.v`,`c`,expression/*(GreaterThanZero)*/.allowEquals()):1==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInOneVariable(``scope-t1:6`.a`,``scope-t1:6`.v`,`c`,expression/*(GreaterThanZero)*/.allowEquals()):null/*(LinearInequalityInOneVariable)*/.allowEquals`):null.right`,`expressions.get(1) instanceof GreaterThanZero&&2==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInTwoVariables(``scope-t1:7`.a`,``scope-t1:7`.v`,``scope-t2:7`.a`,``scope-t2:7`.v`,`c`,expressions.get(1)/*(GreaterThanZero)*/.allowEquals()):1==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInOneVariable(``scope-t1:6`.a`,``scope-t1:6`.v`,`c`,expressions.get(1)/*(GreaterThanZero)*/.allowEquals()):null instanceof LinearInequalityInOneVariable&&2==expressions.size()&&(1==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()||2==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size())?`2==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInTwoVariables(``scope-t1:7`.a`,``scope-t1:7`.v`,``scope-t2:7`.a`,``scope-t2:7`.v`,`c`,expression/*(GreaterThanZero)*/.allowEquals()):1==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInOneVariable(``scope-t1:6`.a`,``scope-t1:6`.v`,`c`,expression/*(GreaterThanZero)*/.allowEquals()):null/*(LinearInequalityInOneVariable)*/.a`>0?new Interval(-`bOverA`,`2==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInTwoVariables(``scope-t1:7`.a`,``scope-t1:7`.v`,``scope-t2:7`.a`,``scope-t2:7`.v`,`c`,expression/*(GreaterThanZero)*/.allowEquals()):1==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInOneVariable(``scope-t1:6`.a`,``scope-t1:6`.v`,`c`,expression/*(GreaterThanZero)*/.allowEquals()):null/*(LinearInequalityInOneVariable)*/.allowEquals`,`Double.POSITIVE_INFINITY`,true):new Interval(`Double.NEGATIVE_INFINITY`,true,`bOverA`,`2==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInTwoVariables(``scope-t1:7`.a`,``scope-t1:7`.v`,``scope-t2:7`.a`,``scope-t2:7`.v`,`c`,expression/*(GreaterThanZero)*/.allowEquals()):1==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInOneVariable(``scope-t1:6`.a`,``scope-t1:6`.v`,`c`,expression/*(GreaterThanZero)*/.allowEquals()):null/*(LinearInequalityInOneVariable)*/.allowEquals`):null.rightIncluded`):([Double.isFinite(`left`),Double.isFinite(`right`),expressionsInX.size(),expressionsInX.stream().filter(/*inline test*/e instanceof Equals&&``eq`.lhs` instanceof ConstantExpression<?>&&!(``eq`.lhs` instanceof NullConstant)&&null!=e&&null!=``eq`.lhs`).map(`e/*(Equals)*/.lhs/*(ConstantExpression<?>)*/.t`/*(Number)*/.doubleValue()).findFirst().orElse(null),expressionsInY.stream().filter(/*inline test*/e instanceof Equals&&``eq`.lhs` instanceof ConstantExpression<?>&&!(``eq`.lhs` instanceof NullConstant)&&null!=e&&null!=``eq`.lhs`).map(`e/*(Equals)*/.lhs/*(ConstantExpression<?>)*/.t`/*(Number)*/.doubleValue()).findFirst().orElse(null),expressionsInX.stream().allMatch(/*inline test*/(VariableExpression.class).isAssignableFrom(``eq`.rhs`.getClass())&&e instanceof Negation&&``eq`.lhs` instanceof ConstantExpression<?>&&``n`.expression` instanceof Equals&&null!=e&&null!=``n`.expression`&&null!=``eq`.lhs`),expressionsInY.stream().allMatch(/*inline test*/(VariableExpression.class).isAssignableFrom(``eq`.rhs`.getClass())&&e instanceof Negation&&``eq`.lhs` instanceof ConstantExpression<?>&&``n`.expression` instanceof Equals&&null!=e&&null!=``n`.expression`&&null!=``eq`.lhs`),instance type boolean])?new Interval(`expressions.get(1) instanceof GreaterThanZero&&2==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInTwoVariables(``scope-t1:7`.a`,``scope-t1:7`.v`,``scope-t2:7`.a`,``scope-t2:7`.v`,`c`,expressions.get(1)/*(GreaterThanZero)*/.allowEquals()):1==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInOneVariable(``scope-t1:6`.a`,``scope-t1:6`.v`,`c`,expressions.get(1)/*(GreaterThanZero)*/.allowEquals()):null instanceof LinearInequalityInOneVariable&&2==expressions.size()&&(1==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()||2==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size())?`2==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInTwoVariables(``scope-t1:7`.a`,``scope-t1:7`.v`,``scope-t2:7`.a`,``scope-t2:7`.v`,`c`,expression/*(GreaterThanZero)*/.allowEquals()):1==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInOneVariable(``scope-t1:6`.a`,``scope-t1:6`.v`,`c`,expression/*(GreaterThanZero)*/.allowEquals()):null/*(LinearInequalityInOneVariable)*/.a`>0?new Interval(-`bOverA`,`2==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInTwoVariables(``scope-t1:7`.a`,``scope-t1:7`.v`,``scope-t2:7`.a`,``scope-t2:7`.v`,`c`,expression/*(GreaterThanZero)*/.allowEquals()):1==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInOneVariable(``scope-t1:6`.a`,``scope-t1:6`.v`,`c`,expression/*(GreaterThanZero)*/.allowEquals()):null/*(LinearInequalityInOneVariable)*/.allowEquals`,`Double.POSITIVE_INFINITY`,true):new Interval(`Double.NEGATIVE_INFINITY`,true,`bOverA`,`2==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInTwoVariables(``scope-t1:7`.a`,``scope-t1:7`.v`,``scope-t2:7`.a`,``scope-t2:7`.v`,`c`,expression/*(GreaterThanZero)*/.allowEquals()):1==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInOneVariable(``scope-t1:6`.a`,``scope-t1:6`.v`,`c`,expression/*(GreaterThanZero)*/.allowEquals()):null/*(LinearInequalityInOneVariable)*/.allowEquals`):null.left`,`expressions.get(1) instanceof GreaterThanZero&&2==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInTwoVariables(``scope-t1:7`.a`,``scope-t1:7`.v`,``scope-t2:7`.a`,``scope-t2:7`.v`,`c`,expressions.get(1)/*(GreaterThanZero)*/.allowEquals()):1==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInOneVariable(``scope-t1:6`.a`,``scope-t1:6`.v`,`c`,expressions.get(1)/*(GreaterThanZero)*/.allowEquals()):null instanceof LinearInequalityInOneVariable&&2==expressions.size()&&(1==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()||2==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size())?`2==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInTwoVariables(``scope-t1:7`.a`,``scope-t1:7`.v`,``scope-t2:7`.a`,``scope-t2:7`.v`,`c`,expression/*(GreaterThanZero)*/.allowEquals()):1==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInOneVariable(``scope-t1:6`.a`,``scope-t1:6`.v`,`c`,expression/*(GreaterThanZero)*/.allowEquals()):null/*(LinearInequalityInOneVariable)*/.a`>0?new Interval(-`bOverA`,`2==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInTwoVariables(``scope-t1:7`.a`,``scope-t1:7`.v`,``scope-t2:7`.a`,``scope-t2:7`.v`,`c`,expression/*(GreaterThanZero)*/.allowEquals()):1==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInOneVariable(``scope-t1:6`.a`,``scope-t1:6`.v`,`c`,expression/*(GreaterThanZero)*/.allowEquals()):null/*(LinearInequalityInOneVariable)*/.allowEquals`,`Double.POSITIVE_INFINITY`,true):new Interval(`Double.NEGATIVE_INFINITY`,true,`bOverA`,`2==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInTwoVariables(``scope-t1:7`.a`,``scope-t1:7`.v`,``scope-t2:7`.a`,``scope-t2:7`.v`,`c`,expression/*(GreaterThanZero)*/.allowEquals()):1==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInOneVariable(``scope-t1:6`.a`,``scope-t1:6`.v`,`c`,expression/*(GreaterThanZero)*/.allowEquals()):null/*(LinearInequalityInOneVariable)*/.allowEquals`):null.leftIncluded`,`expressions.get(0) instanceof GreaterThanZero&&2==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInTwoVariables(``scope-t1:7`.a`,``scope-t1:7`.v`,``scope-t2:7`.a`,``scope-t2:7`.v`,`c`,expressions.get(0)/*(GreaterThanZero)*/.allowEquals()):1==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInOneVariable(``scope-t1:6`.a`,``scope-t1:6`.v`,`c`,expressions.get(0)/*(GreaterThanZero)*/.allowEquals()):null instanceof LinearInequalityInOneVariable&&2==expressions.size()&&(1==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()||2==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size())?`2==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInTwoVariables(``scope-t1:7`.a`,``scope-t1:7`.v`,``scope-t2:7`.a`,``scope-t2:7`.v`,`c`,expression/*(GreaterThanZero)*/.allowEquals()):1==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInOneVariable(``scope-t1:6`.a`,``scope-t1:6`.v`,`c`,expression/*(GreaterThanZero)*/.allowEquals()):null/*(LinearInequalityInOneVariable)*/.a`>0?new Interval(-`bOverA`,`2==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInTwoVariables(``scope-t1:7`.a`,``scope-t1:7`.v`,``scope-t2:7`.a`,``scope-t2:7`.v`,`c`,expression/*(GreaterThanZero)*/.allowEquals()):1==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInOneVariable(``scope-t1:6`.a`,``scope-t1:6`.v`,`c`,expression/*(GreaterThanZero)*/.allowEquals()):null/*(LinearInequalityInOneVariable)*/.allowEquals`,`Double.POSITIVE_INFINITY`,true):new Interval(`Double.NEGATIVE_INFINITY`,true,`bOverA`,`2==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInTwoVariables(``scope-t1:7`.a`,``scope-t1:7`.v`,``scope-t2:7`.a`,``scope-t2:7`.v`,`c`,expression/*(GreaterThanZero)*/.allowEquals()):1==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInOneVariable(``scope-t1:6`.a`,``scope-t1:6`.v`,`c`,expression/*(GreaterThanZero)*/.allowEquals()):null/*(LinearInequalityInOneVariable)*/.allowEquals`):null.right`,`expressions.get(1) instanceof GreaterThanZero&&2==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInTwoVariables(``scope-t1:7`.a`,``scope-t1:7`.v`,``scope-t2:7`.a`,``scope-t2:7`.v`,`c`,expressions.get(1)/*(GreaterThanZero)*/.allowEquals()):1==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInOneVariable(``scope-t1:6`.a`,``scope-t1:6`.v`,`c`,expressions.get(1)/*(GreaterThanZero)*/.allowEquals()):null instanceof LinearInequalityInOneVariable&&2==expressions.size()&&(1==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()||2==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size())?`2==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInTwoVariables(``scope-t1:7`.a`,``scope-t1:7`.v`,``scope-t2:7`.a`,``scope-t2:7`.v`,`c`,expression/*(GreaterThanZero)*/.allowEquals()):1==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInOneVariable(``scope-t1:6`.a`,``scope-t1:6`.v`,`c`,expression/*(GreaterThanZero)*/.allowEquals()):null/*(LinearInequalityInOneVariable)*/.a`>0?new Interval(-`bOverA`,`2==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInTwoVariables(``scope-t1:7`.a`,``scope-t1:7`.v`,``scope-t2:7`.a`,``scope-t2:7`.v`,`c`,expression/*(GreaterThanZero)*/.allowEquals()):1==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInOneVariable(``scope-t1:6`.a`,``scope-t1:6`.v`,`c`,expression/*(GreaterThanZero)*/.allowEquals()):null/*(LinearInequalityInOneVariable)*/.allowEquals`,`Double.POSITIVE_INFINITY`,true):new Interval(`Double.NEGATIVE_INFINITY`,true,`bOverA`,`2==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInTwoVariables(``scope-t1:7`.a`,``scope-t1:7`.v`,``scope-t2:7`.a`,``scope-t2:7`.v`,`c`,expression/*(GreaterThanZero)*/.allowEquals()):1==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInOneVariable(``scope-t1:6`.a`,``scope-t1:6`.v`,`c`,expression/*(GreaterThanZero)*/.allowEquals()):null/*(LinearInequalityInOneVariable)*/.allowEquals`):null.rightIncluded`):<return value>:([expressionsInX.get(0),`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size(),expressionsInX.size(),expressionsInX.stream().filter(/*inline test*/e instanceof Equals&&``eq`.lhs` instanceof ConstantExpression<?>&&!(``eq`.lhs` instanceof NullConstant)&&null!=e&&null!=``eq`.lhs`).map(`e/*(Equals)*/.lhs/*(ConstantExpression<?>)*/.t`/*(Number)*/.doubleValue()).findFirst().orElse(null),expressionsInY.stream().filter(/*inline test*/e instanceof Equals&&``eq`.lhs` instanceof ConstantExpression<?>&&!(``eq`.lhs` instanceof NullConstant)&&null!=e&&null!=``eq`.lhs`).map(`e/*(Equals)*/.lhs/*(ConstantExpression<?>)*/.t`/*(Number)*/.doubleValue()).findFirst().orElse(null),expressionsInX.stream().allMatch(/*inline test*/(VariableExpression.class).isAssignableFrom(``eq`.rhs`.getClass())&&e instanceof Negation&&``eq`.lhs` instanceof ConstantExpression<?>&&``n`.expression` instanceof Equals&&null!=e&&null!=``n`.expression`&&null!=``eq`.lhs`),expressionsInY.stream().allMatch(/*inline test*/(VariableExpression.class).isAssignableFrom(``eq`.rhs`.getClass())&&e instanceof Negation&&``eq`.lhs` instanceof ConstantExpression<?>&&``n`.expression` instanceof Equals&&null!=e&&null!=``n`.expression`&&null!=``eq`.lhs`),expressionsInX.get(0)/*(GreaterThanZero)*/.allowEquals(),instance type boolean])?`2==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInTwoVariables(``scope-t1:7`.a`,``scope-t1:7`.v`,``scope-t2:7`.a`,``scope-t2:7`.v`,`c`,expression/*(GreaterThanZero)*/.allowEquals()):1==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInOneVariable(``scope-t1:6`.a`,``scope-t1:6`.v`,`c`,expression/*(GreaterThanZero)*/.allowEquals()):null/*(LinearInequalityInOneVariable)*/.a`>0?new Interval(-`bOverA`,`2==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInTwoVariables(``scope-t1:7`.a`,``scope-t1:7`.v`,``scope-t2:7`.a`,``scope-t2:7`.v`,`c`,expression/*(GreaterThanZero)*/.allowEquals()):1==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInOneVariable(``scope-t1:6`.a`,``scope-t1:6`.v`,`c`,expression/*(GreaterThanZero)*/.allowEquals()):null/*(LinearInequalityInOneVariable)*/.allowEquals`,`Double.POSITIVE_INFINITY`,true):new Interval(`Double.NEGATIVE_INFINITY`,true,`bOverA`,`2==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInTwoVariables(``scope-t1:7`.a`,``scope-t1:7`.v`,``scope-t2:7`.a`,``scope-t2:7`.v`,`c`,expression/*(GreaterThanZero)*/.allowEquals()):1==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInOneVariable(``scope-t1:6`.a`,``scope-t1:6`.v`,`c`,expression/*(GreaterThanZero)*/.allowEquals()):null/*(LinearInequalityInOneVariable)*/.allowEquals`):null";
                        assertEquals(expected, d.currentValue().toString());
                        if (d.iteration() >= BIG) {
                            assertEquals("expressionsInX, expressionsInX, e, e, expressionsInY, e, e, expressionsInX, e, e, expressionsInY, e, e, expressionsInX, expressionsInX, e, e, expressionsInY, e, e, expressionsInX, expressionsInX, e, e, expressionsInY, e, e, expressionsInX, e, e, expressionsInY, e, e, expressionsInX, expressionsInX, expressionsInX, e, e, expressionsInY, e, e, expressionsInX, e, e, expressionsInY, e, e, expressionsInX",
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
            if ("accept".equals(d.methodInfo().name)) {
                assertEquals("$4", d.methodInfo().typeInfo.simpleName);
                if (d.variable() instanceof ParameterInfo pi && "terms".equals(pi.name)) {
                    assertDv(d, DV.TRUE_DV, CONTEXT_MODIFIED);
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
                String srv = d.iteration() <= BIG ? "<m:extractInterval1>" : "<undetermined return value>";
                assertEquals(srv, d.methodAnalysis().getSingleReturnValue().toString());
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
                assertDv(d, BIG, DV.FALSE_DV, Property.MODIFIED_METHOD);
                String expected = d.iteration() < BIG ? "<m:accept3>" :
                        "/*inline accept3*/!instance type boolean&&(instance type boolean||2==expressionsInV.size())?instance type boolean:null==expressionsInV.stream().filter(/*inline test*/e instanceof Equals&&``eq`.lhs` instanceof ConstantExpression<?>&&!(``eq`.lhs` instanceof NullConstant)&&null!=e&&null!=``eq`.lhs`).map(`e/*(Equals)*/.lhs/*(ConstantExpression<?>)*/.t`/*(Number)*/.doubleValue()).findFirst().orElse(null)?null||expressionsInV.stream().allMatch(/*inline test*/(VariableExpression.class).isAssignableFrom(``eq`.rhs`.getClass())&&e instanceof Negation&&``eq`.lhs` instanceof ConstantExpression<?>&&``n`.expression` instanceof Equals&&null!=e&&null!=``n`.expression`&&null!=``eq`.lhs`):`allowEquals`?expressionsInV.stream().filter(/*inline test*/e instanceof Equals&&``eq`.lhs` instanceof ConstantExpression<?>&&!(``eq`.lhs` instanceof NullConstant)&&null!=e&&null!=``eq`.lhs`).map(`e/*(Equals)*/.lhs/*(ConstantExpression<?>)*/.t`/*(Number)*/.doubleValue()).findFirst().orElse(null)*`a`+`b`>=0:-1+expressionsInV.stream().filter(/*inline test*/e instanceof Equals&&``eq`.lhs` instanceof ConstantExpression<?>&&!(``eq`.lhs` instanceof NullConstant)&&null!=e&&null!=``eq`.lhs`).map(`e/*(Equals)*/.lhs/*(ConstantExpression<?>)*/.t`/*(Number)*/.doubleValue()).findFirst().orElse(null)*`a`+`b`>=0";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
                if (d.iteration() >= BIG) {
                    if (d.methodAnalysis().getSingleReturnValue() instanceof InlinedMethod im) {
                        assertEquals("expressionsInV", im.variablesOfExpressionSorted());
                        assertEquals(111, im.getComplexity());
                    } else fail();
                }
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
                assertDv(d, BIG + 1, DV.FALSE_DV, Property.TEMP_MODIFIED_METHOD);

                assertDv(d, BIG, DV.FALSE_DV, Property.MODIFIED_METHOD);
                String expected = d.iteration() < BIG ? "<m:accept6>" : "/*inline accept6*/instance type boolean";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
                if (d.iteration() >= BIG) {
                    if (d.methodAnalysis().getSingleReturnValue() instanceof InlinedMethod im) {
                        assertEquals("", im.variablesOfExpressionSorted());
                    } else fail();
                }
            }
            if ("accept1".equals(d.methodInfo().name)) {
                assertDv(d, BIG, DV.FALSE_DV, Property.MODIFIED_METHOD);
                String expected = d.iteration() < BIG ? "<m:accept1>" : "/*inline accept1*/expression instanceof GreaterThanZero&&null!=expression?2==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInTwoVariables(``scope-t1:7`.a`,``scope-t1:7`.v`,``scope-t2:7`.a`,``scope-t2:7`.v`,`c`,expression/*(GreaterThanZero)*/.allowEquals()):1==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInOneVariable(``scope-t1:6`.a`,``scope-t1:6`.v`,`c`,expression/*(GreaterThanZero)*/.allowEquals()):null instanceof LinearInequalityInTwoVariables&&(1==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()||2==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size())?perComponent.getOrDefault(`2==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInTwoVariables(``scope-t1:7`.a`,``scope-t1:7`.v`,``scope-t2:7`.a`,``scope-t2:7`.v`,`c`,expression/*(GreaterThanZero)*/.allowEquals()):1==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInOneVariable(``scope-t1:6`.a`,``scope-t1:6`.v`,`c`,expression/*(GreaterThanZero)*/.allowEquals()):null/*(LinearInequalityInTwoVariables)*/.x`,List.of()).isEmpty()||perComponent.getOrDefault(`2==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInTwoVariables(``scope-t1:7`.a`,``scope-t1:7`.v`,``scope-t2:7`.a`,``scope-t2:7`.v`,`c`,expression/*(GreaterThanZero)*/.allowEquals()):1==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInOneVariable(``scope-t1:6`.a`,``scope-t1:6`.v`,`c`,expression/*(GreaterThanZero)*/.allowEquals()):null/*(LinearInequalityInTwoVariables)*/.y`,List.of()).isEmpty()?null:instance type boolean:!perComponent.getOrDefault(`2==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInTwoVariables(``scope-t1:7`.a`,``scope-t1:7`.v`,``scope-t2:7`.a`,``scope-t2:7`.v`,`c`,expression/*(GreaterThanZero)*/.allowEquals()):1==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInOneVariable(``scope-t1:6`.a`,``scope-t1:6`.v`,`c`,expression/*(GreaterThanZero)*/.allowEquals()):null/*(LinearInequalityInOneVariable)*/.v`,List.of()).isEmpty()&&expression instanceof GreaterThanZero&&2==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInTwoVariables(``scope-t1:7`.a`,``scope-t1:7`.v`,``scope-t2:7`.a`,``scope-t2:7`.v`,`c`,expression/*(GreaterThanZero)*/.allowEquals()):1==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInOneVariable(``scope-t1:6`.a`,``scope-t1:6`.v`,`c`,expression/*(GreaterThanZero)*/.allowEquals()):null instanceof LinearInequalityInOneVariable&&null!=expression&&(!(2==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInTwoVariables(``scope-t1:7`.a`,``scope-t1:7`.v`,``scope-t2:7`.a`,``scope-t2:7`.v`,`c`,expression/*(GreaterThanZero)*/.allowEquals()):1==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInOneVariable(``scope-t1:6`.a`,``scope-t1:6`.v`,`c`,expression/*(GreaterThanZero)*/.allowEquals()):null instanceof LinearInequalityInTwoVariables)||1!=`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size())&&(!(2==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInTwoVariables(``scope-t1:7`.a`,``scope-t1:7`.v`,``scope-t2:7`.a`,``scope-t2:7`.v`,`c`,expression/*(GreaterThanZero)*/.allowEquals()):1==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInOneVariable(``scope-t1:6`.a`,``scope-t1:6`.v`,`c`,expression/*(GreaterThanZero)*/.allowEquals()):null instanceof LinearInequalityInTwoVariables)||2!=`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size())&&(1==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()||2==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size())?([perComponent.getOrDefault(`2==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInTwoVariables(``scope-t1:7`.a`,``scope-t1:7`.v`,``scope-t2:7`.a`,``scope-t2:7`.v`,`c`,expression/*(GreaterThanZero)*/.allowEquals()):1==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInOneVariable(``scope-t1:6`.a`,``scope-t1:6`.v`,`c`,expression/*(GreaterThanZero)*/.allowEquals()):null/*(LinearInequalityInOneVariable)*/.v`,List.of()).isEmpty(),perComponent.getOrDefault(`2==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInTwoVariables(``scope-t1:7`.a`,``scope-t1:7`.v`,``scope-t2:7`.a`,``scope-t2:7`.v`,`c`,expression/*(GreaterThanZero)*/.allowEquals()):1==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInOneVariable(``scope-t1:6`.a`,``scope-t1:6`.v`,`c`,expression/*(GreaterThanZero)*/.allowEquals()):null/*(LinearInequalityInOneVariable)*/.v`,List.of()).size(),`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size(),expression/*(GreaterThanZero)*/.allowEquals(),expression,instance type boolean])?instance type boolean:([perComponent.getOrDefault(`2==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInTwoVariables(``scope-t1:7`.a`,``scope-t1:7`.v`,``scope-t2:7`.a`,``scope-t2:7`.v`,`c`,expression/*(GreaterThanZero)*/.allowEquals()):1==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInOneVariable(``scope-t1:6`.a`,``scope-t1:6`.v`,`c`,expression/*(GreaterThanZero)*/.allowEquals()):null/*(LinearInequalityInOneVariable)*/.v`,List.of()).isEmpty(),`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size(),perComponent.getOrDefault(`2==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInTwoVariables(``scope-t1:7`.a`,``scope-t1:7`.v`,``scope-t2:7`.a`,``scope-t2:7`.v`,`c`,expression/*(GreaterThanZero)*/.allowEquals()):1==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInOneVariable(``scope-t1:6`.a`,``scope-t1:6`.v`,`c`,expression/*(GreaterThanZero)*/.allowEquals()):null/*(LinearInequalityInOneVariable)*/.v`,List.of()).stream().filter(/*inline test*/e instanceof Equals&&``eq`.lhs` instanceof ConstantExpression<?>&&!(``eq`.lhs` instanceof NullConstant)&&null!=e&&null!=``eq`.lhs`).map(`e/*(Equals)*/.lhs/*(ConstantExpression<?>)*/.t`/*(Number)*/.doubleValue()).findFirst().orElse(null),expression/*(GreaterThanZero)*/.allowEquals(),expression,instance type boolean])?null||perComponent.getOrDefault(`2==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInTwoVariables(``scope-t1:7`.a`,``scope-t1:7`.v`,``scope-t2:7`.a`,``scope-t2:7`.v`,`c`,expression/*(GreaterThanZero)*/.allowEquals()):1==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInOneVariable(``scope-t1:6`.a`,``scope-t1:6`.v`,`c`,expression/*(GreaterThanZero)*/.allowEquals()):null/*(LinearInequalityInOneVariable)*/.v`,List.of()).stream().allMatch(/*inline test*/(VariableExpression.class).isAssignableFrom(``eq`.rhs`.getClass())&&e instanceof Negation&&``eq`.lhs` instanceof ConstantExpression<?>&&``n`.expression` instanceof Equals&&null!=e&&null!=``n`.expression`&&null!=``eq`.lhs`):`allowEquals`?perComponent.getOrDefault(`2==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInTwoVariables(``scope-t1:7`.a`,``scope-t1:7`.v`,``scope-t2:7`.a`,``scope-t2:7`.v`,`c`,expression/*(GreaterThanZero)*/.allowEquals()):1==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInOneVariable(``scope-t1:6`.a`,``scope-t1:6`.v`,`c`,expression/*(GreaterThanZero)*/.allowEquals()):null/*(LinearInequalityInOneVariable)*/.v`,List.of()).stream().filter(/*inline test*/e instanceof Equals&&``eq`.lhs` instanceof ConstantExpression<?>&&!(``eq`.lhs` instanceof NullConstant)&&null!=e&&null!=``eq`.lhs`).map(`e/*(Equals)*/.lhs/*(ConstantExpression<?>)*/.t`/*(Number)*/.doubleValue()).findFirst().orElse(null)*`a`+`b`>=0:-1+perComponent.getOrDefault(`2==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInTwoVariables(``scope-t1:7`.a`,``scope-t1:7`.v`,``scope-t2:7`.a`,``scope-t2:7`.v`,`c`,expression/*(GreaterThanZero)*/.allowEquals()):1==`terms`.stream()/*0==this.size()*/.filter(null!=``t`.v`).toList().size()?new LinearInequalityInOneVariable(``scope-t1:6`.a`,``scope-t1:6`.v`,`c`,expression/*(GreaterThanZero)*/.allowEquals()):null/*(LinearInequalityInOneVariable)*/.v`,List.of()).stream().filter(/*inline test*/e instanceof Equals&&``eq`.lhs` instanceof ConstantExpression<?>&&!(``eq`.lhs` instanceof NullConstant)&&null!=e&&null!=``eq`.lhs`).map(`e/*(Equals)*/.lhs/*(ConstantExpression<?>)*/.t`/*(Number)*/.doubleValue()).findFirst().orElse(null)*`a`+`b`>=0:null:null";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
                if (d.iteration() >= BIG) {
                    if (d.methodAnalysis().getSingleReturnValue() instanceof InlinedMethod im) {
                        assertEquals(262, im.complexity);
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
                assertDv(d, DV.TRUE_DV, FINAL);
                assertDv(d, MultiLevel.MUTABLE_DV, EXTERNAL_IMMUTABLE);
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
                assertDv(d, MultiLevel.MUTABLE_DV, IMMUTABLE);
            }
            if ("Variable".equals(d.typeInfo().simpleName)) {
                assertDv(d, MultiLevel.MUTABLE_DV, IMMUTABLE);
            }
            if ("LinearInequalityInOneVariable".equals(d.typeInfo().simpleName)) {
                assertDv(d, 71, MultiLevel.INDEPENDENT_1_DV, INDEPENDENT);
                assertDv(d, 72, MultiLevel.CONTAINER_DV, CONTAINER);
            }
            if ("Term".equals(d.typeInfo().simpleName)) {
                assertDv(d, 89, MultiLevel.EFFECTIVELY_E2IMMUTABLE_DV, IMMUTABLE);
            }
        };


        testClass("Expressions_0", 2, 17,
                new DebugConfiguration.Builder()
                    //    .addEvaluationResultVisitor(evaluationResultVisitor)
                  //      .addStatementAnalyserVisitor(statementAnalyserVisitor)
                    //    .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                   //     .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                        .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                        .build(),
                new AnalyserConfiguration.Builder()
                        .setComputeFieldAnalyserAcrossAllMethods(true)
                        .setForceAlphabeticAnalysisInPrimaryType(true)
                        .build());
    }
}
