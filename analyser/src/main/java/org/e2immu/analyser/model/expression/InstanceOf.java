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
import org.e2immu.analyser.model.impl.BaseExpression;
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
public class InstanceOf extends BaseExpression implements Expression {

    private final Primitives primitives;
    private final ParameterizedType parameterizedType;
    private final Expression expression;
    private final LocalVariableReference patternVariable;

    public InstanceOf(Identifier identifier,
                      Primitives primitives,
                      ParameterizedType parameterizedType,
                      Expression expression,
                      LocalVariableReference patternVariable) {
        super(identifier);
        this.parameterizedType = Objects.requireNonNull(parameterizedType);
        this.expression = Objects.requireNonNull(expression);
        this.primitives = Objects.requireNonNull(primitives);
        this.patternVariable = patternVariable;
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
    public DV getProperty(EvaluationContext evaluationContext, Property property, boolean duringEvaluation) {
        return UnknownExpression.primitiveGetProperty(property);
    }

    @Override
    public Expression translate(TranslationMap translationMap) {
        ParameterizedType translatedType = translationMap.translateType(this.parameterizedType);
        Expression translatedExpression = expression == null ? null : expression.translate(translationMap);
        LocalVariableReference translatedLvr = patternVariable == null ? null
                : (LocalVariableReference) translationMap.translateVariable(patternVariable);
        if (translatedType == parameterizedType && translatedExpression == expression && translatedLvr == patternVariable) {
            return this;
        }
        return new InstanceOf(identifier, primitives, translatedType, translatedExpression, translatedLvr);
    }

    @Override
    public int order() {
        return ExpressionComparator.ORDER_INSTANCE_OF;
    }

    @Override
    public int internalCompareTo(Expression v) {
        Expression e;
        if (v instanceof InlineConditional inlineConditional) {
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
        throw new UnsupportedOperationException("Comparing to " + e + " -- " + e.getClass());
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

    // makes sense if there is a patternVariable, and we continue with that patternVariable

    @Override
    public LinkedVariables linkedVariables(EvaluationContext evaluationContext) {
        return expression.linkedVariables(evaluationContext);
    }

    @Override
    public List<Variable> variables(boolean descendIntoFieldReferences) {
        return expression.variables(descendIntoFieldReferences);
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext evaluationContext, ForwardEvaluationInfo forwardEvaluationInfo) {
        // do not pass on the forward requirements on to expression! See e.g. InstanceOf_8
        EvaluationResult evaluationResult = expression.evaluate(evaluationContext, ForwardEvaluationInfo.DEFAULT);
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext).compose(evaluationResult);


        Primitives primitives = evaluationContext.getPrimitives();
        Expression value = evaluationResult.value();

        if (value.isEmpty()) {
            return builder.setExpression(value).build();
        }
        if (value.isDelayed()) {
            LinkedVariables linkedVariables = value.linkedVariables(evaluationContext);
            return builder.setExpression(DelayedExpression.forInstanceOf(evaluationContext.getPrimitives(),
                            parameterizedType, linkedVariables.changeAllToDelay(value.causesOfDelay()), value.causesOfDelay()))
                    .build();
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
        Instance instance;
        if ((instance = value.asInstanceOf(Instance.class)) != null) {
            EvaluationResult er = BooleanConstant.of(parameterizedType.isAssignableFrom(InspectionProvider.defaultFrom(primitives),
                    instance.parameterizedType()), evaluationContext);
            return builder.compose(er).setExpression(er.value()).build();
        }
        if (value.isInstanceOf(MethodCall.class)) {
            return builder.setExpression(this).build(); // no clue, too deep
        }

        // whatever it is, it is not null; we're more interested in that, than it its type which is guarded by the compiler
        Expression notNull = Negation.negate(evaluationContext,
                Equals.equals(evaluationContext, expression, NullConstant.NULL_CONSTANT));
        InstanceOf newInstanceOf = new InstanceOf(identifier,
                primitives, parameterizedType, evaluationResult.getExpression(), null);
        return builder
                .setExpression(And.and(evaluationContext, newInstanceOf, notNull))
                .build();
    }

    @Override
    @NotNull
    public ParameterizedType returnType() {
        return primitives.booleanParameterizedType();
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
    public CausesOfDelay causesOfDelay() {
        return expression.causesOfDelay();
    }

    @Override
    public Expression mergeDelays(CausesOfDelay causesOfDelay) {
        if (expression.isDelayed()) {
            return new InstanceOf(identifier, primitives, parameterizedType, expression.mergeDelays(causesOfDelay), patternVariable);
        }
        return this;
    }

    @Override
    public UpgradableBooleanMap<TypeInfo> typesReferenced() {
        return UpgradableBooleanMap.of(expression.typesReferenced(), parameterizedType.typesReferenced(true));
    }

    public LocalVariableReference patternVariable() {
        return patternVariable;
    }

    public ParameterizedType parameterizedType() {
        return parameterizedType;
    }

    public Expression expression() {
        return expression;
    }
}
