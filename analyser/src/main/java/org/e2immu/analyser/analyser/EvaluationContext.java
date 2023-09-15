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

import org.e2immu.analyser.analyser.delay.DelayFactory;
import org.e2immu.analyser.analyser.delay.SimpleCause;
import org.e2immu.analyser.analyser.impl.util.BreakDelayLevel;
import org.e2immu.analyser.analysis.FieldAnalysis;
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.variable.*;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.ListUtil;
import org.e2immu.annotation.NotNull;
import org.e2immu.support.Either;
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
        return Expression.SOFT_LIMIT_ON_COMPLEXITY; // can be overridden for testing
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

    default Location getLocation(Stage level) {
        return null;
    }

    default Location getEvaluationLocation(Identifier identifier) {
        return null;
    }

    default Primitives getPrimitives() {
        return getAnalyserContext().getPrimitives();
    }

    // on top of the normal condition and state in the current statement, we can add decisions from the ?: operator
    default EvaluationContext child(Expression condition, Set<Variable> conditionVariables) {
        throw new UnsupportedOperationException();
    }

    default EvaluationContext dropConditionManager() {
        throw new UnsupportedOperationException();
    }

    default EvaluationContext child(Expression condition, Set<Variable> conditionVariables, boolean disableEvaluationOfMethodCallsUsingCompanionMethods) {
        return child(condition, conditionVariables);
    }

    default EvaluationContext childState(Expression state, Set<Variable> stateVariables) {
        throw new UnsupportedOperationException();
    }

    default EvaluationContext updateStatementTime(int statementTime) {
        return this;
    }

    default int getCurrentStatementTime() {
        return getInitialStatementTime();
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

    default Expression currentValue(Variable variable,
                                    Expression scopeValue,
                                    Expression indexValue,
                                    Identifier identifier,
                                    ForwardEvaluationInfo forwardEvaluationInfo) {
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
    default Properties getProperties(Expression value, List<Property> properties, boolean duringEvaluation,
                                     boolean ignoreStateInConditionManager) {
        Properties writable = Properties.writable();
        for (Property property : properties) {
            DV v = getProperty(value, property, duringEvaluation, ignoreStateInConditionManager);
            writable.put(property, v);
        }
        return writable.immutable();
    }

    /**
     * FIXME move to evaluationResult?
     *
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
                /*
                pre-aspect variables must be nullable, because there can be no information, in which case "null" is injected.
                the companion methods must take the null-value into account, see e.g. that of List.addAll, size aspect,
                Modification_26. See also CompanionAnalyser.EvaluationContextImpl.getProperty().
                 */
                if (property == NOT_NULL_EXPRESSION) return MultiLevel.NULLABLE_DV;
                return pre.valueForProperties().getProperty(EvaluationResult.from(this), property, true);
            }
            throw new UnsupportedOperationException("Variable value of type " + variable.getClass());
        }
        return value.getProperty(EvaluationResult.from(this), property, true); // will work in many cases
    }

    /*
     assumes that currentValue has been queried before!
     */
    default DV getProperty(Variable variable, Property property) {
        throw new UnsupportedOperationException();
    }

    default DV getPropertyFromPreviousOrInitial(Variable variable, Property property) {
        throw new UnsupportedOperationException("Not implemented in " + getClass());
    }

    default ConditionManager getConditionManager() {
        return null;
    }

    default DV isNotNull0(Expression value, boolean useEnnInsteadOfCnn, ForwardEvaluationInfo forwardEvaluationInfo) {
        return DV.FALSE_DV;
    }

    default DV notNullAccordingToConditionManager(Variable variable) {
        return DV.FALSE_DV;
    }

    default DV notNullAccordingToConditionManager(Expression expression) {
        return DV.FALSE_DV;
    }

    default LinkedVariables linkedVariables(Variable variable) {
        return LinkedVariables.EMPTY;
    }

    // do not change order: compatible with SingleDelay
    List<Property> VALUE_PROPERTIES = List.of(CONTAINER, IDENTITY, IGNORE_MODIFICATIONS, IMMUTABLE, INDEPENDENT, NOT_NULL_EXPRESSION);

    Properties PRIMITIVE_VALUE_PROPERTIES = Properties.of(Map.of(NOT_NULL_EXPRESSION, MultiLevel.EFFECTIVELY_NOT_NULL_DV,
            IMMUTABLE, MultiLevel.EFFECTIVELY_IMMUTABLE_DV,
            INDEPENDENT, MultiLevel.INDEPENDENT_DV,
            CONTAINER, MultiLevel.CONTAINER_DV,
            IDENTITY, IDENTITY.falseDv,
            IGNORE_MODIFICATIONS, IGNORE_MODIFICATIONS.falseDv));

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
        if (value.isInstanceOf(NullConstant.class)) {
            assert formalType != null : "Use other call!";
            return valuePropertiesOfNullConstant(formalType);
        }
        if (value instanceof UnknownExpression ue && UnknownExpression.RETURN_VALUE.equals(ue.msg())) {
            return valuePropertiesOfFormalType(getCurrentMethod().getMethodInspection().getReturnType());
        }
        return getProperties(value, VALUE_PROPERTIES, true, ignoreConditionInConditionManager);
    }

    default Properties valuePropertiesOfFormalType(ParameterizedType formalType) {
        DV nne = AnalysisProvider.defaultNotNull(formalType).maxIgnoreDelay(NOT_NULL_EXPRESSION.falseDv);
        return valuePropertiesOfFormalType(formalType, nne);
    }

    default Properties valuePropertiesOfFormalType(ParameterizedType formalType, DV notNullExpression) {
        assert notNullExpression.isDone();
        AnalyserContext analyserContext = getAnalyserContext();
        Properties properties = Properties.ofWritable(Map.of(
                IMMUTABLE, analyserContext.typeImmutable(formalType).maxIgnoreDelay(IMMUTABLE.falseDv),
                INDEPENDENT, analyserContext.typeIndependent(formalType).maxIgnoreDelay(INDEPENDENT.falseDv),
                NOT_NULL_EXPRESSION, notNullExpression,
                CONTAINER, analyserContext.typeContainer(formalType).maxIgnoreDelay(CONTAINER.falseDv),
                IDENTITY, IDENTITY.falseDv,
                IGNORE_MODIFICATIONS, IGNORE_MODIFICATIONS.falseDv));
        assert properties.stream().noneMatch(e -> e.getValue().isDelayed());
        return properties;
    }

    default Properties valuePropertiesOfNullConstant(ParameterizedType formalType) {
        AnalyserContext analyserContext = getAnalyserContext();
        return Properties.ofWritable(Map.of(
                IMMUTABLE, analyserContext.typeImmutable(formalType),
                INDEPENDENT, analyserContext.typeIndependent(formalType),
                NOT_NULL_EXPRESSION, AnalysisProvider.defaultNotNull(formalType).maxIgnoreDelay(NOT_NULL_EXPRESSION.falseDv),
                CONTAINER, analyserContext.typeContainer(formalType),
                IDENTITY, IDENTITY.falseDv,
                IGNORE_MODIFICATIONS, IGNORE_MODIFICATIONS.falseDv));
    }

    default Properties defaultValuePropertiesAllowMyself(ParameterizedType formalType, DV nne) {
        AnalyserContext analyserContext = getAnalyserContext();
        DV immutable = isMyself(formalType) ? MultiLevel.MUTABLE_DV
                : analyserContext.typeImmutable(formalType);
        return Properties.ofWritable(Map.of(
                IMMUTABLE, immutable,
                INDEPENDENT, analyserContext.typeIndependent(formalType),
                NOT_NULL_EXPRESSION, nne,
                CONTAINER, analyserContext.typeContainer(formalType),
                IDENTITY, IDENTITY.falseDv,
                IGNORE_MODIFICATIONS, IGNORE_MODIFICATIONS.falseDv));
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

    default Expression acceptAndTranslatePrecondition(Identifier identifier, Expression rest) {
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

    default This currentThis() {
        return new This(getAnalyserContext(), getCurrentType());
    }

    default DV cannotBeModified(Expression value) {
        return DV.FALSE_DV;
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
        if (variable instanceof This) return true;
        if (variable instanceof FieldReference fr && fr.isStatic) return false;
        return isMyself(variable.parameterizedType());
    }

    default boolean isMyselfExcludeThis(Variable variable) {
        if (variable instanceof This) return false;
        if (variable instanceof FieldReference fr && fr.isStatic) return false;
        return isMyself(variable.parameterizedType());
    }

    default boolean isMyself(ParameterizedType type) {
        return getCurrentType().isMyself(type, getAnalyserContext());
    }

    default Properties ensureMyselfValueProperties(Properties existing) {
        Properties p = Properties.of(Map.of(
                IMMUTABLE, IMMUTABLE.falseDv,
                INDEPENDENT, INDEPENDENT.falseDv,
                CONTAINER, CONTAINER.falseDv,
                IDENTITY, IDENTITY.falseDv,
                IGNORE_MODIFICATIONS, IGNORE_MODIFICATIONS.falseDv,
                NOT_NULL_EXPRESSION, NOT_NULL_EXPRESSION.falseDv));
        // combine overwrites
        return existing.combine(p);
    }

    default boolean inConstruction() {
        MethodAnalyser ma = getCurrentMethod();
        return ma != null && ma.getMethodInfo().inConstruction();
    }

    /*
     Store_0 shows an example of a stack overflow going from the ConditionManager.absoluteState via And, Negation,
     Equals, EvaluationContext.isNotNull0, notNullAccordingToConditionManager, findIndividualNullInState and back to
     absoluteState... This method, only applied in Negation at the moment, prevents this infinite loop from occurring.
     */
    default boolean preventAbsoluteStateComputation() {
        return false;
    }

    default EvaluationContext copyToPreventAbsoluteStateComputation() {
        return this;
    }

    /**
     * @param variable   the variable in the nested type
     * @param nestedType the nested type, can be null in case of method references
     * @return true when we want to transfer properties from the nested type to the current type
     */
    default boolean acceptForVariableAccessReport(Variable variable, TypeInfo nestedType) {
        if (variable instanceof FieldReference fr) {
            return fr.fieldInfo.owner != nestedType
                    && (nestedType == null || fr.fieldInfo.owner.primaryType().equals(nestedType.primaryType()))
                    && fr.scopeVariable != null
                    && acceptForVariableAccessReport(fr.scopeVariable, nestedType);
        }
        return isPresent(variable);
    }

    default DependentVariable searchInEquivalenceGroupForLatestAssignment(DependentVariable variable,
                                                                          Expression arrayValue,
                                                                          Expression indexValue,
                                                                          ForwardEvaluationInfo forwardEvaluationInfo) {
        return variable;
    }

    // problem: definedInBlock() is only non-null after the first evaluation
    default boolean isPatternVariableCreatedAt(Variable v, String index) {
        return v.variableNature() instanceof VariableNature.Pattern pvn && index.equals(pvn.definedInBlock());
    }


    Either<CausesOfDelay, Set<Variable>> NO_LOOP_SOURCE_VARIABLES = Either.right(Set.of());

    default Either<CausesOfDelay, Set<Variable>> loopSourceVariables(Variable variable) {
        return NO_LOOP_SOURCE_VARIABLES;
    }

    default Stream<Map.Entry<String, VariableInfoContainer>> variablesFromClosure() {
        return Stream.of();
    }

    default Properties getExternalProperties(Expression valueToWrite) {
        return Properties.EMPTY;
    }

    default BreakDelayLevel breakDelayLevel() {
        return BreakDelayLevel.NONE;
    }

    /*
    modifications on immutable object...
     */
    default boolean inConstructionOrInStaticWithRespectTo(TypeInfo typeInfo) {
        if (inConstruction() || typeInfo == null) return true;
        MethodAnalyser methodAnalyser = getCurrentMethod();
        if (methodAnalyser == null) return false; //?
        /*
         UpgradableBooleanMap: static factory methods: want to return true
         ExternalImmutable_0: static, but not part of construction
         */
        if (methodAnalyser.getMethodInfo().methodInspection.get().isFactoryMethod()) return true;
        return typeInfo.primaryType() == getCurrentType().primaryType()
                && getCurrentType().recursivelyInConstructionOrStaticWithRespectTo(getAnalyserContext(), typeInfo);
    }

    default int initialModificationTimeOrZero(Variable variable) {
        return 0;
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
                DelayFactory.createDelay(new SimpleCause(getLocation(Stage.EVALUATION), CauseOfDelay.Cause.HIDDEN_CONTENT)));
        if (hiddenContentTypes.contains(concreteType)) {
            return new HiddenContent(List.of(concreteType), CausesOfDelay.EMPTY);
        }
        TypeInfo bestType = concreteType.bestTypeInfo(getAnalyserContext());
        if (bestType == null) return NO_HIDDEN_CONTENT; // method type parameter, but not involved in fields of type
        DV immutable = getAnalyserContext().typeImmutable(concreteType);
        if (immutable.equals(MultiLevel.INDEPENDENT_DV)) return NO_HIDDEN_CONTENT;
        if (immutable.isDelayed()) {
            new HiddenContent(List.of(),
                    DelayFactory.createDelay(new SimpleCause(bestType.newLocation(), CauseOfDelay.Cause.HIDDEN_CONTENT))
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

    default Expression getVariableValue(Variable myself,
                                        Expression scopeValue,
                                        Expression indexValue,
                                        Identifier identifier,
                                        VariableInfo variableInfo,
                                        ForwardEvaluationInfo forwardEvaluationInfo) {
        return variableInfo.getValue();
    }

    static Map<Property, DV> delayedValueProperties(CausesOfDelay causes) {
        return VALUE_PROPERTIES.stream().collect(Collectors.toUnmodifiableMap(p -> p, p -> causes));
    }

    default boolean delayStatementBecauseOfECI() {
        return false;
    }

    int getDepth();
}
