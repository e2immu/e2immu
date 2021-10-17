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

import org.e2immu.analyser.analyser.util.DelayDebugger;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.DelayedExpression;
import org.e2immu.analyser.model.expression.DelayedVariableExpression;
import org.e2immu.analyser.model.expression.NewObject;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.ListUtil;
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
public interface EvaluationContext extends DelayDebugger {
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

    default Location getLocation(Identifier identifier) {
        return null;
    }

    default Primitives getPrimitives() {
        return getAnalyserContext().getPrimitives();
    }

    // on top of the normal condition and state in the current statement, we can add decisions from the ?: operator
    default EvaluationContext child(Expression condition) {
        throw new UnsupportedOperationException();
    }

    default EvaluationContext dropConditionManager() {
        throw new UnsupportedOperationException();
    }

    default EvaluationContext child(Expression condition, boolean disableEvaluationOfMethodCallsUsingCompanionMethods) {
        return child(condition);
    }

    default EvaluationContext childState(Expression state) {
        throw new UnsupportedOperationException();
    }

    default Expression currentValue(Variable variable, int statementTime, ForwardEvaluationInfo forwardEvaluationInfo) {
        throw new UnsupportedOperationException("In " + getClass());
    }

    default AnalyserContext getAnalyserContext() {
        throw new UnsupportedOperationException();
    }

    default Stream<ParameterAnalysis> getParameterAnalyses(MethodInfo methodInfo) {
        MethodAnalyser methodAnalyser = getAnalyserContext().getMethodAnalyser(methodInfo);
        return methodAnalyser != null ? methodAnalyser.getParameterAnalysers().stream()
                .map(ParameterAnalyser::getParameterAnalysis)
                : methodInfo.methodInspection.get(methodInfo.fullyQualifiedName)
                .getParameters().stream().map(parameterInfo ->
                        parameterInfo.parameterAnalysis.get(parameterInfo.fullyQualifiedName()));
    }

    /**
     * @param duringEvaluation true when this method is called during the EVAL process. It then reads variable's properties from the
     *                         INIT side, rather than current. Current may be MERGE, which is definitely wrong during the EVAL process.
     */
    default int getProperty(Expression value, VariableProperty variableProperty,
                            boolean duringEvaluation,
                            boolean ignoreStateInConditionManager) {
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
        assert value != null;
        VariableExpression ve;
        if ((ve = value.asInstanceOf(VariableExpression.class)) != null) {
            return linkedVariables(ve.variable());
        }
        return value.linkedVariables(this);
    }

    default LinkedVariables linked1Variables(Expression value) {
        assert value != null;
        VariableExpression ve;
        if ((ve = value.asInstanceOf(VariableExpression.class)) != null) {
            return linked1Variables(ve.variable());
        }
        return value.linked1VariablesValue(this);
    }

    /*
    assumes that currentValue has been queried before!
     */
    default LinkedVariables linkedVariables(Variable variable) {
        return LinkedVariables.EMPTY;
    }

