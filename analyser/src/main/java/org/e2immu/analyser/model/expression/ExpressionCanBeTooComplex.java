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

package org.e2immu.analyser.model.expression;

import org.e2immu.analyser.analyser.CausesOfDelay;
import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.Identifier;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.expression.util.MultiExpression;
import org.e2immu.analyser.model.impl.BaseExpression;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class ExpressionCanBeTooComplex extends BaseExpression implements Expression {

    protected ExpressionCanBeTooComplex(Identifier identifier, int complexity) {
        super(identifier, complexity);
    }

    // make a MultiValue with one component per variable (so that they are marked "read")
    // and one per assignment. Even though the And may be too complex, we should not ignore READ/ASSIGNED AT
    // information
    protected Expression reducedComplexity(EvaluationResult evaluationContext,
                                           List<Expression> expressions,
                                           Expression[] values) {
        ParameterizedType booleanType = evaluationContext.getPrimitives().booleanParameterizedType();

        // IMPROVE also add assignments
        // catch all variable expressions
        TreeSet<Expression> variableExpressions = Stream.concat(Arrays.stream(values), expressions.stream())
                .flatMap(e -> collect(e).stream())
                .collect(Collectors.toCollection(TreeSet::new));
        List<Expression> newExpressions = new LinkedList<>(variableExpressions);
        CausesOfDelay causesOfDelay = Arrays.stream(values).map(Expression::causesOfDelay)
                .reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge);
        Expression instance = causesOfDelay.isDelayed()
                ? DelayedExpression.forTooComplex(identifier, booleanType, causesOfDelay)
                : Instance.forTooComplex(identifier, booleanType);
        newExpressions.add(instance);
        MultiExpression multiExpression = new MultiExpression(newExpressions.toArray(Expression[]::new));
        return new MultiExpressions(identifier, evaluationContext.getAnalyserContext(), multiExpression);
    }

    private static List<Expression> collect(Expression expression) {
        List<Expression> result = new ArrayList<>();
        expression.visit(e -> {
            if (e.isInstanceOf(IsVariableExpression.class) || e.isInstanceOf(UnknownExpression.class)) {
                result.add(e);
                return false;
            }
            return true;
        });
        return result;
    }
}
