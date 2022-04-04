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

package org.e2immu.analyser.analyser.statementanalyser;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analyser.delay.DelayFactory;
import org.e2immu.analyser.analyser.delay.VariableCause;
import org.e2immu.analyser.analyser.nonanalyserimpl.AbstractEvaluationContextImpl;
import org.e2immu.analyser.analyser.nonanalyserimpl.VariableInfoImpl;
import org.e2immu.analyser.analysis.FieldAnalysis;
import org.e2immu.analyser.analysis.StatementAnalysis;
import org.e2immu.analyser.analysis.impl.StatementAnalysisImpl;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.expression.util.LhsRhs;
import org.e2immu.analyser.model.impl.LocationImpl;
import org.e2immu.analyser.model.impl.TranslationMapImpl;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.model.variable.VariableNature;
import org.e2immu.annotation.NotNull;
import org.e2immu.support.SetOnce;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.Property.*;
import static org.e2immu.analyser.analyser.Stage.EVALUATION;
import static org.e2immu.analyser.analyser.Stage.INITIAL;
import static org.e2immu.analyser.model.MultiLevel.NOT_INVOLVED_DV;
import static org.e2immu.analyser.model.MultiLevel.NULLABLE_DV;

class SAEvaluationContext extends AbstractEvaluationContextImpl {
    private static final Logger LOGGER = LoggerFactory.getLogger(SAEvaluationContext.class);

    private final boolean disableEvaluationOfMethodCallsUsingCompanionMethods;
    private final StatementAnalysis statementAnalysis;
    private final MethodAnalyser myMethodAnalyser;
    private final StatementAnalyser statementAnalyser;
    private final AnalyserContext analyserContext;
    private final SetOnce<List<PrimaryTypeAnalyser>> localAnalysers;
    private final boolean preventAbsoluteStateComputation;
    private final boolean delayStatementBecauseOfECI;

    SAEvaluationContext(StatementAnalysis statementAnalysis,
                        MethodAnalyser myMethodAnalyser,
                        StatementAnalyser statementAnalyser,
                        AnalyserContext analyserContext,
                        SetOnce<List<PrimaryTypeAnalyser>> localAnalysers,
                        int iteration,
                        ConditionManager conditionManager,
                        EvaluationContext closure,
                        // NOTE: ECI = explicit constructor invocation
                        boolean delayStatementBecauseOfECI) {
        this(statementAnalysis, myMethodAnalyser, statementAnalyser, analyserContext, localAnalysers,
                iteration, conditionManager, closure, false, true, false, delayStatementBecauseOfECI);
    }

    // base is used to distinguish between the context created in SAEvaluationOfMain, as compared to temporary ones
    // created to compute inline conditions, short-circuit operators, etc.

    SAEvaluationContext(StatementAnalysis statementAnalysis,
                        MethodAnalyser myMethodAnalyser,
                        StatementAnalyser statementAnalyser,
                        AnalyserContext analyserContext,
                        SetOnce<List<PrimaryTypeAnalyser>> localAnalysers,
                        int iteration,
                        ConditionManager conditionManager,
                        EvaluationContext closure,
                        boolean disableEvaluationOfMethodCallsUsingCompanionMethods,
                        boolean base,
                        boolean preventAbsoluteStateComputation,
                        boolean delayStatementBecauseOfECI) {
        super(closure == null ? 1 : closure.getDepth() + 1, iteration, conditionManager, closure);
        this.statementAnalyser = statementAnalyser;
        this.localAnalysers = localAnalysers;
        this.myMethodAnalyser = myMethodAnalyser;
        this.analyserContext = analyserContext;
        this.statementAnalysis = statementAnalysis;
        this.disableEvaluationOfMethodCallsUsingCompanionMethods = disableEvaluationOfMethodCallsUsingCompanionMethods;
        this.preventAbsoluteStateComputation = preventAbsoluteStateComputation;
        this.delayStatementBecauseOfECI = delayStatementBecauseOfECI;

        // part 1 of the work: all evaluations will get to read the new value
        // part 2 is at the start of SAApply, where the value will be assigned
        if (base) {
            Expression absoluteState = conditionManager.absoluteState(EvaluationResult.from(this));
            if (absoluteState.isDone()) {
                List<LhsRhs> equalities = LhsRhs.extractEqualities(absoluteState);
                for (LhsRhs lhsRhs : equalities) {
                    if (lhsRhs.rhs() instanceof VariableExpression ve
                            && isPresent(ve.variable())
                            && !lhsRhs.lhs().isInstanceOf(IsVariableExpression.class) // do not assign to other variable!
                            && !statementAnalysis.stateData().equalityAccordingToStateIsSet(ve)) {
                        VariableInfoContainer vic = statementAnalysis.getVariable(ve.variable().fullyQualifiedName());
                        Expression value = lhsRhs.lhs();
                        assert value.isDone();
                        // we want to ensure that no values can be written unless the state is done
                        // the following condition is mostly relevant for CyclicReferences_2,3,4
                        if (!vic.hasEvaluation() || vic.best(EVALUATION).isDelayed()) {
                            LOGGER.debug("Caught equality on variable with 'instance' value {}: {}", ve, value);
                            statementAnalysis.stateData().equalityAccordingToStatePut(ve, value);
                        }
                    }
                }
            }
        }
    }

