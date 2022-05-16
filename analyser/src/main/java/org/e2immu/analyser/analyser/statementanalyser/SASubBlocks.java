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
import org.e2immu.analyser.analyser.delay.SimpleCause;
import org.e2immu.analyser.analyser.util.AnalyserResult;
import org.e2immu.analyser.analysis.FlowData;
import org.e2immu.analyser.analysis.StatementAnalysis;
import org.e2immu.analyser.analysis.impl.StatementAnalysisImpl;
import org.e2immu.analyser.analysis.range.Range;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.statement.*;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Primitives;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.AnalysisStatus.DONE;

record SASubBlocks(StatementAnalysis statementAnalysis, StatementAnalyser statementAnalyser) {
    private static final Logger LOGGER = LoggerFactory.getLogger(SASubBlocks.class);

    private String index() {
        return statementAnalysis.index();
    }

    private Statement statement() {
        return statementAnalysis.statement();
    }

    private record ConditionManagerAndStatus(ConditionManager conditionManager, AnalysisStatus analysisStatus) {
    }

    AnalysisStatus subBlocks(StatementAnalyserSharedState sharedState) {
        List<Optional<StatementAnalyser>> startOfBlocks = statementAnalyser.navigationData().blocks.get();
        AnalysisStatus statusFromLocalCm = AnalysisStatus.of(sharedState.localConditionManager().causesOfDelay());

        if (!startOfBlocks.isEmpty()) {
            AnalysisStatus analysisStatus = haveSubBlocks(sharedState, startOfBlocks).combine(statusFromLocalCm);
            ensureEmptyPrecondition();
            return analysisStatus;
        }

        // IMPROVE this should also be implemented on the haveSubBlocks side
        Set<Variable> variablesAssigned = statementAnalysis.variableStream().filter(vi -> vi.isAssignedAt(index()))
                .map(VariableInfo::variable).collect(Collectors.toUnmodifiableSet());
        ConditionManager cm = sharedState.localConditionManager().removeFromState(sharedState.context(),
                variablesAssigned);

        AnalysisStatus statusFromStatement;
        ConditionManager cmFromStatement;
        if (statement() instanceof AssertStatement) {
            ConditionManagerAndStatus conditionManagerAndStatus = doAssertStatement(sharedState, cm);
            cmFromStatement = conditionManagerAndStatus.conditionManager;
            statusFromStatement = statusFromLocalCm.combine(conditionManagerAndStatus.analysisStatus);
        } else if (statement() instanceof ThrowStatement) {
            AnalysisStatus statusFromThrows = doThrowStatement(sharedState, cm);
            cmFromStatement = cm; // the change in condition manager comes from the surrounding block
            statusFromStatement = statusFromLocalCm.combine(statusFromThrows);
        } else {
            // FIXME this should also be implemented on the haveSubBlocks side
            //Set<FieldInfo> fieldsWithBreakDelay = statementAnalysis.fieldsWithBreakInitDelay();
            //if (!fieldsWithBreakDelay.isEmpty()) {
            //    cm = cm.removeDelaysOn(statementAnalysis.primitives(), fieldsWithBreakDelay);
            //}
            cmFromStatement = cm;
            statusFromStatement = statusFromLocalCm.combine(AnalysisStatus.of(ensureEmptyPrecondition()));
        }
        statementAnalysis.stateData().setLocalConditionManagerForNextStatement(cmFromStatement);

        if (statementAnalysis.flowData().timeAfterSubBlocksNotYetSet()) {
            statementAnalysis.flowData().copyTimeAfterSubBlocksFromTimeAfterExecution();
        }
        return statusFromStatement;
    }

    private CausesOfDelay ensureEmptyPrecondition() {
        MethodInfo methodInfo = statementAnalysis.methodAnalysis().getMethodInfo();
        if (statementAnalysis.stateData().preconditionNoInformationYet(methodInfo)) {
            // it could have been set from the assert statement (subBlocks) or apply via a method call
            statementAnalysis.stateData().setPrecondition(Precondition.empty(statementAnalysis.primitives()));
            return CausesOfDelay.EMPTY;
        }
        return statementAnalysis.stateData().getPrecondition().causesOfDelay();
    }

