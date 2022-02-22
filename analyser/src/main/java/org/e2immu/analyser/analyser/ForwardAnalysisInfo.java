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

package org.e2immu.analyser.analyser;

import org.e2immu.analyser.analysis.FlowData;
import org.e2immu.analyser.analysis.StatementAnalysis;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.Statement;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.statement.BreakStatement;
import org.e2immu.analyser.model.statement.ReturnStatement;
import org.e2immu.analyser.parser.Primitives;

import java.util.Map;
import java.util.Objects;

public record ForwardAnalysisInfo(DV execution, ConditionManager conditionManager,
                                  LocalVariableCreation catchVariable,
                                  Map<String, Expression> switchIdToLabels,
                                  Expression switchSelector,
                                  CausesOfDelay switchSelectorIsDelayed) {

    public ForwardAnalysisInfo {
        Objects.requireNonNull(switchSelectorIsDelayed);
    }

    public static ForwardAnalysisInfo startOfMethod(Primitives primitives) {
        return new ForwardAnalysisInfo(FlowData.ALWAYS, ConditionManager.initialConditionManager(primitives),
                null, null, null, CausesOfDelay.EMPTY);
    }

    public ForwardAnalysisInfo otherConditionManager(ConditionManager conditionManager) {
        return new ForwardAnalysisInfo(execution, conditionManager, catchVariable, switchIdToLabels, switchSelector, switchSelectorIsDelayed);
    }

    /*
        this is the statement to be executed (with an ID that can match one of the elements in the map
         */
    public Expression conditionInSwitchStatement(EvaluationResult evaluationContext,
                                                 StatementAnalyser previousStatement,
                                                 Expression previous,
                                                 StatementAnalysis statementAnalysis) {
        Expression expression = computeConditionInSwitchStatement(evaluationContext, previousStatement, previous,
                statementAnalysis);
        if (switchSelectorIsDelayed.isDelayed()) {
            return DelayedExpression.forSwitchSelector(evaluationContext.getPrimitives(),
                    switchSelector.causesOfDelay().merge(expression.causesOfDelay()));
        }
        return expression;
    }

    private Expression computeConditionInSwitchStatement(EvaluationResult context,
                                                         StatementAnalyser previousStatement,
                                                         Expression previous,
                                                         StatementAnalysis statementAnalysis) {
        if (switchIdToLabels() != null) {
            Statement statement = previousStatement == null ? null : previousStatement.statement();
            Expression startFrom;
            if (statement instanceof BreakStatement || statement instanceof ReturnStatement) {
                // clear all
                startFrom = new BooleanConstant(statementAnalysis.primitives(), true);
            } else {
                startFrom = previous;
            }
            Expression label = switchIdToLabels().get(statementAnalysis.index());
            if (label != null) {
                Expression toAdd;
                if (label == EmptyExpression.DEFAULT_EXPRESSION) {
                    toAdd = Negation.negate(context,
                            Or.or(context, switchIdToLabels().values().stream()
                                    .filter(e -> e != EmptyExpression.DEFAULT_EXPRESSION).toList()));
                } else {
                    toAdd = label;
                }
                if (startFrom.isBoolValueTrue()) return toAdd;
                return And.and(context, startFrom, toAdd);
            }
            return startFrom;
        }
        return previous;
    }
}
