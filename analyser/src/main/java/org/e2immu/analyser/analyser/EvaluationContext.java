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

package org.e2immu.analyser.analyser;

import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.SetUtil;
import org.e2immu.annotation.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.VariableProperty.*;

/**
 * Defaults because of tests
 */
public interface EvaluationContext {
    Logger LOGGER = LoggerFactory.getLogger(EvaluationContext.class);

    default int getIteration() {
        return 0;
    }

    @NotNull
    default TypeInfo getCurrentType() {
        return null;
    }

    default MethodAnalyser getCurrentMethod() {
        return null;
    }

    default StatementAnalyser getCurrentStatement() {
        return null;
    }

    default Location getLocation() {
        return null;
    }

    default Location getLocation(Expression expression) {
        return null;
    }

    default Primitives getPrimitives() {
        return getAnalyserContext().getPrimitives();
    }

    // on top of the normal condition and state in the current statement, we can add decisions from the ?: operator
    default EvaluationContext child(Expression condition) {
        throw new UnsupportedOperationException();
    }

    default EvaluationContext child(Expression condition, boolean disableEvaluationOfMethodCallsUsingCompanionMethods) {
        return child(condition);
    }

    default EvaluationContext childState(Expression state) {
        throw new UnsupportedOperationException();
    }

    default Expression currentValue(Variable variable, int statementTime, boolean isNotAssignmentTarget) {
        throw new UnsupportedOperationException();
    }

    default AnalyserContext getAnalyserContext() {
        throw new UnsupportedOperationException();
    }

    default Stream<ParameterAnalysis> getParameterAnalyses(MethodInfo methodInfo) {
        MethodAnalyser methodAnalyser = getAnalyserContext().getMethodAnalyser(methodInfo);
        return methodAnalyser != null ? methodAnalyser.getParameterAnalysers().stream().map(ParameterAnalyser::getParameterAnalysis)
                : methodInfo.methodInspection.get().getParameters().stream().map(parameterInfo -> parameterInfo.parameterAnalysis.get());
    }

    /**
     * @param duringEvaluation true when this method is called during the EVAL process. It then reads variable's properties from the
     *                         INIT side, rather than current. Current may be MERGE, which is definitely wrong during the EVAL process.
     */
    default int getProperty(Expression value, VariableProperty variableProperty,
                            boolean duringEvaluation,
                            boolean ignoreConditionManager) {
        if (value instanceof VariableExpression variableValue) {
            Variable variable = variableValue.variable();
            if (variable instanceof ParameterInfo parameterInfo) {
                VariableProperty vp = variableProperty == NOT_NULL_EXPRESSION ? NOT_NULL_PARAMETER : variableProperty;
                return getAnalyserContext().getParameterAnalysis(parameterInfo).getProperty(vp);
            }
            if (variable instanceof FieldReference fieldReference) {
                VariableProperty vp = variableProperty == NOT_NULL_EXPRESSION ? EXTERNAL_NOT_NULL : variableProperty;
                return getAnalyserContext().getFieldAnalysis(fieldReference.fieldInfo).getProperty(vp);
            }
            if (variable instanceof This thisVariable) {
                return getAnalyserContext().getTypeAnalysis(thisVariable.typeInfo).getProperty(variableProperty);
            }
            if (variable instanceof PreAspectVariable pre) {
                return pre.valueForProperties().getProperty(this, variableProperty, true);
            }
            throw new UnsupportedOperationException("Variable value of type " + variable.getClass());
        }
        return value.getProperty(this, variableProperty, true); // will work in many cases
    }

    /*
     assumes that currentValue has been queried before!
     */
    default int getProperty(Variable variable, VariableProperty variableProperty) {
        throw new UnsupportedOperationException();
    }

    default int getPropertyFromPreviousOrInitial(Variable variable, VariableProperty variableProperty, int statementTime) {
        throw new UnsupportedOperationException();
    }

    default ConditionManager getConditionManager() {
        return null;
    }

    default boolean isNotNull0(Expression value, boolean useEnnInsteadOfCnn) {
        return true;
    }

    default boolean notNullAccordingToConditionManager(Variable variable) {
        return true;
    }

    default LinkedVariables linkedVariables(Expression value) {
        return value.linkedVariables(this);
    }

    /*
    assumes that currentValue has been queried before!
     */
    default LinkedVariables linkedVariables(Variable variable) {
        return LinkedVariables.EMPTY;
    }

    Set<VariableProperty> VALUE_PROPERTIES = Set.of(IDENTITY, IMMUTABLE, CONTAINER,
            NOT_NULL_EXPRESSION, INDEPENDENT);

