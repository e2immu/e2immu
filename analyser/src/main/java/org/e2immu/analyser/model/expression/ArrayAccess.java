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
import org.e2immu.analyser.model.variable.DependentVariable;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.annotation.E2Container;
import org.e2immu.annotation.NotNull;

import java.util.List;
import java.util.Objects;

@E2Container
public class ArrayAccess extends BaseExpression implements Expression {

    public final Expression expression;
    public final Expression index;
    public final DependentVariable dependentVariable;
    public final ParameterizedType returnType;

    public ArrayAccess(Identifier identifier, @NotNull Expression expression, @NotNull Expression index) {
        super(identifier);
        this.expression = Objects.requireNonNull(expression);
        this.index = Objects.requireNonNull(index);
        this.returnType = expression.returnType().copyWithOneFewerArrays();
        dependentVariable = new DependentVariable(identifier, expression, index, returnType, "");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ArrayAccess that = (ArrayAccess) o;
        return expression.equals(that.expression) &&
                index.equals(that.index);
    }

    @Override
    public int hashCode() {
        return Objects.hash(expression, index);
    }

    @Override
    public Expression translate(InspectionProvider inspectionProvider, TranslationMap translationMap) {
        Expression translatedExpression = expression.translate(inspectionProvider, translationMap);
        Expression translatedIndex = index.translate(inspectionProvider, translationMap);
        if (translatedIndex == this.index && translatedExpression == this.expression) return this;
        return new ArrayAccess(identifier, translatedExpression, translatedIndex);
    }

    @Override
    public int order() {
        return 0;
    }

    @Override
    public DV getProperty(EvaluationResult context, Property property, boolean duringEvaluation) {
        if (property == Property.NOT_NULL_EXPRESSION) {
            DV nneArray = context.evaluationContext().getProperty(expression, Property.NOT_NULL_EXPRESSION, duringEvaluation, false);
            if (nneArray.isDelayed()) return nneArray.causesOfDelay();
            return MultiLevel.composeOneLevelLessNotNull(nneArray);
        }
        throw new UnsupportedOperationException("Not yet evaluated");
    }

    @Override
    public ParameterizedType returnType() {
        return returnType;
    }

    @Override
    public OutputBuilder output(Qualification qualification) {
        return new OutputBuilder().add(outputInParenthesis(qualification, precedence(), expression))
                .add(Symbol.LEFT_BRACKET).add(index.output(qualification)).add(Symbol.RIGHT_BRACKET);
    }

    @Override
    public String toString() {
        return minimalOutput();
    }

    @Override
    public Precedence precedence() {
        return Precedence.ARRAY_ACCESS;
    }

    @Override
    public List<? extends Element> subElements() {
        return List.of(expression, index);
    }

    @Override
    public EvaluationResult evaluate(EvaluationResult context, ForwardEvaluationInfo forwardEvaluationInfo) {
        EvaluationResult array = expression.evaluate(context, forwardEvaluationInfo.notNullKeepAssignment());
        EvaluationResult indexValue = index.evaluate(context, forwardEvaluationInfo.notNullNotAssignment());
        EvaluationResult.Builder builder = new EvaluationResult.Builder(context).compose(array, indexValue);

        Expression arrayValue = array.value();
        if (arrayValue instanceof ArrayInitializer initializer && indexValue.value() instanceof Numeric in) {
            // known array, known index (a[] = {1,2,3}, a[2] == 3)
            int intIndex = in.getNumber().intValue();
            if (intIndex < 0 || intIndex >= initializer.multiExpression.expressions().length) {
                throw new ArrayIndexOutOfBoundsException();
            }
            builder.setExpression(initializer.multiExpression.expressions()[intIndex]);
        } else {
            Expression arrayExpression = DependentVariable.singleVariable(expression) != null ? expression : arrayValue;
            boolean delayed = arrayExpression.isDelayed() || indexValue.value().isDelayed();
            DependentVariable evaluatedDependentVariable = new DependentVariable(identifier, expression,
                    indexValue.value(), returnType, context.evaluationContext().statementIndex());
            if (evaluatedDependentVariable.hasArrayVariable()) {
                builder.markRead(evaluatedDependentVariable.arrayVariable());
            }
            if (forwardEvaluationInfo.isAssignmentTarget()) {
                builder.setExpression(new VariableExpression(evaluatedDependentVariable));
            } else {

                // evaluatedDependentVariable is our best effort at evaluation of the individual components
                // we need to mark it as read, even if it is delayed!
                builder.markRead(evaluatedDependentVariable);

                if (delayed) {
                    CausesOfDelay causesOfDelay = arrayExpression.causesOfDelay()
                            .merge(indexValue.value().causesOfDelay());
                    Expression dve = DelayedVariableExpression.forVariable(evaluatedDependentVariable,
                            context.evaluationContext().getInitialStatementTime(), causesOfDelay);

                    builder.setExpression(dve);
                } else {
                    if (evaluatedDependentVariable.hasArrayVariable()) {
                        builder.variableOccursInNotNullContext(evaluatedDependentVariable.arrayVariable(), arrayExpression,
                                MultiLevel.EFFECTIVELY_NOT_NULL_DV, forwardEvaluationInfo.complainInlineConditional());
                    }
                    Expression currentValue = builder.currentExpression(evaluatedDependentVariable, forwardEvaluationInfo);
                    builder.setExpression(currentValue);
                }
            }
        }

        DV notNullRequired = forwardEvaluationInfo.getProperty(Property.CONTEXT_NOT_NULL);
        IsVariableExpression ve;
        if (notNullRequired.gt(MultiLevel.NULLABLE_DV) &&
                (ve = builder.getExpression().asInstanceOf(IsVariableExpression.class)) != null) {
            builder.variableOccursInNotNullContext(ve.variable(), builder.getExpression(), notNullRequired,
                    forwardEvaluationInfo.complainInlineConditional());
        }
        return builder.build();
    }
}
