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

package org.e2immu.analyser.analyser.check;

import org.e2immu.analyser.analyser.CompanionAnalysis;
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.model.CompanionMethodName;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.expression.BooleanConstant;
import org.e2immu.analyser.parser.Message;

import java.util.Map;

public class CheckPrecondition {

    public static Message checkPrecondition(MethodInfo methodInfo,
                                            MethodAnalysis methodAnalysis,
                                            Map<CompanionMethodName, CompanionAnalysis> companionAnalyses) {
        Expression precondition = methodAnalysis.getPrecondition().expression();
        for (Map.Entry<CompanionMethodName, CompanionAnalysis> entry : companionAnalyses.entrySet()) {
            CompanionMethodName cmn = entry.getKey();
            if (cmn.action() == CompanionMethodName.Action.PRECONDITION) {
                if (precondition == null || precondition instanceof BooleanConstant) {
                    return Message.newMessage(methodInfo.newLocation(), Message.Label.PRECONDITION_ABSENT);
                }
                Expression expectedPrecondition = entry.getValue().getValue();

                if (!precondition.equals(expectedPrecondition)) {
                    return Message.newMessage(methodInfo.newLocation(), Message.Label.WRONG_PRECONDITION,
                            "Expected: '" + expectedPrecondition + "', but got: '" + precondition + "'");
                }
            }
        }
        return null;
    }
}
