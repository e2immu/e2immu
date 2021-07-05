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
                && Objects.equals(patternVariable, that.patternVariable);
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
        Expression e;
        if(v instanceof InlineConditional inlineConditional) {
            e = inlineConditional.condition;
        } else {
            e = v;
        }
        if (e instanceof InstanceOf other) {
            if (expression instanceof VariableExpression ve
                    && other.expression instanceof VariableExpression ve2) {
                int c = ve.variable().fullyQualifiedName().compareTo(ve2.variable().fullyQualifiedName());
                if (c == 0) c = parameterizedType.detailedString().compareTo(other.parameterizedType.detailedString());
                return c;
            }
            int c = parameterizedType.fullyQualifiedName().compareTo(other.parameterizedType.fullyQualifiedName());
            if (c != 0) return c;
            return expression.compareTo(other.expression);
        }
        throw new UnsupportedOperationException("Comparing to "+e+" -- "+e.getClass());
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
        // do not pass on the forward requirements on to expression! See e.g. InstanceOf_8
        EvaluationResult evaluationResult = expression.evaluate(evaluationContext, ForwardEvaluationInfo.DEFAULT);
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext).compose(evaluationResult);


        Primitives primitives = evaluationContext.getPrimitives();
        Expression value = evaluationResult.value();

        if (value.isUnknown()) {
            return builder.setExpression(value).build();
        }
        if (value.isDelayed(evaluationContext)) {
            return builder.setExpression(DelayedExpression
                    .forInstanceOf(evaluationContext.getPrimitives(), parameterizedType, value.variables())).build();
        }
        if (value instanceof NullConstant) {
            return builder.setExpression(new BooleanConstant(evaluationContext.getPrimitives(), false)).build();
        }
        VariableExpression ve;
        if ((ve = value.asInstanceOf(VariableExpression.class)) != null) {
            if (parameterizedType.isAssignableFrom(InspectionProvider.defaultFrom(primitives), ve.variable().parameterizedType())) {
                return builder.setExpression(new BooleanConstant(primitives, true)).build();
            }
        }
        NewObject newObject;
        if ((newObject = value.asInstanceOf(NewObject.class)) != null) {
            EvaluationResult er = BooleanConstant.of(parameterizedType.isAssignableFrom(InspectionProvider.defaultFrom(primitives),
                    newObject.parameterizedType()), evaluationContext);
            return builder.compose(er).setExpression(er.value()).build();
        }
        if (value.isInstanceOf(MethodCall.class)) {
            return builder.setExpression(this).build(); // no clue, too deep
        }

        // whatever it is, it is not null; we're more interested in that, than it its type which is guarded by the compiler
        Expression notNull = Negation.negate(evaluationContext,
                Equals.equals(evaluationContext, expression, NullConstant.NULL_CONSTANT));
        InstanceOf newInstanceOf = new InstanceOf(primitives, parameterizedType, evaluationResult.getExpression(), null);
        return builder
                .setExpression(new And(evaluationContext.getPrimitives()).append(evaluationContext, newInstanceOf, notNull))
                .build();
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
