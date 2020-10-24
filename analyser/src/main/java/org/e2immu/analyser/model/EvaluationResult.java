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

package org.e2immu.analyser.model;

import com.google.common.collect.ImmutableSet;
import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.model.abstractvalue.VariableValue;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.objectflow.Origin;
import org.e2immu.analyser.objectflow.access.MethodAccess;
import org.e2immu.analyser.parser.Message;

import java.util.*;
import java.util.stream.Stream;

import static org.e2immu.analyser.model.abstractvalue.UnknownValue.NO_VALUE;
import static org.e2immu.analyser.util.Logger.LogTarget.DEBUG_MODIFY_CONTENT;
import static org.e2immu.analyser.util.Logger.LogTarget.OBJECT_FLOW;
import static org.e2immu.analyser.util.Logger.log;

public class EvaluationResult {

    private final List<StatementAnalysis.StatementAnalysisModification> modifications;
    private final List<StatementAnalysis.StateChange> stateChanges;
    private final List<ObjectFlow> objectFlows;
    public final Value value;
    public final int iteration;

    public Stream<StatementAnalysis.StatementAnalysisModification> getModificationStream() {
        return modifications.stream();
    }

    public Stream<ObjectFlow> getObjectFlowStream() {
        return objectFlows.stream();
    }

    public Stream<StatementAnalysis.StateChange> getStateChangeStream() {
        return stateChanges.stream();
    }

    public Value getValue() {
        return value;
    }
    // messages

    // properties to be added

    // create local variables

    // link variables

    // mark a variable read

    private EvaluationResult(int iteration,
                             Value value,
                             List<StatementAnalysis.StatementAnalysisModification> modifications,
                             List<StatementAnalysis.StateChange> stateChanges,
                             List<ObjectFlow> objectFlows) {
        this.modifications = modifications;
        this.stateChanges = stateChanges;
        this.objectFlows = objectFlows;
        this.value = value;
        this.iteration = iteration;
    }

    @Override
    public String toString() {
        return "EvaluationResult{" +
                "modifications=" + modifications +
                ", stateChanges=" + stateChanges +
                ", objectFlows=" + objectFlows +
                ", value=" + value +
                ", iteration=" + iteration +
                '}';
    }

    public boolean isNotNull0(EvaluationContext evaluationContext) {
        // should we trawl through the modifications?
        return evaluationContext.isNotNull0(value);
    }

    // lazy creation of lists
    public static class Builder {
        private final EvaluationContext evaluationContext;
        private final StatementAnalyser statementAnalyser;
        private List<StatementAnalysis.StatementAnalysisModification> modifications;
        private List<StatementAnalysis.StateChange> stateChanges;
        private List<ObjectFlow> objectFlows;
        private Value value;

        // for a constant EvaluationResult
        public Builder() {
            evaluationContext = null;
            statementAnalyser = null;
        }

        public Builder(EvaluationContext evaluationContext) {
            this.evaluationContext = evaluationContext;
            this.statementAnalyser = evaluationContext.getCurrentStatement();
        }

        public Builder compose(EvaluationResult... previousResults) {
            if (previousResults != null) {
                for (EvaluationResult evaluationResult : previousResults) {
                    append(evaluationResult);
                }
            }
            return this;
        }

        public Builder compose(Iterable<EvaluationResult> previousResults) {
            for (EvaluationResult evaluationResult : previousResults) {
                append(evaluationResult);
            }
            return this;
        }

        private void append(EvaluationResult evaluationResult) {
            if (!evaluationResult.modifications.isEmpty()) {
                if (modifications == null) {
                    modifications = new LinkedList<>(evaluationResult.modifications);
                } else modifications.addAll(evaluationResult.modifications);
            }
            if (!evaluationResult.objectFlows.isEmpty()) {
                if (objectFlows == null) objectFlows = new LinkedList<>(evaluationResult.objectFlows);
                else objectFlows.addAll(evaluationResult.objectFlows);
            }
            if (!evaluationResult.stateChanges.isEmpty()) {
                if (stateChanges == null) stateChanges = new LinkedList<>(evaluationResult.stateChanges);
                else stateChanges.addAll(evaluationResult.stateChanges);
            }
            if (value == null && evaluationResult.value != null) {
                value = evaluationResult.value;
            }
            // we propagate NO_VALUE
            if (evaluationResult.value == NO_VALUE) value = NO_VALUE;
        }

