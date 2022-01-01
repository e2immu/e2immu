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

package org.e2immu.analyser.analysis;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analyser.delay.NoDelay;
import org.e2immu.analyser.analyser.delay.SimpleSet;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.impl.LocationImpl;
import org.e2immu.analyser.model.Statement;
import org.e2immu.analyser.model.statement.*;
import org.e2immu.analyser.util.Logger;
import org.e2immu.support.SetOnce;
import org.e2immu.support.SetOnceMap;
import org.e2immu.support.VariableFirstThen;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.e2immu.analyser.analyser.AnalysisStatus.DONE;
import static org.e2immu.analyser.analyser.AnalysisStatus.DONE_ALL;
import static org.e2immu.analyser.analyser.InterruptsFlow.*;
import static org.e2immu.analyser.util.Logger.log;

/**
 * Flow is that part of the analysis that is concerned with reachability of statements,
 * and escaping from the normal sequential flow.
 * <p>
 * Flow analysis is never delayed.
 */
public class FlowData {

    // meant for statements following a
    private final VariableFirstThen<CausesOfDelay, DV> guaranteedToBeReachedInCurrentBlock;
    private final VariableFirstThen<CausesOfDelay, DV> guaranteedToBeReachedInMethod;
    // execution of the block
    public final VariableFirstThen<CausesOfDelay, DV> blockExecution;
    // are there any statements in sub-blocks that interrupt the flow?
    private final VariableFirstThen<CausesOfDelay, Map<InterruptsFlow, DV>> interruptsFlow;

    // counts all increases in statement time
    private final SetOnce<Integer> initialTime = new SetOnce<>(); // STEP 1
    private final SetOnce<Integer> timeAfterEvaluation = new SetOnce<>(); // STEP 3
    private final SetOnce<Integer> timeAfterSubBlocks = new SetOnce<>(); // STEP 4

    public final SetOnceMap<Integer, String> assignmentIdOfStatementTime = new SetOnceMap<>();

    public FlowData(LocationImpl location) {
        CausesOfDelay initialDelay = new SimpleSet(location, CauseOfDelay.Cause.INITIAL_VALUE);
        guaranteedToBeReachedInCurrentBlock = new VariableFirstThen<>(initialDelay);
        guaranteedToBeReachedInMethod = new VariableFirstThen<>(initialDelay);
        interruptsFlow = new VariableFirstThen<>(initialDelay);
        blockExecution = new VariableFirstThen<>(initialDelay);
    }

    public void initialiseAssignmentIds(FlowData previous) {
        assignmentIdOfStatementTime.putAll(previous.assignmentIdOfStatementTime);
    }

    public void setInitialTime(int time, String index) {
        initialTime.set(time);
        if (!assignmentIdOfStatementTime.isSet(time)) {
            assignmentIdOfStatementTime.put(time, index + VariableInfoContainer.Level.INITIAL);
        }
    }

    public void setTimeAfterEvaluation(int time, String index) {
        timeAfterEvaluation.set(time);
        if (!assignmentIdOfStatementTime.isSet(time)) {
            assignmentIdOfStatementTime.put(time, index + VariableInfoContainer.Level.EVALUATION);
        }
    }

    public void setTimeAfterSubBlocks(int time, String index) {
        timeAfterSubBlocks.set(time);
        if (!assignmentIdOfStatementTime.isSet(time)) {
            assignmentIdOfStatementTime.put(time, index + VariableInfoContainer.Level.MERGE);
        }
    }

    public void copyTimeAfterExecutionFromInitialTime() {
        int ini = initialTime.get();
        timeAfterEvaluation.set(ini);
    }

    public void copyTimeAfterSubBlocksFromTimeAfterExecution() {
        int exe = timeAfterEvaluation.get();
        timeAfterSubBlocks.set(exe);
    }

    public boolean initialTimeNotYetSet() {
        return !initialTime.isSet();
    }

    public boolean timeAfterExecutionNotYetSet() {
        return !timeAfterEvaluation.isSet();
    }

    public boolean timeAfterSubBlocksNotYetSet() {
        return !timeAfterSubBlocks.isSet();
    }

    public int getInitialTime() {
        return initialTime.get();
    }

    public int getTimeAfterEvaluation() {
        return timeAfterEvaluation.get();
    }

    public int getTimeAfterSubBlocks() {
        return timeAfterSubBlocks.get();
    }

    public DV interruptStatus() {
        // what is the worst that can happen? ESCAPE-ALWAYS
        if (!interruptsFlowIsSet()) return interruptsFlow.getFirst();
        return interruptsFlow.get().values().stream().reduce(NEVER, DV::max);
    }

