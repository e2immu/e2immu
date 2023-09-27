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

package org.e2immu.analyser.analyser.statementanalyser;

import org.e2immu.analyser.analyser.CauseOfDelay;
import org.e2immu.analyser.analyser.CausesOfDelay;
import org.e2immu.analyser.analyser.ConditionManager;
import org.e2immu.analyser.analyser.Precondition;
import org.e2immu.analyser.analyser.util.ConditionManagerImpl;
import org.e2immu.analyser.analysis.StatementAnalysis;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.Identifier;
import org.e2immu.analyser.model.expression.DelayedExpression;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.parser.Primitives;

import java.util.Objects;
import java.util.Set;

class ConditionManagerHelper {
    /*
        three aspects:
        1- precondition, comes via MethodLevelData; is cumulative
        2- state, comes via conditionManagerForNextStatement
        3- condition, can be updated in case of SwitchOldStyle
         */
    static ConditionManager makeLocalConditionManager(Identifier identifier,
                                                      StatementAnalysis previous,
                                                      Expression condition,
                                                      Set<Variable> conditionVariables) {
        Primitives primitives = previous.primitives();
        Precondition combinedPrecondition;
        if (previous.methodLevelData().combinedPreconditionIsFinal()) {
            combinedPrecondition = previous.methodLevelData().combinedPreconditionGet();
            assert combinedPrecondition.expression().isDone();
        } else {
            CausesOfDelay causes = Objects.requireNonNullElseGet(
                    previous.methodLevelData().combinedPreconditionIsDelayedSet(), () ->
                            previous.methodAnalysis().getMethodInfo().delay(CauseOfDelay.Cause.UNREACHABLE));
            Expression delayedExpression = DelayedExpression.forPrecondition(identifier, primitives,
                    previous.methodLevelData().combinedPreconditionGet().expression(),
                    causes);
            combinedPrecondition = Precondition.forDelayed(delayedExpression);
        }

        ConditionManager previousCm = previous.stateData().getConditionManagerForNextStatement();
        // can be null in case the statement is unreachable
        if (previousCm == null) {
            return ConditionManagerImpl.impossibleConditionManager(primitives);
        }
        if (previousCm.condition().equals(condition)) {
            return previousCm.withPrecondition(combinedPrecondition);
        }
        // swap condition for the one from forwardAnalysisInfo
        return new ConditionManagerImpl(condition, conditionVariables, previousCm.state(),
                previousCm.stateVariables(), combinedPrecondition, previousCm.ignore(), previousCm.parent());
    }
}
