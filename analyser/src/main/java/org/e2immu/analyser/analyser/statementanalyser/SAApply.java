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
import org.e2immu.analyser.analyser.delay.*;
import org.e2immu.analyser.analysis.StatementAnalysis;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.impl.QualificationImpl;
import org.e2immu.analyser.model.statement.*;
import org.e2immu.analyser.model.variable.*;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.visitor.EvaluationResultVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.e2immu.analyser.analyser.Property.*;
import static org.e2immu.analyser.analyser.Stage.EVALUATION;
import static org.e2immu.analyser.analyser.Stage.INITIAL;

record SAApply(StatementAnalysis statementAnalysis, MethodAnalyser myMethodAnalyser) {
    private static final Logger LOGGER = LoggerFactory.getLogger(SAApply.class);

    private Location getLocation() {
        return statementAnalysis.location(EVALUATION);
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
        EvaluationResult evaluationResult = potentiallyModifyEvaluationResult(sharedState, evaluationResultIn);

        AnalyserContext analyserContext = evaluationResult.evaluationContext().getAnalyserContext();
        GroupPropertyValues groupPropertyValues = new GroupPropertyValues();

        // the first part is per variable
        // order is important because we need to re-map statically assigned variables
        // but first, we need to ensure that all variables exist, independent of the later ordering

        // make a copy because we might add a variable when linking the local loop copy

        // here we set read+assigned
        evaluationResult.changeData().forEach((v, cd) -> statementAnalysis
                .ensureVariable(sharedState.evaluationContext(), v, cd, evaluationResult.statementTime()));
        Map<Variable, VariableInfoContainer> existingVariablesNotVisited = statementAnalysis.variableEntryStream(EVALUATION)
                .collect(Collectors.toMap(e -> e.getValue().current().variable(), Map.Entry::getValue,
                        (v1, v2) -> v2, HashMap::new));
        Map<Variable, VariableInfoContainer> variablesDefinedOutsideLoop = statementAnalysis.rawVariableStream()
                .filter(e -> e.getValue().variableNature() instanceof VariableNature.VariableDefinedOutsideLoop)
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

        Location initialLocation = statementAnalysis.location(INITIAL);
        Optional<VariableCause> optBreakInitDelay = evaluationResult.causesOfDelay()
                .findVariableCause(CauseOfDelay.Cause.BREAK_INIT_DELAY, vc -> vc.location().equals(initialLocation));

        CausesOfDelay cumulativeDelay = evaluationResult.causesOfDelay();
        boolean progress = false;

        for (Map.Entry<Variable, EvaluationResult.ChangeData> entry : sortedEntries) {
            Variable variable = entry.getKey();
            existingVariablesNotVisited.remove(variable);
            variablesDefinedOutsideLoop.remove(variable);
            EvaluationResult.ChangeData changeData = entry.getValue();

            // we're now guaranteed to find the variable
            VariableInfoContainer vic = statementAnalysis.getVariable(variable.fullyQualifiedName());
            VariableInfo vi = vic.best(EVALUATION);
            VariableInfo vi1 = vic.getPreviousOrInitial();
            assert vi != vi1 : "There should already be a different EVALUATION object";

            if (changeData.markAssignment()) {
                progress |= markAssignment(sharedState, groupPropertyValues, variable, changeData, vic, vi, vi1);
                cumulativeDelay = cumulativeDelay.merge(vi.getValue().causesOfDelay());
            } else {
                if (changeData.value() != null && (changeData.value().isDone()
                        || !(vi1.getValue() instanceof DelayedWrappedExpression)) && !vi1.isDelayed()) {
                    progress |= changeValueWithoutAssignment(sharedState, groupPropertyValues, variable, changeData, vic, vi1);
                    cumulativeDelay = cumulativeDelay.merge(vi.getValue().causesOfDelay());
                } else {
                    AnalysisStatus status = noValueChange(sharedState, evaluationResult.causesOfDelay(), groupPropertyValues,
                            optBreakInitDelay.orElse(null), variable, changeData, vic, vi, vi1);
                    cumulativeDelay = cumulativeDelay.merge(status.causesOfDelay());
                    progress |= status.isProgress();
                }
            }
        }

        for (Map.Entry<Variable, VariableInfoContainer> e : variablesDefinedOutsideLoop.entrySet()) {
            LoopResult loopResult = setValueForVariablesInLoopDefinedOutsideAssignedInside(sharedState, e.getKey(),
                    e.getValue(), e.getValue().best(EVALUATION), null, groupPropertyValues);
            if (loopResult.wroteValue) existingVariablesNotVisited.remove(e.getKey());
            cumulativeDelay = cumulativeDelay.merge(loopResult.delays);
            progress |= loopResult.progress();
        }

        // idea: variables which already have an evaluation, but do not feature (anymore) in the evaluation result
        // or where the eval was created in contextProperties() in an earlier iteration (e.g. InstanceOf_9)
        for (Map.Entry<Variable, VariableInfoContainer> e : existingVariablesNotVisited.entrySet()) {
            if (!(statement() instanceof ExplicitConstructorInvocation
                    && e.getKey() instanceof FieldReference fr && fr.scopeIsThis())) {
                AnalysisStatus status = e.getValue().copyFromPreviousOrInitialIntoEvaluation();
                cumulativeDelay = cumulativeDelay.merge(status.causesOfDelay());
                progress |= status.isProgress();
            }
        }

        ProgressAndDelay delayStatus = new ProgressAndDelay(progress, cumulativeDelay);
        ApplyStatusAndEnnStatus applyStatusAndEnnStatus = contextProperties(sharedState, evaluationResult,
                delayStatus, analyserContext, groupPropertyValues);

        // debugging...

        for (EvaluationResultVisitor evaluationResultVisitor : analyserContext.getConfiguration()
                .debugConfiguration().evaluationResultVisitors()) {
            int iteration = evaluationResult.evaluationContext().getIteration();
            evaluationResultVisitor.visit(new EvaluationResultVisitor.Data(iteration,
                    methodInfo(), statementAnalysis.index(), statementAnalysis, evaluationResult,
                    applyStatusAndEnnStatus.status(), applyStatusAndEnnStatus.ennStatus()));
        }

        return applyStatusAndEnnStatus;
    }

