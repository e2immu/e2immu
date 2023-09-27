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
import org.e2immu.analyser.model.Expression;
import org.e2immu.support.SetOnceMap;

import java.util.Map;

/**
 * Flow is that part of the analysis that is concerned with reachability of statements,
 * and escaping from the normal sequential flow.
 * <p>
 * Flow analysis is never delayed.
 */
public interface FlowData {

    void makeUnreachable();

    void initialiseAssignmentIds(FlowData previous);

    void setInitialTime(int time, String index);

    void setTimeAfterEvaluation(int time, String index);

    void setTimeAfterSubBlocks(int time, String index);

    void copyTimeAfterExecutionFromInitialTime();

    void copyTimeAfterSubBlocksFromTimeAfterExecution();

    boolean initialTimeNotYetSet();

    boolean timeAfterExecutionNotYetSet();

    boolean timeAfterSubBlocksNotYetSet();

    int getInitialTime();

    int getTimeAfterEvaluation();

    int getTimeAfterSubBlocks();

    DV interruptStatus();

    InterruptsFlow bestAlwaysInterrupt();

    DV execution(DV statementsExecution);

    void setGuaranteedToBeReached(DV execution);

    void setGuaranteedToBeReachedInCurrentBlock(DV executionInBlock);

    void setGuaranteedToBeReachedInMethod(DV executionInMethod);

    DV getGuaranteedToBeReachedInCurrentBlock();

    DV getGuaranteedToBeReachedInMethod();

    boolean isUnreachable();

    // testing only
    Map<InterruptsFlow, DV> getInterruptsFlow();

    boolean interruptsFlowIsSet();

    /*
    at some point we need to make a distinction between different
     */
    boolean alwaysEscapesViaException();

    /**
     * Call occurs before the actual analysis of the statement.
     *
     * @param previousStatement previous statement; null if this is the first statement in a block
     * @param blockExecution    the execution value of the block, if this is the first statement in the block; otherwise, ALWAYS
     * @param state             current state of local condition manager, can be delayed
     */
    AnalysisStatus computeGuaranteedToBeReachedReturnUnreachable(StatementAnalysis previousStatement,
                                                                 DV blockExecution,
                                                                 Expression state,
                                                                 CausesOfDelay stateIsDelayed,
                                                                 CausesOfDelay localConditionManagerIsDelayed);

    AnalysisStatus analyseInterruptsFlow(StatementAnalyser statementAnalyser, StatementAnalysis previousStatement);


    AnalysisStatus setBlockExecution(DV blockExecution);

    AnalysisStatus interruptsFlowGetFirst();

    Map<InterruptsFlow, DV> interruptsFlowGet();

    DV interruptStatusToExecution();

    SetOnceMap<Integer, String> assignmentIdOfStatementTime();

    String assignmentIdOfStatementTimeGet(int i);

    boolean blockExecutionIsFirst();

    DV blockExecutionGet();

    CausesOfDelay blockExecutionGetFirst();

    void internalAllDoneCheck();
}
