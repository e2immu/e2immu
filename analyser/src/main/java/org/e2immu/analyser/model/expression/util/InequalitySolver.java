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
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.expression.And;
import org.e2immu.analyser.model.expression.Equals;
import org.e2immu.analyser.model.expression.GreaterThanZero;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.SMapList;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
very simple inequality solver

first goal: given i>0, j<0 then j>i should fail

 */
public class InequalitySolver {
    private final Map<Variable, List<Expression>> perVariable;
    private final EvaluationContext evaluationContext;

    /*
    expression is the "given": we extract components that have only one variable (i>0, j<0, k!=3).

    We're assuming that per single variable, the expressions are consistent (i.e., not i>0 and i<0 together),
    and not redundant (i>0, i>1).
     */
    public InequalitySolver(EvaluationContext evaluationContext, Expression expression) {
        this.evaluationContext = evaluationContext;
        Map<Variable, List<Expression>> builder = new HashMap<>();
        if (expression instanceof And and) {
            and.expressions().forEach(e -> tryToAddSingleNumericVariableComparison(builder, e));
        } else {
            tryToAddSingleNumericVariableComparison(builder, expression);
        }
        perVariable = Map.copyOf(builder);
    }

    public InequalitySolver(EvaluationContext evaluationContext, List<Expression> expressions) {
        this.evaluationContext = evaluationContext;
        Map<Variable, List<Expression>> builder = new HashMap<>();
        expressions.forEach(e -> tryToAddSingleNumericVariableComparison(builder, e));
        perVariable = Map.copyOf(builder);
    }

    private static void tryToAddSingleNumericVariableComparison(Map<Variable, List<Expression>> map, Expression e) {
        List<Variable> vars = e.variables();
        if (vars.size() == 1) {
            Variable variable = vars.get(0);
            if (Primitives.isNumeric(variable.parameterizedType()) &&
                    (e.isInstanceOf(GreaterThanZero.class) || e.isInstanceOf(Equals.class))) {
                SMapList.add(map, variable, e);
            }
        }
    }

    public Map<Variable, List<Expression>> getPerVariable() {
        return perVariable;
    }

    /*
    evaluate expressions in single or multiple variables (j>i)

    we're mostly interested in the two variable situation.

    returns null when not applicable
    */

    public Boolean evaluate(Expression expression) {
        if (expression instanceof And and) {
            return and.expressions().stream().map(this::accept)
                    .reduce(true, (v1, v2) -> v1 == null ? v2 : v2 == null ? v1 : v1 && v2);
        }
        return accept(expression);
    }

    private Boolean accept(Expression expression) {
        if (expression instanceof GreaterThanZero gt0) {
            Inequality inequality = InequalityHelper.extract(evaluationContext, gt0);

            if (inequality instanceof LinearInequalityInOneVariable oneVar) {
                List<Expression> expressionsInV = perVariable.getOrDefault(oneVar.v(), List.of());
                if (expressionsInV.isEmpty()) return null;
                return oneVar.accept(expressionsInV);
            }

            if (inequality instanceof LinearInequalityInTwoVariables twoVars) {
                List<Expression> expressionsInX = perVariable.getOrDefault(twoVars.x(), List.of());
                List<Expression> expressionsInY = perVariable.getOrDefault(twoVars.y(), List.of());
                if (expressionsInX.isEmpty() || expressionsInY.isEmpty()) return null;

                return twoVars.accept(expressionsInX, expressionsInY);
            }
        }
        return null;
    }
}