    private AnalysisStatus doThrowStatement(StatementAnalyserSharedState sharedState, ConditionManager cm) {
        DV escapeAlwaysExecuted = statementAnalysis.isEscapeAlwaysExecutedInCurrentBlock();

        CausesOfDelay delays = escapeAlwaysExecuted.causesOfDelay()
                .merge(statementAnalysis.stateData().conditionManagerForNextStatementStatus());
        Expression precondition = cm.precondition(EvaluationResult.from(sharedState.evaluationContext()));
        // the identifier of the "throws" expression, in case we have a delayed precondition
        Identifier identifier = statement().getStructure().expression().getIdentifier();
        Expression translated = sharedState.evaluationContext().acceptAndTranslatePrecondition(identifier, precondition);
        if (translated != null) {
            LOGGER.debug("Escape with precondition {}", translated);
            Precondition pc = new Precondition(translated, List.of(new Precondition.EscapeCause()));
            statementAnalysis.stateData().setPrecondition(pc);
            CausesOfDelay preconditionIsDelayed = precondition.causesOfDelay().merge(delays);
            return AnalysisStatus.of(preconditionIsDelayed);
        }
        if (escapeAlwaysExecuted.isDelayed()) {
            return AnalysisStatus.of(delays.causesOfDelay());
        }
        // else: not stored in pc, so ensure it is unconditionally empty
        statementAnalysis.stateData().setPrecondition(Precondition.empty(sharedState.context().getPrimitives()));
        return DONE;
    }

    private ConditionManagerAndStatus doAssertStatement(StatementAnalyserSharedState sharedState, ConditionManager cm) {
        Expression assertion = statementAnalysis.stateData().valueOfExpression.get();
        Expression pcFromMethod = statementAnalysis.stateData().getPreconditionFromMethodCalls().expression();
        Expression combined = And.and(sharedState.context(), assertion, pcFromMethod);

        boolean expressionIsDelayed = statementAnalysis.stateData().valueOfExpression.isVariable();
        // NOTE that it is possible that assertion is not delayed, but the valueOfExpression is delayed
        // because of other delays in the apply method (see setValueOfExpression call in evaluationOfMainExpression)

        Precondition pc;
        if (SAHelper.moveConditionToParameter(sharedState.context(), combined) == null) {
            // in IfStatement_10, we have an "assert" condition that cannot simply be moved to the precondition, because
            // it turns out the condition will always be false. We really need the local condition manager for next
            // statement to be delayed until we know the precondition can be accepted.
            // the identifier of the "assert" expression, in case we have a delayed precondition
            Identifier identifier = statement().getStructure().expression().getIdentifier();
            Expression translated = Objects.requireNonNullElse(
                    sharedState.evaluationContext().acceptAndTranslatePrecondition(identifier, combined),
                    new BooleanConstant(statementAnalysis.primitives(), true));

            List<Precondition.PreconditionCause> preconditionCauses = Stream.concat(
                    translated.isBoolValueTrue() ? Stream.of() : Stream.of(new Precondition.EscapeCause()),
                    statementAnalysis.stateData().getPreconditionFromMethodCalls().causes().stream()).toList();

            pc = new Precondition(translated, preconditionCauses);
        } else {
            // the null/not null of parameters has been handled during the main evaluation
            pc = Precondition.empty(statementAnalysis.primitives());
        }
        statementAnalysis.stateData().setPrecondition(pc);

        AnalysisStatus analysisStatus;
        if (expressionIsDelayed || pc.isDelayed()) {
            CausesOfDelay merge = statementAnalysis.stateData().valueOfExpressionIsDelayed().merge(pc.causesOfDelay());
            analysisStatus = AnalysisStatus.of(merge);
        } else {
            analysisStatus = DONE;
        }
        return new ConditionManagerAndStatus(cm.addState(combined), analysisStatus);
    }


    public StatementAnalyser navigateTo(String target) {
        String myIndex = index();
        if (myIndex.equals(target)) return statementAnalyser;
        NavigationData<StatementAnalyser> navigationData = statementAnalyser.navigationData();
        if (target.startsWith(myIndex)) {
            // go into sub-block
            int n = myIndex.length();
            int blockIndex = Integer.parseInt(target.substring(n + 1, target.indexOf('.', n + 1)));
            return navigationData.blocks.get().get(blockIndex)
                    .orElseThrow(() -> new UnsupportedOperationException("Looking for " + target + ", block " + blockIndex));
        }
        if (myIndex.compareTo(target) < 0 && navigationData.next.get().isPresent()) {
            return navigationData.next.get().get().navigateTo(target);
        }
        throw new UnsupportedOperationException("? have index " + myIndex + ", looking for " + target);
    }