    default LinkedVariables linked1Variables(Variable variable) {
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
    default Map<VariableProperty, Integer> getValueProperties(Expression value, boolean ignoreConditionInConditionManager) {
        Map<VariableProperty, Integer> builder = new HashMap<>();
        for (VariableProperty property : VALUE_PROPERTIES) {
            int v = getProperty(value, property, true, ignoreConditionInConditionManager);
            if (v != Level.DELAY) builder.put(property, v);
        }
        return Map.copyOf(builder);
    }

    /*
    This default implementation is the correct one for basic tests and the companion analyser (we cannot use companions in the
    companion analyser, that would be chicken-and-egg).
     */
    default Expression currentValue(Variable variable, int statementTime) {
        if (Primitives.isPrimitiveExcludingVoid(variable.parameterizedType())) return null;
        // a new one with empty state -- we cannot be bothered here.
        return NewObject.forTesting(variable.parameterizedType());
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
                .filter(pta -> pta.primaryTypes.contains(methodInfo.typeInfo))
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
        if (expression.isInstanceOf(DelayedExpression.class) || expression.isInstanceOf(DelayedVariableExpression.class)) {
            return true;
        }
        try {
            // not a stream, easier to debug
            for (Element sub : expression.subElements()) {
                if (sub instanceof Expression e && isDelayed(e)) {
                    return true;
                }
            }
            return false;
        } catch (RuntimeException runtimeException) {
            LOGGER.error("Error computing isDelayed on type " + expression.getClass());
            throw runtimeException;
        }
    }

    default Set<Variable> isDelayedSet(Expression expression) {
        if (expression.isInstanceOf(DelayedExpression.class)) return Set.of();
        DelayedVariableExpression dve;
        if ((dve = expression.asInstanceOf(DelayedVariableExpression.class)) != null) return Set.of(dve.variable());
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

    default This currentThis() {
        return new This(getAnalyserContext(), getCurrentType());
    }

    default boolean cannotBeModified(Expression value) {
        return false;
    }

    default MethodInfo concreteMethod(Variable variable, MethodInfo methodInfo) {
        return null;
    }

    default String statementIndex() {
        return "-";
    }

    default boolean firstAssignmentOfFieldInConstructor(Variable variable) {
        MethodAnalyser cm = getCurrentMethod();
        if (cm == null) return false;
        if (!cm.methodInfo.isConstructor) return false;
        if (!(variable instanceof FieldReference)) return false;
        return !hasBeenAssigned(variable);
    }

    default boolean hasBeenAssigned(Variable variable) {
        return false;
    }

    /*
    if the formal type is T (hidden content), then the expression is returned is List.of(expression).
    It is important to return the expression, because it may have a dynamic immutability higher than its formal value,
    e.g., as a result of another method call.

    if the formal type is Collection<T>, we'll have to go and search for the concrete type represented by T.
    We then return a list of the concrete type parameters, as TypeExpression.
    This again allows us to compute immutability values better than formal.
     */

    record HiddenContent(List<ParameterizedType> hiddenTypes, boolean delay) {
        public HiddenContent {
            assert hiddenTypes != null;
        }

        public HiddenContent merge(HiddenContent other) {
            return new HiddenContent(ListUtil.concatImmutable(hiddenTypes, other.hiddenTypes), delay || other.delay);
        }
    }

    HiddenContent NO_HIDDEN_CONTENT = new HiddenContent(List.of(), false);
    HiddenContent HIDDEN_CONTENT_DELAYED = new HiddenContent(List.of(), true);

    default HiddenContent extractHiddenContentTypes(ParameterizedType concreteType, HiddenContentTypes hiddenContentTypes) {
        if (hiddenContentTypes == null) return HIDDEN_CONTENT_DELAYED;
        if (hiddenContentTypes.contains(concreteType)) {
            return new HiddenContent(List.of(concreteType), false);
        }
        TypeInfo bestType = concreteType.bestTypeInfo(getAnalyserContext());
        if (bestType == null) return NO_HIDDEN_CONTENT; // method type parameter, but not involved in fields of type
        int immutable = concreteType.defaultImmutable(getAnalyserContext(), false);
        if (immutable == MultiLevel.INDEPENDENT) return NO_HIDDEN_CONTENT;
        if (immutable <= Level.DELAY) return HIDDEN_CONTENT_DELAYED; // and type analysis not available

        // hidden content is more complex
        TypeInspection bestInspection = getAnalyserContext().getTypeInspection(bestType);
        if (!bestInspection.typeParameters().isEmpty()) {
            return concreteType.parameters.stream()
                    .reduce(NO_HIDDEN_CONTENT, (hc, pt) -> extractHiddenContentTypes(pt, hiddenContentTypes),
                            HiddenContent::merge);
        }
        return NO_HIDDEN_CONTENT;
    }

    // meant for computing method analyser, computing field analyser

    default boolean hasState(Expression expression) {
        if (expression.cannotHaveState()) return false;
        VariableExpression ve;
        if ((ve = expression.asInstanceOf(VariableExpression.class)) != null) {
            if (ve.variable() instanceof FieldReference fr) {
                FieldAnalysis fa = getAnalyserContext().getFieldAnalysis(fr.fieldInfo);
                return fa.getEffectivelyFinalValue() != null &&
                        fa.getEffectivelyFinalValue().hasState();
            }
            return false; // no way we have this info here
        }
        return expression.hasState();
    }

    default Expression state(Expression expression) {
        VariableExpression ve;
        if ((ve = expression.asInstanceOf(VariableExpression.class)) != null) {
            if (ve.variable() instanceof FieldReference fr) {
                FieldAnalysis fa = getAnalyserContext().getFieldAnalysis(fr.fieldInfo);
                return fa.getEffectivelyFinalValue().state();
            }
            throw new UnsupportedOperationException();
        }
        return expression.state();
    }

    default VariableInfo findOrThrow(Variable variable) {
        throw new UnsupportedOperationException();
    }
}
