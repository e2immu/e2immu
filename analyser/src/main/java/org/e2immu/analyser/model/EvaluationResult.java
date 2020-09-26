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
import org.e2immu.analyser.analyser.StateData;
import org.e2immu.analyser.analyser.StatementAnalyser;
import org.e2immu.analyser.analyser.VariableDataImpl;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.abstractvalue.VariableValue;
import org.e2immu.analyser.model.expression.ArrayAccess;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.objectflow.Origin;
import org.e2immu.analyser.objectflow.access.MethodAccess;
import org.e2immu.analyser.parser.Message;

import java.util.*;
import java.util.stream.Stream;

import static org.e2immu.analyser.model.abstractvalue.UnknownValue.NO_VALUE;
import static org.e2immu.analyser.util.Logger.LogTarget.DEBUG_MODIFY_CONTENT;
import static org.e2immu.analyser.util.Logger.log;

public class EvaluationResult {

    private final Stream<StatementAnalysis.StatementAnalysisModification> modificationStream;
    private final Stream<StatementAnalysis.StateChange> stateChangeStream;
    public final Value value;

    public Stream<StatementAnalysis.StatementAnalysisModification> getModificationStream() {
        return modificationStream;
    }

    public Stream<StatementAnalysis.StateChange> getStateChangeStream() {
        return stateChangeStream;
    }

    public Value getValue() {
        return value;
    }
    // messages

    // properties to be added

    // create local variables

    // link variables

    // mark a variable read

    private EvaluationResult(Value value, Stream<StatementAnalysis.StatementAnalysisModification> modificationStream,
                             Stream<StatementAnalysis.StateChange> stateChangeStream) {
        this.modificationStream = modificationStream;
        this.stateChangeStream = stateChangeStream;
        this.value = value;
    }

    public boolean isNotNull0(EvaluationContext evaluationContext) {
    }

    // lazy creation of lists
    public static class Builder {
        private final EvaluationContext evaluationContext;
        private final StatementAnalyser statementAnalyser;
        private List<StatementAnalysis.StatementAnalysisModification> modifications;
        private List<StatementAnalysis.StateChange> stateChanges;
        private List<EvaluationResult> previousResults;
        private Value value;

        // for a constant EvaluationResult
        public Builder() {
            evaluationContext = null;
            statementAnalysis = null;
        }

        public Builder(EvaluationContext evaluationContext) {
            this.evaluationContext = evaluationContext;
            this.statementAnalyser = evaluationContext.getCurrentStatement();
        }

        public Builder compose(EvaluationResult... previousResults) {
            if (previousResults != null) {
                if (this.previousResults == null) {
                    this.previousResults = new ArrayList<>();
                }
                Collections.addAll(this.previousResults, previousResults);
            }
            return this;
        }

        public Builder compose(Iterable<EvaluationResult> previousResults) {
            if (this.previousResults == null) {
                this.previousResults = new ArrayList<>();
            }
            for (EvaluationResult evaluationResult : previousResults) {
                this.previousResults.add(evaluationResult);
            }
            return this;
        }


        // for those rare occasions where the result of the expression is different from
        // the value returned
        public Builder setValueAndResultOfExpression(Value value, Value resultOfExpression) {
            return this;
        }

        // also sets result of expression
        public Builder setValue(Value value) {
            this.value = value;
            return this;
        }

        public Value getValue() {
            return value;
        }

        public EvaluationResult build() {
            Value firstNonNull = value != null || previousResults == null ? value : previousResults.stream()
                    .map(er -> er.value)
                    .filter(Objects::nonNull)
                    .findFirst().orElse(null);
            Stream<StatementAnalysis.StatementAnalysisModification> modificationStream = modifications == null ? Stream.empty() : modifications.stream();
            Stream<StatementAnalysis.StateChange> stateChangeStream = stateChanges == null ? Stream.empty() : stateChanges.stream();
            if (previousResults != null) {
                for (EvaluationResult evaluationResult : previousResults) {
                    modificationStream = Stream.concat(evaluationResult.getModificationStream(), modificationStream);
                    stateChangeStream = Stream.concat(evaluationResult.getStateChangeStream(), stateChangeStream);
                }
            }

            return new EvaluationResult(firstNonNull, modificationStream, stateChangeStream);
        }

