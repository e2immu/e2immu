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

import static org.e2immu.analyser.analyser.Property.CONTEXT_MODIFIED;

public interface StatementAnalysis extends Analysis,
        Comparable<StatementAnalysis>,
        HasNavigationData<StatementAnalysis>,
        LimitedStatementAnalysis {

    @NotNull
    default String fullyQualifiedName() {
        throw new UnsupportedOperationException();
    }

    default List<StatementAnalysis> lastStatementsOfNonEmptySubBlocks() {
        throw new UnsupportedOperationException();
    }

    default boolean atTopLevel() {
        throw new UnsupportedOperationException();
    }

    default void ensure(Message newMessage) {
        throw new UnsupportedOperationException();
    }

    @NotNull
    default VariableInfo getLatestVariableInfo(String variableName) {
        throw new UnsupportedOperationException();
    }

    default List<VariableInfo> latestInfoOfVariablesReferringTo(FieldInfo fieldInfo) {
        throw new UnsupportedOperationException();
    }

    default Stream<VariableInfo> streamOfLatestInfoOfVariablesReferringTo(FieldInfo fieldInfo) {
        throw new UnsupportedOperationException();
    }

    default boolean containsMessage(Message.Label messageLabel) {
        throw new UnsupportedOperationException();
    }

    default boolean containsMessage(Message message) {
        throw new UnsupportedOperationException();
    }

    default void initIteration0(EvaluationContext evaluationContext, MethodInfo currentMethod, StatementAnalysis previous) {
        throw new UnsupportedOperationException();
    }

    default void ensureMessages(Stream<Message> messageStream) {
        throw new UnsupportedOperationException();
    }

    default boolean assignsToFields() {
        throw new UnsupportedOperationException();
    }

    default boolean noIncompatiblePrecondition() {
        throw new UnsupportedOperationException();
    }

    @NotNull
    default Stream<Message> messageStream() {
        throw new UnsupportedOperationException();
    }

    /*
        create a variable, potentially even assign an initial value and a linked variables set.
        everything is written into the INITIAL level, assignmentId and readId are both NOT_YET...
         */
    @Modified
    @NotNull
    default VariableInfoContainer createVariable(EvaluationContext evaluationContext,
                                                 Variable variable,
                                                 int statementTime,
                                                 VariableNature variableInLoop) {
        throw new UnsupportedOperationException();
    }

    default StatementAnalysis mostEnclosingLoop() {
        throw new UnsupportedOperationException();
    }

    /*
        We've encountered a break or continue statement, and need to find the corresponding loop...
         */
    default FindLoopResult findLoopByLabel(BreakOrContinueStatement breakOrContinue) {
        throw new UnsupportedOperationException();
    }

    default VariableInfo findOrNull(@NotNull Variable variable, Stage level) {
        throw new UnsupportedOperationException();
    }

    default VariableInfoContainer findOrNull(@NotNull Variable variable) {
        throw new UnsupportedOperationException();
    }

    default VariableInfo findOrThrow(@NotNull Variable variable) {
        throw new UnsupportedOperationException();
    }

    default boolean isLocalVariableAndLocalToThisBlock(String variableName) {
        throw new UnsupportedOperationException();
    }

    /*
        will cause errors if variable does not exist yet!
        before you write, you'll have to ensureEvaluation
         */
    default VariableInfoContainer findForWriting(@NotNull Variable variable) {
        throw new UnsupportedOperationException();
    }

    @NotNull(content = true)
    default Stream<VariableInfo> variableStream() {
        throw new UnsupportedOperationException();
    }

    @NotNull(content = true)
    default Stream<Map.Entry<String, VariableInfoContainer>> rawVariableStream() {
        throw new UnsupportedOperationException();
    }

    @NotNull(content = true)
    default Stream<Map.Entry<String, VariableInfoContainer>> variableEntryStream(Stage level) {
        throw new UnsupportedOperationException();
    }

    @NotNull(content = true)
    default Stream<VariableInfoContainer> variableInfoContainerStream() {
        throw new UnsupportedOperationException();
    }

    default Expression notNullValuesAsExpression(EvaluationContext evaluationContext) {
        throw new UnsupportedOperationException();
    }

    @NotNull
    default NavigationData<StatementAnalysis> navigationData() {
        throw new UnsupportedOperationException();
    }

    @NotNull
    default FlowData flowData() {
        throw new UnsupportedOperationException();
    }

    @NotNull
    default MethodLevelData methodLevelData() {
        throw new UnsupportedOperationException();
    }

    default StatementAnalysis navigateTo(String target) {
        throw new UnsupportedOperationException();
    }

    default StatementAnalysis startOfBlockStatementAnalysis(int i) {
        throw new UnsupportedOperationException();
    }

    default VariableInfoContainer getVariable(String fullyQualifiedName) {
        throw new UnsupportedOperationException();
    }

    default VariableInfoContainer getVariableOrDefaultNull(String fullyQualifiedName) {
        throw new UnsupportedOperationException();
    }

    default boolean variableIsSet(String fullyQualifiedName) {
        throw new UnsupportedOperationException();
    }

    @NotNull
    default StateData stateData() {
        throw new UnsupportedOperationException();
    }

    @NotNull
    default RangeData rangeData() {
        throw new UnsupportedOperationException();
    }

    default boolean inSyncBlock() {
        throw new UnsupportedOperationException();
    }

    default int statementTime(Stage merge) {
        throw new UnsupportedOperationException();
    }

    @NotNull
    default MethodAnalysis methodAnalysis() {
        throw new UnsupportedOperationException();
    }

    default int numberOfVariables() {
        throw new UnsupportedOperationException();
    }

    default Primitives primitives() {
        throw new UnsupportedOperationException();
    }

    default void initIteration1Plus(EvaluationContext evaluationContext, StatementAnalysis previous) {
        throw new UnsupportedOperationException();
    }

    default Stream<Variable> candidateVariablesForNullPtrWarningStream() {
        throw new UnsupportedOperationException();
    }

    default void setVariableAccessReportOfSubAnalysers(VariableAccessReport variableAccessReport) {
        throw new UnsupportedOperationException();
    }

    default List<Variable> variablesReadBySubAnalysers() {
        throw new UnsupportedOperationException();
    }

    default Stream<Map.Entry<Variable, Properties>> propertiesFromSubAnalysers() {
        throw new UnsupportedOperationException();
    }

    default String propertiesFromSubAnalysersSortedToString() {
        return propertiesFromSubAnalysers()
                .map(e -> e.getKey().simpleName() + "={" + e.getValue().sortedToString() + "}")
                .sorted().collect(Collectors.joining(", "));
    }

    default boolean havePropertiesFromSubAnalysers() {
        throw new UnsupportedOperationException();
    }

    default boolean latestDelay(CausesOfDelay delay) {
        throw new UnsupportedOperationException();
    }

    default boolean inLoop() {
        throw new UnsupportedOperationException();
    }

    // can we still reach index?
    default boolean isStillReachable(String index) {
        throw new UnsupportedOperationException();
    }

    default DependentVariable searchInEquivalenceGroupForLatestAssignment(EvaluationContext evaluationContext,
                                                                          DependentVariable variable,
                                                                          Expression arrayValue,
                                                                          Expression indexValue,
                                                                          ForwardEvaluationInfo forwardEvaluationInfo) {
        throw new UnsupportedOperationException();
    }

    default void makeUnreachable() {
        throw new UnsupportedOperationException();
    }

    default Either<CausesOfDelay, Set<Variable>> recursivelyLinkedToParameterOrField(AnalyserContext analyserContext,
                                                                                     Variable v,
                                                                                     boolean cnnTravelsToFields) {
        throw new UnsupportedOperationException();
    }

    default boolean isBrokeDelay() {
        throw new UnsupportedOperationException();
    }

    default void setBrokeDelay() {
        throw new UnsupportedOperationException();
    }

    default DV variableHasBeenModified(Variable variable) {
        VariableInfoContainer vic = getVariable(variable.fullyQualifiedName());
        VariableInfo vi = vic.getPreviousOrInitial();
        return vi.getProperty(CONTEXT_MODIFIED);
    }

    default boolean recursivelyContainedIn(Expression expression, Variable variable) {
        throw new UnsupportedOperationException();
    }

    record FindLoopResult(StatementAnalysis statementAnalysis, int steps, boolean isLoop) {
    }

    default DV isReturnOrEscapeAlwaysExecutedInCurrentBlock(boolean escapeOnly) {
        throw new UnsupportedOperationException();
    }

    default DV isEscapeAlwaysExecutedInCurrentBlock() {
        throw new UnsupportedOperationException();
    }

    default Variable obtainLoopVar() {
        throw new UnsupportedOperationException();
    }

    default EvaluationResult evaluationOfForEachVariable(Variable loopVar,
                                                         EvaluationResult evaluatedIterable,
                                                         CausesOfDelay someValueWasDelayed,
                                                         EvaluationResult evaluationResult) {
        throw new UnsupportedOperationException();
    }

    default void potentiallyRaiseErrorsOnNotNullInContext(AnalyserContext analyserContext,
                                                          Map<Variable, ChangeData> changeDataMap) {
        throw new UnsupportedOperationException();
    }

    default void potentiallyRaiseNullPointerWarningENN() {
        throw new UnsupportedOperationException();
    }

    // return progress
    default boolean applyPrecondition(Precondition precondition,
                                      EvaluationContext evaluationContext,
                                      ConditionManager localConditionManager) {
        throw new UnsupportedOperationException();
    }

    default void ensureVariable(EvaluationContext evaluationContext,
                                Variable variable,
                                ChangeData changeData,
                                int newStatementTime) {
        throw new UnsupportedOperationException();
    }

    // return progress
    default boolean addToAssignmentsInLoop(VariableInfoContainer vic, String fullyQualifiedName) {
        throw new UnsupportedOperationException();
    }
}
