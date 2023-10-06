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
import org.e2immu.analyser.analyser.delay.*;
import org.e2immu.analyser.analyser.impl.context.EvaluationResultImpl;
import org.e2immu.analyser.analyser.util.AnalyserResult;
import org.e2immu.analyser.analyser.util.ConditionManagerImpl;
import org.e2immu.analyser.analysis.StateData;
import org.e2immu.analyser.analysis.StatementAnalysis;
import org.e2immu.analyser.analysis.range.Range;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.statement.*;
import org.e2immu.analyser.model.variable.ReturnVariable;
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

        if (!startOfBlocks.isEmpty() && startOfBlocks.stream().anyMatch(Optional::isPresent)) {
            AnalysisStatus analysisStatus = haveSubBlocks(sharedState, startOfBlocks).combine(statusFromLocalCm)
                    .toAnalysisStatus();
            ensureEmptyPreAndPostCondition();
            return analysisStatus;
        }

        // FIXME this should also be implemented on the haveSubBlocks side
        Set<Variable> variablesAssigned = Stream.concat(
                        statementAnalysis.variableStream()
                                .filter(vi -> !(vi.variable() instanceof ReturnVariable))
                                .filter(vi -> vi.isAssignedAt(index()))
                                .map(VariableInfo::variable),
                        sharedState.localConditionManager().ignore().stream())
                .collect(Collectors.toUnmodifiableSet());
        ConditionManager cm = sharedState.localConditionManager().removeVariables(variablesAssigned);

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
            cmFromStatement = cm;
            statusFromStatement = statusFromLocalCm.combine(AnalysisStatus.of(ensureEmptyPreAndPostCondition()));
        }
        boolean progress = statementAnalysis.stateData().setLocalConditionManagerForNextStatement(cmFromStatement);

        if (statementAnalysis.flowData().timeAfterSubBlocksNotYetSet()) {
            statementAnalysis.flowData().copyTimeAfterSubBlocksFromTimeAfterExecution();
        }
        return statusFromStatement.addProgress(progress);
    }

    private CausesOfDelay ensureEmptyPreAndPostCondition() {
        MethodInfo methodInfo = statementAnalysis.methodAnalysis().getMethodInfo();
        CausesOfDelay preDelay;
        if (statementAnalysis.stateData().preconditionNoInformationYet(methodInfo)) {
            // it could have been set from the assert statement (subBlocks) or apply via a method call
            statementAnalysis.stateData().setPrecondition(Precondition.empty(statementAnalysis.primitives()));
            preDelay = CausesOfDelay.EMPTY;
        } else {
            preDelay = statementAnalysis.stateData().getPrecondition().causesOfDelay();
        }
        CausesOfDelay postDelay;
        if (statementAnalysis.stateData().postConditionNoInformationYet()) {
            statementAnalysis.stateData().setPostCondition(PostCondition.empty(statementAnalysis.primitives()));
            postDelay = CausesOfDelay.EMPTY;
        } else {
            postDelay = statementAnalysis.stateData().getPostCondition().causesOfDelay();
        }
        return preDelay.merge(postDelay);
    }

    private AnalysisStatus doThrowStatement(StatementAnalyserSharedState sharedState, ConditionManager cm) {
        Primitives primitives = sharedState.context().getPrimitives();
        DV escapeAlwaysExecuted = statementAnalysis.isEscapeAlwaysExecutedInCurrentBlock();

        Expression expression = cm.precondition(EvaluationResultImpl.from(sharedState.evaluationContext()));
        StateData stateData = statementAnalysis.stateData();

        CausesOfDelay delays = escapeAlwaysExecuted.causesOfDelay()
                .merge(expression.causesOfDelay())
                .merge(stateData.conditionManagerForNextStatementStatus());

        boolean progress;
        boolean inPreOrPostCondition = false;
        boolean canBeRepresented = Precondition.canBeRepresented(expression);

        DV isPostCondition = isPostCondition(expression);
        if (!isPostCondition.valueIsFalse() && canBeRepresented) {
            delays = delays.merge(isPostCondition.causesOfDelay());
            Expression potentiallyDelayed;
            if (isPostCondition.isDelayed() && !expression.isDelayed()) {
                potentiallyDelayed = DelayedExpression.forPrecondition(expression.getIdentifier(), primitives,
                        expression, isPostCondition.causesOfDelay());
            } else {
                potentiallyDelayed = expression;
            }
            PostCondition pc = new PostCondition(potentiallyDelayed, statementAnalysis.index());
            progress = stateData.setPostCondition(pc);
            LOGGER.debug("Escape with post-condition {}", potentiallyDelayed);
            inPreOrPostCondition = true;
        } else {
            // postCondition is false and there are no delays...
            progress = stateData.setPostCondition(PostCondition.empty(primitives));
        }
        if (!isPostCondition.valueIsTrue() && canBeRepresented) {
            // the identifier of the "throws" expression, in case we have a delayed precondition
            Identifier identifier = statement().getStructure().expression().getIdentifier();
            Expression translated = sharedState.evaluationContext().acceptAndTranslatePrecondition(identifier, expression);

            if (translated != null) {
                LOGGER.debug("Escape with precondition {}", translated);
                Precondition pc = new Precondition(translated, List.of(new Precondition.EscapeCause()));
                progress |= stateData.setPrecondition(pc);
            } else {
                // else: not stored in pc, so ensure it is unconditionally empty
                progress |= stateData.setPrecondition(Precondition.empty(primitives));
            }
            inPreOrPostCondition = true;
        } else {
            progress |= stateData.setPrecondition(Precondition.empty(primitives));
        }
        if (!inPreOrPostCondition) {
            stateData.ensureEscapeNotInPreOrPostConditions();
        }
        return ProgressWrapper.of(progress, delays);
    }

    private DV isPostCondition(Expression condition) {
        List<Variable> vars = condition.variableStream().toList();
        if (condition.isDelayed()) return condition.causesOfDelay();
        return vars.stream()
                .filter(v -> statementAnalysis.variableIsSet(v.fullyQualifiedName()))
                .map(statementAnalysis::variableHasBeenModified)
                .reduce(DV.FALSE_DV, (v1, v2) -> {
                    if (v1.valueIsTrue() || v2.valueIsTrue()) return DV.TRUE_DV;
                    if (v1.isDelayed() || v2.isDelayed()) {
                        return v1.causesOfDelay().merge(v2.causesOfDelay());
                    }
                    return DV.FALSE_DV;
                });
    }

    private ConditionManagerAndStatus doAssertStatement(StatementAnalyserSharedState sharedState, ConditionManager cm) {
        StateData stateData = statementAnalysis.stateData();
        Expression assertion = stateData.valueOfExpressionGet();
        Expression pcFromMethod = stateData.getPreconditionFromMethodCalls().expression();
        Expression combined = And.and(sharedState.context(), assertion, pcFromMethod);
        DV isPostCondition = isPostCondition(combined);

        // is this a post-condition or a precondition? that will depend on earlier modifications
        // when these modifications are delayed, we cannot decide between them, and write delays in both

        boolean inPreOrPostCondition = false;
        boolean progress;
        Primitives primitives = statementAnalysis.primitives();
        CausesOfDelay delays = CausesOfDelay.EMPTY;
        boolean canBeRepresented = Precondition.canBeRepresented(combined);
        if (!isPostCondition.valueIsFalse() && canBeRepresented) {
            delays = isPostCondition.causesOfDelay().merge(combined.causesOfDelay());
            Expression withIsPostCondition;
            if (isPostCondition.isDone() || combined.isDelayed()) {
                withIsPostCondition = combined;
            } else {
                withIsPostCondition = DelayedExpression.forPrecondition(assertion.getIdentifier(),
                        sharedState.context().getPrimitives(), combined, delays);
            }
            PostCondition pc = new PostCondition(withIsPostCondition, statementAnalysis.index());
            progress = stateData.setPostCondition(pc);
            inPreOrPostCondition = true;
        } else {
            // postCondition is false and there are no delays...
            progress = stateData.setPostCondition(PostCondition.empty(primitives));
        }
        if (!isPostCondition.valueIsTrue() && canBeRepresented) {
            Precondition pc;
            if (SAHelper.moveConditionToParameter(sharedState.context(), combined) == null) {
                // in IfStatement_10, we have an "assert" condition that cannot simply be moved to the precondition, because
                // it turns out the condition will always be false. We really need the local condition manager for next
                // statement to be delayed until we know the precondition can be accepted.
                // the identifier of the "assert" expression, in case we have a delayed precondition
                Identifier identifier = statement().getStructure().expression().getIdentifier();
                Expression accepted = sharedState.evaluationContext().acceptAndTranslatePrecondition(identifier, combined);
                Expression translated = Objects.requireNonNullElse(accepted,
                        new BooleanConstant(primitives, true));

                List<Precondition.PreconditionCause> preconditionCauses = Stream.concat(
                        translated.isBoolValueTrue() ? Stream.of() : Stream.of(new Precondition.EscapeCause()),
                        stateData.getPreconditionFromMethodCalls().causes().stream()).toList();

                Expression withIsPostCondition;
                if (isPostCondition.isDone() || translated.isDelayed()) {
                    withIsPostCondition = translated;
                } else {
                    withIsPostCondition = DelayedExpression.forPrecondition(assertion.getIdentifier(),
                            sharedState.context().getPrimitives(), combined, delays);
                }
                pc = new Precondition(withIsPostCondition, preconditionCauses);
                delays = delays.merge(withIsPostCondition.causesOfDelay());
            } else {
                // the null/not null of parameters has been handled during the main evaluation
                pc = Precondition.empty(primitives);
            }
            progress |= stateData.setPrecondition(pc);
            inPreOrPostCondition = true;
        } else {
            // postCondition is true, there are no delays
            progress |= stateData.setPrecondition(Precondition.empty(primitives));
        }

        if (!inPreOrPostCondition) {
            stateData.ensureEscapeNotInPreOrPostConditions();
        }
        boolean expressionIsDelayed = stateData.valueOfExpressionIsVariable();
        // NOTE that it is possible that assertion is not delayed, but the valueOfExpression is delayed
        // because of other delays in the apply method (see setValueOfExpression call in evaluationOfMainExpression)
        AnalysisStatus analysisStatus;
        if (expressionIsDelayed || delays.isDelayed()) {
            CausesOfDelay merge = stateData.valueOfExpressionIsDelayed().merge(delays);
            analysisStatus = ProgressWrapper.of(progress, merge);
        } else {
            analysisStatus = DONE;
        }
        Set<Variable> combinedVariables = Stream.concat(
                        statementAnalysis.statement().getStructure().expression().variableStream(),
                        combined.variableStream())
                .collect(Collectors.toUnmodifiableSet());
        return new ConditionManagerAndStatus(cm.addState(combined, combinedVariables), analysisStatus);
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
                                    Set<Variable> conditionVariables,
                                    Expression absoluteState,
                                    boolean isDefault,
                                    LocalVariableCreation catchVariable) {

        public boolean escapesAlwaysButNotWithPrecondition() {
            if (!execution.equals(FlowDataConstants.NEVER) && startOfBlock != null) {
                StatementAnalysis lastStatement = startOfBlock.lastStatement().getStatementAnalysis();
                return lastStatement.flowData().interruptStatus().equals(FlowDataConstants.ALWAYS)
                        && !lastStatement.flowData().alwaysEscapesViaException();
            }
            return false;
        }

        public boolean escapesWithPrecondition() {
            if (!execution.equals(FlowDataConstants.NEVER) && startOfBlock != null) {
                StatementAnalysis lastStatement = startOfBlock.lastStatement().getStatementAnalysis();
                return lastStatement.flowData().alwaysEscapesViaException();
            }
            return false;
        }

        public boolean escapesAlways() {
            if (!execution.equals(FlowDataConstants.NEVER) && startOfBlock != null) {
                StatementAnalysis lastStatement = startOfBlock.lastStatement().getStatementAnalysis();
                return lastStatement.flowData().interruptStatus().equals(FlowDataConstants.ALWAYS);
            }
            return false;
        }

        public boolean alwaysExecuted() {
            return execution.equals(FlowDataConstants.ALWAYS) && startOfBlock != null;
        }
    }

    private ProgressAndDelay haveSubBlocks(StatementAnalyserSharedState sharedState, List<Optional<StatementAnalyser>> startOfBlocks) {
        EvaluationContext evaluationContext = sharedState.evaluationContext();

        List<ExecutionOfBlock> executions = subBlocks_determineExecution(sharedState, startOfBlocks);
        ProgressAndDelay analysisStatus = ProgressAndDelay.EMPTY;

        int blocksExecuted = 0;
        for (ExecutionOfBlock executionOfBlock : executions) {
            if (executionOfBlock.startOfBlock != null) {
                if (!executionOfBlock.execution.equals(FlowDataConstants.NEVER)) {
                    ForwardAnalysisInfo forward;
                    if (statement() instanceof SwitchStatementOldStyle switchStatement) {
                        forward = new ForwardAnalysisInfo(executionOfBlock.execution,
                                executionOfBlock.conditionManager, executionOfBlock.catchVariable,
                                switchStatement.startingPointToLabels(sharedState.context(),
                                        executionOfBlock.startOfBlock.getStatementAnalysis()),
                                statementAnalysis.stateData().valueOfExpressionGet(),
                                statementAnalysis.stateData().valueOfExpressionGet().causesOfDelay(),
                                evaluationContext.breakDelayLevel());
                    } else {
                        forward = new ForwardAnalysisInfo(executionOfBlock.execution,
                                executionOfBlock.conditionManager, executionOfBlock.catchVariable,
                                null, null, CausesOfDelay.EMPTY,
                                evaluationContext.breakDelayLevel());
                    }
                    AnalyserResult result = ((StatementAnalyserImpl) executionOfBlock.startOfBlock)
                            .analyseAllStatementsInBlock(evaluationContext.getIteration(),
                                    statementAnalysis.flowData().getTimeAfterEvaluation(),
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
        MergeVariables mergeVariables = new MergeVariables(statementAnalysis);

        if (blocksExecuted > 0) {
            boolean atLeastOneBlockExecuted = atLeastOneBlockExecuted(executions);

            // note that isEscapeAlwaysExecuted cannot be delayed (otherwise, it wasn't ALWAYS?)
            List<MergeVariables.ConditionAndLastStatement> lastStatements;
            int maxTime;
            if (statement() instanceof SwitchStatementOldStyle switchStatementOldStyle) {
                lastStatements = composeLastStatements(sharedState.context(), switchStatementOldStyle, executions.get(0).startOfBlock);
                maxTime = executions.get(0).startOfBlock == null ? statementAnalysis.flowData().getTimeAfterEvaluation() :
                        executions.get(0).startOfBlock.lastStatement().getStatementAnalysis().flowData().getTimeAfterSubBlocks();
            } else {
                lastStatements = executions.stream()
                        .filter(ex -> ex.startOfBlock != null && !ex.startOfBlock.getStatementAnalysis().flowData().isUnreachable())
                        .map(ex -> {
                            StatementAnalyser lastStatement = ex.startOfBlock.lastStatement();
                            StatementAnalysis lastAnalysis = lastStatement.getStatementAnalysis();
                            return new MergeVariables.ConditionAndLastStatement(ex.condition,
                                    ex.absoluteState,
                                    ex.startOfBlock.index(),
                                    lastStatement,
                                    lastAnalysis.flowData().getGuaranteedToBeReachedInCurrentBlock(),
                                    lastAnalysis.isReturnOrEscapeAlwaysExecutedInCurrentBlock(true).valueIsTrue(),
                                    lastAnalysis.isReturnOrEscapeAlwaysExecutedInCurrentBlock(false).valueIsTrue());
                        })
                        .toList();
                /*
                 See VariableField_0; because we don't know which sub-block gets executed, we cannot use either
                 of the local copies, so we must create a new one.
                 */
                int increment = atLeastOneBlockExecuted ? 0 : 1;
                maxTime = lastStatements.stream()
                        .map(MergeVariables.ConditionAndLastStatement::lastStatement)
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
            MergeVariables.MergeResult result = mergeVariables.mergeVariablesFromSubBlocks(evaluationContext,
                    sharedState.localConditionManager().state(), addToStateAfterStatement, lastStatements, atLeastOneBlockExecuted,
                    maxTimeWithEscape, setCnnVariables);
            analysisStatus = analysisStatus.combine(result.analysisStatus());

            // compute the escape situation of the sub-blocks

            Expression translatedAddToState = result.translatedAddToStateAfterMerge();
            if (!translatedAddToState.isBoolValueTrue()) {
                // check: is the following statement correct?
                Set<Variable> stateVariables = translatedAddToState.variableStream().collect(Collectors.toUnmodifiableSet());
                ConditionManager newLocalConditionManager = sharedState.localConditionManager()
                        .newForNextStatementDoNotChangePrecondition(sharedState.context(), translatedAddToState,
                                stateVariables);
                statementAnalysis.stateData().setLocalConditionManagerForNextStatement(newLocalConditionManager);
                keepCurrentLocalConditionManager = false;
                LOGGER.debug("Continuing beyond default condition with conditional {}", translatedAddToState);
            }
        } else {
            int maxTime = statementAnalysis.flowData().getTimeAfterEvaluation();
            if (statementAnalysis.flowData().timeAfterSubBlocksNotYetSet()) {
                statementAnalysis.flowData().setTimeAfterSubBlocks(maxTime, index());
            }
            MergeVariables.MergeResult result = mergeVariables
                    .mergeVariablesFromSubBlocks(evaluationContext, sharedState.localConditionManager().state(),
                            null, List.of(), false, maxTime, Map.of());
            analysisStatus = analysisStatus.combine(result.analysisStatus());
        }

        boolean progress;
        if (keepCurrentLocalConditionManager) {
            progress = statementAnalysis.stateData().setLocalConditionManagerForNextStatement(sharedState.localConditionManager());
        } else {
            progress = false;
        }
        // has to be executed AFTER merging
        statementAnalysis.potentiallyRaiseNullPointerWarningENN();

        // whatever we do, we do not return DONE in the first iteration inside a loop, because of delayed values lingering
        // because we need to decide whether variables defined outside the loop are assigned in it.
        if (statementAnalysis.inLoop() && sharedState.evaluationContext().getIteration() == 0 && analysisStatus.isDone()) {
            CausesOfDelay delay = DelayFactory.createDelay(new SimpleCause(statementAnalysis.location(Stage.MERGE),
                    CauseOfDelay.Cause.WAIT_FOR_ASSIGNMENT));
            return new ProgressAndDelay(true, delay);
        }
        return analysisStatus.addProgress(progress);
    }

    /*
    an old-style switch statement is analysed as a single block where return and break statements at the level
    below the statement have no hard interrupt value (see flow data).
    the aggregation of results (merging), however, is computed based on the case statements and the break/return statements.

    This method does the splitting in different groups of statements.
     */

    private List<MergeVariables.ConditionAndLastStatement> composeLastStatements(
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
            return new MergeVariables.ConditionAndLastStatement(e.getValue(),
                    e.getValue(), // TODO not verified (absolute state == condition)
                    e.getKey(), lastStatement,
                    lastStatement.getStatementAnalysis().flowData().getGuaranteedToBeReachedInCurrentBlock(),
                    alwaysEscapes,
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
                list.stream().allMatch(e -> (e.execution.equals(FlowDataConstants.CONDITIONALLY) || e.execution.isDelayed())
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
        Set<Variable> set = ConditionManagerImpl.findIndividualNull(condition, evaluationContext, Filter.FilterMode.REJECT, true);
        if (condition.isDelayed()) {
            List<Variable> variables = statement().getStructure().expression().variables(DescendMode.NO);
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
                        range.expression(statement.getIdentifier()),
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

        Expression value = statementAnalysis.stateData().valueOfExpressionGet();
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
            Set<Variable> conditionVariables;

            boolean isDefault;
            DV statementsExecution = subStatements.statementExecution().apply(value, sharedState.context());
            DV newExecution;
            if (statementsExecution.equals(FlowDataConstants.DEFAULT_EXECUTION)) {
                isDefault = true;
                conditionForSubStatement = defaultCondition(sharedState.context(), executions);
                conditionVariables = executions.stream().flatMap(e -> e.conditionVariables == null ? Stream.of() : e.conditionVariables.stream()).collect(Collectors.toUnmodifiableSet());
                if (conditionForSubStatement.isBoolValueFalse()) newExecution = FlowDataConstants.NEVER;
                else if (conditionForSubStatement.isBoolValueTrue()) newExecution = FlowDataConstants.ALWAYS;
                else if (conditionForSubStatement.isDelayed())
                    newExecution = conditionForSubStatement.causesOfDelay();
                else newExecution = FlowDataConstants.CONDITIONALLY;
            } else {
                if (statement() instanceof SwitchStatementNewStyle newStyle) {
                    SwitchEntry switchEntry = newStyle.switchEntries.get(count);
                    conditionForSubStatement = switchEntry.structure.expression();
                    conditionVariables = conditionForSubStatement.variableStream().collect(Collectors.toUnmodifiableSet());
                } else if (statementsExecution.equals(FlowDataConstants.ALWAYS)) {
                    conditionForSubStatement = new BooleanConstant(statementAnalysis.primitives(), true);
                    conditionVariables = Set.of();
                    assert conditionForSubStatement.isDone();
                } else if (statementsExecution.equals(FlowDataConstants.NEVER)) {
                    conditionForSubStatement = null; // will not be executed anyway
                    conditionVariables = null;
                } else if (statement() instanceof TryStatement) { // catch
                    // extract them from the condition of the main block; it's got to be the same Instance objects
                    Expression condition = executions.get(0).conditionManager.condition();
                    Expression negated = condition instanceof And and ? and.getExpressions().get(count - 1) : condition;
                    conditionForSubStatement = Negation.negate(sharedState.context(), negated);
                    assert conditionForSubStatement.isDone();
                    conditionVariables = executions.get(0).conditionManager.conditionVariables(); // maybe too many??

                } else throw new UnsupportedOperationException();
                newExecution = statementsExecution;
                isDefault = true;
            }
            DV execution = statementAnalysis.flowData().execution(newExecution);

            ConditionManager subCm = execution.equals(FlowDataConstants.NEVER) ? null :
                    sharedState.localConditionManager().newAtStartOfNewBlockDoNotChangePrecondition(statementAnalysis.primitives(),
                            conditionForSubStatement, conditionVariables);
            Expression absoluteState = subCm == null ? null : subCm.absoluteState(sharedState.context());
            boolean inCatch = statement() instanceof TryStatement && !subStatements.initialisers().isEmpty(); // otherwise, it is finally
            LocalVariableCreation catchVariable = inCatch ? (LocalVariableCreation) subStatements.initialisers().get(0) : null;
            executions.add(new ExecutionOfBlock(execution, startOfBlocks.get(count).orElse(null), subCm,
                    conditionForSubStatement, conditionVariables, absoluteState, isDefault, catchVariable));
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
        Set<Variable> conditionVariables;
        if (firstBlockExecution.equals(FlowDataConstants.NEVER)) {
            cm = null;
            condition = null;
            conditionVariables = null;
            absoluteState = null;
        } else {
            Primitives primitives = statementAnalysis.primitives();
            cm = conditionManagerForFirstBlock(localConditionManager, evaluationContext, primitives, value, valueIsDelayed);
            if (cm == localConditionManager) {
                condition = new BooleanConstant(primitives, true);
                conditionVariables = Set.of();
            } else {
                condition = cm.condition();
                conditionVariables = cm.conditionVariables();
            }
            absoluteState = cm.absoluteState(evaluationContext);
        }
        return new ExecutionOfBlock(firstBlockExecution, startOfBlocks.get(0).orElse(null), cm, condition,
                conditionVariables,
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
            Set<Variable> conditionVariables = new HashSet<>();
            int cnt = 0;
            for (Structure s : structure.subStatements()) {
                if (s.statementExecution() == StatementExecution.CONDITIONALLY) {
                    Identifier identifier = tryStatement.catchClauses.get(cnt++).k.identifier;
                    booleanVars.add(Instance.forUnspecifiedCatchCondition(context.getPrimitives(), identifier));
                    conditionVariables.addAll(s.expression().variables(DescendMode.NO));
                }
            }
            Expression condition = And.and(context, booleanVars.stream()
                    .map(v -> Negation.negate(context, v)).toArray(Expression[]::new));
            return localConditionManager.newAtStartOfNewBlockDoNotChangePrecondition(primitives, condition, Set.copyOf(conditionVariables));
        }
        if (statement() instanceof LoopStatement) {
            Identifier identifier = statement().getIdentifier();
            Range range = statementAnalysis.rangeData().getRange();
            if (range.isDelayed()) {
                CausesOfDelay causesOfDelay = range.causesOfDelay();
                return localConditionManager.newAtStartOfNewBlockDoNotChangePrecondition(primitives,
                        DelayedExpression.forUnspecifiedLoopCondition(statement().getIdentifier(),
                                primitives.booleanParameterizedType(),
                                range.expression(identifier),
                                causesOfDelay), Set.copyOf(range.variables()));
            }

            if (range != Range.NO_RANGE) {
                Expression condition = statementAnalysis.rangeData().extraState(identifier, context.evaluationContext());
                Set<Variable> conditionVariables = Stream.concat(range.variables().stream(), condition.variableStream()).collect(Collectors.toUnmodifiableSet());
                return localConditionManager.newAtStartOfNewBlockDoNotChangePrecondition(primitives,
                        condition, conditionVariables);
            }
            if (statement() instanceof ForEachStatement) {
                // the expression is not a condition; however, we add one to ensure that the content is not empty
                Expression condition = isNotEmpty(context, value, valueIsDelayed.isDelayed());
                Set<Variable> conditionVariables = Stream.concat(structure.expression().variableStream(),
                        condition.variableStream()).collect(Collectors.toUnmodifiableSet());
                return localConditionManager.newAtStartOfNewBlockDoNotChangePrecondition(primitives, condition, conditionVariables);
            }
        }
        // there are loop statements which are not forEach, and do not have a range, but do have a condition!
        if (structure.expressionIsCondition()) {
            Expression condition = value;
            // this block is needed for InstanceOf_11, the literal null check
            if (!condition.equals(structure.expression())) {
                Expression literal = structure.expression().keepLiteralNotNull(context, true);
                if (literal != null) {
                    //    condition = And.and(context, condition, literal);
                }
            }
            Set<Variable> conditionVariables = Stream.concat(structure.expression().variableStream(),
                    condition.variableStream()).collect(Collectors.toUnmodifiableSet());
            return localConditionManager.newAtStartOfNewBlockDoNotChangePrecondition(primitives, condition, conditionVariables);
        }
        return localConditionManager;
    }

    private Expression isNotEmpty(EvaluationResult context, Expression value, boolean valueIsDelayed) {
        if (valueIsDelayed) {
            return DelayedExpression.forUnspecifiedLoopCondition(Identifier.loopCondition(index()),
                    context.getPrimitives().booleanParameterizedType(), value, value.causesOfDelay());
        }
        if (value instanceof ArrayInitializer ai) {
            return new BooleanConstant(context.getPrimitives(), ai.multiExpression.expressions().length > 0);
        }
        ParameterizedType returnType = value.returnType();
        if (returnType.arrays > 0) {
            ArrayLength arrayLength = new ArrayLength(value.getIdentifier(), context.getPrimitives(), value);
            Expression minusOne = Sum.sum(context, arrayLength, IntConstant.minusOne(context.getPrimitives()));
            return new GreaterThanZero(Identifier.generate("gt0"), context.getPrimitives(),
                    minusOne, true);
        }
        if (returnType.typeInfo != null) {
            TypeInfo collection = returnType.typeInfo.recursivelyImplements(context.getAnalyserContext(),
                    "java.util.Collection");
            if (collection != null) {
                MethodInfo isEmpty = collection.findUniqueMethod("isEmpty", 0);
                return Negation.negate(context, new MethodCall(Identifier.generate("isEmpty call"), false, value, isEmpty,
                        isEmpty.returnType(), List.of(), context.modificationTimesOf(value)));
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
        // FIXME add keepLiteral!!
        return And.and(context, negated);
    }

}
