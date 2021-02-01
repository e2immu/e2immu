/*
 * e2immu: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.e2immu.analyser.analyser;

import com.google.common.collect.ImmutableMap;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.Statement;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.model.statement.*;
import org.e2immu.analyser.util.Logger;
import org.e2immu.analyser.util.SetOnce;
import org.e2immu.analyser.util.SetOnceMap;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.e2immu.analyser.analyser.AnalysisStatus.DELAYS;
import static org.e2immu.analyser.analyser.AnalysisStatus.DONE;
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
    private final SetOnce<Execution> guaranteedToBeReachedInCurrentBlock = new SetOnce<>();
    private final SetOnce<Execution> guaranteedToBeReachedInMethod = new SetOnce<>();
    // execution of the block
    public final SetOnce<Execution> blockExecution = new SetOnce<>();
    // are there any statements in sub-blocks that interrupt the flow?
    private final SetOnce<Map<InterruptsFlow, Execution>> interruptsFlow = new SetOnce<>();

    // counts all increases in statement time
    private final SetOnce<Integer> initialTime = new SetOnce<>(); // STEP 1
    private final SetOnce<Integer> timeAfterEvaluation = new SetOnce<>(); // STEP 3
    private final SetOnce<Integer> timeAfterSubBlocks = new SetOnce<>(); // STEP 4

    public final SetOnceMap<Integer, String> assignmentIdOfStatementTime = new SetOnceMap<>();

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

    public boolean initialTimeIsSet() {
        return initialTime.isSet();
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

    public Execution interruptStatus() {
        // what is the worst that can happen? ESCAPE-ALWAYS
        if (!interruptsFlowIsSet()) return Execution.DELAYED_EXECUTION;
        return interruptsFlow.get().values().stream().reduce(Execution.NEVER, Execution::best);
    }

    public InterruptsFlow bestAlwaysInterrupt() {
        if (!interruptsFlowIsSet()) return DELAYED;
        return interruptsFlow.get().entrySet().stream().filter(e -> e.getValue() == Execution.ALWAYS)
                .map(Map.Entry::getKey).reduce(NO, InterruptsFlow::best);
    }

    public Execution execution(Execution statementsExecution) {
        // combine with guaranteed to be reached in block
        FlowData.Execution execution = guaranteedToBeReachedInMethod.getOrElse(Execution.DELAYED_EXECUTION);
        return execution.worst(statementsExecution);
    }

    public void setGuaranteedToBeReached(Execution execution) {
        setGuaranteedToBeReachedInCurrentBlock(execution);
        setGuaranteedToBeReachedInMethod(execution);
    }

    public void setGuaranteedToBeReachedInCurrentBlock(Execution executionInBlock) {
        if (executionInBlock != Execution.DELAYED_EXECUTION) {
            if ((!guaranteedToBeReachedInCurrentBlock.isSet() || !guaranteedToBeReachedInCurrentBlock.get().equals(executionInBlock))) {
                guaranteedToBeReachedInCurrentBlock.set(executionInBlock);
            }
        }
    }

    public void setGuaranteedToBeReachedInMethod(Execution executionInMethod) {
        if (executionInMethod != Execution.DELAYED_EXECUTION) {
            if (!guaranteedToBeReachedInMethod.isSet() || !guaranteedToBeReachedInMethod.get().equals(executionInMethod)) {
                guaranteedToBeReachedInMethod.set(executionInMethod);
            }
        }
    }

    public Execution getGuaranteedToBeReachedInCurrentBlock() {
        return guaranteedToBeReachedInCurrentBlock.getOrElse(Execution.DELAYED_EXECUTION);
    }

    public Execution getGuaranteedToBeReachedInMethod() {
        return guaranteedToBeReachedInMethod.getOrElse(Execution.DELAYED_EXECUTION);
    }

    public boolean isUnreachable() {
        return guaranteedToBeReachedInMethod.isSet() && guaranteedToBeReachedInMethod.get() == Execution.NEVER;
    }

    public Map<InterruptsFlow, Execution> getInterruptsFlow() {
        return interruptsFlow.getOrElse(null);
    }

    public boolean interruptsFlowIsSet() {
        return interruptsFlow.isSet();
    }

    /*
    at some point we need to make a distinction between different
     */
    public boolean escapesViaException() {
        return interruptsFlow.isSet() && interruptsFlow.get().containsKey(ESCAPE);
    }

    public enum Execution {
        DEFAULT(3), // only local data transfer from SwitchEntry or IfElseStatement

        ALWAYS(2), CONDITIONALLY(1), NEVER(0),

        DELAYED_EXECUTION(-1); // don't know yet
        final int level;

        Execution(int level) {
            this.level = level;
        }

        Execution worst(Execution other) {
            return level <= other.level ? this : other;
        }

        public Execution complement() {
            if (this == ALWAYS) return NEVER;
            if (this == NEVER) return ALWAYS;
            return this;
        }

        public Execution best(Execution other) {
            if (this == DELAYED_EXECUTION || other == DELAYED_EXECUTION) return DELAYED_EXECUTION;
            return level >= other.level ? this : other;
        }
    }

    /**
     * Call occurs before the actual analysis of the statement.
     *
     * @param previousStatement previous statement; null if this is the first statement in a block
     * @param blockExecution    the execution value of the block, if this is the first statement in the block; otherwise, ALWAYS
     * @param state             current state of local condition manager, can be delayed
     * @return true when unreachable statement
     */
    public boolean computeGuaranteedToBeReachedReturnUnreachable(StatementAnalysis previousStatement,
                                                                 Execution blockExecution,
                                                                 Expression state,
                                                                 boolean stateIsDelayed) {
        // some statements that need executing independently of delays
        if (previousStatement == null) {
            // start of a block is always reached in that block
            setGuaranteedToBeReachedInCurrentBlock(Execution.ALWAYS);
        } else if (previousStatement.flowData.getGuaranteedToBeReachedInMethod() == Execution.NEVER) {
            setGuaranteedToBeReachedInCurrentBlock(Execution.NEVER);
            setGuaranteedToBeReachedInMethod(Execution.NEVER);
            return false; // no more errors
        }

        if (stateIsDelayed) {
            log(Logger.LogTarget.DELAYED, "Delaying guaranteed to be reached, no value state");
            return false;
        }

        if (guaranteedToBeReachedInMethod.isSet()) {
            return false; // already done!
        }

        if (previousStatement == null) {
            // start of a block, within method
            setGuaranteedToBeReachedInMethod(blockExecution);
            return false;
        }

        // look at the previous statement in the block, there are no delays

        Execution prev = previousStatement.flowData.getGuaranteedToBeReachedInCurrentBlock();
        // ALWAYS = always interrupted, NEVER = never interrupted, CONDITIONALLY = potentially interrupted
        Execution interrupt = previousStatement.flowData.interruptStatus().complement();
        Execution execBasedOnState = state.isBoolValueFalse() ? Execution.NEVER : Execution.ALWAYS;
        Execution executionInCurrentBlock = prev.worst(interrupt).worst(execBasedOnState);

        setGuaranteedToBeReachedInCurrentBlock(executionInCurrentBlock);
        setGuaranteedToBeReachedInMethod(executionInCurrentBlock.worst(blockExecution));
        return executionInCurrentBlock == Execution.NEVER; // raise error when NEVER by returning true
    }

    public AnalysisStatus analyse(StatementAnalyser statementAnalyser, StatementAnalysis previousStatement, Execution blockExecution) {
        AnalysisStatus analysisStatus = setBlockExecution(blockExecution);

        Statement statement = statementAnalyser.statement();
        // without a block
        if (statement instanceof ReturnStatement) {
            setInterruptsFlow(Map.of(RETURN, Execution.ALWAYS));
            return DONE.combine(analysisStatus);
        }
        if (statement instanceof ThrowStatement) {
            setInterruptsFlow(Map.of(ESCAPE, Execution.ALWAYS));
            return DONE.combine(analysisStatus);
        }
        if (statement instanceof BreakStatement) {
            setInterruptsFlow(Map.of(InterruptsFlow.createBreak(((BreakStatement) statement).label), Execution.ALWAYS));
            return DONE.combine(analysisStatus);
        }
        if (statement instanceof ContinueStatement) {
            setInterruptsFlow(Map.of(InterruptsFlow.createContinue(((ContinueStatement) statement).label), Execution.ALWAYS));
            return DONE.combine(analysisStatus);
        }
        // in case there is no explicit return statement at the end of the method...
        // this one is probably completely irrelevant
        boolean endOfBlockTopLevel = statementAnalyser.statementAnalysis.atTopLevel() &&
                statementAnalyser.navigationData.next.get().isEmpty();
        if (endOfBlockTopLevel) {
            setInterruptsFlow(Map.of(RETURN, Execution.ALWAYS));
            return DONE.combine(analysisStatus);
        }

        if (previousStatement != null && !previousStatement.flowData.interruptsFlowIsSet()) {
            log(Logger.LogTarget.DELAYED, "Delaying interrupts flow, previous statement {} has no interruptsFlow yet",
                    previousStatement.index());
            return DELAYS;
        }

        // situation from the previous statement
        Map<InterruptsFlow, Execution> builder = new HashMap<>(previousStatement == null ? Map.of() :
                previousStatement.flowData.interruptsFlow.get());

        List<StatementAnalyser> lastStatementsOfSubBlocks = statementAnalyser.lastStatementsOfNonEmptySubBlocks();
        for (StatementAnalyser subAnalyser : lastStatementsOfSubBlocks) {
            if (!subAnalyser.statementAnalysis.flowData.interruptsFlowIsSet()) {
                log(Logger.LogTarget.DELAYED, "Delaying interrupts flow, substatement {} has no interruptsFlow yet",
                        subAnalyser.index());
                return DELAYS;
            }
            for (Map.Entry<InterruptsFlow, Execution> entry : subAnalyser.statementAnalysis.flowData.interruptsFlow.get().entrySet()) {
                InterruptsFlow i = entry.getKey();
                Execution e = entry.getValue();
                if (e == Execution.DELAYED_EXECUTION) {
                    log(Logger.LogTarget.DELAYED, "Delaying interrupts flow, received DELAYED_EXECUTION from sub statement {} interruptsFlow",
                            subAnalyser.index());
                    return DELAYS;
                }
                // if we're a loop statement, we can accept the interrupt as being one for us (break, continue)
                if (rejectInterrupt(statement, i)) {
                    builder.merge(i, e, (a, b) -> b.best(a));
                }
                Execution subAnalyserExecution = subAnalyser.statementAnalysis.flowData.blockExecution.getOrElse(Execution.DELAYED_EXECUTION);
                if (subAnalyserExecution == Execution.DELAYED_EXECUTION) {
                    log(Logger.LogTarget.DELAYED, "Delaying interrupts flow, received DELAYED_EXECUTION from sub statement {} execution",
                            subAnalyser.index());
                    return DELAYS;
                }
                builder.merge(i, subAnalyserExecution, (a, b) -> b.worst(a));
            }
        }
        setInterruptsFlow(ImmutableMap.copyOf(builder));
        return DONE.combine(analysisStatus);
    }

    private void setInterruptsFlow(Map<InterruptsFlow, Execution> map) {
        if (!interruptsFlow.isSet() || !interruptsFlow.get().equals(map)) {
            interruptsFlow.set(map);
        }
    }

    private AnalysisStatus setBlockExecution(Execution blockExecution) {
        if (blockExecution != Execution.DELAYED_EXECUTION) {
            if ((!this.blockExecution.isSet() || !this.blockExecution.get().equals(blockExecution))) {
                this.blockExecution.set(blockExecution);
            }
            return DONE;
        }
        return DELAYS;
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
