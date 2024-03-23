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
import org.e2immu.analyser.analyser.impl.context.EvaluationResultImpl;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.model.impl.BaseExpression;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.graph.analyser.PackedInt;
import org.e2immu.analyser.util2.PackedIntMap;
import org.e2immu.analyser.util.UpgradableBooleanMap;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

public class Cast extends BaseExpression implements Expression {

    private final Expression expression;
    private final ParameterizedType parameterizedType;

    public Cast(Identifier identifier, Expression expression, ParameterizedType parameterizedType) {
        super(identifier, 2 + expression.getComplexity());
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
    public Expression translate(InspectionProvider inspectionProvider, TranslationMap translationMap) {
        Expression translated = translationMap.translateExpression(this);
        if (translated != this) return translated;

        Expression translatedExpression = expression.translate(inspectionProvider, translationMap);
        ParameterizedType translatedType = translationMap.translateType(this.parameterizedType);
        if (translatedExpression == this.expression && translatedType == this.parameterizedType) return this;
        return new Cast(identifier, translatedExpression, translatedType);
    }

    @Override
    public int order() {
        return 0;
    }

    @Override
    public int internalCompareTo(Expression v) throws ExpressionComparator.InternalError {
        if (v instanceof Cast cast) {
            return expression.compareTo(cast.expression);
        }
        throw new ExpressionComparator.InternalError();
    }

    @Override
    public EvaluationResult evaluate(EvaluationResult context, ForwardEvaluationInfo forwardEvaluationInfo) {
        EvaluationResult er = expression.evaluate(context, forwardEvaluationInfo);
        if (forwardEvaluationInfo.isOnlySort()) {
            Cast newCast = new Cast(identifier, er.getExpression(), parameterizedType);
            return new EvaluationResultImpl.Builder(context).compose(er).setExpression(newCast).build();
        }
        if (parameterizedType.equals(er.getExpression().returnType())) return er;
        Expression result = PropertyWrapper.propertyWrapper(er.getExpression(), null, parameterizedType);

        return new EvaluationResultImpl.Builder(context).compose(er).setExpression(result).build();
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
    public PackedIntMap<TypeInfo> typesReferenced2(PackedInt weight) {
        return PackedIntMap.of(expression.typesReferenced2(weight), parameterizedType.typesReferenced2(weight));
    }

    @Override
    public List<? extends Element> subElements() {
        return List.of(expression);
    }

    @Override
    public void visit(Predicate<Element> predicate) {
        if (predicate.test(this)) {
            expression.visit(predicate);
        }
    }

    @Override
    public void visit(Visitor visitor) {
        if (visitor.beforeExpression(this)) {
            expression.visit(visitor);
        }
        visitor.afterExpression(this);
    }

    @Override
    public DV getProperty(EvaluationResult context, Property property, boolean duringEvaluation) {
        if (property == Property.IMMUTABLE || property == Property.CONTAINER || property == Property.INDEPENDENT) {
            return context.getAnalyserContext().getProperty(parameterizedType, property);
        }
        return context.evaluationContext().getProperty(expression, property, duringEvaluation, false);
    }

    @Override
    public CausesOfDelay causesOfDelay() {
        return expression.causesOfDelay();
    }

    @Override
    public Expression mergeDelays(CausesOfDelay causesOfDelay) {
        if (expression.isDelayed()) {
            return new Cast(identifier, expression.mergeDelays(causesOfDelay), parameterizedType);
        }
        return this;
    }

    public Expression getExpression() {
        return expression;
    }

    public ParameterizedType getParameterizedType() {
        return parameterizedType;
    }
}
