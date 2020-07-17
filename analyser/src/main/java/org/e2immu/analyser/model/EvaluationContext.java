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

import org.e2immu.analyser.model.abstractvalue.UnknownValue;
import org.e2immu.analyser.model.abstractvalue.VariableValue;
import org.e2immu.analyser.model.expression.ArrayAccess;
import org.e2immu.analyser.objectflow.Access;
import org.e2immu.analyser.objectflow.Location;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.objectflow.Origin;
import org.e2immu.analyser.parser.TypeContext;
import org.e2immu.annotation.NotNull;

import java.util.Set;

public interface EvaluationContext {

    default int getIteration() {
        return 0;
    }

    default void addProperty(Variable variable, VariableProperty variableProperty, int value) {
    }
    // WHERE ARE WE??

    // can be null, in evaluation of lambda expressions
    default MethodInfo getCurrentMethod() {
        return null;
    }

    default FieldInfo getCurrentField() {
        return null;
    }

    default NumberedStatement getCurrentStatement() {
        return null;
    }

    @NotNull
    default TypeInfo getCurrentType() {
        throw new UnsupportedOperationException();
    }

    // METHODS FOR THE MANAGEMENT OF HIERARCHY (NEW CONTEXT IN EACH BLOCK)

    @NotNull
    default EvaluationContext childInSyncBlock(@NotNull Value conditional,
                                               Runnable uponUsingConditional,
                                               boolean inSyncBlock,
                                               boolean guaranteedToBeReachedByParentStatement) {
        throw new UnsupportedOperationException();
    }

    @NotNull
    default EvaluationContext child(@NotNull Value conditional,
                                    Runnable uponUsingConditional,
                                    boolean guaranteedToBeReachedByParentStatement) {
        throw new UnsupportedOperationException();
    }

    // used in LambdaBlock to create a new StatementAnalyser, which needs the typeContext...
    @NotNull
    default TypeContext getTypeContext() {
        throw new UnsupportedOperationException();
    }

    // BASIC ACCESS

    default void createLocalVariableOrParameter(@NotNull Variable variable) {
    }

    // mark that variables are linked (statement analyser, assignment)
    default void linkVariables(@NotNull Variable variableFromExpression, @NotNull Set<Variable> toBestCase, @NotNull Set<Variable> toWorstCase) {
    }

    // called by VariableExpression, FieldAccess; will create a field if necessary
    // errors on local vars, parameters if they don't exist

    @NotNull
    default Value currentValue(@NotNull Variable variable) {
        return UnknownValue.NO_VALUE;
    }

    // we're adding a variable to the evaluation context, from FieldAnalyser with final value

    @NotNull
    default VariableValue newVariableValue(@NotNull Variable variable) {
        throw new UnsupportedOperationException();
    }

    // obtain a variable value for a dynamic array position, like a[i], {1,2,3}[i] or a[3] (but not {1,2,3}[1], because that == 2)
    @NotNull
    default Value arrayVariableValue(Value array, Value indexValue, ParameterizedType parameterizedType, Set<Variable> dependencies, Variable arrayVariable) {
        throw new UnsupportedOperationException();
    }

    // delegation from VariableValue and analysers
    default int getProperty(@NotNull Variable variable, @NotNull VariableProperty variableProperty) {
        return Level.DELAY;
    }

    // to be called from getProperty() in value
    default int getProperty(@NotNull Value value, @NotNull VariableProperty variableProperty) {
        return Level.DELAY;
    }

    default boolean isNotNull0(Value value) {
        return MultiLevel.value(getProperty(value, VariableProperty.NOT_NULL), 0) >= MultiLevel.EVENTUAL_AFTER;
    }

    // method of VariableValue
    default boolean equals(@NotNull Variable variable, Variable other) {
        return false;
    }

    // merge "up", explicitly called for Lambda blocks and expressions
    default void merge(EvaluationContext child) {
    }

    default void addPropertyRestriction(Variable variable, VariableProperty property, int value) {
    }

    default void markRead(Variable variable) {
    }

    default void markRead(String variableName) {
    }

    default DependentVariable ensureArrayVariable(ArrayAccess arrayAccess, String name, Variable arrayVariable) {
        return null;
    }

    default void assignmentBasics(Variable at, Value value, boolean assignmentToNonEmptyExpression) {
    }

    default void raiseError(String message) {
    }

    default void raiseError(String message, String extra) {
    }

    default Location getLocation() {
        return null;
    }

    default ObjectFlow createLiteralObjectFlow(ParameterizedType parameterizedType) {
        return createInternalObjectFlow(parameterizedType, Origin.LITERAL);
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

    default ObjectFlow getObjectFlow(Variable variable) {
        return ObjectFlow.NO_FLOW;
    }

    default void updateObjectFlow(Variable variable, ObjectFlow second) {
        throw new UnsupportedOperationException();
    }

    default void modifyingMethodAccess(Variable variable) {
    }
}
