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

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.impl.BaseExpression;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.output.Text;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.annotation.E2Container;
import org.e2immu.annotation.NotNull;

import java.util.List;
import java.util.Objects;

@E2Container
public class ArrayLength extends BaseExpression implements Expression {
    private final Primitives primitives;
    private final Expression scope;

    public ArrayLength(Primitives primitives, @NotNull Expression scope) {
        super(Identifier.generate());
        this.scope = Objects.requireNonNull(scope);
        this.primitives = primitives;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ArrayLength that = (ArrayLength) o;
        return scope.equals(that.scope);
    }

    @Override
    public int hashCode() {
        return Objects.hash(scope);
    }

    @Override
    public Expression translate(TranslationMap translationMap) {
        Expression translated = scope.translate(translationMap);
        if (translated == scope) return this;
        return new ArrayLength(primitives, translated);
    }

    @Override
    public CausesOfDelay causesOfDelay() {
        return scope.causesOfDelay();
    }

    @Override
    public Expression mergeDelays(CausesOfDelay causesOfDelay) {
        if (scope.isDelayed()) {
            return new ArrayLength(primitives, scope.mergeDelays(causesOfDelay));
        }
        return this;
    }

    @Override
    public int order() {
        return 0;
    }

    @Override
    public List<? extends Element> subElements() {
        return List.of(scope);
    }

    @Override
    public ParameterizedType returnType() {
        return primitives.intParameterizedType();
    }

    @Override
    public String toString() {
        return minimalOutput();
    }

    @Override
    public OutputBuilder output(Qualification qualification) {
        return new OutputBuilder().add(outputInParenthesis(qualification, precedence(), scope)).add(Symbol.DOT).add(new Text("length"));
    }

    @Override
    public Precedence precedence() {
        return Precedence.ARRAY_ACCESS;
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext evaluationContext, ForwardEvaluationInfo forwardEvaluationInfo) {
        EvaluationResult result = scope.evaluate(evaluationContext, forwardEvaluationInfo.notNullNotAssignment());
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext).compose(result);

        if (result.value() instanceof ArrayInitializer arrayInitializer) {
            Expression size = new IntConstant(evaluationContext.getPrimitives(), arrayInitializer.multiExpression.expressions().length);
            builder.setExpression(size);
        } else if (result.value().isDelayed()) {
            builder.setExpression(DelayedExpression.forArrayLength(evaluationContext.getPrimitives(), result.value().causesOfDelay()));
        } else {
            builder.setExpression(this);
        }
        return builder.build();
    }

    @Override
    public DV getProperty(EvaluationContext evaluationContext, Property property, boolean duringEvaluation) {
        return ConstantExpression.propertyOfConstant(property);
    }
}
