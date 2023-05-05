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

import org.e2immu.analyser.analyser.CausesOfDelay;
import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.model.Element;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.TranslationMap;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.parser.InspectionProvider;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// helper
public record MultiExpression(Expression... expressions) {

    public static MultiExpression create(List<Expression> values) {
        return new MultiExpression(values.toArray(Expression[]::new));
    }

    public ParameterizedType commonType(InspectionProvider inspectionProvider) {
        return Arrays.stream(expressions)
                .map(Expression::returnType)
                .reduce((pt1, pt2) -> pt1.commonType(inspectionProvider, pt2))
                .orElse(ParameterizedType.NULL_CONSTANT);
    }

    public DV getProperty(EvaluationResult context, Property property, boolean duringEvaluation) {
        return Arrays.stream(expressions)
                .filter(Expression::isComputeProperties) // <return value> does NOT contribute!
                .map(value -> context.evaluationContext().getProperty(value, property, duringEvaluation, false))
                .reduce(property.bestDv, DV::min);
    }

    public Stream<Expression> stream() {
        return Arrays.stream(expressions);
    }

    public List<Variable> variables(Element.DescendMode descendMode) {
        return stream().flatMap(e -> e.variables(descendMode).stream()).collect(Collectors.toList());
    }

    @Override
    public String toString() {
        return "[" + Arrays.stream(expressions).map(Object::toString).collect(Collectors.joining(",")) + "]";
    }

    public boolean isEmpty() {
        return expressions.length == 0;
    }

    public MultiExpression translate(InspectionProvider inspectionProvider, TranslationMap translationMap) {
        return new MultiExpression(Arrays.stream(expressions)
                .map(e -> e.translate(inspectionProvider, translationMap)).toArray(Expression[]::new));
    }

    public Expression lastExpression() {
        return expressions[expressions.length - 1];
    }

    public int size() {
        return expressions.length;
    }

    public MultiExpression mergeDelaysOnLastExpression(CausesOfDelay causesOfDelay) {
        Expression[] e = new Expression[expressions.length];
        System.arraycopy(expressions, 0, e, 0, expressions.length);
        e[expressions.length - 1] = expressions[expressions.length - 1].mergeDelays(causesOfDelay);
        return new MultiExpression(e);
    }

    public int getComplexity() {
        return Arrays.stream(expressions).mapToInt(Expression::getComplexity).sum();
    }
}
