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
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.util.UpgradableBooleanMap;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record Cast(Expression expression, ParameterizedType parameterizedType) implements Expression {

    public Cast(Expression expression, ParameterizedType parameterizedType) {
        this.expression = Objects.requireNonNull(expression);
        this.parameterizedType = Objects.requireNonNull(parameterizedType);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Cast cast = (Cast) o;
        return expression.equals(cast.expression) &&
                parameterizedType.equals(cast.parameterizedType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(expression, parameterizedType);
    }

    @Override
    public Expression translate(TranslationMap translationMap) {
        return new Cast(translationMap.translateExpression(expression), translationMap.translateType(parameterizedType));
    }

    @Override
    public int order() {
        return 0;
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext evaluationContext, ForwardEvaluationInfo forwardEvaluationInfo) {
        EvaluationResult er = expression.evaluate(evaluationContext, forwardEvaluationInfo);

        if(parameterizedType.equals(er.getExpression().returnType())) return er;
        Expression result = PropertyWrapper.propertyWrapper(expression, Map.of(), parameterizedType);

        return new EvaluationResult.Builder(evaluationContext).compose(er).setExpression(result).build();
    }

    @Override
    public LinkedVariables linkedVariables(EvaluationContext evaluationContext) {
        return expression.linkedVariables(evaluationContext);
    }

    @Override
    public ParameterizedType returnType() {
        return parameterizedType;
    }

    @Override
    public OutputBuilder output(Qualification qualification) {
        return new OutputBuilder().add(Symbol.LEFT_PARENTHESIS).add(parameterizedType.output(qualification)).add(Symbol.RIGHT_PARENTHESIS)
                .add(outputInParenthesis(qualification, precedence(), expression));
    }

    @Override
    public Precedence precedence() {
        return Precedence.UNARY;
    }

    @Override
    public UpgradableBooleanMap<TypeInfo> typesReferenced() {
        return UpgradableBooleanMap.of(expression.typesReferenced(), parameterizedType.typesReferenced(true));
    }

    @Override
    public List<? extends Element> subElements() {
        return List.of(expression);
    }
}
