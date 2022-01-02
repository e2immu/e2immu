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
import org.e2immu.analyser.analyser.delay.SimpleSet;
import org.e2immu.analyser.analyser.delay.VariableCause;
import org.e2immu.analyser.analyser.nonanalyserimpl.AbstractEvaluationContextImpl;
import org.e2immu.analyser.analyser.nonanalyserimpl.VariableInfoImpl;
import org.e2immu.analyser.analysis.StatementAnalysis;
import org.e2immu.analyser.analysis.impl.StatementAnalysisImpl;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.impl.LocationImpl;
import org.e2immu.analyser.model.impl.TranslationMapImpl;
import org.e2immu.analyser.model.statement.LoopStatement;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.model.variable.VariableNature;
import org.e2immu.annotation.NotNull;
import org.e2immu.support.SetOnce;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.Property.*;
import static org.e2immu.analyser.analyser.VariableInfoContainer.Level.EVALUATION;
import static org.e2immu.analyser.analyser.VariableInfoContainer.Level.INITIAL;

class SAEvaluationContext extends AbstractEvaluationContextImpl {
    private final boolean disableEvaluationOfMethodCallsUsingCompanionMethods;
    private final StatementAnalysis statementAnalysis;
    private final MethodAnalyser myMethodAnalyser;
    private final StatementAnalyser statementAnalyser;
    private final AnalyserContext analyserContext;
    private final SetOnce<List<PrimaryTypeAnalyser>> localAnalysers;

    SAEvaluationContext(StatementAnalysis statementAnalysis,
                        MethodAnalyser myMethodAnalyser,
                        StatementAnalyser statementAnalyser,
                        AnalyserContext analyserContext,
                        SetOnce<List<PrimaryTypeAnalyser>> localAnalysers,
                        int iteration,
                        ConditionManager conditionManager,
                        EvaluationContext closure) {
        this(statementAnalysis, myMethodAnalyser, statementAnalyser, analyserContext, localAnalysers,
                iteration, conditionManager, closure, false);
    }

    SAEvaluationContext(StatementAnalysis statementAnalysis,
                        MethodAnalyser myMethodAnalyser,
                        StatementAnalyser statementAnalyser,
                        AnalyserContext analyserContext,
                        SetOnce<List<PrimaryTypeAnalyser>> localAnalysers,
                        int iteration,
                        ConditionManager conditionManager,
                        EvaluationContext closure,
                        boolean disableEvaluationOfMethodCallsUsingCompanionMethods) {
        super(iteration, conditionManager, closure);
        this.statementAnalyser = statementAnalyser;
        this.localAnalysers = localAnalysers;
        this.myMethodAnalyser = myMethodAnalyser;
        this.analyserContext = analyserContext;
        this.statementAnalysis = statementAnalysis;
        this.disableEvaluationOfMethodCallsUsingCompanionMethods = disableEvaluationOfMethodCallsUsingCompanionMethods;
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
    public LocationImpl getLocation() {
        return new LocationImpl(methodInfo(), statementAnalysis.index(),
                statementAnalysis.statement().getIdentifier());
    }

    @Override
    public Location getLocation(Identifier identifier) {
        return new LocationImpl(methodInfo(), statementAnalysis.index(), identifier);
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
                conditionManager.newAtStartOfNewBlockDoNotChangePrecondition(getPrimitives(), condition, condition.causesOfDelay()),
                closure,
                disableEvaluationOfMethodCallsUsingCompanionMethods);
    }

    @Override
    public EvaluationContext dropConditionManager() {
        ConditionManager cm = ConditionManager.initialConditionManager(getPrimitives());
        return new SAEvaluationContext(statementAnalysis,
                myMethodAnalyser, statementAnalyser, analyserContext, localAnalysers,
                iteration, cm, closure, disableEvaluationOfMethodCallsUsingCompanionMethods);
    }

    public EvaluationContext childState(Expression state) {
        return new SAEvaluationContext(statementAnalysis,
                myMethodAnalyser, statementAnalyser, analyserContext, localAnalysers,
                iteration, conditionManager.addState(state, state.causesOfDelay()), closure,
                false);
    }

