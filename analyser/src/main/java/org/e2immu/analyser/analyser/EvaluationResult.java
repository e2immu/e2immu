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
import org.e2immu.analyser.model.expression.NewObject;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.objectflow.Origin;
import org.e2immu.analyser.objectflow.access.MethodAccess;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Primitives;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Stream;

import static org.e2immu.analyser.model.expression.EmptyExpression.NO_VALUE;
import static org.e2immu.analyser.util.Logger.LogTarget.DEBUG_MODIFY_CONTENT;
import static org.e2immu.analyser.util.Logger.LogTarget.OBJECT_FLOW;
import static org.e2immu.analyser.util.Logger.log;

public record EvaluationResult(EvaluationContext evaluationContext,
                               int statementTime,
                               Expression value,
                               List<StatementAnalyser.StatementAnalysisModification> modifications,
                               List<StatementAnalysis.StateChange> stateChanges,
                               List<ObjectFlow> objectFlows,
                               Map<Variable, ExpressionChangeData> valueChanges) {

    public final static Set<Variable> LINKED_VARIABLE_DELAY = Set.of(Variable.fake());

    private static final Logger LOGGER = LoggerFactory.getLogger(EvaluationResult.class);

    public Stream<StatementAnalyser.StatementAnalysisModification> getModificationStream() {
        return modifications.stream();
    }

    public Stream<ObjectFlow> getObjectFlowStream() {
        return objectFlows.stream();
    }

    public Stream<StatementAnalysis.StateChange> getStateChangeStream() {
        return stateChanges.stream();
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
                                       Expression stateOnAssignment,
                                       boolean markAssignment,
                                       boolean isNotAssignmentTarget,
                                       Set<Variable> linkedVariables) {
        public ExpressionChangeData {
            Objects.requireNonNull(value);
            Objects.requireNonNull(stateOnAssignment);
            assert value == NO_VALUE || stateOnAssignment != NO_VALUE : "Have state " + stateOnAssignment + ", value" + value;
        }

        public ExpressionChangeData merge(ExpressionChangeData other) {
            return other; //  should we merge? don't think so
        }
    }

    // lazy creation of lists
    public static class Builder {
        private final EvaluationContext evaluationContext;
        private List<StatementAnalyser.StatementAnalysisModification> modifications;
        private List<StatementAnalysis.StateChange> stateChanges;
        private List<ObjectFlow> objectFlows;
        private Expression value;
        private int statementTime;
        private final Map<Variable, ExpressionChangeData> valueChanges = new HashMap<>();

        private void addToModifications(StatementAnalyser.StatementAnalysisModification modification) {
            if (modifications == null) modifications = new ArrayList<>();
            modifications.add(modification);
        }

        private void addToModifications(Collection<StatementAnalyser.StatementAnalysisModification> modification) {
            if (modifications == null) modifications = new ArrayList<>();
            modifications.addAll(modification);
        }

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

            if (!evaluationResult.modifications.isEmpty()) {
                addToModifications(evaluationResult.modifications);
            }
            if (!evaluationResult.objectFlows.isEmpty()) {
                if (objectFlows == null) objectFlows = new LinkedList<>(evaluationResult.objectFlows);
                else objectFlows.addAll(evaluationResult.objectFlows);
            }
            if (!evaluationResult.stateChanges.isEmpty()) {
                if (stateChanges == null) stateChanges = new LinkedList<>(evaluationResult.stateChanges);
                else stateChanges.addAll(evaluationResult.stateChanges);
            }
            for (Map.Entry<Variable, ExpressionChangeData> e : evaluationResult.valueChanges.entrySet()) {
                valueChanges.merge(e.getKey(), e.getValue(), ExpressionChangeData::merge);
            }

            statementTime = Math.max(statementTime, evaluationResult.statementTime);
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
            return new EvaluationResult(evaluationContext,
                    statementTime,
                    value,
                    modifications == null ? List.of() : modifications,
                    stateChanges == null ? List.of() : stateChanges,
                    objectFlows == null ? List.of() : objectFlows,
                    valueChanges);
        }

        private StatementAnalyser statementAnalyser(Variable variable) {
            EvaluationContext ec = evaluationContext;
            assert ec != null; // otherwise current statement also null
            while (ec.getClosure() != null && ec.getCurrentType() != variable.getOwningType()) {
                ec = ec.getClosure();
            }
            return ec.getCurrentStatement();
        }

        public void variableOccursInNotNullContext(Variable variable, Expression value, int notNullRequired) {
            assert evaluationContext != null;

            if (value == NO_VALUE) {
                if (variable instanceof ParameterInfo) {
                    // we will mark this, so that the parameter analyser knows that it should wait
                    addToModifications(statementAnalyser(variable).new SetProperty(variable, VariableProperty.NOT_NULL_DELAYS_RESOLVED, Level.FALSE));
                }
                return; // not yet
            }
            if (variable instanceof This) return; // nothing to be done here
            if (variable instanceof ParameterInfo) {
                // the opposite of the previous one
                addToModifications(statementAnalyser(variable).new SetProperty(variable, VariableProperty.NOT_NULL_DELAYS_RESOLVED, Level.TRUE));
            }

            // if we already know that the variable is NOT @NotNull, then we'll raise an error
            int notNull = MultiLevel.value(evaluationContext.getProperty(value, VariableProperty.NOT_NULL), MultiLevel.NOT_NULL);
            if (notNull == MultiLevel.FALSE) {
                Message message = Message.newMessage(evaluationContext.getLocation(), Message.POTENTIAL_NULL_POINTER_EXCEPTION,
                        "Variable: " + variable.simpleName());
                addToModifications(statementAnalyser(variable).new RaiseErrorMessage(message));
            } else if (notNull == MultiLevel.DELAY) {
                // we only need to mark this in case of doubt (if we already know, we should not mark)
                addToModifications(statementAnalyser(variable).new SetProperty(variable, VariableProperty.NOT_NULL, notNullRequired));
            }
        }

        public void markRead(Variable variable, int iteration) {
            StatementAnalyser statementAnalyser = statementAnalyser(variable);
            if (iteration == 0 && statementAnalyser != null) {
                addToModifications(statementAnalyser.new MarkRead(variable, statementTime));

                // we do this because this. is often implicit
                // when explicit, there may be two MarkRead modifications, which should not bother us too much??
                if (variable instanceof FieldReference fieldReference && fieldReference.scope instanceof This) {
                    addToModifications(statementAnalyser.new MarkRead(fieldReference.scope, statementTime));
                }
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

        private void add(StatementAnalysis.StateChange modification) {
            if (stateChanges == null) stateChanges = new LinkedList<>();
            stateChanges.add(modification);
        }

        public Builder raiseError(String messageString) {
            assert evaluationContext != null;
            StatementAnalyser statementAnalyser = evaluationContext.getCurrentStatement();
            if (statementAnalyser != null) {
                Message message = Message.newMessage(evaluationContext.getLocation(), messageString);
                addToModifications(statementAnalyser.new RaiseErrorMessage(message));
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
                addToModifications(statementAnalyser.new RaiseErrorMessage(message));
            } else {
                LOGGER.warn("Analyser error: {}, {}", messageString, extra);
            }
        }

        public Expression currentExpression(Variable variable, boolean isNotAssignmentTarget) {
            ExpressionChangeData currentExpression = valueChanges.get(variable);
            if (currentExpression == null || currentExpression.value == NO_VALUE) {
                assert evaluationContext != null;
                return evaluationContext.currentValue(variable, statementTime, isNotAssignmentTarget);
            }
            return currentExpression.value;
        }

        public Expression currentStateOnAssignment(Variable variable) {
            ExpressionChangeData currentExpression = valueChanges.get(variable);
            if (currentExpression == null || currentExpression.stateOnAssignment == NO_VALUE) {
                assert evaluationContext != null;
                return evaluationContext.currentStateOnAssignment(variable);
            }
            return currentExpression.stateOnAssignment;
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
        private void assignInstanceToVariable(Variable variable, NewObject instance, Set<Variable> linkedVariables) {
            ExpressionChangeData current = valueChanges.get(variable);
            ExpressionChangeData newVcd;
            if (current == null) {
                Expression currentStateOnAssignment = currentStateOnAssignment(variable);
                newVcd = new ExpressionChangeData(instance, currentStateOnAssignment, false, true, linkedVariables);
            } else {
                newVcd = new ExpressionChangeData(instance, current.stateOnAssignment, current.markAssignment, true, linkedVariables);
            }
            valueChanges.put(variable, newVcd);
        }

        public void markMethodDelay(Variable variable, int methodDelay) {
            addToModifications(statementAnalyser(variable).new SetProperty(variable, VariableProperty.METHOD_DELAY, methodDelay));
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
                addToModifications(statementAnalyser(v).new SetProperty(v, VariableProperty.METHOD_CALLED, methodCalled));
            }
        }

        public void markContentModified(Variable variable, int modified) {
            assert evaluationContext != null;
            int ignoreContentModifications = evaluationContext.getProperty(variable, VariableProperty.IGNORE_MODIFICATIONS);
            if (ignoreContentModifications != Level.TRUE) {
                log(DEBUG_MODIFY_CONTENT, "Mark method object as content modified {}: {}", modified, variable.fullyQualifiedName());
                StatementAnalyser statementAnalyser = evaluationContext.getCurrentStatement();
                addToModifications(statementAnalyser.new SetProperty(variable, VariableProperty.MODIFIED, modified));
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
                addToModifications(statementAnalyser.new RaiseErrorMessage(message));
            } else if (notModified1 == Level.DELAY) {
                // we only need to mark this in case of doubt (if we already know, we should not mark)
                addToModifications(statementAnalyser.new SetProperty(variable, VariableProperty.NOT_MODIFIED_1, Level.TRUE));
            }
        }

        /*
        Called from Assignment and from LocalVariableCreation.
         */
        public Builder assignment(Variable assignmentTarget,
                                  Expression resultOfExpression,
                                  boolean assignmentToNonEmptyExpression,
                                  Set<Variable> linkedVariables,
                                  int iteration) {
            assert evaluationContext != null;
            Expression state = evaluationContext.getConditionManager().state;

            ExpressionChangeData valueChangeData = new ExpressionChangeData(
                    state == NO_VALUE ? NO_VALUE : resultOfExpression,
                    state,
                    iteration == 0 && assignmentToNonEmptyExpression,
                    false,
                    linkedVariables);
            valueChanges.put(assignmentTarget, valueChangeData);
            return this;
        }

        // Used in transformation of parameter lists
        public void setProperty(Variable variable, VariableProperty property, int value) {
            assert evaluationContext != null;
            StatementAnalyser statementAnalyser = evaluationContext.getCurrentStatement();
            addToModifications(statementAnalyser.new SetProperty(variable, property, value));
        }

        public void addPrecondition(Expression rest) {
            // TODO inheritance of preconditions
        }

        public void addCallOut(boolean b, ObjectFlow destination, Expression parameterExpression) {
            // TODO part of object flow
        }

        public void addAccess(boolean b, MethodAccess methodAccess, Expression object) {
            // TODO part of object flow
        }

        public void modifyingMethodAccess(Variable variable, NewObject newInstance, Set<Variable> linkedVariables) {
            add(new StateData.RemoveVariableFromState(evaluationContext, variable));
            assignInstanceToVariable(variable, newInstance, linkedVariables);
        }

        public void addErrorAssigningToFieldOutsideType(FieldInfo fieldInfo) {
            assert evaluationContext != null;
            addToModifications(evaluationContext.getCurrentStatement()
                    .new ErrorAssigningToFieldOutsideType(fieldInfo, evaluationContext.getLocation()));
        }

        public void addParameterShouldNotBeAssignedTo(ParameterInfo parameterInfo) {
            assert evaluationContext != null;
            addToModifications(evaluationContext.getCurrentStatement()
                    .new ParameterShouldNotBeAssignedTo(parameterInfo, evaluationContext.getLocation()));
        }

        public void addCircularCallOrUndeclaredFunctionalInterface() {
            assert evaluationContext != null;
            MethodLevelData methodLevelData = evaluationContext.getCurrentStatement().statementAnalysis.methodLevelData;
            addToModifications(methodLevelData.new SetCircularCallOrUndeclaredFunctionalInterface());
        }

        public int getStatementTime() {
            return statementTime;
        }

        public boolean isNotNull0(Expression expression) {
            assert evaluationContext != null;
            // intercept modifications?
            if (modifications != null && expression instanceof VariableExpression variableExpression) {
                if (modifications.stream().anyMatch(m -> m instanceof StatementAnalyser.SetProperty setProperty &&
                        setProperty.property == VariableProperty.NOT_NULL &&
                        variableExpression.variable().equals(setProperty.variable) &&
                        setProperty.value >= MultiLevel.EFFECTIVELY_NOT_NULL)) return true;
            }
            return evaluationContext.isNotNull0(expression);
        }
    }
}
