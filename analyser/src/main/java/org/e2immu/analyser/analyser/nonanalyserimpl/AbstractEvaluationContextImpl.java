package org.e2immu.analyser.analyser.nonanalyserimpl;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analyser.delay.DelayFactory;
import org.e2immu.analyser.analyser.delay.SimpleCause;
import org.e2immu.analyser.analyser.impl.util.BreakDelayLevel;
import org.e2immu.analyser.analysis.FieldAnalysis;
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.Instance;
import org.e2immu.analyser.model.expression.UnknownExpression;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.variable.*;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.annotation.NotNull;
import org.e2immu.support.Either;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.Property.*;
import static org.e2immu.analyser.analyser.Property.NOT_NULL_EXPRESSION;

public abstract class AbstractEvaluationContextImpl implements EvaluationContext {


    @Override
    public int limitOnComplexity() {
        return Expression.SOFT_LIMIT_ON_COMPLEXITY; // can be overridden for testing
    }

    public int getIteration() {
        return 0;
    }

    @NotNull
    public TypeInfo getCurrentType() {
        return null;
    }

    // convenient in breakpoints while debugging
    @SuppressWarnings("unused")
    public String safeMethodName() {
        MethodAnalyser ma = getCurrentMethod();
        return ma == null ? null : ma.getMethodInfo().name;
    }

    public MethodAnalyser getCurrentMethod() {
        return null;
    }

    public StatementAnalyser getCurrentStatement() {
        return null;
    }

    public boolean haveCurrentStatement() {
        return getCurrentStatement() != null;
    }

    public Location getLocation(Stage level) {
        return null;
    }

    public Location getEvaluationLocation(Identifier identifier) {
        return null;
    }

    public Primitives getPrimitives() {
        return getAnalyserContext().getPrimitives();
    }

    // on top of the normal condition and state in the current statement, we can add decisions from the ?: operator
    public EvaluationContext child(Expression condition, Set<Variable> conditionVariables) {
        throw new UnsupportedOperationException();
    }

    public EvaluationContext dropConditionManager() {
        throw new UnsupportedOperationException();
    }

    public EvaluationContext child(Expression condition, Set<Variable> conditionVariables, boolean disableEvaluationOfMethodCallsUsingCompanionMethods) {
        return child(condition, conditionVariables);
    }

    public EvaluationContext childState(Expression state, Set<Variable> stateVariables) {
        throw new UnsupportedOperationException();
    }

    public EvaluationContext updateStatementTime(int statementTime) {
        return this;
    }

    public int getCurrentStatementTime() {
        return getInitialStatementTime();
    }

    /*
     This public implementation is the correct one for basic tests and the companion analyser (we cannot use companions in the
     companion analyser, that would be chicken-and-egg).
    */
    public Expression currentValue(Variable variable) {
        if (variable.parameterizedType().isPrimitiveExcludingVoid()) return null;
        // a new one with empty state -- we cannot be bothered here.
        return Instance.forTesting(variable.parameterizedType());
    }

    public Expression currentValue(Variable variable,
                                   Expression scopeValue,
                                   Expression indexValue,
                                   Identifier identifier,
                                   ForwardEvaluationInfo forwardEvaluationInfo) {
        throw new UnsupportedOperationException("In " + getClass());
    }

    public AnalyserContext getAnalyserContext() {
        throw new UnsupportedOperationException();
    }

    public Stream<ParameterAnalysis> getParameterAnalyses(MethodInfo methodInfo) {
        MethodAnalyser methodAnalyser = getAnalyserContext().getMethodAnalyser(methodInfo);
        return methodAnalyser != null ? methodAnalyser.getParameterAnalysers().stream()
                .map(ParameterAnalyser::getParameterAnalysis)
                : methodInfo.methodInspection.get(methodInfo.fullyQualifiedName)
                .getParameters().stream().map(parameterInfo ->
                        parameterInfo.parameterAnalysis.get(parameterInfo.fullyQualifiedName()));
    }