    private EvaluationResult potentiallyModifyEvaluationResult(StatementAnalyserSharedState sharedState,
                                                               EvaluationResult evaluationResultIn) {
        EvaluationResult evaluationResult1 = variablesReadOrModifiedInSubAnalysers(evaluationResultIn,
                sharedState.context());

        EvaluationResult evaluationResult;
        if (statementAnalysis.statement() instanceof ExpressionAsStatement || statementAnalysis.statement() instanceof AssertStatement) {
            evaluationResult = SAHelper.scopeVariablesForPatternVariables(evaluationResult1, index());
        } else if (statementAnalysis.statement() instanceof ForEachStatement) {
           /*
            The loop variable has been created in the initialisation phase. Evaluation has to wait until
            the expression of the forEach statement has been evaluated. For this reason, we need to handle
            this separately.
            */
            Variable loopVar = statementAnalysis.obtainLoopVar();
            evaluationResult = statementAnalysis.evaluationOfForEachVariable(loopVar,
                    evaluationResultIn.getExpression(), evaluationResultIn.causesOfDelay(), evaluationResult1);

        } else {
            evaluationResult = evaluationResult1;
        }
        return evaluationResult;
    }

    private AnalysisStatus noValueChange(StatementAnalyserSharedState sharedState,
                                         CausesOfDelay delayIn,
                                         GroupPropertyValues groupPropertyValues,
                                         VariableCause optBreakInitDelay,
                                         Variable variable,
                                         EvaluationResult.ChangeData changeData,
                                         VariableInfoContainer vic,
                                         VariableInfo vi,
                                         VariableInfo vi1) {
        LoopResult loopResult = setValueForVariablesInLoopDefinedOutsideAssignedInside(sharedState,
                variable, vic, vi, changeData, groupPropertyValues);
        CausesOfDelay delay = delayIn.merge(loopResult.delays);
        boolean progress;
        if (!loopResult.wroteValue) {
            if (variable instanceof This
                    || !delayIn.isDelayed()
                    || optBreakInitDelay != null
                    || vi1.getValue() instanceof DelayedWrappedExpression) {
                // we're not assigning (and there is no change in instance because of a modifying method)
                // only then we copy from INIT to EVAL
                // if the existing value is not delayed, the value properties must not be delayed either!
                Map<Property, DV> merged = SAHelper.mergePreviousAndChange(
                        sharedState.evaluationContext(),
                        variable, vi1.getProperties(),
                        changeData.properties(), groupPropertyValues, true);
                assert vi1.getValue().isDelayed()
                        || vi1.getValue().isNotYetAssigned()
                        || EvaluationContext.VALUE_PROPERTIES.stream().noneMatch(p -> merged.get(p) == null || merged.get(p).isDelayed()) :
                        "While writing to variable " + variable;
                Expression value;
                if (optBreakInitDelay != null && vi1.isDelayed()) {
                    // if we break a delay, but the value that we write is still delayed, we propagate the delay (see Basics_7_1)
                    LOGGER.debug("Propagate break init delay in {} in {}", variable, index());
                    value = vi1.getValue().mergeDelays(DelayFactory.createDelay(optBreakInitDelay));
                } else {
                    value = vi1.getValue();
                }
                progress = vic.setValue(value, null, Properties.of(merged), EVALUATION);
                delay = delay.merge(value.causesOfDelay());
            } else {
                // delayed situation; do not copy the value properties UNLESS there is a break delay
                Map<Property, DV> merged = SAHelper.mergePreviousAndChange(
                        sharedState.evaluationContext(),
                        variable, vi1.getProperties(),
                        changeData.properties(), groupPropertyValues, false);
                progress = merged.entrySet().stream().map(e -> vic.setProperty(e.getKey(), e.getValue(), EVALUATION))
                        .reduce(false, Boolean::logicalOr);
                // copy the causes of the delay in the value, so that the SAEvaluationContext.breakDelay can notice
                vic.setDelayedValue(delayIn, EVALUATION);
            }
        } else {
            progress = false;
        }
        return ProgressWrapper.of(progress, delay);
    }