    private record ExecutionOfBlock(DV execution,
                                    StatementAnalyser startOfBlock,
                                    ConditionManager conditionManager,
                                    Expression condition,
                                    Expression absoluteState,
                                    boolean isDefault,
                                    LocalVariableCreation catchVariable) {

        public boolean escapesAlwaysButNotWithPrecondition() {
            if (!execution.equals(FlowData.NEVER) && startOfBlock != null) {
                StatementAnalysis lastStatement = startOfBlock.lastStatement().getStatementAnalysis();
                return lastStatement.flowData().interruptStatus().equals(FlowData.ALWAYS)
                        && !lastStatement.flowData().alwaysEscapesViaException();
            }
            return false;
        }

        public boolean escapesWithPrecondition() {
            if (!execution.equals(FlowData.NEVER) && startOfBlock != null) {
                StatementAnalysis lastStatement = startOfBlock.lastStatement().getStatementAnalysis();
                return lastStatement.flowData().alwaysEscapesViaException();
            }
            return false;
        }

        public boolean escapesAlways() {
            if (!execution.equals(FlowData.NEVER) && startOfBlock != null) {
                StatementAnalysis lastStatement = startOfBlock.lastStatement().getStatementAnalysis();
                return lastStatement.flowData().interruptStatus().equals(FlowData.ALWAYS);
            }
            return false;
        }

        public boolean alwaysExecuted() {
            return execution.equals(FlowData.ALWAYS) && startOfBlock != null;
        }
    }