        // also sets result of expression, but cannot overwrite NO_VALUE
        public Builder setValue(Value value) {
            Objects.requireNonNull(value);
            if (this.value != NO_VALUE) {
                this.value = value;
            }
            return this;
        }

        public Value getValue() {
            return value;
        }

        public int getIteration() {
            return evaluationContext == null ? -1 : evaluationContext.getIteration();
        }

        public EvaluationResult build() {
            return new EvaluationResult(getIteration(), value, modifications == null ? List.of() : modifications,
                    stateChanges == null ? List.of() : stateChanges, objectFlows == null ? List.of() : objectFlows);
        }

        public void variableOccursInNotNullContext(Variable variable, Value value, int notNullRequired) {
            if (value == NO_VALUE) return; // not yet
            if (variable instanceof This) return; // nothing to be done here

            // if we already know that the variable is NOT @NotNull, then we'll raise an error
            int notNull = MultiLevel.value(evaluationContext.getProperty(value, VariableProperty.NOT_NULL), MultiLevel.NOT_NULL);
            if (notNull == MultiLevel.FALSE) {
                Message message = Message.newMessage(evaluationContext.getLocation(), Message.POTENTIAL_NULL_POINTER_EXCEPTION, variable.simpleName());
                add(statementAnalyser.new RaiseErrorMessage(message));
            } else if (notNull == MultiLevel.DELAY) {
                // we only need to mark this in case of doubt (if we already know, we should not mark)
                add(statementAnalyser.new SetProperty(variable, VariableProperty.NOT_NULL, notNullRequired));
            }
        }

        public Value createArrayVariableValue(EvaluationResult array,
                                              EvaluationResult indexValue,
                                              Location location,
                                              ParameterizedType parameterizedType,
                                              Set<Variable> dependencies,
                                              Variable arrayVariable) {
            String name = DependentVariable.dependentVariableName(array.value, indexValue.value);
            Value current = evaluationContext.currentValue(name);
            if (current != null) return current;
            String arrayName = arrayVariable == null ? null : arrayVariable.fullyQualifiedName();
            DependentVariable dependentVariable = new DependentVariable(parameterizedType, ImmutableSet.copyOf(dependencies), name, arrayName);
            modifications.add(statementAnalyser.new AddVariable(dependentVariable));

            ObjectFlow objectFlow = createInternalObjectFlow(location, parameterizedType, Origin.FIELD_ACCESS);
            return new VariableValue(dependentVariable, dependentVariable.simpleName(), objectFlow, false);
        }

        public Builder markRead(Variable variable) {
            if (modifications == null) modifications = new LinkedList<>();
            modifications.add(statementAnalyser.new SetProperty(variable, VariableProperty.READ, Level.TRUE));
            return this;
        }