        /*
        differs sufficiently from the regular getProperty, in that it fast tracks as soon as one of the not nulls
        reaches EFFECTIVELY_NOT_NULL, and that it always reads from the initial value of variables.
         */

    @Override
    public boolean isNotNull0(Expression value, boolean useEnnInsteadOfCnn) {
        if (value instanceof IsVariableExpression ve) {
            VariableInfo variableInfo = findForReading(ve.variable(), getInitialStatementTime(), true);
            DV cnn = variableInfo.getProperty(useEnnInsteadOfCnn ? EXTERNAL_NOT_NULL : CONTEXT_NOT_NULL);
            if (cnn.ge(MultiLevel.EFFECTIVELY_NOT_NULL_DV)) return true;
            DV nne = variableInfo.getProperty(NOT_NULL_EXPRESSION);
            if (nne.ge(MultiLevel.EFFECTIVELY_NOT_NULL_DV)) return true;
            return notNullAccordingToConditionManager(ve.variable());
        }
        return MultiLevel.isEffectivelyNotNull(getProperty(value, NOT_NULL_EXPRESSION,
                true, false));
    }

    /*
    this one is meant for non-eventual types (for now). After/before errors are caught in EvaluationResult
     */
    @Override
    public boolean cannotBeModified(Expression value) {
        if (value instanceof IsVariableExpression ve) {
            VariableInfo variableInfo = findForReading(ve.variable(), getInitialStatementTime(), true);
            DV cImm = variableInfo.getProperty(CONTEXT_IMMUTABLE);
            if (MultiLevel.isAtLeastEffectivelyE2Immutable(cImm)) return true;
            DV imm = variableInfo.getProperty(IMMUTABLE);
            if (MultiLevel.isAtLeastEffectivelyE2Immutable(imm)) return true;
            DV extImm = variableInfo.getProperty(EXTERNAL_IMMUTABLE);
            if (MultiLevel.isAtLeastEffectivelyE2Immutable(extImm)) return true;
            DV formal = analyserContext.defaultImmutable(variableInfo.variable().parameterizedType(), false);
            return MultiLevel.isAtLeastEffectivelyE2Immutable(formal);
        }
        DV valueProperty = getProperty(value, IMMUTABLE, true, false);
        return MultiLevel.isAtLeastEffectivelyE2Immutable(valueProperty);
    }

    private DV getVariableProperty(Variable variable, Property property, boolean duringEvaluation) {
        if (duringEvaluation) {
            return getPropertyFromPreviousOrInitial(variable, property, getInitialStatementTime());
        }
        return getProperty(variable, property);
    }

