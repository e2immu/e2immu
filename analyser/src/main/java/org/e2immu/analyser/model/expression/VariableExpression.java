/*
 * e2immu-analyser: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.e2immu.analyser.model.expression;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.UpgradableBooleanMap;
import org.e2immu.annotation.E2Container;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@E2Container
public record VariableExpression(Variable variable,
                                 String name,
                                 ObjectFlow objectFlow) implements Expression, IsVariableExpression {

    public VariableExpression(Variable variable) {
        this(variable, ObjectFlow.NO_FLOW);
    }

    public VariableExpression(Variable variable, ObjectFlow objectFlow) {
        this(variable, variable.fullyQualifiedName(), objectFlow);
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
        Variable inMap = translationMap.variables.get(variable);
        if (inMap != null) {
            return new VariableExpression(inMap);
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

    @Override
    public ObjectFlow getObjectFlow() {
        return objectFlow;
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
            return new EvaluationResult.Builder().setExpression(inMap).build();
        }
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext);
        builder.setExpression(this);
        builder.markRead(variable);
        return builder.build();
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext evaluationContext, ForwardEvaluationInfo forwardEvaluationInfo) {
        return evaluate(evaluationContext, forwardEvaluationInfo, replaceSuperByThis(evaluationContext, variable));
    }

    @Override
    public boolean isDelayed(EvaluationContext evaluationContext) {
        return evaluationContext.variableIsDelayed(variable);
    }

    public static Variable replaceSuperByThis(EvaluationContext evaluationContext, Variable variable) {
        if (variable instanceof This tv && tv.typeInfo != evaluationContext.getCurrentType()) {
            return new This(evaluationContext.getAnalyserContext(), evaluationContext.getCurrentType());
        }
        return variable;
    }

    // code also used by FieldAccess
    public static EvaluationResult evaluate(EvaluationContext evaluationContext,
                                            ForwardEvaluationInfo forwardEvaluationInfo,
                                            Variable variable) {
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext);
        Expression currentValue = builder.currentExpression(variable, forwardEvaluationInfo.isNotAssignmentTarget());
        builder.setExpression(currentValue);

        // no statement analyser... we're in the shallow analyser
        if (evaluationContext.getCurrentStatement() == null) return builder.build();

        if (forwardEvaluationInfo.isNotAssignmentTarget()) {
            builder.markRead(variable);
            if (currentValue instanceof VariableExpression ve) {
                builder.markRead(ve.variable);
            }
        } else if (variable instanceof FieldReference fieldReference && fieldReference.scope instanceof This thisVar) {
            builder.markRead(thisVar);
        }

        int notNull = forwardEvaluationInfo.getProperty(VariableProperty.CONTEXT_NOT_NULL);
        if (notNull > MultiLevel.NULLABLE) {
            builder.variableOccursInNotNullContext(variable, currentValue, notNull);
        }
        int modified = forwardEvaluationInfo.getProperty(VariableProperty.CONTEXT_MODIFIED);
        if (modified != Level.DELAY) {
            builder.markContextModified(variable, modified);
            // do not check for implicit this!! otherwise, any x.y will also affect this.y
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
    public SideEffect sideEffect(EvaluationContext evaluationContext) {
        return variable.sideEffect(Objects.requireNonNull(evaluationContext));
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
