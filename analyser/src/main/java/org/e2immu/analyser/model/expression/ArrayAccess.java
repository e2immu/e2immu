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
        dependentVariable = new DependentVariable(identifier, expression, index, returnType);
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
    public Expression translate(TranslationMap translationMap) {
        return new ArrayAccess(identifier, translationMap.translateExpression(expression), translationMap.translateExpression(index));
    }

    @Override
    public int order() {
        return 0;
    }

    @Override
    public DV getProperty(EvaluationContext evaluationContext, Property property, boolean duringEvaluation) {
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
    public EvaluationResult evaluate(EvaluationContext evaluationContext, ForwardEvaluationInfo forwardEvaluationInfo) {
        EvaluationResult array = expression.evaluate(evaluationContext, forwardEvaluationInfo.notNullKeepAssignment());
        EvaluationResult indexValue = index.evaluate(evaluationContext, forwardEvaluationInfo.notNullNotAssignment());
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext).compose(array, indexValue);


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
            DependentVariable evaluatedDependentVariable = new DependentVariable(identifier, expression, indexValue.value(), returnType);
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
                    Expression dve = DelayedVariableExpression.forVariable(evaluatedDependentVariable, causesOfDelay);

                    builder.setExpression(dve);
                } else {
                    if (evaluatedDependentVariable.hasArrayVariable()) {
                        builder.variableOccursInNotNullContext(evaluatedDependentVariable.arrayVariable(), arrayExpression,
                                MultiLevel.EFFECTIVELY_NOT_NULL_DV);
                    }
                    Expression currentValue = builder.currentExpression(evaluatedDependentVariable, forwardEvaluationInfo);
                /*    if (currentValue.isDelayed() || currentValue instanceof UnknownExpression) {
                        // we have no value yet
                        Expression newObject = Instance.genericArrayAccess(getIdentifier(), evaluationContext, arrayValue,
                                evaluatedDependentVariable);
                        DV independent = determineIndependentOfArrayBase(evaluationContext, arrayValue);
                        LinkedVariables linkedVariables = arrayValue.linkedVariables(evaluationContext)
                                .changeAllTo(independent)
                                .merge(LinkedVariables.of(evaluatedDependentVariable, LinkedVariables.ASSIGNED_DV));
                        builder.assignment(evaluatedDependentVariable, newObject, linkedVariables);
                        Expression wrappedObject = PropertyWrapper.propertyWrapper(newObject, linkedVariables);
                        builder.setExpression(wrappedObject);
                    } else {*/
                    builder.setExpression(currentValue);
                    //  }
                }
            }
        }

        DV notNullRequired = forwardEvaluationInfo.getProperty(Property.CONTEXT_NOT_NULL);
        IsVariableExpression ve;
        if (notNullRequired.gt(MultiLevel.NULLABLE_DV) &&
                (ve = builder.getExpression().asInstanceOf(IsVariableExpression.class)) != null) {
            builder.variableOccursInNotNullContext(ve.variable(), builder.getExpression(), notNullRequired);
        }
        return builder.build();
    }
}
