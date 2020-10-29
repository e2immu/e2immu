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
import org.e2immu.analyser.model.Statement;
import org.e2immu.analyser.model.StatementAnalysis;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.statement.*;
import org.e2immu.analyser.model.value.BoolValue;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.SetOnce;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.e2immu.analyser.analyser.AnalysisStatus.DONE;
import static org.e2immu.analyser.analyser.InterruptsFlow.*;

/**
 * Flow is that part of the analysis that is concerned with reachability of statements,
 * and escaping from the normal sequential flow.
 * <p>
 * Flow analysis is never delayed.
 */
public class FlowData {

    public final SetOnce<Execution> guaranteedToBeReachedInCurrentBlock = new SetOnce<>();
    public final SetOnce<Execution> guaranteedToBeReachedInMethod = new SetOnce<>();

    public final SetOnce<Map<InterruptsFlow, Execution>> interruptsFlow = new SetOnce<>();
    public final SetOnce<Execution> blockExecution = new SetOnce<>();

    public Execution interruptStatus() {
        // what is the worst that can happen? ESCAPE-ALWAYS
        return interruptsFlow.get().values().stream().reduce(Execution.NEVER, Execution::best);
    }

    public InterruptsFlow bestAlwaysInterrupt() {
        return interruptsFlow.get().entrySet().stream().filter(e -> e.getValue() == Execution.ALWAYS)
                .map(Map.Entry::getKey).reduce(NO, InterruptsFlow::best);
    }

    public Execution execution(boolean statementsExecutedAtLeastOnce) {
        FlowData.Execution executionBlock = statementsExecutedAtLeastOnce ? FlowData.Execution.ALWAYS : FlowData.Execution.CONDITIONALLY;
        // combine with guaranteed to be reached in block
        FlowData.Execution execution = guaranteedToBeReachedInMethod.get();
        return execution.worst(executionBlock);
    }


    public enum Execution {
        ALWAYS(2), CONDITIONALLY(1), NEVER(0);
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
            return level >= other.level ? this : other;
        }
    }

    /**
     * Call occurs before the actual analysis of the statement.
     *
     * @param previousStatement previous statement; null if this is the first statement in a block
     * @param blockExecution    the execution value of the block, if this is the first statement in the block; otherwise, ALWAYS
     * @param state
     * @return true when unreachable statement
     */
    public boolean computeGuaranteedToBeReachedReturnUnreachable(Primitives primitives,
                                                                 StatementAnalysis previousStatement,
                                                                 Execution blockExecution,
                                                                 Value state) {
        if (previousStatement == null) {
            // start of a block
            guaranteedToBeReachedInCurrentBlock.set(Execution.ALWAYS);
            guaranteedToBeReachedInMethod.set(blockExecution);
            return false;
        }
        if (previousStatement.flowData.guaranteedToBeReachedInCurrentBlock.get() == Execution.NEVER) {
            guaranteedToBeReachedInCurrentBlock.set(Execution.NEVER);
            guaranteedToBeReachedInMethod.set(Execution.NEVER);
            return false; // no more errors
        }

        Execution prev = previousStatement.flowData.guaranteedToBeReachedInCurrentBlock.get();
        // ALWAYS = always interrupted, NEVER = never interrupted, CONDITIONALLY = potentially interrupted
        Execution interrupt = previousStatement.flowData.interruptStatus().complement();
        Execution execBasedOnState = state.equals(BoolValue.createFalse(primitives)) ? Execution.NEVER : Execution.ALWAYS;
        Execution executionInCurrentBlock = prev.worst(interrupt).worst(execBasedOnState);

        guaranteedToBeReachedInCurrentBlock.set(executionInCurrentBlock);
        guaranteedToBeReachedInMethod.set(executionInCurrentBlock.worst(blockExecution));
        return executionInCurrentBlock == Execution.NEVER; // raise error when NEVER by returning true
    }

    public AnalysisStatus analyse(StatementAnalyser statementAnalyser, StatementAnalysis previousStatement, Execution blockExecution) {
        this.blockExecution.set(blockExecution);

        Statement statement = statementAnalyser.statement();
        // without a block
        if (statement instanceof ReturnStatement) {
            interruptsFlow.set(Map.of(RETURN, Execution.ALWAYS));
            return DONE;
        }
        if (statement instanceof ThrowStatement) {
            interruptsFlow.set(Map.of(ESCAPE, Execution.ALWAYS));
            return DONE;
        }
        if (statement instanceof BreakStatement) {
            interruptsFlow.set(Map.of(InterruptsFlow.createBreak(((BreakStatement) statement).label), Execution.ALWAYS));
            return DONE;
        }
        if (statement instanceof ContinueStatement) {
            interruptsFlow.set(Map.of(InterruptsFlow.createContinue(((ContinueStatement) statement).label), Execution.ALWAYS));
            return DONE;
        }
        // in case there is no explicit return statement at the end of the method...
        // this one is probably completely irrelevant
        boolean endOfBlockTopLevel = statementAnalyser.statementAnalysis.atTopLevel() &&
                statementAnalyser.navigationData.next.get().isEmpty();
        if (endOfBlockTopLevel) {
            interruptsFlow.set(Map.of(RETURN, Execution.ALWAYS));
            return DONE;
        }

        // situation from the previous statement
        Map<InterruptsFlow, Execution> builder = new HashMap<>(previousStatement == null ? Map.of() :
                previousStatement.flowData.interruptsFlow.get());

        List<StatementAnalyser> lastStatementsOfSubBlocks = statementAnalyser.lastStatementsOfSubBlocks();
        for (StatementAnalyser subAnalyser : lastStatementsOfSubBlocks) {

            for (Map.Entry<InterruptsFlow, Execution> entry : subAnalyser.statementAnalysis.flowData.interruptsFlow.get().entrySet()) {
                InterruptsFlow i = entry.getKey();
                Execution e = entry.getValue();

                // if we're a loop statement, we can accept the interrupt as being one for us (break, continue)
                if (rejectInterrupt(statement, i)) {
                    builder.merge(i, e, (a, b) -> b.best(a));
                }
                builder.merge(i, subAnalyser.statementAnalysis.flowData.blockExecution.get(), (a, b) -> b.worst(a));
            }
        }
        this.interruptsFlow.set(ImmutableMap.copyOf(builder));
        return DONE;
    }

    private static boolean rejectInterrupt(Statement statement, InterruptsFlow interruptsFlow) {
        if ((interruptsFlow.isBreak() || interruptsFlow.isContinue()) && statement instanceof LoopStatement) {
            String label = ((LoopStatement) statement).label;
            if (label == null) label = "";
            return label.equals(interruptsFlow.label);
        }
        if (interruptsFlow.isBreak() && statement instanceof SwitchEntry) {
            return interruptsFlow.label.isEmpty();
        }
        return true;
    }

}