    // if interrupt status is empty, we return ALWAYS -- we're good to proceed
    // if interrupt status is ALWAYS, we return NEVER -- this execution will not be reachable
    // if interrupt status is NEVER, or there is no interrupt, we return ALWAYS -- this we can reach
    // if interrupt status are ALWAYS and NEVER, we should interpret as CONDITIONALLY
    private DV interruptStatusToExecution() {
        if (!interruptsFlowIsSet()) return interruptsFlow.getFirst();
        List<DV> execs = interruptsFlow.get().entrySet().stream()
                // "NO -> ALWAYS" is filtered out; NO - CONDITIONALLY needs to be kept!
                .filter(e -> !e.getKey().equals(NO) || !e.getValue().equals(ALWAYS))
                .map(Map.Entry::getValue).toList();
        if (execs.isEmpty()) return ALWAYS;
        boolean allAlways = execs.stream().allMatch(e -> e.equals(ALWAYS));
        if (allAlways) {
            return NEVER;
        }
        boolean allNever = execs.stream().allMatch(e -> e.equals(NEVER));
        if (allNever) return ALWAYS;
        return CONDITIONALLY;
    }

    public InterruptsFlow bestAlwaysInterrupt() {
        assert interruptsFlowIsSet();
        InterruptsFlow best = NO;
        for (Map.Entry<InterruptsFlow, DV> e : interruptsFlow.get().entrySet()) {
            DV execution = e.getValue();
            if (!execution.equals(ALWAYS)) return NO;
            InterruptsFlow interruptsFlow = e.getKey();
            best = interruptsFlow.best(best);
        }
        return best;
    }

    public DV execution(DV statementsExecution) {
        // combine with guaranteed to be reached in block
        if (guaranteedToBeReachedInMethod.isSet()) {
            return guaranteedToBeReachedInMethod.get().min(statementsExecution);
        }
        return guaranteedToBeReachedInMethod.getFirst().min(statementsExecution);
    }

    public void setGuaranteedToBeReached(DV execution) {
        setGuaranteedToBeReachedInCurrentBlock(execution);
        setGuaranteedToBeReachedInMethod(execution);
    }

    public void setGuaranteedToBeReachedInCurrentBlock(DV executionInBlock) {
        if (executionInBlock.isDone()) {
            if ((!guaranteedToBeReachedInCurrentBlock.isSet() || !guaranteedToBeReachedInCurrentBlock.get().equals(executionInBlock))) {
                guaranteedToBeReachedInCurrentBlock.set(executionInBlock);
            }
        } else {
            guaranteedToBeReachedInCurrentBlock.setFirst(executionInBlock.causesOfDelay());
        }
    }

    public void setGuaranteedToBeReachedInMethod(DV executionInMethod) {
        if (executionInMethod.isDone()) {
            if (!guaranteedToBeReachedInMethod.isSet() || !guaranteedToBeReachedInMethod.get().equals(executionInMethod)) {
                guaranteedToBeReachedInMethod.set(executionInMethod);
            }
        } else {
            guaranteedToBeReachedInMethod.setFirst(executionInMethod.causesOfDelay());
        }
    }

    public DV getGuaranteedToBeReachedInCurrentBlock() {
        if (guaranteedToBeReachedInCurrentBlock.isSet()) {
            return guaranteedToBeReachedInCurrentBlock.get();
        }
        return guaranteedToBeReachedInCurrentBlock.getFirst();
    }

    public DV getGuaranteedToBeReachedInMethod() {
        if (guaranteedToBeReachedInMethod.isSet()) {
            return guaranteedToBeReachedInMethod.get();
        }
        return guaranteedToBeReachedInMethod.getFirst();
    }

    public boolean isUnreachable() {
        return guaranteedToBeReachedInMethod.isSet() && guaranteedToBeReachedInMethod.get().equals(NEVER);
    }

    // testing only
    public Map<InterruptsFlow, DV> getInterruptsFlow() {
        return interruptsFlow.get();
    }

    public boolean interruptsFlowIsSet() {
        return interruptsFlow.isSet();
    }

    /*
    at some point we need to make a distinction between different
     */
    public boolean alwaysEscapesViaException() {
        return interruptsFlow.isSet() && ALWAYS.equals(interruptsFlow.get().get(ESCAPE));
    }

    public static final DV DEFAULT_EXECUTION = new NoDelay(3);
    public static final DV ALWAYS = new NoDelay(2);
    public static final DV CONDITIONALLY = new NoDelay(1);
    public static final DV NEVER = new NoDelay(0);

