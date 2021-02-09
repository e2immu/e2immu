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

import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.annotation.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Container to store different versions of a VariableInfo object, one or more of this list:
 * INITIAL OR PREVIOUS: initial if first occurrence of variable
 * EVALUATION: values of the evaluation
 * MERGE: created by step 4 in the analyser
 */
public interface VariableInfoContainer {

    int VARIABLE_FIELD_DELAY = -1;
    int NOT_A_VARIABLE_FIELD = -2;

    // prefixes in assignment id
    // see TestLevelSuffixes to visually understand the order

    String NOT_YET_READ = "-";
    String NOT_YET_ASSIGNED = "-";

    boolean hasEvaluation();

    boolean hasMerge();

    default boolean isNotAssignedInThisStatement() {
        return !hasEvaluation() || getPreviousOrInitial().getAssignmentId().compareTo(best(Level.EVALUATION).getAssignmentId()) >= 0;
    }

    default boolean isReadInThisStatement() {
        return hasEvaluation() && getPreviousOrInitial().getReadId().compareTo(best(Level.EVALUATION).getReadId()) < 0;
    }

    void setStaticallyAssignedVariables(LinkedVariables staticallyAssignedVariables, boolean initialOrEvaluation);

    void copyFromEvalIntoMerge();

    // suffixes in assignment id; these act as the 3 levels for setProperty
    enum Level {
        INITIAL("-C"), // C for creation, but essentially, it should be < E
        EVALUATION("-E"), // the - comes before the digits
        MERGE(":M"); // the : comes after the digits
        public final String label;

        Level(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /*
    method is here so we know we need to search for - and :
     */
    static String statementId(String assignmentId) {
        assert assignmentId.endsWith(Level.INITIAL.label) || assignmentId.endsWith(Level.EVALUATION.label)
                || assignmentId.endsWith(Level.MERGE.label);
        return assignmentId.substring(0, assignmentId.length() - 2);
    }

    /*
    explicit freezing (DONE at the end of statement analyser): forbid any future writing
     */
    void freeze();

    /**
     * General method for obtaining the "most relevant" <code>VariableInfo</code> object describing the state
     * of the variable after executing this statement.
     *
     * @return a VariableInfo object, always. There would not have been a <code>VariableInfoContainer</code> if there
     * was not at least one <code>VariableInfo</code> object.
     */
    @NotNull
    VariableInfo current();

    /*
     * like current, but then with a limit
     */
    VariableInfo best(Level level);

    /**
     * Returns either the current() of the previous VIC, or the initial value if this is the first statement
     * where this variable occurs.
     */
    @NotNull
    VariableInfo getPreviousOrInitial();

    /**
     * @return if the variable was created in this statement
     */
    boolean isInitial();

    // writing operations
    void setValue(Expression value,
                  boolean valueIsDelayed,
                  LinkedVariables staticallyAssignedVariables,
                  Map<VariableProperty, Integer> propertiesToSet,
                  boolean initialOrEvaluation);

    default void setProperty(VariableProperty variableProperty, int value, Level level) {
        setProperty(variableProperty, value, true, level);
    }

    void setProperty(VariableProperty variableProperty, int value, boolean failWhenTryingToWriteALowerValue, Level level);

    void increasePropertyOfInitial(VariableProperty variableProperty, int value);

    /**
     * set linked variables
     *
     * @param linkedVariables                   must not be null, must not be delay
     * @param writeInInitialOtherwiseEvaluation true then written in initial (when the linked variables come from the analyser),
     *                                          otherwise in evaluation
     */
    void setLinkedVariables(LinkedVariables linkedVariables, boolean writeInInitialOtherwiseEvaluation);

    /*
    copy from one statement to the next.
    this method uses assignmentId and readId to determine which values can be copied, and which values will by set
    by the apply method in the statement analyser.
     */
    void copy();

    void setObjectFlow(ObjectFlow objectFlow, boolean writeInInitialOtherwiseEvaluation);

    void ensureEvaluation(String assignmentId, String readId, int statementTime, Set<Integer> readAtStatementTimes);

    void merge(EvaluationContext evaluationContext,
               Expression stateOfDestination,
               boolean atLeastOneBlockExecuted,
               List<StatementAnalysis.ConditionAndVariableInfo> mergeSources);

    /*
    Statement time is irrelevant for all but variable fields.
    The correct value comes in a later iteration; it is set in StatementAnalysis based on information
    from the field analyser.

     */
    void setStatementTime(int statementTime);

    /*
    Is true starting from the level 3 main expression of the loop statement, down to all statements in the block.
    Is true at all times for variables declared in the loop statement's level 2 (for, forEach)
     */
    default boolean isLocalVariableInLoopDefinedOutside() {
        VariableInLoop.VariableType vt = getVariableInLoop().variableType();
        return vt == VariableInLoop.VariableType.IN_LOOP_DEFINED_OUTSIDE || vt == VariableInLoop.VariableType.LOOP;
    }

    /*
    Never null, points to NOT_IN_LOOP otherwise
     */
    VariableInLoop getVariableInLoop();

    default String getStatementIndexOfThisLoopOrShadowVariable() {
        return getVariableInLoop().statementId(VariableInLoop.VariableType.LOOP, VariableInLoop.VariableType.LOOP_COPY);
    }

    default String getStatementIndexOfThisShadowVariable() {
        return getVariableInLoop().statementId(VariableInLoop.VariableType.LOOP_COPY);
    }

    default String getStatementIndexOfThisLoopVariable() {
        return getVariableInLoop().statementId(VariableInLoop.VariableType.LOOP);
    }
}