    // return progress
    private boolean changeValueWithoutAssignment(StatementAnalyserSharedState sharedState,
                                                 GroupPropertyValues groupPropertyValues,
                                                 Variable variable,
                                                 EvaluationResult.ChangeData changeData,
                                                 VariableInfoContainer vic,
                                                 VariableInfo vi1) {
        // a modifying method caused an updated instance value. IMPORTANT: the value properties do not change.
        // see e.g. UpgradableBooleanMap, E2InContext_0, _2
        //assert changeData.value().isDone();
        // we cannot let DWEs be changed into other delays... they must be passed on (e.g. StaticSideEffects_1)

        Map<Property, DV> merged = SAHelper.mergePreviousAndChange(sharedState.evaluationContext(),
                variable, vi1.getProperties(),
                changeData.properties(), groupPropertyValues, true);

        Properties properties = Properties.of(merged);
        CausesOfDelay causesOfDelay = properties.delays();
        Expression toWrite;
        if (changeData.value().isDone() && causesOfDelay.isDelayed()) {
            // cannot yet change to changeData.value()...
            toWrite = DelayedExpression.forDelayedValueProperties(changeData.value().getIdentifier(),
                    changeData.value().returnType(), changeData.value(), causesOfDelay, Properties.EMPTY);
        } else {
            toWrite = changeData.value();
        }
        return vic.setValue(toWrite, null, properties, EVALUATION);
    }

    // returns progress
    private boolean markAssignment(StatementAnalyserSharedState sharedState,
                                   GroupPropertyValues groupPropertyValues,
                                   Variable variable,
                                   EvaluationResult.ChangeData changeData,
                                   VariableInfoContainer vic,
                                   VariableInfo vi,
                                   VariableInfo vi1) {
        if (vi.valueIsSet()) return false;
        if (conditionsForOverwritingPreviousAssignment(myMethodAnalyser, vi1, vic, changeData,
                sharedState.localConditionManager(), sharedState.context())) {
            statementAnalysis.ensure(Message.newMessage(getLocation(),
                    Message.Label.OVERWRITING_PREVIOUS_ASSIGNMENT, "variable " + variable.simpleName()));
        }

        Expression bestValue = SAHelper.bestValue(changeData, vi1);
        Expression valueToWrite = maybeValueNeedsState(sharedState, vic, variable, bestValue, changeData.stateIsDelayed());

        // first do the properties that come with the value; later, we'll write the ones in changeData
        // ignoreConditionInCM: true, exactly because the state has been added by maybeValueNeedsState,
        // it should not be taken into account anymore. (See e.g. Loops_1)
        Properties valueProperties = sharedState.evaluationContext()
                .getValueProperties(variable.parameterizedType(), valueToWrite, true);
        Properties externalProperties = sharedState.evaluationContext().getExternalProperties(valueToWrite);

        Expression valueToWritePossiblyDelayed = delayAssignmentValue(sharedState, valueToWrite, valueProperties.delays());

        Properties changeDataProperties = Properties.of(changeData.properties());
        boolean myself = sharedState.evaluationContext().isMyself(variable);
        Properties merged = SAHelper.mergeAssignment(variable, valueProperties, changeDataProperties,
                externalProperties, groupPropertyValues);
        // LVs start empty, the changeData.linkedVariables will be added later
        Properties combined;
        if (myself && variable instanceof FieldReference fr && !fr.fieldInfo.isStatic()) {
            // captures self-referencing instance fields (but not static fields, as in Enum_)
            // a similar check exists in StatementAnalysisImpl.initializeFieldReference
            combined = sharedState.evaluationContext().ensureMyselfValueProperties(merged);
        } else {
            combined = merged;
        }

        boolean progressSet;
        if (!detectBreakDelayInAssignment(variable, vic, changeData, valueToWrite,
                valueToWritePossiblyDelayed, combined, sharedState.evaluationContext().getAnalyserContext())) {
            // the field analyser con spot DelayedWrappedExpressions but cannot compute its value properties, as it does not have the same
            // evaluation context
            if (LOGGER.isDebugEnabled() && valueToWritePossiblyDelayed.isDone()) {
                LOGGER.debug("Write value {} to variable {}",
                        valueToWritePossiblyDelayed.output(new QualificationImpl()), // can't write lambda's properly, otherwise
                        variable.fullyQualifiedName());
            }
            progressSet = vic.setValue(valueToWritePossiblyDelayed, null, combined, EVALUATION);
        } else {
            progressSet = false;
        }
        if (vic.variableNature() instanceof VariableNature.VariableDefinedOutsideLoop) {
            boolean progressAdd = statementAnalysis.addToAssignmentsInLoop(vic, variable.fullyQualifiedName());
            return progressAdd || progressSet;
        }
        return progressSet;
    }

