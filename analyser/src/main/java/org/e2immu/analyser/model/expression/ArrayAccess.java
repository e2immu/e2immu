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
import org.e2immu.analyser.model.variable.DependentVariable;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.annotation.E2Container;
import org.e2immu.annotation.NotNull;

import java.util.List;
import java.util.Objects;

@E2Container
public class ArrayAccess extends ElementImpl implements Expression {

    public final Expression expression;
    public final Expression index;
    public final DependentVariable dependentVariable;
    public final ParameterizedType returnType;

    public ArrayAccess(@NotNull Expression expression, @NotNull Expression index) {
        super(Identifier.generate());
        this.expression = Objects.requireNonNull(expression);
        this.index = Objects.requireNonNull(index);
        this.returnType = expression.returnType().copyWithOneFewerArrays();
        dependentVariable = computeDependentVariable(expression, index, returnType);
    }

    private static DependentVariable computeDependentVariable(Expression expression,
                                                              Expression index,
                                                              ParameterizedType returnType) {
        Variable arrayVariable = singleVariable(expression);
        Variable indexVariable = singleVariable(index);
        String name = (arrayVariable == null ? expression.minimalOutput() : arrayVariable.fullyQualifiedName())
                + "[" + (indexVariable == null ? index.minimalOutput() : indexVariable.fullyQualifiedName()) + "]";
        String simpleName = (arrayVariable == null ? expression.minimalOutput() : arrayVariable.simpleName())
                + "[" + (indexVariable == null ? index.minimalOutput() : indexVariable.simpleName()) + "]";
        return new DependentVariable(name, simpleName,
                arrayVariable == null ? null : arrayVariable.getOwningType(),
                returnType, indexVariable == null ? List.of() : List.of(indexVariable), arrayVariable);
    }

    private static Variable singleVariable(Expression expression) {
        VariableExpression ve;
        if ((ve = expression.asInstanceOf(VariableExpression.class)) != null) {
            return ve.variable();
        }
        return null;
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
        return new ArrayAccess(translationMap.translateExpression(expression), translationMap.translateExpression(index));
    }

    @Override
    public int order() {
        return 0;
    }

    @Override
    public DV getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty, boolean duringEvaluation) {
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
        EvaluationResult array = expression.evaluate(evaluationContext, forwardEvaluationInfo.copyNotNull());
        EvaluationResult indexValue = index.evaluate(evaluationContext, forwardEvaluationInfo.copyNotNull());
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext).compose(array, indexValue);

        if (array.value() instanceof ArrayInitializer arrayValue && indexValue.value() instanceof Numeric in) {
            // known array, known index (a[] = {1,2,3}, a[2] == 3)
            int intIndex = in.getNumber().intValue();
            if (intIndex < 0 || intIndex >= arrayValue.multiExpression.expressions().length) {
                throw new ArrayIndexOutOfBoundsException();
            }
            builder.setExpression(arrayValue.multiExpression.expressions()[intIndex]);
        } else {
            boolean delayed = array.value().isDelayed(evaluationContext) || indexValue.value().isDelayed(evaluationContext);
            DependentVariable evaluatedDependentVariable = computeDependentVariable(array.value(), indexValue.value(), returnType);
            // evaluatedDependentVariable is our best effort at evaluation of the individual components

            if (forwardEvaluationInfo.isNotAssignmentTarget()) {
                builder.setExpression(NullConstant.NULL_CONSTANT); // not really relevant
            } else if (delayed) {
                CausesOfDelay causesOfDelay = array.value().causesOfDelay(evaluationContext)
                        .merge(indexValue.value().causesOfDelay(evaluationContext));
                Expression dve = DelayedVariableExpression.forVariable(evaluatedDependentVariable, causesOfDelay);
                builder.setExpression(dve);
            } else {
                if (evaluatedDependentVariable.arrayVariable != null) {
                    builder.variableOccursInNotNullContext(evaluatedDependentVariable.arrayVariable, array.value(),
                            MultiLevel.EFFECTIVELY_NOT_NULL);
                }
                Expression currentValue = builder.currentExpression(evaluatedDependentVariable, forwardEvaluationInfo);
                if (currentValue.isDelayed(evaluationContext)) {
                    // we have no value yet
                    Expression newObject = Instance.genericArrayAccess(getIdentifier(), evaluationContext, array.value(),
                            evaluatedDependentVariable);
                    LinkedVariables linkedVariables = array.value().linkedVariables(evaluationContext);
                    builder.assignment(evaluatedDependentVariable, newObject, linkedVariables);
                    Expression wrappedObject = PropertyWrapper.propertyWrapper(newObject, linkedVariables);
                    builder.setExpression(wrappedObject);
                } else {
                    builder.setExpression(currentValue);
                }
                builder.markRead(evaluatedDependentVariable);
            }
        }

        int notNullRequired = forwardEvaluationInfo.getProperty(VariableProperty.CONTEXT_NOT_NULL);
        VariableExpression ve;
        if (notNullRequired > MultiLevel.NULLABLE &&
                (ve = builder.getExpression().asInstanceOf(VariableExpression.class)) != null) {
            builder.variableOccursInNotNullContext(ve.variable(), builder.getExpression(), notNullRequired);
        }
        return builder.build();
    }
}
