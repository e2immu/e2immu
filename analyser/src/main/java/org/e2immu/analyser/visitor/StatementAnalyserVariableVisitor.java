/*
 * e2immu: a static code analyser for effective and eventual immutability
 * Copyright 2020-2021, Bart Naudts, https://www.e2immu.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details. You should have received a copy of the GNU Lesser General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
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
            return evaluationContext.getProperty(currentValue, variableProperty, false, false);
        }

        public int falseFrom1() {
            return iteration == 0 ? Level.DELAY: Level.FALSE;
        }

        public int falseFrom2() {
            return iteration <= 1 ? Level.DELAY: Level.FALSE;
        }
    }

    void visit(Data data);
}