        public void variableOccursInNotNullContext(Variable variable, Value value, int notNullRequired) {
            if (value == NO_VALUE) return; // not yet
            if (variable instanceof This) return; // nothing to be done here

            // if we already know that the variable is NOT @NotNull, then we'll raise an error
            int notNull = MultiLevel.value(evaluationContext.getProperty(value, VariableProperty.NOT_NULL), MultiLevel.NOT_NULL);
            if (notNull == MultiLevel.FALSE) {
                Message message = Message.newMessage(evaluationContext.getLocation(), Message.POTENTIAL_NULL_POINTER_EXCEPTION, variable.name());
                add(statementAnalyser.new RaiseErrorMessage(message));
            } else if (notNull == MultiLevel.DELAY) {
                // we only need to mark this in case of doubt (if we already know, we should not mark)
                add(statementAnalyser.new SetProperty(variable, VariableProperty.NOT_NULL, notNullRequired));
            }
        }

        public Value createArrayVariableValue(EvaluationResult array,
                                              EvaluationResult indexValue,
                                              ParameterizedType parameterizedType,
                                              Set<Variable> dependencies,
                                              Variable arrayVariable) {
            String name = ArrayAccess.dependentVariableName(array.value, indexValue.value);
            Value current = evaluationContext.currentValue(name);
            if (current != null) return current;
            String arrayName = arrayVariable == null ? null : VariableDataImpl.Builder.variableName(arrayVariable);
            DependentVariable dependentVariable = new DependentVariable(parameterizedType, ImmutableSet.copyOf(dependencies), name, arrayName);
            modifications.add(statementAnalyser.new AddVariable(dependentVariable));
            return new VariableValue(evaluationContext, dependentVariable, dependentVariable.name());
        }

        public Builder markRead(String dependentVariableName) {
            modifications.add(statementAnalyser.new SetProperty(dependentVariableName, VariableProperty.READ, Level.TRUE));
            return this;
        }

        public Builder markRead(Variable variable) {
            modifications.add(statementAnalyser.new SetProperty(variable, VariableProperty.READ, Level.TRUE));
            return this;
        }

        public ObjectFlow createLiteralObjectFlow(ParameterizedType commonType) {
        }

        public ObjectFlow createInternalObjectFlow(ParameterizedType intParameterizedType, Origin resultOfMethod) {
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
            if (methodCalled == Level.TRUE) {
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
        }

        public void markSizeRestriction(Variable variable, int size) {
            add(statementAnalyser.new SetProperty(variable, VariableProperty.SIZE, size));
        }

        public void markContentModified(Variable variable, int modified) {
            int ignoreContentModifications = evaluationContext.getProperty(variable, VariableProperty.IGNORE_MODIFICATIONS);
            if (ignoreContentModifications != Level.TRUE) {
                log(DEBUG_MODIFY_CONTENT, "Mark method object as content modified {}: {}", modified, variable.detailedString());
                add(statementAnalyser.new SetProperty(variable, VariableProperty.MODIFIED, modified));
            } else {
                log(DEBUG_MODIFY_CONTENT, "Skip marking method object as content modified: {}", variable.detailedString());
            }
        }

        public void variableOccursInNotModified1Context(Variable variable, Value currentValue) {
            if (currentValue == NO_VALUE) return; // not yet

            // if we already know that the variable is NOT @NotNull, then we'll raise an error
            int notModified1 = evaluationContext.getProperty(currentValue, VariableProperty.NOT_MODIFIED_1);
            if (notModified1 == Level.FALSE) {
                Message message = Message.newMessage(evaluationContext.getLocation(), Message.MODIFICATION_NOT_ALLOWED, variable.name());
                add(statementAnalyser.new RaiseErrorMessage(message));
            } else if (notModified1 == Level.DELAY) {
                // we only need to mark this in case of doubt (if we already know, we should not mark)
                add(statementAnalyser.new SetProperty(variable, VariableProperty.NOT_MODIFIED_1, Level.TRUE));
            }
        }

        public Variable ensureArrayVariable(ArrayAccess arrayAccess, String name, Variable arrayVariable) {
        }


        public void linkVariables(Variable at, Set<Variable> linked) {
            add(statementAnalyser.new LinkVariable(at, linked));
        }

        public void assignmentBasics(Variable at, Value resultOfExpression, boolean b) {
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

        public void addResultOfMethodAnalyser(boolean analyse) {
        }
    }
}
