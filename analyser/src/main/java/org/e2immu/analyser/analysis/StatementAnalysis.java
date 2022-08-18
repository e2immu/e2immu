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
import org.e2immu.analyser.analyser.util.VariableAccessReport;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.statement.BreakOrContinueStatement;
import org.e2immu.analyser.model.variable.DependentVariable;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.model.variable.VariableNature;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.annotation.Modified;
import org.e2immu.annotation.NotNull;
import org.e2immu.support.Either;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public interface StatementAnalysis extends Analysis,
        Comparable<StatementAnalysis>,
        HasNavigationData<StatementAnalysis>,
        LimitedStatementAnalysis {

    @NotNull
    String fullyQualifiedName();

    List<StatementAnalysis> lastStatementsOfNonEmptySubBlocks();

    boolean atTopLevel();

    void ensure(Message newMessage);

    @NotNull
    VariableInfo getLatestVariableInfo(String variableName);

    List<VariableInfo> latestInfoOfVariablesReferringTo(FieldInfo fieldInfo);

    Stream<VariableInfo> streamOfLatestInfoOfVariablesReferringTo(FieldInfo fieldInfo);

    boolean containsMessage(Message.Label messageLabel);

    boolean containsMessage(Message message);

    void initIteration0(EvaluationContext evaluationContext, MethodInfo currentMethod, StatementAnalysis previous);

    void ensureMessages(Stream<Message> messageStream);

    boolean assignsToFields();

    boolean noIncompatiblePrecondition();

    @NotNull
    Stream<Message> messageStream();

    /*
        create a variable, potentially even assign an initial value and a linked variables set.
        everything is written into the INITIAL level, assignmentId and readId are both NOT_YET...
         */
    @Modified
    @NotNull
    VariableInfoContainer createVariable(EvaluationContext evaluationContext,
                                         Variable variable,
                                         int statementTime,
                                         VariableNature variableInLoop);

    StatementAnalysis mostEnclosingLoop();

    /*
        We've encountered a break or continue statement, and need to find the corresponding loop...
         */
    FindLoopResult findLoopByLabel(BreakOrContinueStatement breakOrContinue);

    VariableInfo findOrNull(@NotNull Variable variable, Stage level);

    VariableInfoContainer findOrNull(@NotNull Variable variable);

    VariableInfo findOrThrow(@NotNull Variable variable);

    boolean isLocalVariableAndLocalToThisBlock(String variableName);

    VariableInfoContainer findForWriting(@NotNull String variableName);

    /*
        will cause errors if variable does not exist yet!
        before you write, you'll have to ensureEvaluation
         */
    VariableInfoContainer findForWriting(@NotNull Variable variable);

    @NotNull(content = true)
    Stream<VariableInfo> variableStream();

    @NotNull(content = true)
    Stream<Map.Entry<String, VariableInfoContainer>> rawVariableStream();

    @NotNull(content = true)
    Stream<Map.Entry<String, VariableInfoContainer>> variableEntryStream(Stage level);

    Expression notNullValuesAsExpression(EvaluationContext evaluationContext);

    @NotNull
    NavigationData<StatementAnalysis> navigationData();

    @NotNull
    FlowData flowData();

    @NotNull
    MethodLevelData methodLevelData();

    StatementAnalysis navigateTo(String target);

    StatementAnalysis startOfBlockStatementAnalysis(int i);

    VariableInfoContainer getVariable(String fullyQualifiedName);

    VariableInfoContainer getVariableOrDefaultNull(String fullyQualifiedName);

    boolean variableIsSet(String fullyQualifiedName);

    @NotNull
    StateData stateData();

    @NotNull
    RangeData rangeData();

    boolean inSyncBlock();

    int statementTime(Stage merge);

    @NotNull
    MethodAnalysis methodAnalysis();

    int numberOfVariables();

    Primitives primitives();

    void initIteration1Plus(EvaluationContext evaluationContext, StatementAnalysis previous);

    Stream<Variable> candidateVariablesForNullPtrWarningStream();

    void setVariableAccessReportOfSubAnalysers(VariableAccessReport variableAccessReport);

    List<Variable> variablesReadBySubAnalysers();

    Stream<Map.Entry<Variable, Properties>> propertiesFromSubAnalysers();

    default String propertiesFromSubAnalysersSortedToString() {
        return propertiesFromSubAnalysers()
                .map(e -> e.getKey().simpleName() + "={" + e.getValue().sortedToString() + "}")
                .sorted().collect(Collectors.joining(", "));
    }

    boolean havePropertiesFromSubAnalysers();

    boolean latestDelay(CausesOfDelay delay);

    boolean inLoop();

    // can we still reach index?
    boolean isStillReachable(String index);

    DependentVariable searchInEquivalenceGroupForLatestAssignment(EvaluationContext evaluationContext,
                                                                  DependentVariable variable,
                                                                  Expression arrayValue,
                                                                  Expression indexValue,
                                                                  ForwardEvaluationInfo forwardEvaluationInfo);

    void makeUnreachable();

    Either<CausesOfDelay, Set<Variable>> recursivelyLinkedToParameterOrField(AnalyserContext analyserContext,
                                                                             Variable v,
                                                                             boolean cnnTravelsToFields);

    boolean isBrokeDelay();
    void setBrokeDelay();

    record FindLoopResult(StatementAnalysis statementAnalysis, int steps) {
    }

    DV isReturnOrEscapeAlwaysExecutedInCurrentBlock(boolean escapeOnly);

    DV isEscapeAlwaysExecutedInCurrentBlock();

    Variable obtainLoopVar();

    EvaluationResult evaluationOfForEachVariable(Variable loopVar,
                                                 Expression evaluatedIterable,
                                                 CausesOfDelay someValueWasDelayed,
                                                 EvaluationResult evaluationResult);

    void potentiallyRaiseErrorsOnNotNullInContext(AnalyserContext analyserContext,
                                                  Map<Variable, EvaluationResult.ChangeData> changeDataMap);

    void potentiallyRaiseNullPointerWarningENN();

    // return progress
    boolean applyPrecondition(Precondition precondition,
                              EvaluationContext evaluationContext,
                              ConditionManager localConditionManager);

    void ensureVariable(EvaluationContext evaluationContext,
                        Variable variable,
                        EvaluationResult.ChangeData changeData,
                        int newStatementTime);

    // return progress
    boolean addToAssignmentsInLoop(VariableInfoContainer vic, String fullyQualifiedName);
}
