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

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.abstractvalue.UnknownValue;
import org.e2immu.analyser.model.abstractvalue.ValueWithVariable;
import org.e2immu.analyser.model.abstractvalue.VariableValue;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Messages;
import org.e2immu.analyser.util.Either;
import org.e2immu.analyser.util.SetOnce;
import org.e2immu.analyser.util.SetOnceMap;
import org.e2immu.annotation.AnnotationMode;
import org.e2immu.annotation.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static org.e2immu.analyser.util.Logger.LogTarget.VARIABLE_PROPERTIES;
import static org.e2immu.analyser.util.Logger.log;

public class StatementAnalysis extends Analysis implements Comparable<StatementAnalysis> {

    public final Statement statement;
    public final String index;
    public final MethodInfo methodInfo;
    public final Messages messages = new Messages();

    // navigation

    public final StatementAnalysis parent;
    public SetOnce<List<StatementAnalysis>> blocks = new SetOnce<>();
    public SetOnce<Optional<StatementAnalysis>> next = new SetOnce<>();

    // to be computed

    public final SetOnce<Boolean> errorValue = new SetOnce<>(); // if we detected an error value on this statement
    public final SetOnce<Value> precondition = new SetOnce<>(); // set on statements of depth 1, ie., 0, 1, 2,..., not 0.0.0, 1.0.0

    public final SetOnce<Value> state = new SetOnce<>(); // the state as it is after evaluating the statement
    public final SetOnce<Value> condition = new SetOnce<>(); // the condition as it is after evaluating the statement

    public final SetOnce<Value> valueOfExpression = new SetOnce<>();
    public final SetOnce<StatementAnalysis> replacement = new SetOnce<>();

    public final SetOnceMap<String, AboutVariable> variables = new SetOnceMap<>();

    static class AboutVariable {

    }

    enum FieldReferenceState {
        EFFECTIVELY_FINAL_DELAYED,
        SINGLE_COPY,
        MULTI_COPY,
    }


    public StatementAnalysis(MethodInfo enclosingMethod, Statement statement, StatementAnalysis parent, String index) {
        super(true, enclosingMethod.name + ":" + index);
        this.index = index;
        this.statement = statement;
        this.parent = parent;
        this.methodInfo = enclosingMethod;
    }