    private AnalysisStatus haveSubBlocks(StatementAnalyserSharedState sharedState, List<Optional<StatementAnalyser>> startOfBlocks) {
        EvaluationContext evaluationContext = sharedState.evaluationContext();

        List<ExecutionOfBlock> executions = subBlocks_determineExecution(sharedState, startOfBlocks);
        AnalysisStatus analysisStatus = DONE;


        int blocksExecuted = 0;
        for (ExecutionOfBlock executionOfBlock : executions) {
            if (executionOfBlock.startOfBlock != null) {
                if (!executionOfBlock.execution.equals(FlowData.NEVER)) {
                    ForwardAnalysisInfo forward;
                    if (statement() instanceof SwitchStatementOldStyle switchStatement) {
                        forward = new ForwardAnalysisInfo(executionOfBlock.execution,
                                executionOfBlock.conditionManager, executionOfBlock.catchVariable,
                                switchStatement.startingPointToLabels(sharedState.context(),
                                        executionOfBlock.startOfBlock.getStatementAnalysis()),
                                statementAnalysis.stateData().valueOfExpression.get(),
                                statementAnalysis.stateData().valueOfExpression.get().causesOfDelay());
                    } else {
                        forward = new ForwardAnalysisInfo(executionOfBlock.execution,
                                executionOfBlock.conditionManager, executionOfBlock.catchVariable,
                                null, null, CausesOfDelay.EMPTY);
                    }
                    AnalyserResult result = ((StatementAnalyserImpl) executionOfBlock.startOfBlock)
                            .analyseAllStatementsInBlock(evaluationContext.getIteration(),
                                    forward, evaluationContext.getClosure());
                    sharedState.builder().add(result);
                    analysisStatus = analysisStatus.combine(result.analysisStatus());
                    blocksExecuted++;
                } else {
                    // ensure that the first statement is unreachable
                    if (statement() instanceof LoopStatement) {
                        statementAnalysis.ensure(Message.newMessage(statementAnalysis.location(Stage.MERGE),
                                Message.Label.EMPTY_LOOP));
                    }
                    sharedState.builder().addMessages(executionOfBlock.startOfBlock.getStatementAnalysis().messageStream());
                    executionOfBlock.startOfBlock.makeUnreachable();
                }
            }
        }
        boolean keepCurrentLocalConditionManager = true;

        if (blocksExecuted > 0) {
            boolean atLeastOneBlockExecuted = atLeastOneBlockExecuted(executions);

            // note that isEscapeAlwaysExecuted cannot be delayed (otherwise, it wasn't ALWAYS?)
            List<StatementAnalysisImpl.ConditionAndLastStatement> lastStatements;
            int maxTime;
            if (statement() instanceof SwitchStatementOldStyle switchStatementOldStyle) {
                lastStatements = composeLastStatements(sharedState.context(), switchStatementOldStyle, executions.get(0).startOfBlock);
                maxTime = executions.get(0).startOfBlock == null ? statementAnalysis.flowData().getTimeAfterEvaluation() :
                        executions.get(0).startOfBlock.lastStatement().getStatementAnalysis().flowData().getTimeAfterSubBlocks();
            } else {
                lastStatements = executions.stream()
                        .filter(ex -> ex.startOfBlock != null && !ex.startOfBlock.getStatementAnalysis().flowData().isUnreachable())
                        .map(ex -> new StatementAnalysisImpl.ConditionAndLastStatement(ex.condition,
                                ex.absoluteState,
                                ex.startOfBlock.index(),
                                ex.startOfBlock.lastStatement(),
                                ex.startOfBlock.lastStatement().getStatementAnalysis().isReturnOrEscapeAlwaysExecutedInCurrentBlock(true).valueIsTrue(),
                                ex.startOfBlock.lastStatement().getStatementAnalysis().isReturnOrEscapeAlwaysExecutedInCurrentBlock(false).valueIsTrue()))
                        .toList();
                /*
                 See VariableField_0; because we don't know which sub-block gets executed, we cannot use either
                 of the local copies, so we must create a new one.
                 */
                int increment = atLeastOneBlockExecuted ? 0 : 1;
                maxTime = lastStatements.stream()
                        .map(StatementAnalysisImpl.ConditionAndLastStatement::lastStatement)
                        .mapToInt(sa -> sa.getStatementAnalysis().flowData().getTimeAfterSubBlocks())
                        .max().orElse(statementAnalysis.flowData().getTimeAfterEvaluation()) + increment;
            }
            int maxTimeWithEscape;
            if (executions.stream().allMatch(ExecutionOfBlock::escapesAlways)) {
                maxTimeWithEscape = statementAnalysis.flowData().getTimeAfterEvaluation();
            } else {
                maxTimeWithEscape = maxTime;
            }
            if (statementAnalysis.flowData().timeAfterSubBlocksNotYetSet()) {
                statementAnalysis.flowData().setTimeAfterSubBlocks(maxTimeWithEscape, index());
            }

            Expression addToStateAfterStatement = addToStateAfterStatement(sharedState.context(), executions);
            Map<Variable, DV> setCnnVariables = addToContextNotNullAfterStatement(sharedState.context(), executions);

            // need timeAfterSubBlocks set already
            StatementAnalysisImpl.MergeResult result = ((StatementAnalysisImpl) statementAnalysis).mergeVariablesFromSubBlocks(evaluationContext,
                    sharedState.localConditionManager().state(), addToStateAfterStatement, lastStatements, atLeastOneBlockExecuted,
                    maxTimeWithEscape, setCnnVariables);
            analysisStatus = analysisStatus.combine(result.analysisStatus());

            // compute the escape situation of the sub-blocks

            Expression translatedAddToState = result.translatedAddToStateAfterMerge();
            if (!translatedAddToState.isBoolValueTrue()) {
                ConditionManager newLocalConditionManager = sharedState.localConditionManager()
                        .newForNextStatementDoNotChangePrecondition(sharedState.context(), translatedAddToState);
                statementAnalysis.stateData().setLocalConditionManagerForNextStatement(newLocalConditionManager);
                keepCurrentLocalConditionManager = false;
                LOGGER.debug("Continuing beyond default condition with conditional {}", translatedAddToState);
            }
        } else {
            int maxTime = statementAnalysis.flowData().getTimeAfterEvaluation();
            if (statementAnalysis.flowData().timeAfterSubBlocksNotYetSet()) {
                statementAnalysis.flowData().setTimeAfterSubBlocks(maxTime, index());
            }
            StatementAnalysisImpl.MergeResult result = ((StatementAnalysisImpl) statementAnalysis)
                    .mergeVariablesFromSubBlocks(evaluationContext, sharedState.localConditionManager().state(),
                            null, List.of(), false, maxTime, Map.of());
            analysisStatus = analysisStatus.combine(result.analysisStatus());
        }

        if (keepCurrentLocalConditionManager) {
            statementAnalysis.stateData().setLocalConditionManagerForNextStatement(sharedState.localConditionManager());
        }
        // has to be executed AFTER merging
        statementAnalysis.potentiallyRaiseNullPointerWarningENN();

        // whatever we do, we do not return DONE in the first iteration inside a loop, because of delayed values lingering
        // because we need to decide whether variables defined outside the loop are assigned in it.
        if (statementAnalysis.inLoop() && sharedState.evaluationContext().getIteration() == 0 && analysisStatus == DONE) {
            return AnalysisStatus.of(DelayFactory.createDelay(new SimpleCause(statementAnalysis.location(Stage.MERGE),
                    CauseOfDelay.Cause.WAIT_FOR_ASSIGNMENT)));
        }
        return analysisStatus;
    }