    @Override
    public DV getProperty(Expression value, Property property, boolean duringEvaluation,
                          boolean ignoreStateInConditionManager) {
        // IMPORTANT: here we do not want to catch VariableValues wrapped in the PropertyWrapper
        if (value instanceof IsVariableExpression ve) {
            Variable variable = ve.variable();
            // read what's in the property map (all values should be there) at initial or current level
            DV inMap = getVariableProperty(variable, property, duringEvaluation);
            if (property == NOT_NULL_EXPRESSION) {
                if (variable.parameterizedType().isPrimitiveExcludingVoid()) {
                    return MultiLevel.EFFECTIVELY_NOT_NULL_DV;
                }
                DV cnn = getVariableProperty(variable, CONTEXT_NOT_NULL, duringEvaluation);
                DV cnnInMap = cnn.max(inMap);
                if (cnnInMap.isDelayed()) {
                    // we return even if cmNn would be ENN, because our value could be higher
                    return cnnInMap;
                }
                boolean cmNn = notNullAccordingToConditionManager(variable);
                return cnnInMap.max(cmNn ? MultiLevel.EFFECTIVELY_NOT_NULL_DV : MultiLevel.NULLABLE_DV);
            }

            if (property == IMMUTABLE) {
                DV formally = getAnalyserContext().defaultImmutable(variable.parameterizedType(), false);
                if (formally.equals(IMMUTABLE.bestDv)) return formally; // EFFECTIVELY_E2, for primitives etc.
                if (isMyself(variable.parameterizedType())) return MultiLevel.MUTABLE_DV;
                DV formallyInMap = formally.max(inMap);
                if (formallyInMap.isDelayed()) {
                    return formally;
                }
                DV cImm = getVariableProperty(variable, CONTEXT_IMMUTABLE, duringEvaluation);
                if (cImm.isDelayed()) {
                    return cImm;
                }
                return cImm.max(formallyInMap);
            }
            return inMap;
        }

        if (NOT_NULL_EXPRESSION == property) {
            if (ignoreStateInConditionManager) {
                EvaluationContext customEc = new SAEvaluationContext(statementAnalysis,
                        myMethodAnalyser, statementAnalyser, analyserContext, localAnalysers,
                        iteration, conditionManager.withoutState(getPrimitives()), closure);
                return value.getProperty(customEc, NOT_NULL_EXPRESSION, true);
            }

            DV directNN = value.getProperty(this, NOT_NULL_EXPRESSION, true);
            // assert !Primitives.isPrimitiveExcludingVoid(value.returnType()) || directNN == MultiLevel.EFFECTIVELY_NOT_NULL;

            if (directNN.equals(MultiLevel.NULLABLE_DV)) {
                Expression valueIsNull = Equals.equals(Identifier.generate(),
                        this, value, NullConstant.NULL_CONSTANT, false);
                Expression evaluation = conditionManager.evaluate(this, valueIsNull);
                if (evaluation.isBoolValueFalse()) {
                    // IMPROVE should not necessarily be ENN, could be ContentNN depending
                    return MultiLevel.EFFECTIVELY_NOT_NULL_DV.max(directNN);
                }
            }
            return directNN;
        }

        // redirect to Value.getProperty()
        // this is the only usage of this method; all other evaluation of a Value in an evaluation context
        // must go via the current method
        return value.getProperty(this, property, true);
    }