        public ObjectFlow createLiteralObjectFlow(ParameterizedType parameterizedType) {
            return createInternalObjectFlow(new Location(evaluationContext.getCurrentType().typeInfo), parameterizedType, Origin.LITERAL);
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

        public void add(StatementAnalysis.StatementAnalysisModification modification) {
            if (modifications == null) modifications = new LinkedList<>();
            modifications.add(modification);
        }

        public void add(StatementAnalysis.StateChange modification) {
            if (stateChanges == null) stateChanges = new LinkedList<>();
            stateChanges.add(modification);
        }

        public Builder raiseError(String messageString) {
            Message message = Message.newMessage(evaluationContext.getLocation(), messageString);
            modifications.add(statementAnalyser.new RaiseErrorMessage(message));
            return this;
        }

        public Builder raiseError(String messageString, String extra) {
            Message message = Message.newMessage(evaluationContext.getLocation(), messageString, extra);
            modifications.add(statementAnalyser.new RaiseErrorMessage(message));
            return this;
        }

        public Builder addMessage(Message message) {
            modifications.add(statementAnalyser.new RaiseErrorMessage(message));
            return this;
        }

        public Value currentValue(Variable variable) {
            // TODO we may want to look inside the modifications, to see if there are assignments?
            return evaluationContext.currentValue(variable);
        }

        public void markMethodDelay(Variable variable, int methodDelay) {
            modifications.add(statementAnalyser.new SetProperty(variable, VariableProperty.METHOD_DELAY, methodDelay));
        }

        public void markMethodCalled(Variable variable, int methodCalled) {
            Variable v;
            if (variable instanceof This) {
                v = variable;
            } else if (variable.concreteReturnType().typeInfo == evaluationContext.getCurrentType().typeInfo) {
                v = new This(evaluationContext.getCurrentType().typeInfo);
            } else v = null;
            if (v != null) {
                add(statementAnalyser.new SetProperty(v, VariableProperty.METHOD_CALLED, methodCalled));
            }
        }

        public void markSizeRestriction(Variable variable, int size) {
            add(statementAnalyser.new SetProperty(variable, VariableProperty.SIZE, size));
        }

        public void markContentModified(Variable variable, int modified) {
            int ignoreContentModifications = evaluationContext.getProperty(variable, VariableProperty.IGNORE_MODIFICATIONS);
            if (ignoreContentModifications != Level.TRUE) {
                log(DEBUG_MODIFY_CONTENT, "Mark method object as content modified {}: {}", modified, variable.fullyQualifiedName());
                add(statementAnalyser.new SetProperty(variable, VariableProperty.MODIFIED, modified));
            } else {
                log(DEBUG_MODIFY_CONTENT, "Skip marking method object as content modified: {}", variable.fullyQualifiedName());
            }
        }

        public void variableOccursInNotModified1Context(Variable variable, Value currentValue) {
            if (currentValue == NO_VALUE) return; // not yet

            // if we already know that the variable is NOT @NotNull, then we'll raise an error
            int notModified1 = evaluationContext.getProperty(currentValue, VariableProperty.NOT_MODIFIED_1);
            if (notModified1 == Level.FALSE) {
                Message message = Message.newMessage(evaluationContext.getLocation(), Message.MODIFICATION_NOT_ALLOWED, variable.simpleName());
                add(statementAnalyser.new RaiseErrorMessage(message));
            } else if (notModified1 == Level.DELAY) {
                // we only need to mark this in case of doubt (if we already know, we should not mark)
                add(statementAnalyser.new SetProperty(variable, VariableProperty.NOT_MODIFIED_1, Level.TRUE));
            }
        }


        public void linkVariables(Variable at, Set<Variable> linked) {
            add(statementAnalyser.new LinkVariable(at, linked));
        }

        public void assignmentBasics(Variable at, Value resultOfExpression, boolean assignmentToNonEmptyExpression) {
            add(statementAnalyser.new Assignment(at, resultOfExpression, assignmentToNonEmptyExpression, evaluationContext));
        }

        public void merge(EvaluationContext copyForThen) {
        }

        public void addPropertyRestriction(Variable variable, VariableProperty property, int value) {
            add(statementAnalyser.new SetProperty(variable, property, value));
        }

        public void addPrecondition(Value rest) {
        }

        public void addCallOut(boolean b, ObjectFlow destination, Value parameterValue) {
        }

        public void addProperty(Variable variable, VariableProperty size, int newSize) {
        }

        public void addAccess(boolean b, MethodAccess methodAccess, Value object) {
        }

        public void modifyingMethodAccess(Variable variable) {
            add(new StateData.RemoveVariableFromState(variable));
        }

        public void addResultOfMethodAnalyser(AnalysisStatus analysisStatus) {
        }
    }
}
