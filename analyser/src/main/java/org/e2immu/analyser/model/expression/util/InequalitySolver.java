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

package org.e2immu.analyser.model.expression.util;

import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.util.SMapList;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/*
very simple inequality solver

first goal: given i>0, j<0 then j>i should fail

A component can be a variable, but it can as well be a method call (expression) having a variable
as object. Rat

 */
public class InequalitySolver {
    private final Map<OneVariable, List<Expression>> perComponent;
    private final EvaluationContext evaluationContext;

    /*
    expression is the "given": we extract components that have only one variable (i>0, j<0, k!=3).

    We're assuming that per single variable, the expressions are consistent (i.e., not i>0 and i<0 together),
    and not redundant (i>0, i>1).
     */
    public InequalitySolver(EvaluationContext evaluationContext, Expression expression) {
        this.evaluationContext = evaluationContext;
        Map<OneVariable, List<Expression>> builder = new HashMap<>();
        if (expression instanceof And and) {
            and.getExpressions().forEach(e -> tryToAddSingleNumericVariableComparison(builder, e));
        } else {
            tryToAddSingleNumericVariableComparison(builder, expression);
        }
        perComponent = Map.copyOf(builder);
    }

    public InequalitySolver(EvaluationContext evaluationContext, List<Expression> expressions) {
        this.evaluationContext = evaluationContext;
        Map<OneVariable, List<Expression>> builder = new HashMap<>();
        expressions.forEach(e -> tryToAddSingleNumericVariableComparison(builder, e));
        perComponent = Map.copyOf(builder);
    }

    /*
    if the expression is recognized as an expression in a single variable, then it is added to the map.
    We recognize a single variable comparison, such as i=1, i<=10, etc. but also set.size()<=10
     */
    private void tryToAddSingleNumericVariableComparison(Map<OneVariable, List<Expression>> map, Expression e) {
        Set<OneVariable> oneVariables = new HashSet<>();
        AtomicBoolean invalid = new AtomicBoolean();
        e.visit(element -> {
            if (element instanceof MethodCall methodCall) {
                if (methodCall.returnType().isNumeric() && evaluationContext.getAnalyserContext()
                        .getMethodAnalysis(methodCall.methodInfo)
                        .getProperty(Property.MODIFIED_METHOD).valueIsFalse()) {
                    oneVariables.add(methodCall);
                } else {
                    invalid.set(true);
                }
                return false;
            }
            VariableExpression ve;
            if ((ve = element.asInstanceOf(VariableExpression.class)) != null) {
                if (ve.variable().parameterizedType().isNumeric()) {
                    oneVariables.add(ve.variable());
                } else {
                    invalid.set(true);
                }
                return false;
            }
            if (element instanceof InlineConditional) {
                invalid.set(true);
                return false;
            }
            return true;
        });
        if (oneVariables.size() == 1 && !invalid.get()) {
            SMapList.add(map, oneVariables.stream().findFirst().orElseThrow(), e);
        }
    }

    public Map<OneVariable, List<Expression>> getPerComponent() {
        return perComponent;
    }

    /*
    evaluate expressions in single or multiple variables (j>i)

    we're mostly interested in the two variable situation.

    returns null when not applicable
    */

    public Boolean evaluate(Expression expression) {
        if (expression instanceof And and) {
            return and.getExpressions().stream().map(this::accept)
                    .reduce(true, (v1, v2) -> v1 == null ? v2 : v2 == null ? v1 : v1 && v2);
        }
        return accept(expression);
    }

    private Boolean accept(Expression expression) {
        if (expression instanceof GreaterThanZero gt0) {
            Inequality inequality = InequalityHelper.extract(gt0);

            if (inequality instanceof LinearInequalityInOneVariable oneVar) {
                List<Expression> expressionsInV = perComponent.getOrDefault(oneVar.v(), List.of());
                if (expressionsInV.isEmpty()) return null;
                return oneVar.accept(expressionsInV);
            }

            if (inequality instanceof LinearInequalityInTwoVariables twoVars) {
                List<Expression> expressionsInX = perComponent.getOrDefault(twoVars.x(), List.of());
                List<Expression> expressionsInY = perComponent.getOrDefault(twoVars.y(), List.of());
                if (expressionsInX.isEmpty() || expressionsInY.isEmpty()) return null;

                return twoVars.accept(expressionsInX, expressionsInY);
            }
        }
        return null;
    }
}