    default Map<VariableProperty, Integer> getValueProperties(Expression value) {
        return getValueProperties(value, false);
    }
    /*
    computed/copied during assignment. Critical that NNE is present!
     */
    default Map<VariableProperty, Integer> getValueProperties(Expression value, boolean ignoreConditionManager) {
        Map<VariableProperty, Integer> builder = new HashMap<>();
        for (VariableProperty property : VALUE_PROPERTIES) {
            int v = getProperty(value, property, true, ignoreConditionManager);
            if (v != Level.DELAY) builder.put(property, v);
        }
        return Map.copyOf(builder);
    }

    default Map<VariableProperty, Integer> getVariableProperties(Expression valueToWrite, int statementTime) {
        if (valueToWrite instanceof IsVariableExpression ve) {
            return Map.of(EXTERNAL_NOT_NULL, getPropertyFromPreviousOrInitial(ve.variable(), EXTERNAL_NOT_NULL, statementTime),
                    EXTERNAL_IMMUTABLE, getPropertyFromPreviousOrInitial(ve.variable(), EXTERNAL_IMMUTABLE, statementTime));
        }
        return Map.of();
    }

    /*
    This default implementation is the correct one for basic tests and the companion analyser (we cannot use companions in the
    companion analyser, that would be chicken-and-egg).
     */
    default NewObject currentInstance(Variable variable, int statementTime) {
        if (Primitives.isPrimitiveExcludingVoid(variable.parameterizedType())) return null;
        // always a new one with empty state -- we cannot be bothered here.
        return NewObject.forTesting(getPrimitives(), variable.parameterizedType());
    }

    default boolean disableEvaluationOfMethodCallsUsingCompanionMethods() {
        return getAnalyserContext().inAnnotatedAPIAnalysis();
    }

    default EvaluationContext getClosure() {
        return null;
    }

    default int getInitialStatementTime() {
        return 0;
    }

    default int getFinalStatementTime() {
        return 0;
    }

    default boolean allowedToIncrementStatementTime() {
        return true;
    }

    default Expression replaceLocalVariables(Expression expression) {
        return expression;
    }

    default Expression acceptAndTranslatePrecondition(Expression rest) {
        return null;
    }

    default boolean isPresent(Variable variable) {
        return true;
    }

    default List<PrimaryTypeAnalyser> getLocalPrimaryTypeAnalysers() {
        return List.of();
    }

    default Stream<Map.Entry<String, VariableInfoContainer>> localVariableStream() {
        return Stream.empty();
    }

    default MethodAnalysis findMethodAnalysisOfLambda(MethodInfo methodInfo) {
        MethodAnalysis inLocalPTAs = getLocalPrimaryTypeAnalysers().stream()
                .filter(pta -> pta.primaryType == methodInfo.typeInfo)
                .map(pta -> pta.getMethodAnalysis(methodInfo))
                .findFirst().orElse(null);
        if (inLocalPTAs != null) return inLocalPTAs;
        return getAnalyserContext().getMethodAnalysis(methodInfo);
    }

    default LinkedVariables getStaticallyAssignedVariables(Variable variable, int statementTime) {
        return null;
    }

    default boolean variableIsDelayed(Variable variable) {
        return false;
    }

    default boolean isDelayed(Expression expression) {
        if (expression instanceof DelayedExpression || expression instanceof DelayedVariableExpression) return true;
        try {
            return expression.subElements().stream().anyMatch(e -> e instanceof Expression expr && isDelayed(expr));
        } catch (RuntimeException runtimeException) {
            LOGGER.error("Error computing isDelayed on type " + expression.getClass());
            throw runtimeException;
        }
    }

    default Set<Variable> isDelayedSet(Expression expression) {
        if (expression instanceof DelayedExpression) return Set.of();
        if (expression instanceof DelayedVariableExpression dve) return Set.of(dve.variable());
        try {
            Set<Variable> set = null;
            for (Element element : expression.subElements()) {
                if (element instanceof Expression ex) {
                    Set<Variable> delayed = isDelayedSet(ex);
                    if (delayed != null) {
                        if (set == null) set = delayed;
                        else set = SetUtil.immutableUnion(set, delayed);
                    }
                }
            }
            return set;
        } catch (RuntimeException runtimeException) {
            LOGGER.error("Error computing isDelayed on type " + expression.getClass());
            throw runtimeException;
        }
    }

    default boolean isNotDelayed(Expression expression) {
        return !isDelayed(expression);
    }

    String newObjectIdentifier();

    default This currentThis() {
        return new This(getAnalyserContext(), getCurrentType());
    }

    default Boolean isCurrentlyLinkedToField(Expression objectValue) {
        return false;
    }

    default boolean cannotBeModified(Expression value) {
        return false;
    }

    default MethodInfo concreteMethod(Variable variable, MethodInfo methodInfo) {
        return null;
    }
}
