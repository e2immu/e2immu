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

import org.e2immu.analyser.analyser.delay.FlowDataConstants;
import org.e2immu.analyser.analyser.impl.util.BreakDelayLevel;
import org.e2immu.analyser.analyser.util.ConditionManagerImpl;
import org.e2immu.analyser.analysis.StatementAnalysis;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.Identifier;
import org.e2immu.analyser.model.Statement;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.statement.BreakStatement;
import org.e2immu.analyser.model.statement.ReturnStatement;
import org.e2immu.analyser.parser.Primitives;

import java.util.Map;
import java.util.Objects;

public record ForwardAnalysisInfo(DV execution,
                                  ConditionManager conditionManager,
                                  LocalVariableCreation catchVariable,
                                  SwitchData switchData,
                                  BreakDelayLevel breakDelayLevel) {

    public record SwitchData(Map<String, Expression> switchIdToLabels,
                             Expression switchSelector,
                             CausesOfDelay switchSelectorIsDelayed,
                             ConditionManager initialConditionManager) {
        public SwitchData {
            Objects.requireNonNull(switchSelectorIsDelayed);
        }

        /*
         this is the statement to be executed (with an ID that can match one of the elements in the map
         */
        public Expression conditionInSwitchStatement(EvaluationResult evaluationContext,
                                                     StatementAnalyser previousStatement,
                                                     Expression previous,
                                                     StatementAnalysis statementAnalysis) {
            Expression expression = computeSwitchCondition(evaluationContext, previousStatement, previous,
                    statementAnalysis);
            if (switchSelectorIsDelayed.isDelayed()) {
                return DelayedExpression.forSwitchSelector(Identifier.generate("switchSelector"),
                        evaluationContext.getPrimitives(), switchSelector,
                        switchSelector.causesOfDelay().merge(expression.causesOfDelay()));
            }
            return expression;
        }

        private Expression computeSwitchCondition(EvaluationResult context,
                                                  StatementAnalyser previousStatement,
                                                  Expression previousSwitchCondition,
                                                  StatementAnalysis statementAnalysis) {
            Map<String, Expression> stringExpressionMap = switchIdToLabels();
            if (stringExpressionMap == null) {
                return previousSwitchCondition;
            }
            Expression label = stringExpressionMap.get(statementAnalysis.index());
            if (label == null) {
                return previousSwitchCondition; // stays as is; SwitchStatement_8: 1.0.01, 2==i
            }
            if (previousStatement == null) {
                return label; // first case; SwitchStatement_8: 2==i
            }
            if (previousSwitchCondition.isDelayed()) {
                return previousSwitchCondition; // we'll have to wait
            }

            ConditionManager cm = previousStatement.getStatementAnalysis().stateData()
                    .getConditionManagerForNextStatement();
            if (cm.state().isBoolValueTrue()) {
                return label; // replace
            }
            Expression absoluteStateOfPrevious = cm.absoluteStateUpTo(initialConditionManager, context);
            boolean replace;
            if (label instanceof And andLabel) {
                // default situation
                replace = andLabel.getExpressions().stream().anyMatch(e -> equalOrIn(e, absoluteStateOfPrevious));
            } else if (label instanceof Negation n) {
                // default situation, only one 'case' with one condition
                replace = equalOrIn(n.getExpression(), absoluteStateOfPrevious);
            } else if (label instanceof Or or) {
                // multiple labels
                replace = or.expressions().stream().anyMatch(o ->
                        equalOrIn(Negation.negate(context, o), absoluteStateOfPrevious));
            } else {
                // single label
                replace = equalOrIn(Negation.negate(context, label), absoluteStateOfPrevious);
            }
            if (replace) {
                // normal replace; SwitchStatement_8: 2->3, 5->6, 6-> default
                return label;
            }
            // TODO: must compensate for initial state (parameterize absoluteState to limit recursion)
            return Or.or(context, label, absoluteStateOfPrevious); // SwitchStatement_8: from 3->4,4->5
        }

        private boolean equalOrIn(Expression expression, Expression target) {
            return target.equals(expression) ||
                    target instanceof And and && and.getExpressions().stream().anyMatch(expression::equals);
        }
    }

    public static ForwardAnalysisInfo startOfMethod(Primitives primitives, BreakDelayLevel breakDelayLevel) {
        return new ForwardAnalysisInfo(FlowDataConstants.ALWAYS, ConditionManagerImpl.initialConditionManager(primitives),
                null, null, breakDelayLevel);
    }

    public ForwardAnalysisInfo otherConditionManager(ConditionManager conditionManager) {
        return new ForwardAnalysisInfo(execution, conditionManager, catchVariable, switchData, breakDelayLevel);
    }

}
