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

import org.e2immu.analyser.analyser.delay.SimpleCause;
import org.e2immu.analyser.analyser.delay.SimpleSet;
import org.e2immu.analyser.analysis.FieldAnalysis;
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.inspector.MethodResolution;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.Instance;
import org.e2immu.analyser.model.expression.NullConstant;
import org.e2immu.analyser.model.expression.UnknownExpression;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.ListUtil;
import org.e2immu.annotation.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.Property.*;

/**
 * Defaults because of tests
 */
public interface EvaluationContext {
    Logger LOGGER = LoggerFactory.getLogger(EvaluationContext.class);

    default int limitOnComplexity() {
        return 100; // can be overridden for testing
    }

    default int getIteration() {
        return 0;
    }

    @NotNull
    default TypeInfo getCurrentType() {
        return null;
    }

    // convenient in breakpoints while debugging
    @SuppressWarnings("unused")
    default String safeMethodName() {
        MethodAnalyser ma = getCurrentMethod();
        return ma == null ? null : ma.getMethodInfo().name;
    }

    default MethodAnalyser getCurrentMethod() {
        return null;
    }

    default StatementAnalyser getCurrentStatement() {
        return null;
    }

    default boolean haveCurrentStatement() {
        return getCurrentStatement() != null;
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

    default Expression currentValue(Variable variable, ForwardEvaluationInfo forwardEvaluationInfo) {
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

    // will have a more performant implementation in SAEvaluationContext,
    // because getVariableProperty is pretty expensive
    default Properties getProperties(Expression value, Set<Property> properties, boolean duringEvaluation,
                                     boolean ignoreStateInConditionManager) {
        Properties writable = Properties.writable();
        for (Property property : properties) {
            DV v = getProperty(value, property, duringEvaluation, ignoreStateInConditionManager);
            writable.put(property, v);
        }
        return writable.immutable();
    }

    /**
     * @param duringEvaluation true when this method is called during the EVAL process. It then reads variable's properties from the
     *                         INIT side, rather than current. Current may be MERGE, which is definitely wrong during the EVAL process.
     */
    default DV getProperty(Expression value, Property property,
                           boolean duringEvaluation,
                           boolean ignoreStateInConditionManager) {
        if (value instanceof VariableExpression variableValue) {
            Variable variable = variableValue.variable();
            if (variable instanceof ParameterInfo parameterInfo) {
                Property vp = property == NOT_NULL_EXPRESSION ? NOT_NULL_PARAMETER : property;
                return getAnalyserContext().getParameterAnalysis(parameterInfo).getProperty(vp);
            }
            if (variable instanceof FieldReference fieldReference) {
                Property vp = property == NOT_NULL_EXPRESSION ? EXTERNAL_NOT_NULL : property;
                return getAnalyserContext().getFieldAnalysis(fieldReference.fieldInfo).getProperty(vp);
            }
            if (variable instanceof This thisVariable) {
                return getAnalyserContext().getTypeAnalysis(thisVariable.typeInfo).getProperty(property);
            }
            if (variable instanceof PreAspectVariable pre) {
                return pre.valueForProperties().getProperty(this, property, true);
            }
            throw new UnsupportedOperationException("Variable value of type " + variable.getClass());
        }
        return value.getProperty(this, property, true); // will work in many cases
    }

    /*
     assumes that currentValue has been queried before!
     */
    default DV getProperty(Variable variable, Property property) {
        throw new UnsupportedOperationException();
    }

    default DV getPropertyFromPreviousOrInitial(Variable variable, Property property) {
        throw new UnsupportedOperationException();
    }

    default ConditionManager getConditionManager() {
        return null;
    }

    default boolean isNotNull0(Expression value, boolean useEnnInsteadOfCnn) {
        return true;
    }

    default boolean notNullAccordingToConditionManager(Variable variable) {
        return false;
    }

    default boolean notNullAccordingToConditionManager(Expression expression) {
        return false;
    }

    default LinkedVariables linkedVariables(Variable variable) {
        return LinkedVariables.EMPTY;
    }

    // DO NOT change this set unless you adapt NewObject as well; it maintains a set of value properties
    Set<Property> VALUE_PROPERTIES = Set.of(IDENTITY, IMMUTABLE, CONTAINER, NOT_NULL_EXPRESSION, INDEPENDENT);

    Properties PRIMITIVE_VALUE_PROPERTIES = Properties.of(Map.of(NOT_NULL_EXPRESSION, MultiLevel.EFFECTIVELY_NOT_NULL_DV,
            IMMUTABLE, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV,
            INDEPENDENT, MultiLevel.INDEPENDENT_DV,
            CONTAINER, MultiLevel.CONTAINER_DV,
            IDENTITY, DV.FALSE_DV));

    default Properties getValueProperties(Expression value) {
        return getValueProperties(null, value, false);
    }

    default Properties getValueProperties(ParameterizedType formalType, Expression value) {
        return getValueProperties(formalType, value, false);
    }

    // NOTE: when the value is a VariableExpression pointing to a variable field, variable in loop or anything that
    // causes findForReading to generate a new VariableInfoImpl, this loop will cause 5x the same logic to be applied.
    // should be able to do better/faster.
    default Properties getValueProperties(ParameterizedType formalType, Expression value, boolean ignoreConditionInConditionManager) {
        if (value instanceof NullConstant) {
            assert formalType != null : "Use other call!";
            return valuePropertiesOfNullConstant(formalType);
        }
        if (value instanceof UnknownExpression ue && UnknownExpression.RETURN_VALUE.equals(ue.msg())) {
            return valuePropertiesOfFormalType(getCurrentMethod().getMethodInspection().getReturnType());
        }
        return getProperties(value, VALUE_PROPERTIES, true, ignoreConditionInConditionManager);
    }

    default Properties valuePropertiesOfFormalType(ParameterizedType formalType) {
        AnalyserContext analyserContext = getAnalyserContext();
        Properties properties = Properties.ofWritable(Map.of(
                IMMUTABLE, analyserContext.defaultImmutable(formalType, false).maxIgnoreDelay(IMMUTABLE.falseDv),
                INDEPENDENT, analyserContext.defaultIndependent(formalType).maxIgnoreDelay(INDEPENDENT.falseDv),
                NOT_NULL_EXPRESSION, AnalysisProvider.defaultNotNull(formalType).maxIgnoreDelay(NOT_NULL_EXPRESSION.falseDv),
                CONTAINER, analyserContext.defaultContainer(formalType).maxIgnoreDelay(CONTAINER.falseDv),
                IDENTITY, DV.FALSE_DV));
        assert properties.stream().noneMatch(e -> e.getValue().isDelayed());
        return properties;
    }

    default Properties valuePropertiesOfNullConstant(ParameterizedType formalType) {
        AnalyserContext analyserContext = getAnalyserContext();
        return Properties.ofWritable(Map.of(
                IMMUTABLE, analyserContext.defaultImmutable(formalType, false),
                INDEPENDENT, analyserContext.defaultIndependent(formalType),
                NOT_NULL_EXPRESSION, AnalysisProvider.defaultNotNull(formalType).maxIgnoreDelay(NOT_NULL_EXPRESSION.falseDv),
                CONTAINER, analyserContext.defaultContainer(formalType),
                IDENTITY, DV.FALSE_DV));
    }

    /*
    This default implementation is the correct one for basic tests and the companion analyser (we cannot use companions in the
    companion analyser, that would be chicken-and-egg).
     */
    default Expression currentValue(Variable variable) {
        if (variable.parameterizedType().isPrimitiveExcludingVoid()) return null;
        // a new one with empty state -- we cannot be bothered here.
        return Instance.forTesting(variable.parameterizedType());
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
                .filter(pta -> pta.containsPrimaryType(methodInfo.typeInfo))
                .map(pta -> pta.getMethodAnalysis(methodInfo))
                .findFirst().orElse(null);
        if (inLocalPTAs != null) return inLocalPTAs;
        return getAnalyserContext().getMethodAnalysis(methodInfo);
    }

    default CausesOfDelay variableIsDelayed(Variable variable) {
        return CausesOfDelay.EMPTY;
    }

    default CausesOfDelay isDelayed(Expression expression) {
        return expression.causesOfDelay();
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
        if (!cm.getMethodInfo().isConstructor) return false;
        if (!(variable instanceof FieldReference)) return false;
        return !hasBeenAssigned(variable);
    }

    default boolean hasBeenAssigned(Variable variable) {
        return false;
    }

    /*
    should we compute context immutable? not if we're a variable of the type itself
     */
    default boolean isMyself(Variable variable) {
        return isMyself(variable.parameterizedType());
    }

    default boolean isMyself(ParameterizedType parameterizedType) {
        InspectionProvider inspectionProvider = getAnalyserContext();
        TypeInfo bestType = parameterizedType.bestTypeInfo(inspectionProvider);
        TypeInfo myself = getCurrentType();
        if (myself.equals(bestType)) return true;
        TypeInfo primaryVariable = bestType == null ? null : bestType.primaryType();
        TypeInfo primaryMyself = myself.primaryType();
        if (primaryMyself.equals(primaryVariable)) {
            // in the same compilation unit, analysed at the same time
            return bestType.parentalHierarchyContains(myself, inspectionProvider) ||
                    myself.parentalHierarchyContains(bestType, inspectionProvider) ||
                    bestType.nonStaticallyEnclosingTypesContains(myself, inspectionProvider) ||
                    myself.nonStaticallyEnclosingTypesContains(bestType, inspectionProvider);
        }
        return false;
    }

    default Properties ensureMyselfValueProperties(Properties existing) {
        Properties p = Properties.of(Map.of(
                IMMUTABLE, IMMUTABLE.falseDv,
                INDEPENDENT, INDEPENDENT.falseDv,
                CONTAINER, CONTAINER.falseDv,
                IDENTITY, IDENTITY.falseDv,
                NOT_NULL_EXPRESSION, NOT_NULL_EXPRESSION.falseDv));
        // combine overwrites
        return existing.combine(p);
    }

    default boolean inConstruction() {
        MethodAnalyser ma = getCurrentMethod();
        return ma != null && ma.getMethodInfo().inConstruction();
    }

    /*
    if the formal type is T (hidden content), then the expression is returned is List.of(expression).
    It is important to return the expression, because it may have a dynamic immutability higher than its formal value,
    e.g., as a result of another method call.

    if the formal type is Collection<T>, we'll have to go and search for the concrete type represented by T.
    We then return a list of the concrete type parameters, as TypeExpression.
    This again allows us to compute immutability values better than formal.
     */

    record HiddenContent(List<ParameterizedType> hiddenTypes, CausesOfDelay causesOfDelay) {
        public HiddenContent {
            assert hiddenTypes != null;
        }

        public HiddenContent merge(HiddenContent other) {
            return new HiddenContent(ListUtil.concatImmutable(hiddenTypes, other.hiddenTypes),
                    causesOfDelay.merge(other.causesOfDelay));
        }
    }

    HiddenContent NO_HIDDEN_CONTENT = new HiddenContent(List.of(), CausesOfDelay.EMPTY);


    default HiddenContent extractHiddenContentTypes(ParameterizedType concreteType, SetOfTypes hiddenContentTypes) {
        if (hiddenContentTypes == null) return new HiddenContent(List.of(),
                new SimpleSet(new SimpleCause(getLocation(), CauseOfDelay.Cause.HIDDEN_CONTENT)));
        if (hiddenContentTypes.contains(concreteType)) {
            return new HiddenContent(List.of(concreteType), CausesOfDelay.EMPTY);
        }
        TypeInfo bestType = concreteType.bestTypeInfo(getAnalyserContext());
        if (bestType == null) return NO_HIDDEN_CONTENT; // method type parameter, but not involved in fields of type
        DV immutable = getAnalyserContext().defaultImmutable(concreteType, false);
        if (immutable.equals(MultiLevel.INDEPENDENT_DV)) return NO_HIDDEN_CONTENT;
        if (immutable.isDelayed()) {
            new HiddenContent(List.of(),
                    new SimpleSet(new SimpleCause(bestType.newLocation(), CauseOfDelay.Cause.HIDDEN_CONTENT))
                            .merge(immutable.causesOfDelay()));
        }

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
                return fa.getValue() != null &&
                        fa.getValue().hasState();
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
                return fa.getValue().state();
            }
            throw new UnsupportedOperationException();
        }
        return expression.state();
    }

    default VariableInfo findOrThrow(Variable variable) {
        throw new UnsupportedOperationException();
    }

    default Expression getVariableValue(Variable myself, VariableInfo variableInfo) {
        return variableInfo.getValue();
    }

    static Map<Property, DV> delayedValueProperties(CausesOfDelay causes) {
        return VALUE_PROPERTIES.stream().collect(Collectors.toUnmodifiableMap(p -> p, p -> causes));
    }
}
