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

import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.And;
import org.e2immu.analyser.model.expression.NewObject;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.objectflow.Origin;
import org.e2immu.analyser.objectflow.access.MethodAccess;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Messages;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.SetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Stream;

import static org.e2immu.analyser.model.expression.EmptyExpression.NO_VALUE;
import static org.e2immu.analyser.util.Logger.LogTarget.DEBUG_MODIFY_CONTENT;
import static org.e2immu.analyser.util.Logger.LogTarget.OBJECT_FLOW;
import static org.e2immu.analyser.util.Logger.log;

/*
Contains all side effects of analysing an expression.
The 'apply' method of the analyser executes them.

It contains:

- an increased statement time, caused by calls to those methods that increase time
- the computed result (value)
- an assembled precondition, gathered from calls to methods that have preconditions
- a map of value changes (and linked var, property, ...)
- a list of error messages
- a list of object flows (for later)

Critically, the apply method will effect the value changes before executing the modifications.
Important value changes are:
- a variable has been read at a given statement time
- a variable has been assigned a value
- the linked variables of a variable have been computed

We track delays in state change
 */
public record EvaluationResult(EvaluationContext evaluationContext,
                               int statementTime,
                               Expression value,
                               Messages messages,
                               List<ObjectFlow> objectFlows,
                               Map<Variable, ExpressionChangeData> valueChanges,
                               Expression precondition,
                               boolean addCircularCallOrUndeclaredFunctionalInterface) {

    public EvaluationResult {
        assert valueChanges.values().stream().noneMatch(ecd -> ecd.linkedVariables == null);
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(EvaluationResult.class);

    public Stream<Message> getMessageStream() {
        return messages.getMessageStream();
    }

    public Stream<ObjectFlow> getObjectFlowStream() {
        return objectFlows.stream();
    }

    public Stream<Map.Entry<Variable, ExpressionChangeData>> getExpressionChangeStream() {
        return valueChanges.entrySet().stream();
    }

    public Expression getExpression() {
        return value;
    }

    public boolean isNotNull0(EvaluationContext evaluationContext) {
        // should we trawl through the modifications?
        return evaluationContext.isNotNull0(value);
    }

    /**
     * Any of [value, markAssignment, linkedVariables]
     * can be used independently: possibly we want to mark assignment, but still have NO_VALUE for the value.
     * The stateOnAssignment can also still be NO_VALUE while the value is known, and vice versa.
     */
    public record ExpressionChangeData(Expression value,
                                       boolean stateIsDelayed,
                                       boolean markAssignment,
                                       Set<Integer> readAtStatementTime,
                                       LinkedVariables linkedVariables,
                                       Map<VariableProperty, Integer> properties) {
        public ExpressionChangeData {
            Objects.requireNonNull(value);
        }

        public ExpressionChangeData merge(ExpressionChangeData other) {
            LinkedVariables combinedLinkedVariables = linkedVariables.merge(other.linkedVariables);
            Set<Integer> combinedReadAtStatementTime = SetUtil.immutableUnion(readAtStatementTime, other.readAtStatementTime);
            Map<VariableProperty, Integer> combinedProperties = VariableInfoImpl.mergeProperties(properties, other.properties);
            return new ExpressionChangeData(other.value, other.stateIsDelayed, other.markAssignment || markAssignment,
                    combinedReadAtStatementTime, combinedLinkedVariables, combinedProperties);
        }

    }

    // lazy creation of lists
    public static class Builder {
        private final EvaluationContext evaluationContext;
        private final Messages messages = new Messages();
        private List<ObjectFlow> objectFlows;
        private Expression value;
        private int statementTime;
        private final Map<Variable, ExpressionChangeData> valueChanges = new HashMap<>();
        private Expression precondition;
        private boolean addCircularCallOrUndeclaredFunctionalInterface;

        // for a constant EvaluationResult
        public Builder() {
            evaluationContext = null;
        }

        public Builder(EvaluationContext evaluationContext) {
            this.evaluationContext = evaluationContext;
            this.statementTime = evaluationContext.getInitialStatementTime();
        }

        public Builder compose(EvaluationResult... previousResults) {
            assert previousResults != null;
            for (EvaluationResult evaluationResult : previousResults) {
                append(false, evaluationResult);
            }
            return this;
        }

        public void composeIgnoreExpression(EvaluationResult... previousResults) {
            assert previousResults != null;
            for (EvaluationResult evaluationResult : previousResults) {
                append(true, evaluationResult);
            }
        }

        public Builder compose(Iterable<EvaluationResult> previousResults) {
            for (EvaluationResult evaluationResult : previousResults) {
                append(false, evaluationResult);
            }
            return this;
        }

        private void append(boolean ignoreExpression, EvaluationResult evaluationResult) {
            if (!ignoreExpression && evaluationResult.value != null && (value == null || value != NO_VALUE)) {
                value = evaluationResult.value;
            }

            this.messages.addAll(evaluationResult.getMessageStream());

            if (!evaluationResult.objectFlows.isEmpty()) {
                if (objectFlows == null) objectFlows = new LinkedList<>(evaluationResult.objectFlows);
                else objectFlows.addAll(evaluationResult.objectFlows);
            }
            for (Map.Entry<Variable, ExpressionChangeData> e : evaluationResult.valueChanges.entrySet()) {
                valueChanges.merge(e.getKey(), e.getValue(), ExpressionChangeData::merge);
            }

            statementTime = Math.max(statementTime, evaluationResult.statementTime);

            if (evaluationResult.precondition != null) {
                if (precondition == null) {
                    precondition = evaluationResult.precondition;
                } else {
                    precondition = combinePrecondition(precondition, evaluationResult.precondition);
                }
            }
        }

        public void incrementStatementTime() {
            if (evaluationContext.allowedToIncrementStatementTime()) {
                statementTime++;
            }
        }

        // also sets result of expression, but cannot overwrite NO_VALUE
        public Builder setExpression(Expression value) {
            Objects.requireNonNull(value);
            if (this.value != NO_VALUE) {
                this.value = value;
            }
            return this;
        }

        public Expression getExpression() {
            return value;
        }

        public int getIteration() {
            return evaluationContext == null ? -1 : evaluationContext.getIteration();
        }

        public EvaluationResult build() {
            return new EvaluationResult(evaluationContext, statementTime, value,
                    messages, objectFlows == null ? List.of() : objectFlows, valueChanges, precondition,
                    addCircularCallOrUndeclaredFunctionalInterface);
        }

        public void variableOccursInNotNullContext(Variable variable, Expression value, int notNullRequired) {
            assert evaluationContext != null;

            if (value == NO_VALUE) {
                if (variable instanceof ParameterInfo) {
                    // we will mark this, so that the parameter analyser knows that it should wait
                    setProperty(variable, VariableProperty.NOT_NULL_DELAYS_RESOLVED, Level.FALSE);
                }
                return; // not yet
            }
            if (variable instanceof This) return; // nothing to be done here
            if (variable instanceof ParameterInfo) {
                // the opposite of the previous one
                setProperty(variable, VariableProperty.NOT_NULL_DELAYS_RESOLVED, Level.TRUE);
            }
            if (value instanceof VariableExpression redirect && redirect.variable() instanceof ParameterInfo) {
                // the opposite of the previous one
                setProperty(redirect.variable(), VariableProperty.NOT_NULL_DELAYS_RESOLVED, Level.TRUE);
            }

            // if we already know that the variable is NOT @NotNull, then we'll raise an error
            int notNull = MultiLevel.value(evaluationContext.getProperty(value, VariableProperty.NOT_NULL), MultiLevel.NOT_NULL);
            if (notNull == MultiLevel.FALSE) {
                Message message = Message.newMessage(evaluationContext.getLocation(), Message.POTENTIAL_NULL_POINTER_EXCEPTION,
                        "Variable: " + variable.simpleName());
                messages.add(message);
            } else if (notNull == MultiLevel.DELAY) {
                // we only need to mark this in case of doubt (if we already know, we should not mark)
                setProperty(variable, VariableProperty.NOT_NULL, notNullRequired);
                if (value instanceof VariableExpression redirect) {
                    setProperty(redirect.variable(), VariableProperty.NOT_NULL, notNullRequired);
                }
            }
        }

        public void markRead(Variable variable) {
            ExpressionChangeData ecd = valueChanges.get(variable);
            ExpressionChangeData newEcd;
            if (ecd == null) {
                newEcd = new ExpressionChangeData(NO_VALUE, false, false, Set.of(statementTime),
                        defaultLinkedVariables(variable), Map.of());
            } else {
                newEcd = new ExpressionChangeData(ecd.value, ecd.stateIsDelayed, ecd.markAssignment,
                        SetUtil.immutableUnion(ecd.readAtStatementTime, Set.of(statementTime)), ecd.linkedVariables, Map.of());
            }
            valueChanges.put(variable, newEcd);

            // we do this because this. is often implicit (all other scopes will be marked read explicitly!)
            // when explicit, there may be two MarkRead modifications, which will eventually be merged
            if (variable instanceof FieldReference fieldReference && fieldReference.scope instanceof This) {
                markRead(fieldReference.scope);
            }
        }

        public ObjectFlow createLiteralObjectFlow(ParameterizedType parameterizedType) {
            assert evaluationContext != null;

            return createInternalObjectFlow(new Location(evaluationContext.getCurrentType()), parameterizedType, Origin.LITERAL);
        }

        public ObjectFlow createInternalObjectFlow(Location location, ParameterizedType parameterizedType, Origin origin) {
            ObjectFlow objectFlow = new ObjectFlow(location, parameterizedType, origin);
            if (objectFlows == null) objectFlows = new LinkedList<>();
            if (!objectFlows.contains(objectFlow)) {
                objectFlows.add(objectFlow);
            }
            log(OBJECT_FLOW, "Created internal flow {}", objectFlow);
            return objectFlow;
        }

        public Builder raiseError(String messageString) {
            assert evaluationContext != null;
            StatementAnalyser statementAnalyser = evaluationContext.getCurrentStatement();
            if (statementAnalyser != null) {
                Message message = Message.newMessage(evaluationContext.getLocation(), messageString);
                messages.add(message);
            } else { // e.g. companion analyser
                LOGGER.warn("Analyser error: {}", messageString);
            }
            return this;
        }

        public void raiseError(String messageString, String extra) {
            assert evaluationContext != null;
            StatementAnalyser statementAnalyser = evaluationContext.getCurrentStatement();
            if (statementAnalyser != null) {
                Message message = Message.newMessage(evaluationContext.getLocation(), messageString, extra);
                messages.add(message);
            } else {
                LOGGER.warn("Analyser error: {}, {}", messageString, extra);
            }
        }

        /* when evaluating a variable field, a new local copy of the variable may be created
           when this happens, we need to link the field to this local copy
           this linking takes place in the value changes map, so that the linked variables can be set once, correctly.
         */
        public Expression currentExpression(Variable variable, boolean isNotAssignmentTarget) {
            ExpressionChangeData currentExpression = valueChanges.get(variable);
            if (currentExpression == null || currentExpression.value == NO_VALUE) {
                assert evaluationContext != null;
                return evaluationContext.currentValue(variable, statementTime, isNotAssignmentTarget);
            }
            return currentExpression.value;
        }

        private void addLink(Variable from, Variable to) {
            LinkedVariables linkTo = new LinkedVariables(Set.of(to));
            ExpressionChangeData current = valueChanges.get(from);
            ExpressionChangeData newVcd;
            if (current == null) {
                newVcd = new ExpressionChangeData(NO_VALUE, false, false, Set.of(), linkTo, Map.of());
            } else {
                // we simply merge the linkedVariables
               LinkedVariables linkedVariables = current.linkedVariables.merge(linkTo);
                newVcd = new ExpressionChangeData(current.value,
                        current.stateIsDelayed, current.markAssignment, current.readAtStatementTime, linkedVariables, Map.of());
            }
            valueChanges.put(from, newVcd);
        }

        public NewObject currentInstance(Variable variable, ObjectFlow objectFlowForCreation, Expression stateFromPreconditions) {
            ExpressionChangeData currentExpression = valueChanges.get(variable);
            if (currentExpression != null && currentExpression.value instanceof NewObject instance) return instance;
            assert evaluationContext != null;

            NewObject inContext = evaluationContext.currentInstance(variable, statementTime);
            if (inContext != null) return inContext;
            // there is no instance yet... we'll have to create one, but only if the value can have an instance
            if (Primitives.isPrimitiveExcludingVoid(variable.parameterizedType())) return null;
            Expression value = currentExpression(variable, true);
            if (value.isConstant()) return null;
            NewObject instance = new NewObject(null, variable.parameterizedType(), List.of(),
                    stateFromPreconditions, objectFlowForCreation);
            assignInstanceToVariable(variable, instance, null); // no change to linked variables
            return instance;
        }

        // called when a new instance is needed because of a modifying method call, or when a variable doesn't have
        // an instance yet. Not called upon assignment.
        private void assignInstanceToVariable(Variable variable, NewObject instance, LinkedVariables linkedVariables) {
            ExpressionChangeData current = valueChanges.get(variable);
            ExpressionChangeData newVcd;
            if (current == null) {
                boolean stateIsDelayed = evaluationContext.getConditionManager().isDelayed();
                newVcd = new ExpressionChangeData(instance, stateIsDelayed, false, Set.of(), linkedVariables, Map.of());
            } else {
                newVcd = new ExpressionChangeData(instance, current.stateIsDelayed, current.markAssignment,
                        current.readAtStatementTime, linkedVariables, Map.of());
            }
            valueChanges.put(variable, newVcd);
        }

        public void markMethodDelay(Variable variable, int methodDelay) {
            setProperty(variable, VariableProperty.METHOD_DELAY, methodDelay);
        }

        public void markMethodCalled(Variable variable, int methodCalled) {
            assert evaluationContext != null;

            Variable v;
            if (variable instanceof This) {
                v = variable;
            } else if (variable.concreteReturnType().typeInfo == evaluationContext.getCurrentType()) {
                v = new This(evaluationContext.getAnalyserContext(), evaluationContext.getCurrentType());
            } else v = null;
            if (v != null) {
                setProperty(v, VariableProperty.METHOD_CALLED, methodCalled);
            }
        }

        public void markContentModified(Variable variable, Expression value, int modified) {
            assert evaluationContext != null;
            int ignoreContentModifications = evaluationContext.getProperty(variable, VariableProperty.IGNORE_MODIFICATIONS);
            if (ignoreContentModifications != Level.TRUE) {
                log(DEBUG_MODIFY_CONTENT, "Mark method object as content modified {}: {}", modified, variable.fullyQualifiedName());
                StatementAnalyser statementAnalyser = evaluationContext.getCurrentStatement();
                setProperty(variable, VariableProperty.MODIFIED, modified);

                // modification in MLD via linked variables travels in one direction, but direct assignment also travels
                // "backwards"
                if (value instanceof VariableExpression redirect) {
                    setProperty(redirect.variable(), VariableProperty.MODIFIED, modified);
                }

            } else {
                log(DEBUG_MODIFY_CONTENT, "Skip marking method object as content modified: {}", variable.fullyQualifiedName());
            }
        }

        public void variableOccursInNotModified1Context(Variable variable, Expression currentExpression) {
            if (currentExpression == NO_VALUE) return; // not yet
            assert evaluationContext != null;
            StatementAnalyser statementAnalyser = evaluationContext.getCurrentStatement();
            // if we already know that the variable is NOT @NotNull, then we'll raise an error
            int notModified1 = evaluationContext.getProperty(currentExpression, VariableProperty.NOT_MODIFIED_1);
            if (notModified1 == Level.FALSE) {
                Message message = Message.newMessage(evaluationContext.getLocation(), Message.MODIFICATION_NOT_ALLOWED, variable.simpleName());
                messages.add(message);
            } else if (notModified1 == Level.DELAY) {
                // we only need to mark this in case of doubt (if we already know, we should not mark)
                setProperty(variable, VariableProperty.NOT_MODIFIED_1, Level.TRUE);
            }
        }

        /*
        Called from Assignment and from LocalVariableCreation.
         */
        public Builder assignment(Variable assignmentTarget,
                                  Expression resultOfExpression,
                                  LinkedVariables linkedVariables) {
            assert evaluationContext != null;
            boolean stateIsDelayed = evaluationContext.getConditionManager().isDelayed();

            ExpressionChangeData newEcd;
            ExpressionChangeData ecd = valueChanges.get(assignmentTarget);
            if (ecd == null) {
                newEcd = new ExpressionChangeData(stateIsDelayed ? NO_VALUE : resultOfExpression, stateIsDelayed,
                        true, Set.of(), linkedVariables, Map.of());
            } else {
                newEcd = new ExpressionChangeData(stateIsDelayed ? NO_VALUE : resultOfExpression, stateIsDelayed,
                        true, ecd.readAtStatementTime, linkedVariables, ecd.properties);
            }
            valueChanges.put(assignmentTarget, newEcd);
            return this;
        }

        // Used in transformation of parameter lists
        public void setProperty(Variable variable, VariableProperty property, int value) {
            assert evaluationContext != null;
            ExpressionChangeData newEcd;
            ExpressionChangeData ecd = valueChanges.get(variable);
            if (ecd == null) {
                newEcd = new ExpressionChangeData(NO_VALUE, false, false, Set.of(),
                        defaultLinkedVariables(variable), Map.of(property, value));
            } else {
                newEcd = new ExpressionChangeData(ecd.value, ecd.stateIsDelayed, ecd.markAssignment,
                        ecd.readAtStatementTime, ecd.linkedVariables,
                        VariableInfoImpl.mergeProperties(Map.of(property, value), ecd.properties));
            }
            valueChanges.put(variable, newEcd);
        }

        public void addPrecondition(Expression expression) {
            if (precondition == null) {
                precondition = expression;
            } else {
                precondition = combinePrecondition(precondition, expression);
            }
        }

        private Expression combinePrecondition(Expression e1, Expression e2) {
            return new And(evaluationContext.getPrimitives()).append(evaluationContext, e1, e2);
        }

        public void addCallOut(boolean b, ObjectFlow destination, Expression parameterExpression) {
            // TODO part of object flow
        }

        public void addAccess(boolean b, MethodAccess methodAccess, Expression object) {
            // TODO part of object flow
        }

        public void modifyingMethodAccess(Variable variable, NewObject newInstance, LinkedVariables linkedVariables) {
            //add(new StateData.RemoveVariableFromState(evaluationContext, variable)); TODO replace by other code
            assignInstanceToVariable(variable, newInstance, linkedVariables);
        }

        public void addErrorAssigningToFieldOutsideType(FieldInfo fieldInfo) {
            assert evaluationContext != null;
            messages.add(Message.newMessage(new Location(fieldInfo), Message.ADVISE_AGAINST_ASSIGNMENT_TO_FIELD_OUTSIDE_TYPE));
        }

        public void addParameterShouldNotBeAssignedTo(ParameterInfo parameterInfo) {
            assert evaluationContext != null;
            messages.add(Message.newMessage(new Location(parameterInfo), Message.PARAMETER_SHOULD_NOT_BE_ASSIGNED_TO,
                    parameterInfo.fullyQualifiedName()));
        }

        public void addCircularCallOrUndeclaredFunctionalInterface() {
            addCircularCallOrUndeclaredFunctionalInterface = true;
        }

        public int getStatementTime() {
            return statementTime;
        }

        private LinkedVariables defaultLinkedVariables(Variable variable) {
            return variable instanceof This ? LinkedVariables.EMPTY : LinkedVariables.DELAY;
        }
    }
}