    /**
     * Call occurs before the actual analysis of the statement.
     *
     * @param previousStatement previous statement; null if this is the first statement in a block
     * @param blockExecution    the execution value of the block, if this is the first statement in the block; otherwise, ALWAYS
     * @param state             current state of local condition manager, can be delayed
     * @return true when unreachable statement
     */
    public AnalysisStatus computeGuaranteedToBeReachedReturnUnreachable(StatementAnalysis previousStatement,
                                                                        DV blockExecution,
                                                                        Expression state,
                                                                        CausesOfDelay stateIsDelayed,
                                                                        CausesOfDelay localConditionManagerIsDelayed) {
        CausesOfDelay causes = stateIsDelayed
                .merge(localConditionManagerIsDelayed)
                .merge(blockExecution.causesOfDelay());
        AnalysisStatus delayBasedOnExecutionAndLocalConditionManager = AnalysisStatus.of(causes);

        // some statements that need executing independently of delays
        if (previousStatement == null) {
            // start of a block is always reached in that block
            setGuaranteedToBeReachedInCurrentBlock(ALWAYS);
        } else if (previousStatement.flowData().getGuaranteedToBeReachedInMethod().equals(NEVER)) {
            setGuaranteedToBeReachedInCurrentBlock(NEVER);
            setGuaranteedToBeReachedInMethod(NEVER);
            return delayBasedOnExecutionAndLocalConditionManager; // no more errors
        }

        if (stateIsDelayed.isDelayed()) {
            log(Logger.LogTarget.DELAYED, "Delaying guaranteed to be reached, no value state");
            return delayBasedOnExecutionAndLocalConditionManager;
        }

        if (guaranteedToBeReachedInMethod.isSet()) {
            return delayBasedOnExecutionAndLocalConditionManager; // already done!
        }

        if (previousStatement == null) {
            // start of a block, within method
            setGuaranteedToBeReachedInMethod(blockExecution);
            return delayBasedOnExecutionAndLocalConditionManager;
        }

        // look at the previous statement in the block, there are no delays

        DV prev = previousStatement.flowData().getGuaranteedToBeReachedInCurrentBlock();
        // ALWAYS = always interrupted, NEVER = never interrupted, CONDITIONALLY = potentially interrupted
        DV interrupt = previousStatement.flowData().interruptStatusToExecution();
        DV execBasedOnState = state.isBoolValueFalse() ? NEVER : ALWAYS;
        DV executionInCurrentBlock = prev.min(interrupt).min(execBasedOnState);

        setGuaranteedToBeReachedInCurrentBlock(executionInCurrentBlock);
        setGuaranteedToBeReachedInMethod(executionInCurrentBlock.min(blockExecution));

        if (executionInCurrentBlock.equals(NEVER)) return DONE_ALL;
        if (delayBasedOnExecutionAndLocalConditionManager.isDelayed())
            return delayBasedOnExecutionAndLocalConditionManager;
        return AnalysisStatus.of(executionInCurrentBlock);
    }

