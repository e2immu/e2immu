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

import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analyser.ForwardEvaluationInfo;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.annotation.E2Immutable;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@E2Immutable
public record FieldAccess(Expression expression, Variable variable) implements Expression {

    public FieldAccess {
        Objects.requireNonNull(variable);
        Objects.requireNonNull(expression);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FieldAccess that = (FieldAccess) o;
        return expression.equals(that.expression) &&
                variable.equals(that.variable);
    }

    @Override
    public int hashCode() {
        return Objects.hash(expression, variable);
    }

    public static Expression orElse(Expression expression, Expression alternative) {
        return expression == null ? alternative : expression;
    }

    @Override
    public Expression translate(TranslationMap translationMap) {
        return new FieldAccess(translationMap.translateExpression(expression), translationMap.translateVariable(variable));
    }

    @Override
    public int order() {
        return 0;
    }

    @Override
    public ObjectFlow getObjectFlow() {
        return ObjectFlow.NYE;
    }

    @Override
    public boolean hasBeenEvaluated() {
        return false;
    }

    @Override
    public ParameterizedType returnType() {
        return variable.concreteReturnType();
    }

    @Override
    public String toString() {
        return minimalOutput();
    }

    @Override
    public OutputBuilder output(Qualification qualification) {
        return new OutputBuilder().add(variable.output(qualification));
    }

    @Override
    public Precedence precedence() {
        return Precedence.ARRAY_ACCESS;
    }

    @Override
    public List<? extends Element> subElements() {
        return List.of(expression);
    }

    @Override
    public List<Variable> variables() {
        return List.of(variable);
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext evaluationContext, ForwardEvaluationInfo forwardEvaluationInfo) {
        EvaluationResult scopeResult = expression.evaluate(evaluationContext, forwardEvaluationInfo.copyModificationEnsureNotNull());
        Variable newVar;
        if (scopeResult.value() instanceof VariableExpression variableValue && variable instanceof FieldReference fieldReference) {
            newVar = new FieldReference(evaluationContext.getAnalyserContext(),
                    fieldReference.fieldInfo, variableValue.variable());
        } else {
            newVar = variable;
        }
        Expression shortCut = tryShortCut(evaluationContext, newVar);
        if (shortCut != null) {
            return new EvaluationResult.Builder().compose(scopeResult).setExpression(shortCut).build();
        }
        EvaluationResult evaluationResult = VariableExpression.evaluate(evaluationContext, forwardEvaluationInfo, newVar);
        return new EvaluationResult.Builder(evaluationContext).compose(scopeResult, evaluationResult).build();
    }

    /*
    See also EvaluateMethodCall, which has a similar method
     */
    private Expression tryShortCut(EvaluationContext evaluationContext, Variable variable) {
        if (expression instanceof VariableExpression ve && ve.variable() instanceof FieldReference scopeField) {
            FieldAnalysis fieldAnalysis = evaluationContext.getAnalyserContext().getFieldAnalysis(scopeField.fieldInfo);
            if (fieldAnalysis.getEffectivelyFinalValue() instanceof NewObject newObject && newObject.constructor() != null) {
                // we may have direct values for the field
                if (variable instanceof FieldReference fieldReference) {
                    int i = 0;
                    List<ParameterAnalysis> parameterAnalyses = evaluationContext
                            .getParameterAnalyses(newObject.constructor()).collect(Collectors.toList());
                    for (ParameterAnalysis parameterAnalysis : parameterAnalyses) {
                        Map<FieldInfo, ParameterAnalysis.AssignedOrLinked> assigned = parameterAnalysis.getAssignedToField();
                        ParameterAnalysis.AssignedOrLinked assignedOrLinked = assigned.get(fieldReference.fieldInfo);
                        if (assignedOrLinked == ParameterAnalysis.AssignedOrLinked.ASSIGNED) {
                            return newObject.getParameterExpressions().get(i);
                        }
                        i++;
                    }
                }
            }
        }
        return null;
    }
}
