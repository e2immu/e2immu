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
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.annotation.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Container to store different versions of a VariableInfo object, one or more of this list:
 * <ol>
 *     <li>0 - reference to previous: simply a reference to a previous version, NEVER written</li>
 *     <li>1 - initialisation phase: assignments like <code>int i=3;</code> or <code>for(i=0; ...)</code></li>
 *     <li>2 - evaluation phase: typically does not contain an assignment, but is possible is in <code>while((line = reader.next()) != null)</code></li>
 *     <li>3 - update phase: only in <code>for(...; i++)</code> constructs</li>
 *     <li>4 - summary phase: information from sub-blocks</li>
 * </ol>
 * <p>
 * Only in very rare situations, all 5 can be present.
 * Generally, outside of the statement analyser, the highest version will be inspected.
 * <p>
 * Can only be created in increasing levels! Will be frozen as soon as the statement analyser goes AnalysisStatus == DONE.
 */
public interface VariableInfoContainer {
    int LEVEL_0_PREVIOUS = 0;
    int LEVEL_1_INITIALISER = 1;
    int LEVEL_2_UPDATER = 2; // IMPROVE at some point, we want updating to take place after evaluation
    int LEVEL_3_EVALUATION = 3;
    int LEVEL_4_SUMMARY = 4;

    /**
     * General method for obtaining the "most relevant" <code>VariableInfo</code> object describing the state
     * of the variable after executing this statement.
     *
     * @return a VariableInfo object, always. There would not have been a <code>VariableInfoContainer</code> if there
     * was not at least one <code>VariableInfo</code> object.
     */
    @NotNull
    VariableInfo current();

    /**
     * Return the value at the exact level, for debugging purposes.
     *
     * @param level the level
     * @return null  when there's no object at this level
     */
    VariableInfo get(int level);

    /**
     * @param level max level
     * @return the value at the level, or below.
     */
    VariableInfo best(int level);

    /**
     * Mostly for debugging
     *
     * @return the highest level for which there is <code>VariableInfo</code> data written.
     */
    int getCurrentLevel();

    /**
     * Prepares for an assignment, which must increase the current level.
     * Important to note that an assignment is split in multiple methods (markAssigned, setValue, assigned) because
     * not all data may be available in the same iteration.
     *
     * @param level         the level at which the assignment takes place (1, 2, 3, 4, cannot be 0)
     * @param statementTime the time at which the assignment takes place; it needs to be set ONLY for variable fields;
     *                      otherwise, it must have value VariableInfoImpl.NOT_A_VARIABLE_FIELD
     */
    void assignment(int level, int statementTime);

    // explicit freezing (DONE at the end of statement analyser): forbid any future writing
    void freeze();


    // writing operations
    void setValueOnAssignment(int level, Expression value, Map<VariableProperty, Integer> propertiesToSet);

    void setValueAndStateOnAssignment(int level, Expression value, Expression state, Map<VariableProperty, Integer> propertiesToSet);

    void setStateOnAssignment(int level, Expression state);

    /**
     * Typically in the 1st iteration for effectively final fields, this method
     * is called when the field's final value has been established.
     *
     * @param initialValue the value coming from the field analyser
     */
    void setInitialValueFromAnalyser(Expression initialValue, Map<VariableProperty, Integer> propertiesToSet);

    void setProperty(int level, VariableProperty variableProperty, int value);

    void setProperty(int level, VariableProperty variableProperty, int value, boolean failWhenTryingToWriteALowerValue);

    default void ensureProperty(int level, VariableProperty variableProperty, int value) {
        int current = best(level).getProperty(variableProperty);
        if (value > current) {
            setProperty(level, variableProperty, value);
        }
    }

    void setLinkedVariables(int level, Set<Variable> variables);

    void setLinkedVariablesFromAnalyser(Set<Variable> variables);

    /**
     * aggregation method that copies value, properties, state, object flow, and linked variables
     * using the 'setXX' methods.
     *
     * @param level                            the level to write to
     * @param previousVariableInfo             the source to copy from
     * @param failWhenTryingToWriteALowerValue if false, we ignore lower values. This happens when e.g. a field is overall nullable while
     *                                         in a particular method it has non-null assigned values; also, when a field not belonging
     *                                         to the type being analysed, is being modified. See test Basics_3
     */
    void copy(int level, VariableInfo previousVariableInfo, boolean failWhenTryingToWriteALowerValue);

    void setObjectFlow(int level, ObjectFlow objectFlow);

    /**
     * should only happen in the first iteration, because it increases the property's value based on that of ASSIGNED
     *
     * @param level the level at which the variable is being read
     */
    void markRead(int level);

    void merge(int level, EvaluationContext evaluationContext,
               boolean existingValuesWillBeOverwritten,
               List<VariableInfo> merge);

}
