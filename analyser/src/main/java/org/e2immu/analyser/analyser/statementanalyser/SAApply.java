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
import org.e2immu.analyser.analyser.impl.ComputingMethodAnalyser;
import org.e2immu.analyser.analysis.FieldAnalysis;
import org.e2immu.analyser.analysis.StatementAnalysis;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.InlineConditional;
import org.e2immu.analyser.model.expression.IsVariableExpression;
import org.e2immu.analyser.model.statement.ForEachStatement;
import org.e2immu.analyser.model.variable.*;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.visitor.EvaluationResultVisitor;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.e2immu.analyser.analyser.Property.*;
import static org.e2immu.analyser.analyser.VariableInfoContainer.Level.EVALUATION;
import static org.e2immu.analyser.util.Logger.LogTarget.ANALYSER;
import static org.e2immu.analyser.util.Logger.LogTarget.DELAYED;
import static org.e2immu.analyser.util.Logger.log;

record SAApply(StatementAnalysis statementAnalysis, MethodAnalyser myMethodAnalyser) {

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
    SAEvaluationOfMainExpression.ApplyStatusAndEnnStatus apply(StatementAnalyserSharedState sharedState,
                                                               EvaluationResult evaluationResult,
                                                               List<PrimaryTypeAnalyser> localAnalysers) {
        CausesOfDelay delay = evaluationResult.causes();
        AnalyserContext analyserContext = evaluationResult.evaluationContext().getAnalyserContext();

        if (evaluationResult.addCircularCall()) {
            statementAnalysis.methodLevelData().addCircularCall();
        }

        GroupPropertyValues groupPropertyValues = new GroupPropertyValues();

        // the first part is per variable
        // order is important because we need to re-map statically assigned variables
        // but first, we need to ensure that all variables exist, independent of the later ordering

        // make a copy because we might add a variable when linking the local loop copy
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
                Expression valueToWrite = maybeValueNeedsState(sharedState, vic, variable, bestValue);

                log(ANALYSER, "Write value {} to variable {}", valueToWrite, variable.fullyQualifiedName());
                // first do the properties that come with the value; later, we'll write the ones in changeData
                Map<Property, DV> valueProperties = sharedState.evaluationContext()
                        .getValueProperties(valueToWrite, variable instanceof ReturnVariable);
                CausesOfDelay valuePropertiesIsDelayed = valueProperties.values().stream()
                        .map(DV::causesOfDelay)
                        .reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge);

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

                Map<Property, DV> merged = SAHelper.mergeAssignment(variable, valueProperties, changeData.properties(), groupPropertyValues);
                // LVs start empty, the changeData.linkedVariables will be added later
                vic.setValue(valueToWritePossiblyDelayed, LinkedVariables.EMPTY, merged, false);