    // will have a more performant implementation in SAEvaluationContext,
    // because getVariableProperty is pretty expensive
    public Properties getProperties(IsMyself isMyself,
                                    Expression value,
                                    List<Property> properties,
                                    boolean duringEvaluation,
                                    boolean ignoreStateInConditionManager) {
        Properties writable = Properties.writable();
        for (Property property : properties) {
            DV v = isMyself.toFalse(property) ? property.falseDv
                    : getProperty(value, property, duringEvaluation, ignoreStateInConditionManager);
            writable.put(property, v);
        }
        return writable.immutable();
    }

    /*
     assumes that currentValue has been queried before!
     */
    public DV getProperty(Variable variable, Property property) {
        throw new UnsupportedOperationException();
    }

    public DV getPropertyFromPreviousOrInitial(Variable variable, Property property) {
        throw new UnsupportedOperationException("Not implemented in " + getClass());
    }

    public ConditionManager getConditionManager() {
        return null;
    }

    public DV isNotNull0(Expression value, boolean useEnnInsteadOfCnn, ForwardEvaluationInfo forwardEvaluationInfo) {
        return DV.FALSE_DV;
    }

    public DV notNullAccordingToConditionManager(Variable variable) {
        return DV.FALSE_DV;
    }

    public DV notNullAccordingToConditionManager(Expression expression) {
        return DV.FALSE_DV;
    }

    public LinkedVariables linkedVariables(Variable variable) {
        return LinkedVariables.EMPTY;
    }

    public Properties getValueProperties(Expression value) {
        return getValueProperties(null, value, false);
    }

    public Properties getValueProperties(ParameterizedType formalType, Expression value) {
        return getValueProperties(formalType, value, false);
    }

    // NOTE: when the value is a VariableExpression pointing to a variable field, variable in loop or anything that
    // causes findForReading to generate a new VariableInfoImpl, this loop will cause 5x the same logic to be applied.
    // should be able to do better/faster.
    public Properties getValueProperties(ParameterizedType formalType, Expression value, boolean ignoreConditionInConditionManager) {
        if (value.isNullConstant()) {
            assert formalType != null : "Use other call!";
            IsMyself isMyself = isMyself(formalType);
            return valuePropertiesOfNullConstant(isMyself, formalType);
        }
        if (value instanceof UnknownExpression ue && UnknownExpression.RETURN_VALUE.equals(ue.msg())) {
            return valuePropertiesOfFormalType(getCurrentMethod().getMethodInspection().getReturnType());
        }
        IsMyself isMyself = formalType == null ? IsMyself.NO : isMyself(formalType);
        return getProperties(isMyself, value, VALUE_PROPERTIES, true, ignoreConditionInConditionManager);
    }

    public Properties valuePropertiesOfFormalType(ParameterizedType formalType) {
        DV nne = AnalysisProvider.defaultNotNull(formalType).maxIgnoreDelay(NOT_NULL_EXPRESSION.falseDv);
        return valuePropertiesOfFormalType(formalType, nne);
    }