    private record LoopResult(boolean wroteValue, CausesOfDelay delays, boolean progress) {
    }

    private LoopResult setValueForVariablesInLoopDefinedOutsideAssignedInside(StatementAnalyserSharedState sharedState,
                                                                              Variable variable,
                                                                              VariableInfoContainer vic,
                                                                              VariableInfo vi,
                                                                              EvaluationResult.ChangeData changeData,
                                                                              GroupPropertyValues groupPropertyValues) {
        if (vic.variableNature() instanceof VariableNature.VariableDefinedOutsideLoop outside
                && outside.statementIndex().equals(index())) {
            // we're at the start of the loop for this variable
            if (sharedState.evaluationContext().getIteration() == 0) {
                // we cannot yet know whether the variable will be assigned in this loop, or not
                // write a delayed value
                CausesOfDelay causes = DelayFactory.createDelay(new VariableCause(variable, getLocation(),
                        CauseOfDelay.Cause.WAIT_FOR_ASSIGNMENT));
                return delayValueForInstanceInLoop(sharedState, variable, vic, vi, changeData, null
                        , groupPropertyValues, causes);
            }
            // is the variable assigned inside the loop, but not in -E ?
            if (vic.hasMerge()) {
                VariableInfo merge = vic.current();
                String latestAssignment = merge.getAssignmentIds().getLatestAssignment();
                if (latestAssignment != null && latestAssignment.startsWith(index())) {
                    return changeValueToInstanceInLoop(sharedState, variable, vic, changeData, null,
                            groupPropertyValues);
                }
                /* variable is modified somewhere in the loop, we cannot keep "new ..." and must switch to "instance type ..."
                 see e.g. VariableInLoop_3

                 IMPORTANT this part should not clash with the code that deals with the value AFTER the loop, which sits
                 in SASubBlocks.conditionManagerForFirstBlock.
                 FIXME Problem is that that one uses -E as the basis for the "previous value", messing up the value afterwards

                 (also interfering is the erasure of companion info during the evaluation of a modifying method (MethodCall))
                 See Loops_8
                */
                DV modified = merge.getProperty(CONTEXT_MODIFIED);
                if (!modified.valueIsFalse()) {
                    // we know there is no assignment, so the value properties can remain the same, if we have them
                    Properties valueProperties = vic.getPreviousOrInitial().valueProperties();
                    if (modified.isDelayed()) {
                        CausesOfDelay causes = DelayFactory.createDelay(new VariableCause(variable, getLocation(),
                                CauseOfDelay.Cause.WAIT_FOR_MODIFICATION));
                        return delayValueForInstanceInLoop(sharedState, variable, vic, vi, changeData, valueProperties,
                                groupPropertyValues, causes);
                    }
                    // modified!
                    return changeValueToInstanceInLoop(sharedState, variable, vic, changeData, valueProperties,
                            groupPropertyValues);
                }
            }
            if (vic.hasEvaluation() && vi.isDelayed() && !vic.isInitial()) {
                vic.copy();
            }
        }
        return new LoopResult(false, CausesOfDelay.EMPTY, false);
    }

    private LoopResult delayValueForInstanceInLoop(StatementAnalyserSharedState sharedState,
                                                   Variable variable,
                                                   VariableInfoContainer vic,
                                                   VariableInfo vi,
                                                   EvaluationResult.ChangeData changeData,
                                                   Properties valuePropertiesIn,
                                                   GroupPropertyValues groupPropertyValues,
                                                   CausesOfDelay causes) {
        Expression delayedValue = DelayedVariableExpression.forLocalVariableInLoop(variable, causes);
        Properties delayedVPs;
        if (valuePropertiesIn != null) {
            delayedVPs = valuePropertiesIn;
        } else {
            delayedVPs = sharedState.evaluationContext().getValueProperties(delayedValue);
        }
        vic.ensureEvaluation(getLocation(), vi.getAssignmentIds(), vi.getReadId(), vi.getReadAtStatementTimes());
        boolean progress = vic.setValue(delayedValue, null, delayedVPs, EVALUATION);
        Map<Property, DV> previous = vic.getPreviousOrInitial().getProperties();
        SAHelper.mergePreviousAndChangeOnlyGroupPropertyValues(sharedState.evaluationContext(), variable,
                previous, changeData == null ? null : changeData.properties(), groupPropertyValues);
        return new LoopResult(true, causes, progress);
    }

