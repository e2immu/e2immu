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

import org.e2immu.analyser.analyser.NumberedStatement;
import org.e2immu.analyser.analyser.VariableProperty;

import org.e2immu.analyser.model.abstractvalue.VariableValue;
import org.e2immu.analyser.model.expression.ArrayAccess;
import org.e2immu.analyser.objectflow.Access;
import org.e2immu.analyser.objectflow.Location;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.objectflow.Origin;
import org.e2immu.analyser.objectflow.origin.StaticOrigin;
import org.e2immu.analyser.parser.TypeContext;
import org.e2immu.annotation.NotNull;

import java.util.Set;

public interface EvaluationContext {

    int getIteration();

    void addProperty(Variable variable, VariableProperty variableProperty, int value);
    // WHERE ARE WE??

    // can be null, in evaluation of lambda expressions
    MethodInfo getCurrentMethod();

    default FieldInfo getCurrentField() {
        return null;
    }

    NumberedStatement getCurrentStatement();

    @NotNull
    TypeInfo getCurrentType();

    // METHODS FOR THE MANAGEMENT OF HIERARCHY (NEW CONTEXT IN EACH BLOCK)

    @NotNull
    EvaluationContext childInSyncBlock(@NotNull Value conditional,
                                       Runnable uponUsingConditional,
                                       boolean inSyncBlock,
                                       boolean guaranteedToBeReachedByParentStatement);

    @NotNull
    EvaluationContext child(@NotNull Value conditional,
                            Runnable uponUsingConditional,
                            boolean guaranteedToBeReachedByParentStatement);

    // used in LambdaBlock to create a new StatementAnalyser, which needs the typeContext...
    @NotNull
    TypeContext getTypeContext();

    // BASIC ACCESS

    void createLocalVariableOrParameter(@NotNull Variable variable, VariableProperty... initialProperties);

    // mark that variables are linked (statement analyser, assignment)
    void linkVariables(@NotNull Variable variableFromExpression, @NotNull Set<Variable> toBestCase, @NotNull Set<Variable> toWorstCase);

    // called by VariableExpression, FieldAccess; will create a field if necessary
    // errors on local vars, parameters if they don't exist

    @NotNull
    Value currentValue(@NotNull Variable variable);
    // we're adding a variable to the evaluation context, from FieldAnalyser with final value

    @NotNull
    VariableValue newVariableValue(@NotNull Variable variable);

    // obtain a variable value for a dynamic array position, like a[i], {1,2,3}[i] or a[3] (but not {1,2,3}[1], because that == 2)
    @NotNull
    Value arrayVariableValue(Value array, Value indexValue, ParameterizedType parameterizedType, Set<Variable> dependencies, Variable arrayVariable);

    // delegation from VariableValue and analysers
    int getProperty(@NotNull Variable variable, @NotNull VariableProperty variableProperty);

    // to be called from getProperty() in value
    int getProperty(@NotNull Value value, @NotNull VariableProperty variableProperty);

    default boolean isNotNull0(Value value) {
        return Level.value(getProperty(value, VariableProperty.NOT_NULL), Level.NOT_NULL) == Level.TRUE;
    }

    // method of VariableValue
    boolean equals(@NotNull Variable variable, Variable other);

    // merge "up", explicitly called for Lambda blocks and expressions
    void merge(EvaluationContext child);

    void addPropertyRestriction(Variable variable, VariableProperty property, int value);

    void markRead(Variable variable);

    void markRead(String variableName);

    DependentVariable ensureArrayVariable(ArrayAccess arrayAccess, String name, Variable arrayVariable);

    void assignmentBasics(Variable at, Value value, boolean assignmentToNonEmptyExpression);

    void raiseError(String message);

    void raiseError(String message, String extra);

    Location getLocation();

    default ObjectFlow registerConstantObjectFlow(ParameterizedType parameterizedType) {
        ObjectFlow objectFlow = new ObjectFlow(new Location(getCurrentType()), parameterizedType, StaticOrigin.LITERAL);
        return getCurrentType().typeAnalysis.get().ensureConstantObjectFlow(objectFlow);
    }

    /**
     * creates a new object flow in this current method or field initialiser.
     * The location is created in a more advanced way than using getLocation().
     *
     * @param parameterizedType The type of the object flow
     * @param origin            The origin, must be either ResultOfMethodCall or ParentFlows
     * @return a new ObjectFlow object, with a unique location (not a *reused* object flow object)
     */
    default ObjectFlow createInternalObjectFlow(ParameterizedType parameterizedType, Origin origin) {
        throw new UnsupportedOperationException();
    }

    /**
     * The method updates the flow if the value is a variable value
     *
     * @param modifying is the access modifying?
     * @param access    the access to add
     * @param value     the value whose flow to add the access to
     * @return potentially a new flow, but can be source
     */
    default ObjectFlow addAccess(boolean modifying, Access access, Value value) {
        throw new UnsupportedOperationException();
    }

    default ObjectFlow addCallOut(boolean modifying, ObjectFlow callOut, Value value) {
        throw new UnsupportedOperationException();
    }

    ObjectFlow getObjectFlow(Variable variable);

    default void updateObjectFlow(Variable variable, ObjectFlow second) {
        throw new UnsupportedOperationException();
    }

    default void reassigned(Variable variable) {
    }
}