    public AnalysisStatus analyseInterruptsFlow(StatementAnalyser statementAnalyser, StatementAnalysis previousStatement) {
        Statement statement = statementAnalyser.statement();
        boolean oldStyleSwitch = statementAnalyser.parent() != null &&
                statementAnalyser.parent().statement() instanceof SwitchStatementOldStyle;

        if (!oldStyleSwitch) {
            if (statement instanceof ReturnStatement) {
                setInterruptsFlow(Map.of(RETURN, ALWAYS));
                return DONE;
            }
            if (statement instanceof ThrowStatement) {
                setInterruptsFlow(Map.of(ESCAPE, ALWAYS));
                return DONE;
            }
            if (statement instanceof BreakStatement breakStatement) {
                setInterruptsFlow(Map.of(InterruptsFlow.createBreak(breakStatement.label), ALWAYS));
                return DONE;
            }
        }

        if (statement instanceof ExpressionAsStatement || statement instanceof EmptyStatement) {
            setInterruptsFlow(Map.of());
            return DONE;
        }

        if (statement instanceof ContinueStatement continueStatement) {
            setInterruptsFlow(Map.of(InterruptsFlow.createContinue(continueStatement.label), ALWAYS));
            return DONE;
        }
        // in case there is no explicit return statement at the end of the method...
        // this one is probably completely irrelevant
        boolean endOfBlockTopLevel = statementAnalyser.getStatementAnalysis().atTopLevel() &&
                statementAnalyser.navigationDataNextGet().isEmpty();
        if (endOfBlockTopLevel) {
            setInterruptsFlow(Map.of(RETURN, ALWAYS));
            return DONE;
        }

        // all the obvious ones have been done; for the rest we need to ensure that blockExecution has been set already
        if (!blockExecution.isSet()) return blockExecution.getFirst();

        if (previousStatement != null && !previousStatement.flowData().interruptsFlowIsSet()) {
            log(Logger.LogTarget.DELAYED, "Delaying interrupts flow, previous statement {} has no interruptsFlow yet",
                    previousStatement.index());
            return previousStatement.flowData().interruptsFlow.getFirst();
        }

        // situation from the previous statement
        Map<InterruptsFlow, DV> builder = new HashMap<>(previousStatement == null ? Map.of() :
                previousStatement.flowData().interruptsFlow.get());

        List<StatementAnalyser> lastStatementsOfSubBlocks = statementAnalyser.lastStatementsOfNonEmptySubBlocks();
        for (StatementAnalyser subAnalyser : lastStatementsOfSubBlocks) {
            StatementAnalysis subStatementAnalysis = subAnalyser.getStatementAnalysis();
            FlowData flowData = subStatementAnalysis.flowData();
            if (!flowData.interruptsFlowIsSet()) {
                log(Logger.LogTarget.DELAYED, "Delaying interrupts flow, sub-statement {} has no interruptsFlow yet",
                        subAnalyser.index());
                CausesOfDelay delays = flowData.interruptsFlow.getFirst().causesOfDelay();
                interruptsFlow.setFirst(delays);
                return delays;
            }
            Map<InterruptsFlow, DV> subInterrupts = flowData.interruptsFlow.get();
            if (subInterrupts.isEmpty()) {
                // in this sub-block, there are no interrupts...
                if (flowData.blockExecution.isFirst()) {
                    CausesOfDelay delays = flowData.blockExecution.getFirst().causesOfDelay();
                    interruptsFlow.setFirst(delays);
                    log(Logger.LogTarget.DELAYED, "Delaying interrupts flow, received DELAYED_EXECUTION from sub-statement {} execution",
                            subAnalyser.index());
                    return delays;
                }
                builder.put(NO, flowData.blockExecution.get());
            } else for (Map.Entry<InterruptsFlow, DV> entry : subInterrupts.entrySet()) {
                InterruptsFlow i = entry.getKey();
                DV e = entry.getValue();
                if (e.isDelayed()) {
                    log(Logger.LogTarget.DELAYED, "Delaying interrupts flow, received DELAYED_EXECUTION from sub-statement {} interruptsFlow",
                            subAnalyser.index());
                    interruptsFlow.setFirst(e.causesOfDelay());
                    return e.causesOfDelay();
                }
                // if we're a loop statement, we can accept the interrupt as being one for us (break, continue)
                if (rejectInterrupt(statement, i)) {
                    builder.merge(i, e, (a, b) -> b.max(a));
                }
                if (flowData.blockExecution.isFirst()) {
                    CausesOfDelay delays = flowData.blockExecution.getFirst().causesOfDelay();
                    interruptsFlow.setFirst(delays);
                    log(Logger.LogTarget.DELAYED, "Delaying interrupts flow, received DELAYED_EXECUTION from sub-statement {} execution",
                            subAnalyser.index());
                    return delays;
                }
                builder.merge(i, flowData.blockExecution.get(), (a, b) -> b.min(a));
            }
        }
        setInterruptsFlow(Map.copyOf(builder));
        return DONE;
    }

    private void setInterruptsFlow(Map<InterruptsFlow, DV> map) {
        if (!interruptsFlow.isSet() || !interruptsFlow.get().equals(map)) {
            interruptsFlow.set(map);
        }
    }

    public AnalysisStatus setBlockExecution(DV blockExecution) {
        if (blockExecution.isDone()) {
            if ((!this.blockExecution.isSet() || !this.blockExecution.get().equals(blockExecution))) {
                this.blockExecution.set(blockExecution);
            }
            return DONE;
        }
        this.blockExecution.setFirst(blockExecution.causesOfDelay());
        return blockExecution.causesOfDelay();
    }

    private static boolean rejectInterrupt(Statement statement, InterruptsFlow interruptsFlow) {
        if ((interruptsFlow.isBreak() || interruptsFlow.isContinue()) && statement instanceof LoopStatement) {
            String label = ((LoopStatement) statement).label;
            if (label == null) label = "";
            return label.equals(interruptsFlow.label());
        }
        if (interruptsFlow.isBreak() && statement instanceof SwitchEntry) {
            return interruptsFlow.label().isEmpty();
        }
        return true;
    }

}
