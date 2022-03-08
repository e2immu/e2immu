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

package org.e2immu.analyser.resolver.testexample;


import java.util.Objects;

// reflects an inspection problem with code in StatementAnalysisImpl, around line 2000
public class MethodCall_19 {
    interface EvaluationContext {
    }

    interface Precondition {
        static Precondition empty(EvaluationContext evaluationContext) {
            return new Precondition() {
            };
        }
    }

    public static void method(Precondition precondition, EvaluationContext evaluationContext) {
        setPreconditionFromMethodCalls(Objects.requireNonNullElseGet(precondition,
                () -> Precondition.empty(evaluationContext)));
    }

    public static void setPreconditionFromMethodCalls(Precondition precondition) {
        // don't need anything here
    }
}
