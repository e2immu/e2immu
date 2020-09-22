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
import org.e2immu.analyser.model.expression.ArrayAccess;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.objectflow.Origin;
import org.e2immu.analyser.objectflow.access.MethodAccess;
import org.e2immu.analyser.parser.Message;

import java.util.Collection;
import java.util.Set;

public class EvaluationResult {

    public final Value value;

    public Value getValue() {
        return value;
    }


    // messages

    // properties to be added

    // create local variables

    // link variables

    // mark a variable read

    private EvaluationResult(Value value, EvaluationResult... previous) {
        this.value = value;
    }

    public boolean isNotNull0(EvaluationContext evaluationContext) {
    }

    public static class Builder {

        private Value value;

        public Builder compose(EvaluationResult... previousResults) {
            return this;
        }

        public Builder compose(Iterable<EvaluationResult> previousResults) {
            return this;
        }


        // for those rare occasions where the result of the expression is different from
        // the value returned
        public Builder setValueAndResultOfExpression(Value value, Value resultOfExpression) {
            return this;
        }

        // also sets result of expression
        public Builder setValue(Value value) {
            return this;
        }

        public Value getValue() {
            return value;
        }

        public EvaluationResult build() {

        }

        // TODO From statement analyser, needs to translate into an action

        public void variableOccursInNotNullContext(Variable variable, Value value, EvaluationContext evaluationContext, int notNullRequired) {
        }

        // TODO from variable properties, arrayVariableValue
        // also sets value
        public Value createArrayVariableValue(EvaluationResult array, EvaluationResult indexValue, ParameterizedType returnType, Set<Variable> dependencies, Variable arrayVariable) {
        }

        // TODO from VariableProperties
        public Builder markRead(String dependentVariableName) {
        }
        public Builder markRead(Variable variable) {
        }
        
        public ObjectFlow createLiteralObjectFlow(ParameterizedType commonType) {
        }
        public ObjectFlow createInternalObjectFlow(ParameterizedType intParameterizedType, Origin resultOfMethod) {
        }

        public Builder raiseError(String message) {
            return this;
        }
        public Builder raiseError(String message, String extra) {
            return this;
        }
        public Builder addMessage(Message newMessage) {
            return this;
        }

        public Value currentValue(Variable variable, EvaluationContext evaluationContext) {
        }

        public void markMethodDelay(EvaluationContext evaluationContext, Variable variable, int methodDelay) {
        }

        public void markMethodCalled(EvaluationContext evaluationContext, Variable variable, int methodCalled) {
        }

        public void markSizeRestriction(EvaluationContext evaluationContext, Variable variable, int size) {
        }

        public void markContentModified(EvaluationContext evaluationContext, Variable variable, Value currentValue, int modified) {
        }

        public void variableOccursInNotModified1Context(Variable variable, Value currentValue, EvaluationContext evaluationContext) {
        }

        public void checkForIllegalMethodUsageIntoNestedOrEnclosingType(MethodInfo methodInfo, EvaluationContext evaluationContext) {
        }


        public Variable ensureArrayVariable(ArrayAccess arrayAccess, String name, Variable arrayVariable) {
        }


        public void changeCurrentStatementToErrorState() {
        }

        public void linkVariables(Variable at, Set<Variable> linked) {
        }

        public void assignmentBasics(Variable at, Value resultOfExpression, boolean b) {
        }

        public void merge(EvaluationContext copyForThen) {
        }

        public void addPropertyRestriction(Variable variable, VariableProperty notNull, int effectivelyContentNotNull) {
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
        }

        public void addResultOfMethodAnalyser(boolean analyse) {
        }
    }
}