    @Override
    public boolean delayStatementBecauseOfECI() {
        return delayStatementBecauseOfECI;
    }

    @Override
    public boolean preventAbsoluteStateComputation() {
        return preventAbsoluteStateComputation;
    }

    @Override
    public EvaluationContext copyToPreventAbsoluteStateComputation() {
        return new SAEvaluationContext(statementAnalysis, myMethodAnalyser, statementAnalyser, analyserContext,
                localAnalysers, iteration, conditionManager, closure, disableEvaluationOfMethodCallsUsingCompanionMethods,
                false, true, delayStatementBecauseOfECI);
    }

    private MethodInfo methodInfo() {
        return statementAnalysis.methodAnalysis().getMethodInfo();
    }

    @Override
    public String statementIndex() {
        return statementAnalysis.index();
    }

    @Override
    public boolean allowedToIncrementStatementTime() {
        return !statementAnalysis.inSyncBlock();
    }

    @Override
    public boolean disableEvaluationOfMethodCallsUsingCompanionMethods() {
        return getAnalyserContext().inAnnotatedAPIAnalysis() || disableEvaluationOfMethodCallsUsingCompanionMethods;
    }

    @Override
    public int getIteration() {
        return iteration;
    }

    @Override
    public TypeInfo getCurrentType() {
        return methodInfo().typeInfo;
    }

    @Override
    public MethodAnalyser getCurrentMethod() {
        return myMethodAnalyser;
    }

    @Override
    public StatementAnalyser getCurrentStatement() {
        return statementAnalyser;
    }

    @Override
    public LocationImpl getLocation(Stage level) {
        return new LocationImpl(methodInfo(), statementAnalysis.index() + level.label,
                statementAnalysis.statement().getIdentifier());
    }

    @Override
    public Location getEvaluationLocation(Identifier identifier) {
        return new LocationImpl(methodInfo(), statementAnalysis.index() + EVALUATION, identifier);
    }

    @Override
    public EvaluationContext child(Expression condition) {
        return child(condition, false);
    }

    @Override
    public EvaluationContext child(Expression condition, boolean disableEvaluationOfMethodCallsUsingCompanionMethods) {
        return new SAEvaluationContext(statementAnalysis,
                myMethodAnalyser, statementAnalyser, analyserContext, localAnalysers,
                iteration,
                conditionManager.newAtStartOfNewBlockDoNotChangePrecondition(getPrimitives(), condition),
                closure,
                disableEvaluationOfMethodCallsUsingCompanionMethods, false, preventAbsoluteStateComputation,
                delayStatementBecauseOfECI);
    }

    @Override
    public EvaluationContext dropConditionManager() {
        ConditionManager cm = ConditionManager.initialConditionManager(getPrimitives());
        return new SAEvaluationContext(statementAnalysis,
                myMethodAnalyser, statementAnalyser, analyserContext, localAnalysers,
                iteration, cm, closure, disableEvaluationOfMethodCallsUsingCompanionMethods, false,
                preventAbsoluteStateComputation,
                delayStatementBecauseOfECI);
    }

    public EvaluationContext childState(Expression state) {
        return new SAEvaluationContext(statementAnalysis,
                myMethodAnalyser, statementAnalyser, analyserContext, localAnalysers,
                iteration, conditionManager.addState(state), closure,
                false, false, preventAbsoluteStateComputation,
                delayStatementBecauseOfECI);
    }

        /*
        differs sufficiently from the regular getProperty, in that it fast tracks as soon as one of the not nulls
        reaches EFFECTIVELY_NOT_NULL, and that it always reads from the initial value of variables.
         */