    /*
    an old-style switch statement is analysed as a single block where return and break statements at the level
    below the statement have no hard interrupt value (see flow data).
    the aggregation of results (merging), however, is computed based on the case statements and the break/return statements.

    This method does the splitting in different groups of statements.
     */

    private List<StatementAnalysisImpl.ConditionAndLastStatement> composeLastStatements(
            EvaluationResult evaluationContext,
            SwitchStatementOldStyle switchStatementOldStyle,
            StatementAnalyser startOfBlock) {
        Map<String, Expression> startingPointToLabels = switchStatementOldStyle
                .startingPointToLabels(evaluationContext, startOfBlock.getStatementAnalysis());
        return startingPointToLabels.entrySet().stream().map(e -> {
            StatementAnalyser lastStatement = startOfBlock.lastStatementOfSwitchOldStyle(e.getKey());
            boolean alwaysEscapes = statementAnalysis.flowData().alwaysEscapesViaException();

            // TODO not verified
            boolean alwaysEscapesOrReturns = statementAnalysis.isReturnOrEscapeAlwaysExecutedInCurrentBlock(false).valueIsTrue();
            return new StatementAnalysisImpl.ConditionAndLastStatement(e.getValue(),
                    e.getValue(), // TODO not verified (absolute state == condition)
                    e.getKey(), lastStatement, alwaysEscapes,
                    alwaysEscapesOrReturns);
        }).toList();
    }

    // IMPROVE we need to use interrupts (so that returns and breaks in if's also work!) -- code also at beginning of method!!
    public StatementAnalyser lastStatementOfSwitchOldStyle(String startAt) {
        StatementAnalyser sa = statementAnalyser;
        while (true) {
            if (sa.index().compareTo(startAt) >= 0 &&
                    (sa.getStatementAnalysis().statement() instanceof ReturnStatement ||
                            sa.getStatementAnalysis().statement() instanceof BreakStatement))
                return sa;
            if (sa.navigationDataNextGet().isPresent()) {
                sa = sa.navigationDataNextGet().get();
            } else {
                return sa;
            }
        }
    }


    private boolean atLeastOneBlockExecuted(List<ExecutionOfBlock> list) {
        Statement statement = statement();
        if (statement instanceof SwitchStatementOldStyle switchStatementOldStyle) {
            return switchStatementOldStyle.atLeastOneBlockExecuted();
        }
        if (statement instanceof SynchronizedStatement || statement instanceof TryStatement) return true;
        if (list.stream().anyMatch(ExecutionOfBlock::alwaysExecuted)) return true;
        // we have a default, and all conditions have code, and are possible
        return list.stream().anyMatch(e -> e.isDefault && e.startOfBlock != null) &&
                list.stream().allMatch(e -> (e.execution.equals(FlowData.CONDITIONALLY) || e.execution.isDelayed())
                        && e.startOfBlock != null);
    }

    private Map<Variable, DV> addToContextNotNullAfterStatement(EvaluationResult context, List<ExecutionOfBlock> list) {
        if (statement() instanceof IfElseStatement) {
            ExecutionOfBlock e0 = list.get(0);
            if (list.size() == 1) {
                if (e0.escapesWithPrecondition()) {
                    return findNotNullVariablesInRejectMode(context, list.get(0).condition);
                }
                return Map.of();
            }
            if (list.size() == 2) {
                ExecutionOfBlock e1 = list.get(1);
                boolean escape1 = e1.escapesAlwaysButNotWithPrecondition();
                if (e0.escapesAlwaysButNotWithPrecondition()) {
                    if (escape1) {
                        // both if and else escape; no point!
                        return Map.of();
                    }
                    // if escapes
                    return findNotNullVariablesInRejectMode(context, list.get(1).condition);
                }
                if (escape1) {
                    // else escapes
                    return findNotNullVariablesInRejectMode(context, list.get(0).condition);
                }
                return Map.of();
            }
            throw new UnsupportedOperationException("Impossible, if {} else {} has 2 blocks maximum.");
        }
        return Map.of();
    }

    private Map<Variable, DV> findNotNullVariablesInRejectMode(EvaluationResult evaluationContext, Expression condition) {
        Set<Variable> set = ConditionManager.findIndividualNull(condition, evaluationContext, Filter.FilterMode.REJECT, true);
        if (condition.isDelayed()) {
            List<Variable> variables = statement().getStructure().expression().variables(false);
            return variables.stream().distinct().collect(Collectors.toUnmodifiableMap(e -> e, e -> condition.causesOfDelay()));
        }
        return set.stream().collect(Collectors.toUnmodifiableMap(e -> e, e -> MultiLevel.EFFECTIVELY_NOT_NULL_DV));
    }

