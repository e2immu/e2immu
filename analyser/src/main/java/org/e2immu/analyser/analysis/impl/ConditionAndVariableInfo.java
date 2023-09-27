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

package org.e2immu.analyser.analysis.impl;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.analysis.StatementAnalysis;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.model.variable.VariableNature;

public record ConditionAndVariableInfo(Expression condition,
                                       Expression absoluteState,
                                       VariableInfo variableInfo,
                                       boolean alwaysEscapes,
                                       boolean alwaysEscapesOrReturns,
                                       VariableNature variableNature,
                                       String firstStatementIndexForOldStyleSwitch,
                                       String indexOfLastStatement,
                                       String indexOfCurrentStatement,
                                       StatementAnalysis lastStatement,
                                       DV executionOfLastStatement,
                                       Variable myself,
                                       EvaluationContext evaluationContext) {

    public Expression value() {
        return variableInfo.getValue();
    }

    public boolean keepInMerge() {
        // the return value is kept unless there was an escape via throws
        if (variableInfo.variable() instanceof ReturnVariable) return !alwaysEscapes();
        // other sub-blocks are kept unless there's an escape or return; however, there is an exception for the last statement!
        assert lastStatement.parent() != null;
        if (lastStatement.parent().navigationData().next.get().isEmpty()) {
            // see Basics_7_3 for an example: method ends on synchronized block, with return as its last statement.
            return !alwaysEscapes;
        }
        return !alwaysEscapesOrReturns();
    }
}