    @Override
    public DV isNotNull0(Expression value, boolean useEnnInsteadOfCnn, ForwardEvaluationInfo forwardEvaluationInfo) {
        if (value instanceof IsVariableExpression ve) {
            VariableInfo variableInfo = findForReading(ve.variable(), true);
            DV cnn = variableInfo.getProperty(useEnnInsteadOfCnn ? EXTERNAL_NOT_NULL : CONTEXT_NOT_NULL);
            DV cnnTF = cnn.isDelayed() ? cnn : cnn.equals(NOT_INVOLVED_DV) ? DV.FALSE_DV : DV.fromBoolDv(!cnn.equals(NULLABLE_DV));
            DV nne = variableInfo.getProperty(NOT_NULL_EXPRESSION);
            DV nneTF = nne.isDelayed() ? nne : DV.fromBoolDv(!nne.equals(NULLABLE_DV));
            DV cm = notNullAccordingToConditionManager(ve.variable());
            return cnnTF.max(nneTF).max(cm);
        }
        DV nne = getProperty(value, NOT_NULL_EXPRESSION, true, false);
        return nne.isDelayed() ? nne : DV.fromBoolDv(!nne.equals(NULLABLE_DV));
    }

    /*
    this one is meant for non-eventual types (for now). After/before errors are caught in EvaluationResult
     */
    @Override
    public DV cannotBeModified(Expression value) {
        if (value instanceof IsVariableExpression ve) {
            VariableInfo variableInfo = findForReading(ve.variable(), true);

            // see Lazy: a functional interface comes in as a parameter of a non-private method,
            // and is treated as an E2Immutable object, with the modification on the
            // single modifying method ignored. See ComputingTypeAnalyser.correctIndependentFunctionalInterface()
            DV ignoreMod = variableInfo.getProperty(IGNORE_MODIFICATIONS);
            DV extIgnoreMod = variableInfo.getProperty(EXTERNAL_IGNORE_MODIFICATIONS);
            DV ignore = ignoreMod.max(extIgnoreMod);
            if (ignore.isDelayed()) return ignore;
            if (ignore.equals(MultiLevel.IGNORE_MODS_DV)) return DV.FALSE_DV;

            DV cImm = variableInfo.getProperty(CONTEXT_IMMUTABLE);
            if (MultiLevel.isAtLeastEffectivelyE2Immutable(cImm)) return DV.TRUE_DV;
            DV imm = variableInfo.getProperty(IMMUTABLE);
            if (MultiLevel.isAtLeastEffectivelyE2Immutable(imm)) return DV.TRUE_DV;
            DV extImm = variableInfo.getProperty(EXTERNAL_IMMUTABLE);
            if (MultiLevel.isAtLeastEffectivelyE2Immutable(extImm)) return DV.TRUE_DV;
            DV formal = analyserContext.defaultImmutable(variableInfo.variable().parameterizedType(), false);
            return DV.fromBoolDv(MultiLevel.isAtLeastEffectivelyE2Immutable(formal));
        }
        DV valueProperty = getProperty(value, IMMUTABLE, true, false);
        return DV.fromBoolDv(MultiLevel.isAtLeastEffectivelyE2Immutable(valueProperty));
    }

    private DV getVariableProperty(Variable variable, Property property, boolean duringEvaluation) {
        if (duringEvaluation) {
            return getPropertyFromPreviousOrInitial(variable, property);
        }
        return getProperty(variable, property);
    }

    // vectorized version of getVariableProperty
    private Properties getVariableProperties(Variable variable, List<Property> properties, boolean duringEvaluation) {
        if (duringEvaluation) {
            return getPropertiesFromPreviousOrInitial(variable, properties);
        }
        return getProperties(variable, properties);
    }

    // identical to getProperty, but then for multiple properties!
    @Override
    public Properties getProperties(Expression value, List<Property> toCompute, boolean duringEvaluation,
                                    boolean ignoreStateInConditionManager) {

        if (value instanceof IsVariableExpression ve) {
            return getPropertiesOfVariableExpression(value, toCompute, duringEvaluation, ve);
        }
        if (value instanceof DelayedWrappedExpression dwe && dwe.getExpression() instanceof VariableExpression ve) {
            return getPropertiesOfVariableExpression(value, toCompute, duringEvaluation, ve);
        }

        // this one is more difficult to vectorize
        Properties properties = Properties.writable();
        EvaluationResult context = EvaluationResult.from(this);
        for (Property property : toCompute) {
            DV dv;
            if (NOT_NULL_EXPRESSION == property) {
                dv = nneForValue(value, ignoreStateInConditionManager);
            } else {
                dv = value.getProperty(context, property, duringEvaluation);
            }
            properties.put(property, dv);
        }
        return properties;
    }

    private Properties getPropertiesOfVariableExpression(Expression value, List<Property> toCompute, boolean duringEvaluation, IsVariableExpression ve) {
        Variable variable = ve.variable();
        // read what's in the property map (all values should be there) at initial or current level
        Properties properties = getVariableProperties(variable, toCompute, duringEvaluation);
        DV nne = properties.getOrDefaultNull(NOT_NULL_EXPRESSION);
        DV updated = nneForVariable(duringEvaluation, variable, nne, value.causesOfDelay());
        properties.overwrite(NOT_NULL_EXPRESSION, updated);
        return properties;
    }