    private Expression addToStateAfterStatement(EvaluationResult context, List<ExecutionOfBlock> list) {
        BooleanConstant TRUE = new BooleanConstant(context.getPrimitives(), true);
        Statement statement = statement();
        if (statement instanceof TryStatement) {
            ExecutionOfBlock main = list.get(0);
            if (main.escapesAlways()) {
                Expression[] conditionsWithoutEscape = list.stream()
                        // without escape, and remove main and finally
                        .filter(executionOfBlock -> !executionOfBlock.escapesAlways() && !executionOfBlock.condition.isBoolValueTrue())
                        .map(executionOfBlock -> executionOfBlock.condition)
                        .toArray(Expression[]::new);
                return Or.or(context, conditionsWithoutEscape);
            }
            Expression[] conditionsWithEscape = list.stream()
                    // with escape, and remove main and finally
                    .filter(executionOfBlock -> executionOfBlock.escapesAlways() && !executionOfBlock.condition.isBoolValueTrue())
                    .map(executionOfBlock -> Negation.negate(context, executionOfBlock.condition))
                    .toArray(Expression[]::new);
            return And.and(context, conditionsWithEscape);
        }
        if (statement instanceof IfElseStatement) {
            ExecutionOfBlock e0 = list.get(0);
            if (list.size() == 1) {
                if (e0.escapesAlwaysButNotWithPrecondition()) {
                    return Negation.negate(context, list.get(0).condition);
                }
                return TRUE;
            }
            if (list.size() == 2) {
                ExecutionOfBlock e1 = list.get(1);
                boolean escape1 = e1.escapesAlwaysButNotWithPrecondition();
                if (e0.escapesAlwaysButNotWithPrecondition()) {
                    if (escape1) {
                        // both if and else escape
                        return new BooleanConstant(context.getPrimitives(), false);
                    }
                    // if escapes
                    return list.get(1).condition;
                }
                if (escape1) {
                    // else escapes
                    return list.get(0).condition;
                }
                return TRUE;
            }
            throw new UnsupportedOperationException("Impossible, if {} else {} has 2 blocks maximum.");
        }
        // a switch statement has no primary block, only subStructures, one per SwitchEntry

        // make an And of NOTs for all those conditions where the switch entry escapes
        if (statement instanceof HasSwitchLabels) {
            Expression[] components = list.stream().filter(ExecutionOfBlock::escapesAlwaysButNotWithPrecondition)
                    .map(e -> e.condition).toArray(Expression[]::new);
            if (components.length == 0) return TRUE;
            return And.and(context, components);
        }

        /*
        loop statements: result should be !condition || <any exit of exactly this loop, no return> ...
        forEach loop does not have an exit condition, however it can have exit FIXME
         */
        if (statement instanceof LoopStatement loopStatement) {
            Expression negatedConditionOrExitState;
            Range range = statementAnalysis.rangeData().getRange();
            if (range.isDelayed()) {
                negatedConditionOrExitState = DelayedExpression.forState(loopStatement.identifier,
                        statementAnalysis.primitives().booleanParameterizedType(),
                        range.variables(),
                        range.causesOfDelay());
            } else {
                // at the moment there is no Range which does not return a boolean constant
                Expression exit = range.exitState(context.evaluationContext());
                if (exit.isBooleanConstant() && !(statement instanceof ForEachStatement)) {
                    negatedConditionOrExitState = Negation.negate(context, list.get(0).condition);
                } else {
                    negatedConditionOrExitState = exit;
                }
            }
            return statementAnalysis.stateData().combineInterruptsAndExit(loopStatement, negatedConditionOrExitState,
                    context, statementAnalysis::isStillReachable);
        }

        if (statement instanceof SynchronizedStatement && list.get(0).startOfBlock != null) {
            Expression lastState = list.get(0).startOfBlock.lastStatement()
                    .getStatementAnalysis().stateData().getConditionManagerForNextStatement().state();
            return context.evaluationContext().replaceLocalVariables(lastState);
        }
        return TRUE;
    }