    private LoopResult changeValueToInstanceInLoop(StatementAnalyserSharedState sharedState,
                                                   Variable variable,
                                                   VariableInfoContainer vic,
                                                   EvaluationResult.ChangeData changeData,
                                                   Properties valuePropertiesIn,
                                                   GroupPropertyValues groupPropertyValues) {
        Properties valueProperties;
        if (valuePropertiesIn != null) {
            valueProperties = valuePropertiesIn;
        } else {
            valueProperties = sharedState.evaluationContext().getAnalyserContext()
                    .defaultValueProperties(variable.parameterizedType());
        }
        CausesOfDelay causes = valueProperties.delays();
        Expression value;
        if (causes.isDelayed()) {
            value = DelayedVariableExpression.forLocalVariableInLoop(variable, causes);
        } else {
            Identifier identifier = statement().getIdentifier();
            value = Instance.forVariableInLoopDefinedOutside(identifier, variable.parameterizedType(), valueProperties);
        }
        boolean progress = vic.setValue(value, null, valueProperties, EVALUATION);
        Map<Property, DV> previous = vic.getPreviousOrInitial().getProperties();
        SAHelper.mergePreviousAndChangeOnlyGroupPropertyValues(sharedState.evaluationContext(), variable,
                previous, changeData == null ? null : changeData.properties(), groupPropertyValues);
        return new LoopResult(true, causes, progress);
    }

    private Expression delayAssignmentValue(StatementAnalyserSharedState sharedState,
                                            Expression valueToWrite,
                                            CausesOfDelay valuePropertiesIsDelayed) {
        boolean valueToWriteIsDelayed = valueToWrite.isDelayed();
        CausesOfDelay causes;
        if (sharedState.evaluationContext().delayStatementBecauseOfECI() && !valuePropertiesIsDelayed.isDelayed()) {
            causes = DelayFactory.createDelay(new SimpleCause(getLocation(), CauseOfDelay.Cause.ECI_HELPER));
        } else {
            causes = valuePropertiesIsDelayed;
        }
        if (!valueToWriteIsDelayed && causes.isDelayed()) {
            return valueToWrite.createDelayedValue(valueToWrite.getIdentifier(),
                    EvaluationResult.from(sharedState.evaluationContext()), causes);
        }
        return valueToWrite;
    }

    private boolean detectBreakDelayInAssignment(Variable variable,
                                                 VariableInfoContainer vic,
                                                 EvaluationResult.ChangeData changeData,
                                                 Expression valueToWrite,
                                                 Expression valueToWritePossiblyDelayed,
                                                 Properties combined,
                                                 InspectionProvider inspectionProvider) {
        if (variable instanceof FieldReference target) {
            if (valueToWritePossiblyDelayed.isDelayed()) {
                if (valueToWrite.isInstanceOf(NullConstant.class)) {
                    /*
                    The null constant may have delayed value properties, but it is not useful to delay the whole evaluation
                    for that reason. We cannot simply keep "null" and delayed properties at the same time, so we wrap.
                    See E2Immutable_1 as the primary case; and FieldReference_3 as an example of why wrapping is needed.

                    DWE accepts delayed value properties only for the NullConstant
                    */
                    vic.createAndWriteDelayedWrappedExpressionForEval(Identifier.generate("dwe null constant"),
                            valueToWrite, combined, valueToWritePossiblyDelayed.causesOfDelay());
                    return true;
                }
                Set<CauseOfDelay> breaks = extractBreakInitCause(valueToWritePossiblyDelayed.causesOfDelay(), target);
                if (!breaks.isEmpty()) {
                    /*
                    deal with 2 situations: we have value properties in combined, or we don't, but we know that we're dealing
                    with a variable of primitive type, so we know what the value properties should be.
                    The reason for this second case is the BaseExpression.getPropertyForPrimitiveResults method, which
                    forces delays on value properties even if we know their values.
                     */
                    Properties combinedOrPrimitive = valueToWrite.returnType().isPrimitiveExcludingVoid() || valueToWrite instanceof StringConcat
                            ? EvaluationContext.PRIMITIVE_VALUE_PROPERTIES : combined;
                    if (!combinedOrPrimitive.delays().isDelayed()) {
                        // we don't have a value, but can make a perfectly good "instance", with all the right value properties

                        // replace the DVE with a DelayedWrappedExpression referring to self
                        Expression instance = Instance.forSelfAssignmentBreakInit(Identifier.generate("dwe break self assignment"),
                                target.parameterizedType, combinedOrPrimitive);
                        LOGGER.debug("Return wrapped expression to break value delay on {} in {}", target, index());
                        CausesOfDelay causes = removeBreakButKeepDelayed(valueToWritePossiblyDelayed, breaks);
                        vic.createAndWriteDelayedWrappedExpressionForEval(Identifier.generate("dwe break init delay"),
                                instance, combinedOrPrimitive, causes);
                        return true;
                    }
                }
            } else {
                if (changeData.stateIsDelayed().isDelayed()) {
                    // we have a perfectly good value, but the current state is delayed, so eventually we'll have to revisit
                    Set<CauseOfDelay> breaks = extractBreakInitCause(changeData.stateIsDelayed(), target);
                    if (!breaks.isEmpty()) {
                        vic.createAndWriteDelayedWrappedExpressionForEval(Identifier.generate("dwe break delayed state"),
                                valueToWritePossiblyDelayed,
                                combined, changeData.stateIsDelayed());
                        LOGGER.debug("Return wrapped expression to break state delay on {} in {}", target, index());
                        return true;
                    }
                }
            }
        }
        // move DWE to the front, if it is hidden somewhere deeper inside the expression
        Expression e = DelayedWrappedExpression.moveDelayedWrappedExpressionToFront(inspectionProvider, valueToWritePossiblyDelayed);
        if (e instanceof DelayedWrappedExpression) {
            vic.setValue(e, null, combined, EVALUATION);
            return true;
        }
        return false;
    }

