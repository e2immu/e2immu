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
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.statement.BreakOrContinueStatement;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.model.variable.VariableNature;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.annotation.NotNull;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public interface StatementAnalysis extends Analysis,
        Comparable<StatementAnalysis>,
        HasNavigationData<StatementAnalysis>,
        LimitedStatementAnalysis {

    String fullyQualifiedName();

    List<StatementAnalysis> lastStatementsOfNonEmptySubBlocks();

    boolean atTopLevel();

    void ensure(Message newMessage);

    @NotNull
    VariableInfo getLatestVariableInfo(String variableName);

    List<VariableInfo> assignmentInfo(FieldInfo fieldInfo);

    List<VariableInfo> latestInfoOfVariablesReferringTo(FieldInfo fieldInfo);

    Stream<VariableInfo> streamOfLatestInfoOfVariablesReferringTo(FieldInfo fieldInfo);

    boolean containsMessage(Message.Label messageLabel);

    boolean containsMessage(Message message);

    void initIteration0(EvaluationContext evaluationContext, MethodInfo currentMethod, StatementAnalysis previous);

    void ensureMessages(Stream<Message> messageStream);

    boolean assignsToFields();

    boolean noIncompatiblePrecondition();

    Stream<Message> messageStream();

    /*
        create a variable, potentially even assign an initial value and a linked variables set.
        everything is written into the INITIAL level, assignmentId and readId are both NOT_YET...
         */
    VariableInfoContainer createVariable(EvaluationContext evaluationContext,
                                         Variable variable,
                                         int statementTime,
                                         VariableNature variableInLoop);

    StatementAnalysis mostEnclosingLoop();

    /*
        We've encountered a break or continue statement, and need to find the corresponding loop...
         */
    FindLoopResult findLoopByLabel(BreakOrContinueStatement breakOrContinue);

    Expression initialValueOfReturnVariable(@NotNull Variable variable);

    VariableInfo findOrNull(@NotNull Variable variable, VariableInfoContainer.Level level);

    VariableInfoContainer findOrNull(@NotNull Variable variable);

    VariableInfo findOrThrow(@NotNull Variable variable);

    boolean isLocalVariableAndLocalToThisBlock(String variableName);

    VariableInfoContainer findForWriting(@NotNull String variableName);

    /*
        will cause errors if variable does not exist yet!
        before you write, you'll have to ensureEvaluation
         */
    VariableInfoContainer findForWriting(@NotNull Variable variable);

    Stream<VariableInfo> variableStream();

    Stream<Map.Entry<String, VariableInfoContainer>> rawVariableStream();

    Stream<Map.Entry<String, VariableInfoContainer>> variableEntryStream(VariableInfoContainer.Level level);

    Expression notNullValuesAsExpression(EvaluationContext evaluationContext);

    NavigationData<StatementAnalysis> navigationData();

    FlowData flowData();

    MethodLevelData methodLevelData();

    StatementAnalysis navigateTo(String target);

    StatementAnalysis startOfBlockStatementAnalysis(int i);

    VariableInfoContainer getVariable(String fullyQualifiedName);

    VariableInfoContainer getVariableOrDefaultNull(String fullyQualifiedName);

    boolean variableIsSet(String fullyQualifiedName);

    StateData stateData();

    RangeData rangeData();

    boolean inSyncBlock();

    int statementTime(VariableInfoContainer.Level merge);

    MethodAnalysis methodAnalysis();

    int numberOfVariables();

    Primitives primitives();

    int statementTimeForVariable(AnalyserContext analyserContext, Variable variable, int newStatementTime);

    void initIteration1Plus(EvaluationContext evaluationContext, StatementAnalysis previous);

    Stream<Variable> candidateVariablesForNullPtrWarningStream();


    record FindLoopResult(StatementAnalysis statementAnalysis, int steps) {
    }

    DV isEscapeAlwaysExecutedInCurrentBlock();
     Variable obtainLoopVar();
    CausesOfDelay evaluationOfForEachVariable(Variable loopVar,
                                     Expression evaluatedIterable,
                                     CausesOfDelay someValueWasDelayed,
                                     EvaluationContext evaluationContext);
    void potentiallyRaiseErrorsOnNotNullInContext(Map<Variable, EvaluationResult.ChangeData> changeDataMap);
    void potentiallyRaiseNullPointerWarningENN();
    CausesOfDelay applyPrecondition(Precondition precondition,
                                    EvaluationContext evaluationContext,
                                    ConditionManager localConditionManager);
    void ensureVariables(EvaluationContext evaluationContext,
                         Variable variable,
                         EvaluationResult.ChangeData changeData,
                         int newStatementTime);
    void addToAssignmentsInLoop(VariableInfoContainer vic, String fullyQualifiedName);
}