    private List<ExecutionOfBlock> subBlocks_determineExecution(StatementAnalyserSharedState sharedState, List<Optional<StatementAnalyser>> startOfBlocks) {
        List<ExecutionOfBlock> executions = new ArrayList<>(startOfBlocks.size());

        Expression value = statementAnalysis.stateData().valueOfExpression.get();
        CausesOfDelay valueIsDelayed = statementAnalysis.stateData().valueOfExpressionIsDelayed();
        assert value.isDone() || valueIsDelayed.isDelayed(); // sanity check
        Structure structure = statement().getStructure();

        // main block

        // some loops are never executed, and we can see that
        int start;
        if (statement() instanceof SwitchStatementNewStyle) {
            start = 0;
        } else {
            DV firstBlockStatementsExecution = structure.statementExecution().apply(value, sharedState.context());
            DV firstBlockExecution = statementAnalysis.flowData().execution(firstBlockStatementsExecution);

            executions.add(makeExecutionOfPrimaryBlock(sharedState.context(),
                    sharedState.localConditionManager(),
                    firstBlockExecution, startOfBlocks, value,
                    valueIsDelayed));
            start = 1;
        }

        for (int count = start; count < startOfBlocks.size(); count++) {
            Structure subStatements = structure.subStatements().get(count - start);
            Expression conditionForSubStatement;

            boolean isDefault;
            DV statementsExecution = subStatements.statementExecution().apply(value, sharedState.context());
            DV newExecution;
            if (statementsExecution.equals(FlowData.DEFAULT_EXECUTION)) {
                isDefault = true;
                conditionForSubStatement = defaultCondition(sharedState.context(), executions);
                if (conditionForSubStatement.isBoolValueFalse()) newExecution = FlowData.NEVER;
                else if (conditionForSubStatement.isBoolValueTrue()) newExecution = FlowData.ALWAYS;
                else if (conditionForSubStatement.isDelayed())
                    newExecution = conditionForSubStatement.causesOfDelay();
                else newExecution = FlowData.CONDITIONALLY;
            } else {
                if (statement() instanceof SwitchStatementNewStyle newStyle) {
                    SwitchEntry switchEntry = newStyle.switchEntries.get(count);
                    conditionForSubStatement = switchEntry.structure.expression();
                } else if (statementsExecution.equals(FlowData.ALWAYS)) {
                    conditionForSubStatement = new BooleanConstant(statementAnalysis.primitives(), true);
                    assert conditionForSubStatement.isDone();
                } else if (statementsExecution.equals(FlowData.NEVER)) {
                    conditionForSubStatement = null; // will not be executed anyway
                } else if (statement() instanceof TryStatement) { // catch
                    // extract them from the condition of the main block; it's got to be the same Instance objects
                    Expression condition = executions.get(0).conditionManager.condition();
                    Expression negated = condition instanceof And and ? and.getExpressions().get(count - 1) : condition;
                    conditionForSubStatement = Negation.negate(sharedState.context(), negated);
                    assert conditionForSubStatement.isDone();

                } else throw new UnsupportedOperationException();
                newExecution = statementsExecution;
                isDefault = true;
            }
            DV execution = statementAnalysis.flowData().execution(newExecution);

            ConditionManager subCm = execution.equals(FlowData.NEVER) ? null :
                    sharedState.localConditionManager().newAtStartOfNewBlockDoNotChangePrecondition(statementAnalysis.primitives(),
                            conditionForSubStatement);
            Expression absoluteState = subCm == null ? null : subCm.absoluteState(sharedState.context());
            boolean inCatch = statement() instanceof TryStatement && !subStatements.initialisers().isEmpty(); // otherwise, it is finally
            LocalVariableCreation catchVariable = inCatch ? (LocalVariableCreation) subStatements.initialisers().get(0) : null;
            executions.add(new ExecutionOfBlock(execution, startOfBlocks.get(count).orElse(null), subCm,
                    conditionForSubStatement, absoluteState, isDefault, catchVariable));
        }

        return executions;
    }

    private ExecutionOfBlock makeExecutionOfPrimaryBlock(EvaluationResult evaluationContext,
                                                         ConditionManager localConditionManager,
                                                         DV firstBlockExecution,
                                                         List<Optional<StatementAnalyser>> startOfBlocks,
                                                         Expression value,
                                                         CausesOfDelay valueIsDelayed) {
        Expression condition;
        Expression absoluteState;
        ConditionManager cm;
        if (firstBlockExecution.equals(FlowData.NEVER)) {
            cm = null;
            condition = null;
            absoluteState = null;
        } else {
            Primitives primitives = statementAnalysis.primitives();
            cm = conditionManagerForFirstBlock(localConditionManager, evaluationContext, primitives, value, valueIsDelayed);
            if (cm == localConditionManager) {
                condition = new BooleanConstant(primitives, true);
            } else {
                condition = cm.condition();
            }
            absoluteState = cm.absoluteState(evaluationContext);
        }
        return new ExecutionOfBlock(firstBlockExecution, startOfBlocks.get(0).orElse(null), cm, condition,
                absoluteState, false, null);
    }

