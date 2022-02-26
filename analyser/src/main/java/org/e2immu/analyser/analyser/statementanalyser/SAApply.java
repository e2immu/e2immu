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
import org.e2immu.analyser.analyser.delay.SimpleCause;
import org.e2immu.analyser.analyser.delay.SimpleSet;
import org.e2immu.analyser.analyser.delay.VariableCause;
import org.e2immu.analyser.analyser.nonanalyserimpl.VariableInfoImpl;
import org.e2immu.analyser.analysis.StatementAnalysis;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
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
import java.util.stream.Stream;

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
        CausesOfDelay delay = evaluationResultIn.causesOfDelay();
        EvaluationResult evaluationResult1 = variablesReadOrModifiedInSubAnalysers(evaluationResultIn,
                sharedState.context());

        // *** this is part 2 of a cooperation to move the value of an equality in the state to the actual value
        // part 1 is in the constructor of SAEvaluationContext
        // the data is stored in the state data of statement analysis
        EvaluationResult.Builder builder = new EvaluationResult.Builder(sharedState.context());
        statementAnalysis.stateData().equalityAccordingToStateStream().forEach(e -> {
            EvaluationResult.ChangeData cd = evaluationResult1.changeData().get(e.getKey());
            if (cd != null && cd.isMarkedRead()) {
                LinkedVariables lv = e.getValue().linkedVariables(EvaluationResult.from(sharedState.evaluationContext()));
                builder.assignment(e.getKey(), e.getValue(), lv);
            }
        });
        EvaluationResult evaluationResult = builder.compose(evaluationResult1).build();
        // ***

        AnalyserContext analyserContext = evaluationResult.evaluationContext().getAnalyserContext();
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
        Map<Variable, VariableInfoContainer> localVariablesNotVisited = statementAnalysis.rawVariableStream()
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
        Optional<VariableCause> optBreakInitDelay = evaluationResult.causesOfDelay().causesStream()
                .filter(c -> c instanceof VariableCause vc
                        && vc.cause() == CauseOfDelay.Cause.BREAK_INIT_DELAY
                        && vc.location().equals(initialLocation))
                .map(c -> (VariableCause) c).findFirst();

        for (Map.Entry<Variable, EvaluationResult.ChangeData> entry : sortedEntries) {
            Variable variable = entry.getKey();
            existingVariablesNotVisited.remove(variable);
            localVariablesNotVisited.remove(variable);
            EvaluationResult.ChangeData changeData = entry.getValue();

            // we're now guaranteed to find the variable
            VariableInfoContainer vic = statementAnalysis.getVariable(variable.fullyQualifiedName());
            VariableInfo vi = vic.best(EVALUATION);
            VariableInfo vi1 = vic.getPreviousOrInitial();
            assert vi != vi1 : "There should already be a different EVALUATION object";

            if (changeData.markAssignment()) {
                if (conditionsForOverwritingPreviousAssignment(myMethodAnalyser, vi1, vic, changeData,
                        sharedState.localConditionManager(), sharedState.context())) {
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


                Expression valueToWritePossiblyDelayed = delayAssignmentValue(sharedState, valueToWrite, valueProperties.delays());

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

                Expression possiblyIntroduceDVE = detectBreakDelayInAssignment(variable, vi, changeData, valueToWrite, valueToWritePossiblyDelayed, combined);
                if (possiblyIntroduceDVE instanceof DelayedWrappedExpression) {
                    // trying without setting properties -- too dangerous to set value properties
                    // however, without IMMUTABLE there is little we can do, so we offer a temporary value for the field analyser
                    // (this hack is needed for Lazy, and speeds up, among many others, Basics 14, 18, 21)
                    Properties map = Properties.of(Map.of(IMMUTABLE_BREAK, combined.get(IMMUTABLE)));
                    vic.setValue(possiblyIntroduceDVE, LinkedVariables.EMPTY, map, false);
                } else {
                    // the field analyser con spot DelayedWrappedExpressions but cannot compute its value properties, as it does not have the same
                    // evaluation context
                    vic.setValue(valueToWritePossiblyDelayed, LinkedVariables.EMPTY, combined, false);
                }
                if (vic.variableNature() instanceof VariableNature.VariableDefinedOutsideLoop) {
                    statementAnalysis.addToAssignmentsInLoop(vic, variable.fullyQualifiedName());
                }
            } else {
                if (changeData.value() != null && (changeData.value().isDone() || !(vi1.getValue() instanceof DelayedWrappedExpression))) {
                    // a modifying method caused an updated instance value. IMPORTANT: the value properties do not change.
                    //assert changeData.value().isDone();
                    // we cannot let DWEs be changed into other delays... they must be passed on (e.g. StaticSideEffects_1)

                    Map<Property, DV> merged = SAHelper.mergePreviousAndChange(sharedState.evaluationContext(),
                            variable, vi1.getProperties(),
                            changeData.properties(), groupPropertyValues, true);

                    LinkedVariables removed = vi1.getLinkedVariables()
                            .remove(changeData.toRemoveFromLinkedVariables().variables().keySet());
                    vic.setValue(changeData.value(), removed, merged, false);
                } else {
                    LoopResult loopResult = setValueForVariablesInLoopDefinedOutsideAssignedInside(sharedState, variable, vic, vi);
                    delay = delay.merge(loopResult.delays);
                    if (!loopResult.wroteValue) {
                        if (variable instanceof This
                                || !evaluationResult.causesOfDelay().isDelayed()
                                || optBreakInitDelay.isPresent()
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
                            if (optBreakInitDelay.isPresent() && vi1.isDelayed()) {
                                // if we break a delay, but the value that we write is still delayed, we propagate the delay (see Basics_7_1)
                                LOGGER.debug("Propagate break init delay in {} in {}", variable, index());
                                value = vi1.getValue().mergeDelays(new SimpleSet(optBreakInitDelay.get()));
                            } else {
                                value = vi1.getValue();
                            }
                            vic.setValue(value, vi1.getLinkedVariables(), merged, false);
                        } else {
                            // delayed situation; do not copy the value properties UNLESS there is a break delay
                            Map<Property, DV> merged = SAHelper.mergePreviousAndChange(
                                    sharedState.evaluationContext(),
                                    variable, vi1.getProperties(),
                                    changeData.properties(), groupPropertyValues, false);
                            merged.forEach((k, v) -> vic.setProperty(k, v, EVALUATION));
                            // copy the causes of the delay in the value, so that the SAEvaluationContext.breakDelay can notice
                            vic.setDelayedValue(evaluationResult.causesOfDelay(), EVALUATION);
                        }
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

        for (Map.Entry<Variable, VariableInfoContainer> e : localVariablesNotVisited.entrySet()) {
            LoopResult loopResult = setValueForVariablesInLoopDefinedOutsideAssignedInside(sharedState, e.getKey(),
                    e.getValue(), e.getValue().best(EVALUATION));
            delay = delay.merge(loopResult.delays);
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

    private record LoopResult(boolean wroteValue, CausesOfDelay delays) {
    }

    private LoopResult setValueForVariablesInLoopDefinedOutsideAssignedInside(StatementAnalyserSharedState sharedState,
                                                                              Variable variable,
                                                                              VariableInfoContainer vic,
                                                                              VariableInfo vi) {
        if (vic.variableNature() instanceof VariableNature.VariableDefinedOutsideLoop outside
                && outside.statementIndex().equals(index())) {
            // we're at the start of the loop for this variable
            if (sharedState.evaluationContext().getIteration() == 0) {
                // we cannot yet know whether the variable will be assigned in this loop, or not
                // write a delayed value
                CausesOfDelay causes = new SimpleSet(new VariableCause(variable, getLocation(), CauseOfDelay.Cause.WAIT_FOR_ASSIGNMENT));
                Expression delayedValue = DelayedVariableExpression.forLocalVariableInLoop(variable, causes);
                Properties delayedVPs = sharedState.evaluationContext().getValueProperties(delayedValue);
                vic.ensureEvaluation(getLocation(), vi.getAssignmentIds(), vi.getReadId(), vi.getReadAtStatementTimes());
                vic.setValue(delayedValue, LinkedVariables.delayedEmpty(causes), delayedVPs, false);
                return new LoopResult(true, causes);
            }
            // is the variable assigned inside the loop, but not in -E ?
            if (vic.hasMerge()) {
                VariableInfo merge = vic.current();
                String latestAssignment = merge.getAssignmentIds().getLatestAssignment();
                if (latestAssignment != null && latestAssignment.startsWith(index())) {
                    Properties properties = sharedState.evaluationContext().getAnalyserContext().defaultValueProperties(variable.parameterizedType());
                    CausesOfDelay causes = properties.delays();
                    Expression value;
                    if (causes.isDelayed()) {
                        value = DelayedVariableExpression.forLocalVariableInLoop(variable, causes);
                    } else {
                        Identifier identifier = statement().getIdentifier();
                        value = Instance.forVariableInLoopDefinedOutside(identifier, variable.parameterizedType(), properties);
                    }
                    vic.setValue(value, LinkedVariables.EMPTY, properties, false);
                    return new LoopResult(true, causes);
                }
            }
            if (vic.hasEvaluation() && vi.isDelayed()) {
                vic.copy();
            }
        }
        return new LoopResult(false, CausesOfDelay.EMPTY);
    }

    private Expression delayAssignmentValue(StatementAnalyserSharedState sharedState,
                                            Expression valueToWrite,
                                            CausesOfDelay valuePropertiesIsDelayed) {
        boolean valueToWriteIsDelayed = valueToWrite.isDelayed();
        CausesOfDelay causes;
        if (sharedState.evaluationContext().delayStatementBecauseOfECI() && !valuePropertiesIsDelayed.isDelayed()) {
            causes = new SimpleSet(new SimpleCause(getLocation(), CauseOfDelay.Cause.ECI_HELPER));
        } else {
            causes = valuePropertiesIsDelayed;
        }
        if (!valueToWriteIsDelayed && causes.isDelayed()) {
            return valueToWrite.createDelayedValue(EvaluationResult.from(sharedState.evaluationContext()), causes);
        }
        return valueToWrite;
    }

    private Expression detectBreakDelayInAssignment(Variable variable,
                                                    VariableInfo vi,
                                                    EvaluationResult.ChangeData changeData,
                                                    Expression valueToWrite,
                                                    Expression valueToWritePossiblyDelayed,
                                                    Properties combined) {
        if (variable instanceof FieldReference target) {
            if (valueToWritePossiblyDelayed.isDelayed()) {
                if (valueToWrite instanceof NullConstant) {
                    /*
                    The null constant may have delayed value properties, but it is not useful to delay the whole evaluation
                    for that reason. We cannot simply keep "null" and delayed properties at the same time, so we wrap.
                    See E2Immutable_1 as the primary case; and FieldReference_3 as an example of why wrapping is needed.
                    */
                    return new DelayedWrappedExpression(Identifier.generate("dwe null constant"), valueToWrite, vi, valueToWritePossiblyDelayed.causesOfDelay());
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
                        CausesOfDelay causes = valueToWritePossiblyDelayed.causesOfDelay().removeAll(breaks);
                        if (causes.isDone()) {
                            //just making sure that we are delayed
                            causes = new SimpleSet(getLocation(), CauseOfDelay.Cause.WAIT_FOR_ASSIGNMENT);
                        }
                        VariableInfo viCombinedOrPrimitive;
                        if (combinedOrPrimitive == EvaluationContext.PRIMITIVE_VALUE_PROPERTIES) {
                            viCombinedOrPrimitive = new VariableInfoImpl(getLocation(), vi.variable(), vi.getValue(), combinedOrPrimitive);
                        } else {
                            viCombinedOrPrimitive = vi;
                        }
                        return new DelayedWrappedExpression(Identifier.generate("dwe break init delay"), instance, viCombinedOrPrimitive, causes);
                    }
                }
            } else {
                if (changeData.stateIsDelayed().isDelayed()) {
                    // we have a perfectly good value, but the current state is delayed, so eventually we'll have to revisit
                    Set<CauseOfDelay> breaks = extractBreakInitCause(changeData.stateIsDelayed(), target);
                    if (!breaks.isEmpty()) {
                        // works in tandem with EvaluationResult.breakSelfReferenceDelay; also requires code at end of SASubBlocks.subBlocks
                        Expression res = new DelayedWrappedExpression(Identifier.generate("dwe break delayed state"), valueToWritePossiblyDelayed,
                                vi, changeData.stateIsDelayed());
                        assert res.isDelayed();
                        LOGGER.debug("Return wrapped expression to break state delay on {} in {}", target, index());
                        return res;
                    }
                }
            }
        }
        // move DWE to the front, if it is hidden somewhere deeper inside the expression
        return DelayedWrappedExpression.moveDelayedWrappedExpressionToFront(valueToWritePossiblyDelayed);
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
        List<Variable> readBySubAnalysers = statementAnalysis.variablesReadBySubAnalysers();
        if (!readBySubAnalysers.isEmpty() || statementAnalysis.haveVariablesModifiedBySubAnalysers()) {
            EvaluationResult.Builder builder = new EvaluationResult.Builder(context);
            builder.compose(evaluationResultIn);
            for (Variable variable : readBySubAnalysers) {
                builder.markRead(variable);
            }
            Stream<Map.Entry<Variable, DV>> modifiedStream = statementAnalysis.variablesModifiedBySubAnalysers();
            modifiedStream.forEach(e -> builder.setProperty(e.getKey(), CONTEXT_MODIFIED, e.getValue()));

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
                                                      CausesOfDelay delayIn,
                                                      AnalyserContext analyserContext,
                                                      GroupPropertyValues groupPropertyValues) {
        // the second one is across clusters of variables
        groupPropertyValues.addToMap(statementAnalysis, analyserContext, EVALUATION);

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
        CausesOfDelay delay = delayIn.merge(cnnStatus);

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

        // 8
        CausesOfDelay extIgnMod = computeLinkedVariables.write(EXTERNAL_IGNORE_MODIFICATIONS,
                groupPropertyValues.getMap(EXTERNAL_IGNORE_MODIFICATIONS));
        CausesOfDelay anyExtIgnMod = statementAnalysis.variableStream()
                .map(vi -> {
                    CausesOfDelay causes = vi.getProperty(EXTERNAL_IGNORE_MODIFICATIONS).causesOfDelay();
                    if (causes.isDelayed()) {
                        return causes.merge(new SimpleSet(new VariableCause(vi.variable(), getLocation(),
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
        CausesOfDelay applyPrecondition = statementAnalysis.applyPrecondition(precondition, sharedState.evaluationContext(),
                sharedState.localConditionManager());
        delay = delay.merge(applyPrecondition);
        CausesOfDelay externalDelay = ennStatus.merge(extImmStatus).merge(anyEnn)
                .merge(anyExtImm).merge(extContStatus).merge(anyExtCont).merge(extIgnMod).merge(anyExtIgnMod);

        return new ApplyStatusAndEnnStatus(delay, externalDelay);
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
            CausesOfDelay marker = new SimpleSet(new VariableCause(variable, getLocation(), CauseOfDelay.Cause.STATE_DELAYED));
            if (stateIsDelayedInChangeData.causesStream().anyMatch(c -> c.cause() == CauseOfDelay.Cause.STATE_DELAYED &&
                    c instanceof VariableCause vc && vc.variable().equals(variable) && c.location().equals(vc.location()))) {
                LOGGER.debug("Breaking delay " + marker);
            } else {
                CausesOfDelay merged = stateIsDelayedInChangeData.merge(marker);
                // if the delays are caused by a field in break_init_delay, we will return the value rather than a delay
                if (merged.causesStream().anyMatch(cause -> cause.cause().equals(CauseOfDelay.Cause.BREAK_INIT_DELAY))) {
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

                ForwardEvaluationInfo fwd = new ForwardEvaluationInfo(Map.of(), false, true, variable, true);
                // do not take vi1 itself, but "the" local copy of the variable
                EvaluationContext evaluationContext = sharedState.evaluationContext();
                Expression valueOfVariablePreAssignment = evaluationContext.currentValue(variable, fwd);

                Identifier generate = Identifier.generate("inline condition var def outside loop");
                InlineConditional inlineConditional = new InlineConditional(generate,
                        evaluationContext.getAnalyserContext(), state, value, valueOfVariablePreAssignment);
                return inlineConditional.optimise(EvaluationResult.from(evaluationContext.dropConditionManager()));
            }
        }
        return value;
    }


}
