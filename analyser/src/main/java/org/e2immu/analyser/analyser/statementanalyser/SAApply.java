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

import org.e2immu.analyser.analyser.Properties;
import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analyser.delay.SimpleSet;
import org.e2immu.analyser.analyser.delay.VariableCause;
import org.e2immu.analyser.analysis.FieldAnalysis;
import org.e2immu.analyser.analysis.StatementAnalysis;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.DelayedExpression;
import org.e2immu.analyser.model.expression.InlineConditional;
import org.e2immu.analyser.model.expression.IsVariableExpression;
import org.e2immu.analyser.model.impl.QualificationImpl;
import org.e2immu.analyser.model.statement.ForEachStatement;
import org.e2immu.analyser.model.statement.ThrowStatement;
import org.e2immu.analyser.model.variable.*;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.visitor.EvaluationResultVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.e2immu.analyser.analyser.Property.*;
import static org.e2immu.analyser.analyser.VariableInfoContainer.Level.EVALUATION;

record SAApply(StatementAnalysis statementAnalysis, MethodAnalyser myMethodAnalyser) {
    private static final Logger LOGGER = LoggerFactory.getLogger(SAApply.class);

    private Location getLocation() {
        return statementAnalysis.location();
    }

    private String index() {
        return statementAnalysis.index();
    }

    private MethodInfo methodInfo() {
        return statementAnalysis.methodAnalysis().getMethodInfo();
    }

    private Statement statement() {
        return statementAnalysis.statement();
    }