    private ConditionManager conditionManagerForFirstBlock(ConditionManager localConditionManager,
                                                           EvaluationResult context,
                                                           Primitives primitives,
                                                           Expression value,
                                                           CausesOfDelay valueIsDelayed) {
        Structure structure = statement().getStructure();

        if (statement() instanceof TryStatement tryStatement) {
            if (tryStatement.catchClauses.isEmpty()) {
                return localConditionManager;
            }
            List<Expression> booleanVars = new ArrayList<>();
            int cnt = 1;
            for (Structure s : structure.subStatements()) {
                if (s.statementExecution() == StatementExecution.CONDITIONALLY) {
                    String index = index() + "." + (cnt++) + ".0";
                    booleanVars.add(Instance.forUnspecifiedCatchCondition(index, context.getPrimitives()));
                }
            }
            Expression condition = And.and(context, booleanVars.stream()
                    .map(v -> Negation.negate(context, v)).toArray(Expression[]::new));
            return localConditionManager.newAtStartOfNewBlockDoNotChangePrecondition(primitives, condition);
        }
        if (statement() instanceof LoopStatement) {
            Range range = statementAnalysis.rangeData().getRange();
            if (range.isDelayed()) {
                CausesOfDelay causesOfDelay = range.causesOfDelay();
                return localConditionManager.newAtStartOfNewBlockDoNotChangePrecondition(primitives,
                        DelayedExpression.forUnspecifiedLoopCondition(statement().getIdentifier(),
                                primitives.booleanParameterizedType(),
                                range.variables(),
                                causesOfDelay));
            }

            if (range != Range.NO_RANGE) {
                Expression condition = statementAnalysis.rangeData().extraState(context.evaluationContext());
                return localConditionManager.newAtStartOfNewBlockDoNotChangePrecondition(primitives,
                        condition);
            }
            if (statement() instanceof ForEachStatement) {
                // the expression is not a condition; however, we add one to ensure that the content is not empty
                Expression condition = isNotEmpty(context, value, valueIsDelayed.isDelayed());
                return localConditionManager.newAtStartOfNewBlockDoNotChangePrecondition(primitives, condition);
            }
        }
        // there are loop statements which are not forEach, and do not have a range, but do have a condition!
        if (structure.expressionIsCondition()) {
            return localConditionManager.newAtStartOfNewBlockDoNotChangePrecondition(primitives, value);
        }
        return localConditionManager;
    }

    private Expression isNotEmpty(EvaluationResult context, Expression value, boolean valueIsDelayed) {
        if (valueIsDelayed) {
            return DelayedExpression.forUnspecifiedLoopCondition(Identifier.loopCondition(index()),
                    context.getPrimitives().booleanParameterizedType(),
                    value.variables(true),
                    value.causesOfDelay());
        }
        if (value instanceof ArrayInitializer ai) {
            return new BooleanConstant(context.getPrimitives(), ai.multiExpression.expressions().length > 0);
        }
        ParameterizedType returnType = value.returnType();
        if (returnType.arrays > 0) {
            return new GreaterThanZero(Identifier.generate("gt0"), context.getPrimitives().booleanParameterizedType(),
                    new ArrayLength(context.getPrimitives(), value), false);
        }
        if (returnType.typeInfo != null) {
            TypeInfo collection = returnType.typeInfo.recursivelyImplements(context.getAnalyserContext(),
                    "java.util.Collection");
            if (collection != null) {
                MethodInfo isEmpty = collection.findUniqueMethod("isEmpty", 0);
                return Negation.negate(context, new MethodCall(Identifier.generate("isEmpty call"), false, value, isEmpty,
                        isEmpty.returnType(), List.of()));
            }
        }
        return Instance.forUnspecifiedLoopCondition(index(), context.getPrimitives());
    }

    private Expression defaultCondition(EvaluationResult context, List<ExecutionOfBlock> executions) {
        List<Expression> previousConditions = executions.stream().map(e -> e.condition).toList();
        if (previousConditions.isEmpty()) {
            return new BooleanConstant(context.getPrimitives(), true);
        }
        Expression[] negated = previousConditions.stream()
                .filter(Objects::nonNull)
                .map(c -> Negation.negate(context, c))
                .toArray(Expression[]::new);
        return And.and(context, negated);
    }

}