                if (vic.variableNature().isLocalVariableInLoopDefinedOutside()) {
                    VariableInfoContainer local = statementAnalysis.addToAssignmentsInLoop(vic, variable.fullyQualifiedName());
                    // assign the value of the assignment to the local copy created
                    if (local != null) {
                        Variable localVar = local.current().variable();
                        // calculation needs to be done again, this time with the local copy rather than the original
                        // (so that we can replace the local one with instance)
                        Expression valueToWrite2 = maybeValueNeedsState(sharedState, vic, localVar, bestValue);
                        Expression valueToWriteCorrected;
                        IsVariableExpression ive;
                        if ((ive = valueToWrite2.asInstanceOf(IsVariableExpression.class)) != null &&
                                ive.variable().equals(localVar)) {
                            // not allowing j$2 to be assigned to j$2; assign to initial instead
                            valueToWriteCorrected = local.getPreviousOrInitial().getValue();
                        } else {
                            valueToWriteCorrected = valueToWrite2;
                        }
                        log(ANALYSER, "Write value {} to local copy variable {}", valueToWriteCorrected, localVar.fullyQualifiedName());
                        Map<Property, DV> merged2 = SAHelper.mergeAssignment(localVar, valueProperties,
                                changeData.properties(), groupPropertyValues);

                        LinkedVariables linkedToMain = LinkedVariables.of(variable, LinkedVariables.STATICALLY_ASSIGNED_DV);
                        local.ensureEvaluation(getLocation(),
                                new AssignmentIds(index() + EVALUATION), VariableInfoContainer.NOT_YET_READ,
                                statementAnalysis.statementTime(EVALUATION), Set.of());
                        local.setValue(valueToWriteCorrected, linkedToMain, merged2, false);
                        existingVariablesNotVisited.remove(localVar);
                    }
                }
                if (variable instanceof FieldReference fr) {
                    FieldAnalysis fieldAnalysis = analyserContext.getFieldAnalysis(fr.fieldInfo);
                    DV effFinal = fieldAnalysis.getProperty(Property.FINAL);
                    if (effFinal.isDelayed()) {
                        log(DELAYED, "Delaying statement {}, assignment to field, no idea about @Final", index());
                        delay = delay.merge(effFinal.causesOfDelay());
                    }
                }
            } else if (!SAHelper.assignmentToNonCopy(vic, evaluationResult)) {
                if (changeData.value() != null) {
                    // a modifying method caused an updated instance value

                    Map<Property, DV> merged = SAHelper.mergePreviousAndChange(sharedState.evaluationContext(),
                            variable, vi1.getProperties(),
                            changeData.properties(), groupPropertyValues, true);
                    vic.setValue(changeData.value(), vi1.getLinkedVariables(), merged, false);
                } else {
                    if (variable instanceof This || !evaluationResult.causes().isDelayed()) {
                        // TODO we used to check for "haveDelaysCausedByMethodCalls"; now assuming ALL delays
                        // we're not assigning (and there is no change in instance because of a modifying method)
                        // only then we copy from INIT to EVAL
                        // so we must integrate set properties
                        Map<Property, DV> merged = SAHelper.mergePreviousAndChange(
                                sharedState.evaluationContext(),
                                variable, vi1.getProperties(),
                                changeData.properties(), groupPropertyValues, true);
                        vic.setValue(vi1.getValue(), vi1.getLinkedVariables(), merged, false);
                    } else {
                        // delayed situation; do not copy the value properties
                        Map<Property, DV> merged = SAHelper.mergePreviousAndChange(
                                sharedState.evaluationContext(),
                                variable, vi1.getProperties(),
                                changeData.properties(), groupPropertyValues, false);
                        merged.forEach((k, v) -> vic.setProperty(k, v, false, EVALUATION));
                    }
                }
            }
            if (vi.isDelayed()) {
                log(DELAYED, "Apply of {}, {} is delayed because of unknown value for {}",
                        index(), methodInfo().fullyQualifiedName, variable);
                delay = delay.merge(vi.getValue().causesOfDelay());
            } else if (changeData.delays().isDelayed()) {
                log(DELAYED, "Apply of {}, {} is delayed because of delay in method call on {}",
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
            statementAnalysis.evaluationOfForEachVariable(loopVar, evaluationResult.getExpression(),
                    evaluationResult.causes(), sharedState.evaluationContext());
        }

        // OutputBuilderSimplified 2, statement 0 in "go", shows why we may want to copy from prev -> eval
        // This should not happen when due to an assignment, the loop copy also gets a new value. See loop above,
        // where we remove the loop copy from existingVarsNotVisited. Example in Loops_2, 9, 10
        for (Map.Entry<Variable, VariableInfoContainer> e : existingVariablesNotVisited.entrySet()) {
            VariableInfoContainer vic = e.getValue();
            if (vic.hasEvaluation()) {
                /* so we have an evaluation, but we did not get the chance to copy from previous into evaluation.
                 (this happened because an evaluation was ensured for some other reason than the pure
                  evaluation of the expression).
                At least for IMMUTABLE we need to copy the value from previous into evaluation, because
                the next statement will copy it from there
                 */
                VariableInfo prev = vic.getPreviousOrInitial();
                DV immPrev = prev.getProperty(IMMUTABLE);
                if (immPrev.isDone()) {
                    vic.setProperty(IMMUTABLE, immPrev, EVALUATION);
                }
            }
        }

        // the second one is across clusters of variables
        addToMap(groupPropertyValues, analyserContext);

        if (statement() instanceof ForEachStatement) {
            Variable loopVar = statementAnalysis.obtainLoopVar();
            potentiallyUpgradeCnnOfLocalLoopVariableAndCopy(sharedState.evaluationContext(),
                    groupPropertyValues.getMap(EXTERNAL_NOT_NULL),
                    groupPropertyValues.getMap(CONTEXT_NOT_NULL), evaluationResult.value(),
                    evaluationResult.causes(), loopVar);
        }

        Function<Variable, LinkedVariables> linkedVariablesFromChangeData = v -> {
            EvaluationResult.ChangeData changeData = evaluationResult.changeData().get(v);
            return changeData == null ? LinkedVariables.EMPTY : changeData.linkedVariables();
        };
        Set<Variable> reassigned = evaluationResult.changeData().entrySet().stream()
                .filter(e -> e.getValue().markAssignment()).map(Map.Entry::getKey).collect(Collectors.toUnmodifiableSet());
        ComputeLinkedVariables computeLinkedVariables = ComputeLinkedVariables.create(statementAnalysis, EVALUATION,
                v -> false,
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

        // 3
        CausesOfDelay extImmStatus = computeLinkedVariables.write(EXTERNAL_IMMUTABLE,
                groupPropertyValues.getMap(EXTERNAL_IMMUTABLE));

        // 4
        CausesOfDelay cImmStatus = computeLinkedVariables.write(CONTEXT_IMMUTABLE, groupPropertyValues.getMap(CONTEXT_IMMUTABLE));

        // 5
        importContextModifiedValuesForThisFromSubTypes(analyserContext, localAnalysers, groupPropertyValues.getMap(CONTEXT_MODIFIED));
        CausesOfDelay cmStatus = computeLinkedVariables.write(CONTEXT_MODIFIED,
                groupPropertyValues.getMap(CONTEXT_MODIFIED));
        delay = delay.merge(cmStatus);

        // odds and ends

        if (evaluationResult.causes().isDone()) {
            evaluationResult.messages().getMessageStream().forEach(statementAnalysis::ensure);
        }

        // not checking on DONE anymore because any delay will also have crept into the precondition itself??
        Precondition precondition = evaluationResult.precondition();
        delay = delay.merge(statementAnalysis.applyPrecondition(precondition, sharedState.evaluationContext(),
                sharedState.localConditionManager()));

        // debugging...

        for (EvaluationResultVisitor evaluationResultVisitor : analyserContext.getConfiguration()
                .debugConfiguration().evaluationResultVisitors()) {
            evaluationResultVisitor.visit(new EvaluationResultVisitor.Data(evaluationResult.evaluationContext().getIteration(),
                    methodInfo(), statementAnalysis.index(), statementAnalysis, evaluationResult));
        }

        return new SAEvaluationOfMainExpression.ApplyStatusAndEnnStatus(delay, ennStatus.merge(extImmStatus).merge(cImmStatus));
    }


    private void importContextModifiedValuesForThisFromSubTypes(AnalyserContext analyserContext,
                                                                List<PrimaryTypeAnalyser> localAnalysers,
                                                                Map<Variable, DV> map) {
        DV bestInSub = localAnalysers.stream()
                .flatMap(PrimaryTypeAnalyser::methodAnalyserStream)
                .filter(ma -> ma instanceof ComputingMethodAnalyser)
                .map(ma -> ((ComputingMethodAnalyser) ma).getThisAsVariable())
                .filter(Objects::nonNull)
                .map(variableInfo -> variableInfo.getProperty(CONTEXT_MODIFIED))
                .reduce(DV.MIN_INT_DV, DV::max);
        if (bestInSub != DV.MIN_INT_DV) {
            Variable thisVar = new This(analyserContext, methodInfo().typeInfo);
            DV myValue = map.getOrDefault(thisVar, null);
            DV merged = myValue == null ? bestInSub : myValue.max(bestInSub);
            map.put(thisVar, merged);
        }
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
            if (sa.stateData().conditionManagerForNextStatement.isVariable()) {
                return false; // we'll be back
            }
            ConditionManager atAssignment = sa.stateData().conditionManagerForNextStatement.get();
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
                                    Expression value) {
        boolean valueIsDelayed = value.isDelayed();
        if (valueIsDelayed || !(vic.variableNature() instanceof VariableNature.VariableDefinedOutsideLoop)) {
            // not applicable
            return value;
        }
        // variable defined outside loop, now in loop, not delayed

        ConditionManager localConditionManager = sharedState.localConditionManager();

        if (!localConditionManager.state().isBoolValueTrue()) {
            Expression state = localConditionManager.state();

            ForwardEvaluationInfo fwd = new ForwardEvaluationInfo(Map.of(), true, variable);
            // do not take vi1 itself, but "the" local copy of the variable
            EvaluationContext evaluationContext = sharedState.evaluationContext();
            Expression valueOfVariablePreAssignment = evaluationContext.currentValue(variable,
                    statementAnalysis.statementTime(VariableInfoContainer.Level.INITIAL), fwd);

            InlineConditional inlineConditional = new InlineConditional(Identifier.generate(),
                    evaluationContext.getAnalyserContext(), state, value, valueOfVariablePreAssignment);
            return inlineConditional.optimise(evaluationContext.dropConditionManager());
        }
        return value;
    }


    void potentiallyUpgradeCnnOfLocalLoopVariableAndCopy(EvaluationContext evaluationContext,
                                                         Map<Variable, DV> externalNotNull,
                                                         Map<Variable, DV> contextNotNull,
                                                         Expression value,
                                                         CausesOfDelay delays,
                                                         Variable loopVar) {
        assert contextNotNull.containsKey(loopVar); // must be present!
        if (delays.isDelayed()) {
            // we want to avoid a particular value on EVAL for the loop variable
            contextNotNull.put(loopVar, delays);
            externalNotNull.put(loopVar, MultiLevel.NOT_INVOLVED_DV);
        } else {
            DV nne = evaluationContext.getProperty(value, NOT_NULL_EXPRESSION, false, false);
            boolean variableNotNull = nne.ge(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV);
            if (variableNotNull) {
                DV oneLevelLess = MultiLevel.composeOneLevelLessNotNull(nne);

                contextNotNull.put(loopVar, oneLevelLess);
                externalNotNull.put(loopVar, MultiLevel.NOT_INVOLVED_DV);

                LocalVariableReference copyVar = statementAnalysis.createLocalLoopCopy(loopVar, statementAnalysis.index());
                if (contextNotNull.containsKey(copyVar)) {
                    // can be delayed to the next iteration
                    contextNotNull.put(copyVar, MultiLevel.EFFECTIVELY_NOT_NULL_DV);
                }
            }
        }
    }

    void addToMap(GroupPropertyValues groupPropertyValues, AnalyserContext analyserContext) {
        addToMap(groupPropertyValues, CONTEXT_NOT_NULL, x -> AnalysisProvider.defaultNotNull(x.parameterizedType()), true);
        addToMap(groupPropertyValues, EXTERNAL_NOT_NULL, x ->
                new SimpleSet(new VariableCause(x, statementAnalysis.location(),
                        CauseOfDelay.Cause.EXTERNAL_NOT_NULL)), false);
        addToMap(groupPropertyValues, EXTERNAL_IMMUTABLE, x -> analyserContext.defaultImmutable(x.parameterizedType(), false), false);
        addToMap(groupPropertyValues, CONTEXT_IMMUTABLE, x -> MultiLevel.NOT_INVOLVED_DV, true);
        addToMap(groupPropertyValues, CONTEXT_MODIFIED, x -> DV.FALSE_DV, true);
    }

    void addToMap(GroupPropertyValues groupPropertyValues,
                  Property property,
                  Function<Variable, DV> falseValue,
                  boolean complainDelay0) {
        Map<Variable, DV> map = groupPropertyValues.getMap(property);
        statementAnalysis.rawVariableStream().forEach(e -> {
            VariableInfoContainer vic = e.getValue();
            VariableInfo vi1 = vic.getPreviousOrInitial();
            if (!map.containsKey(vi1.variable())) { // variables that don't occur in contextNotNull
                DV prev = vi1.getProperty(property);
                if (prev.isDone()) {
                    if (vic.hasEvaluation()) {
                        VariableInfo vi = vic.best(EVALUATION);
                        DV eval = vi.getProperty(property);
                        if (eval.isDelayed()) {
                            map.put(vi.variable(), prev.maxIgnoreDelay(falseValue.apply(vi.variable())));
                        } else {
                            map.put(vi.variable(), eval);
                        }
                    } else {
                        map.put(vi1.variable(), prev);
                    }
                } else {
                    map.put(vi1.variable(), prev);
                    if (complainDelay0 && "0".equals(statementAnalysis.index())) {
                        throw new UnsupportedOperationException(
                                "Impossible, all variables start with non-delay: " + vi1.variable().fullyQualifiedName()
                                        + ", prop " + property);
                    }
                }
            }
        });
    }

}
