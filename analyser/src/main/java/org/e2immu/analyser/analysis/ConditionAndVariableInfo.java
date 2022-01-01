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

package org.e2immu.analyser.analysis;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.expression.DelayedExpression;
import org.e2immu.analyser.model.expression.Instance;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.model.variable.VariableNature;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record ConditionAndVariableInfo(Expression condition,
                                       VariableInfo variableInfo,
                                       boolean alwaysEscapes,
                                       VariableNature variableNature,
                                       String firstStatementIndexForOldStyleSwitch,
                                       String indexOfLastStatement,
                                       String indexOfCurrentStatement,
                                       StatementAnalysis lastStatement,
                                       Variable myself,
                                       EvaluationContext evaluationContext) {
    // for testing
    public ConditionAndVariableInfo(Expression condition, VariableInfo variableInfo) {
        this(condition, variableInfo, false, VariableNature.METHOD_WIDE, null, "0", "-", null, variableInfo.variable(), null);
    }

    public Expression value() {
        Expression value = variableInfo.getVariableValue(myself);

        List<Variable> variables = value.variables();
        if (variables.isEmpty()) return value;
        Map<Variable, Expression> replacements = new HashMap<>();
        for (Variable variable : variables) {
            // Test 26 Enum 1 shows that the variable may not exist
            VariableInfoContainer vic = lastStatement.getVariableOrDefaultNull(variable.fullyQualifiedName());
            if (vic != null && !vic.variableNature().acceptForSubBlockMerging(indexOfCurrentStatement)) {
                Expression currentValue = vic.current().getValue();
                replacements.put(variable, currentValue);
            }
        }
        if (replacements.isEmpty()) return value;

        if (value.isDelayed()) {
            return DelayedExpression.forMerge(variableInfo.variable().parameterizedType(),
                    variableInfo.getLinkedVariables().changeAllToDelay(value.causesOfDelay()), value.causesOfDelay());
        }
        Map<Property, DV> valueProperties = evaluationContext.getValueProperties(value);
        return Instance.genericMergeResult(indexOfCurrentStatement, variableInfo.variable(), valueProperties);
    }
}