    @Override
    public AnnotationMode annotationMode() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void transferPropertiesToAnnotations(E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected Location location() {
        return new Location(methodInfo, index);
    }


    public String toString() {
        return index + ": " + statement.getClass().getSimpleName();
    }

    @Override
    public int compareTo(StatementAnalysis o) {
        return index.compareTo(o.index);
    }

    public boolean inErrorState() {
        boolean parentInErrorState = parent != null && parent.inErrorState();
        if (parentInErrorState) return true;
        return errorValue.isSet() && errorValue.get();
    }

    public static StatementAnalysis startOfBlock(StatementAnalysis sa, int block) {
        return sa == null ? null : sa.startOfBlock(block);
    }

    private StatementAnalysis startOfBlock(int i) {
        if (!blocks.isSet()) return null;
        List<StatementAnalysis> list = blocks.get();
        return i >= list.size() ? null : list.get(i);
    }

    public StatementAnalysis followReplacements() {
        if (replacement.isSet()) {
            return replacement.get().followReplacements();
        }
        return this;
    }

    public interface StateChange extends Function<Value, Value> {

    }

    public interface StatementAnalysisModification extends Runnable {
        // nothing extra at the moment
    }

    public void apply(StatementAnalysisModification modification) {
        modification.run();
    }

    class RaiseErrorMessage implements StatementAnalysisModification {
        private final Message message;

        public RaiseErrorMessage(Message message) {
            this.message = message;
        }

        @Override
        public void run() {
            messages.add(message);
            if (message.location.equals(location())) throw new UnsupportedOperationException();
        }
    }

    class RaiseError implements StatementAnalysisModification {
        private final String message;
        private final String extra;

        public RaiseError(String message) {
            this.message = message;
            this.extra = null;
        }

        public RaiseError(String message, String extra) {
            this.extra = extra;
            this.message = message;
        }

        @Override
        public void run() {
            if (extra != null) {
                messages.add(Message.newMessage(location(), message, extra));
            } else {
                messages.add(Message.newMessage(location(), message));
            }
            if (!errorValue.isSet()) {
                errorValue.set(true);
            }
        }
    }


    @NotNull
    public static String variableName(@NotNull Variable variable) {
        String name;
        if (variable instanceof FieldReference) {
            FieldReference fieldReference = (FieldReference) variable;
            // there are 3 cases: a field during construction phase, an effectively final field of the type we're analysing, and a field of a record
            if (fieldReference.scope == null) {
                name = fieldReference.fieldInfo.fullyQualifiedName();
            } else if (fieldReference.scope instanceof This) {
                name = ((This) fieldReference.scope).typeInfo.simpleName + ".this." + fieldReference.fieldInfo.name;
            } else {
                name = fieldReference.scope.name() + "." + fieldReference.fieldInfo.name;
            }
        } else if (variable instanceof This) {
            This thisVariable = (This) variable;
            name = thisVariable.toString();
        } else {
            // parameter, local variable
            name = variable.name();
        }
        log(VARIABLE_PROPERTIES, "Resolved variable {} to {}", variable.detailedString(), name);
        return name;
    }


    public AboutVariable ensureFieldReference(FieldReference fieldReference) {
        String name = variableName(fieldReference);
        AboutVariable av = find(name);
        if (find(name) != null) return;
        Value resetValue;
        FieldReferenceState fieldReferenceState = singleCopy(fieldReference);
        if (fieldReferenceState == FieldReferenceState.EFFECTIVELY_FINAL_DELAYED) {
            resetValue = UnknownValue.NO_VALUE; // delay
        } else if (fieldReferenceState == FieldReferenceState.MULTI_COPY) {
            resetValue = new VariableValue(this, fieldReference, name);
        } else {
            FieldAnalysis fieldAnalysis = fieldReference.fieldInfo.fieldAnalysis.get();
            int effectivelyFinal = fieldAnalysis.getProperty(VariableProperty.FINAL);
            if (effectivelyFinal == Level.TRUE) {
                if (fieldAnalysis.effectivelyFinalValue.isSet()) {
                    resetValue = safeFinalFieldValue(fieldAnalysis.effectivelyFinalValue.get());
                } else if (fieldReference.fieldInfo.owner.hasBeenDefined()) {
                    resetValue = UnknownValue.NO_VALUE; // delay
                } else {
                    // undefined, will never get a value, but may have decent properties
                    // the properties will be copied from fieldAnalysis into properties in internalCreate
                    resetValue = new VariableValue(this, fieldReference, name);
                }
            } else {
                // local variable situation
                resetValue = new VariableValue(this, fieldReference, name);
            }
        }
        internalCreate(fieldReference, name, resetValue, resetValue, fieldReferenceState);
    }

    public void ensureThisVariable(This thisVariable) {
        String name = variableName(thisVariable);
        if (find(name) != null) return;
        VariableValue resetValue = new VariableValue(this, thisVariable, name);
        internalCreate(thisVariable, name, resetValue, resetValue, FieldReferenceState.SINGLE_COPY);
    }

    private AboutVariable findComplain(@NotNull Variable variable) {
        AboutVariable aboutVariable = find(variable);
        if (aboutVariable != null) {
            return aboutVariable;
        }
        if (variable instanceof FieldReference) {
            ensureFieldReference((FieldReference) variable);
        } else if (variable instanceof This) {
            ensureThisVariable((This) variable);
        }
        AboutVariable aboutVariable2ndAttempt = find(variable);
        if (aboutVariable2ndAttempt != null) {
            return aboutVariable2ndAttempt;
        }
        throw new UnsupportedOperationException("Cannot find variable " + variable.detailedString());
    }

    private AboutVariable find(@NotNull Variable variable) {
        String name = variableName(variable);
        if (name == null) return null;
        return find(name);
    }

    private AboutVariable find(String name) {
        StatementAnalysis level = this;
        while (level != null) {
            AboutVariable aboutVariable = level.variables.get(name);
            if (aboutVariable != null) return aboutVariable;
            level = level.parent;
        }
        return null;
    }


    class SetProperty implements StatementAnalysisModification {
        private final Either<Variable, String> variable;
        private final VariableProperty property;
        private final int value;

        public SetProperty(Variable variable, VariableProperty property, int value) {
            this.value = value;
            this.property = property;
            this.variable = Either.left(variable);
        }

        public SetProperty(String variableName, VariableProperty property, int value) {
            this.value = value;
            this.property = property;
            this.variable = Either.right(variableName);
        }

        @Override
        public void run() {
            AboutVariable aboutVariable = variable.isLeft() ? find(variable.getLeft()) : find(variable.getRight());
            if (aboutVariable == null) {
                if (create && variable.isLeft()) {
                    if (variable.getLeft() instanceof FieldReference)
                        aboutVariable = ensureFieldReference((FieldReference) variable.getLeft());
                } else return;
            }
            int current = aboutVariable.getProperty(property);
            if (current < value) {
                aboutVariable.setProperty(property, value);
            }

            Value currentValue = aboutVariable.getCurrentValue();
            ValueWithVariable valueWithVariable;
            if ((valueWithVariable = currentValue.asInstanceOf(ValueWithVariable.class)) == null) return;
            Variable other = valueWithVariable.variable;
            if (!variable.equals(other)) {
                addProperty(other, property, value);
            }
        }
    }

    class SetErrorState implements StatementAnalysisModification {
        @Override
        public void run() {
            if (!errorValue.isSet()) errorValue.set(true);
        }
    }

    class AddVariable implements StatementAnalysisModification {
        private final Variable variable;

        public AddVariable(Variable variable) {
            this.variable = variable;
        }

        @Override
        public void run() {

        }
    }

    class LinkVariable implements StatementAnalysisModification {
        private final Variable variable;
        private final Set<Variable> to;

        public LinkVariable(Variable variable, Set<Variable> to) {
            this.variable = variable;
            this.to = to;
        }

        @Override
        public void run() {

        }
    }

    class RemoveVariableFromState implements StateChange {
        private final Variable variable;

        public RemoveVariableFromState(Variable variable) {
            this.variable = variable;
        }

        @Override
        public Value apply(Value value) {
            return null;
        }
    }

}