    // identical to getProperties, but then for a single property!
    @Override
    public DV getProperty(Expression value, Property property, boolean duringEvaluation,
                          boolean ignoreStateInConditionManager) {
        // IMPORTANT: here we do not want to catch VariableValues wrapped in the PropertyWrapper
        if (value instanceof IsVariableExpression ve) {
            Variable variable = ve.variable();
            // read what's in the property map (all values should be there) at initial or current level
            DV inMap = getVariableProperty(variable, property, duringEvaluation);
            if (property == NOT_NULL_EXPRESSION) {
                return nneForVariable(duringEvaluation, variable, inMap, value.causesOfDelay());
            }
            return inMap;
        }

        if (NOT_NULL_EXPRESSION == property) {
            return nneForValue(value, ignoreStateInConditionManager);
        }

        // redirect to Value.getProperty()
        // this is the only usage of this method; all other evaluation of a Value in an evaluation context
        // must go via the current method
        return value.getProperty(EvaluationResult.from(this), property, true);
    }

    private DV nneForValue(Expression value, boolean ignoreStateInConditionManager) {
        if (ignoreStateInConditionManager) {
            EvaluationContext customEc = new SAEvaluationContext(statementAnalysis,
                    myMethodAnalyser, statementAnalyser, analyserContext, localAnalysers,
                    iteration, conditionManager.withoutState(getPrimitives()), closure, false, false,
                    preventAbsoluteStateComputation, delayStatementBecauseOfECI);
            EvaluationResult context = EvaluationResult.from(customEc);
            return value.getProperty(context, NOT_NULL_EXPRESSION, true);
        }

        EvaluationResult context = EvaluationResult.from(this);
        DV directNN = value.getProperty(context, NOT_NULL_EXPRESSION, true);
        if (directNN.equals(NULLABLE_DV)) {
            Expression valueIsNull = Equals.equals(Identifier.generate("nne equals"),
                    context, value, NullConstant.NULL_CONSTANT, false, ForwardEvaluationInfo.DEFAULT);
            Expression evaluation = conditionManager.evaluate(context, valueIsNull);
            if (evaluation.isBoolValueFalse()) {
                // IMPROVE should not necessarily be ENN, could be ContentNN depending
                return MultiLevel.EFFECTIVELY_NOT_NULL_DV.max(directNN);
            }
        }
        return directNN;
    }

    private DV nneForVariable(boolean duringEvaluation, Variable variable, DV inMap, CausesOfDelay delays) {
        if (delays.isDelayed()) {
            return delays; // see Enum_8; otherwise we compute NNE of a delayed field <f:...>
        }
        if (variable.parameterizedType().isPrimitiveExcludingVoid()) {
            return MultiLevel.EFFECTIVELY_NOT_NULL_DV;
        }
        DV cnn = getVariableProperty(variable, CONTEXT_NOT_NULL, duringEvaluation);
        DV cnnInMap = cnn.max(inMap);
        boolean isBreakInitDelay = cnn.isDelayed() && cnn.containsCauseOfDelay(CauseOfDelay.Cause.BREAK_INIT_DELAY);
        if (cnnInMap.isDelayed() && !isBreakInitDelay) {
            // we return even if cmNn would be ENN, because our value could be higher
            return cnnInMap;
        }
        DV cmNn = notNullAccordingToConditionManager(variable);
        DV cm = cmNn.isDelayed() ? cmNn : cmNn.valueIsTrue() ? MultiLevel.EFFECTIVELY_NOT_NULL_DV : NULLABLE_DV;
        if (isBreakInitDelay) return cm;
        return cnnInMap.max(cm);
    }

    // specific implementation for SAEvaluationContext, currently only used by EvaluationResult.removeFromLinkedVariables
    // it does not use findForReading because it does not need to switch to local copies
    @Override
    public LinkedVariables linkedVariables(Variable variable) {
        String fqn = variable.fullyQualifiedName();
        if (!statementAnalysis.variableIsSet(fqn)) return null; // not known
        VariableInfoContainer vic = statementAnalysis.getVariable(fqn);
        VariableInfo variableInfo = vic.getPreviousOrInitial();
        return variableInfo.getLinkedVariables();
    }

    /**
     * Important that the closure is used for local variables and parameters (we'd never find them otherwise).
     * However, fields will be introduced in StatementAnalysis.fromFieldAnalyserIntoInitial and should
     * have their own local copy.
     * <p>
     * Equally important, if we have a local copy already, we must use it! See e.g. BasicCompanionMethods_11
     */
    private VariableInfo findForReading(Variable variable, boolean isNotAssignmentTarget) {
        boolean haveVariable = statementAnalysis.variableIsSet(variable.fullyQualifiedName());
        if (!haveVariable && closure != null && isNotMine(variable) && !(variable instanceof FieldReference)) {
            return ((SAEvaluationContext) closure).findForReading(variable, isNotAssignmentTarget);
        }
        return initialValueForReading(variable, isNotAssignmentTarget);
    }

