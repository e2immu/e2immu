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

package org.e2immu.analyser.visitor;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.variable.Variable;

import java.util.Objects;

public interface StatementAnalyserVariableVisitor {

    record Data(int iteration,
                EvaluationContext evaluationContext,
                MethodInfo methodInfo,
                String statementId,
                String variableName,
                Variable variable,
                Expression currentValue,
                boolean currentValueIsDelayed,
                VariableProperties properties,
                VariableInfo variableInfo,
                VariableInfoContainer variableInfoContainer) {

        public Data {
            Objects.requireNonNull(currentValue);
        }

        public int getProperty(VariableProperty variableProperty) {
            return properties.getOrDefault(variableProperty, Level.DELAY);
        }

        public boolean hasProperty(VariableProperty variableProperty) {
            return properties.isSet(variableProperty);
        }

        public int getPropertyOfCurrentValue(VariableProperty variableProperty) {
            return evaluationContext.getProperty(currentValue, variableProperty, false);
        }

        @Override
        public String toString() {
            return "Data{" +
                    "iteration=" + iteration +
                    ", methodInfo=" + methodInfo +
                    ", statementId='" + statementId + '\'' +
                    ", variableName='" + variableName + '\'' +
                    ", variable=" + variable +
                    ", currentValue=" + currentValue +
                    ", properties=" + properties +
                    ", evaluationContext=" + evaluationContext +
                    '}';
        }
    }

    void visit(Data data);
}