    @Override
    public boolean notNullAccordingToConditionManager(Variable variable) {
        return notNullAccordingToConditionManager(variable, statementAnalysis::findOrThrow);
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

    /*
        Important that the closure is used for local variables and parameters (we'd never find them otherwise).
        However, fields will be introduced in StatementAnalysis.fromFieldAnalyserIntoInitial and should
        have their own local copy.
         */
    private VariableInfo findForReading(Variable variable, int statementTime, boolean isNotAssignmentTarget) {
        if (closure != null && isNotMine(variable) && !(variable instanceof FieldReference)) {
            return ((SAEvaluationContext) closure).findForReading(variable, statementTime, isNotAssignmentTarget);
        }
        return initialValueForReading(variable, statementTime, isNotAssignmentTarget);
    }


    /**
     * Find a variable for reading. Intercepts variable fields and local variables.
     * This is the general method that must be used by the evaluation context, currentInstance, currentValue
     *
     * @param variable the variable
     * @return the most current variable info object
     */
    public VariableInfo initialValueForReading(@NotNull Variable variable,
                                               int statementTime,
                                               boolean isNotAssignmentTarget) {
        String fqn = variable.fullyQualifiedName();
        if (!statementAnalysis.variableIsSet(fqn)) {
            assert !(variable instanceof ParameterInfo) :
                    "Parameter " + variable.fullyQualifiedName() + " should be known in " + methodInfo().fullyQualifiedName
                            + ", statement " + statementAnalysis.index();
            return new VariableInfoImpl(variable); // no value, no state; will be created by a MarkRead
        }
        VariableInfoContainer vic = statementAnalysis.getVariable(fqn);
        VariableInfo vi = vic.getPreviousOrInitial();
        if (isNotAssignmentTarget) {
            if (vi.variable() instanceof FieldReference fieldReference) {
                if (vi.isConfirmedVariableField()) {
                    LocalVariableReference copy = statementAnalysis.createCopyOfVariableField(fieldReference, vi, statementTime);
                    if (!statementAnalysis.variableIsSet(copy.fullyQualifiedName())) {
                        // it is possible that the field has been assigned to, so it exists, but the local copy does not yet
                        return new VariableInfoImpl(variable);
                    }
                    return statementAnalysis.getVariable(copy.fullyQualifiedName()).getPreviousOrInitial();
                }
                if (vi.statementTimeDelayed()) {
                    return new VariableInfoImpl(variable);
                }
            }
            if (vic.variableNature().isLocalVariableInLoopDefinedOutside()) {
                StatementAnalysisImpl relevantLoop = (StatementAnalysisImpl) statementAnalysis.mostEnclosingLoop();
                if (relevantLoop.localVariablesAssignedInThisLoop.isFrozen()) {
                    if (relevantLoop.localVariablesAssignedInThisLoop.contains(fqn)) {
                        LocalVariableReference localCopy = statementAnalysis.createLocalLoopCopy(vi.variable(), relevantLoop.index);
                        // at this point we are certain the local copy exists
                        VariableInfoContainer newVic = statementAnalysis.getVariable(localCopy.fullyQualifiedName());
                        return newVic.getPreviousOrInitial();
                    }
                    return vi; // we don't participate in the modification process?
                }
                return new VariableInfoImpl(variable); // no value, no state
            }
        } // else we need to go to the variable itself
        return vi;
    }

    private boolean isNotMine(Variable variable) {
        return getCurrentType() != variable.getOwningType();
    }

    // we pass on the information about the potential newly created local variable copy
    @Override
    public Expression currentValue(Variable variable, int statementTime, ForwardEvaluationInfo forwardEvaluationInfo) {
        VariableInfo variableInfo = findForReading(variable, statementTime, forwardEvaluationInfo.isNotAssignmentTarget());

        // important! do not use variable in the next statement, but variableInfo.variable()
        // we could have redirected from a variable field to a local variable copy
        if (forwardEvaluationInfo.assignToField()
                && variable instanceof LocalVariableReference lvr && lvr.variable.nature().localCopyOf() == null) {
            return variableInfo.getValue();
        }
        return variableInfo.getVariableValue(forwardEvaluationInfo.assignmentTarget());
    }

    @Override
    public Expression currentValue(Variable variable, int statementTime) {
        VariableInfo variableInfo = findForReading(variable, statementTime, true);
        Expression value = variableInfo.getValue();

        // redirect to other variable
        VariableExpression ve;
        if ((ve = value.asInstanceOf(VariableExpression.class)) != null) {
            assert ve.variable() != variable :
                    "Variable " + variable.fullyQualifiedName() + " has been assigned a VariableValue value pointing to itself";
            return currentValue(ve.variable(), statementTime);
        }
        return value;
    }

    @Override
    public DV getProperty(Variable variable, Property property) {
        VariableInfo vi = statementAnalysis.findOrThrow(variable);
        return vi.getProperty(property); // ALWAYS from the map!!!!
    }

    @Override
    public DV getPropertyFromPreviousOrInitial(Variable variable, Property property, int statementTime) {
        VariableInfo vi = findForReading(variable, statementTime, true);
        return vi.getProperty(property);
    }

    @Override
    public AnalyserContext getAnalyserContext() {
        return analyserContext;
    }

        /*
        The linkedVariables of a VariableExpression redirect to this method, because we have
        access to a lot more information about the variable.

        @Override
        public LinkedVariables linkedVariables(Variable variable) {
            Boolean hidden = variable.parameterizedType().isTransparent(analyserContext, myMethodAnalyser.methodInfo.typeInfo);
            int value;
            if (hidden == null) {
                value = LinkedVariables.DELAYED_VALUE;
            } else if (hidden) {
                VariableInfo variableInfo = statementAnalysis.initialValueForReading(variable, getInitialStatementTime(), true);
                int immutable = variableInfo.getProperty(IMMUTABLE);
                int level = MultiLevel.level(immutable);
                value = MultiLevel.independentCorrespondingToImmutableLevel(level);
                if (value == MultiLevel.INDEPENDENT) return LinkedVariables.EMPTY;
            } else {
                // accessible, like an assignment to the variable
                value = LinkedVariables.ASSIGNED;
            }
            return new LinkedVariables(Map.of(variable, value), value == LinkedVariables.DELAYED_VALUE);
        }
        */

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
        if (statementAnalysis.statement() instanceof LoopStatement) {
            TranslationMapImpl.Builder translationMap = new TranslationMapImpl.Builder();
            statementAnalysis.rawVariableStream()
                    .filter(e -> statementAnalysis.index().equals(e.getValue()
                            .variableNature().getStatementIndexOfThisLoopOrLoopCopyVariable()))
                    .forEach(e -> {
                        VariableInfo eval = e.getValue().best(EVALUATION);
                        Variable variable = eval.variable();

                        Map<Property, DV> valueProperties = getValueProperties(eval.getValue());
                        CausesOfDelay delays = valueProperties.values().stream()
                                .map(DV::causesOfDelay)
                                .reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge);
                        if (delays.isDone()) {
                            Expression newObject = Instance.genericMergeResult(statementAnalysis.index(),
                                    e.getValue().current().variable(), valueProperties);
                            translationMap.put(new VariableExpression(variable), newObject);
                        }

                        Expression delayed = DelayedExpression.forReplacementObject(variable.parameterizedType(),
                                eval.getLinkedVariables().remove(v -> v.equals(variable)).changeAllToDelay(delays), delays);
                        translationMap.put(DelayedVariableExpression.forVariable(variable, delays), delayed);
                    });
            return mergeValue.translate(translationMap.build());
        }
        return mergeValue;
    }

    /*
    Need to translate local copies of fields into fields.
    Should we do only their first appearance? ($0)
     */
    @Override
    public Expression acceptAndTranslatePrecondition(Expression precondition) {
        if (precondition.isBooleanConstant()) return null;
        Map<Expression, Expression> translationMap = precondition.variables().stream()
                .filter(v -> v instanceof LocalVariableReference lvr &&
                        lvr.variable.nature() instanceof VariableNature.CopyOfVariableField)
                .collect(Collectors.toUnmodifiableMap(VariableExpression::new,
                        v -> new VariableExpression(((LocalVariableReference) v).variable.nature().localCopyOf())));
        Expression translated;
        if (translationMap.isEmpty()) {
            translated = precondition;
        } else {
            // we need an evaluation context that simply translates, but does not interpret stuff
            EvaluationContext evaluationContext = new ConditionManager.EvaluationContextImpl(getAnalyserContext());
            translated = precondition.reEvaluate(evaluationContext, translationMap).getExpression();
        }
        if (translated.variables().stream()
                .allMatch(v -> v instanceof ParameterInfo || v instanceof FieldReference)) {
            return translated;
        }
        return null;
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
            return new SimpleSet(new VariableCause(variable,
                    getLocation(), CauseOfDelay.Cause.VARIABLE_DOES_NOT_EXIST));
        }
        return vi.getValue().causesOfDelay();
    }

    @Override
    public MethodInfo concreteMethod(Variable variable, MethodInfo abstractMethodInfo) {
        assert abstractMethodInfo.isAbstract();
        VariableInfo variableInfo = findForReading(variable, getInitialStatementTime(), true);
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
        VariableExpression ve;
        if ((ve = expression.asInstanceOf(VariableExpression.class)) != null) {
            VariableInfo vi = findForReading(ve.variable(), statementAnalysis.statementTime(INITIAL), true);
            return vi.getValue() != null && vi.getValue().hasState();
        }
        return expression.hasState();
    }

    @Override
    public Expression state(Expression expression) {
        VariableExpression ve;
        if ((ve = expression.asInstanceOf(VariableExpression.class)) != null) {
            VariableInfo vi = findForReading(ve.variable(), statementAnalysis.statementTime(INITIAL), true);
            return vi.getValue().state();
        }
        return expression.state();
    }

    @Override
    public VariableInfo findOrThrow(Variable variable) {
        return statementAnalysis.findOrThrow(variable);
    }

}