    /**
     * Find a variable for reading. Intercepts variable fields and local variables.
     * This is the general method that must be used by the evaluation context, currentInstance, currentValue
     *
     * @param variable the variable
     * @return the most current variable info object
     */
    public VariableInfo initialValueForReading(@NotNull Variable variable, boolean isNotAssignmentTarget) {
        String fqn = variable.fullyQualifiedName();
        if (!statementAnalysis.variableIsSet(fqn)) {
            assert !(variable instanceof ParameterInfo) :
                    "Parameter " + variable.fullyQualifiedName() + " should be known in " + methodInfo().fullyQualifiedName
                            + ", statement " + statementAnalysis.index();
            return new VariableInfoImpl(getLocation(INITIAL), variable, getInitialStatementTime()); // no value, no state; will be created by a MarkRead
        }
        VariableInfoContainer vic = statementAnalysis.getVariable(fqn);
        VariableInfo vi = vic.getPreviousOrInitial();
        if (isNotAssignmentTarget) {
            if (variable instanceof FieldReference fr) {
                FieldAnalysis fieldAnalysis = getAnalyserContext().getFieldAnalysis(fr.fieldInfo);
                if (situationForVariableFieldReference(fr)) {
                    // is it a variable field, or a final field? if we don't know, return an empty VI
                    // in constructors, and sync blocks, this does not hold
                    DV effectivelyFinal = fieldAnalysis.getProperty(FINAL);
                    if (effectivelyFinal.isDelayed() || effectivelyFinal.valueIsTrue() && noValuesYet(fieldAnalysis)) {
                        VariableInfo breakDelay = breakDelay(fr, fieldAnalysis);
                        if (breakDelay != null) return breakDelay;
                        return new VariableInfoImpl(getLocation(INITIAL), variable, getInitialStatementTime());
                    }
                }
                // we still could have a delay to be broken (See e.g. E2Immutable_1)
                // the condition of vi.getValue().isDelayed is for Final_0
                // IMPROVE conditions feel shaky, but do work for now

                // ConditionalInitialization_0: noValueYet is false because <variable value>, yet without value properties
                if (vi.getValue().isDelayed() && noValuesYet(fieldAnalysis)) {
                    VariableInfo breakDelay = breakDelay(fr, fieldAnalysis);
                    if (breakDelay != null) return breakDelay;
                }

            }
            if (vic.variableNature() instanceof VariableNature.VariableDefinedOutsideLoop) {
                StatementAnalysisImpl relevantLoop = (StatementAnalysisImpl) statementAnalysis.mostEnclosingLoop();
                if (!relevantLoop.localVariablesAssignedInThisLoop.isFrozen()) {
                    return new VariableInfoImpl(getLocation(INITIAL), variable, getInitialStatementTime()); // no value, no state
                }
            }
        } // else we need to go to the variable itself
        return vi;
    }

    // E2Immutable_1, ConditionalInitialization_0, EventuallyE1Immutable_0
    // by returning a new VII object after having returned null here, we introduce the INITIAL_VALUE delay
    private VariableInfo breakDelay(FieldReference fr, FieldAnalysis fieldAnalysis) {
        CausesOfDelay causes = fieldAnalysis.valuesDelayed().causesOfDelay();
        //assert causes.isDelayed();
        Location here = getLocation(INITIAL);
        boolean cyclicDependency = causes.containsCauseOfDelay(CauseOfDelay.Cause.INITIAL_VALUE,
                cause -> cause instanceof VariableCause vc && vc.variable().equals(fr) && vc.location().equals(here));
        if (cyclicDependency) {
            LOGGER.debug("Breaking the delay by inserting a special delayed value for {} at {}", fr, statementIndex());
            CauseOfDelay cause = new VariableCause(fr, here, CauseOfDelay.Cause.BREAK_INIT_DELAY);
            Expression dve = DelayedVariableExpression.forBreakingInitialisationDelay(fr, getInitialStatementTime(), cause);
            return new VariableInfoImpl(getLocation(INITIAL), fr, dve);
        }
        return null;
    }

    /**
     * E2Immutable_1; multiple constructors, don't come to a conclusion too quickly
     * On the other hand, we have to be careful not to cause an infinite delay.
     * To break this delay, we need to return a VI with an instance object rather than a delay.
     * The getVariableValue() method then has to pick up this instance, and keep the variable.
     */
    private boolean noValuesYet(FieldAnalysis fieldAnalysis) {
        return fieldAnalysis.valuesDelayed().isDelayed();
    }

