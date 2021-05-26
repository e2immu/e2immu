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
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.UpgradableBooleanMap;
import org.e2immu.annotation.E2Container;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@E2Container
public record VariableExpression(Variable variable, String name) implements Expression, IsVariableExpression {

    public VariableExpression(Variable variable) {
        this(variable, variable.fullyQualifiedName());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VariableExpression that)) return false;
        return variable.equals(that.variable);
    }

    @Override
    public int hashCode() {
        return Objects.hash(variable);
    }

    @Override
    public Expression translate(TranslationMap translationMap) {
        Variable translated = translationMap.translateVariable(variable);
        if (translated != variable) {
            return new VariableExpression(translated);
        }
        return this;
    }

    @Override
    public int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty, boolean duringEvaluation) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int order() {
        return ExpressionComparator.ORDER_VARIABLE;
    }

    @Override
    public int internalCompareTo(Expression v) {
        VariableExpression variableValue;
        if (v instanceof InlineConditional inlineConditional)
            variableValue = (VariableExpression) inlineConditional.condition;
        else if (v instanceof VariableExpression ve) variableValue = ve;
        else throw new UnsupportedOperationException();
        return name.compareTo(variableValue.name);
    }

    @Override
    public boolean isNumeric() {
        TypeInfo typeInfo = variable.parameterizedType().bestTypeInfo();
        return Primitives.isNumeric(typeInfo);
    }

    @Override
    public NewObject getInstance(EvaluationResult evaluationResult) {
        return evaluationResult.evaluationContext().currentInstance(variable, evaluationResult.statementTime());
    }

    /*
    the purpose of having this extra "markRead" here (as compared to the default implementation in Expression),
    is to ensure that fields exist when they are encountered -- reEvaluate is called from the single return value of
    method; if this one returns a field, that field has to be made available to the next iteration; see Enum_3 statement 0 in
    posInList

    Full evaluation causes a lot of trouble with improper delays because we have no decent ForwardEvaluationInfo
     */
    @Override
    public EvaluationResult reEvaluate(EvaluationContext evaluationContext, Map<Expression, Expression> translation) {
        Expression inMap = translation.get(this);
        if (inMap != null) {
            VariableExpression ve;
            if ((ve = inMap.asInstanceOf(VariableExpression.class)) != null) {
                return evaluate(evaluationContext, ForwardEvaluationInfo.DEFAULT, ve.variable);
            }
            return new EvaluationResult.Builder().setExpression(inMap).build();
        }
        if (variable instanceof FieldReference fieldReference && fieldReference.scope != null) {
            // the variable itself is not in the map, but we may have to substitute
            // (see EventuallyImmutableUtil_5, s1.bool with substitution s1 -> t.s1
            // IMPROVE how should we go recursive here? we should call reEvaluate, but may bump into
            // unknown fields (t is known, but t.s1 is not), which causes infinite delays.
            Expression scopeInMap = translation.get(new VariableExpression(fieldReference.scope));
            if (scopeInMap instanceof VariableExpression newScope) {
                Variable newFieldRef = new FieldReference(evaluationContext.getAnalyserContext(), fieldReference.fieldInfo, newScope.variable);
                return new EvaluationResult.Builder(evaluationContext)
                        .setExpression(new VariableExpression(newFieldRef)).build();
            }
        }
        return new EvaluationResult.Builder(evaluationContext).setExpression(this).markRead(variable).build();
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext evaluationContext, ForwardEvaluationInfo forwardEvaluationInfo) {
        return evaluate(evaluationContext, forwardEvaluationInfo, variable);
    }

    @Override
    public boolean isDelayed(EvaluationContext evaluationContext) {
        return evaluationContext.variableIsDelayed(variable);
    }

    // code also used by FieldAccess
    public static EvaluationResult evaluate(EvaluationContext evaluationContext,
                                            ForwardEvaluationInfo forwardEvaluationInfo,
                                            Variable variable) {
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext);
        Expression currentValue = builder.currentExpression(variable, forwardEvaluationInfo);
        builder.setExpression(currentValue);

        // no statement analyser... we're in the shallow analyser
        if (evaluationContext.getCurrentStatement() == null) return builder.build();

        if (forwardEvaluationInfo.isNotAssignmentTarget()) {
            builder.markRead(variable);
            if (variable instanceof This thisVar && !thisVar.typeInfo.equals(evaluationContext.getCurrentType())) {
                builder.markRead(evaluationContext.currentThis());
            }
            VariableExpression ve;
            if ((ve = currentValue.asInstanceOf(VariableExpression.class)) != null) {
                builder.markRead(ve.variable);
                if (ve.variable instanceof This thisVar && !thisVar.typeInfo.equals(evaluationContext.getCurrentType())) {
                    builder.markRead(evaluationContext.currentThis());
                }
            }
        } else if (variable instanceof FieldReference fieldReference && fieldReference.scope instanceof This thisVar) {
            builder.markRead(thisVar);
            // if super is read, then this should be read to
            if (!thisVar.typeInfo.equals(evaluationContext.getCurrentType())) {
                builder.markRead(evaluationContext.currentThis());
            } // TODO: and do all types "in between"
        }

        int notNull = forwardEvaluationInfo.getProperty(VariableProperty.CONTEXT_NOT_NULL);
        if (notNull > MultiLevel.NULLABLE) {
            builder.variableOccursInNotNullContext(variable, currentValue, notNull);
        }
        int modified = forwardEvaluationInfo.getProperty(VariableProperty.CONTEXT_MODIFIED);
        if (modified != Level.DELAY) {
            builder.markContextModified(variable, modified);
            // do not check for implicit this!! otherwise, any x.y will also affect this.y

            // if super is modified, then this should be modified to
            if (variable instanceof This thisVar && !thisVar.typeInfo.equals(evaluationContext.getCurrentType())) {
                builder.markContextModified(evaluationContext.currentThis(), modified);
            }
        }

        int notModified1 = forwardEvaluationInfo.getProperty(VariableProperty.NOT_MODIFIED_1);
        if (notModified1 == Level.TRUE) {
            builder.variableOccursInNotModified1Context(variable, currentValue);
        }

        int methodCalled = forwardEvaluationInfo.getProperty(VariableProperty.METHOD_CALLED);
        if (methodCalled == Level.TRUE) {
            builder.markMethodCalled(variable);
        }

        int contextModifiedDelay = forwardEvaluationInfo.getProperty(VariableProperty.CONTEXT_MODIFIED_DELAY);
        if (contextModifiedDelay == Level.TRUE) {
            builder.markContextModifiedDelay(variable);
        }

        int contextNotNullDelay = forwardEvaluationInfo.getProperty(VariableProperty.CONTEXT_NOT_NULL_DELAY);
        if (contextNotNullDelay == Level.TRUE) {
            builder.markContextNotNullDelay(variable);
        }

        int contextImmutable = forwardEvaluationInfo.getProperty(VariableProperty.CONTEXT_IMMUTABLE);
        int nextImmutable = forwardEvaluationInfo.getProperty(VariableProperty.NEXT_CONTEXT_IMMUTABLE);
        if (contextImmutable > MultiLevel.MUTABLE) {
            builder.variableOccursInEventuallyImmutableContext(variable, contextImmutable, nextImmutable);
        }

        int contextImmutableDelay = forwardEvaluationInfo.getProperty(VariableProperty.CONTEXT_IMMUTABLE_DELAY);
        if (contextImmutableDelay == Level.TRUE) {
            builder.markContextImmutableDelay(variable);
        }

        // calling an abstract method without MODIFIED value (Level.DELAY)
        int propagate = forwardEvaluationInfo.getProperty(VariableProperty.CONTEXT_PROPAGATE_MOD);
        if (propagate == Level.TRUE) {
            assert modified == Level.FALSE;
            builder.markPropagateModification(variable);
        }

        // forEach(consumer) -> consumer gets a CONTEXT_PROPAGATE_MOD = Level.TRUE
        int propagateModification = forwardEvaluationInfo.getProperty(VariableProperty.PROPAGATE_MODIFICATION);
        if (propagateModification == Level.TRUE) {
            builder.markPropagateModification(variable);
        }

        // when we don't know yet if forEach( ...)'s first parameter has the @PropagateModification annotation
        int propagateModificationDelay = forwardEvaluationInfo.getProperty(VariableProperty.PROPAGATE_MODIFICATION_DELAY);
        if (propagateModificationDelay == Level.TRUE) {
            builder.markPropagateModificationDelay(variable);
        }
        return builder.build();
    }

    @Override
    public LinkedVariables linkedVariables(EvaluationContext evaluationContext) {
        throw new UnsupportedOperationException(); // should be caught be evaluation context
    }

    @Override
    public ParameterizedType returnType() {
        return variable.parameterizedType();
    }

    @Override
    public Precedence precedence() {
        return Precedence.TOP;
    }

    @Override
    public List<Variable> variables() {
        return List.of(variable);
    }

    @Override
    public OutputBuilder output(Qualification qualification) {
        return new OutputBuilder().add(variable.output(qualification));
    }

    @Override
    public String toString() {
        return minimalOutput();
    }

    @Override
    public UpgradableBooleanMap<TypeInfo> typesReferenced() {
        return variable.typesReferenced(false);
    }
}
