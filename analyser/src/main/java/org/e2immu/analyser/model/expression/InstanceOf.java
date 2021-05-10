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
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Space;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.UpgradableBooleanMap;
import org.e2immu.annotation.E2Container;
import org.e2immu.annotation.NotNull;

import java.util.List;
import java.util.Objects;


@E2Container
public record InstanceOf(Primitives primitives,
                         ParameterizedType parameterizedType,
                         Expression expression,
                         LocalVariableReference patternVariable) implements Expression {

    public InstanceOf {
        Objects.requireNonNull(parameterizedType);
        Objects.requireNonNull(expression);
        Objects.requireNonNull(primitives);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InstanceOf that = (InstanceOf) o;
        return parameterizedType.equals(that.parameterizedType)
                && expression.equals(that.expression)
                && patternVariable.equals(that.patternVariable);
    }

    @Override
    public int hashCode() {
        return Objects.hash(parameterizedType, expression, patternVariable);
    }

    @Override
    public int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty, boolean duringEvaluation) {
        return UnknownExpression.primitiveGetProperty(variableProperty);
    }

    @Override
    public Expression translate(TranslationMap translationMap) {
        return new InstanceOf(primitives,
                translationMap.translateType(parameterizedType),
                expression == null ? null : expression.translate(translationMap),
                patternVariable == null ? null : (LocalVariableReference) translationMap.translateVariable(patternVariable));
    }

    @Override
    public int order() {
        return ExpressionComparator.ORDER_INSTANCE_OF;
    }

    @Override
    public int internalCompareTo(Expression v) {
        if (expression instanceof VariableExpression ve
                && v instanceof InstanceOf other
                && other.expression instanceof VariableExpression ve2) {
            int c = ve.variable().fullyQualifiedName().compareTo(ve2.variable().fullyQualifiedName());
            if (c == 0) c = parameterizedType.detailedString().compareTo(other.parameterizedType.detailedString());
            return c;
        }
        return expression.internalCompareTo(v);
    }

    @Override
    public OutputBuilder output(Qualification qualification) {
        OutputBuilder ob = new OutputBuilder()
                .add(expression.output(qualification))
                .add(Symbol.INSTANCE_OF)
                .add(parameterizedType.output(qualification));
        if (patternVariable != null) {
            ob.add(Space.ONE).add(patternVariable.output(qualification));
        }
        return ob;
    }

    @Override
    public String toString() {
        return minimalOutput();
    }

    @Override
    public LinkedVariables linkedVariables(EvaluationContext evaluationContext) {
        VariableExpression ve;
        if ((ve = expression.asInstanceOf(VariableExpression.class)) != null) {
            return evaluationContext.linkedVariables(ve.variable());
        }
        return LinkedVariables.EMPTY;
    }

    @Override
    public List<Variable> variables() {
        return expression.variables();
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext evaluationContext, ForwardEvaluationInfo forwardEvaluationInfo) {
        EvaluationResult evaluationResult = expression.evaluate(evaluationContext, forwardEvaluationInfo);
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext).compose(evaluationResult);
        return localEvaluation(builder, evaluationContext, evaluationResult.getExpression());
    }

    private EvaluationResult localEvaluation(EvaluationResult.Builder builder, EvaluationContext evaluationContext, Expression value) {
        Primitives primitives = evaluationContext.getPrimitives();

        if (value.isUnknown()) {
            return builder.setExpression(value).build();
        }
        if (value.isDelayed(evaluationContext)) {
            return builder.setExpression(DelayedExpression
                    .forInstanceOf(evaluationContext.getPrimitives(), parameterizedType)).build();
        }
        if (value instanceof NullConstant) {
            return builder.setExpression(new BooleanConstant(evaluationContext.getPrimitives(), false)).build();
        }
        VariableExpression ve;
        if ((ve = value.asInstanceOf(VariableExpression.class)) != null) {
            if (parameterizedType.isAssignableFrom(InspectionProvider.defaultFrom(primitives), ve.variable().parameterizedType())) {
                return builder.setExpression(new BooleanConstant(primitives, true)).build();
            }
            // no real evaluation
            return builder.setExpression(this).build();
        }
        if (value instanceof NewObject newObject) {
            EvaluationResult er = BooleanConstant.of(parameterizedType.isAssignableFrom(InspectionProvider.defaultFrom(primitives),
                    newObject.parameterizedType()), evaluationContext);
            return builder.compose(er).setExpression(er.value()).build();
        }
        if (value instanceof MethodCall) {
            return builder.setExpression(new UnknownExpression(returnType(), "instanceof value")).build(); // no clue, too deep
        }
        if (value instanceof ClassExpression ce) {
            EvaluationResult er = BooleanConstant.of(parameterizedType.isAssignableFrom(InspectionProvider.defaultFrom(primitives),
                    ce.parameterizedType()), evaluationContext);
            return builder.compose(er).setExpression(er.value()).build();
        }
        // this error occurs with a TypeExpression, probably due to our code giving priority to types rather than
        // variable names, when you use a type name as a variable name, which is perfectly allowed in Java but is
        // horrible practice. We leave the bug for now.
        throw new UnsupportedOperationException("? have expression of " + expression.getClass() + " value is " + value + " of " + value.getClass());
    }

    @Override
    @NotNull
    public ParameterizedType returnType() {
        return primitives.booleanParameterizedType;
    }

    @Override
    public Precedence precedence() {
        return Precedence.INSTANCE_OF;
    }

    @Override
    public List<? extends Element> subElements() {
        return expression != null ? List.of(expression) : List.of();
    }

    @Override
    public boolean isDelayed(EvaluationContext evaluationContext) {
        if (expression instanceof VariableExpression ve) {
            return evaluationContext.variableIsDelayed(ve.variable());
        }
        return expression.isDelayed(evaluationContext);
    }

    @Override
    public UpgradableBooleanMap<TypeInfo> typesReferenced() {
        return UpgradableBooleanMap.of(expression.typesReferenced(), parameterizedType.typesReferenced(true));
    }
}