    private boolean isNotMine(Variable variable) {
        return getCurrentType() != variable.getOwningType();
    }

    // getVariableValue potentially returns a new VariableExpression with a different name, pointing to the same variable
    // this happens in the case of confirmed variable fields, where the name indicates the statement time;
    // and in the case of variables assigned in a loop defined outside, where the name indicates the loop statement id
    @Override
    public Expression currentValue(Variable variable, Expression scopeValue, ForwardEvaluationInfo forwardEvaluationInfo) {
        VariableInfo variableInfo = findForReading(variable, forwardEvaluationInfo.isNotAssignmentTarget());

        // important! do not use variable in the next statement, but variableInfo.variable()
        // we could have redirected from a variable field to a local variable copy
        if (forwardEvaluationInfo.assignToField() && variable instanceof LocalVariableReference) {
            return variableInfo.getValue();
        }
        // on the LHS of an assignment, we do not expand variables that hold {1, 2, 3} array initializers
        if (forwardEvaluationInfo.isAssignmentTarget() && variableInfo.getValue() instanceof ArrayInitializer) {
            return new VariableExpression(variable);
        }
        // NOTE: we use null instead of forwardEvaluationInfo.assignmentTarget()
        return getVariableValue(null, scopeValue, variableInfo);
    }

    @Override
    public Expression currentValue(Variable variable) {
        VariableInfo variableInfo = findForReading(variable, true);
        Expression value = variableInfo.getValue();

        // redirect to other variable
        VariableExpression ve;
        if ((ve = value.asInstanceOf(VariableExpression.class)) != null) {
            assert ve.variable() != variable :
                    "Variable " + variable.fullyQualifiedName() + " has been assigned a VariableValue value pointing to itself";
            return currentValue(ve.variable());
        }
        return value;
    }

    @Override
    public DV getProperty(Variable variable, Property property) {
        VariableInfo vi = statementAnalysis.findOrThrow(variable);
        return vi.getProperty(property); // ALWAYS from the map!!!!
    }

    // vectorized version of getProperty
    private Properties getProperties(Variable variable, List<Property> properties) {
        VariableInfo vi = statementAnalysis.findOrThrow(variable);
        return properties.stream().collect(Properties.collect(vi::getProperty, true));
    }

    @Override
    public DV getPropertyFromPreviousOrInitial(Variable variable, Property property) {
        VariableInfo vi = findForReading(variable, true);
        return vi.getProperty(property);
    }

    // vectorized version of getPropertyFromPreviousOrInitial
    public Properties getPropertiesFromPreviousOrInitial(Variable variable, List<Property> properties) {
        VariableInfo vi = findForReading(variable, true);
        return properties.stream().collect(Properties.collect(vi::getProperty, true));
    }

    @Override
    public AnalyserContext getAnalyserContext() {
        return analyserContext;
    }

    @Override
    public int getInitialStatementTime() {
        return statementAnalysis.flowData().getInitialTime();
    }

    @Override
    public int getFinalStatementTime() {
        return statementAnalysis.flowData().getTimeAfterSubBlocks();
    }

    /*
    go from local loop variable to instance when exiting a loop statement;
    both for merging and for the state

    non-null status to be copied from the variable; delays should/can work but need testing.
    A positive @NotNull is transferred, a positive @Nullable is replaced by a DELAY...
     */
    @Override
    public Expression replaceLocalVariables(Expression mergeValue) {
        TranslationMapImpl.Builder translationMap = new TranslationMapImpl.Builder();
        statementAnalysis.rawVariableStream()
                .map(Map.Entry::getValue)
                .filter(this::isReplaceVariable)
                .forEach(vic -> addToTranslationMapBuilder(vic, translationMap));
        if (translationMap.isEmpty()) return mergeValue;
        return mergeValue.translate(getAnalyserContext(), translationMap.build());
    }