    private CausesOfDelay removeBreakButKeepDelayed(Expression valueToWritePossiblyDelayed, Set<CauseOfDelay> breaks) {
        CausesOfDelay causesBreakRemoved = valueToWritePossiblyDelayed.causesOfDelay().removeAll(breaks);
        CausesOfDelay causes;
        if (causesBreakRemoved.isDone()) {
            //just making sure that we are delayed
            causes = DelayFactory.createDelay(getLocation(), CauseOfDelay.Cause.SA_APPLY_DUMMY_DELAY);
        } else {
            causes = causesBreakRemoved;
        }
        return causes;
    }

    private Set<CauseOfDelay> extractBreakInitCause(CausesOfDelay valueToWritePossiblyDelayed, FieldReference target) {
        return valueToWritePossiblyDelayed.causesStream()
                .filter(c -> c instanceof VariableCause vc
                        && vc.cause() == CauseOfDelay.Cause.BREAK_INIT_DELAY
                        && vc.variable() instanceof FieldReference fr && fr.fieldInfo == target.fieldInfo)
                .collect(Collectors.toUnmodifiableSet());
    }

    private EvaluationResult variablesReadOrModifiedInSubAnalysers(EvaluationResult evaluationResultIn,
                                                                   EvaluationResult context) {
        if (statementAnalysis.havePropertiesFromSubAnalysers()) {
            EvaluationResult.Builder builder = new EvaluationResult.Builder(context);
            builder.compose(evaluationResultIn);
            statementAnalysis.propertiesFromSubAnalysers().forEach(e -> {
                Variable variable = e.getKey();
                Properties properties = e.getValue();
                properties.stream().forEach(ee -> {
                    Property property = ee.getKey();
                    DV value = ee.getValue();
                    if (property == READ) {
                        if (value == DV.TRUE_DV) builder.markRead(variable);
                    } else {
                        builder.setProperty(variable, property, value);
                    }
                });
            });
            return builder.build();
        }

        return evaluationResultIn;
    }

    private boolean variableUnknown(Variable variable) {
        if (!statementAnalysis.variableIsSet(variable.fullyQualifiedName())) return true;
        IsVariableExpression ive;
        if (variable instanceof FieldReference fr && ((ive = fr.scope.asInstanceOf(IsVariableExpression.class)) != null)) {
            return variableUnknown(ive.variable());
        }
        return false;
    }

