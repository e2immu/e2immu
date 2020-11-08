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

import org.e2immu.analyser.analyser.*;
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

    private final List<StatementAnalyser.StatementAnalysisModification> modifications;
    private final List<StatementAnalysis.StateChange> stateChanges;
    private final List<ObjectFlow> objectFlows;
    public final Value value;
    public final int iteration;
    public final Map<Variable, Value> valueChanges;

    public Stream<StatementAnalyser.StatementAnalysisModification> getModificationStream() {
        return modifications.stream();
    }

    public Stream<ObjectFlow> getObjectFlowStream() {
        return objectFlows.stream();
    }

    public Stream<StatementAnalysis.StateChange> getStateChangeStream() {
        return stateChanges.stream();
    }

    public Stream<Map.Entry<Variable, Value>> getValueChangeStream() {
        return valueChanges.entrySet().stream();
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
                             List<StatementAnalyser.StatementAnalysisModification> modifications,
                             List<StatementAnalysis.StateChange> stateChanges,
                             List<ObjectFlow> objectFlows,
                             Map<Variable, Value> valueChanges) {
        this.modifications = modifications;
        this.stateChanges = stateChanges;
        this.objectFlows = objectFlows;
        this.value = value;
        this.iteration = iteration;
        this.valueChanges = valueChanges;
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
        private List<StatementAnalyser.StatementAnalysisModification> modifications;
        private List<StatementAnalysis.StateChange> stateChanges;
        private List<ObjectFlow> objectFlows;
        private Value value;
        private final Map<Variable, Value> valueChanges = new HashMap<>();

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
            statementAnalyser = null;
        }

        public Builder(EvaluationContext evaluationContext) {
            this.evaluationContext = evaluationContext;
            this.statementAnalyser = evaluationContext.getCurrentStatement(); // can be null!
        }

        public Builder compose(EvaluationResult... previousResults) {
            assert previousResults != null;
            for (EvaluationResult evaluationResult : previousResults) {
                append(false, evaluationResult);
            }
            return this;
        }

        public Builder composeIgnoreValue(EvaluationResult... previousResults) {
            assert previousResults != null;
            for (EvaluationResult evaluationResult : previousResults) {
                append(true, evaluationResult);
            }
            return this;
        }

        public Builder compose(Iterable<EvaluationResult> previousResults) {
            for (EvaluationResult evaluationResult : previousResults) {
                append(false, evaluationResult);
            }
            return this;
        }

        private void append(boolean ignoreValue, EvaluationResult evaluationResult) {
            if (!ignoreValue && evaluationResult.value != null && (value == null || value != NO_VALUE)) {
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
            valueChanges.putAll(evaluationResult.valueChanges);
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
                    stateChanges == null ? List.of() : stateChanges, objectFlows == null ? List.of() : objectFlows,
                    valueChanges);
        }

        public void variableOccursInNotNullContext(Variable variable, Value value, int notNullRequired) {
            if (value == NO_VALUE) {
                if (variable instanceof ParameterInfo) {
                    // we will mark this, so that the parameter analyser knows that it should wait
                    addToModifications(statementAnalyser.new SetProperty(variable, VariableProperty.NOT_NULL_DELAYS_RESOLVED, Level.FALSE));
                }
                return; // not yet
            }
            if (variable instanceof This) return; // nothing to be done here
            if (variable instanceof ParameterInfo) {
                // the opposite of the previous one
                addToModifications(statementAnalyser.new SetProperty(variable, VariableProperty.NOT_NULL_DELAYS_RESOLVED, Level.TRUE));
            }

            // if we already know that the variable is NOT @NotNull, then we'll raise an error
            int notNull = MultiLevel.value(evaluationContext.getProperty(value, VariableProperty.NOT_NULL), MultiLevel.NOT_NULL);
            if (notNull == MultiLevel.FALSE) {
                Message message = Message.newMessage(evaluationContext.getLocation(), Message.POTENTIAL_NULL_POINTER_EXCEPTION,
                        "Variable: " + variable.simpleName());
                addToModifications(statementAnalyser.new RaiseErrorMessage(message));
            } else if (notNull == MultiLevel.DELAY) {
                // we only need to mark this in case of doubt (if we already know, we should not mark)
                addToModifications(statementAnalyser.new SetProperty(variable, VariableProperty.NOT_NULL, notNullRequired));
            }
        }

        public Builder markRead(Variable variable, int iteration) {
            if (iteration == 0 && statementAnalyser != null) {
                addToModifications(statementAnalyser.new MarkRead(variable));

                // we do this because this. is often implicit
                // when explicit, there may be two MarkRead modifications, which should not bother us too much??
                if (variable instanceof FieldReference fieldReference && fieldReference.scope instanceof This) {
                    addToModifications(statementAnalyser.new MarkRead(fieldReference.scope));
                }
            }
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

        public void add(StatementAnalysis.StateChange modification) {
            if (stateChanges == null) stateChanges = new LinkedList<>();
            stateChanges.add(modification);
        }

        public Builder raiseError(String messageString) {
            Message message = Message.newMessage(evaluationContext.getLocation(), messageString);
            addToModifications(statementAnalyser.new RaiseErrorMessage(message));
            return this;
        }

        public Builder raiseError(String messageString, String extra) {
            Message message = Message.newMessage(evaluationContext.getLocation(), messageString, extra);
            addToModifications(statementAnalyser.new RaiseErrorMessage(message));
            return this;
        }

        public Builder addMessage(Message message) {
            addToModifications(statementAnalyser.new RaiseErrorMessage(message));
            return this;
        }

        public Value currentValue(Variable variable) {
            Value currentValue = valueChanges.get(variable);
            if (currentValue == null) return evaluationContext.currentValue(variable);
            return currentValue;
        }

        private void setCurrentValue(Variable variable, Value value) {
            if (value != NO_VALUE) {
                valueChanges.put(variable, value);
            }
        }

        public Stream<Map.Entry<Variable, Value>> getCurrentValuesStream() {
            return valueChanges.entrySet().stream();
        }

        public void markMethodDelay(Variable variable, int methodDelay) {
            addToModifications(statementAnalyser.new SetProperty(variable, VariableProperty.METHOD_DELAY, methodDelay));
        }

        public void markMethodCalled(Variable variable, int methodCalled) {
            Variable v;
            if (variable instanceof This) {
                v = variable;
            } else if (variable.concreteReturnType().typeInfo == evaluationContext.getCurrentType().typeInfo) {
                v = new This(evaluationContext.getCurrentType().typeInfo);
            } else v = null;
            if (v != null) {
                addToModifications(statementAnalyser.new SetProperty(v, VariableProperty.METHOD_CALLED, methodCalled));
            }
        }

        public void markSizeRestriction(Variable variable, int size) {
            addToModifications(statementAnalyser.new SetProperty(variable, VariableProperty.SIZE, size));
        }

        public void markContentModified(Variable variable, int modified) {
            int ignoreContentModifications = evaluationContext.getProperty(variable, VariableProperty.IGNORE_MODIFICATIONS);
            if (ignoreContentModifications != Level.TRUE) {
                log(DEBUG_MODIFY_CONTENT, "Mark method object as content modified {}: {}", modified, variable.fullyQualifiedName());
                addToModifications(statementAnalyser.new SetProperty(variable, VariableProperty.MODIFIED, modified));
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
                addToModifications(statementAnalyser.new RaiseErrorMessage(message));
            } else if (notModified1 == Level.DELAY) {
                // we only need to mark this in case of doubt (if we already know, we should not mark)
                addToModifications(statementAnalyser.new SetProperty(variable, VariableProperty.NOT_MODIFIED_1, Level.TRUE));
            }
        }


        public void linkVariables(Variable at, Set<Variable> linked) {
            addToModifications(statementAnalyser.new LinkVariable(at, linked));
        }

        public Builder assignment(Variable at, Value resultOfExpression, boolean assignmentToNonEmptyExpression, int iteration) {
            setCurrentValue(at, resultOfExpression);

            if (iteration == 0 && assignmentToNonEmptyExpression) {
                addToModifications(statementAnalyser.new MarkAssigned(at));
                Value state = evaluationContext.getConditionManager().state;
                if (state != NO_VALUE) {
                    addToModifications(statementAnalyser.new SetStateOnAssignment(at, state));
                }
            }
            return this;
        }

        public void merge(EvaluationContext copyForThen) {
        }

        public void addPropertyRestriction(Variable variable, VariableProperty property, int value) {
            addToModifications(statementAnalyser.new SetProperty(variable, property, value));
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
            add(new StateData.RemoveVariableFromState(evaluationContext, variable));
        }

        public void addResultOfMethodAnalyser(AnalysisStatus analysisStatus) {
        }

        public void addErrorAssigningToFieldOutsideType(FieldInfo fieldInfo) {
            addToModifications(evaluationContext.getCurrentStatement()
                    .new ErrorAssigningToFieldOutsideType(fieldInfo, evaluationContext.getLocation()));
        }

        public void addParameterShouldNotBeAssignedTo(ParameterInfo parameterInfo) {
            addToModifications(evaluationContext.getCurrentStatement()
                    .new ParameterShouldNotBeAssignedTo(parameterInfo, evaluationContext.getLocation()));
        }

        public void addCircularCallOrUndeclaredFunctionalInterface() {
            MethodLevelData methodLevelData = evaluationContext.getCurrentStatement().statementAnalysis.methodLevelData;
            addToModifications(methodLevelData.new SetCircularCallOrUndeclaredFunctionalInterface());
        }
    }
}