    private void addToTranslationMapBuilder(VariableInfoContainer vic,
                                            TranslationMapImpl.Builder translationMap) {
        VariableInfo eval = vic.best(EVALUATION);
        Variable variable = eval.variable();

        // what happens when eval.getValue() is the null constant? we cannot properly compute
        // @Container on the null constant; that would have to come from a real value.
        Expression bestValue = eval.getValue();

        if (vic.variableNature() instanceof VariableNature.VariableDefinedOutsideLoop) {
            if (bestValue.isDone()) {
                VariableExpression.Suffix suffix = vic.variableNature().suffix();
                VariableExpression veSuffix = new VariableExpression(variable, suffix, null); // FIXME

                VariableExpression ve = new VariableExpression(variable);
                translationMap.put(veSuffix, ve);
            }
        } else {
            Properties valueProperties;
            if (bestValue instanceof NullConstant || bestValue instanceof UnknownExpression || bestValue.isDelayed()) {
                valueProperties = analyserContext.defaultValueProperties(variable.parameterizedType());
            } else {
                valueProperties = getValueProperties(bestValue);
            }

            CausesOfDelay delays = valueProperties.delays();
            if (delays.isDone()) {
                Expression newObject = Instance.genericMergeResult(statementAnalysis.index(), variable,
                        valueProperties);
                VariableExpression.Suffix suffix = vic.variableNature().suffix();
                VariableExpression ve = new VariableExpression(variable, suffix, null); // FIXME implement
                translationMap.put(ve, newObject);
            } else {
                Expression delayed = DelayedExpression.forReplacementObject(variable.parameterizedType(),
                        eval.getLinkedVariables().remove(v -> v.equals(variable)).changeAllToDelay(delays), delays);
                translationMap.put(DelayedVariableExpression.forVariable(variable, getInitialStatementTime(), delays), delayed);
                // we add this one as well because the evaluation result, which feeds the state, may have no delays while the actual SAApply does (because of value properties)
                // see VariableScope_10
                //if (variable.allowedToCreateVariableExpression()) { TODO implement
                translationMap.put(new VariableExpression(variable), delayed);
                //}
            }
        }
    }

    private boolean isReplaceVariable(VariableInfoContainer vic) {
        return statementIndex().equals(vic.variableNature().getStatementIndexOfBlockVariable());
    }

    /*
    Need to translate local copies of fields into fields.
    Should we do only their first appearance? ($0)

    This method accepts delayed variable expressions as well.
     */
    @Override
    public Expression acceptAndTranslatePrecondition(Identifier identifier, Expression precondition) {
        if (precondition.isBooleanConstant()) return null;
        TranslationMapImpl.Builder builder = new TranslationMapImpl.Builder();
        precondition.visit(e -> {
            if (e instanceof VariableExpression ve && ve.isDependentOnStatementTime()) {
                builder.put(ve, new VariableExpression(ve.variable()));
            }
        });
        TranslationMap translationMap = builder.build();
        Expression translated = precondition.translate(getAnalyserContext(), translationMap);
        List<Variable> variables = translated.variables(false);
        if (variables.stream().allMatch(v -> v instanceof ParameterInfo || v instanceof FieldReference)) {
            DV modified = variables.stream()
                    .filter(this::isPresent)
                    .map(v -> getProperty(v, CONTEXT_MODIFIED)).reduce(DV.FALSE_DV, DV::max);
            if (modified.valueIsFalse()) {
                return translated;
            }
            if (modified.isDelayed()) {
                return DelayedExpression.forPrecondition(identifier, getPrimitives(), modified.causesOfDelay());
            }
        }
        return precondition.isDelayed() ? precondition : null;
    }

    @Override
    public boolean isPresent(Variable variable) {
        return statementAnalysis.variableIsSet(variable.fullyQualifiedName());
    }

    @Override
    public List<PrimaryTypeAnalyser> getLocalPrimaryTypeAnalysers() {
        return localAnalysers.isSet() ? localAnalysers.get() : null;
    }

    @Override
    public Stream<Map.Entry<String, VariableInfoContainer>> localVariableStream() {
        return statementAnalysis.rawVariableStream().filter(e -> e.getValue().current()
                .variable() instanceof LocalVariableReference);
    }

    @Override
    public CausesOfDelay variableIsDelayed(Variable variable) {
        VariableInfo vi = statementAnalysis.findOrNull(variable, INITIAL);
        if (vi == null) {
            return DelayFactory.createDelay(new VariableCause(variable,
                    getLocation(INITIAL), CauseOfDelay.Cause.VARIABLE_DOES_NOT_EXIST));
        }
        return vi.getValue().causesOfDelay();
    }

    @Override
    public MethodInfo concreteMethod(Variable variable, MethodInfo abstractMethodInfo) {
        assert abstractMethodInfo.isAbstract();
        VariableInfo variableInfo = findForReading(variable, true);
        ParameterizedType type = variableInfo.getValue().returnType();
        if (type.typeInfo != null && !type.typeInfo.isAbstract()) {
            return type.typeInfo.findMethodImplementing(abstractMethodInfo);
        }
        return null;
    }


    @Override
    public boolean hasBeenAssigned(Variable variable) {
        VariableInfoContainer vic = statementAnalysis.getVariableOrDefaultNull(variable.fullyQualifiedName());
        if (vic == null) return false;
        VariableInfo vi = vic.getPreviousOrInitial();
        return vi.isAssigned();
    }

    @Override
    public boolean hasState(Expression expression) {
        IsVariableExpression ive;
        if ((ive = expression.asInstanceOf(IsVariableExpression.class)) != null) {
            VariableInfo vi = findForReading(ive.variable(), true);
            Expression v = vi.getValue();
            return v != null && !(v.isInstanceOf(IsVariableExpression.class)) && v.hasState();
        }
        return expression.hasState();
    }