    /**
     * Compute the context properties, linked variables, preconditions. Return delays.
     */
    private ApplyStatusAndEnnStatus contextProperties(StatementAnalyserSharedState sharedState,
                                                      EvaluationResult evaluationResult,
                                                      ProgressAndDelay delayIn,
                                                      AnalyserContext analyserContext,
                                                      GroupPropertyValues groupPropertyValues) {
        // the second one is across clusters of variables
        groupPropertyValues.addToMap(statementAnalysis);

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

        // we should be able to cache the statically assigned variables, they cannot change anymore after iteration 0
        ProgressAndDelay linkDelays = computeLinkedVariablesCm.writeClusteredLinkedVariables(computeLinkedVariables);

        // 1
        ProgressAndDelay cnnStatus = computeLinkedVariables.write(CONTEXT_NOT_NULL,
                groupPropertyValues.getMap(CONTEXT_NOT_NULL));

        // 2
        ProgressAndDelay ennStatus = computeLinkedVariables.write(EXTERNAL_NOT_NULL,
                groupPropertyValues.getMap(EXTERNAL_NOT_NULL));
        boolean travelProgress;
        if (sharedState.evaluationContext().getIteration() == 0) {
            boolean cnnTravelsToFields = analyserContext.getConfiguration().analyserConfiguration()
                    .computeContextPropertiesOverAllMethods();
            travelProgress = computeLinkedVariables.writeCnnTravelsToFields(
                    sharedState.evaluationContext().getAnalyserContext(),
                    cnnTravelsToFields);
        } else {
            travelProgress = false;
        }
        statementAnalysis.potentiallyRaiseErrorsOnNotNullInContext(sharedState.evaluationContext().getAnalyserContext(),
                evaluationResult.changeData());

        // the following statement is necessary to keep this statement from disappearing if it still has to process
        // EXT_NN (and a similar statement below for EXT_IMM); we go through ALL variables because this may be a statement
        // such as "throw new Exception()", see Basics_17
        CausesOfDelay anyEnn = statementAnalysis.variableStream()
                .map(vi -> {
                    CausesOfDelay causes = vi.getProperty(EXTERNAL_NOT_NULL).causesOfDelay();
                    if (causes.isDelayed()) {
                        // decorate, so that we have an idea which variable is the cause of the problem
                        return causes.merge(DelayFactory.createDelay(new VariableCause(vi.variable(), getLocation(),
                                CauseOfDelay.Cause.EXTERNAL_NOT_NULL)));
                    }
                    return CausesOfDelay.EMPTY;
                })
                .reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge);

        // 3
        ProgressAndDelay extImmStatus = computeLinkedVariables.write(EXTERNAL_IMMUTABLE,
                groupPropertyValues.getMap(EXTERNAL_IMMUTABLE));

