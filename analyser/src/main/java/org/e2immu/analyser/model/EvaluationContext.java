/*
 * e2immu-analyser: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.e2immu.analyser.model;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.abstractvalue.VariableValue;
import org.e2immu.analyser.parser.TypeContext;
import org.e2immu.annotation.NotNull;

import java.util.Set;

public interface EvaluationContext {

    // called by VariableExpression, FieldAccess
    Value currentValue(Variable variable);

    // with null meaning that no decision can be made yet
    boolean isNotNull(Variable variable);

    MethodInfo getCurrentMethod();

    TypeInfo getCurrentType();

    EvaluationContext childInSyncBlock(Value conditional, Runnable uponUsingConditional,
                            boolean inSyncBlock,
                            boolean guaranteedToBeReachedByParentStatement);

    EvaluationContext child(Value conditional, Runnable uponUsingConditional,
                            boolean guaranteedToBeReachedByParentStatement);

    void create(Variable variable, VariableProperty... initialProperties);

    String variableName(@NotNull Variable variable);

    TypeContext getTypeContext();

    void linkVariables(Variable variableFromExpression, Set<Variable> toBestCase, Set<Variable> toWorstCase);

    void setNotNull(Variable variable);

    void setValue(@NotNull Variable variable, @NotNull Value value);

    boolean equals(String name, String name1);

    /// NEW METHODS

    // we're adding a variable to the evaluation context
    VariableValue newVariableValue(Variable variable);

    // create a variable value for a dynamic array position, like a[i], {1,2,3}[i] or a[3] (but not {1,2,3}[1], because that == 2)
    VariableValue newArrayVariableValue(Value array, Value indexValue);

    // called by Assignment operator
    Value assignment(Variable assignmentTarget, Value resultOfExpression);

    // delegation
    int getProperty(VariableProperty variableProperty);
}