    @Override
    public Expression state(Expression expression) {
        VariableExpression ve;
        if ((ve = expression.asInstanceOf(VariableExpression.class)) != null) {
            VariableInfo vi = findForReading(ve.variable(), true);
            return vi.getValue().state();
        }
        return expression.state();
    }

    private boolean situationForVariableFieldReference(FieldReference fieldReference) {
        if (statementAnalysis.inSyncBlock()) return false;
        // true outside construction; inside construction, does not hold for this.i but does hold for other.i
        return !methodInfo().inConstruction() || !fieldReference.scopeIsThis();
    }

    /**
     * @param myself       can be null, when target of an assignment
     * @param variableInfo the variable info from which to extract the value
     * @return the value, potentially replaced by a VariableExpression
     */
    @Override
    public Expression getVariableValue(Variable myself, Expression scopeValue, VariableInfo variableInfo) {
        Expression expression = makeVariableExpression(variableInfo, scopeValue);
        if (expression.isDelayed()) return expression;

        if (expression instanceof VariableExpression ve) {
            Expression valueFromState = statementAnalysis.stateData().equalityAccordingToStateGetOrDefaultNull(ve);
            if (valueFromState != null) {
                return valueFromState;
            }
        }

        // variable fields

        Expression value = variableInfo.getValue();
        Variable v = variableInfo.variable();
        boolean isInstance = value.isInstanceOf(Instance.class);
        if (isInstance && !v.equals(myself) && v instanceof FieldReference) {
            // see Basics_4 for the combination of v==myself, yet VE is returned
            return expression;
        }
        if (!v.equals(myself)) {
            VariableInfoContainer vic = statementAnalysis.getVariableOrDefaultNull(v.fullyQualifiedName());
            if (vic != null && vic.variableNature() instanceof VariableNature.VariableDefinedOutsideLoop outside) {
                Expression sv = expression instanceof VariableExpression ve ? ve.getScopeValue() : null;
                // variables in loop defined outside
                if (isInstance) {
                    VariableExpression.Suffix suffix2 = new VariableExpression.VariableInLoop(outside.statementIndex());
                    return new VariableExpression(v, suffix2, sv);
                }
                // do not return a value when it has not yet been written to
                if (!value.isDelayed()) {
                    VariableInfo prev = vic.getPreviousOrInitial();
                    String latestAssignment = prev.getAssignmentIds().getLatestAssignment();
                    if (latestAssignment.compareTo(outside.statementIndex()) < 0
                            && statementIndex().startsWith(outside.statementIndex())) {
                        // has not yet been assigned in the loop, and we're in that loop
                        VariableExpression.Suffix suffix2 = new VariableExpression.VariableInLoop(outside.statementIndex());
                        return new VariableExpression(v, suffix2, sv);
                    }
                }
            }
            if (isInstance) {
                return new VariableExpression(v);
            }
        }
        return value;
    }

    public Expression makeVariableExpression(VariableInfo variableInfo, Expression scopeValue) {
        VariableExpression.Suffix suffix;
        Variable variable = variableInfo.variable();
        Expression evaluatedScopeValue = scopeValue != null
                ? conditionManager.evaluateNonBoolean(EvaluationResult.from(this), scopeValue) : null;

        if (variable instanceof FieldReference fieldReference) {
            if (evaluatedScopeValue != null && evaluatedScopeValue.isDelayed()) {
                int initialStatementTime = getInitialStatementTime();
                CausesOfDelay causesOfDelay = evaluatedScopeValue.causesOfDelay();
                return DelayedVariableExpression.forField(fieldReference, initialStatementTime, causesOfDelay);
            }
            FieldAnalysis fieldAnalysis = getAnalyserContext().getFieldAnalysis(fieldReference.fieldInfo);
            DV finalDV = fieldAnalysis.getProperty(Property.FINAL);
            if (finalDV.valueIsFalse() && situationForVariableFieldReference(fieldReference)) {
                String assignmentId = variableInfo.getAssignmentIds().getLatestAssignmentNullWhenEmpty();
                suffix = new VariableExpression.VariableField(getInitialStatementTime(), assignmentId);
            } else {
                suffix = VariableExpression.NO_SUFFIX;
            }
            if (evaluatedScopeValue != null) {
                Expression shortCut = VariableExpression.tryShortCut(EvaluationResult.from(this), evaluatedScopeValue, fieldReference);
                if (shortCut != null) return shortCut;
            }
        } else {
            suffix = VariableExpression.NO_SUFFIX;
        }
        return new VariableExpression(variable, suffix, evaluatedScopeValue);
    }
}
