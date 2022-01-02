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

import org.e2immu.analyser.analyser.CausesOfDelay;
import org.e2immu.analyser.analyser.ConditionManager;
import org.e2immu.analyser.analyser.Precondition;
import org.e2immu.analyser.analysis.StatementAnalysis;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.parser.Primitives;

class ConditionManagerHelper {
    /*
        three aspects:
        1- precondition, comes via MethodLevelData; is cumulative
        2- state, comes via conditionManagerForNextStatement
        3- condition, can be updated in case of SwitchOldStyle
         */
    static ConditionManager makeLocalConditionManager(StatementAnalysis previous,
                                                      Expression condition,
                                                      CausesOfDelay conditionIsDelayed) {
        Primitives primitives = previous.primitives();
        Precondition combinedPrecondition;
        CausesOfDelay combinedPreconditionIsDelayed;
        if (previous.methodLevelData().combinedPrecondition.isFinal()) {
            combinedPrecondition = previous.methodLevelData().combinedPrecondition.get();
            combinedPreconditionIsDelayed = CausesOfDelay.EMPTY;
        } else {
            combinedPreconditionIsDelayed = previous.methodLevelData().combinedPreconditionIsDelayedSet();
            combinedPrecondition = Precondition.empty(primitives);
        }

        ConditionManager previousCm = previous.stateData().conditionManagerForNextStatement.get();
        // can be null in case the statement is unreachable
        if (previousCm == null) {
            return ConditionManager.impossibleConditionManager(primitives);
        }
        if (previousCm.condition().equals(condition)) {
            return previousCm.withPrecondition(combinedPrecondition, combinedPreconditionIsDelayed);
        }
        // swap condition for the one from forwardAnalysisInfo
        return new ConditionManager(condition, conditionIsDelayed, previousCm.state(),
                previousCm.stateIsDelayed(), combinedPrecondition,
                combinedPreconditionIsDelayed, previousCm.parent());
    }
}