    public Properties valuePropertiesOfFormalType(ParameterizedType formalType, DV notNullExpression) {
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

    public Properties valuePropertiesOfNullConstant(IsMyself isMyself, ParameterizedType formalType) {
        AnalyserContext analyserContext = getAnalyserContext();
        DV immutable = isMyself.toFalse(IMMUTABLE) ? IMMUTABLE.falseDv : analyserContext.typeImmutable(formalType);
        DV independent = isMyself.toFalse(INDEPENDENT) ? INDEPENDENT.falseDv : analyserContext.typeIndependent(formalType);
        DV container = isMyself.toFalse(CONTAINER) ? CONTAINER.falseDv : analyserContext.typeContainer(formalType);
        DV notNull = AnalysisProvider.defaultNotNull(formalType).maxIgnoreDelay(NOT_NULL_EXPRESSION.falseDv);
        return Properties.ofWritable(Map.of(
                IMMUTABLE, immutable,
                INDEPENDENT, independent,
                NOT_NULL_EXPRESSION, notNull,
                CONTAINER, container,
                IDENTITY, IDENTITY.falseDv,
                IGNORE_MODIFICATIONS, IGNORE_MODIFICATIONS.falseDv));
    }

    public boolean disableEvaluationOfMethodCallsUsingCompanionMethods() {
        return getAnalyserContext().inAnnotatedAPIAnalysis();
    }

    public EvaluationContext getClosure() {
        return null;
    }

    public int getInitialStatementTime() {
        return 0;
    }

    public int getFinalStatementTime() {
        return 0;
    }

    public boolean allowedToIncrementStatementTime() {
        return true;
    }

    public Expression replaceLocalVariables(Expression expression) {
        return expression;
    }

    public Expression acceptAndTranslatePrecondition(Identifier identifier, Expression rest) {
        return null;
    }

    public boolean isPresent(Variable variable) {
        return true;
    }

    public List<PrimaryTypeAnalyser> getLocalPrimaryTypeAnalysers() {
        return List.of();
    }

    public Stream<Map.Entry<String, VariableInfoContainer>> localVariableStream() {
        return Stream.empty();
    }

    public MethodAnalysis findMethodAnalysisOfLambda(MethodInfo methodInfo) {
        MethodAnalysis inLocalPTAs = getLocalPrimaryTypeAnalysers().stream()
                .filter(pta -> pta.containsPrimaryType(methodInfo.typeInfo))
                .map(pta -> pta.getMethodAnalysis(methodInfo))
                .findFirst().orElse(null);
        if (inLocalPTAs != null) return inLocalPTAs;
        return getAnalyserContext().getMethodAnalysis(methodInfo);
    }

    public This currentThis() {
        return new This(getAnalyserContext(), getCurrentType());
    }

    public DV cannotBeModified(Expression value) {
        return DV.FALSE_DV;
    }

    public MethodInfo concreteMethod(Variable variable, MethodInfo methodInfo) {
        return null;
    }

    public String statementIndex() {
        return "-";
    }

    public boolean firstAssignmentOfFieldInConstructor(Variable variable) {
        MethodAnalyser cm = getCurrentMethod();
        if (cm == null) return false;
        if (!cm.getMethodInfo().isConstructor()) return false;
        if (!(variable instanceof FieldReference)) return false;
        return !hasBeenAssigned(variable);
    }

    public boolean hasBeenAssigned(Variable variable) {
        return false;
    }

    /*
    should we compute context immutable? not if we're a variable of the type itself
     */
    public IsMyself isMyself(Variable variable) {
        if (variable instanceof This) return IsMyself.YES;
        if (variable instanceof FieldReference fr && fr.isStatic()) return IsMyself.NO;
        return isMyself(variable.parameterizedType());
    }

    public IsMyself isMyselfExcludeThis(Variable variable) {
        if (variable instanceof This) return IsMyself.NO;
        if (variable instanceof FieldReference fr && fr.isStatic()) return IsMyself.NO;
        return isMyself(variable.parameterizedType());
    }

    public IsMyself isMyself(ParameterizedType type) {
        return getCurrentType().isMyself(type, getAnalyserContext());
    }

    public Properties ensureMyselfValueProperties(Properties existing) {
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

    public boolean inConstruction() {
        MethodAnalyser ma = getCurrentMethod();
        return ma != null && ma.getMethodInfo().inConstruction();
    }

    /*
     Store_0 shows an example of a stack overflow going from the ConditionManager.absoluteState via And, Negation,
     Equals, EvaluationContext.isNotNull0, notNullAccordingToConditionManager, findIndividualNullInState and back to
     absoluteState... This method, only applied in Negation at the moment, prevents this infinite loop from occurring.
     */
    public boolean preventAbsoluteStateComputation() {
        return false;
    }

    public EvaluationContext copyToPreventAbsoluteStateComputation() {
        return this;
    }

    /**
     * @param variable   the variable in the nested type
     * @param nestedType the nested type, can be null in case of method references
     * @return true when we want to transfer properties from the nested type to the current type
     */
    public boolean acceptForVariableAccessReport(Variable variable, TypeInfo nestedType) {
        if (variable instanceof FieldReference fr) {
            return fr.fieldInfo().owner != nestedType
                    && (nestedType == null || fr.fieldInfo().owner.primaryType().equals(nestedType.primaryType()))
                    && fr.scopeVariable() != null
                    && acceptForVariableAccessReport(fr.scopeVariable(), nestedType);
        }
        return isPresent(variable);
    }

    public DependentVariable searchInEquivalenceGroupForLatestAssignment(DependentVariable variable,
                                                                         Expression arrayValue,
                                                                         Expression indexValue,
                                                                         ForwardEvaluationInfo forwardEvaluationInfo) {
        return variable;
    }

    // problem: definedInBlock() is only non-null after the first evaluation
    public boolean isPatternVariableCreatedAt(Variable v, String index) {
        return v.variableNature() instanceof VariableNature.Pattern pvn && index.equals(pvn.definedInBlock());
    }


    Either<CausesOfDelay, Set<Variable>> NO_LOOP_SOURCE_VARIABLES = Either.right(Set.of());

    public Either<CausesOfDelay, Set<Variable>> loopSourceVariables(Variable variable) {
        return NO_LOOP_SOURCE_VARIABLES;
    }

    public Stream<Map.Entry<String, VariableInfoContainer>> variablesFromClosure() {
        return Stream.of();
    }

    public Properties getExternalProperties(Expression valueToWrite) {
        return Properties.EMPTY;
    }

    public BreakDelayLevel breakDelayLevel() {
        return BreakDelayLevel.NONE;
    }

    /*
    modifications on immutable object...
     */
    public boolean inConstructionOrInStaticWithRespectTo(TypeInfo typeInfo) {
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

    public int initialModificationTimeOrZero(Variable variable) {
        return 0;
    }

    public HiddenContent extractHiddenContentTypes(ParameterizedType concreteType, SetOfTypes hiddenContentTypes) {
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

    public boolean hasState(Expression expression) {
        if (expression.cannotHaveState()) return false;
        VariableExpression ve;
        if ((ve = expression.asInstanceOf(VariableExpression.class)) != null) {
            if (ve.variable() instanceof FieldReference fr) {
                FieldAnalysis fa = getAnalyserContext().getFieldAnalysis(fr.fieldInfo());
                return fa.getValue() != null &&
                        fa.getValue().hasState();
            }
            return false; // no way we have this info here
        }
        return expression.hasState();
    }

    public Expression state(Expression expression) {
        VariableExpression ve;
        if ((ve = expression.asInstanceOf(VariableExpression.class)) != null) {
            if (ve.variable() instanceof FieldReference fr) {
                FieldAnalysis fa = getAnalyserContext().getFieldAnalysis(fr.fieldInfo());
                return fa.getValue().state();
            }
            throw new UnsupportedOperationException();
        }
        return expression.state();
    }

    public Expression getVariableValue(Variable myself,
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

    public boolean delayStatementBecauseOfECI() {
        return false;
    }

    @Override
    public Properties defaultValueProperties(ParameterizedType parameterizedType, DV valueForNotNullExpression) {
        IsMyself isMyself = isMyself(parameterizedType);
        return EvaluationContext.VALUE_PROPERTIES.stream()
                .collect(Properties.collect(p -> p == Property.NOT_NULL_EXPRESSION ? valueForNotNullExpression
                        : isMyself.toFalse(p) ? p.falseDv
                        : getAnalyserContext().defaultValueProperty(p, parameterizedType), false));
    }

    public Properties defaultValueProperties(ParameterizedType parameterizedType) {
        return defaultValueProperties(parameterizedType, false);
    }

    public Properties defaultValueProperties(ParameterizedType parameterizedType, boolean writable) {
        IsMyself isMyself = isMyself(parameterizedType);
        return EvaluationContext.VALUE_PROPERTIES.stream()
                .collect(Properties.collect(p -> isMyself.toFalse(p) ? p.falseDv
                        : getAnalyserContext().defaultValueProperty(p, parameterizedType), writable));
    }
}
