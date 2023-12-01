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
import org.e2immu.analyser.model.expression.util.ExtractComponentsOfTooComplex;
import org.e2immu.analyser.model.expression.util.MultiExpression;
import org.e2immu.analyser.model.impl.BaseExpression;
import org.e2immu.analyser.parser.InspectionProvider;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public abstract class ExpressionCanBeTooComplex extends BaseExpression implements Expression {

    protected ExpressionCanBeTooComplex(Identifier identifier, int complexity) {
        super(identifier, complexity);
    }

    // make a MultiValue with one component per variable (so that they are marked "read")
    // and one per assignment. Even though the And may be too complex, we should not ignore READ/ASSIGNED AT
    // information
    public static Expression reducedComplexity(Identifier identifier,
                                               EvaluationResult context,
                                               List<Expression> expressions,
                                               Expression[] values) {
        ParameterizedType booleanType = context.getPrimitives().booleanParameterizedType();

        CausesOfDelay causesOfDelay = Arrays.stream(values).map(Expression::causesOfDelay)
                .reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge);
        Expression instance = causesOfDelay.isDelayed()
                ? DelayedExpression.forTooComplex(identifier, booleanType, EmptyExpression.EMPTY_EXPRESSION,
                causesOfDelay)
                : Instance.forTooComplex(identifier, context.evaluationContext().statementIndex(), booleanType);

        // IMPORTANT: instance has to be the last one, it determines type, delay, etc.
        Stream<Expression> components = Stream.concat(Arrays.stream(values), expressions.stream())
                .flatMap(e -> collect(context.getAnalyserContext(), e).stream());
        Expression[] newExpressions = Stream.concat(components.distinct().sorted(), Stream.of(instance))
                .toArray(Expression[]::new);
        MultiExpression multiExpression = new MultiExpression(newExpressions);
        return new MultiExpressions(identifier, context.getAnalyserContext(), multiExpression);
    }

    /*
    goal is to replicate the ContextNotNull environment as much as possible (anything that increases Context
    properties should be evaluated, for consistency's sake)
     */
    private static Set<Expression> collect(InspectionProvider inspectionProvider, Expression expression) {
        ExtractComponentsOfTooComplex ev = new ExtractComponentsOfTooComplex(inspectionProvider);
        expression.visit(ev);
        return ev.getExpressions();
    }
}