    /*
    The main loop calls the apply method with the results of an evaluation.
    For every variable, a number of steps are executed:
    - it is created, some local copies are created, and it is prepared for EVAL level
    - values, linked variables are set

    Finally, general modifications are carried out
     */
    ApplyStatusAndEnnStatus apply(StatementAnalyserSharedState sharedState,
                                  EvaluationResult evaluationResultIn) {
        CausesOfDelay delay = evaluationResultIn.causesOfDelay();
        EvaluationResult evaluationResult1 = variablesReadAndAssignedInSubAnalysers(evaluationResultIn,
                sharedState.evaluationContext());

        // *** this is part 2 of a cooperation to move the value of an equality in the state to the actual value
        // part 1 is in the constructor of SAEvaluationContext
        // the data is stored in the state data of statement analysis
        EvaluationResult.Builder builder = new EvaluationResult.Builder(sharedState.evaluationContext());
        statementAnalysis.stateData().equalityAccordingToStateStream().forEach(e -> {
            EvaluationResult.ChangeData cd = evaluationResult1.changeData().get(e.getKey());
            if (cd != null && cd.isMarkedRead()) {
                LinkedVariables lv = e.getValue().linkedVariables(sharedState.evaluationContext());
                builder.assignment(e.getKey(), e.getValue(), lv);
            }
        });
        EvaluationResult evaluationResult = builder.compose(evaluationResult1).build();
        // ***

        AnalyserContext analyserContext = evaluationResult.evaluationContext().getAnalyserContext();

        if (evaluationResult.addCircularCall()) {
            statementAnalysis.methodLevelData().addCircularCall();
        }

        GroupPropertyValues groupPropertyValues = new GroupPropertyValues();

        // the first part is per variable
        // order is important because we need to re-map statically assigned variables
        // but first, we need to ensure that all variables exist, independent of the later ordering

        // make a copy because we might add a variable when linking the local loop copy

        // here we set read+assigned
        evaluationResult.changeData().forEach((v, cd) -> statementAnalysis
                .ensureVariables(sharedState.evaluationContext(), v, cd, evaluationResult.statementTime()));
        Map<Variable, VariableInfoContainer> existingVariablesNotVisited = statementAnalysis.variableEntryStream(EVALUATION)
                .collect(Collectors.toMap(e -> e.getValue().current().variable(), Map.Entry::getValue,
                        (v1, v2) -> v2, HashMap::new));

        List<Map.Entry<Variable, EvaluationResult.ChangeData>> sortedEntries =
                new ArrayList<>(evaluationResult.changeData().entrySet());
        sortedEntries.sort((e1, e2) -> {
            // return variables at the end
            if (e1.getKey() instanceof ReturnVariable) return 1;
            if (e2.getKey() instanceof ReturnVariable) return -1;
            // then assignments
            if (e1.getValue().markAssignment() && !e2.getValue().markAssignment()) return -1;
            if (e2.getValue().markAssignment() && !e1.getValue().markAssignment()) return 1;
            // then the "mentions" (markRead, change linked variables, etc.)
            return e1.getKey().fullyQualifiedName().compareTo(e2.getKey().fullyQualifiedName());
        });

        for (Map.Entry<Variable, EvaluationResult.ChangeData> entry : sortedEntries) {
            Variable variable = entry.getKey();
            existingVariablesNotVisited.remove(variable);
            EvaluationResult.ChangeData changeData = entry.getValue();

            // we're now guaranteed to find the variable
            VariableInfoContainer vic = statementAnalysis.getVariable(variable.fullyQualifiedName());
            VariableInfo vi = vic.best(EVALUATION);
            VariableInfo vi1 = vic.getPreviousOrInitial();
            assert vi != vi1 : "There should already be a different EVALUATION object";

            if (changeData.markAssignment()) {
                if (conditionsForOverwritingPreviousAssignment(myMethodAnalyser, vi1, vic, changeData,
                        sharedState.localConditionManager(), sharedState.evaluationContext())) {
                    statementAnalysis.ensure(Message.newMessage(getLocation(),
                            Message.Label.OVERWRITING_PREVIOUS_ASSIGNMENT, "variable " + variable.simpleName()));
                }

                Expression bestValue = SAHelper.bestValue(changeData, vi1);
                Expression valueToWrite = maybeValueNeedsState(sharedState, vic, variable, bestValue, changeData.stateIsDelayed());

                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Write value {} to variable {}",
                            valueToWrite.output(new QualificationImpl()), // can't write lambda's properly, otherwise
                            variable.fullyQualifiedName());
                }
                // first do the properties that come with the value; later, we'll write the ones in changeData
                // ignoreConditionInCM: true, exactly because the state has been added by maybeValueNeedsState,
                // it should not be taken into account anymore. (See e.g. Loops_1)
                Properties valueProperties = sharedState.evaluationContext()
                        .getValueProperties(variable.parameterizedType(), valueToWrite, true);
                CausesOfDelay valuePropertiesIsDelayed = valueProperties.delays();

                boolean valueToWriteIsDelayed = valueToWrite.isDelayed();
                Expression valueToWritePossiblyDelayed;
                if (valueToWriteIsDelayed) {
                    valueToWritePossiblyDelayed = valueToWrite;
                } else if (valuePropertiesIsDelayed.isDelayed()) {
                    valueToWritePossiblyDelayed = valueToWrite.createDelayedValue(sharedState.evaluationContext(),
                            valuePropertiesIsDelayed);
                } else {
                    // no delays!
                    valueToWritePossiblyDelayed = valueToWrite;
                }

                Properties changeDataProperties = Properties.of(changeData.properties());
                boolean myself = sharedState.evaluationContext().isMyself(variable);
                Properties merged = SAHelper.mergeAssignment(variable, myself, valueProperties, changeDataProperties,
                        groupPropertyValues);
                // LVs start empty, the changeData.linkedVariables will be added later
                Properties combined;
                if (myself && variable instanceof FieldReference fr && !fr.fieldInfo.isStatic()) {
                    // captures self-referencing instance fields (but not static fields, as in Enum_)
                    // a similar check exists in StatementAnalysisImpl.initializeFieldReference
                    combined = sharedState.evaluationContext().ensureMyselfValueProperties(merged);
                } else {
                    combined = merged;
                }
                vic.setValue(valueToWritePossiblyDelayed, LinkedVariables.EMPTY, combined, false);

                if (vic.variableNature() instanceof VariableNature.VariableDefinedOutsideLoop) {
                    statementAnalysis.addToAssignmentsInLoop(vic, variable.fullyQualifiedName());
                }
                if (variable instanceof FieldReference fr) {
                    FieldAnalysis fieldAnalysis = analyserContext.getFieldAnalysis(fr.fieldInfo);
                    DV effFinal = fieldAnalysis.getProperty(Property.FINAL);
                    if (effFinal.isDelayed()) {
                        LOGGER.debug("Delaying statement {}, assignment to field, no idea about @Final", index());
                        delay = delay.merge(effFinal.causesOfDelay());
                    }
                }
            } else {
                if (changeData.value() != null) {
                    // a modifying method caused an updated instance value. IMPORTANT: the value properties do not change.
                    assert changeData.value().isDone();

                    Map<Property, DV> merged = SAHelper.mergePreviousAndChange(sharedState.evaluationContext(),
                            variable, vi1.getProperties(),
                            changeData.properties(), groupPropertyValues, true);

                    LinkedVariables removed = vi1.getLinkedVariables()
                            .remove(changeData.toRemoveFromLinkedVariables().variables().keySet());
                    vic.setValue(changeData.value(), removed, merged, false);
                } else {
                    if (variable instanceof This || !evaluationResult.causesOfDelay().isDelayed()) {
                        // we're not assigning (and there is no change in instance because of a modifying method)
                        // only then we copy from INIT to EVAL
                        // if the existing value is not delayed, the value properties must not be delayed either!
                        Map<Property, DV> merged = SAHelper.mergePreviousAndChange(
                                sharedState.evaluationContext(),
                                variable, vi1.getProperties(),
                                changeData.properties(), groupPropertyValues, true);
                        assert vi1.getValue().isDelayed()
                                || EvaluationContext.VALUE_PROPERTIES.stream().noneMatch(p -> merged.get(p) == null || merged.get(p).isDelayed()) :
                                "While writing to variable " + variable;
                        vic.setValue(vi1.getValue(), vi1.getLinkedVariables(), merged, false);
                    } else {
                        // delayed situation; do not copy the value properties
                        Map<Property, DV> merged = SAHelper.mergePreviousAndChange(
                                sharedState.evaluationContext(),
                                variable, vi1.getProperties(),
                                changeData.properties(), groupPropertyValues, false);
                        merged.forEach((k, v) -> vic.setProperty(k, v, EVALUATION));
                    }
                }
            }
            if (vi.isDelayed()) {
                LOGGER.debug("Apply of {}, {} is delayed because of unknown value for {}",
                        index(), methodInfo().fullyQualifiedName, variable);
                delay = delay.merge(vi.getValue().causesOfDelay());
            } else if (changeData.delays().isDelayed()) {
                LOGGER.debug("Apply of {}, {} is delayed because of delay in method call on {}",
                        index(), methodInfo().fullyQualifiedName, variable);
                delay = delay.merge(changeData.delays());
            }
        }

        /*
        The loop variable has been created in the initialisation phase. Evaluation has to wait until
        the expression of the forEach statement has been evaluated. For this reason, we need to handle
        this separately.
         */

        if (statement() instanceof ForEachStatement) {
            Variable loopVar = statementAnalysis.obtainLoopVar();
            CausesOfDelay evalForEach = statementAnalysis.evaluationOfForEachVariable(loopVar,
                    evaluationResult.getExpression(), evaluationResult.causesOfDelay(), sharedState.evaluationContext());
            delay = delay.merge(evalForEach);
        }

        ApplyStatusAndEnnStatus applyStatusAndEnnStatus = contextProperties
                (sharedState, evaluationResult, delay, analyserContext, groupPropertyValues);

        // debugging...

        for (EvaluationResultVisitor evaluationResultVisitor : analyserContext.getConfiguration()
                .debugConfiguration().evaluationResultVisitors()) {
            evaluationResultVisitor.visit(new EvaluationResultVisitor.Data(evaluationResult.evaluationContext().getIteration(),
                    methodInfo(), statementAnalysis.index(), statementAnalysis, evaluationResult));
        }

        return applyStatusAndEnnStatus;
    }

    private EvaluationResult variablesReadAndAssignedInSubAnalysers(EvaluationResult evaluationResultIn,
                                                                    EvaluationContext evaluationContext) {
        List<Variable> readBySubAnalysers = statementAnalysis.variablesReadBySubAnalysers();

        if (!readBySubAnalysers.isEmpty()) {
            EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext);
            builder.compose(evaluationResultIn);
            for (Variable variable : readBySubAnalysers) {
                builder.markRead(variable);
            }
            return builder.build();
        }

        return evaluationResultIn;
    }

    private boolean variableUnknown(Variable variable) {
        if (!statementAnalysis.variableIsSet(variable.fullyQualifiedName())) return true;
        IsVariableExpression ive;
        if (variable instanceof FieldReference fr
                && fr.scope != null
                && ((ive = fr.scope.asInstanceOf(IsVariableExpression.class)) != null)) {
            return variableUnknown(ive.variable());
        }
        return false;
    }

    /**
     * Compute the context properties, linked variables, preconditions. Return delays.
     */
    private ApplyStatusAndEnnStatus contextProperties(StatementAnalyserSharedState sharedState,
                                                      EvaluationResult evaluationResult,
                                                      CausesOfDelay delay,
                                                      AnalyserContext analyserContext,
                                                      GroupPropertyValues groupPropertyValues) {
        // the second one is across clusters of variables
        groupPropertyValues.addToMap(statementAnalysis, analyserContext);

        Function<Variable, LinkedVariables> linkedVariablesFromChangeData = v -> {
            EvaluationResult.ChangeData changeData = evaluationResult.changeData().get(v);
            return changeData == null ? LinkedVariables.EMPTY : changeData.linkedVariables();
        };

        // We're not only relying on the "reassigned" system (see Basics_7) because it ignores variables without
        // EVALUATION level. Instead, we use ER.toRemoveFromLinkedVariables + variables added to changeData, which moves them
        // in EVALUATION anyway
        Set<Variable> reassigned = evaluationResult.changeData().entrySet().stream()
                .filter(e -> e.getValue().markAssignment()).map(Map.Entry::getKey).collect(Collectors.toUnmodifiableSet());
        ComputeLinkedVariables computeLinkedVariables = ComputeLinkedVariables.create(statementAnalysis, EVALUATION,
                true,
                (vic, v) -> variableUnknown(v),
                reassigned,
                linkedVariablesFromChangeData,
                sharedState.evaluationContext());
        ComputeLinkedVariables computeLinkedVariablesCm = ComputeLinkedVariables.create(statementAnalysis, EVALUATION,
                false,
                (vic, v) -> variableUnknown(v),
                reassigned,
                linkedVariablesFromChangeData,
                sharedState.evaluationContext());

        computeLinkedVariables.writeClusteredLinkedVariables();

        // 1
        CausesOfDelay cnnStatus = computeLinkedVariables.write(CONTEXT_NOT_NULL,
                groupPropertyValues.getMap(CONTEXT_NOT_NULL));
        delay = delay.merge(cnnStatus);

        // 2
        CausesOfDelay ennStatus = computeLinkedVariables.write(EXTERNAL_NOT_NULL, groupPropertyValues.getMap(EXTERNAL_NOT_NULL));
        statementAnalysis.potentiallyRaiseErrorsOnNotNullInContext(evaluationResult.changeData());

        // the following statement is necessary to keep this statement from disappearing if it still has to process
        // EXT_NN (and a similar statement below for EXT_IMM); we go through ALL variables because this may be a statement
        // such as "throw new Exception()", see Basics_17
        CausesOfDelay anyEnn = statementAnalysis.variableStream()
                .map(vi -> {
                    CausesOfDelay causes = vi.getProperty(EXTERNAL_NOT_NULL).causesOfDelay();
                    if (causes.isDelayed()) {
                        // decorate, so that we have an idea which variable is the cause of the problem
                        return causes.merge(new SimpleSet(new VariableCause(vi.variable(), getLocation(),
                                CauseOfDelay.Cause.EXTERNAL_NOT_NULL)));
                    }
                    return CausesOfDelay.EMPTY;
                })
                .reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge);

        // 3
        CausesOfDelay extImmStatus = computeLinkedVariables.write(EXTERNAL_IMMUTABLE,
                groupPropertyValues.getMap(EXTERNAL_IMMUTABLE));

        CausesOfDelay anyExtImm = statementAnalysis.variableStream()
                .map(vi -> {
                    CausesOfDelay causes = vi.getProperty(EXTERNAL_IMMUTABLE).causesOfDelay();
                    if (causes.isDelayed()) {
                        return causes.merge(new SimpleSet(new VariableCause(vi.variable(), getLocation(),
                                CauseOfDelay.Cause.EXT_IMM)));
                    }
                    return CausesOfDelay.EMPTY;
                })
                .reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge);

        // 4
        CausesOfDelay cImmStatus = computeLinkedVariables.write(CONTEXT_IMMUTABLE, groupPropertyValues.getMap(CONTEXT_IMMUTABLE));
        delay = delay.merge(cImmStatus);

        // 5
        CausesOfDelay extContStatus = computeLinkedVariables.write(EXTERNAL_CONTAINER, groupPropertyValues.getMap(EXTERNAL_CONTAINER));
        CausesOfDelay anyExtCont = statementAnalysis.variableStream()
                .map(vi -> {
                    CausesOfDelay causes = vi.getProperty(EXTERNAL_CONTAINER).causesOfDelay();
                    if (causes.isDelayed()) {
                        return causes.merge(new SimpleSet(new VariableCause(vi.variable(), getLocation(),
                                CauseOfDelay.Cause.EXT_CONTAINER)));
                    }
                    return CausesOfDelay.EMPTY;
                })
                .reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge);

        // 6
        CausesOfDelay cContStatus = computeLinkedVariables.write(CONTEXT_CONTAINER, groupPropertyValues.getMap(CONTEXT_CONTAINER));
        delay = delay.merge(cContStatus);

        // 7
        CausesOfDelay cmStatus = computeLinkedVariablesCm.write(CONTEXT_MODIFIED, groupPropertyValues.getMap(CONTEXT_MODIFIED));
        delay = delay.merge(cmStatus);

        // odds and ends

        if (evaluationResult.causesOfDelay().isDone()) {
            evaluationResult.messages().getMessageStream()
                    .filter(this::acceptMessage)
                    .forEach(statementAnalysis::ensure);
        }

        // not checking on DONE anymore because any delay will also have crept into the precondition itself??
        Precondition precondition = evaluationResult.precondition();
        delay = delay.merge(statementAnalysis.applyPrecondition(precondition, sharedState.evaluationContext(),
                sharedState.localConditionManager()));
        CausesOfDelay externalDelay = ennStatus.merge(extImmStatus).merge(anyEnn)
                .merge(anyExtImm).merge(extContStatus).merge(anyExtCont);

        boolean progress = statementAnalysis.latestDelay(delay);
        return new ApplyStatusAndEnnStatus(delay, externalDelay, progress);
    }

    // filter out inline conditional on throws statements, when the state becomes "false"
    // see e.g. SwitchExpression_4, at the throws statement at the end of the method
    private boolean acceptMessage(Message m) {
        return m.message() != Message.Label.INLINE_CONDITION_EVALUATES_TO_CONSTANT
                || !(statement() instanceof ThrowStatement);
    }

    boolean conditionsForOverwritingPreviousAssignment(
            MethodAnalyser methodAnalyser,
            VariableInfo vi1,
            VariableInfoContainer vic,
            EvaluationResult.ChangeData changeData,
            ConditionManager conditionManager,
            EvaluationContext evaluationContext) {
        if (vi1.isAssigned() && !vi1.isRead() && changeData.markAssignment() &&
                changeData.readAtStatementTime().isEmpty() && !(vi1.variable() instanceof ReturnVariable)) {
            String index = vi1.getAssignmentIds().getLatestAssignmentIndex();
            StatementAnalysis sa = methodAnalyser.findStatementAnalyser(index).getStatementAnalysis();
            if (sa.stateData().conditionManagerForNextStatementStatus().isDelayed()) {
                return false; // we'll be back
            }
            ConditionManager atAssignment = sa.stateData().getConditionManagerForNextStatement();
            Expression myAbsoluteState = conditionManager.absoluteState(evaluationContext);
            Expression initialAbsoluteState = atAssignment.absoluteState(evaluationContext);
            if (!initialAbsoluteState.equals(myAbsoluteState)) return false;
            // now check if we're in loop block, and there was an assignment outside
            // this loop block will not have an effect on the absolute state (See Loops_2, Loops_13)
            VariableInfoContainer initialVic = sa.getVariable(vi1.variable().fullyQualifiedName());
            // do raise an error when the assignment is in the loop condition
            return initialVic.variableNature() instanceof VariableNature.VariableDefinedOutsideLoop ||
                    !(vic.variableNature() instanceof VariableNature.VariableDefinedOutsideLoop loop) ||
                    statementAnalysis.index().equals(loop.statementIndex());
        }
        return false;
    }


    /*
    See among others Loops_1: when a variable is assigned in a loop, it is possible that interrupts (break, etc.) have
    caused a state. If this variable is defined outside the loop, it'll have to have a value when coming out of the loop.
    The added state helps to avoid unconditional assignments.
    In i=3; while(c) { if(x) break; i=5; }, we return x?3:5; x will most likely be dependent on the loop and, be
    turned into some generic integer

    In i=3; while(c) { i=5; if(x) break; }, we return c?5:3, as soon as c has a value

    Q: what is the best place for this piece of code? EvalResult?? This here seems too late
     */
    Expression maybeValueNeedsState(StatementAnalyserSharedState sharedState,
                                    VariableInfoContainer vic,
                                    Variable variable,
                                    Expression value,
                                    CausesOfDelay stateIsDelayedInChangeData) {
        if (value.isDelayed()) {
            return value;
        }
        if (stateIsDelayedInChangeData.isDelayed()) {
            // see Precondition_3, Basics_14: both need breaking the delay loop
            CausesOfDelay marker = new SimpleSet(new VariableCause(variable, getLocation(), CauseOfDelay.Cause.STATE_DELAYED));
            if (stateIsDelayedInChangeData.causesStream().anyMatch(c -> c.cause() == CauseOfDelay.Cause.STATE_DELAYED &&
                    c instanceof VariableCause vc && vc.variable().equals(variable) && c.location().equals(vc.location()))) {
                LOGGER.debug("Breaking delay " + marker);
            } else {
                CausesOfDelay merged = stateIsDelayedInChangeData.merge(marker);
                // if the delays are caused by a field in break_init_delay, we will return the value rather than a delay
                if(merged.causesStream().anyMatch(cause -> cause.cause().equals(CauseOfDelay.Cause.BREAK_INIT_DELAY))) {
                    return value;
                }
                LinkedVariables lv = LinkedVariables.delayedEmpty(merged);
                return DelayedExpression.forState(variable.parameterizedType(), lv, merged);
            }
        }
        if (vic.variableNature() instanceof VariableNature.VariableDefinedOutsideLoop) {
            // variable defined outside loop, now in loop, not delayed

            ConditionManager localConditionManager = sharedState.localConditionManager();

            if (!localConditionManager.state().isBoolValueTrue()) {
                Expression state = localConditionManager.state();

                ForwardEvaluationInfo fwd = new ForwardEvaluationInfo(Map.of(), true, variable, true);
                // do not take vi1 itself, but "the" local copy of the variable
                EvaluationContext evaluationContext = sharedState.evaluationContext();
                Expression valueOfVariablePreAssignment = evaluationContext.currentValue(variable, fwd);

                InlineConditional inlineConditional = new InlineConditional(Identifier.generate(),
                        evaluationContext.getAnalyserContext(), state, value, valueOfVariablePreAssignment);
                return inlineConditional.optimise(evaluationContext.dropConditionManager());
            }
        }
        return value;
    }


}