        CausesOfDelay anyExtImm = statementAnalysis.variableStream()
                .map(vi -> {
                    CausesOfDelay causes = vi.getProperty(EXTERNAL_IMMUTABLE).causesOfDelay();
                    if (causes.isDelayed()) {
                        return causes.merge(DelayFactory.createDelay(new VariableCause(vi.variable(), getLocation(),
                                CauseOfDelay.Cause.EXT_IMM)));
                    }
                    return CausesOfDelay.EMPTY;
                })
                .reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge);

        // 4
        ProgressAndDelay cImmStatus = computeLinkedVariables.write(CONTEXT_IMMUTABLE,
                groupPropertyValues.getMap(CONTEXT_IMMUTABLE));

        // 5
        ProgressAndDelay extContStatus = computeLinkedVariables.write(EXTERNAL_CONTAINER,
                groupPropertyValues.getMap(EXTERNAL_CONTAINER));
        CausesOfDelay anyExtCont = statementAnalysis.variableStream()
                .map(vi -> {
                    CausesOfDelay causes = vi.getProperty(EXTERNAL_CONTAINER).causesOfDelay();
                    if (causes.isDelayed()) {
                        return causes.merge(DelayFactory.createDelay(new VariableCause(vi.variable(), getLocation(),
                                CauseOfDelay.Cause.EXT_CONTAINER)));
                    }
                    return CausesOfDelay.EMPTY;
                })
                .reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge);

        // 6
        ProgressAndDelay cContStatus = computeLinkedVariables.write(CONTEXT_CONTAINER, groupPropertyValues.getMap(CONTEXT_CONTAINER));

        // 7
        ProgressAndDelay cmStatus = computeLinkedVariablesCm.write(CONTEXT_MODIFIED, groupPropertyValues.getMap(CONTEXT_MODIFIED));

        // 8
        ProgressAndDelay extIgnMod = computeLinkedVariables.write(EXTERNAL_IGNORE_MODIFICATIONS,
                groupPropertyValues.getMap(EXTERNAL_IGNORE_MODIFICATIONS));
        CausesOfDelay anyExtIgnMod = statementAnalysis.variableStream()
                .map(vi -> {
                    CausesOfDelay causes = vi.getProperty(EXTERNAL_IGNORE_MODIFICATIONS).causesOfDelay();
                    if (causes.isDelayed()) {
                        return causes.merge(DelayFactory.createDelay(new VariableCause(vi.variable(), getLocation(),
                                CauseOfDelay.Cause.EXT_IGNORE_MODS)));
                    }
                    return CausesOfDelay.EMPTY;
                })
                .reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge);

        // odds and ends
        /*
         this copying used to be restricted to absence of delays -- however, since the introduction of the re-evaluation
         of variables in VariableExpression (forced by InstanceOf_11, variable d) we observe that the interaction between
         the condition and the variable obscures potential null pointer exceptions when in "done" mode. This needs
         revisiting at some point.
         */
        evaluationResult.messages().getMessageStream().filter(this::acceptMessage).forEach(statementAnalysis::ensure);

        // not checking on DONE anymore because any delay will also have crept into the precondition itself??
        Precondition precondition = evaluationResult.precondition();
        boolean preconditionProgress = statementAnalysis.applyPrecondition(precondition, sharedState.evaluationContext(),
                sharedState.localConditionManager());


        ProgressAndDelay status = delayIn.combine(linkDelays).combine(cContStatus).combine(cnnStatus)
                .combine(cImmStatus).combine(cnnStatus).combine(cmStatus)
                .addProgress(travelProgress || preconditionProgress);

        CausesOfDelay anyDelays = anyEnn.merge(anyExtImm).merge(anyExtCont).merge(anyExtIgnMod);
        ProgressAndDelay externalDelay = ennStatus.combine(extImmStatus).combine(extContStatus).combine(extIgnMod);
        ProgressAndDelay externalStatus = externalDelay.merge(anyDelays);

        return new ApplyStatusAndEnnStatus(status, externalStatus);
    }

    // filter out inline conditional on throws statements, when the state becomes "false"
    // see e.g. SwitchExpression_4, at the "throws" statement at the end of the method
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
            EvaluationResult context) {
        if (vi1.isAssigned() && !vi1.isRead() && changeData.markAssignment() &&
                changeData.readAtStatementTime().isEmpty() && !(vi1.variable() instanceof ReturnVariable)) {
            String index = vi1.getAssignmentIds().getLatestAssignmentIndex();
            StatementAnalysis sa = methodAnalyser.findStatementAnalyser(index).getStatementAnalysis();
            if (sa.stateData().conditionManagerForNextStatementStatus().isDelayed()) {
                return false; // we'll be back
            }
            ConditionManager atAssignment = sa.stateData().getConditionManagerForNextStatement();
            Expression myAbsoluteState = conditionManager.absoluteState(context);
            Expression initialAbsoluteState = atAssignment.absoluteState(context);
            if (!initialAbsoluteState.equals(myAbsoluteState)) return false;
            // now check if we're in loop block, and there was an assignment outside
            // this loop block will not have an effect on the absolute state (See Loops_2, Loops_13)
            String fqn = vi1.variable().fullyQualifiedName();
            if (!sa.variableIsSet(fqn)) {
                // does not exist in previous statement, so no point...
                return false;
            }
            VariableInfoContainer initialVic = sa.getVariable(fqn);
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
            CausesOfDelay marker = DelayFactory.createDelay(new VariableCause(variable, getLocation(), CauseOfDelay.Cause.STATE_DELAYED));
            if (stateIsDelayedInChangeData.containsCauseOfDelay(CauseOfDelay.Cause.STATE_DELAYED, c ->
                    c instanceof VariableCause vc && vc.variable().equals(variable) && c.location().equals(vc.location()))) {
                LOGGER.debug("Breaking delay " + marker);
            } else {
                CausesOfDelay merged = stateIsDelayedInChangeData.merge(marker);
                // if the delays are caused by a field in break_init_delay, we will return the value rather than a delay
                if (merged.containsCauseOfDelay(CauseOfDelay.Cause.BREAK_INIT_DELAY)) {
                    return value;
                }
                return DelayedExpression.forState(Identifier.state(index()), variable.parameterizedType(), value, merged);
            }
        }
        if (vic.variableNature() instanceof VariableNature.VariableDefinedOutsideLoop) {
            // variable defined outside loop, now in loop, not delayed

            ConditionManager localConditionManager = sharedState.localConditionManager();

            if (!localConditionManager.state().isBoolValueTrue()) {
                Expression state = localConditionManager.state();

                ForwardEvaluationInfo fwd = new ForwardEvaluationInfo.Builder().setAssignmentTarget(variable).build();

                // do not take vi1 itself, but "the" local copy of the variable
                EvaluationContext evaluationContext = sharedState.evaluationContext();
                Expression valueOfVariablePreAssignment = evaluationContext.currentValue(variable, null, null, fwd); // FIXME

                Identifier generate = Identifier.generate("inline condition var def outside loop");
                InlineConditional inlineConditional = new InlineConditional(generate,
                        evaluationContext.getAnalyserContext(), state, value, valueOfVariablePreAssignment);
                return inlineConditional.optimise(EvaluationResult.from(evaluationContext.dropConditionManager()), variable);
            }
        }
        return value;
    }


}
