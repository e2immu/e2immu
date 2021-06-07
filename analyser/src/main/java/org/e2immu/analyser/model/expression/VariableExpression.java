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
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.ListUtil;
import org.e2immu.analyser.util.UpgradableBooleanMap;
import org.e2immu.annotation.E2Container;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

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
                return ve.evaluate(evaluationContext, ForwardEvaluationInfo.DEFAULT);
            }
            return new EvaluationResult.Builder().setExpression(inMap).build();
        }
        if (variable instanceof FieldReference fieldReference && fieldReference.scope instanceof VariableExpression ve) {
            // the variable itself is not in the map, but we may have to substitute
            // (see EventuallyImmutableUtil_5, s1.bool with substitution s1 -> t.s1
            // IMPROVE how should we go recursive here? we should call reEvaluate, but may bump into
            // unknown fields (t is known, but t.s1 is not), which causes infinite delays.
            Expression scopeInMap = translation.get(ve);
            if (scopeInMap instanceof VariableExpression newScope) {
                Variable newFieldRef = new FieldReference(evaluationContext.getAnalyserContext(), fieldReference.fieldInfo, newScope);
                return new EvaluationResult.Builder(evaluationContext)
                        .setExpression(new VariableExpression(newFieldRef)).build();
            }
        }
        return new EvaluationResult.Builder(evaluationContext).setExpression(this).markRead(variable).build();
    }

    @Override
    public boolean isDelayed(EvaluationContext evaluationContext) {
        return evaluationContext.variableIsDelayed(variable);
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext evaluationContext, ForwardEvaluationInfo forwardEvaluationInfo) {
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext);
        EvaluationResult scopeResult;
        if (variable instanceof FieldReference fr && fr.scope != null) {
            // do not continue modification onto This: we want modifications on this only when there's a direct method call
            ForwardEvaluationInfo forward = fr.scopeIsThis() ? ForwardEvaluationInfo.NOT_NULL :
                    forwardEvaluationInfo.copyModificationEnsureNotNull();
            scopeResult = fr.scope.evaluate(evaluationContext, forward);
            builder.compose(scopeResult);
        } else {
            scopeResult = null;
        }

        Expression currentValue = builder.currentExpression(variable, forwardEvaluationInfo);
        Expression adjustedScope = adjustScope(evaluationContext.getAnalyserContext(), scopeResult, currentValue);

        builder.setExpression(adjustedScope);

        // no statement analyser... we're in the shallow analyser
        if (evaluationContext.getCurrentStatement() == null) return builder.build();

        if (variable instanceof This thisVar && !thisVar.typeInfo.equals(evaluationContext.getCurrentType())) {
            builder.markRead(evaluationContext.currentThis());
        }
        if (forwardEvaluationInfo.isNotAssignmentTarget()) {
            builder.markRead(variable);
            VariableExpression ve;
            if ((ve = adjustedScope.asInstanceOf(VariableExpression.class)) != null) {
                builder.markRead(ve.variable);
                if (ve.variable instanceof This thisVar && !thisVar.typeInfo.equals(evaluationContext.getCurrentType())) {
                    builder.markRead(evaluationContext.currentThis());
                }
            }
        }

        int notNull = forwardEvaluationInfo.getProperty(VariableProperty.CONTEXT_NOT_NULL);
        if (notNull > MultiLevel.NULLABLE) {
            builder.variableOccursInNotNullContext(variable, adjustedScope, notNull);
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
            builder.variableOccursInNotModified1Context(variable, adjustedScope);
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

        // having done all this, we do try for a shortcut
        if (scopeResult != null) {
            Expression shortCut = tryShortCut(evaluationContext, scopeResult.value(), currentValue);
            if (shortCut != null) {
                builder.setExpression(shortCut);
            }
        }
        return builder.build();
    }

    private Expression adjustScope(InspectionProvider inspectionProvider, EvaluationResult scopeResult, Expression currentValue) {
        if (scopeResult != null) {
            if (currentValue instanceof VariableExpression ve
                    && ve.variable() instanceof FieldReference fr && !fr.scope.equals(scopeResult.value())) {
                return new VariableExpression(new FieldReference(inspectionProvider, fr.fieldInfo, scopeResult.getExpression()));
            }
            if (currentValue instanceof DelayedVariableExpression ve
                    && ve.variable() instanceof FieldReference fr && !fr.scope.equals(scopeResult.value())) {
                return DelayedVariableExpression.forField(new FieldReference(inspectionProvider, fr.fieldInfo, scopeResult.getExpression()));
            }
        }
        return currentValue;
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
        if (variable instanceof FieldReference fr && fr.scope != null && !fr.scopeIsThis()) {
            return ListUtil.concatImmutable(fr.scope.variables(), List.of(variable));
        }
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

    @Override
    public List<? extends Element> subElements() {
        if (variable instanceof FieldReference fr && fr.scope != null && !fr.scopeIsThis()) {
            return List.of(fr.scope);
        }
        return List.of();
    }

    private Expression tryShortCut(EvaluationContext evaluationContext, Expression scopeValue, Expression variableValue) {
        if (variableValue instanceof VariableExpression ve && ve.variable instanceof FieldReference fr) {
            if (scopeValue instanceof NewObject newObject && newObject.constructor() != null) {
                return extractNewObject(evaluationContext, newObject, fr.fieldInfo);
            }
            if (scopeValue instanceof VariableExpression scopeVe && scopeVe.variable instanceof FieldReference scopeFr) {
                FieldAnalysis fieldAnalysis = evaluationContext.getAnalyserContext().getFieldAnalysis(scopeFr.fieldInfo);
                if (fieldAnalysis.getEffectivelyFinalValue() instanceof NewObject newObject && newObject.constructor() != null) {
                    return extractNewObject(evaluationContext, newObject, fr.fieldInfo);
                }
            }
        }
        return null;
    }

    private Expression extractNewObject(EvaluationContext evaluationContext, NewObject newObject, FieldInfo
            fieldInfo) {
        int i = 0;
        List<ParameterAnalysis> parameterAnalyses = evaluationContext
                .getParameterAnalyses(newObject.constructor()).collect(Collectors.toList());
        for (ParameterAnalysis parameterAnalysis : parameterAnalyses) {
            Map<FieldInfo, ParameterAnalysis.AssignedOrLinked> assigned = parameterAnalysis.getAssignedToField();
            ParameterAnalysis.AssignedOrLinked assignedOrLinked = assigned.get(fieldInfo);
            if (assignedOrLinked == ParameterAnalysis.AssignedOrLinked.ASSIGNED) {
                return newObject.getParameterExpressions().get(i);
            }
            i++;
        }
        return null;
    }
}
